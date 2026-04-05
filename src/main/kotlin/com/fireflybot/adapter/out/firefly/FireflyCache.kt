package com.fireflybot.adapter.out.firefly

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple TTL-based in-memory cache for Firefly III data that changes rarely.
 * Thread-safe via ConcurrentHashMap. Stale-entry cleanup uses `remove(key, value)`
 * so a concurrently written fresh entry is never evicted by a racing expired-read.
 *
 * @param clock injectable time source — override in tests to advance time deterministically.
 */
class FireflyCache<K : Any, V>(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val e = entries[key] ?: return null
        return if (clock() < e.expiresAt) {
            e.value
        } else {
            entries.remove(key, e) // conditional: won't evict a fresh entry put by another thread
            null
        }
    }

    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock() + ttlMs)
    }

    fun invalidate(key: K) {
        entries.remove(key)
    }

    fun invalidateAll() {
        entries.clear()
    }

    companion object {
        /** 30 minutes — long enough to be effective, short enough to pick up rare changes. */
        const val DEFAULT_TTL_MS = 30L * 60 * 1000
    }
}
