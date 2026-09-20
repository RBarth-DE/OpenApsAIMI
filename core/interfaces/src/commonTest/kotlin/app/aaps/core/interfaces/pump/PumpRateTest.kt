package app.aaps.core.interfaces.pump

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PumpRateTest {

    @Test
    fun `iU absolute with U100 returns same value`() {
        val pr = PumpRate(2.0)
        assertEquals(2.0, pr.iU(1.0, isAbsolute = true))
    }

    @Test
    fun `iU absolute with U200 doubles value`() {
        val pr = PumpRate(2.0)
        assertEquals(4.0, pr.iU(2.0, isAbsolute = true))
    }

    @Test
    fun `iU absolute with U50 halves value`() {
        val pr = PumpRate(4.0)
        assertEquals(2.0, pr.iU(0.5, isAbsolute = true))
    }

    @Test
    fun `iU percent with U200 returns unchanged value`() {
        val pr = PumpRate(150.0)
        assertEquals(150.0, pr.iU(2.0, isAbsolute = false))
    }

    @Test
    fun `iU percent with U50 returns unchanged value`() {
        val pr = PumpRate(200.0)
        assertEquals(200.0, pr.iU(0.5, isAbsolute = false))
    }

    @Test
    fun `iU percent with U100 returns unchanged value`() {
        val pr = PumpRate(100.0)
        assertEquals(100.0, pr.iU(1.0, isAbsolute = false))
    }

    @Test
    fun `cU returns raw value`() {
        val pr = PumpRate(1.5)
        assertEquals(1.5, pr.cU)
    }

    @Test
    fun `equals compares cU values`() {
        assertEquals(PumpRate(2.0), PumpRate(2.0))
        assertNotEquals(PumpRate(2.1), PumpRate(2.0))
    }

    @Test
    fun `hashCode is consistent`() {
        assertEquals(PumpRate(2.0).hashCode(), PumpRate(2.0).hashCode())
    }

    @Test
    fun `toString formats correctly`() {
        assertEquals("PumpRate(1.5)", PumpRate(1.5).toString())
    }
}
