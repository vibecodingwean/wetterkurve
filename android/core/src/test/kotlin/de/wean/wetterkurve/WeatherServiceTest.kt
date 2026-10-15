package de.wean.wetterkurve

import kotlinx.serialization.json.Json
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
    fun nextHours_usesFutureLocalHoursAndWeatherCodes() {
        val hours = WeatherService.nextHours(samplePayload(), LocalDateTime.of(2026, 7, 30, 10, 35))
        assertEquals((11..16).toList(), hours.map { it.time.hour })
        assertEquals((11..16).map(Int::toDouble), hours.map { it.temperature })
        assertEquals((11..16).toList(), hours.map { it.weatherCode })
        assertEquals(emptyList<HourlyPoint>(), WeatherService.nextHours(samplePayload(), LocalDateTime.of(2026, 8, 3, 0, 0)))
    }

    @Test
    fun nextHours_showsMoonForNightAndSunAfterSunriseFromApiFlags() {
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<ForecastPayload>("""
            {"hourly":{"time":["2026-10-12T05:00","2026-10-12T06:00","2026-10-12T07:00","2026-10-12T08:00"],
            "temperature_2m":[8,8,9,10],"weather_code":[0,2,0,2],"is_day":[0,0,1,1]}}
        """.trimIndent())
        val hours = WeatherService.nextHours(payload, LocalDateTime.of(2026, 10, 12, 4, 30))
        assertEquals(listOf("clear-night", "partly-cloudy-night", "clear", "partly-cloudy"),
            hours.map { WeatherService.hourCondition(it).icon })
        assertEquals(listOf("Clear", "Partly cloudy", "Clear", "Partly cloudy"),
            hours.map { WeatherService.hourCondition(it).description })
    }

    @Test
    fun nextHours_includesExactUpcomingSunEvents() {
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<ForecastPayload>("""
            {"hourly":{"time":["2026-10-12T07:00","2026-10-12T08:00","2026-10-12T18:00","2026-10-12T19:00"],
            "temperature_2m":[8,9,10,9],"weather_code":[0,2,63,0],"is_day":[0,1,1,0]},
            "daily":{"sunrise":["2026-10-12T07:26"],"sunset":["2026-10-12T18:19"]}}
        """.trimIndent())
        val hours = WeatherService.nextHours(payload, LocalDateTime.of(2026, 10, 12, 7, 10))
        assertEquals(listOf("07:26", "08:00", "18:19", "19:00"), hours.map { it.time.toLocalTime().toString() })
        assertEquals(listOf("sunrise", "partly-cloudy", "sunset", "clear-night"),
            hours.map { WeatherService.hourCondition(it).icon })
        assertEquals("Sonnenaufgang", WeatherService.hourCondition(hours[0], "de").description)
        assertEquals("Sunset", WeatherService.hourCondition(hours[2]).description)
    }

    @Test
    fun currentSkySwitchesAtExactSunTimesEvenWithStaleCurrentAndHourlyFlags() {
        val payload = samplePayload().copy(
            current = samplePayload().current!!.copy(weatherCode = 0, isDay = 1, time = "2026-10-12T18:15"),
            hourly = HourlyWeather(time = listOf("2026-10-12T07:00", "2026-10-12T18:00"), isDay = listOf(0, 1)),
            daily = SolarTimes(listOf("2026-10-12T07:26"), listOf("2026-10-12T18:19")),
        )
        for ((time, icon) in listOf("07:25" to "clear-night", "07:26" to "clear", "18:18" to "clear", "18:19" to "clear-night", "23:00" to "clear-night")) {
            val now = LocalDateTime.parse("2026-10-12T$time")
            assertEquals(icon, WeatherService.currentCondition(payload, now).icon)
            assertEquals(icon == "clear", WeatherService.currentIsDay(payload, now))
        }
    }

    @Test
    fun legacyCachesAndShortDaylightArraysRemainUsable() {
        val payload = samplePayload().copy(hourly = samplePayload().hourly!!.copy(isDay = listOf(0)))
        val hours = WeatherService.nextHours(payload, LocalDateTime.of(2026, 7, 30, 20, 30))
        assertEquals(6, hours.size)
        assertEquals("clear-night", WeatherService.hourCondition(hours.first().copy(weatherCode = 0)).icon)
        assertEquals("rain", WeatherService.weatherInfo(63, isDay = false).icon)
        assertEquals("unknown", WeatherService.weatherInfo(1234, isDay = false).icon)
        assertEquals("Partly cloudy", WeatherService.weatherInfo(2, isDay = false).description)
    }

    @Test
    fun forecastRequestsContainCurrentHourlyDaylightAndDailySunTimes() {
        val url = java.net.URI(WeatherService.buildForecastUrl(52.52, 13.405, "Europe/Berlin"))
        val query = url.rawQuery.split("&").associate { part ->
            val pair = part.split("=", limit = 2)
            pair[0] to java.net.URLDecoder.decode(pair[1], "UTF-8")
        }
        assertTrue(query.getValue("current").split(",").contains("is_day"))
        assertTrue(query.getValue("hourly").split(",").contains("is_day"))
        assertEquals("sunrise,sunset", query["daily"])
        assertEquals("Europe/Berlin", query["timezone"])
    }

    @Test
    fun sameHourPolarSunriseAndSunsetBothAppearInChronologicalOrder() {
        val payload = ForecastPayload(hourly = HourlyWeather(
            time = listOf("2026-12-12T11:00", "2026-12-12T12:00"),
            temperature = listOf(-5.0, -4.0), weatherCode = listOf(0, 0), isDay = listOf(0, 0),
        ), daily = SolarTimes(listOf("2026-12-12T11:15"), listOf("2026-12-12T11:47")))
        val hours = WeatherService.nextHours(payload, LocalDateTime.parse("2026-12-12T10:30"))
        assertEquals(listOf("11:15", "11:47", "12:00"), hours.map { it.time.toLocalTime().toString() })
        assertEquals(listOf("sunrise", "sunset", "clear-night"), hours.map { WeatherService.hourCondition(it).icon })
        assertEquals(6, WeatherService.nextHours(samplePayload(), LocalDateTime.parse("2026-07-30T10:30")).size)
    }

    @Test
    fun missingSunArraysRequestUpgradeButPolarPlaceholdersKeepDayFlags() {
        val payload = ForecastPayload(hourly = HourlyWeather(time = listOf("2026-12-12T12:00"), isDay = listOf(0)),
            daily = SolarTimes())
        assertEquals(false, WeatherService.hasWidgetSkyData(payload))
        val polar = payload.copy(daily = SolarTimes(listOf(""), listOf("")))
        assertEquals(true, WeatherService.hasWidgetSkyData(polar))
        assertEquals(false, WeatherService.currentIsDay(polar, LocalDateTime.parse("2026-12-12T12:30")))
        assertEquals(false, WeatherService.hasWidgetSkyData(polar.copy(hourly = polar.hourly!!.copy(isDay = emptyList()))))
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
        val url = WeatherService.buildForecastUrl(48.137, 11.576)
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
    fun language_defaultsToEnglishAndPersistsGermanChoice() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("en", Language.forLocale())
            assertEquals("en", SettingsStore.parse("{}").language)
            val german = SettingsStore.defaultState().copy(language = "de")
            assertEquals(german, SettingsStore.parse(SettingsStore.serialize(german)))
            assertEquals("en", SettingsStore.parse("{\"language\":\"fr\"}").language)
        } finally {
            java.util.Locale.setDefault(previous)
        }
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
        val fallback = listOf(WeatherLocation("x", "München", "München", 48.137, 11.576, "Europe/Berlin"))
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
        assertEquals("Berlin", SettingsStore.parse("nope").locations.single().name)
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
        assertEquals(false, parsed.showWind)
        assertEquals(false, parsed.showFeelsLike)
    }

    @Test
    fun settingsStore_feelsLikeMigratesAndPersistsWithoutChangingOtherLayers() {
        val old = """{"locations":[],"activeLocation":0,"showClouds":false,"showWind":true,"language":"de"}"""
        val migrated = SettingsStore.parse(old)
        assertEquals(false, migrated.showFeelsLike)
        assertEquals(false, migrated.showClouds)
        assertEquals(true, migrated.showWind)
        assertEquals("de", migrated.language)

        val restored = SettingsStore.parse(SettingsStore.serialize(migrated.copy(showFeelsLike = true)))
        assertEquals(true, restored.showFeelsLike)
        assertEquals(false, restored.showClouds)
        assertEquals(true, restored.showWind)
        assertEquals("de", restored.language)
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

    @Test fun currentMixedPrecipitationFieldsAreRequestedAndLegacyCachesStillDecode() {
        val current = """{"temperature_2m":1,"apparent_temperature":-1,"weather_code":73,"wind_speed_10m":12,"relative_humidity_2m":80}"""
        val legacy = Json.decodeFromString<CurrentWeather>(current)
        assertEquals(null, legacy.rain)
        assertEquals(null, legacy.snowfall)
        val mixed = Json.decodeFromString<CurrentWeather>(current.dropLast(1) + ",\"rain\":0.2,\"snowfall\":0.1}")
        assertEquals(0.2, mixed.rain)
        assertEquals(0.1, mixed.snowfall)
        assertTrue(WeatherService.buildForecastUrl(52.52, 13.405).contains("is_day,rain,snowfall"))
    }
}
