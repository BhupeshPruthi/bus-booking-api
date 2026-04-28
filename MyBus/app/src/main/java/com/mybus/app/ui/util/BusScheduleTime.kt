package com.mybus.app.ui.util

import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val BusScheduleZoneId: ZoneId = ZoneId.of("Asia/Kolkata")

private val CompactDateFormatter = DateTimeFormatter.ofPattern("dd MMM")
private val CompactDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")
private val FullDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

fun toBusScheduleApiDateTime(date: LocalDate, time: LocalTime): String {
    return ZonedDateTime.of(date, time, BusScheduleZoneId)
        .toInstant()
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

fun parseBusScheduleInstantOrNull(isoString: String?): Instant? {
    if (isoString.isNullOrBlank()) return null
    return try {
        Instant.parse(isoString)
    } catch (_: Exception) {
        null
    }
}

fun formatBusScheduleDate(isoString: String): String {
    return try {
        Instant.parse(isoString).atZone(BusScheduleZoneId).format(CompactDateFormatter)
    } catch (_: Exception) {
        isoString
    }
}

fun formatBusScheduleDateTime(isoString: String): String {
    return try {
        Instant.parse(isoString).atZone(BusScheduleZoneId).format(CompactDateTimeFormatter)
    } catch (_: Exception) {
        isoString
    }
}

fun formatBusScheduleDateTimeFull(isoString: String): String {
    return try {
        Instant.parse(isoString).atZone(BusScheduleZoneId).format(FullDateTimeFormatter)
    } catch (_: Exception) {
        isoString
    }
}

fun formatBusScheduleDuration(startIso: String?, endIso: String?): String? {
    val start = parseBusScheduleInstantOrNull(startIso)
    val end = parseBusScheduleInstantOrNull(endIso)
    if (start == null || end == null || end.isBefore(start)) return null

    val totalMinutes = Duration.between(start, end).toMinutes()
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || isEmpty()) add("${minutes}m")
    }
    return parts.joinToString(" ")
}
