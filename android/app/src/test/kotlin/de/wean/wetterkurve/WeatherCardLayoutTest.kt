package de.wean.wetterkurve

import de.wean.wetterkurve.widget.fitCardText
import de.wean.wetterkurve.widget.weatherCardLayout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCardLayoutTest {
    @Test
    fun smallMinimumKeepsCityTemperatureAndConditionSeparate() {
        val layout = weatherCardLayout(110f, medium = false)
        assertTrue(layout.city + 4 < layout.temperature - layout.temperatureSize)
        assertTrue(layout.temperature + 5 < layout.condition - 11)
        assertTrue(layout.condition + 3 < 110)
        assertNull(layout.range)
    }

    @Test
    fun mediumMinimumKeepsAllSixHourRowsInsideCard() {
        val layout = weatherCardLayout(100f, medium = true)
        assertTrue(layout.city + 2 < layout.temperature - layout.temperatureSize)
        assertTrue(layout.temperature + 5 < layout.hourTime!! - 9)
        assertTrue(layout.hourTime + 2 < layout.hourIcon!! - layout.hourIconSize / 2)
        assertTrue(layout.hourIcon + layout.hourIconSize / 2 < layout.hourTemperature!! - layout.hourTemperatureSize)
        assertTrue(layout.hourTemperature + 3 < 100)
    }

    @Test
    fun defaultCardsFitAndLongNamesAreShortened() {
        val small = weatherCardLayout(200f, medium = false)
        val medium = weatherCardLayout(190f, medium = true)
        assertTrue(small.range!! + 3 < 200)
        assertTrue(medium.hourTemperature!! + 3 < 190)
        val fitted = fitCardText("Schönau am Königssee", 60f) { it.length * 10f }
        assertTrue(fitted.endsWith("…"))
        assertTrue(fitted.length <= 6)
        assertTrue(fitCardText("Berlin", 100f) { it.length * 10f } == "Berlin")
    }
}
