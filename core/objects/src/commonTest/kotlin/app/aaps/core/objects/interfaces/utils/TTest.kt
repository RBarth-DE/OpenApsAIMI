package app.aaps.core.objects.interfaces.utils

import app.aaps.core.data.time.T
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest` rather than `androidHostTest`: [T] is shared code, and arithmetic on it is what
 * every timestamp in the app is built from.
 *
 * The three tests that took the wall clock now start from a fixed millisecond value. The assertions
 * were always relative to it - the value itself was never asserted - so the result is the same, and
 * the test no longer depends on what time it is run.
 */
@Suppress("SpellCheckingInspection")
class TTest {

    @Test fun toUnits() {
        assertEquals(1, T.msecs(1000).secs())
        assertEquals(1, T.secs(60).mins())
        assertEquals(1, T.mins(60).hours())
        assertEquals(1, T.hours(24).days())
        assertEquals(24, T.days(1).hours())
        assertEquals(60000, T.mins(1).msecs())
    }

    @Test fun additions() {
        val nowMsecs = 1656358822000L
        val now = T.msecs(nowMsecs)
        assertEquals(nowMsecs + 5 * 1000, now.plus(T.secs(5)).msecs())
        assertEquals(nowMsecs + 5 * 60 * 1000, now.plus(T.mins(5)).msecs())
        assertEquals(nowMsecs + 5 * 60 * 60 * 1000, now.plus(T.hours(5)).msecs())
        assertEquals(nowMsecs + 5 * 24 * 60 * 60 * 1000, now.plus(T.days(5)).msecs())
    }

    @Test fun subtractions() {
        val nowMsecs = 1656358822000L
        val now = T.msecs(nowMsecs)
        assertEquals(nowMsecs - 5 * 1000, now.minus(T.secs(5)).msecs())
        assertEquals(nowMsecs - 5 * 60 * 1000, now.minus(T.mins(5)).msecs())
        assertEquals(nowMsecs - 5 * 60 * 60 * 1000, now.minus(T.hours(5)).msecs())
        assertEquals(nowMsecs - 5 * 24 * 60 * 60 * 1000, now.minus(T.days(5)).msecs())
    }
}
