package com.voxora.app.dub

import com.voxora.app.dub.sync.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Live Dub status-indicator contract.
 *
 * The rule that matters most: **an unavailable sync is not an error.** "Audio only" and "sync
 * unavailable" are honest limitations of the device or the source app, so they are neutral and
 * must never be coloured like a failure.
 */
class SyncStatusVisualTest {

    @Test
    fun `only a synced pipeline is active`() {
        assertEquals(SyncTone.ACTIVE, SyncStatusVisual.tone(SyncState.SYNCED))
        for (state in SyncState.entries) {
            if (state != SyncState.SYNCED) {
                assertTrue(
                    "$state must not be active",
                    SyncStatusVisual.tone(state) != SyncTone.ACTIVE,
                )
            }
        }
    }

    @Test
    fun `measuring and catching up are in-progress, not success`() {
        assertEquals(SyncTone.WARNING, SyncStatusVisual.tone(SyncState.WARMING_UP))
        assertEquals(SyncTone.WARNING, SyncStatusVisual.tone(SyncState.CORRECTING))
    }

    @Test
    fun `an unavailable or audio-only sync is neutral, never a failure`() {
        assertEquals(SyncTone.NEUTRAL, SyncStatusVisual.tone(SyncState.AUDIO_ONLY))
        assertEquals(SyncTone.NEUTRAL, SyncStatusVisual.tone(SyncState.UNAVAILABLE))
        assertEquals(SyncTone.NEUTRAL, SyncStatusVisual.tone(SyncState.IDLE))
    }

    @Test
    fun `every state has a tone`() {
        for (state in SyncState.entries) {
            SyncStatusVisual.tone(state)
        }
    }

    @Test
    fun `the video limitation is explained only once the pipeline is synced`() {
        assertTrue(SyncStatusVisual.showsVideoNote(SyncState.SYNCED))
        for (state in SyncState.entries) {
            if (state != SyncState.SYNCED) {
                assertFalse("$state must not show the note", SyncStatusVisual.showsVideoNote(state))
            }
        }
    }

    @Test
    fun `the media-control opt-in is offered only while live and without the grant`() {
        assertTrue(SyncStatusVisual.showsPermissionPrompt(live = true, granted = false))
        assertFalse(SyncStatusVisual.showsPermissionPrompt(live = true, granted = true))
        assertFalse(SyncStatusVisual.showsPermissionPrompt(live = false, granted = false))
        assertFalse(SyncStatusVisual.showsPermissionPrompt(live = false, granted = true))
    }
}
