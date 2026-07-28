export const WMO = {
    0: ['☀', 'Klar'],
    1: ['🌤', 'Überwiegend klar'],
    2: ['⛅', 'Teilweise bewölkt'],
    3: ['☁', 'Bedeckt'],
    45: ['≋', 'Nebel'],
    48: ['≋', 'Reifnebel'],
    51: ['☂', 'Leichter Nieselregen'],
    53: ['☂', 'Nieselregen'],
    55: ['☂', 'Starker Nieselregen'],
    56: ['☂', 'Gefrierender Nieselregen'],
    57: ['☂', 'Starker gefrierender Nieselregen'],
    61: ['☂', 'Leichter Regen'],
    63: ['☂', 'Regen'],
    65: ['☂', 'Starker Regen'],
    66: ['☂', 'Gefrierender Regen'],
    67: ['☂', 'Starker gefrierender Regen'],
    71: ['❄', 'Leichter Schneefall'],
    73: ['❄', 'Schneefall'],
    75: ['❄', 'Starker Schneefall'],
    77: ['❄', 'Schneegriesel'],
    80: ['☂', 'Leichte Regenschauer'],
    81: ['☂', 'Regenschauer'],
    82: ['☂', 'Starke Regenschauer'],
    85: ['❄', 'Leichte Schneeschauer'],
    86: ['❄', 'Starke Schneeschauer'],
    95: ['ϟ', 'Gewitter'],
    96: ['ϟ', 'Gewitter mit Hagel'],
    99: ['ϟ', 'Starkes Gewitter mit Hagel'],
};

export function weatherInfo(code) {
    return WMO[code] ?? ['•', 'Unbekannt'];
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
        'forecast_hours=60',
        `timezone=${encodeURIComponent(timezone)}`,
    ];
    return `https://api.open-meteo.com/v1/forecast?${params.join('&')}`;
}

export function chartForecast(payload, hours = 48) {
    if (!payload?.hourly?.time?.length)
        return [];

    const h = payload.hourly;
    const now = Date.now();
    let start = h.time.findIndex(value =>
        new Date(value).getTime() >= now - 60 * 60 * 1000);
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
