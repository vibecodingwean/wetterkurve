package de.wean.wetterkurve.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import de.wean.wetterkurve.R

/** Bounded decoded images; cached bitmaps are immutable and never recycled while another draw may use them. */
private val weatherBackgroundCache = object : LruCache<Int, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
}

internal fun backgroundScene(snapshot: WidgetSnapshot): WeatherBackgroundScene {
    val code = snapshot.weatherCode ?: when (snapshot.icon.removeSuffix("-night")) {
        "clear" -> 0
        "partly-cloudy" -> 2
        "cloudy" -> 3
        "fog" -> 45
        "drizzle" -> 51
        "rain" -> 63
        "snow" -> 73
        "thunderstorm" -> 95
        else -> -1
    }
    return weatherBackgroundScene(code, snapshot.isDay, snapshot.windSpeed, snapshot.rain,
        snapshot.snowfall, snapshot.backgroundSunEvent)
}

internal fun backgroundResource(scene: WeatherBackgroundScene, medium: Boolean): Int? = when (scene) {
        WeatherBackgroundScene.CLEAR_DAY -> if (medium) R.drawable.weather_bg_clear_day_medium else R.drawable.weather_bg_clear_day_small
        WeatherBackgroundScene.CLEAR_NIGHT -> if (medium) R.drawable.weather_bg_clear_night_medium else R.drawable.weather_bg_clear_night_small
        WeatherBackgroundScene.PARTLY_DAY -> if (medium) R.drawable.weather_bg_partly_day_medium else R.drawable.weather_bg_partly_day_small
        WeatherBackgroundScene.PARTLY_NIGHT -> if (medium) R.drawable.weather_bg_partly_night_medium else R.drawable.weather_bg_partly_night_small
        WeatherBackgroundScene.OVERCAST_DAY -> if (medium) R.drawable.weather_bg_overcast_day_medium else R.drawable.weather_bg_overcast_day_small
        WeatherBackgroundScene.OVERCAST_NIGHT -> if (medium) R.drawable.weather_bg_overcast_night_medium else R.drawable.weather_bg_overcast_night_small
        WeatherBackgroundScene.FOG_DAY -> if (medium) R.drawable.weather_bg_fog_day_medium else R.drawable.weather_bg_fog_day_small
        WeatherBackgroundScene.FOG_NIGHT -> if (medium) R.drawable.weather_bg_fog_night_medium else R.drawable.weather_bg_fog_night_small
        WeatherBackgroundScene.DRIZZLE -> if (medium) R.drawable.weather_bg_drizzle_medium else R.drawable.weather_bg_drizzle_small
        WeatherBackgroundScene.RAIN -> if (medium) R.drawable.weather_bg_rain_medium else R.drawable.weather_bg_rain_small
        WeatherBackgroundScene.HEAVY_RAIN -> if (medium) R.drawable.weather_bg_heavy_rain_medium else R.drawable.weather_bg_heavy_rain_small
        WeatherBackgroundScene.FREEZING_RAIN -> if (medium) R.drawable.weather_bg_freezing_rain_medium else R.drawable.weather_bg_freezing_rain_small
        WeatherBackgroundScene.SNOW -> if (medium) R.drawable.weather_bg_snow_medium else R.drawable.weather_bg_snow_small
        WeatherBackgroundScene.HEAVY_SNOW -> if (medium) R.drawable.weather_bg_heavy_snow_medium else R.drawable.weather_bg_heavy_snow_small
        WeatherBackgroundScene.SLEET -> if (medium) R.drawable.weather_bg_sleet_medium else R.drawable.weather_bg_sleet_small
        WeatherBackgroundScene.THUNDERSTORM -> if (medium) R.drawable.weather_bg_thunderstorm_medium else R.drawable.weather_bg_thunderstorm_small
        WeatherBackgroundScene.HAIL -> if (medium) R.drawable.weather_bg_hail_medium else R.drawable.weather_bg_hail_small
        WeatherBackgroundScene.WIND -> if (medium) R.drawable.weather_bg_wind_medium else R.drawable.weather_bg_wind_small
        WeatherBackgroundScene.SUNRISE -> if (medium) R.drawable.weather_bg_sunrise_medium else R.drawable.weather_bg_sunrise_small
        WeatherBackgroundScene.SUNSET -> if (medium) R.drawable.weather_bg_sunset_medium else R.drawable.weather_bg_sunset_small
        WeatherBackgroundScene.UNKNOWN -> null
    }

/** Center-fill stays inside rounded corners at every host-provided size; the neutral gradient is the fallback. */
internal fun drawWeatherCardBackground(context: Context, canvas: Canvas, snapshot: WidgetSnapshot,
    width: Int, height: Int, corner: Float, medium: Boolean) {
    if (!snapshot.showWidgetWeatherImages) return
    val scene = backgroundScene(snapshot)
    val resource = backgroundResource(scene, medium) ?: return
    val source = weatherBackgroundCache.get(resource) ?: BitmapFactory.decodeResource(context.resources, resource)?.also {
        weatherBackgroundCache.put(resource, it)
    } ?: return
    val target = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val factor = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
    val cropWidth = (width / factor).toInt().coerceIn(1, source.width)
    val cropHeight = (height / factor).toInt().coerceIn(1, source.height)
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    val save = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(target, corner, corner, Path.Direction.CW) })
    canvas.drawBitmap(source, Rect(left, top, left + cropWidth, top + cropHeight), target,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    // Lift the night sky toward navy while retaining at least 4.5:1 contrast with white
    // labels, even if an image contains a pure-white star or cloud highlight.
    val nightSky = scene == WeatherBackgroundScene.CLEAR_NIGHT ||
        scene == WeatherBackgroundScene.PARTLY_NIGHT ||
        scene == WeatherBackgroundScene.OVERCAST_NIGHT ||
        scene == WeatherBackgroundScene.FOG_NIGHT
    canvas.drawColor(if (nightSky) Color.argb(165, 25, 40, 65)
        else Color.argb(if (snapshot.isDay) 150 else 195, 0, 0, 0))
    canvas.restoreToCount(save)
}
