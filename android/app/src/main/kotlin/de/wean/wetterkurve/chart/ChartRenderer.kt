package de.wean.wetterkurve.chart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.wean.wetterkurve.HourlyPoint
import de.wean.wetterkurve.WeatherService
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object ChartRenderer {
    enum class LegendSample { Temperature, FeelsLike, Chance, Amount, Clouds, Wind }
    private val temperatureColors = listOf(
        Stop(-15.0, 0.36f, 0.55f, 1.00f),
        Stop(-5.0, 0.20f, 0.78f, 1.00f),
        Stop(5.0, 0.20f, 0.88f, 0.76f),
        Stop(15.0, 0.55f, 0.86f, 0.25f),
        Stop(22.0, 1.00f, 0.84f, 0.16f),
        Stop(28.0, 1.00f, 0.48f, 0.12f),
        Stop(35.0, 1.00f, 0.20f, 0.16f),
    )

    private val dayStripColors = listOf(
        floatArrayOf(0.16f, 0.34f, 0.56f, 0.82f),
        floatArrayOf(0.10f, 0.22f, 0.39f, 0.88f),
    )

    private val dayPlotRgb = intArrayOf(26, 32, 44)
    private val nightPlotRgb = intArrayOf(29, 50, 92)
    private val cloudGrayRgb = intArrayOf(200, 200, 200)

    fun renderPng(
        forecast: List<HourlyPoint>,
        locale: String,
        width: Int,
        height: Int,
        now: LocalDateTime = LocalDateTime.now(),
        showClouds: Boolean = true,
        showWind: Boolean = true,
    ): ByteArray {
        val bitmap = renderBitmap(forecast, locale, width, height, now, showClouds, showWind)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    fun renderBitmap(
        forecast: List<HourlyPoint>,
        locale: String,
        width: Int,
        height: Int,
        now: LocalDateTime = LocalDateTime.now(),
        showClouds: Boolean = true,
        showWind: Boolean = true,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.argb(77, 8, 12, 22))
        paint(canvas, forecast, locale, width, height, now, showClouds, showWind)
        return bitmap
    }

    fun paint(
        canvas: Canvas,
        data: List<HourlyPoint>,
        locale: String,
        width: Int,
        height: Int,
        now: LocalDateTime = LocalDateTime.now(),
        showClouds: Boolean = true,
        showWind: Boolean = true,
    ) {
        if (data.size < 2 || width < 100 || height < 100) return

        val label = (height * 0.10f).coerceIn(28f, 44f)
        val stripBottom = label * 1.5f
        val cloudTop = stripBottom + 4f
        val cloudBottom = if (showClouds) cloudTop + (label * 0.72f).coerceIn(18f, 36f) else stripBottom
        val plot = RectF(label * 1.85f, cloudBottom + 6f, width - label * 1.85f, height - label * 1.85f)
        val plotWidth = plot.width()
        val plotHeight = plot.height()
        val temperatures = data.flatMap { listOf(it.temperature, it.apparent) }
        var minTemp = floor((temperatures.minOrNull() ?: 0.0) / 5.0) * 5.0
        var maxTemp = ceil((temperatures.maxOrNull() ?: 0.0) / 5.0) * 5.0
        if (minTemp == maxTemp) {
            minTemp -= 5
            maxTemp += 5
        }

        fun x(index: Int): Float = plot.left + index * plotWidth / (data.size - 1)
        fun y(value: Double): Float =
            plot.bottom - ((value - minTemp) * plotHeight / (maxTemp - minTemp)).toFloat()

        canvas.drawRect(plot, paint(Color.argb(82, 10, 14, 23), fill = true))
        drawDayStrip(canvas, data, plot, locale, label, stripBottom)
        if (showClouds) {
            drawCloudStrip(canvas, data, ::x, cloudTop, cloudBottom)
        }

        for (i in 0 until data.size - 1) {
            val hour = data[i].time.hour
            if (hour >= 21 || hour < 6) {
                canvas.drawRect(x(i), plot.top, x(i + 1) + 1, plot.bottom, paint(Color.argb(107, 33, 74, 158)))
            }
        }

        for (i in 1 until data.size - 1) {
            val previousNight = data[i - 1].time.hour.let { it >= 21 || it < 6 }
            val isNight = data[i].time.hour.let { it >= 21 || it < 6 }
            if (previousNight == isNight) continue
            val twilight = paint(Color.argb(209, 158, 204, 255), stroke = label * 0.07f)
            canvas.drawLine(x(i), plot.top, x(i), plot.bottom, twilight)
        }

        val labelPaint = paint(Color.WHITE).apply {
            textSize = label
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }
        val gridPaint = paint(Color.argb(48, 255, 255, 255), stroke = label * 0.05f)
        var value = minTemp
        while (value <= maxTemp) {
            val gridY = y(value)
            canvas.drawLine(plot.left, gridY, plot.right, gridY, gridPaint)
            canvas.drawText("${value.toInt()}°", 6f, gridY + label * 0.35f, labelPaint)
            value += 5
        }

        val maxRain = max(1.0, data.maxOf { it.precipitation })
        val barWidth = max(label * 0.12f, plotWidth / data.size - 2f)
        val probabilityPaint = paint(Color.argb(69, 77, 171, 255))
        val rainPaint = paint(Color.argb(240, 64, 189, 255))
        data.forEachIndexed { i, point ->
            val probabilityHeight = plotHeight * 0.36f * point.precipitationProbability.toFloat() / 100f
            if (probabilityHeight > 0) {
                canvas.drawRect(
                    x(i) - barWidth / 2,
                    plot.bottom - probabilityHeight,
                    x(i) + barWidth / 2,
                    plot.bottom,
                    probabilityPaint,
                )
            }
            if (point.precipitation > 0) {
                val rainHeight = plotHeight * 0.36f * (point.precipitation / maxRain).toFloat()
                canvas.drawRect(
                    x(i) - barWidth / 2,
                    plot.bottom - rainHeight,
                    x(i) + barWidth / 2,
                    plot.bottom,
                    rainPaint,
                )
            }
        }

        val gradientStart = y(temperatureColors.first().temperature)
        val gradientEnd = y(temperatureColors.last().temperature)
        val range = temperatureColors.last().temperature - temperatureColors.first().temperature
        val colors = temperatureColors.map {
            Color.rgb((it.r * 255).toInt(), (it.g * 255).toInt(), (it.b * 255).toInt())
        }.toIntArray()
        val positions = temperatureColors.map {
            ((it.temperature - temperatureColors.first().temperature) / range).toFloat()
        }.toFloatArray()
        val shader = LinearGradient(0f, gradientStart, 0f, gradientEnd, colors, positions, Shader.TileMode.CLAMP)

        strokeSmoothLine(canvas, data.map { it.apparent }, ::x, ::y, Color.argb(173, 5, 10, 20), label * 0.22f, dashed = true, dash = label * 0.18f)
        strokeSmoothLine(canvas, data.map { it.apparent }, ::x, ::y, Color.argb(235, 184, 224, 255), label * 0.11f, dashed = true, dash = label * 0.18f)
        strokeSmoothLine(canvas, data.map { it.temperature }, ::x, ::y, Color.argb(209, 5, 10, 20), label * 0.28f, dashed = false)
        val temperaturePaint = paint(Color.WHITE, stroke = label * 0.16f).apply {
            this.shader = shader
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(buildSmoothPath(data.map { it.temperature }, ::x, ::y), temperaturePaint)

        if (showWind) {
            val maxWind = max(20.0, ceil((data.maxOf { it.wind } / 5.0)) * 5.0)
            fun yWind(value: Double): Float =
                plot.bottom - (value / maxWind * plotHeight).toFloat()
            val windColor = Color.parseColor("#00E272")
            strokeSmoothLine(canvas, data.map { it.wind }, ::x, ::yWind, Color.argb(180, 0, 20, 12), label * 0.18f, dashed = false)
            strokeSmoothLine(canvas, data.map { it.wind }, ::x, ::yWind, windColor, label * 0.11f, dashed = false)
            val windLabelPaint = paint(windColor).apply {
                textSize = label * 0.75f
                textAlign = Paint.Align.LEFT
            }
            var windTick = 0.0
            while (windTick <= maxWind) {
                val gridY = yWind(windTick)
                canvas.drawText("${windTick.toInt()}", plot.right + 6f, gridY + label * 0.28f, windLabelPaint)
                windTick += 5
            }
        }

        val hourPaint = paint(Color.WHITE).apply {
            textSize = label * 0.8f
            isAntiAlias = true
        }
        val tickPaint = paint(Color.argb(120, 220, 235, 255), stroke = 1.25f)
        val hourMarks = data.mapIndexedNotNull { i, point ->
            val hour = point.time.hour
            if (hour % 6 == 0) i to hour else null
        }
        hourMarks.forEachIndexed { markIndex, (i, hour) ->
            val staggered = markIndex % 2 == 1
            val textY = if (staggered) height - label * 1.05f else height - label * 0.32f
            val text = hour.toString()
            val textWidth = hourPaint.measureText(text)
            val markX = x(i)
            canvas.drawLine(markX, plot.bottom, markX, textY - hourPaint.textSize * 0.15f, tickPaint)
            canvas.drawText(text, markX - textWidth / 2, textY, hourPaint)
        }

        val first = data.first().time
        val last = data.last().time
        if (!now.isBefore(first) && !now.isAfter(last) && first != last) {
            val nowX = plot.left + java.time.Duration.between(first, now).toNanos().toFloat() /
                java.time.Duration.between(first, last).toNanos().toFloat() * plotWidth
            canvas.drawLine(nowX, plot.top, nowX, plot.bottom, paint(Color.argb(61, 89, 255, 31), stroke = label * 0.35f).apply {
                strokeCap = Paint.Cap.ROUND
            })
            canvas.drawLine(nowX, plot.top, nowX, plot.bottom, paint(Color.argb(255, 115, 255, 31), stroke = label * 0.12f).apply {
                strokeCap = Paint.Cap.ROUND
            })
        }
    }

    fun paintLegendSample(canvas: Canvas, sample: LegendSample, width: Float, height: Float) {
        if (width < 8f || height < 8f) return
        val midY = height / 2f
        val stroke = (height * 0.28f).coerceIn(3f, 6.5f)
        val inset = 3f
        when (sample) {
            LegendSample.Temperature -> {
                val colors = temperatureColors.map {
                    Color.rgb((it.r * 255).toInt(), (it.g * 255).toInt(), (it.b * 255).toInt())
                }.toIntArray()
                val shader = LinearGradient(inset, 0f, width - inset, 0f, colors, null, Shader.TileMode.CLAMP)
                val p = paint(Color.WHITE, stroke = stroke).apply {
                    this.shader = shader
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(inset, midY + 2f, width - inset, midY - 2f, p)
            }
            LegendSample.FeelsLike -> {
                val dash = (height * 0.35f).coerceIn(4f, 8f)
                val outline = paint(Color.argb(173, 5, 10, 20), stroke = stroke * 1.6f).apply {
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
                }
                val line = paint(Color.argb(235, 184, 224, 255), stroke = stroke).apply {
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
                }
                canvas.drawLine(inset, midY, width - inset, midY, outline)
                canvas.drawLine(inset, midY, width - inset, midY, line)
            }
            LegendSample.Chance -> {
                val barW = width * 0.34f
                val left = (width - barW) / 2f
                canvas.drawRect(left, height * 0.28f, left + barW, height - 2f, paint(Color.argb(69, 77, 171, 255)))
            }
            LegendSample.Amount -> {
                val barW = width * 0.34f
                val left = (width - barW) / 2f
                canvas.drawRect(left, height * 0.18f, left + barW, height - 2f, paint(Color.argb(240, 64, 189, 255)))
            }
            LegendSample.Clouds -> {
                val cells = 4
                val cellW = (width - 4f) / cells
                val top = height * 0.22f
                val bottom = height * 0.78f
                for (i in 0 until cells) {
                    val cover = i / (cells - 1f)
                    val r = (dayPlotRgb[0] + (cloudGrayRgb[0] - dayPlotRgb[0]) * cover).toInt()
                    val g = (dayPlotRgb[1] + (cloudGrayRgb[1] - dayPlotRgb[1]) * cover).toInt()
                    val b = (dayPlotRgb[2] + (cloudGrayRgb[2] - dayPlotRgb[2]) * cover).toInt()
                    canvas.drawRect(2f + i * cellW, top, 2f + (i + 1) * cellW, bottom, paint(Color.rgb(r, g, b)))
                }
            }
            LegendSample.Wind -> {
                val p = paint(Color.parseColor("#00E272"), stroke = stroke).apply {
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(inset, midY + 3f, width - inset, midY - 3f, p)
            }
        }
    }

    private fun drawDayStrip(
        canvas: Canvas,
        data: List<HourlyPoint>,
        plot: RectF,
        locale: String,
        label: Float,
        stripBottom: Float,
    ) {
        val stripTop = 6f
        val stripHeight = stripBottom - stripTop
        val days = WeatherService.chartDaySegments(data, locale)
        val textPaint = paint(Color.argb(245, 255, 255, 255)).apply {
            textSize = label * 0.95f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val border = paint(Color.argb(82, 163, 207, 255), stroke = label * 0.05f)
        val divider = paint(Color.argb(77, 163, 207, 255), stroke = label * 0.05f)

        days.forEachIndexed { index, day ->
            val left = plot.left + day.start / data.size.toFloat() * plot.width()
            val right = plot.left + day.end / data.size.toFloat() * plot.width()
            val color = dayStripColors[index % dayStripColors.size]
            val fill = paint(
                Color.argb(
                    (color[3] * 255).toInt(),
                    (color[0] * 255).toInt(),
                    (color[1] * 255).toInt(),
                    (color[2] * 255).toInt(),
                ),
            )
            val rect = RectF(left, stripTop, right, stripBottom)
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, border)
            val textWidth = textPaint.measureText(day.label)
            val textX = left + (right - left - textWidth) / 2
            val textY = stripTop + (stripHeight - (textPaint.descent() - textPaint.ascent())) / 2 - textPaint.ascent()
            canvas.drawText(day.label, textX, textY, textPaint)
            if (index > 0) {
                canvas.drawLine(left, stripBottom, left, plot.bottom, divider)
            }
        }
    }

    private fun drawCloudStrip(
        canvas: Canvas,
        data: List<HourlyPoint>,
        x: (Int) -> Float,
        cloudTop: Float,
        cloudBottom: Float,
    ) {
        val border = paint(Color.argb(80, 220, 220, 220), stroke = 1f)
        for (i in 0 until data.size - 1) {
            val hour = data[i].time.hour
            val night = hour >= 21 || hour < 6
            val cover = (data[i].cloudCover.coerceIn(0.0, 100.0) / 100.0).toFloat()
            val base = if (night) nightPlotRgb else dayPlotRgb
            val r = (base[0] + (cloudGrayRgb[0] - base[0]) * cover).toInt()
            val g = (base[1] + (cloudGrayRgb[1] - base[1]) * cover).toInt()
            val b = (base[2] + (cloudGrayRgb[2] - base[2]) * cover).toInt()
            canvas.drawRect(x(i), cloudTop, x(i + 1) + 1, cloudBottom, paint(Color.rgb(r, g, b)))
        }
        canvas.drawRect(x(0), cloudTop, x(data.lastIndex), cloudBottom, border)
    }

    private fun strokeSmoothLine(
        canvas: Canvas,
        values: List<Double>,
        x: (Int) -> Float,
        y: (Double) -> Float,
        color: Int,
        width: Float,
        dashed: Boolean,
        dash: Float = 1f,
    ) {
        val paint = paint(color, stroke = width).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        }
        canvas.drawPath(buildSmoothPath(values, x, y), paint)
    }

    private fun buildSmoothPath(
        values: List<Double>,
        x: (Int) -> Float,
        y: (Double) -> Float,
    ): Path {
        val path = Path()
        path.moveTo(x(0), y(values[0]))
        for (i in 0 until values.size - 1) {
            val currentX = x(i)
            val nextX = x(i + 1)
            val controlOffset = (nextX - currentX) * 0.45f
            path.cubicTo(
                currentX + controlOffset,
                y(values[i]),
                nextX - controlOffset,
                y(values[i + 1]),
                nextX,
                y(values[i + 1]),
            )
        }
        return path
    }

    private fun paint(color: Int, fill: Boolean = true, stroke: Float? = null): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (stroke != null) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
            } else {
                style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
            }
        }
    }

    private data class Stop(val temperature: Double, val r: Float, val g: Float, val b: Float)
}
