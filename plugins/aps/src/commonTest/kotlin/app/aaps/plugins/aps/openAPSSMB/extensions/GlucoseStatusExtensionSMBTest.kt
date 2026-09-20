package app.aaps.plugins.aps.openAPSSMB.extensions

import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.plugins.aps.FakeDecimalFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the rounding the APS applies to a glucose status before it is used, and the text it logs.
 *
 * In `commonTest` rather than `androidHostTest`: [GlucoseStatusSMB.asRounded] is shared code, and a
 * rounding step that feeds the prediction is worth checking on the platform it ships to.
 *
 * One expectation changed with the move. The old test mocked the formatter with `String.format`,
 * which rounds half away from zero, so `-8.5` was expected as `-9`. The real formatter rounds half
 * to even and renders `-8`, which is what the user and the log actually get; the assertion now says
 * that. `FakeDecimalFormatter` does the formatting the real implementation does, so the rest of the
 * expectations are unchanged and no longer describe a mock.
 */
class GlucoseStatusExtensionSMBTest {

    private val decimalFormatter = FakeDecimalFormatter()

    private fun status(
        glucose: Double = 0.0,
        noise: Double = 0.0,
        delta: Double = 0.0,
        shortAvgDelta: Double = 0.0,
        longAvgDelta: Double = 0.0
    ) = GlucoseStatusSMB(
        glucose = glucose,
        noise = noise,
        delta = delta,
        shortAvgDelta = shortAvgDelta,
        longAvgDelta = longAvgDelta,
        date = 1609459200000L
    )

    @Test
    fun `log formats glucose status with all fields`() {
        val log = status(
            glucose = 120.0,
            noise = 5.0,
            delta = -3.0,
            shortAvgDelta = -2.5,
            longAvgDelta = -1.8
        ).log(decimalFormatter)

        assertTrue(log.contains("Glucose: 120 mg/dl"), log)
        assertTrue(log.contains("Noise: 5"), log)
        assertTrue(log.contains("Delta: -3 mg/dl"), log)
        assertTrue(log.contains("Short avg. delta:  -2.50 mg/dl"), log)
        assertTrue(log.contains("Long avg. delta: -1.80 mg/dl"), log)
    }

    @Test
    fun `log formats glucose with zero values`() {
        val log = status().log(decimalFormatter)

        assertTrue(log.contains("Glucose: 0 mg/dl"), log)
        assertTrue(log.contains("Delta: 0 mg/dl"), log)
        assertTrue(log.contains("Short avg. delta:  0.00 mg/dl"), log)
    }

    @Test
    fun `log formats glucose with high values`() {
        val log = status(
            glucose = 400.0,
            noise = 10.0,
            delta = 15.0,
            shortAvgDelta = 12.5,
            longAvgDelta = 10.8
        ).log(decimalFormatter)

        assertTrue(log.contains("Glucose: 400 mg/dl"), log)
        assertTrue(log.contains("Delta: 15 mg/dl"), log)
    }

    @Test
    fun `log formats glucose with negative delta`() {
        val log = status(
            glucose = 100.0,
            noise = 0.0,
            delta = -8.5,
            shortAvgDelta = -7.2,
            longAvgDelta = -5.3
        ).log(decimalFormatter)

        // -8.5 is a tie, and the formatter rounds half to even, so it renders -8 and not -9.
        assertTrue(log.contains("Delta: -8 mg/dl"), log)
        assertTrue(log.contains("Short avg. delta:  -7.20 mg/dl"), log)
        assertTrue(log.contains("Long avg. delta: -5.30 mg/dl"), log)
    }

    @Test
    fun `asRounded rounds glucose to 0_1`() {
        val rounded = status(glucose = 120.456).asRounded()

        assertEquals(120.5, rounded.glucose)
    }

    @Test
    fun `asRounded rounds noise to 0_01`() {
        val rounded = status(noise = 5.678).asRounded()

        assertEquals(5.68, rounded.noise)
    }

    @Test
    fun `asRounded rounds delta to 0_01`() {
        val rounded = status(delta = 3.456).asRounded()

        assertEquals(3.46, rounded.delta)
    }

    @Test
    fun `asRounded rounds shortAvgDelta to 0_01`() {
        val rounded = status(shortAvgDelta = 2.789).asRounded()

        assertEquals(2.79, rounded.shortAvgDelta)
    }

    @Test
    fun `asRounded rounds longAvgDelta to 0_01`() {
        val rounded = status(longAvgDelta = 1.234).asRounded()

        assertEquals(1.23, rounded.longAvgDelta)
    }

    @Test
    fun `asRounded preserves date`() {
        val rounded = status().asRounded()

        assertEquals(1609459200000L, rounded.date)
    }

    @Test
    fun `asRounded handles all values together`() {
        val rounded = status(
            glucose = 123.456,
            noise = 7.891,
            delta = -4.567,
            shortAvgDelta = -3.234,
            longAvgDelta = -2.111
        ).asRounded()

        assertEquals(123.5, rounded.glucose)
        assertEquals(7.89, rounded.noise)
        assertEquals(-4.57, rounded.delta)
        assertEquals(-3.23, rounded.shortAvgDelta)
        assertEquals(-2.11, rounded.longAvgDelta)
        assertEquals(1609459200000L, rounded.date)
    }

    @Test
    fun `asRounded handles boundary rounding cases`() {
        val rounded = status(
            glucose = 120.05,  // rounds to 120.1
            noise = 5.005,     // rounds to 5.01
            delta = 3.005      // rounds to 3.01
        ).asRounded()

        assertEquals(120.1, rounded.glucose)
        assertEquals(5.01, rounded.noise)
        assertEquals(3.01, rounded.delta)
    }

    @Test
    fun `asRounded handles negative values`() {
        val rounded = status(
            glucose = 80.456,
            delta = -5.678,
            shortAvgDelta = -4.567,
            longAvgDelta = -3.456
        ).asRounded()

        assertEquals(80.5, rounded.glucose)
        assertEquals(-5.68, rounded.delta)
        assertEquals(-4.57, rounded.shortAvgDelta)
        assertEquals(-3.46, rounded.longAvgDelta)
    }

    @Test
    fun `asRounded handles very small values`() {
        val rounded = status(
            glucose = 0.123,
            noise = 0.001,
            delta = 0.001
        ).asRounded()

        assertEquals(0.1, rounded.glucose)
        assertEquals(0.0, rounded.noise)
        assertEquals(0.0, rounded.delta)
    }

    @Test
    fun `log handles fractional values with proper formatting`() {
        val log = status(
            glucose = 125.5,
            noise = 3.25,
            delta = -2.75,
            shortAvgDelta = -1.5,
            longAvgDelta = -0.8
        ).log(decimalFormatter)

        assertTrue(log.contains("Glucose: 126 mg/dl"), log)          // rounds to 0 decimal
        assertTrue(log.contains("Delta: -3 mg/dl"), log)             // rounds to 0 decimal
        assertTrue(log.contains("Short avg. delta:  -1.50 mg/dl"), log) // 2 decimals
        assertTrue(log.contains("Long avg. delta: -0.80 mg/dl"), log)   // 2 decimals
    }
}
