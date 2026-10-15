package de.wean.wetterkurve.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.wean.wetterkurve.HourlyPoint
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherBackgroundRenderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val examples = listOf(
        "clear_day" to sample(0), "clear_night" to sample(0, false),
        "partly_day" to sample(2), "partly_night" to sample(2, false),
        "overcast_day" to sample(3), "overcast_night" to sample(3, false),
        "fog_day" to sample(45), "fog_night" to sample(45, false),
        "drizzle" to sample(51), "rain" to sample(63), "heavy_rain" to sample(65),
        "freezing_rain" to sample(67), "snow" to sample(73), "heavy_snow" to sample(75),
        "sleet" to sample(73).copy(rain=0.2, snowfall=0.1),
        "thunderstorm" to sample(95), "hail" to sample(96), "wind" to sample(2).copy(windSpeed=60.0),
        "sunrise" to sample(0).copy(backgroundSunEvent="sunrise"),
        "sunset" to sample(0).copy(backgroundSunEvent="sunset"),
    )
    private fun sample(code: Int, day: Boolean = true) = WidgetSnapshot(
        "Berlin", if (code in 71..86) "1°" else "12°",
        de.wean.wetterkurve.WeatherService.weatherInfo(code, "de", day).description,
        de.wean.wetterkurve.WeatherService.weatherInfo(code, "de", day).icon, "", null,
        hours = (1..6).map { offset -> HourlyPoint(LocalDateTime.of(2026,10,12,12,0).plusHours(offset.toLong()),
            12.0-offset, 10.0, 30.0, 0.5, code, 12.0, isDay=day) },
        temperatureRange="H:14° T:1°", isDay=day, weatherCode=code, showWidgetWeatherImages=true,
    )

    @Test fun everyGeneratedScenePaintsBothRealWidgetSizesWithTransparentCorners() {
        for (medium in listOf(false, true)) {
            val density = context.resources.displayMetrics.density
            val width = ((if (medium) 320f else 150f) * density).toInt()
            val height = (190f * density).toInt()
            val neutral = renderWeatherCardFace(context, sample(-1), width, height, medium)
            val signatures = mutableSetOf<Int>()
            for ((name, snapshot) in examples) {
                val resource = backgroundResource(backgroundScene(snapshot), medium)
                assertNotNull("missing $name resource", resource)
                val source = BitmapFactory.decodeResource(context.resources, resource!!)
                assertNotNull("undecodable $name", source)
                assertTrue("wrong crop $name", if (medium) source.width > source.height else source.height > source.width)
                source.recycle()
                val face = renderWeatherCardFace(context, snapshot, width, height, medium)
                assertEquals("unclipped corner $name", 0, Color.alpha(face.getPixel(0, 0)))
                var changed = 0
                var signature = 1
                for (y in height*3/4 until height*9/10 step 3) for (x in width/4 until width*3/4 step 3) {
                    val pixel = face.getPixel(x,y)
                    if (pixel != neutral.getPixel(x,y)) changed++
                    signature = 31*signature+pixel
                }
                assertTrue("$name is still the old gradient", changed > 50)
                assertTrue("$name duplicates another scene", signatures.add(signature))
                System.getenv("WETTERKURVE_RENDER_OUTPUT")?.let { directory ->
                    val file = java.io.File(directory, "${if(medium) "medium" else "small"}-$name.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { face.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                face.recycle()
            }
            neutral.recycle()
        }
    }

    @Test fun whiteLabelsKeepContrastAndAllPrecipitationBackgroundsDarkenAtNight() {
        for (code in listOf(51,63,65,67,73,75,95,96)) for (medium in listOf(false,true)) {
            val width = if(medium) 640 else 300
            val day = sample(code).copy(locationName="",temperature="",condition="",temperatureRange=null,hours=emptyList())
            val daytime = renderWeatherCardFace(context,day,width,380,medium)
            val nighttime = renderWeatherCardFace(context,day.copy(isDay=false),width,380,medium)
            var changed = 0
            for (y in 260 until 350 step 4) for(x in 50 until width-50 step 4) {
                val color = daytime.getPixel(x,y)
                assertTrue("insufficient white-text contrast code=$code", maxOf(Color.red(color),Color.green(color),Color.blue(color)) <= 105)
                val night = nighttime.getPixel(x,y)
                assertTrue(Color.red(night)<=Color.red(color) && Color.green(night)<=Color.green(color) && Color.blue(night)<=Color.blue(color))
                if (color != night) changed++
            }
            assertTrue("no night tint",changed>50)
            daytime.recycle();nighttime.recycle()
        }
    }

    @Test fun nightSkyAndCloudsRemainVisibleBehindReadableWhiteLabels() {
        for (code in listOf(0, 2, 3, 45)) for (medium in listOf(false, true)) {
            val width = if (medium) 640 else 300
            val blank = sample(code, day = false).copy(locationName = "", temperature = "",
                condition = "", temperatureRange = null, hours = emptyList())
            val face = renderWeatherCardFace(context, blank, width, 380, medium)
            var brightness = 0L
            var count = 0
            for (y in 260 until 330 step 3) for (x in width / 4 until width * 3 / 4 step 3) {
                val pixel = face.getPixel(x, y)
                val brightest = maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                assertTrue("night background lacks 4.5:1 white-label contrast code=$code medium=$medium",
                    whiteContrast(pixel) >= 4.5)
                brightness += brightest
                count++
            }
            assertTrue("night scene disappears into black code=$code medium=$medium: ${brightness / count}",
                brightness / count >= 35)
            face.recycle()
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

    @Test fun switchingImagesOffRestoresExactlyTheEstablishedGradientOnBothWidgets() {
        for (medium in listOf(false, true)) for (code in listOf(0, 63, 73, 95)) {
            val width = if (medium) 320 else 150
            val off = sample(code).copy(showWidgetWeatherImages=false)
            val before = renderWeatherCardFace(context, off, width,190,medium)
            val on = renderWeatherCardFace(context,off.copy(showWidgetWeatherImages=true),width,190,medium)
            val restored = renderWeatherCardFace(context,off,width,190,medium)
            val a=IntArray(width*190);val b=IntArray(width*190);val c=IntArray(width*190)
            before.getPixels(a,0,width,0,0,width,190);on.getPixels(b,0,width,0,0,width,190);restored.getPixels(c,0,width,0,0,width,190)
            assertFalse("option has no visible effect medium=$medium code=$code",a.contentEquals(b))
            assertArrayEquals("disabling does not restore old face",a,c)
            val palette = weatherCardPalette(off.icon, off.isDay)
            val pixel=before.getPixel(width/2,185)
            assertTrue("not the existing gradient",Color.red(pixel) in minOf(Color.red(palette.start),Color.red(palette.end))..maxOf(Color.red(palette.start),Color.red(palette.end)))
            before.recycle();on.recycle();restored.recycle()
        }
    }
}
