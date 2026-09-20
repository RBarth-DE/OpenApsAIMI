package app.aaps.plugins.aps

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.utils.DecimalFormatter

/**
 * A [DecimalFormatter] for `commonTest`, doing what the real one does.
 *
 * The Android tests used a Mockito stub built on `String.format`, which is JVM only - and which also
 * rounded half away from zero, where the real formatter rounds half to even. So the mock disagreed
 * with the product on exactly the values a test is most likely to use. This fake calls the same
 * [NumberFormat] the real implementation calls, so an assertion made here is an assertion about what
 * the user sees.
 *
 * The separator is fixed to a dot. [NumberFormat] normally takes it from the running machine's
 * locale, which would make these tests pass or fail depending on the machine, not on the code.
 */
class FakeDecimalFormatter : DecimalFormatter {

    override fun to0Decimal(value: Double): String = format(NumberFormat.INTEGER, value)

    override fun to0Decimal(value: Double, unit: String): String = to0Decimal(value) + unit

    override fun to1Decimal(value: Double): String = format(NumberFormat.DECIMAL_1, value)

    override fun to1Decimal(value: Double, unit: String): String = to1Decimal(value) + unit

    override fun to2Decimal(value: Double): String = format(NumberFormat.DECIMAL_2, value)

    override fun to2Decimal(value: Double, unit: String): String = to2Decimal(value) + unit

    override fun to3Decimal(value: Double): String = format(NumberFormat.DECIMAL_3, value)

    override fun to3Decimal(value: Double, unit: String): String = to3Decimal(value) + unit

    override fun toPumpSupportedBolus(value: Double, bolusStep: Double): String =
        if (bolusStep <= 0.051) to2Decimal(value) else to1Decimal(value)

    override fun toPumpSupportedBolusWithUnits(value: Double, bolusStep: Double): String =
        toPumpSupportedBolus(value, bolusStep)

    override fun toPumpSupportedBolusWithUnits(value: PumpInsulin, bolusStep: Double): String =
        toPumpSupportedBolus(value.cU, bolusStep)

    override fun pumpSupportedBolusFormat(bolusStep: Double): NumberFormat =
        if (bolusStep <= 0.051) NumberFormat.DECIMAL_2 else NumberFormat.DECIMAL_1

    private fun format(numberFormat: NumberFormat, value: Double): String =
        numberFormat.format(value, NumberFormatPlatform.SEPARATOR_DOT)
}
