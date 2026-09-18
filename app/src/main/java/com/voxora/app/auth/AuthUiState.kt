package com.voxora.app.auth

/**
 * Why a Google sign-in attempt did not succeed.
 *
 * These are kept separate because the user's next action differs for each one, and because
 * "Google is not configured in this build" must never be presented as "your sign-in failed".
 * A configuration gap is the owner's to fix; a cancellation is the user's choice; a network
 * error is worth retrying.
 *
 * Pure JVM so the classification stays unit-testable without Android or the credentials library.
 */
enum class AuthFailure {
    /** No usable `default_web_client_id` is configured in this build. Not the user's fault. */
    CONFIGURATION_MISSING,

    /** The user dismissed the account picker. Not an error worth shouting about. */
    CANCELLED,

    /** The device has no Google account the picker could offer. */
    NO_CREDENTIAL,

    /** The credential provider is unavailable, usually Play Services missing or out of date. */
    PROVIDER_UNAVAILABLE,

    /** A transient connectivity problem. Retrying is reasonable. */
    NETWORK,

    /** A credential arrived that is not a Google ID token. */
    UNSUPPORTED_CREDENTIAL,

    /** Anything else, reported honestly as unclassified rather than guessed at. */
    UNKNOWN,
}

/**
 * The account card's state.
 *
 * Modelled as a sealed hierarchy rather than a bag of booleans so impossible combinations
 * cannot be represented — there is no way to be simultaneously signing in and signed out, and
 * no way to hold a stale email while signed out.
 */
sealed interface AuthUiState {

    /** Signed out and idle. */
    data object SignedOut : AuthUiState

    /** A sign-in is in flight. The UI must show progress and refuse a second launch. */
    data object SigningIn : AuthUiState

    /** Signed in. Both values are non-blank. */
    data class SignedIn(val email: String, val displayName: String) : AuthUiState

    /** A sign-out is in flight. */
    data object SigningOut : AuthUiState

    /** The last attempt failed, with a classified reason. */
    data class Failed(val reason: AuthFailure) : AuthUiState

    /** True while an operation is running, so buttons can be disabled and taps ignored. */
    val isBusy: Boolean get() = this is SigningIn || this is SigningOut

    /** True only when a real account is currently displayed. */
    val isSignedIn: Boolean get() = this is SignedIn

    /**
     * The email to display, or empty when not signed in.
     *
     * Reading it from the state rather than from a separate variable is what stops a stale
     * email surviving a sign-out: there is no `SignedOut` state that carries one.
     */
    val emailOrEmpty: String get() = (this as? SignedIn)?.email.orEmpty()

    /** The display name to show, falling back to the email so the card is never blank. */
    val nameOrEmpty: String
        get() = (this as? SignedIn)?.let { it.displayName.ifBlank { it.email } }.orEmpty()
}

/**
 * Maps an exception onto [AuthFailure] from its type name and message.
 *
 * The credential exceptions live in the Android credentials library, which cannot be loaded on
 * a plain JVM. Classifying by simple name keeps this logic testable — and it is the *only*
 * place the mapping lives, so the helper itself stays a thin wrapper.
 */
object AuthFailureClassifier {

    fun classify(exceptionTypeName: String?, message: String?): AuthFailure {
        val type = exceptionTypeName.orEmpty().substringAfterLast('.').lowercase()
        val text = message.orEmpty().lowercase()
        return when {
            "cancellation" in type || "canceled" in text || "cancelled" in text -> AuthFailure.CANCELLED
            "nocredential" in type || "no credential" in text -> AuthFailure.NO_CREDENTIAL
            "providerconfiguration" in type || "unsupported" in type -> AuthFailure.PROVIDER_UNAVAILABLE
            "interrupted" in type || "network" in text || "timeout" in text || "timed out" in text ||
                "unavailable" in text -> AuthFailure.NETWORK
            "novalidation" in type || "invalid" in text -> AuthFailure.UNSUPPORTED_CREDENTIAL
            else -> AuthFailure.UNKNOWN
        }
    }
}
