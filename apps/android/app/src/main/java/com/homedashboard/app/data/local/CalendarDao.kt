package com.homedashboard.app.data.local

import androidx.room.*
import com.homedashboard.app.data.model.Calendar
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime

@Dao
interface CalendarDao {

    // ==================== Events ====================

    @Query("SELECT * FROM calendar_events WHERE isDeleted = 0 ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>

    @Query("""
        SELECT * FROM calendar_events
        WHERE isDeleted = 0
        AND startTime >= :startTime
        AND startTime < :endTime
        ORDER BY startTime ASC
    """)
    fun getEventsInRange(startTime: ZonedDateTime, endTime: ZonedDateTime): Flow<List<CalendarEvent>>

    @Query("""
        SELECT * FROM calendar_events
        WHERE isDeleted = 0
        AND calendarId = :calendarId
        ORDER BY startTime ASC
    """)
    fun getEventsByCalendarFlow(calendarId: String): Flow<List<CalendarEvent>>

    @Query("""
        SELECT * FROM calendar_events
        WHERE calendarId = :calendarId
        ORDER BY startTime ASC
    """)
    suspend fun getEventsByCalendar(calendarId: String): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE remoteId = :remoteId")
    suspend fun getEventByRemoteId(remoteId: String): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getEventById(id: String): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE needsSync = 1 AND isDeleted = 0")
    suspend fun getEventsNeedingSync(): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE needsSync = 1 AND isDeleted = 1")
    suspend fun getDeletedEventsNeedingSync(): List<CalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEvent>)

    @Update
    suspend fun updateEvent(event: CalendarEvent)

    @Query("UPDATE calendar_events SET isDeleted = 1, needsSync = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteEvent(id: String, now: ZonedDateTime = ZonedDateTime.now())

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun hardDeleteEvent(id: String)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteEventsByCalendar(calendarId: String)

    // ==================== Calendars ====================

    @Query("SELECT * FROM calendars ORDER BY name ASC")
    fun getAllCalendars(): Flow<List<Calendar>>

    @Query("SELECT * FROM calendars WHERE isVisible = 1 ORDER BY name ASC")
    fun getVisibleCalendars(): Flow<List<Calendar>>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getCalendarById(id: String): Calendar?

    @Query("SELECT id FROM calendars WHERE isVisible = 1")
    suspend fun getVisibleCalendarIds(): List<String>

    @Query("SELECT * FROM calendars WHERE providerType = :providerType ORDER BY name ASC")
    suspend fun getCalendarsByProvider(providerType: CalendarProvider): List<Calendar>

    @Query("SELECT * FROM calendars WHERE isVisible = 1 AND isReadOnly = 0 ORDER BY name ASC")
    suspend fun getWritableVisibleCalendars(): List<Calendar>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendar(calendar: Calendar)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<Calendar>)

    @Update
    suspend fun updateCalendar(calendar: Calendar)

    @Delete
    suspend fun deleteCalendar(calendar: Calendar)

    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun deleteCalendarById(id: String)

    @Query("DELETE FROM calendar_events")
    suspend fun clearAllEvents()

    @Query("DELETE FROM calendar_events WHERE providerType = 'LOCAL'")
    suspend fun clearLocalEvents()

    @Query("DELETE FROM calendars")
    suspend fun clearAllCalendars()
}
