package com.mybus.app.ui.pooja

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoojaActiveWindowTest {
    private val scheduledAt = "2026-07-04T10:00:00Z"
    private val scheduledInstant = Instant.parse(scheduledAt)

    @Test
    fun `booking is not started before scheduled time`() {
        val status = poojaBookingStatus(
            scheduledAt = scheduledAt,
            availableTokens = 5,
            now = scheduledInstant.minusSeconds(1)
        )

        assertEquals(POOJA_BOOKING_NOT_STARTED, status)
        assertFalse(
            canBookPooja(
                scheduledAt = scheduledAt,
                availableTokens = 5,
                now = scheduledInstant.minusSeconds(1)
            )
        )
    }

    @Test
    fun `booking opens exactly at scheduled time`() {
        val status = poojaBookingStatus(
            scheduledAt = scheduledAt,
            availableTokens = 5,
            now = scheduledInstant
        )

        assertEquals(POOJA_BOOKING_OPEN, status)
        assertTrue(
            canBookPooja(
                scheduledAt = scheduledAt,
                availableTokens = 5,
                now = scheduledInstant
            )
        )
    }

    @Test
    fun `booking remains open until eight hours after scheduled time`() {
        val status = poojaBookingStatus(
            scheduledAt = scheduledAt,
            availableTokens = 5,
            now = scheduledInstant.plusSeconds((8 * 60 * 60) - 1)
        )

        assertEquals(POOJA_BOOKING_OPEN, status)
    }

    @Test
    fun `booking expires at eight hour boundary`() {
        val status = poojaBookingStatus(
            scheduledAt = scheduledAt,
            availableTokens = 5,
            now = scheduledInstant.plusSeconds(8 * 60 * 60)
        )

        assertEquals(POOJA_BOOKING_EXPIRED, status)
        assertFalse(
            canBookPooja(
                scheduledAt = scheduledAt,
                availableTokens = 5,
                now = scheduledInstant.plusSeconds(8 * 60 * 60)
            )
        )
    }

    @Test
    fun `booking is full when there are no available tokens inside active window`() {
        val status = poojaBookingStatus(
            scheduledAt = scheduledAt,
            availableTokens = 0,
            now = scheduledInstant.plusSeconds(60)
        )

        assertEquals(POOJA_BOOKING_FULL, status)
        assertFalse(
            canBookPooja(
                scheduledAt = scheduledAt,
                availableTokens = 0,
                now = scheduledInstant.plusSeconds(60)
            )
        )
    }

    @Test
    fun `server booking fields override local fallback calculation`() {
        assertEquals(
            POOJA_BOOKING_FULL,
            poojaBookingStatus(
                scheduledAt = scheduledAt,
                availableTokens = 5,
                serverStatus = POOJA_BOOKING_FULL,
                now = scheduledInstant
            )
        )
        assertFalse(
            canBookPooja(
                scheduledAt = scheduledAt,
                availableTokens = 5,
                serverCanBook = false,
                serverStatus = POOJA_BOOKING_OPEN,
                now = scheduledInstant
            )
        )
    }
}
