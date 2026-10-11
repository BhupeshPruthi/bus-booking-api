package com.mybus.app.ui.events

import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Events are scheduled against the organisation's calendar, rather than the
 * timezone configured on an individual device.
 */
val EventBusinessTimeZone: ZoneId = ZoneId.of("Asia/Kolkata")

fun LocalDate.eventEndInstant(): Instant = atTime(LocalTime.of(23, 59))
    .atZone(EventBusinessTimeZone)
    .toInstant()

fun Instant.eventBusinessDate(): LocalDate = atZone(EventBusinessTimeZone).toLocalDate()
