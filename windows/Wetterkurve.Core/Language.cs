namespace Wetterkurve;

public static class Language
{
    static readonly Dictionary<string, Dictionary<string, string>> Strings = new()
    {
        ["de"] = new()
        {
            ["searchLocation"] = "Ort suchen …",
            ["enterTwoLetters"] = "Mindestens zwei Buchstaben eingeben",
            ["loadingWeather"] = "Wetter wird geladen …",
            ["refreshWeather"] = "Wetter aktualisieren",
            ["feelsLike"] = "Gefühlt",
            ["rain"] = "Regen",
            ["wind"] = "Wind",
            ["humidity"] = "Feuchte",
            ["threeDayForecast"] = "3 Tage ab heute",
            ["chartLegend"] = "● Temperatur   ┄ gefühlt   ▮ Regen",
            ["loading"] = "Open-Meteo · lädt …",
            ["addLocation"] = "+ Ort",
            ["addLocationAccessible"] = "Ort hinzufügen",
            ["selectLocation"] = "{location} auswählen",
            ["removeLocation"] = "{location} entfernen",
            ["searching"] = "Suche …",
            ["locationSearchUnavailable"] = "Ortsuche derzeit nicht erreichbar",
            ["noLocationsFound"] = "Keine passenden Orte gefunden",
            ["chooseLocation"] = "Treffer auswählen",
            ["saved"] = "{location} · gespeichert",
            ["refreshing"] = "Open-Meteo · aktualisiert …",
            ["updated"] = "Open-Meteo · aktualisiert {time} Uhr",
            ["offlineCached"] = "Keine Verbindung · letzte Prognose bleibt sichtbar",
            ["weatherUnavailable"] = "Wetterdaten derzeit nicht erreichbar",
            ["offline"] = "Keine Verbindung",
            ["weather"] = "Wetter",
            ["customize"] = "Orte verwalten",
            ["exitCustomize"] = "Fertig",
            ["search"] = "Suchen",
            ["savedLocations"] = "Gespeicherte Orte",
            ["remove"] = "Entfernen",
            ["openWidgetHint"] = "Widget: Win+W → Widgets hinzufügen → Wetterkurve",
        },
        ["en"] = new()
        {
            ["searchLocation"] = "Search for a location …",
            ["enterTwoLetters"] = "Enter at least two letters",
            ["loadingWeather"] = "Loading weather …",
            ["refreshWeather"] = "Refresh weather",
            ["feelsLike"] = "Feels like",
            ["rain"] = "Rain",
            ["wind"] = "Wind",
            ["humidity"] = "Humidity",
            ["threeDayForecast"] = "3 days from today",
            ["chartLegend"] = "● Temperature   ┄ feels like   ▮ rain",
            ["loading"] = "Open-Meteo · loading …",
            ["addLocation"] = "+ Location",
            ["addLocationAccessible"] = "Add location",
            ["selectLocation"] = "Select {location}",
            ["removeLocation"] = "Remove {location}",
            ["searching"] = "Searching …",
            ["locationSearchUnavailable"] = "Location search is currently unavailable",
            ["noLocationsFound"] = "No matching locations found",
            ["chooseLocation"] = "Choose a location",
            ["saved"] = "{location} · saved",
            ["refreshing"] = "Open-Meteo · updating …",
            ["updated"] = "Open-Meteo · updated {time}",
            ["offlineCached"] = "Offline · showing the last forecast",
            ["weatherUnavailable"] = "Weather data is currently unavailable",
            ["offline"] = "Offline",
            ["weather"] = "Weather",
            ["customize"] = "Manage locations",
            ["exitCustomize"] = "Done",
            ["search"] = "Search",
            ["savedLocations"] = "Saved locations",
            ["remove"] = "Remove",
            ["openWidgetHint"] = "Widget: Win+W → Add widgets → Wetterkurve",
        },
    };

    public static string ForLocale(string? locale = null)
    {
        locale ??= System.Globalization.CultureInfo.CurrentUICulture.Name;
        return System.Text.RegularExpressions.Regex.IsMatch(locale, @"^de(?:[-_]|$)",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase) ? "de" : "en";
    }

    public static string LocaleTag(string language) => language == "de" ? "de-DE" : "en-US";

    public static string Text(string language, string key, IReadOnlyDictionary<string, string>? values = null)
    {
        if (!Strings.TryGetValue(language, out var table) || !table.TryGetValue(key, out var template))
            template = Strings["en"].GetValueOrDefault(key, key);
        if (values is null)
            return template;
        foreach (var (name, value) in values)
            template = template.Replace("{" + name + "}", value);
        return template;
    }
}
