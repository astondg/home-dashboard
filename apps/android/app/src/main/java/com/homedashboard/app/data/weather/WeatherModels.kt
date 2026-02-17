package com.homedashboard.app.data.weather

import java.time.LocalDate
import java.time.LocalTime

data class DailyWeather(
    val date: LocalDate,
    val maxTemp: Int,        // rounded to nearest degree
    val weatherCode: Int,    // WMO code
    val icon: WeatherIcon    // mapped from WMO code
)

enum class WeatherIcon {
    SUNNY, PARTLY_CLOUDY, CLOUDY, FOGGY,
    DRIZZLE, RAIN, SNOW, THUNDERSTORM;

    companion object {
        /**
         * Map WMO weather codes to icons.
         * See: https://open-meteo.com/en/docs
         */
        fun fromWmoCode(code: Int): WeatherIcon = when (code) {
            0 -> SUNNY                          // Clear sky
            1, 2 -> PARTLY_CLOUDY               // Mainly clear, partly cloudy
            3 -> CLOUDY                          // Overcast
            45, 48 -> FOGGY                      // Fog, depositing rime fog
            51, 53, 55 -> DRIZZLE                // Drizzle: light, moderate, dense
            56, 57 -> DRIZZLE                    // Freezing drizzle
            61, 63, 65 -> RAIN                   // Rain: slight, moderate, heavy
            66, 67 -> RAIN                       // Freezing rain
            71, 73, 75, 77 -> SNOW               // Snow: slight, moderate, heavy, snow grains
            80, 81, 82 -> RAIN                   // Rain showers
            85, 86 -> SNOW                       // Snow showers
            95, 96, 99 -> THUNDERSTORM           // Thunderstorm
            else -> CLOUDY
        }
    }
}

data class RainForecast(
    val nextRainTime: LocalTime?,
    val probability: Int
)

enum class TempUnit {
    AUTO, CELSIUS, FAHRENHEIT
}

enum class WeatherLocationMode {
    DEVICE, MANUAL
}
