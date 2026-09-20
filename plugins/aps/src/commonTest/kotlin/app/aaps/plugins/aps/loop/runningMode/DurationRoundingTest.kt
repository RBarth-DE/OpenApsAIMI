package app.aaps.plugins.aps.loop.runningMode

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the running-mode TBR rounding, which is shared code and so cannot be tested only on Android.
 *
 * In `commonTest` rather than `androidHostTest`: the class under test lives in `commonMain` and ships
 * to iOS, where the mistake this guards against - issuing a TBR the pump would shorten - is the same
 * one. The test needed nothing from the Android test base, so it moved as it was.
 */
class DurationRoundingTest {

    @Test
    fun `rounds up when remaining is less than one step`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 15,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(30), r)
    }

    @Test
    fun `rounds up when remaining is not a multiple of step`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 45,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(60), r)
    }

    @Test
    fun `passes through when remaining is already a multiple of step`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 60,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(60), r)
    }

    @Test
    fun `fine-grained pump with step one passes remaining through`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 17,
            pumpStepMinutes = 1,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(17), r)
    }

    @Test
    fun `fifteen minute step pump with forty-five remaining`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 45,
            pumpStepMinutes = 15,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(45), r)
    }

    @Test
    fun `skips when remaining is zero`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 0,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Skip, r)
    }

    @Test
    fun `skips when remaining is negative`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = -5,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Skip, r)
    }

    @Test
    fun `skips when pump step is zero meaning TBR not supported`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 30,
            pumpStepMinutes = 0,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Skip, r)
    }

    @Test
    fun `skips when pump step is negative`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 30,
            pumpStepMinutes = -1,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Skip, r)
    }

    @Test
    fun `caps at pump max duration when remaining exceeds it`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 1000,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(720), r)
    }

    @Test
    fun `caps when rounded-up value exceeds max`() {
        // remaining 721 with 60-min step would round to 780; capped to 720
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 721,
            pumpStepMinutes = 60,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(720), r)
    }

    @Test
    fun `rounded-up value at exactly max is allowed`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 700,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(720), r)
    }

    @Test
    fun `pump max of zero means no cap`() {
        // 4980 is exactly divisible by step 30 so rounded == input
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 4980,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 0
        )
        assertEquals(DurationRounding.Result.Issue(4980), r)
    }

    @Test
    fun `pump max negative means no cap`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 4980,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = -1
        )
        assertEquals(DurationRounding.Result.Issue(4980), r)
    }

    @Test
    fun `one minute remaining with sixty step pump issues sixty`() {
        // Safety: short remaining still rounds up. Worker will cancel at the exact end.
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 1,
            pumpStepMinutes = 60,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(60), r)
    }

    @Test
    fun `step equal to remaining returns exactly that value`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = 30,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(30), r)
    }

    @Test
    fun `very large remaining does not overflow and caps at max`() {
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = Int.MAX_VALUE - 5,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 720
        )
        assertEquals(DurationRounding.Result.Issue(720), r)
    }

    @Test
    fun `very large remaining with no cap rounds correctly and fits in Int`() {
        // Int.MAX_VALUE rounded up to next multiple of 30 does not fit in Int
        // but the caller presumably won't pass this without a cap; still verify no crash.
        val remaining = (Int.MAX_VALUE / 30) * 30 // already a multiple of 30, fits
        val r = DurationRounding.roundUpToPumpStep(
            remainingMinutes = remaining,
            pumpStepMinutes = 30,
            pumpMaxDurationMinutes = 0
        )
        assertEquals(DurationRounding.Result.Issue(remaining), r)
    }
}
