package app.aaps.core.interfaces.tempTargets

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the temp-target preset list, which is a **stored preference** on every existing install.
 *
 * There was no test on the parser at all. That is thin for a reader whose failure mode is silent: it
 * catches everything and answers with an empty list, so a regression does not crash - the user simply
 * finds their presets gone. These tests were written against the `org.json` implementation and kept
 * unchanged across the move to kotlinx, so they say the new reader accepts exactly what the old one
 * wrote.
 */
class TempTargetPresetExtensionsTest {

    private val presets = listOf(
        TTPreset(id = "eating", name = "Eating soon", reason = TT.Reason.EATING_SOON, targetValue = 90.0, duration = 2_700_000L, isDeletable = false),
        TTPreset(id = "activity", name = null, reason = TT.Reason.ACTIVITY, targetValue = 140.0, duration = 5_400_000L, isDeletable = true)
    )

    @Test
    fun `a written list reads back unchanged`() {
        assertEquals(presets, presets.toJson().toTTPresets())
    }

    /** Exactly what an existing install has stored, written by the previous org.json code. */
    @Test
    fun `an already stored document still parses`() {
        val stored = """[{"id":"eating","name":"Eating soon","reason":"Eating Soon","targetValue":90,""" +
            """"duration":2700000,"isDeletable":false},{"id":"activity","reason":"Activity",""" +
            """"targetValue":140,"duration":5400000,"isDeletable":true}]"""

        val parsed = stored.toTTPresets()

        assertEquals(2, parsed.size)
        assertEquals("eating", parsed[0].id)
        assertEquals("Eating soon", parsed[0].name)
        assertEquals(TT.Reason.EATING_SOON, parsed[0].reason)
        assertEquals(90.0, parsed[0].targetValue)
        assertEquals(2_700_000L, parsed[0].duration)
        assertFalse(parsed[0].isDeletable)
        // A missing "name" is null, not an empty string.
        assertNull(parsed[1].name)
        assertTrue(parsed[1].isDeletable)
    }

    /** Numbers written as quoted strings occur in the wild and were accepted before. */
    @Test
    fun `quoted numbers are accepted`() {
        val quoted = """[{"id":"a","reason":"Activity","targetValue":"140.5","duration":"5400000","isDeletable":"true"}]"""

        val parsed = quoted.toTTPresets()

        assertEquals(1, parsed.size)
        assertEquals(140.5, parsed[0].targetValue)
        assertEquals(5_400_000L, parsed[0].duration)
        assertTrue(parsed[0].isDeletable)
    }

    /** An explicit JSON null for name behaves like an absent one. */
    @Test
    fun `an explicit null name is treated as absent`() {
        val withNull = """[{"id":"a","name":null,"reason":"Activity","targetValue":140,"duration":1,"isDeletable":true}]"""

        assertNull(withNull.toTTPresets().single().name)
    }

    @Test
    fun `empty and blank documents give an empty list`() {
        assertTrue("".toTTPresets().isEmpty())
        assertTrue("[]".toTTPresets().isEmpty())
    }

    /**
     * Unreadable input must never propagate. The list is read on a settings screen and in the wizard;
     * throwing there would take the screen down over a corrupt preference.
     */
    @Test
    fun `malformed input gives an empty list rather than throwing`() {
        assertTrue("not json".toTTPresets().isEmpty())
        assertTrue("""{"not":"an array"}""".toTTPresets().isEmpty())
        assertTrue("""[{"id":"a"}]""".toTTPresets().isEmpty())   // missing required fields
    }

    @Test
    fun `a name is omitted rather than written as null`() {
        val json = listOf(presets[1]).toJson()

        assertFalse(json.contains("name"))
    }
}
