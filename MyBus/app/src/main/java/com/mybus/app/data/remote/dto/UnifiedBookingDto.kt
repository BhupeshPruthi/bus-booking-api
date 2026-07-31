package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnifiedBookingPage(
    @Json(name = "items") val items: List<UnifiedBookingItem>,
    @Json(name = "counts") val counts: UnifiedBookingCounts,
    @Json(name = "nextCursor") val nextCursor: String? = null,
    @Json(name = "serverTime") val serverTime: String
)

@JsonClass(generateAdapter = true)
data class UnifiedBookingCounts(
    @Json(name = "upcoming") val upcoming: Int = 0,
    @Json(name = "past") val past: Int = 0,
    @Json(name = "failed") val failed: Int = 0
)

@JsonClass(generateAdapter = true)
data class UnifiedBookingItem(
    @Json(name = "id") val id: String,
    @Json(name = "bookingType") val bookingType: String,
    @Json(name = "bookingId") val bookingId: String,
    @Json(name = "reference") val reference: String,
    @Json(name = "rawStatus") val rawStatus: String,
    @Json(name = "normalizedStatus") val normalizedStatus: String,
    @Json(name = "startsAt") val startsAt: String,
    @Json(name = "endsAt") val endsAt: String,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "totalAmount") val totalAmount: Double? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "subtitle") val subtitle: String,
    @Json(name = "details") val details: UnifiedBookingDetails,
    @Json(name = "availableActions") val availableActions: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UnifiedBookingDetails(
    // Bus
    @Json(name = "busName") val busName: String? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "destination") val destination: String? = null,
    @Json(name = "pickupPoint") val pickupPoint: String? = null,
    @Json(name = "seatCount") val seatCount: Int? = null,
    @Json(name = "assignedSeats") val assignedSeats: String? = null,
    @Json(name = "passengerName") val passengerName: String? = null,
    @Json(name = "passengerPhone") val passengerPhone: String? = null,
    @Json(name = "tripType") val tripType: String? = null,

    // Stay
    @Json(name = "checkInDate") val checkInDate: String? = null,
    @Json(name = "checkOutDate") val checkOutDate: String? = null,
    @Json(name = "nightCount") val nightCount: Int? = null,
    @Json(name = "guestCount") val guestCount: Int? = null,
    @Json(name = "contactName") val contactName: String? = null,
    @Json(name = "contactEmail") val contactEmail: String? = null,
    @Json(name = "contactPhone") val contactPhone: String? = null,
    @Json(name = "subtotalAmount") val subtotalAmount: Double? = null,
    @Json(name = "discountAmount") val discountAmount: Double = 0.0,
    @Json(name = "couponCode") val couponCode: String? = null,
    @Json(name = "items") val items: List<UnifiedStayItem> = emptyList(),
    @Json(name = "rejectionReason") val rejectionReason: String? = null,

    // Pooja
    @Json(name = "poojaId") val poojaId: String? = null,
    @Json(name = "place") val place: String? = null,
    @Json(name = "tokenNumber") val tokenNumber: Int? = null,
    @Json(name = "memberCount") val memberCount: Int? = null,
    @Json(name = "city") val city: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "phone") val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class UnifiedStayItem(
    @Json(name = "unitTypeCode") val unitTypeCode: String,
    @Json(name = "unitTypeName") val unitTypeName: String,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "nightlyRate") val nightlyRate: Double,
    @Json(name = "nightCount") val nightCount: Int,
    @Json(name = "lineTotal") val lineTotal: Double
)
