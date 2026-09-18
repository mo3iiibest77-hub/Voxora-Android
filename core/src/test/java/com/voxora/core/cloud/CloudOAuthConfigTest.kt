package com.voxora.core.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for "is Google sign-in configured in this build?".
 *
 * The reported defect this pins: the card told the owner that sign-in was not configured, and the
 * shipped placeholder made that permanent — but the app also had no way to *prove* the distinction
 * off-device. The rule now lives in a pure-JVM object, so "missing external configuration" is a
 * tested state rather than a claim.
 *
 * The rule is deliberately about presence, not validity: a malformed client ID is Google's answer to
 * give, and pretending to validate it here would turn an honest "not configured" into a misleading
 * "authorization failed".
 */
class CloudOAuthConfigTest {

    @Test
    fun theShippedPlaceholderIsNotConfigured() {
        assertFalse(CloudOAuthConfig.isConfigured("REPLACE_WITH_GOOGLE_WEB_CLIENT_ID"))
    }

    @Test
    fun anyReplacePlaceholderIsNotConfigured() {
        assertFalse(CloudOAuthConfig.isConfigured("REPLACE_ME"))
        assertFalse(CloudOAuthConfig.isConfigured("replace_with_something"))
        assertFalse(CloudOAuthConfig.isConfigured("  REPLACE_  "))
    }

    @Test
    fun anAbsentOrBlankValueIsNotConfigured() {
        assertFalse(CloudOAuthConfig.isConfigured(null))
        assertFalse(CloudOAuthConfig.isConfigured(""))
        assertFalse(CloudOAuthConfig.isConfigured("   "))
        assertFalse(CloudOAuthConfig.isConfigured("\n\t"))
    }

    /**
     * A value that is not a placeholder counts as present even if it is not a shape this app
     * recognises: only Google can say whether a client ID is valid.
     */
    @Test
    fun anyNonPlaceholderValueCountsAsPresent() {
        assertTrue(CloudOAuthConfig.isConfigured("1234567890-abc.apps.googleusercontent.com"))
        assertTrue(CloudOAuthConfig.isConfigured("  1234567890-abc.apps.googleusercontent.com  "))
        assertTrue(CloudOAuthConfig.isConfigured("something-else-entirely"))
    }

    /**
     * The prefix is checked case-insensitively, so a differently-cased placeholder cannot slip
     * through and be sent to Google as if it were a client ID.
     */
    @Test
    fun thePlaceholderCheckIgnoresCase() {
        assertFalse(CloudOAuthConfig.isConfigured("replace_with_google_web_client_id"))
        assertFalse(CloudOAuthConfig.isConfigured("RePlAcE_x"))
    }
}
