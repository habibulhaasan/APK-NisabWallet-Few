package com.hasan.nisabwallet.core.util

// Converted from: src/utils/zakatUtils.js (calculateZakat)

object ZakatCalculator {
    private const val ZAKAT_RATE = 0.025 // 2.5%

    /** Returns zakat due (2.5%) on the given zakatable wealth. Never negative. */
    fun calculateZakat(netZakatableWealth: Double): Double =
        if (netZakatableWealth > 0) netZakatableWealth * ZAKAT_RATE else 0.0
}
