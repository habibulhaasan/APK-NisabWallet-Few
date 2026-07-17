package com.hasan.nisabwallet.core.util

import java.text.DecimalFormat
import java.util.Locale

// Converted from: src/utils/formatCurrency.js (or equivalent Intl.NumberFormat('en-BD', ...) usage)
// Mirrors the "৳12,34,567.89" (South Asian/lakh grouping) style used in the web app for BDT.

object CurrencyFormatter {

    /**
     * Formats a value as Bangladeshi Taka using South Asian digit grouping
     * (e.g. 1234567.5 -> "৳12,34,567.50"), matching the web app's display format.
     */
    fun formatBDT(amount: Double): String {
        val negative = amount < 0
        val value = kotlin.math.abs(amount)

        val wholePart = value.toLong()
        val fraction = Math.round((value - wholePart) * 100)

        val whole = wholePart.toString()
        val grouped = groupSouthAsian(whole)

        val sign = if (negative) "-" else ""
        return "$sign৳$grouped.${fraction.toString().padStart(2, '0')}"
    }

    // South Asian grouping: last 3 digits together, then groups of 2.
    // e.g. 1234567 -> "12,34,567"
    private fun groupSouthAsian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) {
            groups.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.add(0, rest)
        return groups.joinToString(",") + ",$last3"
    }

    /** Plain number formatting without currency symbol, e.g. for input fields. */
    fun formatPlain(amount: Double): String {
        val df = DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.US))
        return df.format(amount)
    }
}
