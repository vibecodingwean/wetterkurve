import {
    buildForecastUrl,
    buildGeocodingUrl,
    chartDaySegments,
    chartForecast,
    locationFromGeocodingResult,
    parseLocations,
    round,
    validateForecast,
    weatherInfo,
} from '../extension/weather.js';
import {languageForLocale, text} from '../extension/language.js';

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
        cloud_cover: values,
    },
};

assert(validateForecast(payload) === payload, 'valid forecast rejected');
const forecast = chartForecast(payload, 72, new Date(2026, 6, 31, 14, 30));
assert(forecast.length === 72, 'chart must contain three full days');
assert(forecast[0].time === '2026-07-31T00:00',
    'chart must start at midnight today');
assert(forecast.at(-1).time === '2026-08-02T23:00',
    'chart must end at the last hour of the third day');
assert(forecast[0].cloudCover === 24, 'chart must read hourly cloud cover');
assert(forecast[0].wind === 24, 'chart must read hourly wind speed');
const days = chartDaySegments(forecast, 'de-DE');
assert(days.length === 3, 'chart must contain three day segments');
assert(days.map(day => day.key).join(',') ===
    '2026-07-31,2026-08-01,2026-08-02',
'day segment dates are wrong');
assert(days.every(day => typeof day.label === 'string' && day.label.length > 0),
    'day segment labels are missing');
assert(days.every(day => day.end - day.start === 24),
    'every full day segment must span 24 hourly values');
assert(days[0].start === 0 && days.at(-1).end === 72,
    'day segments must cover the complete chart');
assert(weatherInfo(0, 'de')[1] === 'Klar', 'German WMO mapping is wrong');
assert(weatherInfo(0)[1] === 'Clear', 'English WMO mapping is wrong');
assert(weatherInfo(1234, 'de')[1] === 'Unbekannt', 'German WMO fallback is wrong');
assert(weatherInfo(1234)[1] === 'Unknown', 'English WMO fallback is wrong');
assert(weatherInfo(0)[2] === 'clear', 'clear weather icon is wrong');
assert(weatherInfo(63)[2] === 'rain', 'rain weather icon is wrong');
assert(weatherInfo(1234)[2] === 'unknown', 'weather icon fallback is wrong');
assert(round(21.6) === 22, 'temperature rounding is wrong');

const url = buildForecastUrl(48.137, 11.576);
assert(url.startsWith('https://api.open-meteo.com/v1/forecast?'),
    'forecast URL endpoint is wrong');
assert(url.includes('forecast_days=3'), 'forecast URL range is wrong');
assert(url.includes('cloud_cover'), 'forecast URL must request cloud cover');
assert(url.includes('timezone=Europe%2FBerlin'), 'forecast URL timezone is wrong');

const expectedForecastQuery =
    'latitude=-33.869&longitude=151.209' +
    '&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m' +
    '&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,cloud_cover' +
    '&forecast_days=3&timezone=Australia%2FSydney';
assert(buildForecastUrl(-33.869, 151.209, 'Australia/Sydney') ===
    `https://api.open-meteo.com/v1/forecast?${expectedForecastQuery}`,
'forecast URL fields, ordering or escaping changed');

let validationCases = 0;
function checkForecast(candidate, expectedError = null) {
    validationCases++;
    const before = JSON.stringify(candidate);
    let result;
    let error;
    try {
        result = validateForecast(candidate);
    } catch (caught) {
        error = caught;
    }
    if (expectedError) {
        assert(error instanceof Error && error.message === expectedError,
            `expected ${expectedError}, received ${error?.message ?? 'success'}`);
    } else {
        assert(!error && result === candidate,
            'validation must return the original accepted payload');
    }
    assert(JSON.stringify(candidate) === before, 'validation must not mutate its input');
}

const copyPayload = () => JSON.parse(JSON.stringify(payload));
for (const candidate of [undefined, null, {}, {current: {}}, {hourly: {}}])
    checkForecast(candidate, 'Incomplete weather data');

for (const key of Object.keys(payload.current)) {
    const missing = copyPayload();
    delete missing.current[key];
    checkForecast(missing, 'Incomplete weather data');
    const undefinedValue = copyPayload();
    undefinedValue.current[key] = undefined;
    checkForecast(undefinedValue, 'Incomplete weather data');
    // The validator checks presence, not numeric validity of current values.
    for (const value of [null, 0, '21.6']) {
        const present = copyPayload();
        present.current[key] = value;
        checkForecast(present);
    }
}

for (const key of Object.keys(payload.hourly)) {
    const missing = copyPayload();
    delete missing.hourly[key];
    checkForecast(missing, 'Incomplete weather data');
    for (const value of [null, {}, 'invalid']) {
        const wrongType = copyPayload();
        wrongType.hourly[key] = value;
        checkForecast(wrongType, 'Incomplete weather data');
    }
    for (const length of [0, 1, hours.length - 1, hours.length + 1]) {
        const mismatched = copyPayload();
        mismatched.hourly[key] = Array(length).fill(0);
        checkForecast(mismatched, 'Inconsistent weather data');
    }
}

for (const length of [0, 1, 2, 72]) {
    const candidate = copyPayload();
    for (const key of Object.keys(candidate.hourly))
        candidate.hourly[key] = candidate.hourly[key].slice(0, length);
    checkForecast(candidate, length < 2 ? 'Inconsistent weather data' : null);
}

const mixedFailures = copyPayload();
mixedFailures.hourly.time = [];
delete mixedFailures.hourly.cloud_cover;
checkForecast(mixedFailures, 'Incomplete weather data');
const extended = copyPayload();
extended.current.extra = 'ignored';
extended.hourly.extra = [1];
checkForecast(extended);
checkForecast(copyPayload());

console.log(`forecast validation: ${validationCases} cases passed`);

const geocodingUrl = buildGeocodingUrl('São Paulo');
assert(geocodingUrl.startsWith('https://geocoding-api.open-meteo.com/v1/search?'),
    'geocoding URL endpoint is wrong');
assert(geocodingUrl.includes('name=S%C3%A3o%20Paulo'),
    'geocoding search must be URL encoded');
assert(geocodingUrl.includes('language=en'), 'English geocoding language is missing');
assert(buildGeocodingUrl('Berlin', 8, 'de').includes('language=de'),
    'German geocoding language is missing');

const berlin = locationFromGeocodingResult({
    name: 'Berlin',
    admin1: 'Berlin',
    country: 'Deutschland',
    latitude: 52.52,
    longitude: 13.405,
    timezone: 'Europe/Berlin',
});
assert(berlin?.label === 'Berlin, Berlin, Deutschland',
    'geocoding result label is wrong');
assert(berlin?.id === '52.5200,13.4050', 'geocoding result ID is wrong');
assert(locationFromGeocodingResult({name: 'Broken'}) === null,
    'invalid geocoding result was accepted');

const fallback = [{name: 'München', latitude: 48.137, longitude: 11.576}];
const parsedLocations = parseLocations(JSON.stringify([berlin]), fallback);
assert(parsedLocations.length === 1 && parsedLocations[0].name === 'Berlin',
    'saved locations were not parsed');
assert(parseLocations('invalid JSON', fallback) === fallback,
    'invalid saved locations did not use fallback');
assert(languageForLocale('de_DE') === 'de', 'German locale was not detected');
assert(languageForLocale('en_GB') === 'en', 'English locale was not detected');
assert(languageForLocale('fr_FR') === 'en', 'non-German locale must use English');
assert(text('en', 'removeLocation', {location: 'Munich'}) === 'Remove Munich',
    'English replacement text is wrong');
assert(text('de', 'clouds') === 'Wolke', 'German cloud label is wrong');
assert(text('en', 'clouds') === 'Cloud', 'English cloud label is wrong');

let invalidRejected = false;
try {
    validateForecast({current: {}, hourly: {}});
} catch {
    invalidRejected = true;
}
assert(invalidRejected, 'invalid forecast was accepted');

console.log('weather.test.js: OK');
