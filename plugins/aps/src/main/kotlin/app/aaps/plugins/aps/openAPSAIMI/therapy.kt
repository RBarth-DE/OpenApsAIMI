package app.aaps.plugins.aps.openAPSAIMI

import android.annotation.SuppressLint
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Therapy(private val persistenceLayer: PersistenceLayer) {

    var sleepTime = false
    var sportTime = false
    var snackTime = false
    var lowCarbTime = false
    var highCarbTime = false
    var mealTime = false
    var bfastTime = false
    var lunchTime = false
    var dinnerTime = false
    var fastingTime = false
    var stopTime = false
    var calibrationTime = false
    var deleteEventDate: String? = null
    var deleteTime = false

    @SuppressLint("CheckResult")
    fun updateStatesBasedOnTherapyEvents() {
        stopTime = findActive("stop")
        if (!stopTime) {
            sleepTime = findActive("sleep")
            sportTime = findActiveSport()
            snackTime = findActive("snack")
            lowCarbTime = findActive("lowcarb")
            highCarbTime = findActiveHighCarb()
            mealTime = findActive("meal")
            bfastTime = findActiveBreakfast()
            lunchTime = findActive("lunch")
            dinnerTime = findActive("dinner")
            fastingTime = findActive("fasting")
            calibrationTime = findCalibration()
            deleteTime = findActive("delete")
            if (deleteTime) {
                val events = getRecentNotes()
                val note = events.find { it.note?.contains("delete", ignoreCase = true) == true }?.note
                deleteEventDate = extractDateFromDeleteEvent(note)
            }
        } else {
            resetAllStates()
        }
    }

    private fun getRecentNotes(): List<TE> {
        val fromTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        return runBlocking { persistenceLayer.getTherapyEventDataFromTime(fromTime, true) }
            .filter { it.type == TE.Type.NOTE }
    }

    private fun findActive(keyword: String): Boolean {
        val now = System.currentTimeMillis()
        return getRecentNotes().any { event ->
            event.note?.contains(keyword, ignoreCase = true) == true &&
                now <= (event.timestamp + event.duration)
        }
    }

    private fun findActiveSport(): Boolean {
        val now = System.currentTimeMillis()
        return getRecentNotes().any { event ->
            val note = event.note?.lowercase() ?: ""
            val containsSport = note.contains("sport", ignoreCase = true)
            val isWalking = note.contains("marche", ignoreCase = true) || note.contains("walk", ignoreCase = true)
            (containsSport && !isWalking) && now <= (event.timestamp + event.duration)
        }
    }

    private fun findActiveHighCarb(): Boolean {
        val now = System.currentTimeMillis()
        return getRecentNotes().any { event ->
            val note = event.note ?: ""
            (note.contains("highcarb", ignoreCase = true) || note.contains("high carb", ignoreCase = true)) &&
                now <= (event.timestamp + event.duration)
        }
    }

    private fun findActiveBreakfast(): Boolean {
        val now = System.currentTimeMillis()
        return getRecentNotes().any { event ->
            val note = event.note ?: ""
            (note.contains("bfast", ignoreCase = true) || note.contains("breakfast", ignoreCase = true)) &&
                now <= (event.timestamp + event.duration)
        }
    }

    private fun findCalibration(): Boolean {
        val tenMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15)
        val events = runBlocking { persistenceLayer.getTherapyEventDataFromTime(tenMinutesAgo, true) }
        val now = System.currentTimeMillis()
        return events.filter { it.type == TE.Type.FINGER_STICK_BG_VALUE }
            .any { now <= (it.timestamp + it.duration) }
    }

    private fun resetAllStates() {
        sleepTime = false; sportTime = false; snackTime = false
        lowCarbTime = false; highCarbTime = false; mealTime = false
        bfastTime = false; lunchTime = false; dinnerTime = false
        fastingTime = false; deleteTime = false
    }

    private fun extractDateFromDeleteEvent(note: String?): String? {
        val pattern = Pattern.compile("delete (\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(note ?: "")
        return if (matcher.find()) matcher.group(1) else null
    }

    fun getTimeElapsedSinceLastEvent(keyword: String): Long {
        val fromTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(60)
        val events = runBlocking { persistenceLayer.getTherapyEventDataFromTime(fromTime, TE.Type.NOTE, true) }
        val lastEvent = events.filter { it.note?.contains(keyword, ignoreCase = true) == true }
            .maxByOrNull { it.timestamp }
        return lastEvent?.let { (System.currentTimeMillis() - it.timestamp) / 60000 } ?: -1
    }
}
