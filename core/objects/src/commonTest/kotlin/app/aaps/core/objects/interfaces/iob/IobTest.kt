package app.aaps.core.objects.interfaces.iob

import app.aaps.core.data.iob.Iob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * In `commonTest` rather than `androidHostTest`: [Iob] is shared code, and its equality is what the
 * IOB totals are compared with, so it is worth checking on every platform that gets it.
 */
class IobTest {

    private fun Iob.iobContrib(iobContrib: Double): Iob {
        this.iobContrib = iobContrib
        return this
    }

    private fun Iob.activityContrib(activityContrib: Double): Iob {
        this.activityContrib = activityContrib
        return this
    }

    @Test fun equalTest() {
        val a1 = Iob().iobContrib(1.0).activityContrib(2.0)
        val a2 = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        assertEquals(a1, a1)
        assertEquals(a1, a2)
        assertNotEquals(b, a1)
        assertNotNull(a1)
        assertNotEquals(Any(), a1)
    }

    @Test fun hashCodeTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        assertNotEquals(0L, a.hashCode().toLong())
    }
}
