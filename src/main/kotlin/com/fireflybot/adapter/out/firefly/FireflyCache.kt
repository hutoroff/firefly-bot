package com.fireflybot.adapter.out.firefly

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * TTL-based in-memory cache with single-flight protection: when multiple threads
 * request the same cold or stale key simultaneously, exactly one fetch runs and all
 * waiters share its result via a [CompletableFuture].
 *
 * Thread-safety notes:
 * - [entries] read/write is done through ConcurrentHashMap.
 * - Stale-entry cleanup uses [ConcurrentHashMap.remove] with the old value so a
 *   concurrently written fresh entry is never accidentally evicted.
 * - [inFlight] serialises concurrent fetches per key via [ConcurrentHashMap.putIfAbsent].
 *
 * @param clock injectable time source — override in tests to advance time deterministically.
 */
class FireflyCache<K : Any, V>(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val inFlight = ConcurrentHashMap<K, CompletableFuture<V>>()

    /**
     * Returns the cached value for [key] if still live, otherwise invokes [fetch] exactly
     * once — even when multiple threads arrive simultaneously on a cold or stale key.
     * Concurrent callers block on the in-flight [CompletableFuture] and share its result.
     */
    fun getOrLoad(key: K, fetch: () -> V): V {
        // Fast path: valid cached entry
        entries[key]?.let { e ->
            if (clock() < e.expiresAt) return e.value
            entries.remove(key, e) // conditional: won't evict a fresh entry written by another thread
        }

        // Register as the fetcher; if another thread beat us, wait for its result.
        val newFuture = CompletableFuture<V>()
        val existingFuture = inFlight.putIfAbsent(key, newFuture)
        if (existingFuture != null) return existingFuture.get()

        // We own the fetch.
        return try {
            val value = fetch()
            entries[key] = Entry(value, clock() + ttlMs)
            newFuture.complete(value)
            value
        } catch (ex: Exception) {
            newFuture.completeExceptionally(ex)
            throw ex
        } finally {
            inFlight.remove(key, newFuture)
        }
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
