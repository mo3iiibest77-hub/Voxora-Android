package com.voxora.core.cloud

/**
 * Why Google Cloud authorization is not currently usable.
 *
 * These are kept apart because the user's next action differs for each, and because
 * "this build has no OAuth client" must never be shown as "your sign-in failed". Pure JVM so the
 * classification stays unit-testable.
 */
enum class CloudAuthFailure {
    /** No OAuth client ID is configured in this build. Not the user's fault. */
    CONFIGURATION_MISSING,

    /** The user dismissed the consent screen. Not an error worth shouting about. */
    CANCELLED,

    /** The device has no Google account the picker could offer. */
    NO_ACCOUNT,

    /** The authorization provider is unavailable, usually Play Services missing or out of date. */
    PROVIDER_UNAVAILABLE,

    /** A transient connectivity problem. Retrying is reasonable. */
    NETWORK,

    /** Google refused the requested scopes. */
    PERMISSION_DENIED,

    /** Google answered but not in a shape we can use. */
    UNSUPPORTED,

    /** Anything else, reported honestly rather than guessed at. */
    UNKNOWN,
}

/**
 * The state of Voxora's Google Cloud authorization.
 *
 * Modelled as a sealed hierarchy so impossible combinations cannot be represented: there is no
 * way to be authorizing and authorized at once, and no way to hold an account while signed out.
 *
 * **The access token is deliberately not part of this type.** It is a bearer credential for the
 * user's whole Cloud read scope; keeping it out of UI state is what stops it reaching a log, a
 * saved-state bundle or a crash report. The authorizer holds it in memory only.
 */
sealed interface CloudAuthState {

    /** This build has no usable OAuth client ID, so authorization cannot even be attempted. */
    data object NotConfigured : CloudAuthState

    /** Configured, but no Google account is authorized yet. */
    data object SignedOut : CloudAuthState

    /** The consent screen is open. The UI must show progress and refuse a second launch. */
    data object Authorizing : CloudAuthState

    /**
     * An account has granted the Cloud scopes.
     *
     * [expiresAtMillis] is the access token's expiry, or null when Google did not state one;
     * either way the token itself lives in the authorizer, not here.
     */
    data class Authorized(
        val accountEmail: String?,
        val expiresAtMillis: Long?,
    ) : CloudAuthState

    /** The last attempt failed, with a classified reason. */
    data class Failed(val reason: CloudAuthFailure) : CloudAuthState

    /** True while an authorization is in flight, so buttons can be disabled and taps ignored. */
    val isBusy: Boolean get() = this is Authorizing

    /** True only when a real Cloud grant is currently held. */
    val isAuthorized: Boolean get() = this is Authorized

    /** The account email, or null when not authorized. Never a stale value from a past grant. */
    val accountEmailOrNull: String? get() = (this as? Authorized)?.accountEmail
}

/**
 * Whether a grant is still usable, and when it stops being so.
 *
 * Access tokens are short-lived. A grant with no stated expiry is treated as usable — Google
 * normally states one, and refusing to try would break the cases where it does not. A grant that
 * has expired, or is within [skewMillis] of expiring, must be re-authorized rather than used and
 * failed.
 */
object CloudTokenPolicy {

    /** Treat a token as expired this long before it actually is, to absorb clock and call latency. */
    const val DEFAULT_SKEW_MILLIS: Long = 60_000L

    fun isUsable(
        expiresAtMillis: Long?,
        nowMillis: Long,
        skewMillis: Long = DEFAULT_SKEW_MILLIS,
    ): Boolean = expiresAtMillis == null || nowMillis < expiresAtMillis - skewMillis
}
