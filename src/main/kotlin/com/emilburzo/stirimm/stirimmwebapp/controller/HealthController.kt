package com.emilburzo.stirimm.stirimmwebapp.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(private val dataSource: DataSource) {

    @GetMapping("/healthz")
    fun health(): ResponseEntity<String> {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute("SELECT 1") }
        }
        return ResponseEntity.ok("ok")
    }
}
