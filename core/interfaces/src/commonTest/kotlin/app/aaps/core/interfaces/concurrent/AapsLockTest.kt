package app.aaps.core.interfaces.concurrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The contract [AapsLock] replaces `kotlin.synchronized` with: reentrant, blocking, and released on
 * a throw.
 *
 * In `commonTest` rather than `androidHostTest`: [AapsLock] is an `expect class` with an actual per
 * target, so the same test has to run on each one. The concurrency test used `Executors` and
 * `CountDownLatch` before the move, which are JVM types; it now uses `Dispatchers.Default`, which is
 * a real thread pool on every target, so the lock is still entered from several threads at once.
 *
 * The plain `Int`s the workers count with are safe **because** the lock holds - which is the thing
 * being tested. A broken lock would show up as a wrong count, exactly as before.
 */
class AapsLockTest {

    @Test
    fun returnsWhateverTheActionReturns() {
        val lock = AapsLock()
        assertEquals(42, lock.withLock { 42 })
    }

    /**
     * The property the ported call sites depend on: several of them call one guarded method from
     * inside another. A non-reentrant lock would deadlock here rather than fail visibly.
     */
    @Test
    fun isReentrant() {
        val lock = AapsLock()
        val result = lock.withLock {
            lock.withLock {
                lock.withLock { "three deep" }
            }
        }
        assertEquals("three deep", result)
    }

    @Test
    fun releasesWhenTheActionThrows() {
        val lock = AapsLock()
        runCatching { lock.withLock { error("boom") } }
        // Would block forever if the failed call had kept the lock.
        assertEquals("still usable", lock.withLock { "still usable" })
    }

    @Test
    fun onlyOneThreadAtATime() = runTest {
        val lock = AapsLock()
        val workers = 8
        val perWorker = 2_000
        var unguarded = 0
        var inside = 0
        var overlaps = 0

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(workers) {
                    launch {
                        repeat(perWorker) {
                            lock.withLock {
                                if (inside != 0) overlaps++
                                inside++
                                unguarded++
                                inside--
                            }
                        }
                    }
                }
            }
        }

        assertEquals(0, overlaps)
        assertEquals(workers * perWorker, unguarded)
    }
}
