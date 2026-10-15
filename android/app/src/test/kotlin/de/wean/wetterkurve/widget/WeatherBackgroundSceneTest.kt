package de.wean.wetterkurve.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherBackgroundSceneTest {
    @Test fun recognizedWeatherSelectsItsSpecificImageNotAnIconGroup() {
        val groups = mapOf(
            WeatherBackgroundScene.CLEAR_DAY to listOf(0),
            WeatherBackgroundScene.PARTLY_DAY to listOf(1, 2),
            WeatherBackgroundScene.OVERCAST_DAY to listOf(3),
            WeatherBackgroundScene.FOG_DAY to listOf(45, 48),
            WeatherBackgroundScene.DRIZZLE to listOf(51, 53, 55),
            WeatherBackgroundScene.RAIN to listOf(61, 63, 80, 81),
            WeatherBackgroundScene.HEAVY_RAIN to listOf(65, 82),
            WeatherBackgroundScene.FREEZING_RAIN to listOf(56, 57, 66, 67),
            WeatherBackgroundScene.SNOW to listOf(71, 73, 77, 85),
            WeatherBackgroundScene.HEAVY_SNOW to listOf(75, 86),
            WeatherBackgroundScene.THUNDERSTORM to listOf(95),
            WeatherBackgroundScene.HAIL to listOf(96, 99),
        )
        for ((scene, codes) in groups) for (code in codes)
            assertEquals("code $code", scene, weatherBackgroundScene(code, true))
    }
    @Test fun nighttimeSkiesNeverShowTheDaytimeSun() {
        for ((code, scene) in mapOf(0 to WeatherBackgroundScene.CLEAR_NIGHT,
            1 to WeatherBackgroundScene.PARTLY_NIGHT, 2 to WeatherBackgroundScene.PARTLY_NIGHT,
            3 to WeatherBackgroundScene.OVERCAST_NIGHT, 45 to WeatherBackgroundScene.FOG_NIGHT,
            48 to WeatherBackgroundScene.FOG_NIGHT))
            assertEquals(scene, weatherBackgroundScene(code, false))
    }
    @Test fun mixedRainAndSnowRequiresActualMeasurementsAndDoesNotMaskHazards() {
        assertEquals(WeatherBackgroundScene.SLEET, weatherBackgroundScene(73, true, rain=0.2, snowfall=0.1))
        assertEquals(WeatherBackgroundScene.SNOW, weatherBackgroundScene(73, true, rain=0.0, snowfall=0.1))
        assertEquals(WeatherBackgroundScene.RAIN, weatherBackgroundScene(63, true))
        assertEquals(WeatherBackgroundScene.FREEZING_RAIN, weatherBackgroundScene(67, true, rain=1.0, snowfall=0.1))
        assertEquals(WeatherBackgroundScene.HAIL, weatherBackgroundScene(96, true, rain=1.0, snowfall=0.1))
        assertEquals(WeatherBackgroundScene.UNKNOWN, weatherBackgroundScene(-1, true, rain=1.0, snowfall=0.1))
    }
    @Test fun windDoesNotHidePrecipitationOrFogAndSunEventsOnlyAffectFairSkies() {
        assertEquals(WeatherBackgroundScene.WIND, weatherBackgroundScene(2, true, wind=50.0))
        assertEquals(WeatherBackgroundScene.PARTLY_DAY, weatherBackgroundScene(2, true, wind=49.9))
        assertEquals(WeatherBackgroundScene.RAIN, weatherBackgroundScene(63, true, wind=90.0, solarEvent="sunrise"))
        assertEquals(WeatherBackgroundScene.FOG_DAY, weatherBackgroundScene(45, true, wind=90.0))
        assertEquals(WeatherBackgroundScene.UNKNOWN, weatherBackgroundScene(1234, true, wind=90.0))
        assertEquals(WeatherBackgroundScene.SUNRISE, weatherBackgroundScene(0, false, solarEvent="sunrise"))
        assertEquals(WeatherBackgroundScene.SUNSET, weatherBackgroundScene(2, true, solarEvent="sunset"))
    }

    @Test fun sunTransitionWindowUsesOnlyValidDateMatchedTimes() {
        val daily = de.wean.wetterkurve.SolarTimes(listOf("2026-10-12T07:26"), listOf("2026-10-12T18:19"))
        fun event(time: String) = currentBackgroundSunEvent(daily, java.time.LocalDateTime.parse("2026-10-12T$time"))
        assertEquals("sunrise", event("07:11"))
        assertEquals("sunrise", event("07:41"))
        assertEquals(null, event("07:10:59"))
        assertEquals("sunset", event("18:19"))
        assertEquals(null, currentBackgroundSunEvent(daily, java.time.LocalDateTime.parse("2026-10-13T07:26")))
        assertEquals(null, currentBackgroundSunEvent(daily.copy(sunset=emptyList()), java.time.LocalDateTime.parse("2026-10-12T07:26")))
        assertEquals(null, currentBackgroundSunEvent(daily.copy(sunrise=listOf("bad")), java.time.LocalDateTime.parse("2026-10-12T07:26")))
    }
}
