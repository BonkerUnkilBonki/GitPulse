package com.codenamezeroseven.gitpulse

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TimeAgo {
    fun since(date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, LocalDate.now())
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            days < 30 -> "${days / 7} week${if (days / 7 == 1L) "" else "s"} ago"
            days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"} ago"
            else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
        }
    }
}
