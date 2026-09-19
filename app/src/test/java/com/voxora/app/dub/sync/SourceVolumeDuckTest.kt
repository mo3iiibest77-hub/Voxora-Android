package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duck/restore arithmetic behind the source-volume contract.
 *
 * The product promise is narrow and absolute: while Live Dub plays, the source is quieter but
 * still audible, and when the session ends the user's own volume is exactly what it was. Both
 * halves are decided here so they can be checked without a device.
 */
class SourceVolumeDuckTest {

    @Test
    fun `the source is reduced but never muted`() {
        val target = SourceVolumeDuck.duckTarget(current = 10, max = 15)
        assertEquals(2, target)
        assertTrue(target!! >= 1)
        assertTrue(target < 10)
    }

    @Test
    fun `a low level still leaves one step of audible source`() {
        // 3 * 28 / 100 == 0, which would be silence; the rule floors it at 1.
        assertEquals(1, SourceVolumeDuck.duckTarget(current = 3, max = 15))
        assertEquals(1, SourceVolumeDuck.duckTarget(current = 2, max = 15))
    }

    @Test
    fun `there is nothing to duck into at the bottom of the range`() {
        assertNull(SourceVolumeDuck.duckTarget(current = 1, max = 15))
        assertNull(SourceVolumeDuck.duckTarget(current = 0, max = 15))
    }

    @Test
    fun `an unusable range is left alone`() {
        assertNull(SourceVolumeDuck.duckTarget(current = 5, max = 0))
        assertNull(SourceVolumeDuck.duckTarget(current = 5, max = 3))
    }

    @Test
    fun `restoring returns exactly the saved level`() {
        assertEquals(7, SourceVolumeDuck.restoreTarget(saved = 7))
        assertEquals(0, SourceVolumeDuck.restoreTarget(saved = 0))
        assertNull(SourceVolumeDuck.restoreTarget(saved = -1))
    }

    @Test
    fun `every duckable level produces a strictly lower, positive level`() {
        for (current in 2..15) {
            val target = SourceVolumeDuck.duckTarget(current, 15)
            assertTrue("current=$current must duck", target != null)
            assertTrue("current=$current target=$target", target!! in 1 until current)
        }
    }
}
