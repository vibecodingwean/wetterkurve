import {
    buildForecastUrl,
    chartForecast,
    round,
    validateForecast,
    weatherInfo,
} from '../extension/weather.js';

function assert(condition, message) {
    if (!condition)
        throw new Error(message);
}

const hours = Array.from({length: 96}, (_, i) => {
    const date = new Date(2026, 6, 30, i);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hour = String(date.getHours()).padStart(2, '0');
    return `${year}-${month}-${day}T${hour}:00`;
});
const values = Array.from({length: hours.length}, (_, i) => i);
const payload = {
    current: {
        temperature_2m: 21.6,
        apparent_temperature: 22.1,
        weather_code: 1,
        wind_speed_10m: 12,
        relative_humidity_2m: 50,
    },
    hourly: {
        time: hours,
        temperature_2m: values,
        apparent_temperature: values,
        precipitation_probability: values,
        precipitation: values,
        weather_code: values,
        wind_speed_10m: values,
    },
};

assert(validateForecast(payload) === payload, 'valid forecast rejected');
const forecast = chartForecast(payload, 72, new Date(2026, 6, 31, 14, 30));
assert(forecast.length === 72, 'chart must contain three full days');
assert(forecast[0].time === '2026-07-31T00:00',
    'chart must start at midnight today');
assert(forecast.at(-1).time === '2026-08-02T23:00',
    'chart must end at the last hour of the third day');
assert(weatherInfo(0)[1] === 'Klar', 'WMO mapping is wrong');
assert(weatherInfo(1234)[1] === 'Unbekannt', 'WMO fallback is wrong');
assert(weatherInfo(0)[2] === 'clear', 'clear weather icon is wrong');
assert(weatherInfo(63)[2] === 'rain', 'rain weather icon is wrong');
assert(weatherInfo(1234)[2] === 'unknown', 'weather icon fallback is wrong');
assert(round(21.6) === 22, 'temperature rounding is wrong');

const url = buildForecastUrl(48.1374, 11.5755);
assert(url.startsWith('https://api.open-meteo.com/v1/forecast?'),
    'forecast URL endpoint is wrong');
assert(url.includes('forecast_days=3'), 'forecast URL range is wrong');
assert(url.includes('timezone=Europe%2FBerlin'), 'forecast URL timezone is wrong');

let invalidRejected = false;
try {
    validateForecast({current: {}, hourly: {}});
} catch {
    invalidRejected = true;
}
assert(invalidRejected, 'invalid forecast was accepted');

print('weather.test.js: OK');
