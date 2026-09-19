package com.voxora.core.cloud

/**
 * The outcome of one authorized Google Cloud read.
 *
 * A refusal and a failure are different things and must never be collapsed: "you do not have
 * permission for this project" is a stable, actionable answer, while a network problem is worth
 * retrying. Nothing here carries a fabricated value — an unsuccessful read has no payload at all,
 * which is what stops a caller from rendering a zero for it.
 */
sealed interface CloudResult<out T> {

    /** Google answered and the payload was readable. An empty list is a real, honest answer. */
    data class Success<T>(val value: T) : CloudResult<T>

    /** No Cloud grant is held, so no call could be made. */
    data object NotAuthorized : CloudResult<Nothing>

    /** The account is authorized but is not allowed to read this resource. */
    data object PermissionDenied : CloudResult<Nothing>

    /** The request never reached Google. */
    data object NetworkUnavailable : CloudResult<Nothing>

    /** Google answered with something unusable. [statusCode] is present when it came from HTTP. */
    data class Failed(val statusCode: Int?) : CloudResult<Nothing>

    val valueOrNull: T? get() = (this as? Success)?.value
}
