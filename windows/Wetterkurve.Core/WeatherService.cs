using System.Globalization;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Wetterkurve;

public static class WeatherService
{
    public const int MaxLocations = 3;
    public const int ChartHours = 3 * 24;
    public const int UpdateSeconds = 20 * 60;
    public const int StaleSeconds = 10 * 60;
    public const int SearchDelayMs = 250;

    public static readonly WeatherLocation DefaultLocation = new(
        "48.1374,11.5755",
        "Munich",
        "Munich, Bavaria, Germany",
        48.1374,
        11.5755,
        "Europe/Berlin");

    static readonly Dictionary<int, (string Symbol, string De, string En, string Icon)> Wmo = new()
    {
        [0] = ("☀", "Klar", "Clear", "clear"),
        [1] = ("🌤", "Überwiegend klar", "Mainly clear", "partly-cloudy"),
        [2] = ("⛅", "Teilweise bewölkt", "Partly cloudy", "partly-cloudy"),
        [3] = ("☁", "Bedeckt", "Overcast", "cloudy"),
        [45] = ("≋", "Nebel", "Fog", "fog"),
        [48] = ("≋", "Reifnebel", "Rime fog", "fog"),
        [51] = ("☂", "Leichter Nieselregen", "Light drizzle", "drizzle"),
        [53] = ("☂", "Nieselregen", "Drizzle", "drizzle"),
        [55] = ("☂", "Starker Nieselregen", "Dense drizzle", "rain"),
        [56] = ("☂", "Gefrierender Nieselregen", "Freezing drizzle", "drizzle"),
        [57] = ("☂", "Starker gefrierender Nieselregen", "Dense freezing drizzle", "rain"),
        [61] = ("☂", "Leichter Regen", "Slight rain", "rain"),
        [63] = ("☂", "Regen", "Rain", "rain"),
        [65] = ("☂", "Starker Regen", "Heavy rain", "rain"),
        [66] = ("☂", "Gefrierender Regen", "Freezing rain", "rain"),
        [67] = ("☂", "Starker gefrierender Regen", "Heavy freezing rain", "rain"),
        [71] = ("❄", "Leichter Schneefall", "Slight snow fall", "snow"),
        [73] = ("❄", "Schneefall", "Snow fall", "snow"),
        [75] = ("❄", "Starker Schneefall", "Heavy snow fall", "snow"),
        [77] = ("❄", "Schneegriesel", "Snow grains", "snow"),
        [80] = ("☂", "Leichte Regenschauer", "Slight rain showers", "rain"),
        [81] = ("☂", "Regenschauer", "Rain showers", "rain"),
        [82] = ("☂", "Starke Regenschauer", "Violent rain showers", "rain"),
        [85] = ("❄", "Leichte Schneeschauer", "Slight snow showers", "snow"),
        [86] = ("❄", "Starke Schneeschauer", "Heavy snow showers", "snow"),
        [95] = ("ϟ", "Gewitter", "Thunderstorm", "thunderstorm"),
        [96] = ("ϟ", "Gewitter mit Hagel", "Thunderstorm with hail", "thunderstorm"),
        [99] = ("ϟ", "Starkes Gewitter mit Hagel", "Thunderstorm with heavy hail", "thunderstorm"),
    };

    static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false,
    };

    public static WeatherCondition WeatherInfo(int code, string language = "en")
    {
        if (!Wmo.TryGetValue(code, out var info))
            return new("•", language == "de" ? "Unbekannt" : "Unknown", "unknown");
        return new(info.Symbol, language == "de" ? info.De : info.En, info.Icon);
    }

    public static int Round(double value) => (int)Math.Round(value, MidpointRounding.AwayFromZero);

    public static string BuildForecastUrl(double latitude, double longitude, string timezone = "Europe/Berlin")
    {
        const string current = "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m";
        const string hourly = "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m";
        return "https://api.open-meteo.com/v1/forecast?" +
            $"latitude={latitude.ToString(CultureInfo.InvariantCulture)}" +
            $"&longitude={longitude.ToString(CultureInfo.InvariantCulture)}" +
            $"&current={current}" +
            $"&hourly={hourly}" +
            "&forecast_days=3" +
            $"&timezone={Uri.EscapeDataString(timezone)}";
    }

    public static string BuildGeocodingUrl(string query, int count = 8, string language = "en")
    {
        return "https://geocoding-api.open-meteo.com/v1/search?" +
            $"name={Uri.EscapeDataString(query.Trim())}" +
            $"&count={count}" +
            $"&language={Uri.EscapeDataString(language)}" +
            "&format=json";
    }

    public static WeatherLocation? LocationFromGeocodingResult(GeocodingHit? result)
    {
        if (result?.Name is null ||
            !double.IsFinite(result.Latitude) ||
            !double.IsFinite(result.Longitude))
            return null;

        var detail = new[] { result.Admin1, result.Country }.Where(value => !string.IsNullOrWhiteSpace(value));
        return new WeatherLocation(
            $"{result.Latitude.ToString("0.0000", CultureInfo.InvariantCulture)},{result.Longitude.ToString("0.0000", CultureInfo.InvariantCulture)}",
            result.Name,
            string.Join(", ", new[] { result.Name }.Concat(detail)),
            result.Latitude,
            result.Longitude,
            string.IsNullOrWhiteSpace(result.Timezone) ? "auto" : result.Timezone);
    }

    public static List<WeatherLocation> ParseLocations(string? value, IReadOnlyList<WeatherLocation> fallback)
    {
        try
        {
            var parsed = JsonSerializer.Deserialize<List<WeatherLocationJson>>(value ?? "", JsonOptions);
            if (parsed is null)
                throw new InvalidOperationException("locations must be an array");

            var valid = parsed
                .Select(location => new WeatherLocation(
                    string.IsNullOrWhiteSpace(location.Id)
                        ? $"{location.Latitude.ToString("0.0000", CultureInfo.InvariantCulture)},{location.Longitude.ToString("0.0000", CultureInfo.InvariantCulture)}"
                        : location.Id!,
                    location.Name ?? "",
                    location.Label ?? location.Name ?? "",
                    location.Latitude,
                    location.Longitude,
                    string.IsNullOrWhiteSpace(location.Timezone) ? "auto" : location.Timezone!))
                .Where(location => !string.IsNullOrWhiteSpace(location.Name) &&
                    double.IsFinite(location.Latitude) &&
                    double.IsFinite(location.Longitude))
                .Take(MaxLocations)
                .ToList();
            return valid.Count > 0 ? valid : fallback.ToList();
        }
        catch
        {
            return fallback.ToList();
        }
    }

    public static string SerializeLocations(IEnumerable<WeatherLocation> locations) =>
        JsonSerializer.Serialize(locations.Select(location => new WeatherLocationJson
        {
            Id = location.Id,
            Name = location.Name,
            Label = location.Label,
            Latitude = location.Latitude,
            Longitude = location.Longitude,
            Timezone = location.Timezone,
        }), JsonOptions);

    public static List<HourlyPoint> ChartForecast(ForecastPayload payload, int hours = ChartHours, DateTime? now = null)
    {
        if (payload.Hourly.Time.Count == 0)
            return [];

        var clock = now ?? DateTime.Now;
        var midnight = clock.Date;
        var start = payload.Hourly.Time.FindIndex(value => ParseHour(value) >= midnight);
        if (start < 0)
            start = 0;

        var length = Math.Min(hours, payload.Hourly.Time.Count - start);
        var points = new List<HourlyPoint>(length);
        for (var offset = 0; offset < length; offset++)
        {
            var i = start + offset;
            points.Add(new HourlyPoint(
                ParseHour(payload.Hourly.Time[i]),
                payload.Hourly.Temperature[i],
                payload.Hourly.ApparentTemperature[i],
                payload.Hourly.PrecipitationProbability[i],
                payload.Hourly.Precipitation[i],
                payload.Hourly.WeatherCode[i],
                payload.Hourly.WindSpeed[i]));
        }
        return points;
    }

    public static List<DaySegment> ChartDaySegments(IReadOnlyList<HourlyPoint> forecast, string locale = "en-US")
    {
        var culture = CultureInfo.GetCultureInfo(locale);
        var segments = new List<DaySegment>();
        for (var index = 0; index < forecast.Count; index++)
        {
            var date = forecast[index].Time;
            var key = date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
            if (segments.Count > 0 && segments[^1].Key == key)
            {
                var current = segments[^1];
                segments[^1] = current with { End = index + 1 };
                continue;
            }

            segments.Add(new DaySegment(
                key,
                date.ToString("dddd", culture),
                index,
                index + 1));
        }
        return segments;
    }

    public static ForecastPayload ValidateForecast(ForecastPayload payload)
    {
        if (payload.Current is null || payload.Hourly is null)
            throw new InvalidOperationException("Incomplete weather data");

        var hourly = payload.Hourly;
        if (hourly.Time is null || hourly.Temperature is null || hourly.ApparentTemperature is null ||
            hourly.PrecipitationProbability is null || hourly.Precipitation is null ||
            hourly.WeatherCode is null || hourly.WindSpeed is null)
            throw new InvalidOperationException("Incomplete weather data");

        var length = hourly.Time.Count;
        if (length < 2 ||
            hourly.Temperature.Count != length ||
            hourly.ApparentTemperature.Count != length ||
            hourly.PrecipitationProbability.Count != length ||
            hourly.Precipitation.Count != length ||
            hourly.WeatherCode.Count != length ||
            hourly.WindSpeed.Count != length)
            throw new InvalidOperationException("Inconsistent weather data");

        return payload;
    }

    public static async Task<ForecastPayload> FetchForecastAsync(
        HttpClient http,
        WeatherLocation location,
        CancellationToken cancellationToken = default)
    {
        var payload = await http.GetFromJsonAsync<ForecastPayload>(
            BuildForecastUrl(location.Latitude, location.Longitude, location.Timezone),
            JsonOptions,
            cancellationToken).ConfigureAwait(false);
        if (payload is null)
            throw new InvalidOperationException("Incomplete weather data");
        return ValidateForecast(payload);
    }

    public static async Task<List<WeatherLocation>> SearchLocationsAsync(
        HttpClient http,
        string query,
        string language = "en",
        CancellationToken cancellationToken = default)
    {
        var response = await http.GetFromJsonAsync<GeocodingResponse>(
            BuildGeocodingUrl(query, 8, language),
            JsonOptions,
            cancellationToken).ConfigureAwait(false);
        return (response?.Results ?? [])
            .Select(LocationFromGeocodingResult)
            .Where(location => location is not null)
            .Cast<WeatherLocation>()
            .ToList();
    }

    public static HttpClient CreateClient()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("Wetterkurve/1.0");
        return http;
    }

    public static int UpcomingRainChance(IReadOnlyList<HourlyPoint> forecast, DateTime? now = null)
    {
        var clock = now ?? DateTime.Now;
        var upcoming = forecast
            .Where(point => point.Time >= clock)
            .Take(12)
            .Select(point => point.PrecipitationProbability)
            .ToList();
        return upcoming.Count == 0 ? 0 : Round(upcoming.Max());
    }

    static DateTime ParseHour(string value)
    {
        return DateTime.Parse(value, CultureInfo.InvariantCulture, DateTimeStyles.AssumeLocal);
    }

    sealed class WeatherLocationJson
    {
        public string? Id { get; set; }
        public string? Name { get; set; }
        public string? Label { get; set; }
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public string? Timezone { get; set; }
    }
}
