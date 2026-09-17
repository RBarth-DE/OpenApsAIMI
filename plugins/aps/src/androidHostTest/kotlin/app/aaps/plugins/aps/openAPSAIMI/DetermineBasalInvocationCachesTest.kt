package app.aaps.plugins.aps.openAPSAIMI

import androidx.collection.LongSparseArray
import app.aaps.core.data.model.TDD
import app.aaps.core.interfaces.stats.TddCalculator
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

/**
 * The **asynchronous** contract of [DetermineBasalInvocationCaches].
 *
 * The cache no longer computes the TDD on the calling thread: `getXxxCached` starts a background
 * refresh (`ioScope`, [kotlinx.coroutines.Dispatchers.IO]) and returns the **last known value** at
 * once - so `null` on the very first call, before the coroutine has finished. The computed value
 * becomes visible from the next invocation on, after another call to [DetermineBasalInvocationCaches.beginInvocation].
 *
 * The invariant these tests really guard is: **one call to the calculator per invocation**, however
 * often the cache is read.
 *
 * The calculator is parked on a [CountDownLatch] in each test. Without that gate the background
 * coroutine can finish before the first read and publish its value, so "nothing is ready yet"
 * would depend on the speed of the machine - the same test would pass and fail in turn.
 */
class DetermineBasalInvocationCachesTest {

    /** Waits until a condition holds, or fails after [timeoutMs]. */
    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `two getTdd24h in same invocation hit the calculator once`() {
        val caches = DetermineBasalInvocationCaches()
        val calls = AtomicInteger(0)
        val tdd = mockk<TddCalculator>(relaxed = true)
        val gate = CountDownLatch(1)
        coEvery { tdd.calculateDaily(-24, 0) } coAnswers {
            calls.incrementAndGet()
            gate.await(5, TimeUnit.SECONDS)
            TDD(timestamp = 1L, totalAmount = 40.0)
        }

        caches.beginInvocation()
        // First call: starts the refresh, nothing is available yet.
        assertThat(caches.getTdd24hTotalAmountCached(tdd)).isNull()
        // Second call in the same invocation: served from the cache, nothing new is started.
        assertThat(caches.getTdd24hTotalAmountCached(tdd)).isNull()

        gate.countDown()
        awaitUntil { calls.get() == 1 }
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `beginInvocation publishes the async result`() {
        val caches = DetermineBasalInvocationCaches()
        val calls = AtomicInteger(0)
        val tdd = mockk<TddCalculator>(relaxed = true)
        val gate = CountDownLatch(1)
        coEvery { tdd.calculateDaily(-24, 0) } coAnswers {
            calls.incrementAndGet()
            gate.await(5, TimeUnit.SECONDS)
            TDD(timestamp = 1L, totalAmount = 40.0)
        }

        caches.beginInvocation()
        // First invocation: the refresh is still parked, so nothing is published.
        assertThat(caches.getTdd24hTotalAmountCached(tdd)).isNull()

        gate.countDown()
        // The value computed in the background becomes visible from a later invocation on.
        var published: Double? = null
        awaitUntil {
            caches.beginInvocation()
            published = caches.getTdd24hTotalAmountCached(tdd)
            published != null
        }
        assertThat(published).isEqualTo(40.0)
        assertThat(calls.get()).isAtLeast(1)
    }

    @Test
    fun `getTdd1d sparse is served from cache within one invocation`() {
        val caches = DetermineBasalInvocationCaches()
        val calls = AtomicInteger(0)
        val tdd = mockk<TddCalculator>(relaxed = true)
        val gate = CountDownLatch(1)
        val sparse = LongSparseArray<TDD>().apply {
            put(1L, TDD(timestamp = 1L, totalAmount = 33.0))
        }
        coEvery { tdd.calculate(1L, false) } coAnswers {
            calls.incrementAndGet()
            gate.await(5, TimeUnit.SECONDS)
            sparse
        }

        caches.beginInvocation()
        assertThat(caches.getTddCalculate1DaySparseCached(tdd)).isNull()
        assertThat(caches.getTddCalculate1DaySparseCached(tdd)).isNull()
        gate.countDown()
        awaitUntil { calls.get() == 1 }
        assertThat(calls.get()).isEqualTo(1)

        // The cached array becomes visible from a later invocation on. Each round starts a new
        // invocation, because the first read of an invocation fills the slot for that invocation
        // whether or not the background refresh has finished by then.
        var published: LongSparseArray<TDD>? = null
        awaitUntil {
            caches.beginInvocation()
            published = caches.getTddCalculate1DaySparseCached(tdd)
            published != null
        }
        assertThat(published).isSameInstanceAs(sparse)
    }
}
