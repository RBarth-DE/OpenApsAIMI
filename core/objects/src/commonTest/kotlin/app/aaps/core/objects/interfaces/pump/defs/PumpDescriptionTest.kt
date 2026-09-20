package app.aaps.core.objects.interfaces.pump.defs

import app.aaps.core.data.pump.defs.Capability
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.defs.fillFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest` rather than `androidHostTest`: [PumpDescription] is shared code and says what the
 * pump can be asked to do - the steps, the maximum temp percent, the durations. The APS reads it
 * before every command, so it is worth checking on every platform.
 */
class PumpDescriptionTest {

    @Test fun setPumpDescription() {
        val pumpDescription = PumpDescription()
        pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
        assertEquals(PumpType.ACCU_CHEK_COMBO.bolusSize(), pumpDescription.bolusStep, 0.1)
        assertEquals(PumpType.ACCU_CHEK_COMBO.baseBasalStep(), pumpDescription.basalMinimumRate, 0.1)
        assertEquals(PumpType.ACCU_CHEK_COMBO.baseBasalStep(), pumpDescription.basalStep, 0.1)
        assertEquals(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings()?.durationStep?.toDouble(), pumpDescription.extendedBolusDurationStep)
        assertEquals(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings()?.maxDuration?.toDouble(), pumpDescription.extendedBolusMaxDuration)
        assertEquals(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings()?.step, pumpDescription.extendedBolusStep)
        assertEquals(PumpType.ACCU_CHEK_COMBO.pumpCapability()?.hasCapability(Capability.ExtendedBolus), pumpDescription.isExtendedBolusCapable)
        assertEquals(PumpType.ACCU_CHEK_COMBO.pumpCapability()?.hasCapability(Capability.Bolus), pumpDescription.isBolusCapable)
        assertEquals(PumpType.ACCU_CHEK_COMBO.pumpCapability()?.hasCapability(Capability.Refill), pumpDescription.isRefillingCapable)
        assertEquals(PumpType.ACCU_CHEK_COMBO.pumpCapability()?.hasCapability(Capability.BasalProfileSet), pumpDescription.isSetBasalProfileCapable)
        assertEquals(PumpType.ACCU_CHEK_COMBO.pumpCapability()?.hasCapability(Capability.TempBasal), pumpDescription.isTempBasalCapable)
        assertEquals(PumpType.ACCU_CHEK_COMBO.tbrSettings()?.maxDose, pumpDescription.maxTempPercent.toDouble())
        assertEquals(PumpType.ACCU_CHEK_COMBO.tbrSettings()?.step, pumpDescription.tempPercentStep.toDouble())
        assertEquals(
            if (PumpType.ACCU_CHEK_COMBO.pumpTempBasalType() == PumpTempBasalType.Percent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE,
            pumpDescription.tempBasalStyle
        )
        assertEquals(PumpType.ACCU_CHEK_COMBO.tbrSettings()?.durationStep?.toLong(), pumpDescription.tempDurationStep.toLong())
        assertEquals(PumpType.ACCU_CHEK_COMBO.specialBasalDurations().contains(Capability.BasalRate_Duration15minAllowed), pumpDescription.tempDurationStep15mAllowed)
        assertEquals(PumpType.ACCU_CHEK_COMBO.specialBasalDurations().contains(Capability.BasalRate_Duration30minAllowed), pumpDescription.tempDurationStep30mAllowed)
    }
}
