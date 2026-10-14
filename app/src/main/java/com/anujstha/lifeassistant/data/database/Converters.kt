package com.anujstha.lifeassistant.data.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Storage formats (part of the schema contract; changing one requires a migration):
 * - [Instant]: epoch milliseconds (UTC).
 * - [LocalDate]: ISO "yyyy-MM-dd" text, so string comparison == chronological comparison.
 * - [LocalTime]: seconds of day (0–86399).
 * - [LocalDateTime]: ISO "yyyy-MM-ddTHH:mm[:ss]" text (wall-clock, zone stored separately).
 * - [YearMonth]: ISO "yyyy-MM" text.
 * Enums use Room's built-in name mapping.
 */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun localDateTimeToString(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun stringToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter fun yearMonthToString(value: YearMonth?): String? = value?.toString()
    @TypeConverter fun stringToYearMonth(value: String?): YearMonth? = value?.let(YearMonth::parse)
}
