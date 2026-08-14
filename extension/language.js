const STRINGS = {
    de: {
        searchLocation: 'Ort suchen …',
        enterTwoLetters: 'Mindestens zwei Buchstaben eingeben',
        loadingWeather: 'Wetter wird geladen …',
        refreshWeather: 'Wetter aktualisieren',
        feelsLike: 'Gefühlt',
        rain: 'Regen',
        wind: 'Wind',
        humidity: 'Feuchte',
        threeDayForecast: '3 Tage ab heute',
        chartLegend: '● Temperatur   ┄ gefühlt   ▮ Regen',
        loading: 'Open-Meteo · lädt …',
        addLocation: '+ Ort',
        addLocationAccessible: 'Ort hinzufügen',
        selectLocation: '{location} auswählen',
        removeLocation: '{location} entfernen',
        searching: 'Suche …',
        locationSearchUnavailable: 'Ortsuche derzeit nicht erreichbar',
        noLocationsFound: 'Keine passenden Orte gefunden',
        chooseLocation: 'Treffer auswählen',
        saved: '{location} · gespeichert',
        refreshing: 'Open-Meteo · aktualisiert …',
        updated: 'Open-Meteo · aktualisiert {time} Uhr',
        offlineCached: 'Keine Verbindung · letzte Prognose bleibt sichtbar',
        weatherUnavailable: 'Wetterdaten derzeit nicht erreichbar',
        offline: 'Keine Verbindung',
        weather: 'Wetter',
    },
    en: {
        searchLocation: 'Search for a location …',
        enterTwoLetters: 'Enter at least two letters',
        loadingWeather: 'Loading weather …',
        refreshWeather: 'Refresh weather',
        feelsLike: 'Feels like',
        rain: 'Rain',
        wind: 'Wind',
        humidity: 'Humidity',
        threeDayForecast: '3 days from today',
        chartLegend: '● Temperature   ┄ feels like   ▮ rain',
        loading: 'Open-Meteo · loading …',
        addLocation: '+ Location',
        addLocationAccessible: 'Add location',
        selectLocation: 'Select {location}',
        removeLocation: 'Remove {location}',
        searching: 'Searching …',
        locationSearchUnavailable: 'Location search is currently unavailable',
        noLocationsFound: 'No matching locations found',
        chooseLocation: 'Choose a location',
        saved: '{location} · saved',
        refreshing: 'Open-Meteo · updating …',
        updated: 'Open-Meteo · updated {time}',
        offlineCached: 'Offline · showing the last forecast',
        weatherUnavailable: 'Weather data is currently unavailable',
        offline: 'Offline',
        weather: 'Weather',
    },
};

export function languageForLocale(locale = Intl.DateTimeFormat().resolvedOptions().locale) {
    return /^de(?:[-_]|$)/i.test(locale || '') ? 'de' : 'en';
}

export function text(language, key, values = {}) {
    const template = STRINGS[language]?.[key] ?? STRINGS.en[key] ?? key;
    return template.replace(/\{(\w+)\}/g, (_match, name) => values[name] ?? '');
}
