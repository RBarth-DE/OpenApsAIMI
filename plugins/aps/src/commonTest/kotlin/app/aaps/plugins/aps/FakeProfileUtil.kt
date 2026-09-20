package app.aaps.plugins.aps

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileUtil
import kotlin.math.roundToInt

/**
 * A [ProfileUtil] for `commonTest`, fixed to one unit.
 *
 * The APS algorithm uses exactly one member of this interface, [fromMgdlToStringInUnits], only to
 * build text for the console log. Mockito is not available on iOS, so the choice was a hand written
 * fake or no test there at all.
 *
 * The unit maths is copied from the real implementation, so a test that reads a converted value
 * cannot be misled. The two members that build user-facing text through the resource helper are not
 * reproduced - see [getBasalProfilesDisplayable], which fails loudly rather than answering wrongly.
 *
 * @param units the unit this fake reports and converts to; mg/dL unless a test says otherwise
 */
class FakeProfileUtil(
    override val units: GlucoseUnit = GlucoseUnit.MGDL
) : ProfileUtil {

    override val unitLabel: String
        get() = if (units == GlucoseUnit.MGDL) "mg/dl" else "mmol/l"

    /** Digits only, no unit text: a test asserts on the surrounding words, not on the formatting. */
    override fun fromMgdlToStringInUnits(valueInMgdl: Double?, targetUnits: GlucoseUnit): String =
        valueInMgdl?.let { toUnitsString(it, targetUnits) } ?: ""

    override fun fromMgdlToStringWithUnits(valueInMgdl: Double?): String = fromMgdlToStringInUnits(valueInMgdl, units)

    override fun fromMgdlToSignedStringInUnits(valueInMgdl: Double, targetUnits: GlucoseUnit): String =
        (if (valueInMgdl > 0) "+" else "") + toUnitsString(valueInMgdl, targetUnits)

    override fun fromMgdlToUnits(valueInMgdl: Double, targetUnits: GlucoseUnit): Double =
        if (targetUnits == GlucoseUnit.MGDL) valueInMgdl else valueInMgdl * Constants.MGDL_TO_MMOLL

    override fun fromMmolToUnits(value: Double, targetUnits: GlucoseUnit): Double =
        if (targetUnits == GlucoseUnit.MMOL) value else value * Constants.MMOLL_TO_MGDL

    override fun valueInCurrentUnitsDetect(anyBg: Double): Double =
        if (isMmol(anyBg)) fromMmolToUnits(anyBg, units) else fromMgdlToUnits(anyBg, units)

    override fun stringInCurrentUnitsDetect(anyBg: Double): String =
        if (isMmol(anyBg)) toUnitsString(anyBg * Constants.MMOLL_TO_MGDL, units) else toUnitsString(anyBg, units)

    override fun isMgdl(anyBg: Double): Boolean = anyBg >= 36

    override fun isMmol(anyBg: Double): Boolean = anyBg < 36

    override fun unitsDetect(anyBg: Double): GlucoseUnit = if (isMgdl(anyBg)) GlucoseUnit.MGDL else GlucoseUnit.MMOL

    override fun valueInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): Double =
        if (isMmol(anyBg)) fromMmolToUnits(anyBg, targetUnits) else fromMgdlToUnits(anyBg, targetUnits)

    override fun stringInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): String =
        if (isMmol(anyBg)) toUnitsString(anyBg * Constants.MMOLL_TO_MGDL, targetUnits) else toUnitsString(anyBg, targetUnits)

    override fun convertToMgdlDetect(anyBg: Double): Double =
        if (isMgdl(anyBg)) anyBg else anyBg * Constants.MMOLL_TO_MGDL

    override fun convertToMgdl(value: Double, sourceUnits: GlucoseUnit): Double =
        if (sourceUnits == GlucoseUnit.MGDL) value else value * Constants.MMOLL_TO_MGDL

    override fun convertToMmol(value: Double, sourceUnits: GlucoseUnit): Double =
        if (sourceUnits == GlucoseUnit.MGDL) value * Constants.MGDL_TO_MMOLL else value

    override fun toTargetRangeString(low: Double, high: Double, sourceUnits: GlucoseUnit, targetUnits: GlucoseUnit): String {
        val lowMgdl = convertToMgdl(low, sourceUnits)
        val highMgdl = convertToMgdl(high, sourceUnits)
        return if (low == high) toUnitsString(lowMgdl, targetUnits)
        else toUnitsString(lowMgdl, targetUnits) + " - " + toUnitsString(highMgdl, targetUnits)
    }

    /**
     * Not implemented: the real one formats a basal rate through the resource helper and a pump
     * specific step, and no test here needs it. Throwing says so; a simplified answer would read
     * like the real text and be wrong.
     */
    override fun getBasalProfilesDisplayable(profiles: Array<Profile.ProfileValue>, pumpType: PumpType): String =
        throw NotImplementedError("FakeProfileUtil does not format basal profiles")

    private fun toUnitsString(valueInMgdl: Double, targetUnits: GlucoseUnit): String =
        if (targetUnits == GlucoseUnit.MGDL) valueInMgdl.roundToInt().toString()
        else ((valueInMgdl * Constants.MGDL_TO_MMOLL) * 10).roundToInt().div(10.0).toString()
}
