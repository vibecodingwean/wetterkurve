using Xunit;

namespace Wetterkurve.Tests;

public class WeatherServiceTests
{
    static ForecastPayload SamplePayload()
    {
        var hours = Enumerable.Range(0, 96).Select(i =>
        {
            var date = new DateTime(2026, 7, 30, 0, 0, 0).AddHours(i);
            return date.ToString("yyyy-MM-ddTHH:mm");
        }).ToList();
        var values = Enumerable.Range(0, hours.Count).Select(i => (double)i).ToList();
        var codes = Enumerable.Range(0, hours.Count).ToList();
        return new ForecastPayload(
            new CurrentWeather(21.6, 22.1, 1, 12, 50),
            new HourlyWeather(hours, values, values, values, values, codes, values));
    }

    [Fact]
    public void ValidateForecast_accepts_complete_payload()
    {
        var payload = SamplePayload();
        Assert.Same(payload, WeatherService.ValidateForecast(payload));
    }

    [Fact]
    public void ChartForecast_covers_three_full_days_from_midnight()
    {
        var forecast = WeatherService.ChartForecast(
            SamplePayload(), 72, new DateTime(2026, 7, 31, 14, 30, 0));
        Assert.Equal(72, forecast.Count);
        Assert.Equal(new DateTime(2026, 7, 31, 0, 0, 0), forecast[0].Time);
        Assert.Equal(new DateTime(2026, 8, 2, 23, 0, 0), forecast[^1].Time);
    }

    [Fact]
    public void ChartDaySegments_span_three_local_days()
    {
        var forecast = WeatherService.ChartForecast(
            SamplePayload(), 72, new DateTime(2026, 7, 31, 14, 30, 0));
        var days = WeatherService.ChartDaySegments(forecast, "de-DE");
        Assert.Equal(3, days.Count);
        Assert.Equal("2026-07-31,2026-08-01,2026-08-02", string.Join(",", days.Select(day => day.Key)));
        Assert.All(days, day =>
        {
            Assert.False(string.IsNullOrWhiteSpace(day.Label));
            Assert.Equal(24, day.End - day.Start);
        });
        Assert.Equal(0, days[0].Start);
        Assert.Equal(72, days[^1].End);
    }

    [Fact]
    public void WeatherInfo_uses_language_and_icon_fallbacks()
    {
        Assert.Equal("Klar", WeatherService.WeatherInfo(0, "de").Description);
        Assert.Equal("Clear", WeatherService.WeatherInfo(0).Description);
        Assert.Equal("Unbekannt", WeatherService.WeatherInfo(1234, "de").Description);
        Assert.Equal("Unknown", WeatherService.WeatherInfo(1234).Description);
        Assert.Equal("clear", WeatherService.WeatherInfo(0).Icon);
        Assert.Equal("rain", WeatherService.WeatherInfo(63).Icon);
        Assert.Equal("unknown", WeatherService.WeatherInfo(1234).Icon);
    }

    [Fact]
    public void Round_matches_javascript_math_round()
    {
        Assert.Equal(22, WeatherService.Round(21.6));
    }

    [Fact]
    public void BuildForecastUrl_matches_gnome_extension()
    {
        var url = WeatherService.BuildForecastUrl(48.1374, 11.5755);
        Assert.StartsWith("https://api.open-meteo.com/v1/forecast?", url);
        Assert.Contains("forecast_days=3", url);
        Assert.Contains("timezone=Europe%2FBerlin", url);
    }

    [Fact]
    public void BuildGeocodingUrl_encodes_query()
    {
        var url = WeatherService.BuildGeocodingUrl("São Paulo");
        Assert.StartsWith("https://geocoding-api.open-meteo.com/v1/search?", url);
        Assert.Contains("name=S%C3%A3o%20Paulo", url);
    }

    [Fact]
    public void Language_detects_german_locales()
    {
        Assert.Equal("de", Language.ForLocale("de-DE"));
        Assert.Equal("de", Language.ForLocale("de"));
        Assert.Equal("en", Language.ForLocale("en-US"));
        Assert.Equal("Ort suchen …", Language.Text("de", "searchLocation"));
        Assert.Equal("Munich · gespeichert", Language.Text("de", "saved", new Dictionary<string, string>
        {
            ["location"] = "Munich",
        }));
    }

    [Fact]
    public void ParseLocations_falls_back_on_invalid_json()
    {
        var parsed = WeatherService.ParseLocations("not-json", [WeatherService.DefaultLocation]);
        Assert.Single(parsed);
        Assert.Equal(WeatherService.DefaultLocation.Id, parsed[0].Id);
    }

    [Fact]
    public void ChartRenderer_writes_png()
    {
        var forecast = WeatherService.ChartForecast(
            SamplePayload(), 72, new DateTime(2026, 7, 31, 14, 30, 0));
        var png = ChartRenderer.RenderPng(forecast, "de-DE", 680, 250);
        Assert.True(png.Length > 100);
        Assert.Equal(0x89, png[0]);
        Assert.Equal((byte)'P', png[1]);
        Assert.Equal((byte)'N', png[2]);
        Assert.Equal((byte)'G', png[3]);
    }
}
