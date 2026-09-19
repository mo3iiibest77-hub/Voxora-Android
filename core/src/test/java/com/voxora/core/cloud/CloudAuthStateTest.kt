package com.voxora.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Cloud authorization state and the token-expiry rule.
 *
 * Two properties matter: the access token is never part of UI state, and an expired grant is
 * treated as unusable rather than sent and failed.
 */
class CloudAuthStateTest {

    // ---- token policy ---------------------------------------------------------------

    @Test
    fun aGrantWithNoStatedExpiryIsUsable() {
        assertTrue(CloudTokenPolicy.isUsable(expiresAtMillis = null, nowMillis = 1_000L))
    }

    @Test
    fun aGrantWellInTheFutureIsUsable() {
        assertTrue(CloudTokenPolicy.isUsable(expiresAtMillis = 1_000_000L, nowMillis = 1_000L))
    }

    @Test
    fun anExpiredGrantIsNotUsable() {
        assertFalse(CloudTokenPolicy.isUsable(expiresAtMillis = 1_000L, nowMillis = 2_000L))
    }

    /** A token about to expire must be refreshed rather than used and failed. */
    @Test
    fun aGrantInsideTheSkewWindowIsNotUsable() {
        val expiry = 100_000L
        assertFalse(
            CloudTokenPolicy.isUsable(
                expiresAtMillis = expiry,
                nowMillis = expiry - CloudTokenPolicy.DEFAULT_SKEW_MILLIS + 1,
            ),
        )
        assertTrue(
            CloudTokenPolicy.isUsable(
                expiresAtMillis = expiry,
                nowMillis = expiry - CloudTokenPolicy.DEFAULT_SKEW_MILLIS - 1,
            ),
        )
    }

    @Test
    fun theSkewCanBeOverridden() {
        assertTrue(CloudTokenPolicy.isUsable(expiresAtMillis = 1_000L, nowMillis = 999L, skewMillis = 0L))
        assertFalse(CloudTokenPolicy.isUsable(expiresAtMillis = 1_000L, nowMillis = 999L, skewMillis = 10L))
    }

    // ---- state shape ----------------------------------------------------------------

    @Test
    fun onlyAuthorizedCarriesAnAccount() {
        assertEquals(
            "a@example.com",
            CloudAuthState.Authorized("a@example.com", 1L).accountEmailOrNull,
        )
        assertNull(CloudAuthState.SignedOut.accountEmailOrNull)
        assertNull(CloudAuthState.Authorizing.accountEmailOrNull)
        assertNull(CloudAuthState.NotConfigured.accountEmailOrNull)
        assertNull(CloudAuthState.Failed(CloudAuthFailure.NETWORK).accountEmailOrNull)
    }

    @Test
    fun onlyAnInFlightAuthorizationIsBusy() {
        assertTrue(CloudAuthState.Authorizing.isBusy)
        assertFalse(CloudAuthState.SignedOut.isBusy)
        assertFalse(CloudAuthState.Authorized("a@example.com", null).isBusy)
        assertFalse(CloudAuthState.Failed(CloudAuthFailure.CANCELLED).isBusy)
    }

    @Test
    fun onlyARealGrantCountsAsAuthorized() {
        assertTrue(CloudAuthState.Authorized("a@example.com", null).isAuthorized)
        assertFalse(CloudAuthState.SignedOut.isAuthorized)
        assertFalse(CloudAuthState.NotConfigured.isAuthorized)
        assertFalse(CloudAuthState.Failed(CloudAuthFailure.PERMISSION_DENIED).isAuthorized)
    }

    /**
     * The state type must not be able to carry a token. The token lives in the authorizer, and
     * `Authorized` exposes only an identity and an expiry, so there is nothing here to log or
     * persist. This pins the shape the UI is allowed to read.
     */
    @Test
    fun authorizedCarriesOnlyAnIdentityAndAnExpiry() {
        val authorized = CloudAuthState.Authorized("a@example.com", 42L)
        assertEquals("a@example.com", authorized.accountEmail)
        assertEquals(42L, authorized.expiresAtMillis)
        assertNull(CloudAuthState.SignedOut.accountEmailOrNull)
    }

    /**
     * The four states the account card renders must stay mutually exclusive.
     *
     * The card branches on exactly these, and each one needs a different action. In particular
     * `NotConfigured` is about the **build**, not the user: it is not a failure and not a
     * signed-out session, and collapsing it into either is what made a supported feature read as
     * "sign-in is not implemented".
     */
    @Test
    fun theFourUserFacingStatesAreMutuallyExclusive() {
        val states: List<CloudAuthState> = listOf(
            CloudAuthState.NotConfigured,
            CloudAuthState.SignedOut,
            CloudAuthState.Authorizing,
            CloudAuthState.Authorized("a@example.com", null),
        )

        // Exactly one can be a real grant and exactly one can be in flight, so a card that asks
        // "authorized?" and "busy?" can never light up two branches at once.
        assertEquals(1, states.count { it.isAuthorized })
        assertEquals(1, states.count { it.isBusy })

        // Configuration missing carries no account and is not authorized: it is its own state.
        assertNull(CloudAuthState.NotConfigured.accountEmailOrNull)
        assertFalse(CloudAuthState.NotConfigured.isAuthorized)
        assertFalse(CloudAuthState.NotConfigured.isBusy)

        // Signed out is the configured-but-not-connected state, and is distinct from a failure.
        assertNull(CloudAuthState.SignedOut.accountEmailOrNull)
        assertFalse(CloudAuthState.SignedOut.isAuthorized)
        assertFalse(CloudAuthState.SignedOut.isBusy)
    }

    @Test
    fun configurationMissingIsDistinctFromEveryOtherFailure() {
        val reasons = CloudAuthFailure.entries
        assertEquals(reasons.size, reasons.toSet().size)
        assertTrue(CloudAuthFailure.CONFIGURATION_MISSING in reasons)
        assertTrue(CloudAuthFailure.PERMISSION_DENIED in reasons)
        assertTrue(CloudAuthFailure.CANCELLED in reasons)
    }

    /**
     * The scope set is pinned rather than pattern-matched: adding a scope must be a deliberate
     * decision, and every entry is read-only because Voxora never writes to Google Cloud.
     */
    @Test
    fun theScopeSetIsExactlyTheReadOnlySetVoxoraNeeds() {
        assertEquals(
            listOf(
                CloudScopes.CLOUD_PLATFORM_READ_ONLY,
                CloudScopes.MONITORING_READ,
                CloudScopes.USERINFO_EMAIL,
            ),
            CloudScopes.ALL,
        )
        assertEquals(CloudScopes.ALL.size, CloudScopes.ALL.toSet().size)
        assertTrue(CloudScopes.ALL.all { it.startsWith("https://www.googleapis.com/auth/") })
        assertTrue(
            CloudScopes.ALL.none {
                it.contains("write") || it.endsWith(".full") || it.endsWith("/cloud-platform")
            },
        )
    }
}
