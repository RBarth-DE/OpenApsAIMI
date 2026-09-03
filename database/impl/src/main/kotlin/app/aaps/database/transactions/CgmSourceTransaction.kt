package app.aaps.database.transactions

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit

/**
 * Inserts data from a CGM source into the database
 */
class CgmSourceTransaction(
    private val glucoseValues: List<GlucoseValue>,
    private val calibrations: List<Calibration>,
    private val sensorInsertionTime: Long?,
    private val now: Long = System.currentTimeMillis(),
    private val nsClientData: Boolean = false
) : Transaction<CgmSourceTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        glucoseValues.forEach { glucoseValue ->
            val current = database.glucoseValueDao.findByTimestampAndSensor(glucoseValue.timestamp, glucoseValue.sourceSensor)
            // if nsId is not provided in new record, copy from current if exists
            if (glucoseValue.interfaceIDs.nightscoutId == null)
                current?.let { existing -> glucoseValue.interfaceIDs.nightscoutId = existing.interfaceIDs.nightscoutId }
            // preserve invalidated status (user may delete record in UI)
            current?.let { existing -> glucoseValue.isValid = existing.isValid }
            when {
                // new record, create new
                current == null                                                                             -> {
                    database.glucoseValueDao.insertNewEntry(glucoseValue)
                    result.inserted.add(glucoseValue)
                }
                // NS data must not replace a fresh local sensor reading. AAPS uploads the value it displays
                // (calibrated and smoothed) as the NS sgv. NS pushes that value back as an echo. Accepting
                // the echo replaces the raw sensor value with the displayed one. If the displayed value ever
                // freezes, the loop pins itself: AAPS writes the frozen value to NS, NS writes it back.
                // So keep the local content and only take over the NS id.
                nsClientData && now - current.timestamp < FRESH_LOCAL_READING_MS                      -> {
                    if (current.interfaceIDs.nightscoutId == null && glucoseValue.interfaceIDs.nightscoutId != null)
                        updateNsIdOnly(current, glucoseValue, result)
                }
                // different record, update
                !current.contentEqualsTo(glucoseValue)                                                      -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
                    result.updated.add(glucoseValue)
                }
                // update NS id if didn't exist and now provided
                current.interfaceIDs.nightscoutId == null && glucoseValue.interfaceIDs.nightscoutId != null -> updateNsIdOnly(current, glucoseValue, result)
            }
        }
        calibrations.forEach {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.FINGER_STICK_BG_VALUE, it.timestamp) == null) {
                val therapyEvent = TherapyEvent(
                    timestamp = it.timestamp,
                    type = TherapyEvent.Type.FINGER_STICK_BG_VALUE,
                    glucose = it.value,
                    glucoseUnit = it.glucoseUnit
                )
                database.therapyEventDao.insertNewEntry(therapyEvent)
                result.calibrationsInserted.add(therapyEvent)
            }
        }
        sensorInsertionTime?.let {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.SENSOR_CHANGE, it) == null) {
                val location = null
                val therapyEvent = TherapyEvent(
                    timestamp = it,
                    type = TherapyEvent.Type.SENSOR_CHANGE,
                    glucoseUnit = GlucoseUnit.MGDL,
                    location = location
                )
                database.therapyEventDao.insertNewEntry(therapyEvent)
                result.sensorInsertionsInserted.add(therapyEvent)
            }
        }
        return result
    }

    private fun updateNsIdOnly(current: GlucoseValue, glucoseValue: GlucoseValue, result: TransactionResult) {
        current.interfaceIDs.nightscoutId = glucoseValue.interfaceIDs.nightscoutId
        database.glucoseValueDao.updateExistingEntry(current)
        result.updatedNsId.add(glucoseValue)
    }

    data class Calibration(
        val timestamp: Long,
        val value: Double,
        val glucoseUnit: GlucoseUnit
    )

    class TransactionResult {

        val inserted = mutableListOf<GlucoseValue>()
        val updated = mutableListOf<GlucoseValue>()
        val updatedNsId = mutableListOf<GlucoseValue>()

        val calibrationsInserted = mutableListOf<TherapyEvent>()
        val sensorInsertionsInserted = mutableListOf<TherapyEvent>()

        fun all(): MutableList<GlucoseValue> =
            mutableListOf<GlucoseValue>().also { result ->
                result.addAll(inserted)
                result.addAll(updated)
            }
    }

    companion object {

        /** Local readings younger than this keep their value when NS data for the same timestamp arrives. */
        private const val FRESH_LOCAL_READING_MS = 15 * 60 * 1000L
    }
}