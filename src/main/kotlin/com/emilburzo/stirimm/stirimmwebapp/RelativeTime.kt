package com.emilburzo.stirimm.stirimmwebapp

import java.time.Duration
import java.time.LocalDateTime

/**
 * Romanian relative time formatter using CLDR plural rules.
 *
 * Romanian has three plural categories:
 * - one:   n == 1
 * - few:   n == 0 or n % 100 in 2..19
 * - other: n % 100 in 20..99 (requires "de" particle)
 */
fun formatTimeAgo(published: LocalDateTime, now: LocalDateTime): String {
    val duration = Duration.between(published, now)
    val totalSeconds = duration.seconds

    return when {
        totalSeconds < 60 -> "chiar acum"
        totalSeconds < 3600 -> formatRo(totalSeconds / 60, "minut", "minute", "de minute")
        totalSeconds < 86400 -> formatRo(totalSeconds / 3600, "oră", "ore", "de ore")
        totalSeconds < 604800 -> formatRo(totalSeconds / 86400, "zi", "zile", "de zile")
        totalSeconds < 2592000 -> formatRo(totalSeconds / 604800, "săptămână", "săptămâni", "de săptămâni")
        totalSeconds < 31536000 -> formatRo(totalSeconds / 2592000, "lună", "luni", "de luni")
        else -> formatRo(totalSeconds / 31536000, "an", "ani", "de ani")
    }
}

private fun formatRo(value: Long, one: String, few: String, other: String): String {
    val mod100 = (value % 100).toInt()
    val unit = when {
        value == 1L -> one
        mod100 in 0..1 || mod100 in 2..19 -> few
        else -> other
    }
    return "acum $value $unit"
}
