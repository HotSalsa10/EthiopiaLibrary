package com.ethiopialibrary.app.dates

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.EthiopicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

/** A date in the Ethiopian calendar (Amete Mihret era). month is 1-13. */
data class EthiopianDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        fun fromEpochMillis(epochMillis: Long): EthiopianDate {
            val cal = EthiopicCalendar(TimeZone.getDefault(), ULocale("am_ET"))
            cal.timeInMillis = epochMillis
            return EthiopianDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}

/** Which calendar(s) a date is shown in; chosen by staff in Settings. */
enum class CalendarMode { DUAL, ETHIOPIAN, GREGORIAN }

/**
 * Ethiopia uses the Ethiopian calendar in daily life. By default every date in
 * the UI shows both calendars (Ethiopian primary when the UI is Amharic,
 * Gregorian primary in English/Arabic), but staff can pick a single calendar in
 * Settings. Digits are forced to Latin everywhere so dates always match the
 * printed B-/M- codes.
 */
object DualCalendarFormatter {

    fun format(
        epochMillis: Long,
        locale: Locale,
        mode: CalendarMode = CalendarMode.DUAL,
    ): String {
        val date = Date(epochMillis)
        val tag = locale.toLanguageTag()
        val ethiopic = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            ULocale.forLanguageTag("$tag-u-ca-ethiopic-nu-latn"),
        ).format(date)
        val gregorian = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            ULocale.forLanguageTag("$tag-u-nu-latn"),
        ).format(date)
        return when (mode) {
            CalendarMode.ETHIOPIAN -> ethiopic
            CalendarMode.GREGORIAN -> gregorian
            CalendarMode.DUAL -> if (locale.language == "am") {
                "$ethiopic ($gregorian)"
            } else {
                "$gregorian ($ethiopic)"
            }
        }
    }
}
