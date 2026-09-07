package de.wean.wetterkurve.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.wean.wetterkurve.Language
import de.wean.wetterkurve.MainActivity
import de.wean.wetterkurve.R
import de.wean.wetterkurve.WeatherService
import de.wean.wetterkurve.languageTag
import de.wean.wetterkurve.data.ForecastRepository
import de.wean.wetterkurve.iconDrawable
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class WidgetSnapshot(
    val locationName: String,
    val temperature: String,
    val condition: String,
    val icon: String,
    val status: String,
    val chart: Bitmap?,
    val showClouds: Boolean = true,
    val showWind: Boolean = true,
)

private val TickKey = longPreferencesKey("tick")
private val CityKey = stringPreferencesKey("city")
private val TempKey = stringPreferencesKey("temp")
private val ConditionKey = stringPreferencesKey("condition")
private val IconKey = stringPreferencesKey("icon")
private val StatusKey = stringPreferencesKey("status")
private val ChartPathKey = stringPreferencesKey("chartPath")

private fun snapshotFromPrefs(prefs: Preferences): WidgetSnapshot {
    val path = prefs[ChartPathKey]
    val chart = path?.let { BitmapFactory.decodeFile(it) }
    return WidgetSnapshot(
        locationName = prefs[CityKey].orEmpty().ifBlank { "–" },
        temperature = prefs[TempKey] ?: "–°",
        condition = prefs[ConditionKey].orEmpty(),
        icon = prefs[IconKey] ?: "unknown",
        status = prefs[StatusKey].orEmpty(),
        chart = chart,
    )
}

private fun writeChartFile(context: Context, appWidgetId: Int, tick: Long, bitmap: Bitmap?): String? {
    if (bitmap == null) return null
    val file = File(context.cacheDir, "glance-chart-$appWidgetId-$tick.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    return file.absolutePath
}

object WetterkurveWidgets {
    suspend fun updateAll(context: Context) {
        bumpAndUpdate(context, CompactWidget(), CompactWidgetReceiver::class.java)
        bumpAndUpdate(context, ChartWidget(), ChartWidgetReceiver::class.java)
        pingHosts(context)
    }

    private suspend fun bumpAndUpdate(context: Context, widget: GlanceAppWidget, receiver: Class<out GlanceAppWidgetReceiver>) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val glanceManager = GlanceAppWidgetManager(context)
        val tick = System.currentTimeMillis()
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
        val density = context.resources.displayMetrics.density
        ids.forEach { appWidgetId ->
            val glanceId = runCatching { glanceManager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: return@forEach
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) * density)
                .roundToInt().coerceAtLeast(200)
            val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) * density)
                .roundToInt().coerceAtLeast(200)
            val snapshot = if (widget is ChartWidget) {
                snapshot(context, width, height)
            } else {
                snapshot(context)
            }
            val chartPath = writeChartFile(context, appWidgetId, tick, snapshot.chart)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TickKey] = tick
                    this[CityKey] = snapshot.locationName
                    this[TempKey] = snapshot.temperature
                    this[ConditionKey] = snapshot.condition
                    this[IconKey] = snapshot.icon
                    this[StatusKey] = snapshot.status
                    if (chartPath != null) this[ChartPathKey] = chartPath
                }
            }
            widget.update(context, glanceId)
        }
    }

    fun pingHosts(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(CompactWidgetReceiver::class.java, ChartWidgetReceiver::class.java).forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return@forEach
            val intent = Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    suspend fun snapshot(
        context: Context,
        faceWidth: Int? = null,
        faceHeight: Int? = null,
    ): WidgetSnapshot {
        val language = Language.forLocale(languageTag(context))
        val locale = Language.localeTag(language)
        val repo = ForecastRepository(context)
        val state = repo.loadState()
        val location = state.locations[state.activeLocation.coerceIn(0, state.locations.lastIndex)]
        val cached = try {
            repo.cachedForecast(location.id) ?: repo.refresh(location, force = true)
        } catch (_: Exception) {
            repo.cachedForecast(location.id)
        }
        val current = cached?.payload?.current
        if (cached == null || current == null) {
            return WidgetSnapshot(
                locationName = location.name,
                temperature = "–°",
                condition = Language.text(language, "loadingWeather"),
                icon = "unknown",
                status = Language.text(language, "loading"),
                chart = null,
                showClouds = state.showClouds,
                showWind = state.showWind,
            )
        }
        val condition = WeatherService.weatherInfo(current.weatherCode, language)
        val temperature = "${WeatherService.round(current.temperature)}°"
        val updated = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(cached.fetchedAtEpochSeconds),
            ZoneId.systemDefault(),
        ).format(DateTimeFormatter.ofPattern("HH:mm"))
        val snapshot = WidgetSnapshot(
            locationName = location.name,
            temperature = temperature,
            condition = condition.description,
            icon = condition.icon,
            status = Language.text(language, "updated", mapOf("time" to updated)),
            chart = null,
            showClouds = state.showClouds,
            showWind = state.showWind,
        )
        if (faceWidth == null || faceHeight == null) return snapshot
        val forecast = WeatherService.chartForecast(cached.payload)
        if (forecast.size < 2) return snapshot
        return snapshot.copy(
            chart = renderChartFace(context, snapshot, forecast, locale, faceWidth, faceHeight),
        )
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repo = ForecastRepository(context)
        val state = repo.loadState()
        val location = state.locations[state.activeLocation.coerceIn(0, state.locations.lastIndex)]
        runCatching { repo.refresh(location, force = true) }
        WetterkurveWidgets.updateAll(context)
    }
}

class CompactWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            CompactContent(snapshotFromPrefs(currentState()))
        }
    }
}

class ChartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ChartContent(snapshotFromPrefs(currentState()))
        }
    }
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CompactWidget()
}

class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ChartWidget()
}

private val OnDark = androidx.glance.unit.ColorProvider(Color.White)

@Composable
private fun CompactContent(snapshot: WidgetSnapshot) {
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF202A3A))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().clickable(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(iconDrawable(snapshot.icon)),
                contentDescription = snapshot.condition,
                modifier = GlanceModifier.size(36.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                snapshot.temperature,
                style = TextStyle(color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.width(12.dp))
            Text(
                snapshot.locationName,
                style = TextStyle(color = OnDark, fontSize = 20.sp),
                maxLines = 1,
            )
        }
        Text(
            "↻",
            style = TextStyle(color = OnDark, fontSize = 26.sp),
            modifier = GlanceModifier
                .clickable(actionRunCallback<RefreshAction>())
                .padding(8.dp),
        )
    }
}

@Composable
private fun ChartContent(snapshot: WidgetSnapshot) {
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF202A3A)),
    ) {
        val chart = snapshot.chart
        if (chart != null) {
            Image(
                provider = ImageProvider(chart),
                contentDescription = LocalContext.current.getString(R.string.chart_widget_description),
                modifier = GlanceModifier.fillMaxSize().clickable(openApp),
                contentScale = ContentScale.FillBounds,
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                "↻",
                style = TextStyle(color = OnDark, fontSize = 26.sp),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RefreshAction>())
                    .padding(12.dp),
            )
        }
    }
}
