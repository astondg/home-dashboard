package com.homedashboard.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Available calendar layout options
 */
enum class CalendarLayoutType {
    /** 3x3 grid with 7 days + notes area + tasks corner */
    GRID_3X3,

    /** Horizontal timeline with all 7 days in a row, tasks below */
    TIMELINE_HORIZONTAL,

    /** Today-focused dashboard with week overview and tasks */
    DASHBOARD_TODAY_FOCUS
}

/**
 * Display mode for e-ink optimization
 */
enum class DisplayMode {
    /** Auto-detect based on device capabilities */
    AUTO,

    /** Force color mode (for testing or color e-ink displays) */
    COLOR,

    /** Force monochrome mode (optimized for standard e-ink) */
    MONOCHROME
}

/**
 * Calendar settings data class
 */
data class CalendarSettings(
    val layoutType: CalendarLayoutType = CalendarLayoutType.TIMELINE_HORIZONTAL,
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val showTasks: Boolean = true,
    val showQuickAdd: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val use24HourFormat: Boolean = false,
    val eInkRefreshMode: Boolean = true // Optimizations for e-ink displays
)

// Extension to get DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_settings")

/**
 * Repository for persisting calendar settings
 */
class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val LAYOUT_TYPE = stringPreferencesKey("layout_type")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val SHOW_TASKS = booleanPreferencesKey("show_tasks")
        val SHOW_QUICK_ADD = booleanPreferencesKey("show_quick_add")
        val WEEK_STARTS_MONDAY = booleanPreferencesKey("week_starts_monday")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val EINK_REFRESH_MODE = booleanPreferencesKey("eink_refresh_mode")
    }

    val settings: Flow<CalendarSettings> = context.dataStore.data.map { preferences ->
        CalendarSettings(
            layoutType = preferences[PreferencesKeys.LAYOUT_TYPE]?.let {
                CalendarLayoutType.valueOf(it)
            } ?: CalendarLayoutType.TIMELINE_HORIZONTAL,
            displayMode = preferences[PreferencesKeys.DISPLAY_MODE]?.let {
                DisplayMode.valueOf(it)
            } ?: DisplayMode.AUTO,
            showTasks = preferences[PreferencesKeys.SHOW_TASKS] ?: true,
            showQuickAdd = preferences[PreferencesKeys.SHOW_QUICK_ADD] ?: true,
            weekStartsOnMonday = preferences[PreferencesKeys.WEEK_STARTS_MONDAY] ?: true,
            use24HourFormat = preferences[PreferencesKeys.USE_24_HOUR] ?: false,
            eInkRefreshMode = preferences[PreferencesKeys.EINK_REFRESH_MODE] ?: true
        )
    }

    suspend fun updateLayoutType(layoutType: CalendarLayoutType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAYOUT_TYPE] = layoutType.name
        }
    }

    suspend fun updateDisplayMode(displayMode: DisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_MODE] = displayMode.name
        }
    }

    suspend fun updateShowTasks(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_TASKS] = show
        }
    }

    suspend fun updateShowQuickAdd(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_QUICK_ADD] = show
        }
    }

    suspend fun updateWeekStartsMonday(monday: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEEK_STARTS_MONDAY] = monday
        }
    }

    suspend fun updateUse24HourFormat(use24Hour: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_24_HOUR] = use24Hour
        }
    }

    suspend fun updateEInkRefreshMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EINK_REFRESH_MODE] = enabled
        }
    }
}
