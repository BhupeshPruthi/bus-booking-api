package com.mybus.app.ui.util

import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.remote.dto.BusDetailData
import com.mybus.app.data.remote.dto.BusListItem

data class SeatAvailability(
    val availableSeats: Int,
    val reservedSeats: Int,
    val bookableSeats: Int,
    val totalSeats: Int
)

private val releasedSeatStatuses = setOf("cancelled", "rejected", "expired")

private fun BookingData.reservesSeat(): Boolean {
    return status !in releasedSeatStatuses
}

private fun bookableSeatCount(totalSeats: Int, seatStartNumber: Int, serverBookableSeats: Int?): Int {
    return serverBookableSeats ?: (totalSeats - seatStartNumber + 1).coerceAtLeast(0)
}

private fun buildSeatAvailability(
    totalSeats: Int,
    seatStartNumber: Int,
    serverBookableSeats: Int?,
    reservedSeats: Int,
    availableSeats: Int? = null
): SeatAvailability {
    val bookableSeats = bookableSeatCount(totalSeats, seatStartNumber, serverBookableSeats)
    val normalizedReserved = reservedSeats.coerceAtLeast(0)
    val normalizedAvailable = availableSeats
        ?: (bookableSeats - normalizedReserved).coerceAtLeast(0)

    return SeatAvailability(
        availableSeats = normalizedAvailable.coerceIn(0, bookableSeats),
        reservedSeats = normalizedReserved,
        bookableSeats = bookableSeats,
        totalSeats = totalSeats
    )
}

fun BusListItem.seatAvailability(): SeatAvailability {
    return buildSeatAvailability(
        totalSeats = totalSeats,
        seatStartNumber = seatStartNumber,
        serverBookableSeats = bookableSeats,
        reservedSeats = bookedSeats,
        availableSeats = availableSeats
    )
}

fun BusListItem.seatAvailabilityWithPair(pair: BusListItem?): SeatAvailability {
    val own = seatAvailability()
    if (tripType != "round_trip" || pair == null || pair.id == id) {
        return own
    }

    if (bookableSeats != null && pair.bookableSeats != null) {
        return own
    }

    val paired = pair.seatAvailability()
    val reservedSeats = own.reservedSeats + paired.reservedSeats
    return SeatAvailability(
        availableSeats = (own.bookableSeats - reservedSeats).coerceIn(0, own.bookableSeats),
        reservedSeats = reservedSeats.coerceAtLeast(0),
        bookableSeats = own.bookableSeats,
        totalSeats = own.totalSeats
    )
}

fun BusDetailData.seatAvailability(): SeatAvailability {
    return buildSeatAvailability(
        totalSeats = totalSeats,
        seatStartNumber = seatStartNumber,
        serverBookableSeats = bookableSeats,
        reservedSeats = bookedSeats,
        availableSeats = availableSeats
    )
}

fun BusDetailData.seatAvailabilityFromBookings(bookings: List<BookingData>): SeatAvailability {
    val reservingBusIds = setOfNotNull(id, returnBusId.takeIf { tripType == "round_trip" })
    val reservedSeats = bookings
        .filter { it.busId == null || it.busId in reservingBusIds }
        .filter { it.reservesSeat() }
        .sumOf { it.seatCount }

    return buildSeatAvailability(
        totalSeats = totalSeats,
        seatStartNumber = seatStartNumber,
        serverBookableSeats = bookableSeats,
        reservedSeats = reservedSeats
    )
}
