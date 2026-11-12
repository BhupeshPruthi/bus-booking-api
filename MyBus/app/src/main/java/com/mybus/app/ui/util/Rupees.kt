package com.mybus.app.ui.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rupee amount without the "₹" sign, in Indian digit grouping with up to two decimals, as iOS
 * shows it: 100000.0 → "1,00,000", 81282.5 → "81,282.5". Grouped by hand so it reads the same
 * on every device and in JVM tests (java.text only groups in threes off-device).
 */
fun Double.asRupees(): String {
    if (!isFinite()) return toString()
    val plain = BigDecimal.valueOf(this)
        .setScale(2, RoundingMode.HALF_EVEN)
        .stripTrailingZeros()
        .toPlainString()
    val sign = if (plain.startsWith("-")) "-" else ""
    val digits = plain.removePrefix("-")
    val whole = digits.substringBefore('.')
    val fraction = digits.substringAfter('.', "")
    val grouped = if (whole.length <= 3) {
        whole
    } else {
        whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed() +
            "," + whole.takeLast(3)
    }
    return sign + grouped + if (fraction.isEmpty()) "" else ".$fraction"
}
