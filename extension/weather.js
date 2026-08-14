export const WMO = {
    0: ['☀', {de: 'Klar', en: 'Clear'}, 'clear'],
    1: ['🌤', {de: 'Überwiegend klar', en: 'Mainly clear'}, 'partly-cloudy'],
    2: ['⛅', {de: 'Teilweise bewölkt', en: 'Partly cloudy'}, 'partly-cloudy'],
    3: ['☁', {de: 'Bedeckt', en: 'Overcast'}, 'cloudy'],
    45: ['≋', {de: 'Nebel', en: 'Fog'}, 'fog'],
    48: ['≋', {de: 'Reifnebel', en: 'Rime fog'}, 'fog'],
    51: ['☂', {de: 'Leichter Nieselregen', en: 'Light drizzle'}, 'drizzle'],
    53: ['☂', {de: 'Nieselregen', en: 'Drizzle'}, 'drizzle'],
    55: ['☂', {de: 'Starker Nieselregen', en: 'Dense drizzle'}, 'rain'],
    56: ['☂', {de: 'Gefrierender Nieselregen', en: 'Freezing drizzle'}, 'drizzle'],
    57: ['☂', {de: 'Starker gefrierender Nieselregen', en: 'Dense freezing drizzle'}, 'rain'],
    61: ['☂', {de: 'Leichter Regen', en: 'Slight rain'}, 'rain'],
    63: ['☂', {de: 'Regen', en: 'Rain'}, 'rain'],
    65: ['☂', {de: 'Starker Regen', en: 'Heavy rain'}, 'rain'],
    66: ['☂', {de: 'Gefrierender Regen', en: 'Freezing rain'}, 'rain'],
    67: ['☂', {de: 'Starker gefrierender Regen', en: 'Heavy freezing rain'}, 'rain'],
    71: ['❄', {de: 'Leichter Schneefall', en: 'Slight snow fall'}, 'snow'],
    73: ['❄', {de: 'Schneefall', en: 'Snow fall'}, 'snow'],
    75: ['❄', {de: 'Starker Schneefall', en: 'Heavy snow fall'}, 'snow'],
    77: ['❄', {de: 'Schneegriesel', en: 'Snow grains'}, 'snow'],
    80: ['☂', {de: 'Leichte Regenschauer', en: 'Slight rain showers'}, 'rain'],
    81: ['☂', {de: 'Regenschauer', en: 'Rain showers'}, 'rain'],
    82: ['☂', {de: 'Starke Regenschauer', en: 'Violent rain showers'}, 'rain'],
    85: ['❄', {de: 'Leichte Schneeschauer', en: 'Slight snow showers'}, 'snow'],
    86: ['❄', {de: 'Starke Schneeschauer', en: 'Heavy snow showers'}, 'snow'],
    95: ['ϟ', {de: 'Gewitter', en: 'Thunderstorm'}, 'thunderstorm'],
    96: ['ϟ', {de: 'Gewitter mit Hagel', en: 'Thunderstorm with hail'}, 'thunderstorm'],
    99: ['ϟ', {de: 'Starkes Gewitter mit Hagel', en: 'Thunderstorm with heavy hail'}, 'thunderstorm'],
};

export function weatherInfo(code, language = 'en') {
    const [symbol, labels, icon] = WMO[code] ??
        ['•', {de: 'Unbekannt', en: 'Unknown'}, 'unknown'];
    return [symbol, labels[language] ?? labels.en, icon];
}

export function round(value) {
    return Math.round(Number(value));
}

export function buildForecastUrl(latitude, longitude, timezone = 'Europe/Berlin') {
    const current = [
        'temperature_2m',
        'apparent_temperature',
        'weather_code',
        'wind_speed_10m',
        'relative_humidity_2m',
    ].join(',');
    const hourly = [
        'temperature_2m',
        'apparent_temperature',
        'precipitation_probability',
        'precipitation',
        'weather_code',
        'wind_speed_10m',
    ].join(',');
    const params = [
        `latitude=${latitude}`,
        `longitude=${longitude}`,
        `current=${current}`,
        `hourly=${hourly}`,
        'forecast_days=3',
        `timezone=${encodeURIComponent(timezone)}`,
    ];
    return `https://api.open-meteo.com/v1/forecast?${params.join('&')}`;
}

export function buildGeocodingUrl(query, count = 8, language = 'en') {
    const params = [
        `name=${encodeURIComponent(query.trim())}`,
        `count=${count}`,
        `language=${encodeURIComponent(language)}`,
        'format=json',
    ];
    return `https://geocoding-api.open-meteo.com/v1/search?${params.join('&')}`;
}

export function locationFromGeocodingResult(result) {
    if (!result?.name || !Number.isFinite(Number(result.latitude)) ||
        !Number.isFinite(Number(result.longitude)))
        return null;

    const detail = [result.admin1, result.country].filter(Boolean);
    return {
        id: `${Number(result.latitude).toFixed(4)},${Number(result.longitude).toFixed(4)}`,
        name: result.name,
        label: [result.name, ...detail].join(', '),
        latitude: Number(result.latitude),
        longitude: Number(result.longitude),
        timezone: result.timezone || 'auto',
    };
}

export function parseLocations(value, fallback) {
    try {
        const locations = JSON.parse(value);
        if (!Array.isArray(locations))
            throw new Error('locations must be an array');

        const validLocations = locations
            .map(location => ({
                ...location,
                id: location.id || `${Number(location.latitude).toFixed(4)},${Number(location.longitude).toFixed(4)}`,
                latitude: Number(location.latitude),
                longitude: Number(location.longitude),
                timezone: location.timezone || 'auto',
            }))
            .filter(location => location.name &&
                Number.isFinite(location.latitude) &&
                Number.isFinite(location.longitude));
        return validLocations.length ? validLocations.slice(0, 3) : fallback;
    } catch {
        return fallback;
    }
}

export function chartForecast(payload, hours = 72, now = new Date()) {
    if (!payload?.hourly?.time?.length)
        return [];

    const h = payload.hourly;
    const midnight = new Date(now);
    midnight.setHours(0, 0, 0, 0);
    let start = h.time.findIndex(value =>
        new Date(value).getTime() >= midnight.getTime());
    if (start < 0)
        start = 0;

    return h.time.slice(start, start + hours).map((time, offset) => {
        const i = start + offset;
        return {
            time,
            temperature: Number(h.temperature_2m[i]),
            apparent: Number(h.apparent_temperature[i]),
            precipitationProbability:
                Number(h.precipitation_probability[i] ?? 0),
            precipitation: Number(h.precipitation[i] ?? 0),
            weatherCode: Number(h.weather_code[i]),
            wind: Number(h.wind_speed_10m[i]),
        };
    });
}

export function chartDaySegments(forecast, locale = 'en-US') {
    const segments = [];

    forecast.forEach((point, index) => {
        const date = new Date(point.time);
        const key = [
            date.getFullYear(),
            String(date.getMonth() + 1).padStart(2, '0'),
            String(date.getDate()).padStart(2, '0'),
        ].join('-');
        const current = segments.at(-1);

        if (current?.key === key) {
            current.end = index + 1;
            return;
        }

        segments.push({
            key,
            label: date.toLocaleDateString(locale, {weekday: 'long'}),
            start: index,
            end: index + 1,
        });
    });

    return segments;
}

export function validateForecast(payload) {
    if (!payload?.current || !payload?.hourly)
        throw new Error('Incomplete weather data');

    const requiredCurrent = [
        'temperature_2m',
        'apparent_temperature',
        'weather_code',
        'wind_speed_10m',
        'relative_humidity_2m',
    ];
    const requiredHourly = [
        'time',
        'temperature_2m',
        'apparent_temperature',
        'precipitation_probability',
        'precipitation',
        'weather_code',
        'wind_speed_10m',
    ];
    if (requiredCurrent.some(key => payload.current[key] === undefined) ||
        requiredHourly.some(key => !Array.isArray(payload.hourly[key])))
        throw new Error('Incomplete weather data');

    const length = payload.hourly.time.length;
    if (length < 2 ||
        requiredHourly.some(key => payload.hourly[key].length !== length))
        throw new Error('Inconsistent weather data');

    return payload;
}
