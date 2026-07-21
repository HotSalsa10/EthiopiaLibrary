package com.ethiopialibrary.app.dates

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

/**
 * The ICU calendar keyword for the Hijri variant used app-wide. Kept as the
 * single swap point: change this one constant to move to e.g. "islamic-civil".
 */
const val HIJRI_ICU_CALENDAR = "islamic-umalqura"

/** A date in the Hijri (Umm al-Qura) calendar. month is 1-12. */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        fun fromEpochMillis(epochMillis: Long): HijriDate {
            val cal = Calendar.getInstance(
                TimeZone.getDefault(),
                ULocale.forLanguageTag("und-u-ca-$HIJRI_ICU_CALENDAR"),
            )
            cal.timeInMillis = epochMillis
            return HijriDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}

/** Which calendar(s) a date is shown in; chosen by staff in Settings. */
enum class CalendarMode { DUAL, HIJRI, GREGORIAN }

/**
 * Dates default to showing both calendars, Hijri primary in every locale,
 * Gregorian in parentheses. Digits are forced to Latin everywhere so dates
 * always match the printed B-/M- codes.
 */
object DualCalendarFormatter {
    fun format(epochMillis: Long, locale: Locale, mode: CalendarMode = CalendarMode.DUAL): String {
        val date = Date(epochMillis)
        val tag = locale.toLanguageTag()
        val hijri = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            ULocale.forLanguageTag("$tag-u-ca-$HIJRI_ICU_CALENDAR-nu-latn"),
        ).format(date)
        val gregorian = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            ULocale.forLanguageTag("$tag-u-nu-latn"),
        ).format(date)
        return when (mode) {
            CalendarMode.HIJRI -> hijri
            CalendarMode.GREGORIAN -> gregorian
            CalendarMode.DUAL -> "$hijri ($gregorian)"
        }
    }
}
