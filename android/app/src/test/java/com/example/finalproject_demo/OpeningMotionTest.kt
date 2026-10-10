package com.example.finalproject_demo

import com.example.finalproject_demo.ui.shell.OpeningMotion
import org.junit.Assert.*
import org.junit.Test

class OpeningMotionTest {
    @Test fun aSmallOrUpwardPullDoesNotStartTheShow() {
        assertFalse(OpeningMotion.canRelease(-100f))
        assertFalse(OpeningMotion.canRelease(20f))
        assertTrue(OpeningMotion.canRelease(70f))
        assertTrue(OpeningMotion.pull(10000f) <= 220f)
    }

    @Test fun theTimelineWaitsForTheActualVoiceCompletion() {
        assertEquals(2920f, OpeningMotion.advance(2910f, 40f, false), 0f)
        assertEquals(2920f, OpeningMotion.advance(2920f, 10000f, false), 0f)
        assertEquals(2960f, OpeningMotion.advance(2920f, 40f, true), 0f)
        assertEquals(OpeningMotion.duration, OpeningMotion.advance(9750f, 200f, true), 0f)
    }

    @Test fun allHopsLandContinuouslyAtTheRealRoomHome() {
        val start = OpeningMotion.Point(710f, 340f, .2f)
        val end = OpeningMotion.Point(400f, 570f, .8f)
        val before = OpeningMotion.cat(4400f, start, end)
        assertEquals(start.x, before.x, .001f)
        assertEquals(start.y, before.y, .001f)
        val last = OpeningMotion.cat(9800f, start, end)
        assertEquals(end.x, last.x, .001f)
        assertEquals(end.y, last.y, .001f)
        assertEquals(end.scale, last.scale, .001f)
        var previous = before
        for (ms in 4401..9800) {
            val frame = OpeningMotion.cat(ms.toFloat(), start, end)
            assertTrue(frame.scale > 0f)
            assertTrue(kotlin.math.abs(frame.x - previous.x) < 2f)
            assertTrue(kotlin.math.abs(frame.y - previous.y) < 2f)
            previous = frame
        }
        assertEquals(4, OpeningMotion.hops.size)
    }
}
