package com.mybus.app.ui.stay

import org.junit.Assert.assertEquals
import org.junit.Test

class StayClockTimeTest {
    @Test
    fun `catalog times read like iOS`() {
        assertEquals("12:00 noon", stayClockTime("12:00", fallback = "x"))
        assertEquals("11:00 AM", stayClockTime("11:00", fallback = "x"))
        assertEquals("2:30 PM", stayClockTime("14:30", fallback = "x"))
        assertEquals("12:05 AM", stayClockTime("00:05:00", fallback = "x"))
    }

    @Test
    fun `missing or odd values fall back`() {
        assertEquals("12:00 noon", stayClockTime(null, fallback = "12:00 noon"))
        assertEquals("11:00 AM", stayClockTime("", fallback = "11:00 AM"))
        assertEquals("after lunch", stayClockTime("after lunch", fallback = "x"))
    }
}
