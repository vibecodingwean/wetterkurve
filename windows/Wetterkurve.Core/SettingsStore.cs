using System.Text.Json;

namespace Wetterkurve;

public static class SettingsStore
{
    public static string DefaultDirectory => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Wetterkurve");

    public static string DefaultPath => Path.Combine(DefaultDirectory, "settings.json");

    public static AppState Load(string? path = null)
    {
        path ??= DefaultPath;
        try
        {
            if (!File.Exists(path))
                return DefaultState();
            var json = File.ReadAllText(path);
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            var locationsJson = root.TryGetProperty("locations", out var locations)
                ? locations.GetRawText()
                : "[]";
            var parsed = WeatherService.ParseLocations(locationsJson, [WeatherService.DefaultLocation]);
            var active = 0;
            if (root.TryGetProperty("activeLocation", out var activeElement))
                active = activeElement.GetInt32();
            double? indicatorLeft = root.TryGetProperty("indicatorLeft", out var leftElement) &&
                leftElement.ValueKind == JsonValueKind.Number
                ? leftElement.GetDouble()
                : null;
            double? indicatorTop = root.TryGetProperty("indicatorTop", out var topElement) &&
                topElement.ValueKind == JsonValueKind.Number
                ? topElement.GetDouble()
                : null;
            return new AppState
            {
                Locations = parsed,
                ActiveLocation = Math.Clamp(active, 0, parsed.Count - 1),
                IndicatorLeft = indicatorLeft,
                IndicatorTop = indicatorTop,
            };
        }
        catch
        {
            return DefaultState();
        }
    }

    public static void Save(AppState state, string? path = null)
    {
        path ??= DefaultPath;
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var json = JsonSerializer.Serialize(new
        {
            locations = JsonSerializer.Deserialize<JsonElement>(WeatherService.SerializeLocations(state.Locations)),
            activeLocation = state.ActiveLocation,
            indicatorLeft = state.IndicatorLeft,
            indicatorTop = state.IndicatorTop,
        });
        File.WriteAllText(path, json);
    }

    public static AppState DefaultState() => new()
    {
        Locations = [WeatherService.DefaultLocation],
        ActiveLocation = 0,
    };
}
