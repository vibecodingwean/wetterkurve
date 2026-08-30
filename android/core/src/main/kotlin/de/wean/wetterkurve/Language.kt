package de.wean.wetterkurve

import java.util.Locale

object Language {
    private val strings = mapOf(
        "de" to mapOf(
            "searchLocation" to "Ort suchen …",
            "enterTwoLetters" to "Mindestens zwei Buchstaben eingeben",
            "loadingWeather" to "Wetter wird geladen …",
            "refreshWeather" to "Wetter aktualisieren",
            "feelsLike" to "Gefühlt",
            "temperature" to "Temperatur",
            "rain" to "Regen",
            "rainChance" to "Chance",
            "rainAmount" to "Menge",
            "clouds" to "Wolke",
            "wind" to "Wind",
            "humidity" to "Feuchte",
            "threeDayForecast" to "3 Tage ab heute",
            "chartRainScale" to "Regenbalken nur unten im Chart: volle Balkenhöhe = 100 % Chance bzw. größte Menge der 3 Tage (mindestens 1 mm).",
            "loading" to "Open-Meteo · lädt …",
            "addLocation" to "+ Ort",
            "addLocationAccessible" to "Ort hinzufügen",
            "selectLocation" to "{location} auswählen",
            "removeLocation" to "{location} entfernen",
            "searching" to "Suche …",
            "locationSearchUnavailable" to "Ortsuche derzeit nicht erreichbar",
            "noLocationsFound" to "Keine passenden Orte gefunden",
            "chooseLocation" to "Treffer auswählen",
            "saved" to "{location} · gespeichert",
            "refreshing" to "Open-Meteo · aktualisiert …",
            "updated" to "Open-Meteo · aktualisiert {time} Uhr",
            "offlineCached" to "Keine Verbindung · letzte Prognose bleibt sichtbar",
            "weatherUnavailable" to "Wetterdaten derzeit nicht erreichbar",
            "offline" to "Keine Verbindung",
            "weather" to "Wetter",
            "savedLocations" to "Gespeicherte Orte",
            "remove" to "Entfernen",
            "addWidgetHint" to "Widget: Homescreen lange drücken → Widgets → Wetterkurve",
        ),
        "en" to mapOf(
            "searchLocation" to "Search for a location …",
            "enterTwoLetters" to "Enter at least two letters",
            "loadingWeather" to "Loading weather …",
            "refreshWeather" to "Refresh weather",
            "feelsLike" to "Feels like",
            "temperature" to "Temperature",
            "rain" to "Rain",
            "rainChance" to "Chance",
            "rainAmount" to "Amount",
            "clouds" to "Cloud",
            "wind" to "Wind",
            "humidity" to "Humidity",
            "threeDayForecast" to "3 days from today",
            "chartRainScale" to "Rain bars sit only in the lower part of the chart: full bar height = 100% chance, or the wettest hour in the 3 days (at least 1 mm).",
            "loading" to "Open-Meteo · loading …",
            "addLocation" to "+ Location",
            "addLocationAccessible" to "Add location",
            "selectLocation" to "Select {location}",
            "removeLocation" to "Remove {location}",
            "searching" to "Searching …",
            "locationSearchUnavailable" to "Location search is currently unavailable",
            "noLocationsFound" to "No matching locations found",
            "chooseLocation" to "Choose a location",
            "saved" to "{location} · saved",
            "refreshing" to "Open-Meteo · updating …",
            "updated" to "Open-Meteo · updated {time}",
            "offlineCached" to "Offline · showing the last forecast",
            "weatherUnavailable" to "Weather data is currently unavailable",
            "offline" to "Offline",
            "weather" to "Weather",
            "savedLocations" to "Saved locations",
            "remove" to "Remove",
            "addWidgetHint" to "Widget: long-press the home screen → Widgets → Wetterkurve",
        ),
    )

    fun forLocale(locale: String? = null): String {
        val tag = locale ?: Locale.getDefault().toLanguageTag()
        return if (Regex("^de(?:[-_]|$)", RegexOption.IGNORE_CASE).containsMatchIn(tag)) "de" else "en"
    }

    fun localeTag(language: String): String = if (language == "de") "de-DE" else "en-US"

    fun text(language: String, key: String, values: Map<String, String> = emptyMap()): String {
        var template = strings[language]?.get(key) ?: strings.getValue("en")[key] ?: key
        for ((name, value) in values) {
            template = template.replace("{$name}", value)
        }
        return template
    }
}
