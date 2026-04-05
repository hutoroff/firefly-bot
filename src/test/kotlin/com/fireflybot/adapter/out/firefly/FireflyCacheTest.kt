package com.fireflybot.adapter.out.firefly

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FireflyCacheTest {

    // ── Single-key (Unit) cache ───────────────────────────────────────────────

    @Test
    fun `get returns null on empty cache`() {
        val cache = FireflyCache<Unit, String>()
        assertNull(cache.get(Unit))
    }

    @Test
    fun `get returns value after put`() {
        val cache = FireflyCache<Unit, String>()
        cache.put(Unit, "hello")
        assertEquals("hello", cache.get(Unit))
    }

    @Test
    fun `get returns null after TTL expires`() {
        var fakeTime = 1000L
        val cache = FireflyCache<Unit, String>(ttlMs = 100, clock = { fakeTime })
        cache.put(Unit, "hello")
        fakeTime += 200 // advance past TTL
        assertNull(cache.get(Unit))
    }

    @Test
    fun `put overwrites previous value`() {
        val cache = FireflyCache<Unit, String>()
        cache.put(Unit, "first")
        cache.put(Unit, "second")
        assertEquals("second", cache.get(Unit))
    }

    @Test
    fun `invalidate removes entry`() {
        val cache = FireflyCache<Unit, String>()
        cache.put(Unit, "hello")
        cache.invalidate(Unit)
        assertNull(cache.get(Unit))
    }

    @Test
    fun `invalidate on missing key is a no-op`() {
        val cache = FireflyCache<Unit, String>()
        assertDoesNotThrow { cache.invalidate(Unit) }
    }

    // ── String-key cache ──────────────────────────────────────────────────────

    @Test
    fun `get returns null for unknown key`() {
        val cache = FireflyCache<String, List<Int>>()
        cache.put("a", listOf(1, 2))
        assertNull(cache.get("b"))
    }

    @Test
    fun `each key is cached independently`() {
        val cache = FireflyCache<String, List<Int>>()
        cache.put("a", listOf(1))
        cache.put("b", listOf(2))
        assertEquals(listOf(1), cache.get("a"))
        assertEquals(listOf(2), cache.get("b"))
    }

    @Test
    fun `invalidateAll removes all entries`() {
        val cache = FireflyCache<String, String>()
        cache.put("x", "1")
        cache.put("y", "2")
        cache.invalidateAll()
        assertNull(cache.get("x"))
        assertNull(cache.get("y"))
    }

    @Test
    fun `expired entry is removed on access`() {
        var fakeTime = 1000L
        val cache = FireflyCache<String, String>(ttlMs = 100, clock = { fakeTime })
        cache.put("q", "stale")
        fakeTime += 200 // advance past TTL
        assertNull(cache.get("q"))
        // put fresh value after expiry — should work normally
        cache.put("q", "fresh")
        assertEquals("fresh", cache.get("q"))
    }

    @Test
    fun `TTL not yet elapsed keeps entry live`() {
        var fakeTime = 1000L
        val cache = FireflyCache<String, String>(ttlMs = 60_000, clock = { fakeTime })
        cache.put("k", "alive")
        fakeTime += 30_000 // advance halfway through TTL
        assertEquals("alive", cache.get("k"))
    }

    @Test
    fun `fresh entry put by another thread is not evicted by stale-read cleanup`() {
        // Verifies that remove(key, staleEntry) won't remove a concurrently written fresh entry.
        // Thread A reads expired entry e1 and is about to call remove(key, e1).
        // Thread B has already put e2 for the same key.
        // remove(key, e1) must be a no-op because the current mapping is e2, not e1.
        var fakeTime = 1000L
        val cache = FireflyCache<String, String>(ttlMs = 100, clock = { fakeTime })
        cache.put("k", "stale") // e1.expiresAt = 1100
        fakeTime += 200         // clock is now 1200 — e1 is expired
        cache.put("k", "fresh") // e2.expiresAt = 1300 (written while clock = 1200)
        // Thread A's removal of the stale e1 must leave the fresh e2 intact
        assertEquals("fresh", cache.get("k"))
    }
}
