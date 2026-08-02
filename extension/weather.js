export const WMO = {
    0: ['☀', 'Klar', 'clear'],
    1: ['🌤', 'Überwiegend klar', 'partly-cloudy'],
    2: ['⛅', 'Teilweise bewölkt', 'partly-cloudy'],
    3: ['☁', 'Bedeckt', 'cloudy'],
    45: ['≋', 'Nebel', 'fog'],
    48: ['≋', 'Reifnebel', 'fog'],
    51: ['☂', 'Leichter Nieselregen', 'drizzle'],
    53: ['☂', 'Nieselregen', 'drizzle'],
    55: ['☂', 'Starker Nieselregen', 'rain'],
    56: ['☂', 'Gefrierender Nieselregen', 'drizzle'],
    57: ['☂', 'Starker gefrierender Nieselregen', 'rain'],
    61: ['☂', 'Leichter Regen', 'rain'],
    63: ['☂', 'Regen', 'rain'],
    65: ['☂', 'Starker Regen', 'rain'],
    66: ['☂', 'Gefrierender Regen', 'rain'],
    67: ['☂', 'Starker gefrierender Regen', 'rain'],
    71: ['❄', 'Leichter Schneefall', 'snow'],
    73: ['❄', 'Schneefall', 'snow'],
    75: ['❄', 'Starker Schneefall', 'snow'],
    77: ['❄', 'Schneegriesel', 'snow'],
    80: ['☂', 'Leichte Regenschauer', 'rain'],
    81: ['☂', 'Regenschauer', 'rain'],
    82: ['☂', 'Starke Regenschauer', 'rain'],
    85: ['❄', 'Leichte Schneeschauer', 'snow'],
    86: ['❄', 'Starke Schneeschauer', 'snow'],
    95: ['ϟ', 'Gewitter', 'thunderstorm'],
    96: ['ϟ', 'Gewitter mit Hagel', 'thunderstorm'],
    99: ['ϟ', 'Starkes Gewitter mit Hagel', 'thunderstorm'],
};

export function weatherInfo(code) {
    return WMO[code] ?? ['•', 'Unbekannt', 'unknown'];
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

export function chartDaySegments(forecast, locale = 'de-DE') {
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
        throw new Error('Unvollständige Wetterdaten');

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
        throw new Error('Unvollständige Wetterdaten');

    const length = payload.hourly.time.length;
    if (length < 2 ||
        requiredHourly.some(key => payload.hourly[key].length !== length))
        throw new Error('Inkonsistente Wetterdaten');

    return payload;
}
