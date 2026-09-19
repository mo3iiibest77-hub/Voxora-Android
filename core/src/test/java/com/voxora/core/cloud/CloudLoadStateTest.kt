package com.voxora.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one property the Cloud lists depend on: an empty answer and a refused one are different
 * screens.
 *
 * "You have no projects" and "we were not allowed to look" look identical if both collapse to an
 * empty list, and only one of them is the user's problem to fix. These tests keep the mapping from
 * [CloudResult] to [CloudLoadState] honest, including the unreadable-answer case, which must never
 * be reported as a successful empty read.
 */
class CloudLoadStateTest {

    @Test
    fun anEmptyCollectionIsEmptyNotLoaded() {
        assertEquals(CloudLoadState.EMPTY, CloudLoadState.of(CloudResult.Success(emptyList<CloudProject>())))
        assertEquals(CloudLoadState.EMPTY, CloudLoadState.of(CloudResult.Success(emptyList<CloudApiKey>())))
    }

    @Test
    fun aNonEmptyCollectionIsLoaded() {
        val projects = listOf(CloudProject("alpha-123", "111", "Alpha", "ACTIVE"))
        assertEquals(CloudLoadState.LOADED, CloudLoadState.of(CloudResult.Success(projects)))
    }

    @Test
    fun aScalarSuccessIsLoadedRatherThanEmpty() {
        // A non-collection payload has no notion of "nothing", so it is never EMPTY.
        assertEquals(CloudLoadState.LOADED, CloudLoadState.of(CloudResult.Success(42)))
    }

    @Test
    fun eachFailureKeepsItsOwnState() {
        assertEquals(CloudLoadState.NOT_AUTHORIZED, CloudLoadState.of(CloudResult.NotAuthorized))
        assertEquals(CloudLoadState.PERMISSION_DENIED, CloudLoadState.of(CloudResult.PermissionDenied))
        assertEquals(CloudLoadState.NETWORK_UNAVAILABLE, CloudLoadState.of(CloudResult.NetworkUnavailable))
        assertEquals(CloudLoadState.FAILED, CloudLoadState.of(CloudResult.Failed(statusCode = 500)))
    }

    @Test
    fun anUnreadableAnswerIsFailedEvenWhenItArrivedOverHttp() {
        // A 2xx that we could not parse is still a failure, not a successful empty list.
        val state = CloudLoadState.of(CloudResult.Failed(statusCode = 200))
        assertEquals(CloudLoadState.FAILED, state)
        assertNotEquals(CloudLoadState.EMPTY, state)
    }

    @Test
    fun emptyAndRefusedAreNeverTheSameState() {
        val empty = CloudLoadState.of(CloudResult.Success(emptyList<CloudProject>()))
        val refused = CloudLoadState.of(CloudResult.PermissionDenied)
        assertNotEquals(empty, refused)
        assertNotEquals(empty, CloudLoadState.NOT_AUTHORIZED)
    }

    @Test
    fun idleIsNotProducedByAnyCompletedRead() {
        val completed = listOf(
            CloudLoadState.of(CloudResult.Success(emptyList<CloudProject>())),
            CloudLoadState.of(CloudResult.Success(listOf(CloudProject("p", "1", "P", "ACTIVE")))),
            CloudLoadState.of(CloudResult.NotAuthorized),
            CloudLoadState.of(CloudResult.PermissionDenied),
            CloudLoadState.of(CloudResult.NetworkUnavailable),
            CloudLoadState.of(CloudResult.Failed(null)),
        )
        assertTrue(completed.none { it == CloudLoadState.IDLE })
        assertTrue(completed.none { it == CloudLoadState.LOADING })
    }
}
