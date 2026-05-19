package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoublePreferenceKey


fun DoublePreferenceKey.resolvedStep(): Double {
    // 1. Explicit step wins
    step?.let { return it }

    // 2. UnitType-based step (non-NONE types have their own rules)
    if (unitType != UnitType.NONE) return unitType.step()

    // 3. Span-based fallback — mirrors AdaptiveDoublePreferenceItem logic exactly
    val span = max - min
    return when {
        span <= 0.15  -> 0.001
        span <= 1.5   -> 0.01
        span <= 25.0  -> 0.1
        else          -> 1.0
    }
}

fun DoublePreferenceKey.snapToStep(value: Double): Double {
    val s = resolvedStep()
    return (Math.round(value / s) * s).coerceIn(min, max)
}
