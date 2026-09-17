package app.aaps.plugins.aps.openAPSAIMI

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the one-shot per-tag prebolus lock ([DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks]):
 * a tag (LUNCH_P1, ...) fires once per activation, without depending on a glucose value, and never
 * twice - not even on a fast sensor.
 *
 * The lock covers a fixed 30-minute meal-mode window plus a 90-second margin. It does not shrink
 * with the runtime of the current activation, because a tag that fires early in the window would
 * otherwise unlock in the middle of that window and fire a second time.
 *
 * Convention: `blocks == true` means the prebolus is refused (already fired in this activation).
 */
class LegacyPrebolusLatchTest {

    private val now = 1_700_000_000_000L
    private fun min(m: Long) = m * 60_000L

    /** Length of the lock window the production code uses: 30 minutes plus 90 seconds. */
    private val window = 30 * min(1) + 90_000L

    @Test
    fun `never fired (firedAt null) does not block - P1 fires on the first tick`() {
        // No fire recorded -> allowed, whatever the runtime is.
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(null, now, 0))
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(null, now, 2))
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(null, now, 17))
    }

    @Test
    fun `same activation after a fire - blocks the second fire`() {
        // Fired 4 minutes ago -> still inside the window -> blocked.
        val firedAt = now - min(4)
        assertTrue(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 6))
    }

    @Test
    fun `fast sensor - two ticks inside the onset still block the double`() {
        // Fired 2 minutes ago, re-evaluated at runtime 3 (a sensor that refreshes faster than 5 min).
        val firedAt = now - min(2)
        assertTrue(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 3))
    }

    @Test
    fun `new activation (runtime back to zero) - allows the fire`() {
        // Fired yesterday (24 h) but this is a new activation -> runtime 2, well past the window.
        val firedAt = now - min(24 * 60)
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 2))
    }

    @Test
    fun `P2 (15-24) allows a fire after P1 - the P1 fire is older than the P2 runtime`() {
        // At runtime 17 a P2 fire would already be older than 17 min if it came from another
        // activation. Here the tag was never fired in this activation: an old firedAt is allowed.
        val firedAt = now - min(60)
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 17))
    }

    @Test
    fun `boundary - a fire at the end of the window is no longer blocked`() {
        val firedAt = now - window
        assertFalse(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 6))
    }

    @Test
    fun `boundary - a fire one millisecond inside the window is still blocked`() {
        val firedAt = now - window + 1
        assertTrue(DetermineBasalaimiSMB2.legacyPrebolusLatchBlocks(firedAt, now, 12))
    }
}
