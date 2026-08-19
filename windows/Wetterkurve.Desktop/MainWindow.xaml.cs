using System.IO;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using System.Runtime.InteropServices;
using Drawing = System.Drawing;
using Forms = System.Windows.Forms;

namespace Wetterkurve.Desktop;

public partial class MainWindow : Window
{
    readonly string _language = Wetterkurve.Language.ForLocale();
    readonly string _locale;
    readonly HttpClient _http = WeatherService.CreateClient();
    readonly DispatcherTimer _refreshTimer = new();
    readonly DispatcherTimer _markerTimer = new();
    readonly DispatcherTimer _searchTimer = new();
    readonly Forms.NotifyIcon _tray = new();
    readonly DispatcherTimer _promoteTimer = new();
    IndicatorWindow? _indicator;
    AppState _state = SettingsStore.Load();
    int _promoteAttempts;
    ForecastPayload? _payload;
    DateTime _lastUpdatedUtc;
    int _forecastRequest;
    int _searchRequest;
    CancellationTokenSource? _forecastCts;
    CancellationTokenSource? _searchCts;
    bool _allowClose;
    Drawing.Icon? _trayIcon;

    public MainWindow()
    {
        _locale = Wetterkurve.Language.LocaleTag(_language);
        InitializeComponent();
        TitleLabel.Text = ActiveLocation.Name;
        ConditionLabel.Text = T("loadingWeather");
        FeelsName.Text = T("feelsLike");
        RainName.Text = T("rain");
        WindName.Text = T("wind");
        HumidityName.Text = T("humidity");
        SectionTitle.Text = T("threeDayForecast");
        LegendLabel.Text = T("chartLegend");
        SearchHint.Text = T("enterTwoLetters");
        StatusLabel.Text = T("loading");
        RefreshButton.ToolTip = T("refreshWeather");
        ApplyEmptyValues();
        RebuildLocationTabs();
        ConfigureTray();
        ConfigureIndicator();
        ConfigureTimers();
        Loaded += async (_, _) => await RefreshAsync(force: true);
        Closing += OnClosing;
        StateChanged += OnStateChanged;
        SizeChanged += (_, _) => RedrawChart();
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.F5)
            {
                e.Handled = true;
                _ = RefreshAsync(true);
            }
        };
    }

    public AppState State => _state;

    WeatherLocation ActiveLocation =>
        _state.Locations[Math.Clamp(_state.ActiveLocation, 0, _state.Locations.Count - 1)];

    string T(string key, IReadOnlyDictionary<string, string>? values = null) =>
        Wetterkurve.Language.Text(_language, key, values);

    void ConfigureTimers()
    {
        _refreshTimer.Interval = TimeSpan.FromSeconds(WeatherService.UpdateSeconds);
        _refreshTimer.Tick += async (_, _) => await RefreshAsync();
        _refreshTimer.Start();
        _markerTimer.Interval = TimeSpan.FromSeconds(60);
        _markerTimer.Tick += (_, _) => RedrawChart();
        _markerTimer.Start();
        _searchTimer.Interval = TimeSpan.FromMilliseconds(WeatherService.SearchDelayMs);
        _searchTimer.Tick += async (_, _) =>
        {
            _searchTimer.Stop();
            await SearchLocationsAsync();
        };
    }

    void ConfigureTray()
    {
        _tray.Icon = CreateTrayIcon(null);
        _tray.Visible = true;
        _tray.Text = "Wetterkurve";
        _tray.MouseClick += (_, e) =>
        {
            if (e.Button == Forms.MouseButtons.Left)
                ToggleFromTray();
        };
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add(T("refreshWeather"), null, async (_, _) => await RefreshAsync(true));
        var topmost = new Forms.ToolStripMenuItem("Immer im Vordergrund")
        {
            Checked = Topmost,
            CheckOnClick = true,
        };
        topmost.CheckedChanged += (_, _) => Topmost = topmost.Checked;
        menu.Items.Add(topmost);
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Beenden", null, (_, _) =>
        {
            _allowClose = true;
            _tray.Visible = false;
            System.Windows.Application.Current.Shutdown();
        });
        _tray.ContextMenuStrip = menu;
        Closed += (_, _) =>
        {
            _promoteTimer.Stop();
            _indicator?.Close();
            _tray.Visible = false;
            _tray.Dispose();
            _trayIcon?.Dispose();
        };
        _promoteTimer.Interval = TimeSpan.FromSeconds(1);
        _promoteTimer.Tick += (_, _) =>
        {
            _promoteAttempts++;
            if (NotifyIconPromoter.Promote(Environment.ProcessPath ?? AppContext.BaseDirectory) ||
                _promoteAttempts >= 20)
                _promoteTimer.Stop();
        };
        _promoteTimer.Start();
    }

    void ConfigureIndicator()
    {
        _indicator = new IndicatorWindow(this);
        _indicator.Update(ActiveLocation.Name, "–°", "unknown");
        _indicator.Show();
    }

    void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (_allowClose)
            return;
        e.Cancel = true;
        Hide();
    }

    void OnStateChanged(object? sender, EventArgs e)
    {
        if (WindowState == WindowState.Minimized)
            Hide();
    }

    public void ToggleFromTray()
    {
        if (IsVisible && WindowState != WindowState.Minimized)
        {
            Hide();
            return;
        }
        RestoreFromTray();
    }

    public void RestoreFromTray()
    {
        Show();
        WindowState = WindowState.Normal;
        Activate();
    }

    void UpdateTray(int temperature, string location)
    {
        _tray.Text = $"{temperature}° {location}".Length <= 63
            ? $"{temperature}° {location}"
            : $"{temperature}°";
        var previous = _trayIcon;
        _trayIcon = CreateTrayIcon(temperature);
        _tray.Icon = _trayIcon;
        previous?.Dispose();
    }

    static Drawing.Icon CreateTrayIcon(int? temperature)
    {
        const int size = 32;
        using var bitmap = new Drawing.Bitmap(size, size);
        using var graphics = Drawing.Graphics.FromImage(bitmap);
        graphics.SmoothingMode = Drawing.Drawing2D.SmoothingMode.AntiAlias;
        graphics.TextRenderingHint = Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        graphics.Clear(Drawing.Color.Transparent);
        using var background = new Drawing.SolidBrush(Drawing.Color.FromArgb(255, 32, 42, 58));
        graphics.FillEllipse(background, 1, 1, size - 3, size - 3);
        var label = temperature is null ? "°" : $"{temperature.Value}";
        var fontSize = label.Length >= 3 ? 11f : 13f;
        using var font = new Drawing.Font("Segoe UI", fontSize, Drawing.FontStyle.Bold, Drawing.GraphicsUnit.Pixel);
        var bounds = graphics.MeasureString(label, font);
        graphics.DrawString(
            label,
            font,
            Drawing.Brushes.White,
            (size - bounds.Width) / 2,
            (size - bounds.Height) / 2);
        var handle = bitmap.GetHicon();
        using var created = Drawing.Icon.FromHandle(handle);
        var clone = (Drawing.Icon)created.Clone();
        DestroyIcon(handle);
        return clone;
    }

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    static extern bool DestroyIcon(IntPtr handle);

    void RebuildLocationTabs()
    {
        LocationTabs.Children.Clear();
        for (var i = 0; i < _state.Locations.Count; i++)
        {
            var index = i;
            var location = _state.Locations[i];
            var button = new Button
            {
                Content = location.Name,
                Tag = index,
                ToolTip = T("selectLocation", new Dictionary<string, string>
                {
                    ["location"] = location.Label,
                }),
            };
            if (index == _state.ActiveLocation)
                button.Background = new SolidColorBrush(Color.FromArgb(0x8C, 0x49, 0x9D, 0xFF));
            button.Click += async (_, _) => await SelectLocationAsync(index);
            LocationTabs.Children.Add(button);
        }

        if (_state.Locations.Count < WeatherService.MaxLocations)
        {
            var add = new Button
            {
                Content = T("addLocation"),
                BorderBrush = new SolidColorBrush(Color.FromArgb(0x66, 0x8E, 0xCD, 0xFF)),
                BorderThickness = new Thickness(1),
                ToolTip = T("addLocationAccessible"),
            };
            add.Click += (_, _) => SetSearchVisible(true);
            LocationTabs.Children.Add(add);
        }

        RemoveLocationButton.Visibility = _state.Locations.Count > 1
            ? Visibility.Visible
            : Visibility.Collapsed;
        RemoveLocationButton.ToolTip = T("removeLocation", new Dictionary<string, string>
        {
            ["location"] = ActiveLocation.Name,
        });
    }

    void SetSearchVisible(bool visible)
    {
        SearchBox.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
        if (!visible)
        {
            _searchRequest++;
            _searchCts?.Cancel();
            SearchEntry.Text = "";
            SearchResults.Children.Clear();
            return;
        }
        SearchHint.Text = T("enterTwoLetters");
        SearchEntry.Focus();
    }

    void SearchEntry_TextChanged(object sender, TextChangedEventArgs e)
    {
        _searchTimer.Stop();
        _searchTimer.Start();
    }

    void SearchEntry_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            _searchTimer.Stop();
            _ = SearchLocationsAsync();
        }
        else if (e.Key == Key.Escape)
        {
            SetSearchVisible(false);
        }
    }

    async Task SearchLocationsAsync()
    {
        if (SearchBox.Visibility != Visibility.Visible)
            return;

        var query = SearchEntry.Text.Trim();
        SearchResults.Children.Clear();
        _searchCts?.Cancel();
        if (query.Length < 2)
        {
            SearchHint.Text = T("enterTwoLetters");
            return;
        }

        var requestId = ++_searchRequest;
        _searchCts = new CancellationTokenSource();
        SearchHint.Text = T("searching");
        try
        {
            var results = await WeatherService.SearchLocationsAsync(
                _http, query, _language, _searchCts.Token);
            if (requestId != _searchRequest)
                return;
            RenderSearchResults(results);
        }
        catch (OperationCanceledException)
        {
        }
        catch
        {
            if (requestId == _searchRequest)
                SearchHint.Text = T("locationSearchUnavailable");
        }
    }

    void RenderSearchResults(IReadOnlyList<WeatherLocation> results)
    {
        SearchResults.Children.Clear();
        if (results.Count == 0)
        {
            SearchHint.Text = T("noLocationsFound");
            return;
        }

        SearchHint.Text = T("chooseLocation");
        foreach (var location in results)
        {
            var alreadySaved = _state.Locations.Any(saved => saved.Id == location.Id);
            var button = new Button
            {
                Content = alreadySaved
                    ? T("saved", new Dictionary<string, string> { ["location"] = location.Label })
                    : location.Label,
                HorizontalContentAlignment = HorizontalAlignment.Left,
                IsEnabled = !alreadySaved,
                Margin = new Thickness(0, 2, 0, 0),
            };
            if (!alreadySaved)
            {
                button.Click += async (_, _) =>
                {
                    if (_state.Locations.Count >= WeatherService.MaxLocations)
                        return;
                    _state.Locations.Add(location);
                    _state.ActiveLocation = _state.Locations.Count - 1;
                    Persist();
                    SetSearchVisible(false);
                    await ApplyActiveLocationAsync();
                };
            }
            SearchResults.Children.Add(button);
        }
    }

    async void RemoveLocation_Click(object sender, RoutedEventArgs e) =>
        await RemoveActiveLocationAsync();

    async void Refresh_Click(object sender, RoutedEventArgs e) =>
        await RefreshAsync(true);

    async Task SelectLocationAsync(int index)
    {
        if (index == _state.ActiveLocation)
            return;
        _state.ActiveLocation = index;
        Persist();
        await ApplyActiveLocationAsync();
    }

    async Task RemoveActiveLocationAsync()
    {
        if (_state.Locations.Count < 2)
            return;
        _state.Locations.RemoveAt(_state.ActiveLocation);
        _state.ActiveLocation = Math.Min(_state.ActiveLocation, _state.Locations.Count - 1);
        Persist();
        await ApplyActiveLocationAsync();
    }

    async Task ApplyActiveLocationAsync()
    {
        _forecastCts?.Cancel();
        _forecastRequest++;
        _payload = null;
        _lastUpdatedUtc = default;
        TitleLabel.Text = ActiveLocation.Name;
        ConditionLabel.Text = T("loadingWeather");
        ApplyEmptyValues();
        SetWeatherIcon("unknown");
        ChartImage.Source = null;
        RebuildLocationTabs();
        await RefreshAsync(true);
    }

    public void Persist() => SettingsStore.Save(_state);

    void ApplyEmptyValues()
    {
        TemperatureLabel.Text = "–°";
        FeelsValue.Text = "–°";
        RainValue.Text = "– %";
        WindValue.Text = "– km/h";
        HumidityValue.Text = "– %";
    }

    async Task RefreshAsync(bool force = false)
    {
        if (!force && _lastUpdatedUtc != default &&
            DateTime.UtcNow - _lastUpdatedUtc < TimeSpan.FromSeconds(60))
            return;

        _forecastCts?.Cancel();
        var requestId = ++_forecastRequest;
        var location = ActiveLocation;
        _forecastCts = new CancellationTokenSource();
        StatusLabel.Text = T("refreshing");
        try
        {
            var payload = await WeatherService.FetchForecastAsync(
                _http, location, _forecastCts.Token);
            if (requestId != _forecastRequest)
                return;
            _payload = payload;
            _lastUpdatedUtc = DateTime.UtcNow;
            Render(payload);
        }
        catch (OperationCanceledException)
        {
        }
        catch
        {
            if (requestId != _forecastRequest)
                return;
            StatusLabel.Text = _payload is null ? T("weatherUnavailable") : T("offlineCached");
            if (_payload is null)
            {
                SetWeatherIcon("unknown");
                ConditionLabel.Text = T("offline");
            }
        }
    }

    void Render(ForecastPayload payload)
    {
        var forecast = WeatherService.ChartForecast(payload);
        var condition = WeatherService.WeatherInfo(payload.Current.WeatherCode, _language);
        var rainChance = WeatherService.UpcomingRainChance(forecast);
        SetWeatherIcon(condition.Icon);
        TitleLabel.Text = ActiveLocation.Name;
        ConditionLabel.Text = condition.Description;
        TemperatureLabel.Text = $"{WeatherService.Round(payload.Current.Temperature)}°";
        FeelsValue.Text = $"{WeatherService.Round(payload.Current.ApparentTemperature)}°";
        RainValue.Text = $"{rainChance} %";
        WindValue.Text = $"{WeatherService.Round(payload.Current.WindSpeed)} km/h";
        HumidityValue.Text = $"{WeatherService.Round(payload.Current.RelativeHumidity)} %";
        var temperature = WeatherService.Round(payload.Current.Temperature);
        UpdateTray(temperature, ActiveLocation.Name);
        _indicator?.Update(ActiveLocation.Name, $"{temperature}°", condition.Icon);
        Title = $"{WeatherService.Round(payload.Current.Temperature)}° {ActiveLocation.Name} · Wetterkurve";
        RedrawChart(forecast);
        Dispatcher.BeginInvoke(() => RedrawChart(forecast), DispatcherPriority.Loaded);
        var updated = DateTime.Now.ToString("HH:mm",
            System.Globalization.CultureInfo.GetCultureInfo(_locale));
        StatusLabel.Text = T("updated", new Dictionary<string, string> { ["time"] = updated });
    }

    void RedrawChart(IReadOnlyList<HourlyPoint>? forecast = null)
    {
        forecast ??= _payload is null ? null : WeatherService.ChartForecast(_payload);
        if (forecast is null || forecast.Count < 2)
            return;

        var width = Math.Max(ChartHost.ActualWidth, ChartImage.ActualWidth);
        var height = Math.Max(ChartHost.ActualHeight, ChartImage.ActualHeight);
        if (width < 80 || height < 80)
        {
            width = 640;
            height = 220;
        }
        var png = ChartRenderer.RenderPng(forecast, _locale, (int)Math.Round(width), (int)Math.Round(height));
        ChartImage.Source = ToBitmap(png);
    }

    void SetWeatherIcon(string name)
    {
        try
        {
            ConditionIcon.Source = new BitmapImage(new Uri(
                $"pack://application:,,,/Assets/icons/{name}.png"));
        }
        catch
        {
            ConditionIcon.Source = null;
        }
    }

    static BitmapImage ToBitmap(byte[] png)
    {
        var image = new BitmapImage();
        using var stream = new MemoryStream(png);
        image.BeginInit();
        image.CacheOption = BitmapCacheOption.OnLoad;
        image.StreamSource = stream;
        image.EndInit();
        image.Freeze();
        return image;
    }
}
