package app.aaps.core.interfaces.pump

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PumpInsulinTest {

    @Test
    fun `iU with U100 returns same value`() {
        val pi = PumpInsulin(3.0)
        assertEquals(3.0, pi.iU(1.0))
    }

    @Test
    fun `iU with U200 doubles value`() {
        val pi = PumpInsulin(3.0)
        assertEquals(6.0, pi.iU(2.0))
    }

    @Test
    fun `iU with U50 halves value`() {
        val pi = PumpInsulin(4.0)
        assertEquals(2.0, pi.iU(0.5))
    }

    @Test
    fun `iU with U500 multiplies by 5`() {
        val pi = PumpInsulin(1.0)
        assertEquals(5.0, pi.iU(5.0))
    }

    @Test
    fun `iU with U10 multiplies by 0_1`() {
        val pi = PumpInsulin(10.0)
        assertEquals(1.0, pi.iU(0.1), 0.001)
    }

    @Test
    fun `cU returns raw value`() {
        val pi = PumpInsulin(3.5)
        assertEquals(3.5, pi.cU)
    }

    @Test
    fun `equals compares cU values`() {
        assertEquals(PumpInsulin(3.0), PumpInsulin(3.0))
        assertNotEquals(PumpInsulin(3.1), PumpInsulin(3.0))
    }

    @Test
    fun `hashCode is consistent`() {
        assertEquals(PumpInsulin(3.0).hashCode(), PumpInsulin(3.0).hashCode())
    }

    @Test
    fun `toString formats correctly`() {
        assertEquals("PumpInsulin(3.5)", PumpInsulin(3.5).toString())
    }
}
