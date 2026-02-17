package com.homedashboard.app.data.weather

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalTime

/**
 * Service for fetching weather data from Open-Meteo API (free, no API key).
 */
class WeatherService(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    /**
     * Fetch 7-day weather forecast for given coordinates.
     */
    suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
        useFahrenheit: Boolean = false
    ): Result<List<DailyWeather>> = withContext(Dispatchers.IO) {
        try {
            val tempUnit = if (useFahrenheit) "fahrenheit" else "celsius"
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&daily=temperature_2m_max,weather_code" +
                "&temperature_unit=$tempUnit" +
                "&timezone=auto" +
                "&forecast_days=7"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Weather API error: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val apiResponse = gson.fromJson(body, OpenMeteoResponse::class.java)
            val daily = apiResponse.daily
                ?: return@withContext Result.failure(Exception("No daily data"))

            val forecasts = daily.time.mapIndexed { index, dateStr ->
                DailyWeather(
                    date = LocalDate.parse(dateStr),
                    maxTemp = daily.temperatureMax.getOrNull(index)?.toInt() ?: 0,
                    weatherCode = daily.weatherCode.getOrNull(index) ?: 0,
                    icon = WeatherIcon.fromWmoCode(daily.weatherCode.getOrNull(index) ?: 0)
                )
            }

            Result.success(forecasts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch hourly precipitation probability for today.
     * Returns a RainForecast with the next hour where probability >= 30%.
     */
    suspend fun fetchHourlyPrecipitation(
        latitude: Double,
        longitude: Double
    ): Result<RainForecast> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&hourly=precipitation_probability" +
                "&timezone=auto" +
                "&forecast_days=1"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Weather API error: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val apiResponse = gson.fromJson(body, HourlyPrecipResponse::class.java)
            val hourly = apiResponse.hourly
                ?: return@withContext Result.success(RainForecast(null, 0))

            val now = LocalTime.now()

            // Find first hour from now with probability >= 30%
            for (i in hourly.time.indices) {
                val hourStr = hourly.time[i]
                val prob = hourly.precipitationProbability.getOrNull(i) ?: 0
                // Parse hour from ISO datetime string (e.g. "2026-02-17T14:00")
                val hourTime = try {
                    LocalTime.parse(hourStr.substringAfter("T"))
                } catch (_: Exception) {
                    continue
                }
                if (hourTime.isAfter(now) && prob >= 30) {
                    return@withContext Result.success(RainForecast(hourTime, prob))
                }
            }

            Result.success(RainForecast(null, 0))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Geocode a city name to coordinates using Open-Meteo geocoding API.
     */
    suspend fun geocodeCity(cityName: String): Result<List<GeocodingResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${cityName}&count=5"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Geocoding API error: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val apiResponse = gson.fromJson(body, GeocodingResponse::class.java)
            val results = apiResponse.results?.map { result ->
                GeocodingResult(
                    name = result.name,
                    country = result.country ?: "",
                    admin1 = result.admin1 ?: "",
                    latitude = result.latitude,
                    longitude = result.longitude
                )
            } ?: emptyList()

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class GeocodingResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (admin1.isNotEmpty()) append(", $admin1")
            if (country.isNotEmpty()) append(", $country")
        }
}

// Open-Meteo API response models
private data class OpenMeteoResponse(
    val daily: DailyData?
)

private data class DailyData(
    val time: List<String>,
    @SerializedName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerializedName("weather_code") val weatherCode: List<Int>
)

private data class HourlyPrecipResponse(
    val hourly: HourlyPrecipData?
)

private data class HourlyPrecipData(
    val time: List<String>,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>
)

private data class GeocodingResponse(
    val results: List<GeocodingItem>?
)

private data class GeocodingItem(
    val name: String,
    val country: String?,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double
)
