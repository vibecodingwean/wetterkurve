package de.wean.wetterkurve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor

object WeatherService {
    const val MAX_LOCATIONS = 3
    const val CHART_HOURS = 3 * 24
    const val UPDATE_SECONDS = 20 * 60
    const val STALE_SECONDS = 10 * 60
    const val SEARCH_DELAY_MS = 250L

    val defaultLocation = WeatherLocation(
        id = "48.1374,11.5755",
        name = "Munich",
        label = "Munich, Bavaria, Germany",
        latitude = 48.1374,
        longitude = 11.5755,
        timezone = "Europe/Berlin",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val wmo = mapOf(
        0 to Wmo("☀", "Klar", "Clear", "clear"),
        1 to Wmo("🌤", "Überwiegend klar", "Mainly clear", "partly-cloudy"),
        2 to Wmo("⛅", "Teilweise bewölkt", "Partly cloudy", "partly-cloudy"),
        3 to Wmo("☁", "Bedeckt", "Overcast", "cloudy"),
        45 to Wmo("≋", "Nebel", "Fog", "fog"),
        48 to Wmo("≋", "Reifnebel", "Rime fog", "fog"),
        51 to Wmo("☂", "Leichter Nieselregen", "Light drizzle", "drizzle"),
        53 to Wmo("☂", "Nieselregen", "Drizzle", "drizzle"),
        55 to Wmo("☂", "Starker Nieselregen", "Dense drizzle", "rain"),
        56 to Wmo("☂", "Gefrierender Nieselregen", "Freezing drizzle", "drizzle"),
        57 to Wmo("☂", "Starker gefrierender Nieselregen", "Dense freezing drizzle", "rain"),
        61 to Wmo("☂", "Leichter Regen", "Slight rain", "rain"),
        63 to Wmo("☂", "Regen", "Rain", "rain"),
        65 to Wmo("☂", "Starker Regen", "Heavy rain", "rain"),
        66 to Wmo("☂", "Gefrierender Regen", "Freezing rain", "rain"),
        67 to Wmo("☂", "Starker gefrierender Regen", "Heavy freezing rain", "rain"),
        71 to Wmo("❄", "Leichter Schneefall", "Slight snow fall", "snow"),
        73 to Wmo("❄", "Schneefall", "Snow fall", "snow"),
        75 to Wmo("❄", "Starker Schneefall", "Heavy snow fall", "snow"),
        77 to Wmo("❄", "Schneegriesel", "Snow grains", "snow"),
        80 to Wmo("☂", "Leichte Regenschauer", "Slight rain showers", "rain"),
        81 to Wmo("☂", "Regenschauer", "Rain showers", "rain"),
        82 to Wmo("☂", "Starke Regenschauer", "Violent rain showers", "rain"),
        85 to Wmo("❄", "Leichte Schneeschauer", "Slight snow showers", "snow"),
        86 to Wmo("❄", "Starke Schneeschauer", "Heavy snow showers", "snow"),
        95 to Wmo("ϟ", "Gewitter", "Thunderstorm", "thunderstorm"),
        96 to Wmo("ϟ", "Gewitter mit Hagel", "Thunderstorm with hail", "thunderstorm"),
        99 to Wmo("ϟ", "Starkes Gewitter mit Hagel", "Thunderstorm with heavy hail", "thunderstorm"),
    )

    fun weatherInfo(code: Int, language: String = "en"): WeatherCondition {
        val info = wmo[code]
        if (info == null) {
            return WeatherCondition("•", if (language == "de") "Unbekannt" else "Unknown", "unknown")
        }
        return WeatherCondition(
            info.symbol,
            if (language == "de") info.de else info.en,
            info.icon,
        )
    }

    fun round(value: Double): Int {
        if (!value.isFinite()) return 0
        return if (value >= 0) floor(value + 0.5).toInt() else ceil(value - 0.5).toInt()
    }

    fun buildForecastUrl(
        latitude: Double,
        longitude: Double,
        timezone: String = "Europe/Berlin",
    ): String {
        val current = "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m"
        val hourly = "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,cloud_cover"
        return "https://api.open-meteo.com/v1/forecast?" +
            "latitude=${formatNumber(latitude)}" +
            "&longitude=${formatNumber(longitude)}" +
            "&current=$current" +
            "&hourly=$hourly" +
            "&forecast_days=3" +
            "&timezone=${encode(timezone)}"
    }

    fun buildGeocodingUrl(query: String, count: Int = 8, language: String = "en"): String {
        return "https://geocoding-api.open-meteo.com/v1/search?" +
            "name=${encode(query.trim())}" +
            "&count=$count" +
            "&language=${encode(language)}" +
            "&format=json"
    }

    fun locationFromGeocodingResult(result: GeocodingHit?): WeatherLocation? {
        val name = result?.name
        if (name.isNullOrBlank() ||
            result.latitude.isNaN() || result.longitude.isNaN() ||
            !result.latitude.isFinite() || !result.longitude.isFinite()
        ) {
            return null
        }
        val detail = listOfNotNull(result.admin1, result.country).filter { it.isNotBlank() }
        return WeatherLocation(
            id = coordinateId(result.latitude, result.longitude),
            name = name,
            label = (listOf(name) + detail).joinToString(", "),
            latitude = result.latitude,
            longitude = result.longitude,
            timezone = result.timezone?.takeIf { it.isNotBlank() } ?: "auto",
        )
    }

    fun parseLocations(value: String?, fallback: List<WeatherLocation>): List<WeatherLocation> {
        return try {
            val parsed = json.decodeFromString<List<WeatherLocationJson>>(value ?: "")
            val valid = parsed.mapNotNull { location ->
                val name = location.name ?: return@mapNotNull null
                if (name.isBlank() || !location.latitude.isFinite() || !location.longitude.isFinite()) {
                    return@mapNotNull null
                }
                WeatherLocation(
                    id = location.id?.takeIf { it.isNotBlank() }
                        ?: coordinateId(location.latitude, location.longitude),
                    name = name,
                    label = location.label?.takeIf { it.isNotBlank() } ?: name,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timezone = location.timezone?.takeIf { it.isNotBlank() } ?: "auto",
                )
            }.take(MAX_LOCATIONS)
            if (valid.isNotEmpty()) valid else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    fun serializeLocations(locations: List<WeatherLocation>): String {
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(WeatherLocationJson.serializer()),
            locations.map {
                WeatherLocationJson(
                    id = it.id,
                    name = it.name,
                    label = it.label,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timezone = it.timezone,
                )
            },
        )
    }

    fun chartForecast(
        payload: ForecastPayload,
        hours: Int = CHART_HOURS,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<HourlyPoint> {
        val hourly = payload.hourly ?: return emptyList()
        if (hourly.time.isEmpty()) return emptyList()
        val midnight = now.toLocalDate().atStartOfDay()
        var start = hourly.time.indexOfFirst { parseHour(it) >= midnight }
        if (start < 0) start = 0
        val length = minOf(hours, hourly.time.size - start)
        return (0 until length).map { offset ->
            val i = start + offset
            HourlyPoint(
                time = parseHour(hourly.time[i]),
                temperature = hourly.temperature[i],
                apparent = hourly.apparentTemperature[i],
                precipitationProbability = hourly.precipitationProbability.getOrElse(i) { 0.0 },
                precipitation = hourly.precipitation.getOrElse(i) { 0.0 },
                weatherCode = hourly.weatherCode[i],
                wind = hourly.windSpeed[i],
                cloudCover = hourly.cloudCover.getOrElse(i) { 0.0 },
            )
        }
    }

    fun chartDaySegments(forecast: List<HourlyPoint>, locale: String = "en-US"): List<DaySegment> {
        val javaLocale = Locale.forLanguageTag(locale)
        val segments = mutableListOf<DaySegment>()
        forecast.forEachIndexed { index, point ->
            val key = point.time.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val current = segments.lastOrNull()
            if (current?.key == key) {
                segments[segments.lastIndex] = current.copy(end = index + 1)
                return@forEachIndexed
            }
            segments += DaySegment(
                key = key,
                label = point.time.toLocalDate().format(
                    DateTimeFormatter.ofPattern("EEEE", javaLocale),
                ),
                start = index,
                end = index + 1,
            )
        }
        return segments
    }

    fun todayTemperatureRange(forecast: List<HourlyPoint>): Pair<Int, Int> {
        if (forecast.isEmpty()) return 0 to 0
        val today = forecast.first().time.toLocalDate()
        val temps = forecast.filter { it.time.toLocalDate().isEqual(today) }.map { it.temperature }
        if (temps.isEmpty()) return 0 to 0
        return round(temps.min()) to round(temps.max())
    }

    fun validateForecast(payload: ForecastPayload): ForecastPayload {
        val current = payload.current
        val hourly = payload.hourly
        if (current == null || hourly == null) {
            error("Incomplete weather data")
        }
        val length = hourly.time.size
        if (length < 2 ||
            hourly.temperature.size != length ||
            hourly.apparentTemperature.size != length ||
            hourly.precipitationProbability.size != length ||
            hourly.precipitation.size != length ||
            hourly.weatherCode.size != length ||
            hourly.windSpeed.size != length
        ) {
            error("Inconsistent weather data")
        }
        return payload
    }

    fun upcomingRainChance(forecast: List<HourlyPoint>, now: LocalDateTime = LocalDateTime.now()): Int {
        val upcoming = forecast.asSequence()
            .filter { it.time >= now }
            .take(12)
            .map { it.precipitationProbability }
            .toList()
        return if (upcoming.isEmpty()) 0 else round(upcoming.max())
    }

    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Wetterkurve/1.0")
                        .build(),
                )
            }
            .build()
    }

    suspend fun fetchForecast(http: OkHttpClient, location: WeatherLocation): ForecastPayload {
        val body = get(http, buildForecastUrl(location.latitude, location.longitude, location.timezone))
        return validateForecast(json.decodeFromString<ForecastPayload>(body))
    }

    suspend fun searchLocations(
        http: OkHttpClient,
        query: String,
        language: String = "en",
    ): List<WeatherLocation> {
        val body = get(http, buildGeocodingUrl(query, 8, language))
        val response = json.decodeFromString<GeocodingResponse>(body)
        return response.results.orEmpty().mapNotNull(::locationFromGeocodingResult)
    }

    private suspend fun get(http: OkHttpClient, url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            response.body?.string() ?: error("Incomplete weather data")
        }
    }

    private fun parseHour(value: String): LocalDateTime {
        val normalized = if (value.length == 16) "$value:00" else value
        return LocalDateTime.parse(normalized)
    }

    private fun coordinateId(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
    }

    private fun formatNumber(value: Double): String {
        val text = value.toString()
        return if (text.contains('E') || text.contains('e')) {
            String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        } else {
            text
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private data class Wmo(
        val symbol: String,
        val de: String,
        val en: String,
        val icon: String,
    )
}
