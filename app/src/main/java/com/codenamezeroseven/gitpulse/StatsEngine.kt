package com.codenamezeroseven.gitpulse

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

object StatsEngine {

    fun dailyCommits(events: List<GhEvent>): Map<LocalDate, Int> =
        events.asSequence()
            .filter { it.type == "PushEvent" && it.commits > 0 }
            .groupBy { it.createdAt }
            .mapValues { (_, es) -> es.sumOf { it.commits } }

    fun streak(daily: Map<LocalDate, Int>): Int {
        var d = LocalDate.now()
        if ((daily[d] ?: 0) == 0) d = d.minusDays(1)
        var s = 0
        while ((daily[d] ?: 0) > 0) {
            s++
            d = d.minusDays(1)
        }
        return s
    }

    fun bestStreak(daily: Map<LocalDate, Int>): Int {
        val days = daily.filter { it.value > 0 }.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && d == prev!!.plusDays(1)) run + 1 else 1
            best = max(best, run)
            prev = d
        }
        return best
    }

    fun activeDays(daily: Map<LocalDate, Int>): Int = daily.count { it.value > 0 }

    fun weekValues(daily: Map<LocalDate, Int>): Pair<List<String>, List<Int>> {
        val labels = mutableListOf<String>()
        val values = mutableListOf<Int>()
        for (i in 6 downTo 0) {
            val d = LocalDate.now().minusDays(i.toLong())
            labels += d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).take(1)
            values += daily[d] ?: 0
        }
        return labels to values
    }

    fun weeklyTotal(daily: Map<LocalDate, Int>): Int {
        var sum = 0
        for (i in 0..6) sum += daily[LocalDate.now().minusDays(i.toLong())] ?: 0
        return sum
    }

    fun daysAgo(date: LocalDate): Long = ChronoUnit.DAYS.between(date, LocalDate.now())
}
