using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Windows.Widgets.Providers;
using Windows.Storage.Streams;

namespace Wetterkurve.Widget;

[ComVisible(true)]
[ComDefaultInterface(typeof(IWidgetProvider))]
[Guid("768c14a0-584a-41cb-8b76-45cf3a764a88")]
public sealed class WidgetProvider : IWidgetProvider, IWidgetProvider2, IWidgetResourceProvider
{
    public const string DefinitionId = "Wetterkurve_Widget";
    const string ResourceHost = "https://wetterkurve.widget/";

    static readonly object Gate = new();
    static readonly HttpClient Http = WeatherService.CreateClient();
    static readonly Dictionary<string, WidgetSession> Sessions = new();
    static readonly System.Threading.Timer RefreshTimer = new(
        _ => RefreshAll(), null, TimeSpan.FromSeconds(WeatherService.UpdateSeconds),
        TimeSpan.FromSeconds(WeatherService.UpdateSeconds));

    readonly string _language = Language.ForLocale();
    readonly string _locale;
    AppState _state;

    public WidgetProvider()
    {
        WidgetLog.Write("provider constructed");
        _locale = Language.LocaleTag(_language);
        _state = LoadState();
        Recover();
    }

    public void CreateWidget(WidgetContext widgetContext)
    {
        WidgetLog.Write($"CreateWidget {widgetContext.Id}");
        var session = GetOrCreate(widgetContext.Id, widgetContext.DefinitionId, "");
        Push(session);
        _ = UpdateAsync(session, force: true);
    }

    public void DeleteWidget(string widgetId, string customState)
    {
        lock (Gate)
            Sessions.Remove(widgetId);
    }

    public void OnActionInvoked(WidgetActionInvokedArgs args)
    {
        var session = GetOrCreate(args.WidgetContext.Id, args.WidgetContext.DefinitionId, "");
        var verb = args.Verb ?? "";
        if (verb == "refresh")
        {
            _ = UpdateAsync(session, force: true);
            return;
        }
        if (verb == "exitCustomization")
        {
            session.Customizing = false;
            _ = UpdateAsync(session);
            return;
        }
        if (verb == "search")
        {
            var query = ReadInput(args.Data, "query");
            _ = SearchAsync(session, query);
            return;
        }
        if (verb.StartsWith("select:", StringComparison.Ordinal))
        {
            if (int.TryParse(verb[7..], out var index) && index >= 0 && index < _state.Locations.Count)
            {
                _state.ActiveLocation = index;
                SaveState();
                session.Customizing = false;
                _ = UpdateAsync(session, force: true);
            }
            return;
        }
        if (verb.StartsWith("remove:", StringComparison.Ordinal))
        {
            if (_state.Locations.Count > 1 &&
                int.TryParse(verb[7..], out var index) &&
                index >= 0 && index < _state.Locations.Count)
            {
                _state.Locations.RemoveAt(index);
                _state.ActiveLocation = Math.Min(_state.ActiveLocation, _state.Locations.Count - 1);
                SaveState();
                _ = UpdateAsync(session);
            }
            return;
        }
        if (verb.StartsWith("add:", StringComparison.Ordinal))
        {
            if (int.TryParse(verb[4..], out var index) &&
                index >= 0 && index < session.SearchResults.Count &&
                _state.Locations.Count < WeatherService.MaxLocations)
            {
                var location = session.SearchResults[index];
                if (_state.Locations.All(saved => saved.Id != location.Id))
                {
                    _state.Locations.Add(location);
                    _state.ActiveLocation = _state.Locations.Count - 1;
                    SaveState();
                    session.Customizing = false;
                    session.SearchResults = [];
                    session.SearchHint = Language.Text(_language, "enterTwoLetters");
                    _ = UpdateAsync(session, force: true);
                }
            }
        }
    }

    public void OnWidgetContextChanged(WidgetContextChangedArgs contextChangedArgs)
    {
        if (Sessions.TryGetValue(contextChangedArgs.WidgetContext.Id, out var session))
            Push(session);
    }

    public void Activate(WidgetContext widgetContext)
    {
        var session = GetOrCreate(widgetContext.Id, widgetContext.DefinitionId, "");
        session.Active = true;
        var stale = session.LastUpdatedUtc == default ||
            DateTime.UtcNow - session.LastUpdatedUtc > TimeSpan.FromSeconds(WeatherService.StaleSeconds);
        _ = UpdateAsync(session, force: stale);
    }

    public void Deactivate(string widgetId)
    {
        if (Sessions.TryGetValue(widgetId, out var session))
            session.Active = false;
    }

    public void OnCustomizationRequested(WidgetCustomizationRequestedArgs customizationInvokedArgs)
    {
        var session = GetOrCreate(
            customizationInvokedArgs.WidgetContext.Id,
            customizationInvokedArgs.WidgetContext.DefinitionId,
            "");
        session.Customizing = true;
        session.SearchHint = Language.Text(_language, "enterTwoLetters");
        Push(session);
    }

    public void OnResourceRequested(WidgetResourceRequestedArgs args)
    {
        var deferral = args.GetDeferral();
        try
        {
            var uri = args.Request.Uri;
            WidgetLog.Write($"resource {uri}");
            if (string.IsNullOrEmpty(uri) || !uri.StartsWith(ResourceHost, StringComparison.OrdinalIgnoreCase))
                return;

            var path = uri[ResourceHost.Length..].Split('?', 2)[0];
            byte[]? bytes = null;
            if (path.StartsWith("chart/", StringComparison.OrdinalIgnoreCase))
            {
                var widgetId = Path.GetFileNameWithoutExtension(path);
                if (Sessions.TryGetValue(widgetId, out var session))
                    bytes = session.ChartPng;
            }
            else if (path.StartsWith("icons/", StringComparison.OrdinalIgnoreCase))
            {
                var name = Path.GetFileNameWithoutExtension(path);
                bytes = ReadIcon(name);
            }

            if (bytes is null)
                return;

            var stream = new InMemoryRandomAccessStream();
            stream.WriteAsync(bytes.AsBuffer()).AsTask().GetAwaiter().GetResult();
            stream.Seek(0);
            var response = new WidgetResourceResponse(
                RandomAccessStreamReference.CreateFromStream(stream), "OK", 200);
            response.Headers["Content-Type"] = "image/png";
            args.Response = response;
        }
        finally
        {
            deferral.Complete();
        }
    }

    WidgetSession GetOrCreate(string widgetId, string definitionId, string customState)
    {
        lock (Gate)
        {
            if (Sessions.TryGetValue(widgetId, out var existing))
                return existing;
            var session = new WidgetSession
            {
                WidgetId = widgetId,
                DefinitionId = definitionId,
                CustomState = customState,
            };
            Sessions[widgetId] = session;
            return session;
        }
    }

    void Recover()
    {
        try
        {
            foreach (var info in WidgetManager.GetDefault().GetWidgetInfos())
            {
                GetOrCreate(info.WidgetContext.Id, info.WidgetContext.DefinitionId, info.CustomState);
            }
        }
        catch
        {
        }
    }

    static void RefreshAll()
    {
        List<WidgetSession> sessions;
        lock (Gate)
            sessions = Sessions.Values.ToList();
        foreach (var session in sessions)
        {
            try
            {
                var provider = new WidgetProvider();
                _ = provider.UpdateAsync(session);
            }
            catch
            {
            }
        }
    }

    async Task SearchAsync(WidgetSession session, string query)
    {
        if (query.Trim().Length < 2)
        {
            session.SearchHint = Language.Text(_language, "enterTwoLetters");
            session.SearchResults = [];
            Push(session);
            return;
        }

        session.SearchHint = Language.Text(_language, "searching");
        Push(session);
        try
        {
            session.SearchResults = await WeatherService.SearchLocationsAsync(Http, query, _language);
            session.SearchHint = session.SearchResults.Count == 0
                ? Language.Text(_language, "noLocationsFound")
                : Language.Text(_language, "chooseLocation");
        }
        catch
        {
            session.SearchResults = [];
            session.SearchHint = Language.Text(_language, "locationSearchUnavailable");
        }
        Push(session);
    }

    async Task UpdateAsync(WidgetSession session, bool force = false)
    {
        try
        {
            if (!force && session.LastUpdatedUtc != default &&
                DateTime.UtcNow - session.LastUpdatedUtc < TimeSpan.FromSeconds(60))
            {
                Push(session);
                return;
            }

            var location = CurrentLocation();
            WidgetLog.Write($"fetch {location.Name}");
            try
            {
                var payload = await WeatherService.FetchForecastAsync(Http, location);
                session.Payload = payload;
                session.LastUpdatedUtc = DateTime.UtcNow;
                session.LastError = null;
                var forecast = WeatherService.ChartForecast(payload);
                session.ChartPng = ChartRenderer.RenderPng(forecast, _locale, 560, 168);
                WidgetLog.Write($"fetched {forecast.Count} points chart={session.ChartPng.Length}");
            }
            catch (Exception exception)
            {
                session.LastError = exception.Message;
                WidgetLog.Write($"fetch failed: {exception}");
            }
            Push(session);
        }
        catch (Exception exception)
        {
            WidgetLog.Write($"UpdateAsync failed: {exception}");
        }
    }

    void Push(WidgetSession session)
    {
        try
        {
            var options = new WidgetUpdateRequestOptions(session.WidgetId)
            {
                Template = session.Customizing
                    ? BuildCustomizationCard(session)
                    : session.ChartPng is { Length: > 0 }
                        ? WidgetTemplates.Display
                        : WidgetTemplates.TextOnly,
                Data = session.Customizing ? "{}" : BuildData(session),
                CustomState = session.CustomState,
            };
            WidgetLog.Write($"Push {session.WidgetId} data={options.Data.Length} chart={(session.ChartPng?.Length ?? 0)}");
            WidgetManager.GetDefault().UpdateWidget(options);
        }
        catch (Exception exception)
        {
            WidgetLog.Write($"Push failed: {exception}");
        }
    }

    string BuildData(WidgetSession session)
    {
        var location = CurrentLocation();
        var language = _language;
        var node = new JsonObject
        {
            ["title"] = location.Name,
            ["condition"] = Language.Text(language, "loadingWeather"),
            ["temperature"] = "–°",
            ["stats"] = "",
            ["chartTitle"] = Language.Text(language, "threeDayForecast"),
            ["chartUrl"] = $"{ResourceHost}chart/{session.WidgetId}.png",
            ["status"] = session.LastError is null
                ? Language.Text(language, "loading")
                : session.Payload is null
                    ? Language.Text(language, "weatherUnavailable")
                    : Language.Text(language, "offlineCached"),
            ["refreshLabel"] = Language.Text(language, "refreshWeather"),
        };

        if (session.Payload is { } payload)
        {
            var forecast = WeatherService.ChartForecast(payload);
            var condition = WeatherService.WeatherInfo(payload.Current.WeatherCode, language);
            node["title"] = $"{location.Name}";
            node["condition"] = $"{condition.Symbol} {condition.Description}";
            node["temperature"] = $"{WeatherService.Round(payload.Current.Temperature)}°";
            node["stats"] =
                $"{Language.Text(language, "feelsLike")} {WeatherService.Round(payload.Current.ApparentTemperature)}°   " +
                $"{Language.Text(language, "rain")} {WeatherService.UpcomingRainChance(forecast)} %   " +
                $"{Language.Text(language, "wind")} {WeatherService.Round(payload.Current.WindSpeed)} km/h   " +
                $"{Language.Text(language, "humidity")} {WeatherService.Round(payload.Current.RelativeHumidity)} %";
            if (session.LastError is null)
            {
                var updated = DateTime.Now.ToString("HH:mm",
                    System.Globalization.CultureInfo.GetCultureInfo(_locale));
                node["status"] = Language.Text(language, "updated",
                    new Dictionary<string, string> { ["time"] = updated });
            }
        }

        return node.ToJsonString();
    }

    string BuildCustomizationCard(WidgetSession session)
    {
        var body = new JsonArray
        {
            new JsonObject
            {
                ["type"] = "TextBlock",
                ["text"] = Language.Text(_language, "customize"),
                ["weight"] = "Bolder",
                ["size"] = "Medium",
            },
            new JsonObject
            {
                ["type"] = "TextBlock",
                ["text"] = Language.Text(_language, "savedLocations"),
                ["spacing"] = "Medium",
            },
        };

        for (var i = 0; i < _state.Locations.Count; i++)
        {
            var actions = new JsonArray
            {
                new JsonObject
                {
                    ["type"] = "Action.Execute",
                    ["title"] = _state.Locations[i].Label,
                    ["verb"] = $"select:{i}",
                },
            };
            if (_state.Locations.Count > 1)
            {
                actions.Add(new JsonObject
                {
                    ["type"] = "Action.Execute",
                    ["title"] = Language.Text(_language, "remove"),
                    ["verb"] = $"remove:{i}",
                });
            }
            body.Add(new JsonObject
            {
                ["type"] = "ActionSet",
                ["actions"] = actions,
            });
        }

        if (_state.Locations.Count < WeatherService.MaxLocations)
        {
            body.Add(new JsonObject
            {
                ["type"] = "Input.Text",
                ["id"] = "query",
                ["placeholder"] = Language.Text(_language, "searchLocation"),
            });
            body.Add(new JsonObject
            {
                ["type"] = "TextBlock",
                ["text"] = session.SearchHint,
                ["size"] = "Small",
                ["isSubtle"] = true,
            });
        }

        for (var i = 0; i < session.SearchResults.Count; i++)
        {
            var result = session.SearchResults[i];
            var saved = _state.Locations.Any(location => location.Id == result.Id);
            body.Add(new JsonObject
            {
                ["type"] = "ActionSet",
                ["actions"] = new JsonArray
                {
                    new JsonObject
                    {
                        ["type"] = "Action.Execute",
                        ["title"] = saved
                            ? Language.Text(_language, "saved", new Dictionary<string, string>
                            {
                                ["location"] = result.Label,
                            })
                            : result.Label,
                        ["verb"] = $"add:{i}",
                        ["isEnabled"] = JsonValue.Create(!saved),
                    },
                },
            });
        }

        var actionsBar = new JsonArray();
        if (_state.Locations.Count < WeatherService.MaxLocations)
        {
            actionsBar.Add(new JsonObject
            {
                ["type"] = "Action.Execute",
                ["title"] = Language.Text(_language, "search"),
                ["verb"] = "search",
            });
        }
        actionsBar.Add(new JsonObject
        {
            ["type"] = "Action.Execute",
            ["title"] = Language.Text(_language, "exitCustomize"),
            ["verb"] = "exitCustomization",
        });

        return new JsonObject
        {
            ["type"] = "AdaptiveCard",
            ["version"] = "1.5",
            ["$schema"] = "http://adaptivecards.io/schemas/adaptive-card.json",
            ["body"] = body,
            ["actions"] = actionsBar,
        }.ToJsonString();
    }

    WeatherLocation CurrentLocation()
    {
        if (_state.Locations.Count == 0)
            _state.Locations.Add(WeatherService.DefaultLocation);
        _state.ActiveLocation = Math.Clamp(_state.ActiveLocation, 0, _state.Locations.Count - 1);
        return _state.Locations[_state.ActiveLocation];
    }

    static string ReadInput(string? data, string key)
    {
        if (string.IsNullOrWhiteSpace(data))
            return "";
        try
        {
            using var document = JsonDocument.Parse(data);
            return document.RootElement.TryGetProperty(key, out var value)
                ? value.GetString() ?? ""
                : "";
        }
        catch
        {
            return "";
        }
    }

    static string IconDataUri(string name) => ToDataUri(ReadIcon(name));

    static string ToDataUri(byte[]? png) =>
        png is { Length: > 0 }
            ? "data:image/png;base64," + Convert.ToBase64String(png)
            : "";

    static byte[]? ReadIcon(string name)
    {
        var safe = Path.GetFileName(name);
        var roots = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "WebIcons", $"{safe}.png"),
            Path.Combine(AppContext.BaseDirectory, "Assets", "icons", $"{safe}.png"),
        };
        foreach (var path in roots)
        {
            if (File.Exists(path))
                return File.ReadAllBytes(path);
        }
        return null;
    }

    static string SettingsPath()
    {
        try
        {
            return Path.Combine(Windows.Storage.ApplicationData.Current.LocalFolder.Path, "settings.json");
        }
        catch
        {
            return SettingsStore.DefaultPath;
        }
    }

    static AppState LoadState() => SettingsStore.Load(SettingsPath());

    void SaveState() => SettingsStore.Save(_state, SettingsPath());
}

sealed class WidgetSession
{
    public string WidgetId { get; set; } = "";
    public string DefinitionId { get; set; } = "";
    public string CustomState { get; set; } = "";
    public bool Active { get; set; }
    public bool Customizing { get; set; }
    public ForecastPayload? Payload { get; set; }
    public byte[]? ChartPng { get; set; }
    public DateTime LastUpdatedUtc { get; set; }
    public string? LastError { get; set; }
    public string SearchHint { get; set; } = "";
    public List<WeatherLocation> SearchResults { get; set; } = [];
}
