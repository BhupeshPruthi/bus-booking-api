package com.mybus.app.ui.chalisa

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Marathon times and reading days follow India time, whatever the phone's own time zone. */
val ChalisaZone: ZoneId = ZoneId.of("Asia/Kolkata")

// NumberFormat isn't thread-safe, and the lock-screen counter formats off the main thread.
private val indianNumbers = ThreadLocal.withInitial { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** 124580 → "1,24,580" */
fun formatCount(value: Long): String = indianNumbers.get()!!.format(value)
fun formatCount(value: Int): String = formatCount(value.toLong())

fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun todayInIndia(): LocalDate = LocalDate.now(ChalisaZone)

/** "Wed, 1 Oct, 6:00 AM" */
fun formatStart(instant: Instant): String {
    val local = instant.atZone(ChalisaZone)
    return "${local.format(dayFormatter)}, ${local.format(timeFormatter)}"
}

/** "6:38 PM", or "Fri, 10 Oct, 6:38 PM" when it isn't today. */
fun formatLastRound(instant: Instant): String {
    val local = instant.atZone(ChalisaZone)
    return if (local.toLocalDate() == todayInIndia()) local.format(timeFormatter)
    else "${local.format(dayFormatter)}, ${local.format(timeFormatter)}"
}

fun formatTime(instant: Instant): String = instant.atZone(ChalisaZone).format(timeFormatter)

fun formatDate(instant: Instant): String = instant.atZone(ChalisaZone).format(dateFormatter)

fun formatShortDate(instant: Instant): String = instant.atZone(ChalisaZone).format(shortDateFormatter)

fun formatMonth(month: YearMonth): String = month.format(monthFormatter)

/** "Today, 11 Oct" / "Fri, 10 Oct" for a reading day. */
fun formatHistoryDay(date: String): String {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val label = day.format(dayFormatter)
    return when (day) {
        todayInIndia() -> "Today, ${day.format(shortDateFormatter)}"
        todayInIndia().minusDays(1) -> "Yesterday, ${day.format(shortDateFormatter)}"
        else -> label
    }
}

/** "in 2 days 14 h", "in 3 h 20 min", "in 5 min", "in under a minute" */
fun formatStartsIn(start: Instant, now: Instant): String {
    val remaining = Duration.between(now, start)
    if (remaining.isNegative || remaining.isZero) return "now"
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    return when {
        days > 0 -> if (hours > 0) "in $days ${if (days == 1L) "day" else "days"} $hours h" else "in $days ${if (days == 1L) "day" else "days"}"
        hours > 0 -> if (minutes > 0) "in $hours h $minutes min" else "in $hours h"
        minutes > 0 -> "in $minutes min"
        else -> "in under a minute"
    }
}
