package de.wean.wetterkurve

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.wean.wetterkurve.data.ForecastRepository
import de.wean.wetterkurve.widget.WetterkurveWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UiState(
    val language: String = Language.forLocale(),
    val locale: String = Language.localeTag(Language.forLocale()),
    val locations: List<WeatherLocation> = listOf(WeatherService.defaultLocation),
    val activeLocation: Int = 0,
    val title: String = WeatherService.defaultLocation.name,
    val condition: String = Language.text(Language.forLocale(), "loadingWeather"),
    val icon: String = "unknown",
    val temperature: String = "–°",
    val status: String = Language.text(Language.forLocale(), "loading"),
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchHint: String = Language.text(Language.forLocale(), "enterTwoLetters"),
    val searchResults: List<WeatherLocation> = emptyList(),
    val showClouds: Boolean = true,
    val showWind: Boolean = true,
)

class WetterkurveViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ForecastRepository(application)
    private val language = Language.forLocale(languageTag(application))
    private var searchJob: Job? = null
    private var forecastJob: Job? = null
    private var lastFetchEpoch = 0L
    private var payload: ForecastPayload? = null
    private var state = AppState(listOf(WeatherService.defaultLocation), 0)

    private val _ui = MutableStateFlow(UiState(language = language))
    val ui: StateFlow<UiState> = _ui

    init {
        viewModelScope.launch {
            state = repo.loadState()
            applyLocationLabels()
            refresh(force = true)
            while (true) {
                delay(WeatherService.UPDATE_SECONDS * 1000L)
                refresh(force = false)
            }
        }
    }

    fun t(key: String, values: Map<String, String> = emptyMap()) = Language.text(language, key, values)

    fun selectLocation(index: Int) {
        if (index == state.activeLocation) return
        state = state.copy(activeLocation = index)
        persistAndReload()
    }

    fun showSearch() {
        _ui.update {
            it.copy(
                searchVisible = true,
                searchQuery = "",
                searchResults = emptyList(),
                searchHint = t("enterTwoLetters"),
            )
        }
    }

    fun hideSearch() {
        searchJob?.cancel()
        _ui.update {
            it.copy(searchVisible = false, searchQuery = "", searchResults = emptyList())
        }
    }

    fun onSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(WeatherService.SEARCH_DELAY_MS)
            search()
        }
    }

    fun addLocation(location: WeatherLocation) {
        if (state.locations.size >= WeatherService.MAX_LOCATIONS) return
        if (state.locations.any { it.id == location.id }) return
        state = state.copy(locations = state.locations + location, activeLocation = state.locations.size)
        hideSearch()
        persistAndReload()
    }

    fun removeActiveLocation() {
        if (state.locations.size < 2) return
        val remaining = state.locations.toMutableList().also { it.removeAt(state.activeLocation) }
        state = state.copy(
            locations = remaining,
            activeLocation = state.activeLocation.coerceAtMost(remaining.lastIndex),
        )
        persistAndReload()
    }

    fun toggleClouds() {
        state = state.copy(showClouds = !state.showClouds)
        persistLayers()
    }

    fun toggleWind() {
        state = state.copy(showWind = !state.showWind)
        persistLayers()
    }

    fun refresh(force: Boolean = true) {
        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            val now = Instant.now().epochSecond
            if (!force && lastFetchEpoch != 0L && now - lastFetchEpoch < 60) return@launch
            val location = active()
            _ui.update { it.copy(status = t("refreshing")) }
            try {
                val cached = repo.refresh(location, force)
                lastFetchEpoch = cached.fetchedAtEpochSeconds
                payload = cached.payload
                render(cached.payload)
                WetterkurveWidgets.updateAll(getApplication())
            } catch (_: Exception) {
                _ui.update {
                    it.copy(
                        status = if (payload == null) t("weatherUnavailable") else t("offlineCached"),
                        condition = if (payload == null) t("offline") else it.condition,
                        icon = if (payload == null) "unknown" else it.icon,
                    )
                }
                WetterkurveWidgets.updateAll(getApplication())
            }
        }
    }

    private fun persistLayers() {
        viewModelScope.launch {
            repo.saveState(state)
            _ui.update { it.copy(showClouds = state.showClouds, showWind = state.showWind) }
            WetterkurveWidgets.updateAll(getApplication())
        }
    }

    private fun persistAndReload() {
        viewModelScope.launch {
            repo.saveState(state)
            WetterkurveWidgets.updateAll(getApplication())
            payload = null
            lastFetchEpoch = 0
            applyLocationLabels()
            refresh(force = true)
        }
    }

    private fun applyLocationLabels() {
        val location = active()
        _ui.update {
            it.copy(
                locations = state.locations,
                activeLocation = state.activeLocation,
                title = location.name,
                condition = t("loadingWeather"),
                icon = "unknown",
                temperature = "–°",
                status = t("loading"),
                showClouds = state.showClouds,
                showWind = state.showWind,
            )
        }
    }

    private fun render(payload: ForecastPayload) {
        val condition = WeatherService.weatherInfo(payload.current?.weatherCode ?: 0, language)
        val updated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        _ui.update {
            it.copy(
                title = active().name,
                condition = condition.description,
                icon = condition.icon,
                temperature = "${WeatherService.round(payload.current?.temperature ?: 0.0)}°",
                status = t("updated", mapOf("time" to updated)),
                locations = state.locations,
                activeLocation = state.activeLocation,
            )
        }
    }

    private suspend fun search() {
        val query = _ui.value.searchQuery.trim()
        if (!_ui.value.searchVisible) return
        if (query.length < 2) {
            _ui.update { it.copy(searchResults = emptyList(), searchHint = t("enterTwoLetters")) }
            return
        }
        _ui.update { it.copy(searchHint = t("searching")) }
        try {
            val results = repo.search(query, language)
            _ui.update {
                it.copy(
                    searchResults = results,
                    searchHint = if (results.isEmpty()) t("noLocationsFound") else t("chooseLocation"),
                )
            }
        } catch (_: Exception) {
            _ui.update { it.copy(searchResults = emptyList(), searchHint = t("locationSearchUnavailable")) }
        }
    }

    private fun active(): WeatherLocation {
        val index = state.activeLocation.coerceIn(0, state.locations.lastIndex)
        return state.locations[index]
    }
}

internal fun languageTag(context: Context): String {
    val locales = context.resources.configuration.locales
    return if (locales.size() > 0) locales[0].toLanguageTag() else Locale.getDefault().toLanguageTag()
}

fun iconDrawable(name: String): Int = when (name) {
    "clear" -> R.drawable.clear
    "partly-cloudy" -> R.drawable.partly_cloudy
    "cloudy" -> R.drawable.cloudy
    "fog" -> R.drawable.fog
    "drizzle" -> R.drawable.drizzle
    "rain" -> R.drawable.rain
    "snow" -> R.drawable.snow
    "thunderstorm" -> R.drawable.thunderstorm
    else -> R.drawable.unknown
}
