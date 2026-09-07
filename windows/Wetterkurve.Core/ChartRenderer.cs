using SkiaSharp;

namespace Wetterkurve;

public static class ChartRenderer
{
    static readonly (double Temperature, float R, float G, float B)[] TemperatureColors =
    [
        (-15, 0.36f, 0.55f, 1.00f),
        (-5, 0.20f, 0.78f, 1.00f),
        (5, 0.20f, 0.88f, 0.76f),
        (15, 0.55f, 0.86f, 0.25f),
        (22, 1.00f, 0.84f, 0.16f),
        (28, 1.00f, 0.48f, 0.12f),
        (35, 1.00f, 0.20f, 0.16f),
    ];

    static readonly (float R, float G, float B, float A)[] DayStripColors =
    [
        (0.16f, 0.34f, 0.56f, 0.82f),
        (0.10f, 0.22f, 0.39f, 0.88f),
    ];

    public static byte[] RenderPng(
        IReadOnlyList<HourlyPoint> forecast,
        string locale,
        int width,
        int height,
        bool showClouds = true,
        bool showWind = true)
    {
        using var surface = SKSurface.Create(new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Premul));
        var canvas = surface.Canvas;
        canvas.Clear(new SKColor(8, 12, 22, 77));
        Paint(canvas, forecast, locale, width, height, showClouds, showWind);
        using var image = surface.Snapshot();
        using var data = image.Encode(SKEncodedImageFormat.Png, 90);
        return data.ToArray();
    }

    public static void Paint(
        SKCanvas canvas,
        IReadOnlyList<HourlyPoint> data,
        string locale,
        int width,
        int height,
        bool showClouds = true,
        bool showWind = true)
    {
        if (data.Count < 2 || width < 100 || height < 100)
            return;

        const float stripBottom = 30;
        const float plotTopGap = 10;
        const float cloudStripHeight = 24;
        var cloudTop = stripBottom + plotTopGap;
        var cloudBottom = cloudTop + cloudStripHeight;
        var plot = new SKRect(
            36,
            showClouds ? cloudBottom : stripBottom + plotTopGap,
            width - 16,
            height - 42);
        var plotWidth = plot.Width;
        var plotHeight = plot.Height;
        var temperatures = data.SelectMany(point => new[] { point.Temperature, point.Apparent }).ToList();
        var minTemp = Math.Floor(temperatures.Min() / 5) * 5;
        var maxTemp = Math.Ceiling(temperatures.Max() / 5) * 5;
        if (minTemp.Equals(maxTemp))
        {
            minTemp -= 5;
            maxTemp += 5;
        }

        float X(int index) => plot.Left + index * plotWidth / (data.Count - 1);
        float Y(double value) => plot.Bottom - (float)((value - minTemp) * plotHeight / (maxTemp - minTemp));

        using (var plotFill = new SKPaint { Color = new SKColor(10, 14, 23, 82), IsAntialias = true })
            canvas.DrawRect(plot, plotFill);

        DrawDayStrip(canvas, data, plot, locale);
        if (showClouds)
            DrawCloudStrip(canvas, data, X, cloudTop, cloudBottom);

        for (var i = 0; i < data.Count - 1; i++)
        {
            var hour = data[i].Time.Hour;
            if (hour is >= 21 or < 6)
            {
                using var night = new SKPaint { Color = new SKColor(33, 74, 158, 107), IsAntialias = false };
                canvas.DrawRect(new SKRect(X(i), plot.Top, X(i + 1) + 1, plot.Bottom), night);
            }
        }

        for (var i = 1; i < data.Count - 1; i++)
        {
            var previousHour = data[i - 1].Time.Hour;
            var hour = data[i].Time.Hour;
            var wasNight = previousHour is >= 21 or < 6;
            var isNight = hour is >= 21 or < 6;
            if (wasNight == isNight)
                continue;
            using var twilight = new SKPaint
            {
                Color = new SKColor(158, 204, 255, 209),
                StrokeWidth = 1.5f,
                IsStroke = true,
                IsAntialias = true,
            };
            canvas.DrawLine(X(i), plot.Top, X(i), plot.Bottom, twilight);
        }

        using var labelPaint = new SKPaint
        {
            Color = SKColors.White,
            TextSize = 10,
            IsAntialias = true,
            Typeface = Typeface(),
        };
        using var gridPaint = new SKPaint
        {
            Color = new SKColor(255, 255, 255, 48),
            StrokeWidth = 1,
            IsStroke = true,
            IsAntialias = true,
        };
        for (var value = minTemp; value <= maxTemp; value += 5)
        {
            var gridY = Y(value);
            var hideTopGrid = showClouds && value.Equals(maxTemp);
            if (!hideTopGrid)
                canvas.DrawLine(plot.Left, gridY, plot.Right, gridY, gridPaint);
            canvas.DrawText($"{value:0}°", 2, gridY + 3, labelPaint);
        }

        var maxRain = Math.Max(1, data.Max(point => point.Precipitation));
        var barWidth = Math.Max(2, plotWidth / data.Count - 1);
        using var probabilityPaint = new SKPaint { Color = new SKColor(77, 171, 255, 69), IsAntialias = true };
        using var rainPaint = new SKPaint { Color = new SKColor(64, 189, 255, 240), IsAntialias = true };
        for (var i = 0; i < data.Count; i++)
        {
            var point = data[i];
            var probabilityHeight = plotHeight * 0.36f * (float)point.PrecipitationProbability / 100f;
            if (probabilityHeight > 0)
            {
                canvas.DrawRect(
                    new SKRect(X(i) - barWidth / 2, plot.Bottom - probabilityHeight, X(i) + barWidth / 2, plot.Bottom),
                    probabilityPaint);
            }
            if (point.Precipitation > 0)
            {
                var rainHeight = plotHeight * 0.36f * (float)(point.Precipitation / maxRain);
                canvas.DrawRect(
                    new SKRect(X(i) - barWidth / 2, plot.Bottom - rainHeight, X(i) + barWidth / 2, plot.Bottom),
                    rainPaint);
            }
        }

        var gradientStart = Y(TemperatureColors[0].Temperature);
        var gradientEnd = Y(TemperatureColors[^1].Temperature);
        var range = TemperatureColors[^1].Temperature - TemperatureColors[0].Temperature;
        var colors = TemperatureColors.Select(stop => new SKColor(
            (byte)(stop.R * 255),
            (byte)(stop.G * 255),
            (byte)(stop.B * 255))).ToArray();
        var positions = TemperatureColors.Select(stop =>
            (float)((stop.Temperature - TemperatureColors[0].Temperature) / range)).ToArray();
        using var temperatureShader = SKShader.CreateLinearGradient(
            new SKPoint(0, gradientStart),
            new SKPoint(0, gradientEnd),
            colors,
            positions,
            SKShaderTileMode.Clamp);

        StrokeSmoothLine(canvas, data.Select(point => point.Apparent).ToList(), X, Y,
            new SKColor(5, 10, 20, 173), 4.5f, dashed: true);
        StrokeSmoothLine(canvas, data.Select(point => point.Apparent).ToList(), X, Y,
            new SKColor(184, 224, 255, 235), 2f, dashed: true);
        StrokeSmoothLine(canvas, data.Select(point => point.Temperature).ToList(), X, Y,
            new SKColor(5, 10, 20, 209), 5.75f, dashed: false);
        using (var temperaturePaint = new SKPaint
        {
            Shader = temperatureShader,
            StrokeWidth = 3.25f,
            IsStroke = true,
            StrokeCap = SKStrokeCap.Round,
            StrokeJoin = SKStrokeJoin.Round,
            IsAntialias = true,
            Style = SKPaintStyle.Stroke,
        })
        {
            canvas.DrawPath(BuildSmoothPath(data.Select(point => point.Temperature).ToList(), X, Y), temperaturePaint);
        }

        if (showWind)
        {
            var maxWind = Math.Max(20, Math.Ceiling(data.Max(point => point.Wind) / 5) * 5);
            float YWind(double value) => plot.Bottom - (float)(value / maxWind * plotHeight);
            StrokeSmoothLine(canvas, data.Select(point => point.Wind).ToList(), X, YWind,
                new SKColor(0, 20, 12, 180), 3.5f, dashed: false);
            StrokeSmoothLine(canvas, data.Select(point => point.Wind).ToList(), X, YWind,
                new SKColor(0, 226, 114, 255), 2f, dashed: false);
            using var windLabel = new SKPaint
            {
                Color = new SKColor(0, 226, 114, 255),
                TextSize = 10,
                IsAntialias = true,
                Typeface = Typeface(),
            };
            for (var tick = 0.0; tick <= maxWind; tick += 5)
                canvas.DrawText($"{tick:0}", plot.Right + 4, YWind(tick) + 3, windLabel);
        }

        using var hourPaint = new SKPaint
        {
            Color = SKColors.White,
            TextSize = 10,
            IsAntialias = true,
            Typeface = Typeface(),
        };
        for (var i = 0; i < data.Count; i++)
        {
            var hour = data[i].Time.Hour;
            if (hour % 6 != 0)
                continue;
            var text = $"{hour:00} h";
            var textWidth = hourPaint.MeasureText(text);
            canvas.DrawText(text, X(i) - textWidth / 2, height - 19, hourPaint);
        }

        var firstTime = data[0].Time.Ticks;
        var lastTime = data[^1].Time.Ticks;
        var now = DateTime.Now.Ticks;
        if (now >= firstTime && now <= lastTime && lastTime != firstTime)
        {
            var nowX = plot.Left + (float)(now - firstTime) / (lastTime - firstTime) * plotWidth;
            using var glow = new SKPaint
            {
                Color = new SKColor(89, 255, 31, 61),
                StrokeWidth = 9,
                IsStroke = true,
                StrokeCap = SKStrokeCap.Round,
                IsAntialias = true,
            };
            using var marker = new SKPaint
            {
                Color = new SKColor(115, 255, 31, 255),
                StrokeWidth = 3,
                IsStroke = true,
                StrokeCap = SKStrokeCap.Round,
                IsAntialias = true,
            };
            canvas.DrawLine(nowX, plot.Top, nowX, plot.Bottom, glow);
            canvas.DrawLine(nowX, plot.Top, nowX, plot.Bottom, marker);
        }
    }

    static void DrawCloudStrip(
        SKCanvas canvas,
        IReadOnlyList<HourlyPoint> data,
        Func<int, float> x,
        float cloudTop,
        float cloudBottom)
    {
        ReadOnlySpan<int> dayPlot = [26, 32, 44];
        ReadOnlySpan<int> nightPlot = [29, 50, 92];
        ReadOnlySpan<int> cloudGray = [200, 200, 200];
        for (var i = 0; i < data.Count - 1; i++)
        {
            var hour = data[i].Time.Hour;
            var night = hour is >= 21 or < 6;
            var cover = (float)(Math.Clamp(data[i].CloudCover, 0, 100) / 100.0);
            var basis = night ? nightPlot : dayPlot;
            var r = (byte)(basis[0] + (cloudGray[0] - basis[0]) * cover);
            var g = (byte)(basis[1] + (cloudGray[1] - basis[1]) * cover);
            var b = (byte)(basis[2] + (cloudGray[2] - basis[2]) * cover);
            using var fill = new SKPaint { Color = new SKColor(r, g, b), IsAntialias = false };
            canvas.DrawRect(new SKRect(x(i), cloudTop, x(i + 1) + 1, cloudBottom), fill);
        }
        using var border = new SKPaint
        {
            Color = new SKColor(220, 220, 220, 80),
            StrokeWidth = 1,
            IsStroke = true,
            IsAntialias = true,
        };
        canvas.DrawLine(x(0), cloudTop, x(data.Count - 1), cloudTop, border);
    }

    static void DrawDayStrip(SKCanvas canvas, IReadOnlyList<HourlyPoint> data, SKRect plot, string locale)
    {
        const float stripTop = 6;
        const float stripBottom = 30;
        var stripHeight = stripBottom - stripTop;
        var days = WeatherService.ChartDaySegments(data, locale);
        using var textPaint = new SKPaint
        {
            Color = new SKColor(255, 255, 255, 245),
            TextSize = 11,
            IsAntialias = true,
            Typeface = Typeface(SKFontStyleWeight.Bold),
        };
        using var border = new SKPaint
        {
            Color = new SKColor(163, 207, 255, 82),
            StrokeWidth = 1,
            IsStroke = true,
            IsAntialias = true,
        };
        using var divider = new SKPaint
        {
            Color = new SKColor(163, 207, 255, 77),
            StrokeWidth = 1,
            IsStroke = true,
            IsAntialias = true,
        };

        for (var index = 0; index < days.Count; index++)
        {
            var day = days[index];
            var left = plot.Left + day.Start / (float)data.Count * plot.Width;
            var right = plot.Left + day.End / (float)data.Count * plot.Width;
            var color = DayStripColors[index % DayStripColors.Length];
            using var fill = new SKPaint
            {
                Color = new SKColor(
                    (byte)(color.R * 255),
                    (byte)(color.G * 255),
                    (byte)(color.B * 255),
                    (byte)(color.A * 255)),
                IsAntialias = true,
            };
            var rect = new SKRect(left, stripTop, right, stripBottom);
            canvas.DrawRect(rect, fill);
            canvas.DrawRect(rect, border);

            var textWidth = textPaint.MeasureText(day.Label);
            var textX = left + (right - left - textWidth) / 2;
            var fontMetrics = textPaint.FontMetrics;
            var textY = stripTop + (stripHeight - (fontMetrics.Descent - fontMetrics.Ascent)) / 2 - fontMetrics.Ascent;
            canvas.DrawText(day.Label, textX, textY, textPaint);

            if (index > 0)
                canvas.DrawLine(left, stripBottom, left, plot.Bottom, divider);
        }
    }

    static void StrokeSmoothLine(
        SKCanvas canvas,
        IReadOnlyList<double> values,
        Func<int, float> x,
        Func<double, float> y,
        SKColor color,
        float width,
        bool dashed)
    {
        using var paint = new SKPaint
        {
            Color = color,
            StrokeWidth = width,
            IsStroke = true,
            StrokeCap = SKStrokeCap.Round,
            StrokeJoin = SKStrokeJoin.Round,
            IsAntialias = true,
            Style = SKPaintStyle.Stroke,
            PathEffect = dashed ? SKPathEffect.CreateDash([5, 5], 0) : null,
        };
        canvas.DrawPath(BuildSmoothPath(values, x, y), paint);
    }

    static SKPath BuildSmoothPath(IReadOnlyList<double> values, Func<int, float> x, Func<double, float> y)
    {
        var path = new SKPath();
        path.MoveTo(x(0), y(values[0]));
        for (var i = 0; i < values.Count - 1; i++)
        {
            var currentX = x(i);
            var nextX = x(i + 1);
            var controlOffset = (nextX - currentX) * 0.45f;
            path.CubicTo(
                currentX + controlOffset, y(values[i]),
                nextX - controlOffset, y(values[i + 1]),
                nextX, y(values[i + 1]));
        }
        return path;
    }

    static SKTypeface Typeface(SKFontStyleWeight weight = SKFontStyleWeight.Normal)
    {
        return SKTypeface.FromFamilyName("Segoe UI", weight, SKFontStyleWidth.Normal, SKFontStyleSlant.Upright)
            ?? SKTypeface.FromFamilyName("DejaVu Sans", weight, SKFontStyleWidth.Normal, SKFontStyleSlant.Upright)
            ?? SKTypeface.Default;
    }
}
