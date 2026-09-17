package com.voxora.app.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Writer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class TextExtractor @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun extract(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        try {
            coroutineContext.ensureActive()
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            var name = uri.lastPathSegment.orEmpty()
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn) && cursor.getLong(sizeColumn) > MAX_BYTES) {
                        throw ExtractionException("This file is too large. Choose a file smaller than 20 MB.")
                    }
                }
            }
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val isPdf = when {
                mime == "application/pdf" -> true
                mime == "text/plain" -> false
                extension == "pdf" -> true
                extension == "txt" -> false
                else -> throw ExtractionException("Unsupported file type. Choose a PDF or UTF-8 TXT file.")
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total + 1))
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) {
                            throw ExtractionException("This file is too large. Choose a file smaller than 20 MB.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } ?: throw ExtractionException("Unable to open this file. Please select it again.")
            coroutineContext.ensureActive()
            val text = BoundedTextWriter(coroutineContext).use { writer ->
                if (isPdf) {
                    PDFBoxResourceLoader.init(context)
                    PDDocument.load(bytes).use { document ->
                        coroutineContext.ensureActive()
                        if (document.numberOfPages > MAX_PAGES) {
                            throw ExtractionException("This PDF has too many pages. Choose a PDF with at most 1,000 pages.")
                        }
                        if (!document.currentAccessPermission.canExtractContent()) {
                            throw ExtractionException("This PDF does not allow text extraction. Choose an unrestricted PDF.")
                        }
                        val stripper = object : PDFTextStripper() {
                            private var processedChars = 0L

                            override fun processTextPosition(text: TextPosition) {
                                coroutineContext.ensureActive()
                                processedChars += text.unicode.length
                                if (processedChars > MAX_CHARS) {
                                    throw ExtractionException("This file contains too much text. Choose a file with at most 2 million characters.")
                                }
                                super.processTextPosition(text)
                            }

                            override fun processPage(page: PDPage) {
                                coroutineContext.ensureActive()
                                super.processPage(page)
                                coroutineContext.ensureActive()
                            }

                            /**
                             * PDFBox collects glyphs in content-stream order and only
                             * sorts them when `sortByPosition` is enabled. That default
                             * is what scrambles the reading order of PDFs whose stream
                             * is not painted in visual order, so the fragments are
                             * ordered here by [PdfReadingOrder] instead — which also
                             * keeps multi-column pages from being row-interleaved.
                             */
                            override fun writePage() {
                                coroutineContext.ensureActive()
                                charactersByArticle.forEach { article ->
                                    if (article.size < 2) return@forEach
                                    val fragments = article.map { position ->
                                        PdfReadingOrder.Fragment(
                                            position.xDirAdj,
                                            position.yDirAdj,
                                            position.widthDirAdj,
                                            position.heightDir,
                                        )
                                    }
                                    val sorted = PdfReadingOrder.order(fragments).map(article::get)
                                    article.clear()
                                    article.addAll(sorted)
                                }
                                super.writePage()
                                coroutineContext.ensureActive()
                            }
                        }
                        stripper.sortByPosition = false
                        stripper.lineSeparator = "\n"
                        stripper.paragraphStart = ""
                        stripper.paragraphEnd = "\n\n"
                        stripper.pageEnd = "\n\n"
                        stripper.writeText(document, writer)
                    }
                } else {
                    val decoder = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    InputStreamReader(bytes.inputStream(), decoder).use { reader ->
                        val buffer = CharArray(8192)
                        var first = true
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            val offset = if (first && buffer[0] == '\uFEFF') 1 else 0
                            first = false
                            writer.write(buffer, offset, count - offset)
                        }
                    }
                }
                writer.toString()
            }
            coroutineContext.ensureActive()
            val chunks = ChunkQueue.documentChunks(text)
            coroutineContext.ensureActive()
            if (chunks.isEmpty()) {
                throw ExtractionException(
                    if (isPdf) "No readable text was found. This PDF may be scanned or blank; OCR is not supported."
                    else "This text file is blank. Choose a file containing text."
                )
            }
            chunks
        } catch (error: ExtractionException) {
            coroutineContext.ensureActive()
            throw error
        } catch (error: InvalidPasswordException) {
            coroutineContext.ensureActive()
            throw ExtractionException("This PDF is password-protected. Choose an unlocked PDF.", error)
        } catch (error: CharacterCodingException) {
            coroutineContext.ensureActive()
            throw ExtractionException("This text file is not valid UTF-8. Save it as UTF-8 and try again.", error)
        } catch (error: SecurityException) {
            coroutineContext.ensureActive()
            throw ExtractionException("Permission to read this file was denied. Please select it again.", error)
        } catch (error: IOException) {
            coroutineContext.ensureActive()
            throw ExtractionException("Unable to read this file. It may be damaged or unavailable; try another PDF or TXT file.", error)
        }
    }

    private class ExtractionException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private class BoundedTextWriter(private val coroutineContext: CoroutineContext) : Writer() {
        private val text = StringBuilder()

        override fun write(buffer: CharArray, offset: Int, length: Int) {
            coroutineContext.ensureActive()
            if (length > MAX_CHARS - text.length) {
                throw ExtractionException("This file contains too much text. Choose a file with at most 2 million characters.")
            }
            text.append(buffer, offset, length)
        }

        override fun flush() = Unit

        override fun close() = Unit

        override fun toString(): String = text.toString()
    }

    private companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        const val MAX_CHARS = 2_000_000
        const val MAX_PAGES = 1_000
    }
}
