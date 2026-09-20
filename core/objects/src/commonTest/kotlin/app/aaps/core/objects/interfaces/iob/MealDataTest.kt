package app.aaps.core.objects.interfaces.iob

import app.aaps.core.interfaces.aps.MealData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest` rather than `androidHostTest`: [MealData] is shared code that feeds the carb
 * prediction, and the test needed nothing from the Android test base.
 */
class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        assertEquals(0.0, md.carbs, 0.01)
    }
}
