package de.wean.wetterkurve.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.wean.wetterkurve.WeatherService
import de.wean.wetterkurve.iconDrawable
import kotlin.math.min

/** Paint the current card at the launcher's exact widget size. The large chart has its own renderer. */
internal fun renderWeatherCardFace(context: Context, snapshot: WidgetSnapshot, width: Int, height: Int, medium: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val density = context.resources.displayMetrics.density
    val scale = min(1f, width / (if (medium) 320f * density else 150f * density))
    val unit = density * scale
    val pad = 14f * unit
    val corner = 24f * unit
    val layout = weatherCardLayout(height / unit, medium)
    val palette = weatherCardPalette(snapshot.icon, snapshot.isDay)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            palette.start, palette.end, Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner, background)
    drawWeatherCardBackground(context, canvas, snapshot, width, height, corner, medium)
    val short = height < 95f * unit
    fun text(value: String, x: Float, y: Float, size: Float, bold: Boolean = false,
             align: Paint.Align = Paint.Align.LEFT, maxWidth: Float? = null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * unit
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
            if (snapshot.showWidgetWeatherImages) setShadowLayer(1.5f * unit, 0f, 0.5f * unit, Color.BLACK)
        }
        val shown = maxWidth?.let { fitCardText(value, it, paint::measureText) } ?: value
        canvas.drawText(shown, x, y, paint)
    }
    fun icon(name: String, centerX: Float, centerY: Float, side: Float) {
        val source = BitmapFactory.decodeResource(context.resources, iconDrawable(name)) ?: return
        val half = side / 2f
        canvas.drawBitmap(source, null, RectF(centerX - half, centerY - half, centerX + half, centerY + half), Paint(Paint.ANTI_ALIAS_FLAG))
        source.recycle()
    }
    if (short) {
        val logicalHeight = height / unit
        // A one-row face cannot fit icon, negative temperature, city and refresh at 110×48 dp.
        // Put the city above the icon/temperature row and reserve the top-right refresh target.
        text(snapshot.locationName, pad, min(20f, logicalHeight * 0.28f) * unit,
            15f, true, maxWidth = width - 2f * pad - 34f * unit)
        icon(snapshot.icon, pad + 14f * unit, height - 18f * unit, 26f * unit)
        text(snapshot.temperature, pad + 32f * unit, height - 6f * unit, 25f, true,
            maxWidth = width - 2f * pad - 32f * unit)
        return bitmap
    }
    text(snapshot.locationName, pad, layout.city * unit, if (medium && height < 165f * unit) 15f else 17f,
        true, maxWidth = if (medium) width * (if (height < 165f * unit) 0.4f else 0.55f)
            else width - 2 * pad - 34f * unit)
    text(snapshot.temperature, pad, layout.temperature * unit, layout.temperatureSize)
    val compactMedium = medium && height < 165f * unit
    val conditionX = if (compactMedium) width - pad - 55f * unit
        else if (medium) width - pad - 25f * unit else pad
    if (medium) icon(snapshot.icon, width - pad - 38f * unit, pad + 12f * unit,
        (if (height < 165f * unit) 19f else 25f) * unit)
    else icon(snapshot.icon, width - pad - 14f * unit, (layout.temperature - 13f) * unit, 27f * unit)
    text(snapshot.condition, conditionX, layout.condition * unit,
        if (medium && height < 165f * unit) 11f else if (medium) 14f else if (height < 145f * unit) 11f else 12f,
        true, if (medium) Paint.Align.RIGHT else Paint.Align.LEFT,
        if (compactMedium) width * 0.35f else if (medium) width * 0.43f else width - 2 * pad)
    snapshot.temperatureRange?.let { range ->
        layout.range?.let { rangeY ->
            text(range, if (medium) conditionX else pad, rangeY * unit,
                if (medium && height < 165f * unit) 9f else 12f,
                align = if (medium) Paint.Align.RIGHT else Paint.Align.LEFT)
        }
    }
    if (!medium) {
        return bitmap
    }
    val column = (width - pad * 2) / 6f
    snapshot.hours.take(6).forEachIndexed { index, hour ->
        val x = pad + column * (index + 0.5f)
        text(hour.time.format(java.time.format.DateTimeFormatter.ofPattern("H:mm")), x, layout.hourTime!! * unit,
            if (height < 165f * unit) 9f else 11f, align = Paint.Align.CENTER)
        icon(WeatherService.hourCondition(hour).icon, x, layout.hourIcon!! * unit,
            layout.hourIconSize * unit)
        text("${WeatherService.round(hour.temperature)}°", x, layout.hourTemperature!! * unit,
            layout.hourTemperatureSize,
            align = Paint.Align.CENTER)
    }
    return bitmap
}
