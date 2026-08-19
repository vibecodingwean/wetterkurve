using Wetterkurve;

var output = args.Length > 0 ? args[0] : "wetterkurve-smoke.png";
using var http = WeatherService.CreateClient();
var payload = await WeatherService.FetchForecastAsync(http, WeatherService.DefaultLocation);
var forecast = WeatherService.ChartForecast(payload);
var condition = WeatherService.WeatherInfo(payload.Current.WeatherCode, "de");
var png = ChartRenderer.RenderPng(forecast, "de-DE", 680, 250);
File.WriteAllBytes(output, png);
Console.WriteLine($"{WeatherService.Round(payload.Current.Temperature)}° {condition.Description}");
Console.WriteLine($"points={forecast.Count} chart={output} bytes={png.Length}");
