package com.flightaware.android.flightfeeder.util

import android.os.SystemClock
import android.util.LruCache

/**
 * @author Baron
 *
 * This class is thread-safe. All operations are synchronized.
 *
 * @param <K>
 * An object to be used as a key.
 * @param <V>
 * An object that you wish to store and retrieve
</V></K> */
class TimedLruCache<K, V> @JvmOverloads constructor(maxSize: Int, maxAge: Long = 60 * 1000.toLong()) {
    class ValueHolder<V>(val value: V, var mTimestamp: Long)

    private val mCache: LruCache<K, ValueHolder<V>>
    private val mMaxAge: Long
    fun evictAll() {
        synchronized(mCache) { mCache.evictAll() }
    }

    operator fun get(key: K): V? {
        synchronized(mCache) {
            val now = SystemClock.uptimeMillis()
            removeExpired(now)
            val holder = mCache[key] ?: return null
            holder.mTimestamp = now
            return holder.value
        }
    }

    fun put(key: K?, value: V?): V? {
        synchronized(mCache) {
            if (key == null || value == null) return null
            val holder = mCache.put(key, ValueHolder(value,
                    SystemClock.uptimeMillis()))
                    ?: return null
            return holder.value
        }
    }

    fun remove(key: K): V? {
        synchronized(mCache) {
            val holder = mCache.remove(key) ?: return null
            return holder.value
        }
    }

    private fun removeExpired(now: Long) {
        synchronized(mCache) {
            val it = mCache.snapshot().values.iterator()
            while (it.hasNext()) {
                val holder = it.next()
                if (now - mMaxAge > holder.mTimestamp) it.remove()
            }
        }
    }

    fun snapshot(): Map<K, ValueHolder<V>> {
        synchronized(mCache) { return mCache.snapshot() }
    }
    /**
     * @param maxSize
     * The maximum number of items this cache can store before
     * evicting old items.
     * @param maxAge
     * The maximum time an item can live in the cache without being
     * accessed - milliseconds
     */
    /**
     * A TimedLruCache configured with a default expiration of 60 seconds.
     *
     * @param maxSize
     * The maximum number of items this cache can store before
     * evicting old items.
     */
    init {
        mCache = LruCache(maxSize)
        mMaxAge = maxAge
    }
}