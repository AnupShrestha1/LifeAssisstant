package com.anujstha.lifeassistant.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Locale-aware time formatter honoring the user's 12/24-hour preference. */
@Composable
fun rememberTimeFormatter(timeFormat: TimeFormat): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val use24h = when (timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
    return remember(use24h, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (use24h) "Hm" else "hma"), locale)
    }
}

@Composable
fun rememberDateFormatter(style: FormatStyle = FormatStyle.MEDIUM): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(style, locale) { DateTimeFormatter.ofLocalizedDate(style).withLocale(locale) }
}

/** "Today", "Tomorrow", "Yesterday", or a localized date. */
@Composable
fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> rememberDateFormatter().format(date)
}

/** Relative date plus optional time, e.g. "Tomorrow, 5:00 PM". */
@Composable
fun dueLabel(date: LocalDate, time: LocalTime?, today: LocalDate, timeFormat: TimeFormat): String {
    val day = relativeDate(date, today)
    return if (time == null) day else "$day, ${rememberTimeFormatter(timeFormat).format(time)}"
}
