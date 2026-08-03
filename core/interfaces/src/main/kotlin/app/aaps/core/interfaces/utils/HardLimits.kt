package app.aaps.core.interfaces.utils

interface HardLimits {
    companion object {

        // Age dependent limits. Keyed by [AgeType] so a value can never be read with a wrong index.
        val MAX_BOLUS = mapOf(
            AgeType.CHILD to 5.0,
            AgeType.TEENAGE to 10.0,
            AgeType.ADULT to 17.0,
            AgeType.RESISTANT_ADULT to 25.0,
            AgeType.PREGNANT to 60.0
        )

        // Very Hard Limits Ranges [mg/dL]
        // The range says how low and how high the limit itself may be set
        val LIMIT_MIN_BG = 80.0..180.0
        val LIMIT_MAX_BG = 90.0..200.0
        val LIMIT_TARGET_BG = 80.0..200.0

        // Very Hard Limits Ranges for Temp Targets
        val MIN_DIA = doubleArrayOf(5.0, 5.0, 4.0, 4.0, 4.0)
        val MAX_DIA = doubleArrayOf(9.0, 9.0, 9.0, 9.0, 12.0)
        val MIN_DIA_INHALED = doubleArrayOf(1.5, 1.5, 1.5, 1.5, 1.5) // Inhaled insulin (e.g. Afrezza) has shorter DIA
        val MAX_DIA_INHALED = doubleArrayOf(4.0, 4.0, 4.0, 4.0, 4.0)
        const val MIN_PEAK = 35 // minutes
        const val MAX_PEAK = 120 // minutes
        // Inhaled insulin (e.g. Afrezza): clinical Tmax ~35–45 min; range must include local default Peak 40.
        const val MIN_PEAK_INHALED = 20 // minutes
        const val MAX_PEAK_INHALED = 45 // minutes
        val MIN_IC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
        val MAX_IC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
        const val MIN_ISF = 2.0 // mgdl
        const val MAX_ISF = 1200.0 // mgdl
         // Very Hard Limits Ranges for Temp Targets [mg/dL]
        val LIMIT_TEMP_MIN_BG = 72.0..180.0
        val LIMIT_TEMP_MAX_BG = 72.0..270.0
        val LIMIT_TEMP_TARGET_BG = 72.0..200.0
        val LIMIT_DIA = mapOf(
            AgeType.CHILD to 5.0..9.0,
            AgeType.TEENAGE to 5.0..9.0,
            AgeType.ADULT to 5.0..9.0,
            AgeType.RESISTANT_ADULT to 5.0..9.0,
            AgeType.PREGNANT to 5.0..10.0
        )
        val LIMIT_PEAK = 35..120 // min
        val LIMIT_IC = mapOf(
            AgeType.CHILD to 2.0..100.0,
            AgeType.TEENAGE to 2.0..100.0,
            AgeType.ADULT to 2.0..100.0,
            AgeType.RESISTANT_ADULT to 2.0..100.0,
            AgeType.PREGNANT to 0.3..100.0
        )
        val LIMIT_ISF = 2.0..1000.0 // mgdl
        val MAX_IOB_AMA = mapOf(
            AgeType.CHILD to 3.0,
            AgeType.TEENAGE to 5.0,
            AgeType.ADULT to 7.0,
            AgeType.RESISTANT_ADULT to 12.0,
            AgeType.PREGNANT to 25.0
        )
        val MAX_IOB_SMB = mapOf(
            AgeType.CHILD to 7.0,
            AgeType.TEENAGE to 13.0,
            AgeType.ADULT to 22.0,
            AgeType.RESISTANT_ADULT to 30.0,
            AgeType.PREGNANT to 70.0
        )
        val MAX_BASAL = mapOf(
            AgeType.CHILD to 2.0,
            AgeType.TEENAGE to 5.0,
            AgeType.ADULT to 10.0,
            AgeType.RESISTANT_ADULT to 12.0,
            AgeType.PREGNANT to 25.0
        )

        //LGS Hard limits
        //No IOB at all
        const val MAX_IOB_LGS = 0.0

        const val MAX_CARBS_DURATION_HOURS  = 10L
        const val MAX_CARBS  = 400
    }

    fun maxBolus(): Double
    fun maxIobAMA(): Double
    fun maxIobSMB(): Double
    fun maxBasal(): Double
    fun minDia(): Double
    fun maxDia(): Double
    fun minDiaInhaled(): Double
    fun maxDiaInhaled(): Double
    fun minPeak(): Int
    fun maxPeak(): Int
    fun minPeakInhaled(): Int
    fun maxPeakInhaled(): Int
    fun minIC(): Double
    fun maxIC(): Double
    fun diaRange(): ClosedFloatingPointRange<Double>
    fun peakRange(): IntRange
    fun icRange(): ClosedFloatingPointRange<Double>

    // safety checks
    fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean

    fun isInRange(value: Double, lowLimit: Double, highLimit: Double): Boolean

    fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double

    /** Same as [verifyHardLimits], for the limit ranges defined above. */
    fun verifyHardLimits(value: Double, valueName: Int, limits: ClosedFloatingPointRange<Double>): Double =
        verifyHardLimits(value, valueName, limits.start, limits.endInclusive)

    fun ageEntries(): Array<CharSequence>
    fun ageEntryValues(): Array<CharSequence>

    enum class AgeType { CHILD, TEENAGE, ADULT, RESISTANT_ADULT, PREGNANT }
}