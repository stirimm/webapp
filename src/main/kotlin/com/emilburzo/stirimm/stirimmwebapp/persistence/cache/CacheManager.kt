package com.emilburzo.stirimm.stirimmwebapp.persistence.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import java.util.concurrent.TimeUnit


@Bean
fun cacheManager(): CacheManager {
    val cacheManager = SimpleCacheManager()
    val newsCache = CaffeineCache(
        CACHE_NAME_RECENT_NEWS,
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1)
            .recordStats()
            .build()
    )
    cacheManager.setCaches(listOf(newsCache))
    return cacheManager
}

const val CACHE_NAME_RECENT_NEWS = "cache_recent_news"