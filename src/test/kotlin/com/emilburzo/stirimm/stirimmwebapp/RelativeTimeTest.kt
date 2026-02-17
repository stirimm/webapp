package com.emilburzo.stirimm.stirimmwebapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RelativeTimeTest {

    private val now = LocalDateTime.of(2026, 2, 17, 12, 0, 0)

    // --- seconds (< 1 minute) ---

    @Test
    fun `just now - 0 seconds`() {
        assertEquals("chiar acum", formatTimeAgo(now, now))
    }

    @Test
    fun `just now - 30 seconds`() {
        assertEquals("chiar acum", formatTimeAgo(now.minusSeconds(30), now))
    }

    @Test
    fun `just now - 59 seconds`() {
        assertEquals("chiar acum", formatTimeAgo(now.minusSeconds(59), now))
    }

    // --- minutes ---

    @Test
    fun `1 minute - singular`() {
        assertEquals("acum 1 minut", formatTimeAgo(now.minusMinutes(1), now))
    }

    @Test
    fun `2 minutes - few`() {
        assertEquals("acum 2 minute", formatTimeAgo(now.minusMinutes(2), now))
    }

    @Test
    fun `5 minutes - few`() {
        assertEquals("acum 5 minute", formatTimeAgo(now.minusMinutes(5), now))
    }

    @Test
    fun `19 minutes - few`() {
        assertEquals("acum 19 minute", formatTimeAgo(now.minusMinutes(19), now))
    }

    @Test
    fun `20 minutes - other, requires 'de' particle`() {
        assertEquals("acum 20 de minute", formatTimeAgo(now.minusMinutes(20), now))
    }

    @Test
    fun `45 minutes - other`() {
        assertEquals("acum 45 de minute", formatTimeAgo(now.minusMinutes(45), now))
    }

    @Test
    fun `59 minutes`() {
        assertEquals("acum 59 de minute", formatTimeAgo(now.minusMinutes(59), now))
    }

    // --- hours ---

    @Test
    fun `1 hour - singular`() {
        assertEquals("acum 1 oră", formatTimeAgo(now.minusHours(1), now))
    }

    @Test
    fun `2 hours - few`() {
        assertEquals("acum 2 ore", formatTimeAgo(now.minusHours(2), now))
    }

    @Test
    fun `13 hours - few`() {
        assertEquals("acum 13 ore", formatTimeAgo(now.minusHours(13), now))
    }

    @Test
    fun `19 hours - few`() {
        assertEquals("acum 19 ore", formatTimeAgo(now.minusHours(19), now))
    }

    @Test
    fun `20 hours - other`() {
        assertEquals("acum 20 de ore", formatTimeAgo(now.minusHours(20), now))
    }

    @Test
    fun `23 hours`() {
        assertEquals("acum 23 de ore", formatTimeAgo(now.minusHours(23), now))
    }

    // --- days ---

    @Test
    fun `1 day - singular`() {
        assertEquals("acum 1 zi", formatTimeAgo(now.minusDays(1), now))
    }

    @Test
    fun `2 days - few`() {
        assertEquals("acum 2 zile", formatTimeAgo(now.minusDays(2), now))
    }

    @Test
    fun `6 days`() {
        assertEquals("acum 6 zile", formatTimeAgo(now.minusDays(6), now))
    }

    // --- weeks ---

    @Test
    fun `1 week - singular`() {
        assertEquals("acum 1 săptămână", formatTimeAgo(now.minusWeeks(1), now))
    }

    @Test
    fun `2 weeks - few`() {
        assertEquals("acum 2 săptămâni", formatTimeAgo(now.minusWeeks(2), now))
    }

    @Test
    fun `3 weeks`() {
        assertEquals("acum 3 săptămâni", formatTimeAgo(now.minusWeeks(3), now))
    }

    // --- months ---

    @Test
    fun `1 month - singular`() {
        assertEquals("acum 1 lună", formatTimeAgo(now.minusDays(30), now))
    }

    @Test
    fun `2 months - few`() {
        assertEquals("acum 2 luni", formatTimeAgo(now.minusDays(60), now))
    }

    @Test
    fun `11 months`() {
        assertEquals("acum 11 luni", formatTimeAgo(now.minusDays(335), now))
    }

    // --- years ---

    @Test
    fun `1 year - singular`() {
        assertEquals("acum 1 an", formatTimeAgo(now.minusDays(365), now))
    }

    @Test
    fun `2 years - few`() {
        assertEquals("acum 2 ani", formatTimeAgo(now.minusDays(730), now))
    }

    @Test
    fun `5 years - few`() {
        assertEquals("acum 5 ani", formatTimeAgo(now.minusDays(1825), now))
    }

    // --- plural edge cases (Romanian "de" particle) ---

    @Test
    fun `120 minutes is 2 hours`() {
        assertEquals("acum 2 ore", formatTimeAgo(now.minusMinutes(120), now))
    }

    @Test
    fun `20 years - other`() {
        assertEquals("acum 20 de ani", formatTimeAgo(now.minusDays(20 * 365L), now))
    }

    @Test
    fun `formatRo - verify 'de' particle logic directly`() {
        // The "de" particle appears for values where n % 100 is in 20..99
        // For hours this means >= 20 hours (still within the hour range < 24)
        assertEquals("acum 20 de ore", formatTimeAgo(now.minusHours(20), now))
        assertEquals("acum 21 de ore", formatTimeAgo(now.minusHours(21), now))
        assertEquals("acum 22 de ore", formatTimeAgo(now.minusHours(22), now))
        assertEquals("acum 23 de ore", formatTimeAgo(now.minusHours(23), now))
    }

    @Test
    fun `formatRo - verify few form for minutes in range`() {
        // 2-19 should use "few" form (no "de" particle)
        for (m in 2L..19L) {
            assertEquals("acum $m minute", formatTimeAgo(now.minusMinutes(m), now))
        }
    }

    @Test
    fun `formatRo - verify other form for minutes 20 and above`() {
        // 20-59 should use "other" form (with "de" particle)
        for (m in listOf(20L, 30L, 45L, 59L)) {
            assertEquals("acum $m de minute", formatTimeAgo(now.minusMinutes(m), now))
        }
    }

    // --- boundary between units ---

    @Test
    fun `60 seconds becomes 1 minute`() {
        assertEquals("acum 1 minut", formatTimeAgo(now.minusSeconds(60), now))
    }

    @Test
    fun `60 minutes becomes 1 hour`() {
        assertEquals("acum 1 oră", formatTimeAgo(now.minusMinutes(60), now))
    }

    @Test
    fun `24 hours becomes 1 day`() {
        assertEquals("acum 1 zi", formatTimeAgo(now.minusHours(24), now))
    }

    @Test
    fun `7 days becomes 1 week`() {
        assertEquals("acum 1 săptămână", formatTimeAgo(now.minusDays(7), now))
    }
}
