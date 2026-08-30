package de.wean.wetterkurve.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import de.wean.wetterkurve.HourlyPoint
import de.wean.wetterkurve.WeatherService
import de.wean.wetterkurve.chart.ChartRenderer
import de.wean.wetterkurve.iconDrawable
import kotlin.math.roundToInt

internal fun renderChartFace(
    context: Context,
    snapshot: WidgetSnapshot,
    forecast: List<HourlyPoint>,
    locale: String,
    width: Int,
    height: Int,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.parseColor("#202A3A"))

    val pad = 16f * density
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f * density
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val conditionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 235, 245, 255)
        textSize = 16f * density
    }
    val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f * density
    }
    val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 235, 245, 255)
        textSize = 13f * density
        textAlign = Paint.Align.RIGHT
    }
    val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 235, 245, 255)
        textSize = 13f * density
    }

    val iconSize = (44f * density).roundToInt()
    val tempWidth = tempPaint.measureText(snapshot.temperature)
    val refreshGap = 44f * density
    val rightBlock = refreshGap + tempWidth + 10f * density + iconSize
    val nameRight = width - pad - rightBlock

    canvas.save()
    canvas.clipRect(pad, 0f, nameRight, 80f * density)
    canvas.drawText(snapshot.locationName, pad, pad + titlePaint.textSize, titlePaint)
    canvas.drawText(
        snapshot.condition,
        pad,
        pad + titlePaint.textSize + 8f * density + conditionPaint.textSize,
        conditionPaint,
    )
    canvas.restore()

    val tempX = width - pad - refreshGap - tempWidth
    val icon = BitmapFactory.decodeResource(context.resources, iconDrawable(snapshot.icon))
    if (icon != null) {
        val scaled = Bitmap.createScaledBitmap(icon, iconSize, iconSize, true)
        canvas.drawBitmap(scaled, tempX - iconSize - 8f * density, pad + 2f * density, null)
        if (scaled != icon) scaled.recycle()
        icon.recycle()
    }
    canvas.drawText(snapshot.temperature, tempX, pad + tempPaint.textSize * 0.95f, tempPaint)
    val (minTemp, maxTemp) = WeatherService.todayTemperatureRange(forecast)
    canvas.drawText(
        "${minTemp}°/${maxTemp}° C",
        width - pad,
        pad + tempPaint.textSize + 4f * density + rangePaint.textSize,
        rangePaint,
    )

    val header = 78f * density
    val footer = 24f * density
    val chartHeight = (height - header - footer).toInt().coerceAtLeast(160)
    canvas.save()
    canvas.translate(0f, header)
    canvas.clipRect(0f, 0f, width.toFloat(), chartHeight.toFloat())
    ChartRenderer.paint(
        canvas,
        forecast,
        locale,
        width,
        chartHeight,
        showClouds = snapshot.showClouds,
        showWind = snapshot.showWind,
    )
    canvas.restore()

    canvas.drawText(snapshot.status, pad, height - 8f * density, statusPaint)
    return bitmap
}
