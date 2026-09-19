package com.voxora.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for showing an API key without revealing it.
 *
 * The key is a secret, so the only thing the UI is allowed to render is a mask. These tests pin
 * that the mask never contains the middle of the key, never varies its length with the key, and
 * never presents the shipped placeholder as though a key were configured.
 */
class ApiKeyMaskTest {

    private val bullets = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
    private val placeholder = "REPLACE_WITH_GOOGLE_WEB_CLIENT_ID"

    @Test
    fun aLongKeyShowsOnlyItsFirstAndLastFourCharacters() {
        val key = "AIzaSyD1234567890ABCD"
        assertEquals("AIza" + bullets + "ABCD", ApiKeyMask.mask(key))
    }

    @Test
    fun theMaskNeverContainsTheMiddleOfTheKey() {
        val key = "AIzaSyD1234567890ABCD"
        val mask = ApiKeyMask.mask(key)
        assertFalse("the mask must not contain the key's body", mask.contains("SyD1234567890"))
        assertFalse("the mask must not equal the key", mask == key)
    }

    @Test
    fun theMaskLengthDoesNotRevealTheKeyLength() {
        val short = ApiKeyMask.mask("AIzaSyD1234ABCD")
        val long = ApiKeyMask.mask("AIza" + "x".repeat(200) + "ABCD")
        assertEquals("the bullet run must be fixed, not proportional", short.length, long.length)
    }

    @Test
    fun aKeyTooShortToMaskSafelyIsFullyHidden() {
        assertEquals(bullets, ApiKeyMask.mask("AIzaSyD1"))
        assertEquals(bullets, ApiKeyMask.mask("short"))
    }

    @Test
    fun nothingIsShownWhenNoUsableKeyExists() {
        assertEquals("", ApiKeyMask.mask(null))
        assertEquals("", ApiKeyMask.mask(""))
        assertEquals("", ApiKeyMask.mask("   "))
        assertEquals("", ApiKeyMask.mask(placeholder))
    }

    @Test
    fun theShippedPlaceholderIsNotAKey() {
        assertFalse(ApiKeyMask.isConfigured(placeholder))
        assertFalse(ApiKeyMask.isConfigured("REPLACE_SOMETHING_ELSE"))
        assertFalse(ApiKeyMask.isConfigured(""))
        assertFalse(ApiKeyMask.isConfigured("   "))
        assertFalse(ApiKeyMask.isConfigured(null))
    }

    @Test
    fun aRealLookingKeyIsConfigured() {
        assertTrue(ApiKeyMask.isConfigured("AIzaSyD1234567890ABCD"))
        assertTrue(ApiKeyMask.isConfigured("  AIzaSyD1234567890ABCD  "))
    }
}
