using System.Text.Json.Serialization;

namespace Wetterkurve;

public sealed record WeatherLocation(
    string Id,
    string Name,
    string Label,
    double Latitude,
    double Longitude,
    string Timezone);

public sealed record HourlyPoint(
    DateTime Time,
    double Temperature,
    double Apparent,
    double PrecipitationProbability,
    double Precipitation,
    int WeatherCode,
    double Wind);

public sealed record CurrentWeather(
    [property: JsonPropertyName("temperature_2m")] double Temperature,
    [property: JsonPropertyName("apparent_temperature")] double ApparentTemperature,
    [property: JsonPropertyName("weather_code")] int WeatherCode,
    [property: JsonPropertyName("wind_speed_10m")] double WindSpeed,
    [property: JsonPropertyName("relative_humidity_2m")] double RelativeHumidity);

public sealed record HourlyWeather(
    [property: JsonPropertyName("time")] List<string> Time,
    [property: JsonPropertyName("temperature_2m")] List<double> Temperature,
    [property: JsonPropertyName("apparent_temperature")] List<double> ApparentTemperature,
    [property: JsonPropertyName("precipitation_probability")] List<double> PrecipitationProbability,
    [property: JsonPropertyName("precipitation")] List<double> Precipitation,
    [property: JsonPropertyName("weather_code")] List<int> WeatherCode,
    [property: JsonPropertyName("wind_speed_10m")] List<double> WindSpeed);

public sealed record ForecastPayload(
    [property: JsonPropertyName("current")] CurrentWeather Current,
    [property: JsonPropertyName("hourly")] HourlyWeather Hourly);

public sealed record GeocodingHit(
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("latitude")] double Latitude,
    [property: JsonPropertyName("longitude")] double Longitude,
    [property: JsonPropertyName("admin1")] string? Admin1,
    [property: JsonPropertyName("country")] string? Country,
    [property: JsonPropertyName("timezone")] string? Timezone);

public sealed record GeocodingResponse(
    [property: JsonPropertyName("results")] List<GeocodingHit>? Results);

public sealed record DaySegment(string Key, string Label, int Start, int End);

public sealed record WeatherCondition(string Symbol, string Description, string Icon);

public sealed record AppState
{
    public List<WeatherLocation> Locations { get; set; } = [];
    public int ActiveLocation { get; set; }
    public double? IndicatorLeft { get; set; }
    public double? IndicatorTop { get; set; }
}
