package com.voxora.core.cloud

/**
 * How a Cloud list or figure is currently presented.
 *
 * An empty result and a refused one are different screens and must stay distinguishable: "you have
 * no projects" is not "we were not allowed to look". Pure JVM so the mapping is unit-testable.
 */
enum class CloudLoadState {
    /** Nothing has been requested yet. */
    IDLE,

    /** A request is in flight. */
    LOADING,

    /** Google answered with at least one item. */
    LOADED,

    /** Google answered and there genuinely is nothing. */
    EMPTY,

    /** No Cloud grant is held, so nothing could be read. */
    NOT_AUTHORIZED,

    /** The account is authorized but not allowed to read this. */
    PERMISSION_DENIED,

    /** The request never reached Google. */
    NETWORK_UNAVAILABLE,

    /** Google answered with something unusable. */
    FAILED,
    ;

    companion object {
        /** Maps one completed read onto its presentation state. */
        fun of(result: CloudResult<*>): CloudLoadState = when (result) {
            is CloudResult.Success -> {
                val value = result.value
                when {
                    value is Collection<*> -> if (value.isEmpty()) EMPTY else LOADED
                    else -> LOADED
                }
            }
            CloudResult.NotAuthorized -> NOT_AUTHORIZED
            CloudResult.PermissionDenied -> PERMISSION_DENIED
            CloudResult.NetworkUnavailable -> NETWORK_UNAVAILABLE
            is CloudResult.Failed -> FAILED
        }
    }
}
