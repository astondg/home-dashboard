package com.homedashboard.app.data.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Instant

/**
 * Repository that manages weather data with in-memory caching.
 * Caches results for 1 hour to avoid unnecessary network calls.
 */
class WeatherRepository(
    private val weatherService: WeatherService = WeatherService()
) {
    private val _weatherByDate = MutableStateFlow<Map<LocalDate, DailyWeather>>(emptyMap())
    val weatherByDate: StateFlow<Map<LocalDate, DailyWeather>> = _weatherByDate.asStateFlow()

    private val _rainForecast = MutableStateFlow<RainForecast?>(null)
    val rainForecast: StateFlow<RainForecast?> = _rainForecast.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastFetchTime: Instant? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private val cacheValidityMs = 3600_000L // 1 hour

    /**
     * Fetch weather data, using cache if available and not expired.
     */
    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        useFahrenheit: Boolean = false
    ) {
        // Check cache validity
        val now = Instant.now()
        val cached = lastFetchTime
        if (cached != null &&
            lastLat == latitude &&
            lastLon == longitude &&
            now.toEpochMilli() - cached.toEpochMilli() < cacheValidityMs &&
            _weatherByDate.value.isNotEmpty()
        ) {
            return // Cache is still valid
        }

        _isLoading.value = true
        val result = weatherService.fetchForecast(latitude, longitude, useFahrenheit)
        result.onSuccess { forecasts ->
            _weatherByDate.value = forecasts.associateBy { it.date }
            lastFetchTime = now
            lastLat = latitude
            lastLon = longitude
        }
        // Fetch hourly precipitation alongside daily weather
        val rainResult = weatherService.fetchHourlyPrecipitation(latitude, longitude)
        rainResult.onSuccess { rain ->
            _rainForecast.value = rain
        }
        _isLoading.value = false
    }

    /**
     * Force refresh weather data (ignoring cache).
     */
    suspend fun refreshWeather(
        latitude: Double,
        longitude: Double,
        useFahrenheit: Boolean = false
    ) {
        lastFetchTime = null
        fetchWeather(latitude, longitude, useFahrenheit)
    }

    /**
     * Geocode a city name.
     */
    suspend fun geocodeCity(cityName: String): Result<List<GeocodingResult>> {
        return weatherService.geocodeCity(cityName)
    }
}
