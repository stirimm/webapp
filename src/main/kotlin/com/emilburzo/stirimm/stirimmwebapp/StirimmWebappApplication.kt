package com.emilburzo.stirimm.stirimmwebapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class StirimmWebappApplication

fun main(args: Array<String>) {
    runApplication<StirimmWebappApplication>(*args)
}
