package app.aaps.core.keys

/**
 * Enum defining unit types for preference values.
 * Used to format values with appropriate units in UI.
 *
 * The mapping from a unit to its text lives in `:core:ui` (`UnitTypeText.kt`), not here, so that
 * this file stays free of Android resources.
 */
enum class UnitType {

    NONE,
    GRAMS,
    MIN,
    SEC,
    HOURS,
    HOURS_DOUBLE,
    DAYS,
    PERCENT,
    INSULIN,
    INSULIN_INT,
    INSULIN_RATE,
    DOUBLE,
    DOUBLE_2,
    DOUBLE_3,
    MGDL
}

/**
 * Returns the number of decimal places for this unit type.
 */
fun UnitType.decimalPlaces(): Int = when (this) {
    UnitType.DOUBLE_3                                                               -> 3
    UnitType.DOUBLE_2                                                               -> 2
    UnitType.INSULIN, UnitType.INSULIN_RATE, UnitType.DOUBLE, UnitType.HOURS_DOUBLE -> 1
    else                                                                            -> 0
}

/**
 * Returns the step size for slider/increment controls.
 */
fun UnitType.step(): Double = when (this) {
    UnitType.DOUBLE_3                                                               -> 0.001
    UnitType.DOUBLE_2                                                               -> 0.01
    UnitType.INSULIN, UnitType.INSULIN_RATE, UnitType.DOUBLE, UnitType.HOURS_DOUBLE -> 0.1
    else                                                                            -> 1.0
}

/**
 * True when this unit is written as a whole number, so a value must be rounded before it is
 * formatted.
 *
 * These are the units whose text in `:core:ui` uses `%d` - MIN, SEC, HOURS, DAYS, GRAMS, PERCENT
 * and INSULIN_INT - and they are exactly the ones with no decimals and a step of one whole unit.
 * Handing such a format string a Double throws `IllegalFormatConversionException`, so a preference
 * that stores a Double still has to round first.
 *
 * [decimalPlaces] and this function say the same thing in two ways, so keep them in step: a unit
 * that changes its format string from `%d` to `%f` changes its decimal count with it.
 */
fun UnitType.isWholeNumber(): Boolean = decimalPlaces() == 0
