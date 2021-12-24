package com.emilburzo.stirimm.stirimmwebapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StirimmWebappApplication

fun main(args: Array<String>) {
    runApplication<StirimmWebappApplication>(*args)
}
