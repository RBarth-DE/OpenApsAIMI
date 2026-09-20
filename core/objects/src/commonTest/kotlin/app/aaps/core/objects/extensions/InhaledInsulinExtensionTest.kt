package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the peak+DIA heuristic used to recognise an inhaled insulin (Afrezza).
 *
 * The peak bands of inhaled and injected insulin overlap (20..45 vs 35..120), so the DIA half of
 * the test is what keeps the answer unambiguous. These cases are the boundary of that rule.
 *
 * In `commonTest` rather than `androidHostTest`: the rule is shared code and it changes how a bolus
 * is calculated, so it is worth checking wherever it runs. The test needed nothing from the Android
 * test base.
 */
class InhaledInsulinExtensionTest {

    private fun iCfg(peak: Int, dia: Double) = ICfg(insulinLabel = "test", peak = peak, dia = dia, concentration = 1.0)

    @Test
    fun `Afrezza factory default is inhaled`() {
        assertTrue(iCfg(peak = 40, dia = 2.5).looksInhaled())
    }

    @Test
    fun `Afrezza with an edited peak is still inhaled`() {
        assertTrue(iCfg(peak = 25, dia = 2.0).looksInhaled())
        assertTrue(iCfg(peak = 45, dia = 3.0).looksInhaled())
    }

    @Test
    fun `Lyumjev is not inhaled even though its peak is in the inhaled band`() {
        assertFalse(iCfg(peak = 45, dia = 8.0).looksInhaled())
    }

    @Test
    fun `Fiasp is not inhaled`() {
        assertFalse(iCfg(peak = 55, dia = 8.0).looksInhaled())
    }

    @Test
    fun `a free peak insulin sitting on the Afrezza peak is not inhaled because its DIA is long`() {
        // The overlap case. OREF_FREE_PEAK allows any peak in 35..120 min, so 40 min alone proves
        // nothing; only the DIA separates the two families.
        assertFalse(iCfg(peak = 40, dia = 6.0).looksInhaled())
    }

    @Test
    fun `DIA of 4 hours is not inhaled because it is a valid injected DIA`() {
        assertFalse(iCfg(peak = 45, dia = 4.0).looksInhaled())
    }
}
