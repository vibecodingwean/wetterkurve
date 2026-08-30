package de.wean.wetterkurve

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class WeatherServiceTest {
    private fun samplePayload(): ForecastPayload {
        val hours = (0 until 96).map { i ->
            LocalDateTime.of(2026, 7, 30, 0, 0).plusHours(i.toLong())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        }
        val values = hours.indices.map { it.toDouble() }
        val codes = hours.indices.toList()
        return ForecastPayload(
            current = CurrentWeather(21.6, 22.1, 1, 12.0, 50.0),
            hourly = HourlyWeather(
                time = hours,
                temperature = values,
                apparentTemperature = values,
                precipitationProbability = values,
                precipitation = values,
                weatherCode = codes,
                windSpeed = values,
            ),
        )
    }

    @Test
    fun validateForecast_acceptsCompletePayload() {
        val payload = samplePayload()
        assertSame(payload, WeatherService.validateForecast(payload))
    }

    @Test
    fun chartForecast_coversThreeFullDaysFromMidnight() {
        val forecast = WeatherService.chartForecast(
            samplePayload(),
            72,
            LocalDateTime.of(2026, 7, 31, 14, 30),
        )
        assertEquals(72, forecast.size)
        assertEquals(LocalDateTime.of(2026, 7, 31, 0, 0), forecast.first().time)
        assertEquals(LocalDateTime.of(2026, 8, 2, 23, 0), forecast.last().time)
    }

    @Test
    fun chartDaySegments_spanThreeLocalDays() {
        val forecast = WeatherService.chartForecast(
            samplePayload(),
            72,
            LocalDateTime.of(2026, 7, 31, 14, 30),
        )
        val days = WeatherService.chartDaySegments(forecast, "de-DE")
        assertEquals(3, days.size)
        assertEquals("2026-07-31,2026-08-01,2026-08-02", days.joinToString(",") { it.key })
        days.forEach { day ->
            assertTrue(day.label.isNotBlank())
            assertEquals(24, day.end - day.start)
        }
        assertEquals(0, days.first().start)
        assertEquals(72, days.last().end)
    }

    @Test
    fun todayTemperatureRange_usesOnlyTheFirstCalendarDay() {
        val forecast = WeatherService.chartForecast(
            samplePayload(),
            72,
            LocalDateTime.of(2026, 7, 31, 14, 30),
        )
        val today = WeatherService.todayTemperatureRange(forecast)
        assertEquals(24, today.first)
        assertEquals(47, today.second)
        assertTrue(forecast.maxOf { it.temperature } > today.second)
    }

    @Test
    fun weatherInfo_usesLanguageAndIconFallbacks() {
        assertEquals("Klar", WeatherService.weatherInfo(0, "de").description)
        assertEquals("Clear", WeatherService.weatherInfo(0).description)
        assertEquals("Unbekannt", WeatherService.weatherInfo(1234, "de").description)
        assertEquals("Unknown", WeatherService.weatherInfo(1234).description)
        assertEquals("clear", WeatherService.weatherInfo(0).icon)
        assertEquals("rain", WeatherService.weatherInfo(63).icon)
        assertEquals("unknown", WeatherService.weatherInfo(1234).icon)
    }

    @Test
    fun round_matchesJavascriptMathRound() {
        assertEquals(22, WeatherService.round(21.6))
    }

    @Test
    fun buildForecastUrl_matchesGnomeExtension() {
        val url = WeatherService.buildForecastUrl(48.1374, 11.5755)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(url.contains("forecast_days=3"))
        assertTrue(url.contains("timezone=Europe%2FBerlin"))
        assertTrue(url.contains("cloud_cover"))
    }

    @Test
    fun chartForecast_readsCloudCover() {
        val payload = samplePayload()
        val withClouds = payload.copy(
            hourly = payload.hourly!!.copy(cloudCover = payload.hourly!!.time.indices.map { it.toDouble() }),
        )
        val forecast = WeatherService.chartForecast(
            withClouds,
            72,
            LocalDateTime.of(2026, 7, 31, 14, 30),
        )
        assertEquals(24.0, forecast[0].cloudCover)
        assertEquals(25.0, forecast[1].cloudCover)
        assertEquals(95.0, forecast.last().cloudCover)
    }

    @Test
    fun buildGeocodingUrl_encodesQuery() {
        val url = WeatherService.buildGeocodingUrl("São Paulo")
        assertTrue(url.startsWith("https://geocoding-api.open-meteo.com/v1/search?"))
        assertTrue(url.contains("name=S%C3%A3o%20Paulo"))
        assertTrue(url.contains("language=en"))
        assertTrue(WeatherService.buildGeocodingUrl("Berlin", 8, "de").contains("language=de"))
    }

    @Test
    fun language_detectsGermanLocales() {
        assertEquals("de", Language.forLocale("de-DE"))
        assertEquals("de", Language.forLocale("de"))
        assertEquals("de", Language.forLocale("de_DE"))
        assertEquals("en", Language.forLocale("en-US"))
        assertEquals("en", Language.forLocale("en-DE"))
        assertEquals("en", Language.forLocale("fr_FR"))
        assertEquals("en", Language.forLocale("it-IT"))
        assertEquals("Ort suchen …", Language.text("de", "searchLocation"))
        assertEquals("Temperature", Language.text("en", "temperature"))
        assertEquals("Temperatur", Language.text("de", "temperature"))
        assertEquals("Remove Munich", Language.text("en", "removeLocation", mapOf("location" to "Munich")))
        assertEquals(
            "Munich · gespeichert",
            Language.text("de", "saved", mapOf("location" to "Munich")),
        )
    }

    @Test
    fun parseLocations_fallsBackOnInvalidJson() {
        val parsed = WeatherService.parseLocations("not-json", listOf(WeatherService.defaultLocation))
        assertEquals(1, parsed.size)
        assertEquals(WeatherService.defaultLocation.id, parsed[0].id)
    }

    @Test
    fun locationFromGeocodingResult_buildsLabelAndId() {
        val berlin = WeatherService.locationFromGeocodingResult(
            GeocodingHit(
                name = "Berlin",
                admin1 = "Berlin",
                country = "Deutschland",
                latitude = 52.52,
                longitude = 13.405,
                timezone = "Europe/Berlin",
            ),
        )
        assertEquals("Berlin, Berlin, Deutschland", berlin?.label)
        assertEquals("52.5200,13.4050", berlin?.id)
        assertEquals(
            null,
            WeatherService.locationFromGeocodingResult(GeocodingHit(name = "Broken")),
        )
    }

    @Test
    fun parseLocations_readsSavedCity() {
        val berlin = WeatherService.locationFromGeocodingResult(
            GeocodingHit(
                name = "Berlin",
                admin1 = "Berlin",
                country = "Deutschland",
                latitude = 52.52,
                longitude = 13.405,
                timezone = "Europe/Berlin",
            ),
        )!!
        val fallback = listOf(WeatherLocation("x", "München", "München", 48.1374, 11.5755, "Europe/Berlin"))
        val parsed = WeatherService.parseLocations(
            """[{"id":"${berlin.id}","name":"${berlin.name}","label":"${berlin.label}","latitude":${berlin.latitude},"longitude":${berlin.longitude},"timezone":"${berlin.timezone}"}]""",
            fallback,
        )
        assertEquals(1, parsed.size)
        assertEquals("Berlin", parsed[0].name)
    }

    @Test
    fun validateForecast_rejectsIncompletePayload() {
        assertThrows<IllegalStateException> {
            WeatherService.validateForecast(ForecastPayload(current = null, hourly = HourlyWeather()))
        }
    }

    @Test
    fun settingsStore_roundTripsLocations() {
        val berlin = WeatherLocation("52.5200,13.4050", "Berlin", "Berlin, Deutschland", 52.52, 13.405, "Europe/Berlin")
        val json = SettingsStore.serialize(AppState(listOf(berlin), 0))
        val restored = SettingsStore.parse(json)
        assertEquals("Berlin", restored.locations.single().name)
        assertEquals(0, restored.activeLocation)
        assertEquals("Munich", SettingsStore.parse("nope").locations.single().name)
    }

    @Test
    fun settingsStore_persistsActiveLocation() {
        val munich = WeatherService.defaultLocation
        val berlin = WeatherLocation("52.5200,13.4050", "Berlin", "Berlin, Deutschland", 52.52, 13.405, "Europe/Berlin")
        val restored = SettingsStore.parse(SettingsStore.serialize(AppState(listOf(munich, berlin), 1)))
        assertEquals(1, restored.activeLocation)
        assertEquals("Berlin", restored.locations[restored.activeLocation].name)
    }

    @Test
    fun settingsStore_roundTripsLayerToggles() {
        val restored = SettingsStore.parse(
            SettingsStore.serialize(AppState(listOf(WeatherService.defaultLocation), 0, showClouds = false, showWind = false)),
        )
        assertEquals(false, restored.showClouds)
        assertEquals(false, restored.showWind)
    }

    @Test
    fun settingsStore_defaultsLayerTogglesWhenMissing() {
        val parsed = SettingsStore.parse("""{"locations":[],"activeLocation":0}""")
        assertEquals(true, parsed.showClouds)
        assertEquals(true, parsed.showWind)
    }

    @Test
    fun settingsStore_layerTogglesAreIndependent() {
        val onlyWind = SettingsStore.parse(
            SettingsStore.serialize(AppState(listOf(WeatherService.defaultLocation), 0, showClouds = false, showWind = true)),
        )
        assertEquals(false, onlyWind.showClouds)
        assertEquals(true, onlyWind.showWind)
        val onlyClouds = SettingsStore.parse(
            SettingsStore.serialize(AppState(listOf(WeatherService.defaultLocation), 0, showClouds = true, showWind = false)),
        )
        assertEquals(true, onlyClouds.showClouds)
        assertEquals(false, onlyClouds.showWind)
    }
}
