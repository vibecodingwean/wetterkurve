package de.wean.wetterkurve.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.wean.wetterkurve.Language
import de.wean.wetterkurve.HourlyPoint
import de.wean.wetterkurve.MainActivity
import de.wean.wetterkurve.R
import de.wean.wetterkurve.WeatherService
import de.wean.wetterkurve.data.ForecastRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

data class WidgetSnapshot(
    val locationName: String,
    val temperature: String,
    val condition: String,
    val icon: String,
    val status: String,
    val chart: Bitmap?,
    val hours: List<HourlyPoint> = emptyList(),
    val temperatureRange: String? = null,
    val showClouds: Boolean = true,
    val showWind: Boolean = false,
    val showFeelsLike: Boolean = false,
    val isDay: Boolean = true,
    val weatherCode: Int? = null,
    val windSpeed: Double = 0.0,
    val rain: Double? = null,
    val snowfall: Double? = null,
    val backgroundSunEvent: String? = null,
    val showWidgetWeatherImages: Boolean = false,
)

private val TickKey = longPreferencesKey("tick")

object WetterkurveWidgets {
    suspend fun updateAll(context: Context) {
        bumpAndUpdate(context, CompactWidget(), CompactWidgetReceiver::class.java)
        bumpAndUpdate(context, ForecastWidget(), ForecastWidgetReceiver::class.java)
        bumpAndUpdate(context, ChartWidget(), ChartWidgetReceiver::class.java)
        pingHosts(context)
    }

    private suspend fun bumpAndUpdate(context: Context, widget: GlanceAppWidget, receiver: Class<out GlanceAppWidgetReceiver>) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val glanceManager = GlanceAppWidgetManager(context)
        val tick = System.currentTimeMillis()
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
        ids.forEach { appWidgetId ->
            val glanceId = runCatching { glanceManager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: return@forEach
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[TickKey] = tick }
            }
            widget.update(context, glanceId)
        }
    }

    fun pingHosts(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(CompactWidgetReceiver::class.java, ForecastWidgetReceiver::class.java, ChartWidgetReceiver::class.java).forEach { cls ->
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
        val repo = ForecastRepository(context)
        val state = repo.loadState()
        val language = state.language
        val locale = Language.localeTag(language)
        val location = state.locations[state.activeLocation.coerceIn(0, state.locations.lastIndex)]
        val cached = try {
            val existing = repo.cachedForecast(location.id)
            if (existing == null || !WeatherService.hasWidgetSkyData(existing.payload)) {
                repo.refresh(location, force = true)
            } else existing
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
                showFeelsLike = state.showFeelsLike,
                showWidgetWeatherImages = state.showWidgetWeatherImages,
            )
        }
        val locationNow = LocalDateTime.now(ZoneId.of(location.timezone))
        val condition = WeatherService.currentCondition(cached.payload, locationNow, language)
        val temperature = "${WeatherService.round(current.temperature)}°"
        val hours = WeatherService.nextHours(cached.payload, locationNow)
        val today = cached.payload.hourly?.let { hourly ->
            hourly.time.indices.mapNotNull { index ->
                if (hourly.time[index].startsWith(locationNow.toLocalDate().toString())) hourly.temperature.getOrNull(index) else null
            }
        }.orEmpty()
        val updated = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(cached.fetchedAtEpochSeconds),
            ZoneId.systemDefault(),
        ).format(DateTimeFormatter.ofPattern("HH:mm"))
        val snapshot = WidgetSnapshot(
            locationName = location.name,
            temperature = temperature,
            condition = condition.description,
            icon = condition.icon,
            isDay = WeatherService.currentIsDay(cached.payload, locationNow),
            weatherCode = current.weatherCode,
            windSpeed = current.windSpeed,
            rain = current.rain,
            snowfall = current.snowfall,
            backgroundSunEvent = currentBackgroundSunEvent(cached.payload.daily, locationNow),
            status = Language.text(language, "updated", mapOf("time" to updated)),
            chart = null,
            hours = hours,
            temperatureRange = if (today.isEmpty()) null else "H:${WeatherService.round(today.max())}° ${if (language == "de") "T" else "L"}:${WeatherService.round(today.min())}°",
            showClouds = state.showClouds,
            showWind = state.showWind,
            showFeelsLike = state.showFeelsLike,
            showWidgetWeatherImages = state.showWidgetWeatherImages,
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
            val tick = currentState<Preferences>()[TickKey]
            val size = LocalSize.current
            val density = context.resources.displayMetrics.density
            val snapshot = remember(tick, size.width, size.height) {
                runBlocking { WetterkurveWidgets.snapshot(context) }
            }
            val face = remember(snapshot, size.width, size.height) {
                renderWeatherCardFace(context, snapshot, (size.width.value * density).roundToInt().coerceAtLeast(1),
                    (size.height.value * density).roundToInt().coerceAtLeast(1), false)
            }
            WeatherCardContent(face, snapshot)
        }
    }
}

class ForecastWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val tick = currentState<Preferences>()[TickKey]
            val size = LocalSize.current
            val density = context.resources.displayMetrics.density
            val snapshot = remember(tick, size.width, size.height) {
                runBlocking { WetterkurveWidgets.snapshot(context) }
            }
            val face = remember(snapshot, size.width, size.height) {
                renderWeatherCardFace(context, snapshot, (size.width.value * density).roundToInt().coerceAtLeast(1),
                    (size.height.value * density).roundToInt().coerceAtLeast(1), true)
            }
            WeatherCardContent(face, snapshot)
        }
    }
}

class ChartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        provideContent {
            val tick = currentState<Preferences>()[TickKey]
            val size = LocalSize.current
            val snapshot = remember(tick, size.width, size.height) {
                runBlocking {
                    WetterkurveWidgets.snapshot(
                        context,
                        (size.width.value * density).roundToInt().coerceAtLeast(1),
                        (size.height.value * density).roundToInt().coerceAtLeast(1),
                    )
                }
            }
            ChartContent(snapshot)
        }
    }
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CompactWidget()
}

class ForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ForecastWidget()
}

@Composable
private fun WeatherCardContent(face: Bitmap, snapshot: WidgetSnapshot) {
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    Box(GlanceModifier.fillMaxSize()) {
        Image(ImageProvider(face), snapshot.locationName + ", " + snapshot.temperature + ", " + snapshot.condition,
            GlanceModifier.fillMaxSize().clickable(openApp), contentScale = ContentScale.FillBounds)
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            Text("↻", style = TextStyle(color = OnDark, fontSize = 22.sp),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()).padding(8.dp))
        }
    }
}

class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ChartWidget()
}

private val OnDark = androidx.glance.unit.ColorProvider(Color.White)

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
                contentScale = ContentScale.Fit,
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
