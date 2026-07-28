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

const hours = Array.from({length: 60}, (_, i) =>
    new Date(Date.now() + i * 3_600_000).toISOString().slice(0, 13) + ':00');
const values = Array.from({length: 60}, (_, i) => i);
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
assert(chartForecast(payload).length === 48, 'chart must contain 48 hours');
assert(weatherInfo(0)[1] === 'Klar', 'WMO mapping is wrong');
assert(weatherInfo(1234)[1] === 'Unbekannt', 'WMO fallback is wrong');
assert(round(21.6) === 22, 'temperature rounding is wrong');

const url = buildForecastUrl(48.1374, 11.5755);
assert(url.startsWith('https://api.open-meteo.com/v1/forecast?'),
    'forecast URL endpoint is wrong');
assert(url.includes('forecast_hours=60'), 'forecast URL range is wrong');
assert(url.includes('timezone=Europe%2FBerlin'), 'forecast URL timezone is wrong');

let invalidRejected = false;
try {
    validateForecast({current: {}, hourly: {}});
} catch {
    invalidRejected = true;
}
assert(invalidRejected, 'invalid forecast was accepted');

print('weather.test.js: OK');
