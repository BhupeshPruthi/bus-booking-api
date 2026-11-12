package com.mybus.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RupeesTest {
    @Test
    fun `whole amounts use Indian grouping without decimals`() {
        assertEquals("0", 0.0.asRupees())
        assertEquals("500", 500.0.asRupees())
        assertEquals("1,000", 1000.0.asRupees())
        assertEquals("1,00,000", 100000.0.asRupees())
        assertEquals("12,34,56,789", 123456789.0.asRupees())
    }

    @Test
    fun `fractions keep up to two decimals like iOS`() {
        assertEquals("81,282.5", 81282.5.asRupees())
        assertEquals("1,499.99", 1499.99.asRupees())
        assertEquals("10", 9.999.asRupees())
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-1,00,000", (-100000.0).asRupees())
    }
}
