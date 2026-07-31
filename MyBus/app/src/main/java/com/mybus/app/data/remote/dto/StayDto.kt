package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StayCatalog(
    @Json(name = "checkInTime") val checkInTime: String,
    @Json(name = "checkOutTime") val checkOutTime: String,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "pricesIncludeTaxes") val pricesIncludeTaxes: Boolean,
    @Json(name = "refundCutoffHours") val refundCutoffHours: Int,
    @Json(name = "maxNights") val maxNights: Int,
    @Json(name = "unitTypes") val unitTypes: List<StayUnitType>
)

@JsonClass(generateAdapter = true)
data class StayUnitType(
    @Json(name = "id") val id: String,
    @Json(name = "code") val code: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "capacity") val capacity: Int? = null,
    @Json(name = "nightlyRate") val nightlyRate: Double,
    @Json(name = "totalUnits") val totalUnits: Int,
    @Json(name = "availableUnits") val availableUnits: Int? = null,
    @Json(name = "requestedQuantity") val requestedQuantity: Int? = null,
    @Json(name = "lineTotal") val lineTotal: Double? = null,
    @Json(name = "canFulfill") val canFulfill: Boolean? = null,
    @Json(name = "isActive") val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class StayBookingItemRequest(
    @Json(name = "unitTypeCode") val unitTypeCode: String,
    @Json(name = "quantity") val quantity: Int
)

@JsonClass(generateAdapter = true)
data class StayQuoteRequest(
    @Json(name = "checkInDate") val checkInDate: String,
    @Json(name = "checkOutDate") val checkOutDate: String,
    @Json(name = "items") val items: List<StayBookingItemRequest>,
    @Json(name = "couponCode") val couponCode: String? = null
)

@JsonClass(generateAdapter = true)
data class StayQuote(
    @Json(name = "checkInDate") val checkInDate: String,
    @Json(name = "checkOutDate") val checkOutDate: String,
    @Json(name = "nightCount") val nightCount: Int,
    @Json(name = "accommodationAmount") val accommodationAmount: Double,
    @Json(name = "subtotalAmount") val subtotalAmount: Double = accommodationAmount,
    @Json(name = "discountAmount") val discountAmount: Double = 0.0,
    @Json(name = "couponCode") val couponCode: String? = null,
    @Json(name = "totalAmount") val totalAmount: Double,
    @Json(name = "totalCapacity") val totalCapacity: Int,
    @Json(name = "currency") val currency: String,
    @Json(name = "pricesIncludeTaxes") val pricesIncludeTaxes: Boolean,
    @Json(name = "canFulfill") val canFulfill: Boolean,
    @Json(name = "availabilityIsInformational") val availabilityIsInformational: Boolean,
    @Json(name = "unitTypes") val unitTypes: List<StayUnitType>
)

@JsonClass(generateAdapter = true)
data class CreateStayBookingRequest(
    @Json(name = "checkInDate") val checkInDate: String,
    @Json(name = "checkOutDate") val checkOutDate: String,
    @Json(name = "items") val items: List<StayBookingItemRequest>,
    @Json(name = "guestCount") val guestCount: Int,
    @Json(name = "contactName") val contactName: String,
    @Json(name = "contactEmail") val contactEmail: String,
    @Json(name = "contactPhone") val contactPhone: String,
    @Json(name = "couponCode") val couponCode: String? = null,
    @Json(name = "cancellationPolicyAccepted") val cancellationPolicyAccepted: Boolean = true,
    @Json(name = "customerNote") val customerNote: String? = null
)

@JsonClass(generateAdapter = true)
data class StayPage<T>(
    @Json(name = "items") val items: List<T>,
    @Json(name = "page") val page: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "total") val total: Int
)

@JsonClass(generateAdapter = true)
data class StayBooking(
    @Json(name = "id") val id: String,
    @Json(name = "reference") val reference: String,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "checkInDate") val checkInDate: String,
    @Json(name = "checkOutDate") val checkOutDate: String,
    @Json(name = "nightCount") val nightCount: Int,
    @Json(name = "guestCount") val guestCount: Int,
    @Json(name = "contactName") val contactName: String,
    @Json(name = "contactEmail") val contactEmail: String,
    @Json(name = "contactPhone") val contactPhone: String,
    @Json(name = "subtotalAmount") val subtotalAmount: Double? = null,
    @Json(name = "discountAmount") val discountAmount: Double = 0.0,
    @Json(name = "couponCode") val couponCode: String? = null,
    @Json(name = "totalAmount") val totalAmount: Double,
    @Json(name = "customerNote") val customerNote: String? = null,
    @Json(name = "rejectionReason") val rejectionReason: String? = null,
    @Json(name = "confirmedAt") val confirmedAt: String? = null,
    @Json(name = "completedAt") val completedAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "items") val items: List<StayBookingItem> = emptyList(),
    @Json(name = "cancellation") val cancellation: StayCancellation? = null
)

@JsonClass(generateAdapter = true)
data class StayBookingItem(
    @Json(name = "id") val id: String,
    @Json(name = "unitTypeCode") val unitTypeCode: String,
    @Json(name = "unitTypeName") val unitTypeName: String,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "nightlyRate") val nightlyRate: Double,
    @Json(name = "nightCount") val nightCount: Int,
    @Json(name = "lineTotal") val lineTotal: Double
)

@JsonClass(generateAdapter = true)
data class StayCancellation(
    @Json(name = "id") val id: String,
    @Json(name = "bookingId") val bookingId: String,
    @Json(name = "reference") val reference: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "previousBookingStatus") val previousBookingStatus: String,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "requestedAt") val requestedAt: String,
    @Json(name = "standardFullRefundEligible") val standardFullRefundEligible: Boolean,
    @Json(name = "hoursBeforeCheckIn") val hoursBeforeCheckIn: Double,
    @Json(name = "refundDecision") val refundDecision: String? = null,
    @Json(name = "refundAmount") val refundAmount: Double? = null,
    @Json(name = "decisionReason") val decisionReason: String? = null,
    @Json(name = "contactName") val contactName: String? = null,
    @Json(name = "contactPhone") val contactPhone: String? = null,
    @Json(name = "contactEmail") val contactEmail: String? = null,
    @Json(name = "checkInDate") val checkInDate: String? = null,
    @Json(name = "checkOutDate") val checkOutDate: String? = null,
    @Json(name = "totalAmount") val totalAmount: Double? = null
)

@JsonClass(generateAdapter = true)
data class StayCancellationRequest(@Json(name = "reason") val reason: String? = null)

@JsonClass(generateAdapter = true)
data class StayRejectionRequest(@Json(name = "reason") val reason: String)

@JsonClass(generateAdapter = true)
data class StayCancellationDecisionRequest(
    @Json(name = "action") val action: String,
    @Json(name = "refundDecision") val refundDecision: String? = null,
    @Json(name = "refundAmount") val refundAmount: Double? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class StayCoupon(
    @Json(name = "id") val id: String,
    @Json(name = "code") val code: String,
    @Json(name = "discountAmount") val discountAmount: Double,
    @Json(name = "startDate") val startDate: String,
    @Json(name = "endDate") val endDate: String,
    @Json(name = "isActive") val isActive: Boolean,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateStayCouponRequest(
    @Json(name = "code") val code: String,
    @Json(name = "discountAmount") val discountAmount: Double,
    @Json(name = "startDate") val startDate: String,
    @Json(name = "endDate") val endDate: String
)
