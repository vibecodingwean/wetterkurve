package de.wean.wetterkurve.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.wean.wetterkurve.HourlyPoint
import java.time.LocalDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inspect ink from the production Canvas renderer at sizes a launcher may actually send. */
@RunWith(AndroidJUnit4::class)
class WeatherCardNativeRenderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
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
    fun nightScenesPaintReadableSmallAndMediumCardsOnAndroid() {
        for (code in listOf(0, 2, 3, 45)) for (medium in listOf(false, true)) {
            val width = ((if (medium) 320f else 150f) * density).toInt()
            val height = (190f * density).toInt()
            val info = de.wean.wetterkurve.WeatherService.weatherInfo(code, "en", false)
            val night = snapshot.copy(locationName = "", temperature = "", condition = "",
                temperatureRange = null, hours = emptyList(), icon = info.icon, isDay = false,
                weatherCode = code, showWidgetWeatherImages = true)
            val card = face(night, width, height, medium)
            var brightness = 0L
            var count = 0
            for (y in height * 68 / 100 until height * 87 / 100 step 3) {
                for (x in width / 4 until width * 3 / 4 step 3) {
                    val pixel = card.getPixel(x, y)
                    val brightest = maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                    assertTrue("night scene lacks 4.5:1 white-label contrast: $code medium=$medium",
                        whiteContrast(pixel) >= 4.5)
                    brightness += brightest
                    count++
                }
            }
            assertTrue("night scene disappears into black: $code medium=$medium (${brightness / count})",
                brightness / count >= 35)
            if (InstrumentationRegistry.getArguments().getString("saveNightPreviews") == "true") {
                val directory = context.getExternalFilesDir("night-widget-previews")!!
                val preview = face(night.copy(locationName = "Berlin", temperature = "12°",
                    condition = info.description), width, height, medium)
                java.io.File(directory, "${if (medium) "medium" else "small"}-$code.png")
                    .outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                preview.recycle()
            }
            card.recycle()
        }
    }

    private fun whiteContrast(pixel: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(Color.red(pixel)) +
            0.7152 * linear(Color.green(pixel)) + 0.0722 * linear(Color.blue(pixel))
        return 1.05 / (luminance + 0.05)
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
