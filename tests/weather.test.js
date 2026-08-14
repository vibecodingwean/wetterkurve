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
    },
};

assert(validateForecast(payload) === payload, 'valid forecast rejected');
const forecast = chartForecast(payload, 72, new Date(2026, 6, 31, 14, 30));
assert(forecast.length === 72, 'chart must contain three full days');
assert(forecast[0].time === '2026-07-31T00:00',
    'chart must start at midnight today');
assert(forecast.at(-1).time === '2026-08-02T23:00',
    'chart must end at the last hour of the third day');
const days = chartDaySegments(forecast, 'de-DE');
assert(days.length === 3, 'chart must contain three day segments');
assert(days[0].label === 'Freitag' && days[1].label === 'Samstag' &&
    days[2].label === 'Sonntag', 'day segment labels are wrong');
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

const url = buildForecastUrl(48.1374, 11.5755);
assert(url.startsWith('https://api.open-meteo.com/v1/forecast?'),
    'forecast URL endpoint is wrong');
assert(url.includes('forecast_days=3'), 'forecast URL range is wrong');
assert(url.includes('timezone=Europe%2FBerlin'), 'forecast URL timezone is wrong');

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

const fallback = [{name: 'München', latitude: 48.1374, longitude: 11.5755}];
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

let invalidRejected = false;
try {
    validateForecast({current: {}, hourly: {}});
} catch {
    invalidRejected = true;
}
assert(invalidRejected, 'invalid forecast was accepted');

print('weather.test.js: OK');
