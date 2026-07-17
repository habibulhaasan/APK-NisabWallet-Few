package com.hasan.nisabwallet.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor

// Converted from: src/utils/zakatUtils.js
// (gregorianToHijri / hijriToGregorian / addOneHijriYear / daysUntilHijriAnniversary /
//  hasOneHijriYearPassed / formatHijriDate helpers)
//
// Uses the tabular Islamic calendar algorithm (civil / arithmetic calendar).
// Good enough for zakat-year tracking; not tied to local moon-sighting.

data class HijriDate(
    val year: Int,
    val month: Int,   // 1-12
    val day: Int,
)

object HijriConverter {

    private val HIJRI_MONTH_NAMES = listOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaban",
        "Ramadan", "Shawwal", "Dhul Qadah", "Dhul Hijjah",
    )

    fun getHijriMonthName(month: Int): String =
        HIJRI_MONTH_NAMES.getOrElse(month - 1) { "—" }

    // ── Gregorian -> Hijri (tabular / civil algorithm) ─────────────────────
    fun gregorianToHijri(date: Date): HijriDate {
        val cal = Calendar.getInstance().apply { time = date }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)

        val jd = gregorianToJulianDay(y, m, d)
        return julianDayToHijri(jd)
    }

    // ── Hijri -> Gregorian ──────────────────────────────────────────────────
    fun hijriToGregorian(hYear: Int, hMonth: Int, hDay: Int): Date {
        val jd = hijriToJulianDay(hYear, hMonth, hDay)
        return julianDayToGregorian(jd)
    }

    // ── addOneHijriYear — mirrors addOneHijriYear() in zakatUtils.js ───────
    // Takes a "yyyy-MM-dd" Gregorian date string, returns the Gregorian date
    // exactly one Hijri year later.
    fun addOneHijriYear(gregorianDateStr: String): Date {
        val date = parseIsoDate(gregorianDateStr) ?: return Date()
        val hijri = gregorianToHijri(date)
        return hijriToGregorian(hijri.year + 1, hijri.month, hijri.day)
    }

    // ── hasOneHijriYearPassed — mirrors hasOneHijriYearPassed() in zakatUtils.js ──
    fun hasOneHijriYearPassed(startDateStr: String): Boolean {
        val anniversary = addOneHijriYear(startDateStr)
        return Date().after(anniversary) || Date() == anniversary
    }

    // ── daysUntilHijriAnniversary — mirrors daysUntilHijriAnniversary() ────
    fun daysUntilHijriAnniversary(startDateStr: String): Int {
        val anniversary = addOneHijriYear(startDateStr)
        val diffMillis = anniversary.time - Date().time
        return floor(diffMillis / (1000.0 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    fun formatHijriDate(date: Date): String {
        val h = gregorianToHijri(date)
        return "${h.day} ${getHijriMonthName(h.month)} ${h.year} AH"
    }

    private fun parseIsoDate(s: String): Date? =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) }.getOrNull()

    // ── Julian Day <-> Gregorian (standard proleptic Gregorian algorithm) ──
    private fun gregorianToJulianDay(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + ((153 * m + 2) / 5) + 365L * y + (y / 4) - (y / 100) + (y / 400) - 32045
    }

    private fun julianDayToGregorian(jd: Long): Date {
        val a = jd + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = (e - (153 * m + 2) / 5 + 1).toInt()
        val month = (m + 3 - 12 * (m / 10)).toInt()
        val year = (100 * b + d - 4800 + m / 10).toInt()
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day)
        return cal.time
    }

    // ── Julian Day <-> Hijri (tabular Islamic calendar, epoch 1948440) ─────
    private const val ISLAMIC_EPOCH = 1948440L

    private fun julianDayToHijri(jd: Long): HijriDate {
        val year = floor((30.0 * (jd - ISLAMIC_EPOCH) + 10646) / 10631.0).toInt()
        var month = kotlin.math.min(
            12,
            kotlin.math.ceil((jd - (29 + hijriToJulianDay(year, 1, 1))) / 29.5).toInt() + 1
        )
        if (month < 1) month = 1
        val day = (jd - hijriToJulianDay(year, month, 1) + 1).toInt()
        return HijriDate(year, month, day)
    }

    private fun hijriToJulianDay(year: Int, month: Int, day: Int): Long {
        return (day
            + kotlin.math.ceil(29.5 * (month - 1)).toLong()
            + (year - 1) * 354L
            + floor((3 + 11 * year.toLong()) / 30.0).toLong()
            + ISLAMIC_EPOCH - 1)
    }
}
