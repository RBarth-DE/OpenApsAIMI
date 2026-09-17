package app.aaps.plugins.source

/**
 * One collected pre-soak reading.
 *
 * It is never stored and never published — see invariant I1 in `docs/LIBRE3_PRESOAK_PLAN.md`. It
 * lives in this module so the pre-soak curve can be drawn without a new module dependency.
 *
 * It sits in commonMain because the curve that draws it is shared code, while the state machine
 * that fills it (`Libre3Staging`) still needs the Android-only Libre 3 driver types.
 */
data class Libre3PresoakPoint(val timestampMs: Long, val mgdl: Double)
