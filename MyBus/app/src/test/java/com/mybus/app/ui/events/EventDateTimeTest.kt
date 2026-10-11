package com.mybus.app.ui.events

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventDateTimeTest {
    @Test
    fun `event end instant keeps the selected India calendar date`() {
        val selectedDate = LocalDate.of(2026, 9, 1)

        val instant = selectedDate.eventEndInstant()

        assertEquals("2026-09-01T18:29:00Z", instant.toString())
        assertEquals(selectedDate, instant.eventBusinessDate())
    }
}
