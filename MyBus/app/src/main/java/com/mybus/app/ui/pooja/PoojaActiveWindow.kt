package com.mybus.app.ui.pooja

import java.time.Duration
import java.time.Instant

internal const val POOJA_BOOKING_NOT_STARTED = "not_started"
internal const val POOJA_BOOKING_OPEN = "open"
internal const val POOJA_BOOKING_FULL = "full"
internal const val POOJA_BOOKING_EXPIRED = "expired"

private val PoojaBookingWindow = Duration.ofHours(8)

internal fun poojaBookingStatus(
    scheduledAt: String,
    availableTokens: Int,
    serverStatus: String? = null,
    now: Instant = Instant.now()
): String {
    if (!serverStatus.isNullOrBlank()) return serverStatus
    return try {
        val opensAt = Instant.parse(scheduledAt)
        val closesAt = opensAt.plus(PoojaBookingWindow)
        when {
            now.isBefore(opensAt) -> POOJA_BOOKING_NOT_STARTED
            !now.isBefore(closesAt) -> POOJA_BOOKING_EXPIRED
            availableTokens <= 0 -> POOJA_BOOKING_FULL
            else -> POOJA_BOOKING_OPEN
        }
    } catch (_: Exception) {
        POOJA_BOOKING_EXPIRED
    }
}

internal fun canBookPooja(
    scheduledAt: String,
    availableTokens: Int,
    serverCanBook: Boolean? = null,
    serverStatus: String? = null,
    now: Instant = Instant.now()
): Boolean {
    return serverCanBook ?: (poojaBookingStatus(scheduledAt, availableTokens, serverStatus, now) == POOJA_BOOKING_OPEN)
}
