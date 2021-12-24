package com.emilburzo.stirimm.stirimmwebapp.persistence.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.scheduling.annotation.Scheduled

class CacheMonitor(private val cache: Cache?) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = (1000 * 60 * 30).toLong()) // ms
    fun monitor() {
        cache ?: logger.info("no cache")
        val stats = (cache as CaffeineCache).nativeCache.stats()
        logger.info("$stats")
    }
}
