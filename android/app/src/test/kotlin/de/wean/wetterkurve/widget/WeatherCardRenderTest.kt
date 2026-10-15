package de.wean.wetterkurve.widget

import android.graphics.Bitmap
import android.graphics.Rect
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.wean.wetterkurve.HourlyPoint
import java.time.LocalDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inspect ink from the production Canvas renderer at sizes a launcher may actually send. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherCardRenderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val density get() = context.resources.displayMetrics.density
    private val snapshot = WidgetSnapshot(
        locationName = "Schönau am Königssee",
        temperature = "-12°",
        condition = "Slight rain showers",
        icon = "rain",
        status = "",
        chart = null,
        temperatureRange = "H:12° L:-12°",
    )

    @Test
    fun shortCardCityAndNegativeTemperatureOccupySeparatePaintedRows() {
        for (widthDp in listOf(110f, 150f)) {
            val width = (widthDp * density).toInt()
            val height = (48f * density).toInt()
            val blank = face(snapshot.copy(locationName = "", temperature = ""), width, height, false)
            val city = face(snapshot.copy(temperature = ""), width, height, false)
            val temperature = face(snapshot.copy(locationName = ""), width, height, false)
            val cityInk = changedBounds(blank, city)
            val tempInk = changedBounds(blank, temperature)
            assertNotNull("no city ink at ${widthDp}dp", cityInk)
            assertNotNull("no temperature ink at ${widthDp}dp", tempInk)
            assertTrue("city and temperature overlap at ${widthDp}dp: $cityInk / $tempInk",
                cityInk!!.bottom + (2f * density).toInt() < tempInk!!.top)
            // The real Glance refresh action occupies the top-right corner of this face.
            assertTrue("city enters refresh target at ${widthDp}dp", cityInk.right < width - (26f * density).toInt())
            blank.recycle(); city.recycle(); temperature.recycle()
        }
    }

    @Test
    fun minimumHourlyCardKeepsConditionClearOfIconAndPaintsSixColumns() {
        val width = (320f * density).toInt()
        val height = (100f * density).toInt()
        val blankCondition = face(snapshot.copy(condition = ""), width, height, true)
        val withCondition = face(snapshot, width, height, true)
        val conditionInk = changedBounds(blankCondition, withCondition)
        assertNotNull("condition has no ink", conditionInk)
        // The right fifth contains the condition icon and refresh target at minimum size.
        assertTrue("condition paints through icon space: $conditionInk",
            conditionInk!!.right < (width * 0.8f).toInt())

        val hours = (15..20).map { hour ->
            HourlyPoint(LocalDateTime.of(2026, 10, 11, hour, 0), hour.toDouble(),
                hour.toDouble(), 20.0, 0.2, 61, 8.0)
        }
        val withoutHours = face(snapshot.copy(hours = emptyList()), width, height, true)
        val withHours = face(snapshot.copy(hours = hours), width, height, true)
        for (column in 0 until 6) {
            val left = width * column / 6
            val right = width * (column + 1) / 6
            assertTrue("hour ${column + 1} has no painted forecast",
                changedPixels(withoutHours, withHours, left, right, height / 2, height) > 10)
        }
        blankCondition.recycle(); withCondition.recycle(); withoutHours.recycle(); withHours.recycle()
    }

    @Test
    fun allSixHourlySymbolsSwitchFromSunToMoonInProductionRenderer() {
        for (code in listOf(0, 2)) {
            val hours = (18..23).map { hour ->
                HourlyPoint(LocalDateTime.of(2026, 10, 12, hour, 0), 10.0, 10.0, 0.0, 0.0, code, 0.0, isDay = true)
            }
            val width = (320f * density).toInt()
            val height = (190f * density).toInt()
            val day = face(snapshot.copy(hours = hours), width, height, true)
            val night = face(snapshot.copy(hours = hours.map { it.copy(isDay = false) }), width, height, true)
            val layout = weatherCardLayout(190f, true)
            val top = ((layout.hourIcon!! - layout.hourIconSize / 2) * density).toInt()
            val bottom = ((layout.hourIcon + layout.hourIconSize / 2) * density).toInt()
            for (column in 0 until 6) {
                assertTrue("hour $column still paints a sun at night for code $code",
                    changedPixels(day, night, width * column / 6, width * (column + 1) / 6, top, bottom) > 10)
            }
            day.recycle(); night.recycle()
        }
    }

    @Test
    fun bothCardSizesPaintCurrentWeatherMoodAndDarkenAtNight() {
        for (medium in listOf(false, true)) for (icon in listOf("clear", "cloudy", "rain", "snow", "thunderstorm")) {
            val width = (if (medium) 320f else 150f).times(density).toInt()
            val height = (190f * density).toInt()
            val day = face(snapshot.copy(icon = icon, isDay = true), width, height, medium)
            val night = face(snapshot.copy(icon = icon, isDay = false), width, height, medium)
            val x = width / 2; val y = (height * 0.95f).toInt()
            assertTrue("$icon mood does not darken for medium=$medium", day.getPixel(x, y) != night.getPixel(x, y))
            System.getProperty("wetterkurve.renderOutput")?.let { output ->
                val directory = java.io.File(output).apply { mkdirs() }
                for ((name, bitmap) in listOf("day" to day, "night" to night)) {
                    java.io.File(directory, "${if (medium) "medium" else "small"}-$icon-$name.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            day.recycle(); night.recycle()
        }
    }

    @Test
    fun sunEventSymbolsPaintDifferentlyAtTheirExactForecastTimes() {
        val width = (320f * density).toInt(); val height = (190f * density).toInt()
        val hour = HourlyPoint(LocalDateTime.of(2026, 10, 12, 18, 19), 10.0, 10.0, 0.0, 0.0, 0, 0.0, isDay = false)
        val normal = face(snapshot.copy(hours = listOf(hour)), width, height, true)
        for (event in de.wean.wetterkurve.SolarEvent.entries) {
            val face = face(snapshot.copy(hours = listOf(hour.copy(solarEvent = event))), width, height, true)
            val layout = weatherCardLayout(190f, true)
            assertTrue("sun event is still the ordinary weather icon",
                changedPixels(normal, face, 0, width / 6,
                    ((layout.hourIcon!! - layout.hourIconSize / 2) * density).toInt(),
                    ((layout.hourIcon + layout.hourIconSize / 2) * density).toInt()) > 10)
            face.recycle()
        }
        normal.recycle()
    }

    private fun face(data: WidgetSnapshot, width: Int, height: Int, medium: Boolean) =
        renderWeatherCardFace(context, data, width, height, medium)

    private fun changedBounds(before: Bitmap, after: Bitmap): Rect? {
        var left = before.width; var top = before.height; var right = -1; var bottom = -1
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun changedPixels(before: Bitmap, after: Bitmap, left: Int, right: Int, top: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) count++
        }
        return count
    }
}
