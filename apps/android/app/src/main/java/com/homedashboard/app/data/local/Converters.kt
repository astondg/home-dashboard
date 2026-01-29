package com.homedashboard.app.data.local

import androidx.room.TypeConverter
import com.homedashboard.app.data.model.CalendarProvider
import com.homedashboard.app.data.model.TaskPriority
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Room type converters for complex types
 */
class Converters {

    private val zonedFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
    private val localDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromZonedDateTime(value: ZonedDateTime?): String? {
        return value?.format(zonedFormatter)
    }

    @TypeConverter
    fun toZonedDateTime(value: String?): ZonedDateTime? {
        return value?.let { ZonedDateTime.parse(it, zonedFormatter) }
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.format(localDateFormatter)
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, localDateFormatter) }
    }

    @TypeConverter
    fun fromCalendarProvider(value: CalendarProvider): String {
        return value.name
    }

    @TypeConverter
    fun toCalendarProvider(value: String): CalendarProvider {
        return CalendarProvider.valueOf(value)
    }

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String {
        return value.name
    }

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority {
        return TaskPriority.valueOf(value)
    }
}
