package com.homedashboard.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.homedashboard.app.data.weather.TempUnit
import com.homedashboard.app.data.weather.WeatherLocationMode
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
    val layoutType: CalendarLayoutType = CalendarLayoutType.GRID_3X3,
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val showTasks: Boolean = true,
    val showQuickAdd: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val use24HourFormat: Boolean = false,
    val wallCalendarMode: Boolean = true, // Large text and touch targets for wall-mounted use
    val eInkRefreshMode: Boolean = false, // High contrast monochrome for e-ink screens
    // Always-on display
    val alwaysOnDisplay: Boolean = false,
    // Night dimming (reduces brightness on a schedule)
    val nightDimEnabled: Boolean = false,
    val nightDimStartHour: Int = 22, // 10 PM
    val nightDimEndHour: Int = 7,    // 7 AM
    val nightDimBrightness: Float = 0.05f, // 5% brightness during night mode
    // Default calendar for new events (null = auto/first available)
    val defaultCalendarId: String? = null,
    // Weather
    val showWeather: Boolean = false,
    val weatherLocationMode: WeatherLocationMode = WeatherLocationMode.DEVICE,
    val weatherLocationLat: Double? = null,
    val weatherLocationLon: Double? = null,
    val weatherLocationName: String? = null,
    val weatherTempUnit: TempUnit = TempUnit.AUTO,
    // Handwriting onboarding — hide write hints after first use
    val hasUsedHandwriting: Boolean = false
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
        val WALL_CALENDAR_MODE = booleanPreferencesKey("wall_calendar_mode")
        val EINK_REFRESH_MODE = booleanPreferencesKey("eink_refresh_mode")
        val ALWAYS_ON_DISPLAY = booleanPreferencesKey("always_on_display")
        val NIGHT_DIM_ENABLED = booleanPreferencesKey("night_dim_enabled")
        val NIGHT_DIM_START_HOUR = intPreferencesKey("night_dim_start_hour")
        val NIGHT_DIM_END_HOUR = intPreferencesKey("night_dim_end_hour")
        val NIGHT_DIM_BRIGHTNESS = floatPreferencesKey("night_dim_brightness")
        val DEFAULT_CALENDAR_ID = stringPreferencesKey("default_calendar_id")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
        val WEATHER_LOCATION_LAT = doublePreferencesKey("weather_location_lat")
        val WEATHER_LOCATION_LON = doublePreferencesKey("weather_location_lon")
        val WEATHER_LOCATION_NAME = stringPreferencesKey("weather_location_name")
        val WEATHER_TEMP_UNIT = stringPreferencesKey("weather_temp_unit")
        val HAS_USED_HANDWRITING = booleanPreferencesKey("has_used_handwriting")
    }

    val settings: Flow<CalendarSettings> = context.dataStore.data.map { preferences ->
        CalendarSettings(
            layoutType = preferences[PreferencesKeys.LAYOUT_TYPE]?.let {
                CalendarLayoutType.valueOf(it)
            } ?: CalendarLayoutType.GRID_3X3,
            displayMode = preferences[PreferencesKeys.DISPLAY_MODE]?.let {
                DisplayMode.valueOf(it)
            } ?: DisplayMode.AUTO,
            showTasks = preferences[PreferencesKeys.SHOW_TASKS] ?: true,
            showQuickAdd = preferences[PreferencesKeys.SHOW_QUICK_ADD] ?: true,
            weekStartsOnMonday = preferences[PreferencesKeys.WEEK_STARTS_MONDAY] ?: true,
            use24HourFormat = preferences[PreferencesKeys.USE_24_HOUR] ?: false,
            wallCalendarMode = preferences[PreferencesKeys.WALL_CALENDAR_MODE] ?: true,
            eInkRefreshMode = preferences[PreferencesKeys.EINK_REFRESH_MODE] ?: false,
            alwaysOnDisplay = preferences[PreferencesKeys.ALWAYS_ON_DISPLAY] ?: false,
            nightDimEnabled = preferences[PreferencesKeys.NIGHT_DIM_ENABLED] ?: false,
            nightDimStartHour = preferences[PreferencesKeys.NIGHT_DIM_START_HOUR] ?: 22,
            nightDimEndHour = preferences[PreferencesKeys.NIGHT_DIM_END_HOUR] ?: 7,
            nightDimBrightness = preferences[PreferencesKeys.NIGHT_DIM_BRIGHTNESS] ?: 0.05f,
            defaultCalendarId = preferences[PreferencesKeys.DEFAULT_CALENDAR_ID],
            showWeather = preferences[PreferencesKeys.SHOW_WEATHER] ?: false,
            weatherLocationMode = preferences[PreferencesKeys.WEATHER_LOCATION_MODE]?.let {
                WeatherLocationMode.valueOf(it)
            } ?: WeatherLocationMode.DEVICE,
            weatherLocationLat = preferences[PreferencesKeys.WEATHER_LOCATION_LAT],
            weatherLocationLon = preferences[PreferencesKeys.WEATHER_LOCATION_LON],
            weatherLocationName = preferences[PreferencesKeys.WEATHER_LOCATION_NAME],
            weatherTempUnit = preferences[PreferencesKeys.WEATHER_TEMP_UNIT]?.let {
                TempUnit.valueOf(it)
            } ?: TempUnit.AUTO,
            hasUsedHandwriting = preferences[PreferencesKeys.HAS_USED_HANDWRITING] ?: false
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

    suspend fun updateWallCalendarMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WALL_CALENDAR_MODE] = enabled
        }
    }

    suspend fun updateEInkRefreshMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EINK_REFRESH_MODE] = enabled
        }
    }

    suspend fun updateAlwaysOnDisplay(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALWAYS_ON_DISPLAY] = enabled
        }
    }

    suspend fun updateNightDimEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NIGHT_DIM_ENABLED] = enabled
        }
    }

    suspend fun updateNightDimStartHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NIGHT_DIM_START_HOUR] = hour.coerceIn(0, 23)
        }
    }

    suspend fun updateNightDimEndHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NIGHT_DIM_END_HOUR] = hour.coerceIn(0, 23)
        }
    }

    suspend fun updateNightDimBrightness(brightness: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NIGHT_DIM_BRIGHTNESS] = brightness.coerceIn(0.01f, 0.5f)
        }
    }

    suspend fun updateDefaultCalendarId(calendarId: String?) {
        context.dataStore.edit { preferences ->
            if (calendarId != null) {
                preferences[PreferencesKeys.DEFAULT_CALENDAR_ID] = calendarId
            } else {
                preferences.remove(PreferencesKeys.DEFAULT_CALENDAR_ID)
            }
        }
    }

    suspend fun updateShowWeather(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_WEATHER] = show
        }
    }

    suspend fun updateWeatherTempUnit(unit: TempUnit) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEATHER_TEMP_UNIT] = unit.name
        }
    }

    suspend fun markHandwritingUsed() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_USED_HANDWRITING] = true
        }
    }

    suspend fun updateWeatherLocation(
        mode: WeatherLocationMode,
        lat: Double? = null,
        lon: Double? = null,
        name: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEATHER_LOCATION_MODE] = mode.name
            if (lat != null) {
                preferences[PreferencesKeys.WEATHER_LOCATION_LAT] = lat
            } else {
                preferences.remove(PreferencesKeys.WEATHER_LOCATION_LAT)
            }
            if (lon != null) {
                preferences[PreferencesKeys.WEATHER_LOCATION_LON] = lon
            } else {
                preferences.remove(PreferencesKeys.WEATHER_LOCATION_LON)
            }
            if (name != null) {
                preferences[PreferencesKeys.WEATHER_LOCATION_NAME] = name
            } else {
                preferences.remove(PreferencesKeys.WEATHER_LOCATION_NAME)
            }
        }
    }
}
