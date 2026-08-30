package de.wean.wetterkurve

import kotlinx.serialization.json.Json
import kotlin.math.min

object SettingsStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun defaultState(): AppState = AppState(
        locations = listOf(WeatherService.defaultLocation),
        activeLocation = 0,
    )

    fun parse(value: String?): AppState {
        return try {
            val parsed = json.decodeFromString<AppStateJson>(value ?: "")
            val locationsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(WeatherLocationJson.serializer()),
                parsed.locations,
            )
            val locations = WeatherService.parseLocations(
                locationsJson,
                listOf(WeatherService.defaultLocation),
            )
            AppState(
                locations = locations,
                activeLocation = parsed.activeLocation.coerceIn(0, locations.lastIndex),
                showClouds = parsed.showClouds,
                showWind = parsed.showWind,
            )
        } catch (_: Exception) {
            defaultState()
        }
    }

    fun serialize(state: AppState): String {
        val locations = WeatherService.parseLocations(
            WeatherService.serializeLocations(state.locations),
            listOf(WeatherService.defaultLocation),
        )
        val active = if (locations.isEmpty()) 0 else min(state.activeLocation.coerceAtLeast(0), locations.lastIndex)
        return json.encodeToString(
            AppStateJson.serializer(),
            AppStateJson(
                locations = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(WeatherLocationJson.serializer()),
                    WeatherService.serializeLocations(locations),
                ),
                activeLocation = active,
                showClouds = state.showClouds,
                showWind = state.showWind,
            ),
        )
    }
}
