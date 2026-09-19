@file:OptIn(UnstableApi::class)

package com.extremestudios.cinearena.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    private const val CACHE_FOLDER = "film_cache"
    private const val MAX_CACHE_SIZE_BYTES = 500L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null
    @Volatile
    private var dataSourceFactory: CacheDataSource.Factory? = null

    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        dataSourceFactory?.let { return it }

        synchronized(this) {
            dataSourceFactory?.let { return it }

            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_FOLDER)
            val cache = simpleCache ?: SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
                StandaloneDatabaseProvider(appContext)
            ).also { simpleCache = it }

            val upstreamFactory = DefaultDataSource.Factory(appContext)
            val factory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setEventListener(
                    object : CacheDataSource.EventListener {
                        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                            Log.d(
                                TAG,
                                "Cache hit/read. cacheSizeBytes=$cacheSizeBytes cachedBytesRead=$cachedBytesRead"
                            )
                        }

                        override fun onCacheIgnored(reason: Int) {
                            Log.d(TAG, "Cache ignored. reason=$reason")
                        }
                    }
                )

            dataSourceFactory = factory
            return factory
        }
    }
}
