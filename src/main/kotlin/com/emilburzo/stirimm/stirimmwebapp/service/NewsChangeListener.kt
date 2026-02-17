package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.util.*

@Component
class NewsChangeListener(
    private val newsService: NewsService,
    private val newsRepository: NewsRepository,
    @Value("\${spring.datasource.url}") private val dbUrl: String,
    @Value("\${spring.datasource.username}") private val dbUser: String,
    @Value("\${spring.datasource.password}") private val dbPassword: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var lastKnownMaxId: Long? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        logger.info("Warming cache on startup")
        newsService.refresh()
        lastKnownMaxId = newsRepository.findMaxId()

        if (dbUrl.startsWith("jdbc:postgresql")) {
            Thread(::listenLoop, "pg-notify-listener").apply {
                isDaemon = true
                start()
            }
        } else {
            logger.info("Not PostgreSQL — LISTEN/NOTIFY disabled, using safety check only")
        }
    }

    private fun listenLoop() {
        while (true) {
            try {
                val props = Properties().apply {
                    setProperty("user", dbUser)
                    setProperty("password", dbPassword)
                    setProperty("tcpKeepAlive", "true")
                }
                DriverManager.getConnection(dbUrl, props).use { conn ->
                    conn.unwrap(PGConnection::class.java)
                    conn.createStatement().execute("LISTEN news_changed")
                    logger.info("Listening for news_changed notifications")

                    while (true) {
                        val pgConn = conn.unwrap(PGConnection::class.java)
                        val notifications = pgConn.getNotifications(30_000)
                        if (notifications != null && notifications.isNotEmpty()) {
                            logger.info("Received news_changed notification, refreshing cache")
                            newsService.refresh()
                            lastKnownMaxId = newsRepository.findMaxId()
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
