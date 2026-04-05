package com.fireflybot.adapter.out.firefly

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class FireflyCacheTest {

    // ── Basic fetch / cache behaviour ─────────────────────────────────────────

    @Test
    fun `cold cache invokes fetch and returns the result`() {
        val cache = FireflyCache<Unit, String>()
        assertEquals("hello", cache.getOrLoad(Unit) { "hello" })
    }

    @Test
    fun `warm cache returns cached value without invoking fetch again`() {
        val cache = FireflyCache<Unit, String>()
        cache.getOrLoad(Unit) { "first" }
        var called = false
        val result = cache.getOrLoad(Unit) { called = true; "second" }
        assertEquals("first", result)
        assertFalse(called)
    }

    @Test
    fun `TTL not yet elapsed serves cached value`() {
        var fakeTime = 1000L
        val cache = FireflyCache<String, String>(ttlMs = 60_000, clock = { fakeTime })
        cache.getOrLoad("k") { "alive" }
        fakeTime += 30_000 // halfway through TTL
        var called = false
        assertEquals("alive", cache.getOrLoad("k") { called = true; "other" })
        assertFalse(called)
    }

    @Test
    fun `re-fetches after TTL expires`() {
        var fakeTime = 1000L
        val cache = FireflyCache<Unit, String>(ttlMs = 100, clock = { fakeTime })
        cache.getOrLoad(Unit) { "stale" }
        fakeTime += 200 // past TTL
        assertEquals("fresh", cache.getOrLoad(Unit) { "fresh" })
    }

    @Test
    fun `each key is cached independently`() {
        val cache = FireflyCache<String, List<Int>>()
        cache.getOrLoad("a") { listOf(1) }
        cache.getOrLoad("b") { listOf(2) }
        var called = false
        assertEquals(listOf(1), cache.getOrLoad("a") { called = true; listOf(99) })
        assertEquals(listOf(2), cache.getOrLoad("b") { called = true; listOf(99) })
        assertFalse(called)
    }

    @Test
    fun `unknown key always invokes fetch`() {
        val cache = FireflyCache<String, String>()
        cache.getOrLoad("a") { "a-value" }
        var called = false
        cache.getOrLoad("b") { called = true; "b-value" }
        assertTrue(called)
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    @Test
    fun `invalidate causes next getOrLoad to re-fetch`() {
        val cache = FireflyCache<Unit, String>()
        cache.getOrLoad(Unit) { "original" }
        cache.invalidate(Unit)
        assertEquals("updated", cache.getOrLoad(Unit) { "updated" })
    }

    @Test
    fun `invalidate on missing key is a no-op`() {
        val cache = FireflyCache<Unit, String>()
        assertDoesNotThrow { cache.invalidate(Unit) }
    }

    @Test
    fun `invalidateAll clears all entries and forces re-fetch`() {
        val cache = FireflyCache<String, String>()
        cache.getOrLoad("x") { "1" }
        cache.getOrLoad("y") { "2" }
        cache.invalidateAll()
        val refetched = mutableListOf<String>()
        cache.getOrLoad("x") { refetched += "x"; "1" }
        cache.getOrLoad("y") { refetched += "y"; "2" }
        assertEquals(listOf("x", "y"), refetched)
    }

    // ── Stale-entry cleanup safety ────────────────────────────────────────────

    @Test
    fun `conditional remove of expired entry does not evict a concurrently written fresh entry`() {
        // Simulates: Thread A reads expired entry e1 and is about to call remove(key, e1).
        // Thread B has already written fresh e2 for the same key.
        // The conditional remove(key, e1) must be a no-op — e2 must survive.
        var fakeTime = 1000L
        val cache = FireflyCache<String, String>(ttlMs = 100, clock = { fakeTime })
        cache.getOrLoad("k") { "stale" }  // e1.expiresAt = 1100
        fakeTime += 200                    // clock = 1200; e1 is now expired
        cache.getOrLoad("k") { "fresh" }  // e2.expiresAt = 1300
        var called = false
        assertEquals("fresh", cache.getOrLoad("k") { called = true; "other" })
        assertFalse(called)
    }

    // ── Single-flight ─────────────────────────────────────────────────────────

    @Test
    fun `only one fetch runs when two threads race on the same cold key`() {
        val fetchCount = AtomicInteger()
        val fetchStarted = CountDownLatch(1)
        val fetchProceed = CountDownLatch(1)
        val cache = FireflyCache<String, String>(ttlMs = 60_000)
        val results = CopyOnWriteArrayList<String>()

        // t1 starts the fetch, then blocks inside it until signalled
        val t1 = Thread {
            results += cache.getOrLoad("k") {
                fetchStarted.countDown() // signal: t1 is inside the fetch
                fetchProceed.await()     // wait until t2 has joined the in-flight queue
                fetchCount.incrementAndGet()
                "value"
            }
        }
        // t2 arrives after t1 is already fetching and should reuse the in-flight future
        val t2 = Thread {
            fetchStarted.await() // wait until t1 is blocked inside fetch
            results += cache.getOrLoad("k") { fetchCount.incrementAndGet(); "value" }
        }

        t1.start()
        t2.start()
        Thread.sleep(20) // give t2 time to block on the in-flight future
        fetchProceed.countDown()
        t1.join(1_000)
        t2.join(1_000)

        assertEquals(1, fetchCount.get(), "fetch must run exactly once")
        assertEquals(2, results.size, "both callers must receive a result")
        assertTrue(results.all { it == "value" })
    }

    // ── Exception handling ────────────────────────────────────────────────────

    @Test
    fun `failed fetch is not cached and the next call retries`() {
        var shouldFail = true
        val cache = FireflyCache<Unit, String>()
        assertThrows<RuntimeException> {
            cache.getOrLoad(Unit) { if (shouldFail) throw RuntimeException("boom") else "ok" }
        }
        shouldFail = false
        assertEquals("ok", cache.getOrLoad(Unit) { "ok" })
    }
}
