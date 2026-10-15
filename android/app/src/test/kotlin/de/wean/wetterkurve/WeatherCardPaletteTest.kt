package de.wean.wetterkurve

import de.wean.wetterkurve.widget.weatherCardPalette
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class WeatherCardPaletteTest {
    @Test
    fun weatherAndNightHaveDistinctMoods() {
        val conditions = listOf("clear", "partly-cloudy", "cloudy", "fog", "rain", "snow", "thunderstorm")
        val palettes = conditions.map { weatherCardPalette(it, true) }
        assertTrue("weather conditions all look the same", palettes.distinct().size >= 6)
        for (icon in conditions) assertNotEquals("night must darken $icon",
            weatherCardPalette(icon, true), weatherCardPalette(icon, false))
    }

    @Test
    fun whiteWidgetLabelsRemainReadableForEveryMood() {
        for (day in listOf(true, false)) for (icon in listOf("clear", "partly-cloudy", "cloudy", "fog", "rain", "drizzle", "snow", "thunderstorm", "unknown")) {
            val palette = weatherCardPalette(icon, day)
            for (color in listOf(palette.start, palette.end)) {
                val components = listOf(16, 8, 0).map { shift ->
                    val s = ((color ushr shift) and 255) / 255.0
                    if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
                }
                val light = components[0] * 0.2126 + components[1] * 0.7152 + components[2] * 0.0722
                assertTrue("white text contrast too low for $icon/day=$day", 1.05 / (light + 0.05) >= 4.5)
            }
        }
    }
}
