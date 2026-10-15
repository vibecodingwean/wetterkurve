using Xunit;
using SkiaSharp;

namespace Wetterkurve.Tests;

public class WeatherServiceTests
{
    [Fact]
    public void Fresh_defaults_and_saved_choices_include_feels_like()
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
        try
        {
            var defaults = SettingsStore.Load(path);
            Assert.Equal("Berlin", defaults.Locations[0].Name);
            Assert.True(defaults.ShowClouds);
            Assert.False(defaults.ShowWind);
            Assert.False(defaults.ShowFeelsLike);

            defaults.Locations = [new WeatherLocation("48.137,11.576", "Munich", "Munich, Bavaria, Germany", 48.137, 11.576, "Europe/Berlin")];
            defaults.ShowClouds = false;
            defaults.ShowWind = true;
            defaults.ShowFeelsLike = true;
            defaults.Language = "de";
            SettingsStore.Save(defaults, path);
            var saved = SettingsStore.Load(path);
            Assert.Equal("Munich", saved.Locations[0].Name);
            Assert.False(saved.ShowClouds);
            Assert.True(saved.ShowWind);
            Assert.True(saved.ShowFeelsLike);
            Assert.Equal("de", saved.Language);

            var oldState = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(path))!.AsObject();
            oldState.Remove("showFeelsLike");
            File.WriteAllText(path, oldState.ToJsonString());
            var migrated = SettingsStore.Load(path);
            Assert.Equal("Munich", migrated.Locations[0].Name);
            Assert.False(migrated.ShowClouds);
            Assert.True(migrated.ShowWind);
            Assert.False(migrated.ShowFeelsLike);
            Assert.Equal("de", migrated.Language);
        }
        finally { File.Delete(path); }
    }

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
        var url = WeatherService.BuildForecastUrl(48.137, 11.576);
        Assert.StartsWith("https://api.open-meteo.com/v1/forecast?", url);
        Assert.Contains("cloud_cover", url);
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
    public void Language_defaults_to_English_and_persists_German_choice()
    {
        var previous = System.Globalization.CultureInfo.CurrentUICulture;
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
        try
        {
            System.Globalization.CultureInfo.CurrentUICulture = new("de-DE");
            Assert.Equal("en", Language.ForLocale());
            Assert.Equal("en", SettingsStore.Load(path).Language);
            var state = SettingsStore.DefaultState();
            state.Language = "de";
            SettingsStore.Save(state, path);
            Assert.Equal("de", SettingsStore.Load(path).Language);
            File.WriteAllText(path, "{\"language\":\"fr\"}");
            Assert.Equal("en", SettingsStore.Load(path).Language);
        }
        finally
        {
            System.Globalization.CultureInfo.CurrentUICulture = previous;
            File.Delete(path);
        }
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
        Assert.Equal(24, ChartRenderer.CloudStripExtra);
        Assert.Equal(0x89, png[0]);
        Assert.Equal((byte)'P', png[1]);
        Assert.Equal((byte)'N', png[2]);
        Assert.Equal((byte)'G', png[3]);
    }

    [Fact]
    public void Hidden_feels_like_does_not_draw_or_change_the_temperature_range()
    {
        var time = new DateTime(2026, 7, 30);
        var forecast = Enumerable.Range(0, 72)
            .Select(i => new HourlyPoint(time.AddHours(i), 20, 19, 0, 0, 0, 5, 0)).ToArray();
        var changed = forecast.Select(point => point with { Apparent = 100 }).ToArray();
        var hidden = ChartRenderer.RenderPng(forecast, "en-US", 680, 250, true, false, false);
        var hiddenChanged = ChartRenderer.RenderPng(changed, "en-US", 680, 250, true, false, false);
        var visible = ChartRenderer.RenderPng(changed, "en-US", 680, 250, true, false, true);
        Assert.Equal(hidden, hiddenChanged);
        Assert.False(hidden.SequenceEqual(visible));
        Assert.Equal(hidden, ChartRenderer.RenderPng(forecast, "en-US", 680, 250));
    }

    [Fact]
    public void Daytime_uses_blue_plot_and_cloud_base_while_night_is_dark()
    {
        var time = new DateTime(2026, 7, 30);
        var forecast = Enumerable.Range(0, 72)
            .Select(i => new HourlyPoint(time.AddHours(i), 20, 19, 0, 0, 0, 5, 0)).ToArray();
        using var bitmap = SKBitmap.Decode(ChartRenderer.RenderPng(
            forecast, "en-US", 680, 250, true, false, false));
        var nightCloud = bitmap.GetPixel(55, 50);
        var dayCloud = bitmap.GetPixel(125, 50);
        Assert.Equal(new SKColor(26, 32, 44), nightCloud);
        Assert.Equal(new SKColor(29, 50, 92), dayCloud);
        var nightPlot = bitmap.GetPixel(55, 100);
        var dayPlot = bitmap.GetPixel(125, 100);
        Assert.True(dayPlot.Blue > nightPlot.Blue);
    }
}
