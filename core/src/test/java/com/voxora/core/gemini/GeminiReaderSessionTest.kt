package com.voxora.core.gemini

import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiReaderSessionTest {
    @Test
    fun reusesConnectionAndReturnsOnlyAudioTranscription() = fixture { f ->
        f.ready()
        val setup = JSONObject(f.socket.sent.single()).getJSONObject("setup")
        assertTrue(setup.has("outputAudioTranscription"))
        repeat(2) {
            val received = mutableListOf<Int>()
            val pending = start(f) { received.add(it[0].toInt()) }
            f.socket.message(audio(1))
            f.socket.message("""{"serverContent":{"modelTurn":{"parts":[{"text":"unspoken"}]},"outputTranscription":{"text":"spoken"},"turnComplete":true}}""")
            assertEquals("spoken", pending.await().getOrThrow())
            assertEquals(listOf(1), received)
            assertTrue(f.session.reusable)
        }
        val empty = start(f)
        f.socket.message(DONE)
        assertNull(empty.await().getOrThrow())
        assertEquals(1, f.factory.sockets.size)
    }

    @Test
    fun transportEndDrainsAcceptedPcmAndHonorsQueuedCompletion() = fixture { f ->
        for (ending in listOf("failure", "closing", "closed")) {
            for (complete in listOf(false, true)) {
                f.ready()
                val gate = f.gate()
                val received = Collections.synchronizedList(mutableListOf<Int>())
                val pending = start(f) {
                    gate.block()
                    received.add(it[0].toInt())
                }
                val socket = f.socket
                socket.message(audio(1))
                gate.awaitEntry()
                socket.message(audio(2), binary = true)
                if (complete) socket.message(DONE)
                socket.end(ending)
                socket.end(ending)
                socket.message(audio(3))
                assertFalse(f.session.reusable)
                assertFalse(pending.isCompleted)
                gate.release.countDown()
                val result = withTimeout(2000) { pending.await() }
                assertEquals(listOf(1, 2), received.toList())
                assertEquals(complete, result.isSuccess)
                f.error()
                assertTrue(socket.cancelled)
                assertTrue(runCatching { f.session.narrate("late") {} }.isFailure)
            }
        }
    }

    @Test
    fun goAwayRetiresAfterCurrentTurnWithoutDroppingAudio() = fixture { f ->
        for (name in listOf("goAway", "go_away")) {
            f.ready()
            val received = mutableListOf<Int>()
            val pending = start(f) { received.add(it[0].toInt()) }
            f.socket.message("""{"$name":{"timeLeft":"1s"}}""")
            waitFor { !f.session.reusable }
            assertFalse(f.socket.cancelled)
            assertFalse(pending.isCompleted)
            f.socket.message(audio(1))
            f.socket.message(audio(2))
            f.socket.message(DONE)
            assertEquals("", pending.await().getOrThrow())
            assertEquals(listOf(1, 2), received)
            f.error()
            assertTrue(f.socket.cancelled)
            assertTrue(runCatching { f.session.narrate("late") {} }.isFailure)
        }
    }

    @Test
    fun idleGoAwayAndTransportEndRetireImmediately() = fixture { f ->
        for (payload in listOf("""{"goAway":{}}""", """{"go_away":{}}""", null)) {
            f.ready()
            if (payload == null) f.socket.end("failure") else f.socket.message(payload)
            f.error()
            assertFalse(f.session.reusable)
            assertTrue(f.socket.cancelled)
        }
    }

    @Test
    fun cancellationDiscardsQueueAndCloseWaitsForInFlightSink() = fixture { f ->
        f.ready()
        val gate = f.gate()
        val calls = AtomicInteger()
        val pending = start(f) {
            calls.incrementAndGet()
            gate.block()
        }
        f.socket.message(audio(1))
        gate.awaitEntry()
        repeat(20) { f.socket.message(audio(2)) }
        pending.cancel()
        withTimeout(2000) { pending.join() }
        assertTrue(f.error().contains("cancelled"))
        val closing = async { f.session.closeAndJoin() }
        delay(30)
        assertFalse(closing.isCompleted)
        gate.release.countDown()
        withTimeout(2000) { closing.await() }
        assertEquals(1, calls.get())
        assertFalse(f.session.reusable)
        assertTrue(runCatching { f.session.connect("key", "read", "model") }.isFailure)
    }

    @Test
    fun replacementAndStopIgnoreEveryStaleCallback() = fixture { f ->
        f.ready()
        val old = f.socket
        val gate = f.gate()
        val calls = AtomicInteger()
        val replaced = start(f) { calls.incrementAndGet(); gate.block() }
        old.message(audio(1))
        gate.awaitEntry()
        old.message(audio(2))
        f.ready()
        assertTrue(replaced.await().isFailure)
        val pending = start(f)
        old.open()
        old.message("""{"setupComplete":{}}""")
        old.message(audio(3), binary = true)
        old.message(DONE)
        old.message("""{"goAway":{}}""")
        old.end("failure")
        old.end("closing")
        old.end("closed")
        gate.release.countDown()
        assertTrue(f.session.reusable)
        assertFalse(pending.isCompleted)
        f.socket.message(audio(4))
        f.socket.message(DONE)
        assertEquals("", pending.await().getOrThrow())
        val stopped = start(f)
        f.session.stop()
        assertTrue(stopped.await().isFailure)
        f.socket.open()
        f.socket.message("""{"setupComplete":{}}""")
        f.socket.end("failure")
        assertEquals(ReaderSessionStatus.Idle, f.session.status.value)
        f.session.closeAndJoin()
        assertEquals(1, calls.get())
    }

    @Test
    fun boundedIngressFailsPromptlyEvenWithBlockedSink() = fixture { f ->
        for (kind in listOf("count", "text", "binary", "oversizeText", "oversizeBinary")) {
            f.ready()
            val gate = f.gate()
            val calls = AtomicInteger()
            val pending = start(f) { calls.incrementAndGet(); gate.block() }
            f.socket.message(audio(1))
            gate.awaitEntry()
            when (kind) {
                "count" -> repeat(4096) { f.socket.message("{}") }
                "text", "binary" -> {
                    val payload = """{"padding":"${"x".repeat(400_000)}"}"""
                    repeat(24) { f.socket.message(payload, binary = kind == "binary") }
                }
                "oversizeText" -> f.socket.message("x".repeat(1024 * 1024 + 1))
                else -> f.socket.message("x".repeat(2 * 1024 * 1024 + 1), binary = true)
            }
            assertTrue(withTimeout(2000) { pending.await() }.isFailure)
            val error = f.error()
            assertTrue(error.contains(if (kind.startsWith("oversize")) "exceeds 2 MiB" else "ingress overrun"))
            val highWater = Regex("queueHW=(\\d+)/(\\d+)B").find(error)!!
            assertTrue(highWater.groupValues[1].toInt() <= 4096)
            assertTrue(highWater.groupValues[2].toLong() <= 8L * 1024 * 1024)
            gate.release.countDown()
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun invalidResponsesFailAndDiagnosticsNeverEchoSecrets() = fixture { f ->
        for (payload in listOf(
            "not-json",
            audio(1).replace("AQA=", "!bad!"),
            audio(1).replace("AQA=", "AQ=="),
            audio(1).replace("24000", "16000"),
            """{"error":{"code":429,"message":"$SECRET"}}""",
            """{"serverContent":{"interrupted":true}}""",
            """{"serverContent":{"outputTranscription":{"text":"${"x".repeat(8193)}"}}}""",
        )) {
            f.ready()
            val pending = start(f) { error("Unexpected PCM") }
            f.socket.message(payload)
            assertTrue(pending.await().isFailure)
            f.error()
            assertFalse(f.session.reusable)
        }
        assertTrue(f.logs.none { it.contains(SECRET) || it.contains("https://") })
    }

    @Test
    fun sinkFailurePreservesOriginalExceptionAndSanitizesStatus() = fixture { f ->
        f.ready()
        val failure = IOException(SECRET)
        val pending = start(f) { throw failure }
        f.socket.message(audio(1))
        // The caller's own exception object must survive await(): coroutine stack
        // trace recovery would otherwise substitute a copy of it whenever JVM
        // assertions are enabled, which Gradle's test task does by default.
        assertSame(failure, pending.await().exceptionOrNull())
        val status = f.error()
        assertTrue(status.contains("storage sink failed"))
        assertFalse(status.contains(SECRET))
        assertTrue(f.logs.none { it.contains(SECRET) })
        // A failed sink retires the session; it must not accept another turn.
        assertFalse(f.session.reusable)
        assertTrue(runCatching { f.session.narrate("late") {} }.isFailure)
    }

    @Test
    fun sendFailureRetiresWithoutWaitingForTurnTimeout() = fixture { f ->
        for (throws in listOf(false, true)) {
            f.ready()
            f.socket.reject = !throws
            f.socket.throwOnSend = throws
            val result = withTimeout(2000) { runCatching { f.session.narrate("test") {} } }
            assertTrue(result.isFailure)
            assertTrue(f.error().contains("send_unavailable"))
            assertFalse(f.session.reusable)
        }
    }

    @Test
    fun setupAndTurnTimeoutsAreBoundedIncludingRetirementAndDrain() = fixture(connectMs = 100, turnMs = 300) { f ->
        f.session.connect("key", "read", "model")
        assertTrue(f.error().contains("setup timed out"))
        for (kind in listOf("normal", "goAway", "drain")) {
            f.ready()
            val gate = f.gate()
            val pending = start(f) { gate.block() }
            when (kind) {
                "goAway" -> f.socket.message("""{"goAway":{}}""")
                "drain" -> {
                    f.socket.message(audio(1))
                    gate.awaitEntry()
                    f.socket.message(audio(2))
                    f.socket.end("failure")
                }
            }
            val failure = withTimeout(2000) { pending.await() }.exceptionOrNull()
            assertTrue(failure is GeminiReaderSession.ReaderSessionException)
            assertTrue(failure!!.message!!.contains("too long"))
            assertFalse(f.session.reusable)
            gate.release.countDown()
        }
    }

    @Test
    fun callerTimeoutRemainsCancellationRatherThanSessionTimeout() = fixture { f ->
        f.ready()
        val result = runCatching {
            withTimeout(100) { f.session.narrate("test") {} }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        assertTrue(f.error().contains("cancelled"))
        assertFalse(f.session.reusable)
    }

    @Test
    fun concurrentNarrationIsRejectedWithoutAbortingActiveTurn() = fixture { f ->
        f.ready()
        val first = start(f)
        assertTrue(runCatching { f.session.narrate("second") {} }.isFailure)
        assertFalse(first.isCompleted)
        f.socket.message(audio(1))
        f.socket.message(DONE)
        assertEquals("", first.await().getOrThrow())
        assertTrue(f.session.reusable)
    }

    private fun fixture(
        connectMs: Long = 2000,
        turnMs: Long = 5000,
        block: suspend CoroutineScope.(Fixture) -> Unit,
    ) = runBlocking {
        val fixture = Fixture(connectMs, turnMs)
        try {
            withTimeout(20_000) { coroutineScope { block(fixture) } }
        } finally {
            fixture.gates.forEach { it.release.countDown() }
            fixture.session.closeAndJoin()
        }
    }

    private suspend fun CoroutineScope.start(f: Fixture, sink: (ByteArray) -> Unit = {}): Deferred<Result<String?>> {
        val before = f.socket.sent.size
        val result = async(Dispatchers.Default) {
            try {
                Result.success(f.session.narrate("test", sink))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        waitFor { f.socket.sent.size > before }
        return result
    }

    private class Fixture(connectMs: Long, turnMs: Long) {
        val factory = Factory()
        val session = GeminiReaderSession(factory, connectMs, turnMs)
        val logs = Collections.synchronizedList(mutableListOf<String>())
        val gates = mutableListOf<Gate>()
        val socket: Socket get() = factory.sockets.last()

        init {
            session.onLog = { logs.add(it) }
        }

        suspend fun ready() {
            session.connect(SECRET, "read", "model")
            socket.open()
            socket.message("""{"setupComplete":{}}""")
            withTimeout(2000) { session.status.first { it == ReaderSessionStatus.Ready } }
        }

        suspend fun error(): String = (withTimeout(2000) {
            session.status.first { it is ReaderSessionStatus.Error }
        } as ReaderSessionStatus.Error).message

        fun gate() = Gate().also { gates.add(it) }
    }

    private class Gate {
        private val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun block() {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }

        suspend fun awaitEntry() {
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
        }
    }

    private class Factory : WebSocket.Factory {
        val sockets = mutableListOf<Socket>()

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            Socket(request, listener).also { sockets.add(it) }
    }

    private class Socket(private val request: Request, private val listener: WebSocketListener) : WebSocket {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var cancelled = false
        var reject = false
        var throwOnSend = false

        override fun request() = request
        override fun queueSize() = 0L
        override fun send(text: String): Boolean {
            if (throwOnSend) throw IOException(SECRET)
            if (reject || cancelled) return false
            sent.add(text)
            return true
        }
        override fun send(bytes: ByteString) = send(bytes.utf8())
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }

        fun open() = listener.onOpen(this, Response.Builder().request(request)
            .protocol(Protocol.HTTP_1_1).code(101).message("test").build())

        fun message(text: String, binary: Boolean = false) {
            if (binary) listener.onMessage(this, text.encodeUtf8()) else listener.onMessage(this, text)
        }

        fun end(kind: String) {
            when (kind) {
                "failure" -> listener.onFailure(this, IOException(SECRET), null)
                "closing" -> listener.onClosing(this, 1000, SECRET)
                else -> listener.onClosed(this, 1000, SECRET)
            }
        }
    }

    companion object {
        private const val SECRET = "fake-secret-key"
        private const val DONE = """{"serverContent":{"turnComplete":true}}"""

        private fun audio(value: Int): String {
            val data = java.util.Base64.getEncoder().encodeToString(byteArrayOf(value.toByte(), 0))
            return """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$data"}}]}}}"""
        }

        private suspend fun waitFor(predicate: () -> Boolean) = withTimeout(2000) {
            while (!predicate()) delay(1)
        }
    }
}
