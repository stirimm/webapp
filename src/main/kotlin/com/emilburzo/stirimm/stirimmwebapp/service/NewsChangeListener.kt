package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

@Component
class NewsChangeListener(
    private val newsService: NewsService,
    private val newsRepository: NewsRepository,
    private val dataSource: DataSource,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var lastKnownMaxId: Long? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        logger.info("Warming cache on startup")
        newsService.refresh()
        lastKnownMaxId = newsRepository.findMaxId()

        val hikari = dataSource as? HikariDataSource
        if (hikari != null && hikari.jdbcUrl.startsWith("jdbc:postgresql")) {
            Thread({ listenLoop(hikari) }, "pg-notify-listener").apply {
                isDaemon = true
                start()
            }
        } else {
            logger.info("Not PostgreSQL — LISTEN/NOTIFY disabled, using safety check only")
        }
    }

    private fun listenLoop(hikari: HikariDataSource) {
        while (true) {
            try {
                val props = Properties().apply {
                    setProperty("user", hikari.username)
                    setProperty("password", hikari.password ?: "")
                    setProperty("tcpKeepAlive", "true")
                }
                DriverManager.getConnection(hikari.jdbcUrl, props).use { conn ->
                    val pgConn = conn.unwrap(PGConnection::class.java)
                    conn.createStatement().execute("LISTEN news_changed")
                    logger.info("Listening for news_changed notifications")

                    while (true) {
                        val notifications = pgConn.getNotifications(30_000)
                        if (notifications != null && notifications.isNotEmpty()) {
                            logger.info("Received news_changed notification, refreshing cache")
                            try {
                                newsService.refresh()
                                lastKnownMaxId = newsRepository.findMaxId()
                            } catch (e: Exception) {
                                logger.error("Failed to refresh cache after notification: {}", e.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("LISTEN connection error: {}. Reconnecting in 5s", e.message)
                try {
                    Thread.sleep(5000)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun safetyCheck() {
        try {
            val currentMaxId = newsRepository.findMaxId()
            if (currentMaxId != null && currentMaxId != lastKnownMaxId) {
                logger.warn(
                    "Safety check detected missed change (lastKnown={}, current={}), refreshing cache",
                    lastKnownMaxId, currentMaxId
                )
                newsService.refresh()
                lastKnownMaxId = currentMaxId
            }
        } catch (e: Exception) {
            logger.error("Safety check failed: {}", e.message)
        }
    }
}
