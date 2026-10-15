package de.wean.wetterkurve.widget

import de.wean.wetterkurve.*
import de.wean.wetterkurve.data.CachedForecast
import de.wean.wetterkurve.data.ForecastRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetImagePersistenceTest {
    @Test fun savedOptionPropagatesIntoRealWidgetSnapshotsWithoutChangingOtherSettings() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val repo=ForecastRepository(context)
        val base=SettingsStore.defaultState().copy(language="de",showWind=true,showFeelsLike=true)
        val location=base.locations.first()
        val now=LocalDateTime.now(ZoneId.of(location.timezone))
        val hours=(1..2).map { now.withMinute(0).withSecond(0).withNano(0).plusHours(it.toLong()).toString() }
        val payload=ForecastPayload(
            current=CurrentWeather(1.0,-1.0,73,14.0,80.0,rain=0.2,snowfall=0.1),
            hourly=HourlyWeather(time=hours,temperature=listOf(1.0,1.0),weatherCode=listOf(73,73),isDay=listOf(1,1)),
            daily=SolarTimes(listOf(""),listOf("")),
        )
        val cached=CachedForecast(location.id,java.time.Instant.now().epochSecond,payload)
        val cacheDir=java.io.File(context.filesDir,"forecasts").apply { mkdirs() }
        val safe=location.id.replace(Regex("[^0-9A-Za-z.,_-]"),"_")
        java.io.File(cacheDir,"$safe.json").writeText(Json.encodeToString(CachedForecast.serializer(),cached))
        for (enabled in listOf(false,true,false)) {
            repo.saveState(base.copy(showWidgetWeatherImages=enabled))
            val reloaded=repo.loadState()
            assertEquals(enabled,reloaded.showWidgetWeatherImages)
            assertTrue(reloaded.showWind);assertTrue(reloaded.showFeelsLike)
            val snapshot=WetterkurveWidgets.snapshot(context)
            assertEquals(enabled,snapshot.showWidgetWeatherImages)
            assertEquals(73,snapshot.weatherCode)
            assertEquals(WeatherBackgroundScene.SLEET,backgroundScene(snapshot))
            assertEquals(base.locations,reloaded.locations)
            assertEquals(base.language,reloaded.language)
        }
    }
}
