package de.wean.wetterkurve.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.wean.wetterkurve.AppState
import de.wean.wetterkurve.ForecastPayload
import de.wean.wetterkurve.SettingsStore
import de.wean.wetterkurve.WeatherLocation
import de.wean.wetterkurve.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

private val Context.settingsDataStore by preferencesDataStore("wetterkurve")

class ForecastRepository(private val context: Context) {
    private val http: OkHttpClient = WeatherService.createClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadState(): AppState = withContext(Dispatchers.IO) {
        val file = settingsFile()
        if (file.exists()) {
            return@withContext SettingsStore.parse(file.readText())
        }
        val migrated = runCatching {
            context.settingsDataStore.data.first()[stringPreferencesKey("settings")]
        }.getOrNull()
        val parsed = SettingsStore.parse(migrated)
        writeSettingsFile(parsed)
        parsed
    }

    suspend fun saveState(state: AppState) = withContext(Dispatchers.IO) {
        writeSettingsFile(state)
    }

    private fun writeSettingsFile(state: AppState) {
        val file = settingsFile()
        val tmp = File(file.parentFile, "settings.json.tmp")
        val text = SettingsStore.serialize(state)
        FileOutputStream(tmp).use { stream ->
            stream.write(text.toByteArray())
            stream.flush()
            stream.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    suspend fun search(query: String, language: String): List<WeatherLocation> {
        return WeatherService.searchLocations(http, query, language)
    }

    suspend fun cachedForecast(locationId: String): CachedForecast? = withContext(Dispatchers.IO) {
        val file = cacheFile(locationId)
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<CachedForecast>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refresh(location: WeatherLocation, force: Boolean = false): CachedForecast {
        val existing = cachedForecast(location.id)
        val now = Instant.now().epochSecond
        if (!force && existing != null && now - existing.fetchedAtEpochSeconds < 60) {
            return existing
        }
        val payload = WeatherService.fetchForecast(http, location)
        val cached = CachedForecast(
            locationId = location.id,
            fetchedAtEpochSeconds = now,
            payload = payload,
        )
        withContext(Dispatchers.IO) {
            cacheFile(location.id).writeText(json.encodeToString(CachedForecast.serializer(), cached))
        }
        return cached
    }

    private fun settingsFile() = File(context.filesDir, "settings.json")

    private fun cacheFile(locationId: String): File {
        val dir = File(context.filesDir, "forecasts")
        dir.mkdirs()
        val safe = locationId.replace(Regex("[^0-9A-Za-z.,_-]"), "_")
        return File(dir, "$safe.json")
    }
}

@Serializable
data class CachedForecast(
    val locationId: String,
    val fetchedAtEpochSeconds: Long,
    val payload: ForecastPayload,
)
