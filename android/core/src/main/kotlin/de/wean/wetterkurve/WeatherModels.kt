package de.wean.wetterkurve

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

data class WeatherLocation(
    val id: String,
    val name: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
)

data class HourlyPoint(
    val time: LocalDateTime,
    val temperature: Double,
    val apparent: Double,
    val precipitationProbability: Double,
    val precipitation: Double,
    val weatherCode: Int,
    val wind: Double,
    val cloudCover: Double = 0.0,
)

data class DaySegment(
    val key: String,
    val label: String,
    val start: Int,
    val end: Int,
)

data class WeatherCondition(
    val symbol: String,
    val description: String,
    val icon: String,
)

data class AppState(
    val locations: List<WeatherLocation>,
    val activeLocation: Int,
    val showClouds: Boolean = true,
    val showWind: Boolean = true,
)

@Serializable
data class ForecastPayload(
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null,
)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Double,
)

@Serializable
data class HourlyWeather(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Double> = emptyList(),
    @SerialName("precipitation") val precipitation: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double> = emptyList(),
)

@Serializable
data class GeocodingHit(
    val name: String? = null,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val admin1: String? = null,
    val country: String? = null,
    val timezone: String? = null,
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingHit>? = null,
)

@Serializable
data class WeatherLocationJson(
    val id: String? = null,
    val name: String? = null,
    val label: String? = null,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val timezone: String? = null,
)

@Serializable
data class AppStateJson(
    val locations: List<WeatherLocationJson> = emptyList(),
    val activeLocation: Int = 0,
    val showClouds: Boolean = true,
    val showWind: Boolean = true,
)
