import SwiftUI
import UIKit

enum ChartColors {
    static let white = Color.white
    static let wind = Color(red: 36 / 255, green: 139 / 255, blue: 1)
    static let rain = Color(red: 0.25, green: 0.74, blue: 1)
    static let apparent = Color(red: 0.72, green: 0.88, blue: 1)
    static let temperatureGradient = Gradient(colors: [.blue, .cyan, .mint, .yellow, .orange, .red])
    static let day = Color(red: 28 / 255, green: 50 / 255, blue: 93 / 255)
    static let night = Color(red: 25 / 255, green: 33 / 255, blue: 47 / 255)

    static func cloudBand(night: Bool, cover: Double) -> Color {
        let base = night ? (26.0, 32.0, 44.0) : (29.0, 50.0, 92.0)
        let amount = min(1, max(0, cover / 100))
        return Color(red: (base.0 + (200 - base.0) * amount) / 255,
                     green: (base.1 + (200 - base.1) * amount) / 255,
                     blue: (base.2 + (200 - base.2) * amount) / 255)
    }
}

struct WeatherChart: View {
    let points: [HourPoint]
    let language: String
    let timezone: String
    let showClouds: Bool
    let showWind: Bool
    let showFeelsLike: Bool
    var compact = false
    var widgetLayout = false

    struct Layout {
        let label: CGFloat
        let left: CGFloat
        let right: CGFloat
        let stripBottom: CGFloat
        let cloudTop: CGFloat
        let cloudBottom: CGFloat
        let top: CGFloat
        let bottom: CGFloat
    }

    static func layout(in size: CGSize, compact: Bool, showClouds: Bool, widgetLayout: Bool,
                       low: Double, high: Double, maxWind: Double?) -> Layout? {
        if !widgetLayout {
            guard size.width > 100, size.height > 100 else { return nil }
            let label = min(30, max(18, size.height * 0.075)) * (compact ? 0.67 : 1)
            let left = label * 1.9
            let right = size.width - label * 2.0
            let stripBottom = label * 1.65
            let cloudTop = stripBottom + 4
            let cloudBottom = cloudTop + label * 0.83
            let top = showClouds ? cloudBottom : stripBottom + 4
            let bottom = size.height - label * 1.9
            guard right > left, bottom > top else { return nil }
            return Layout(label: label, left: left, right: right, stripBottom: stripBottom,
                          cloudTop: cloudTop, cloudBottom: cloudBottom, top: top, bottom: bottom)
        }
        guard size.width > 70, size.height > 60 else { return nil }
        let label = min(16, max(12, size.height * 0.06)) * (compact ? 0.82 : 1)
        let temperatureFont = UIFont.systemFont(ofSize: label * 0.8, weight: .medium)
        let temperatureWidth = stride(from: low, through: high, by: 5).map {
            ("\(Int($0))°" as NSString).size(withAttributes: [.font: temperatureFont]).width
        }.max() ?? 0
        let left = max(label * 1.6, label * 0.9 + temperatureWidth / 2 + 2)
        let windFont = UIFont.systemFont(ofSize: label * 0.65, weight: .medium)
        let windWidth = maxWind.map { maximum in
            stride(from: 0.0, through: maximum, by: compact ? 10.0 : 5.0).map {
                ("\(Int($0))" as NSString).size(withAttributes: [.font: windFont]).width
            }.max() ?? 0
        } ?? 0
        let rightMargin = max(label * 1.5, maxWind == nil ? 0 : label * 0.65 + windWidth / 2 + 2)
        let right = size.width - rightMargin
        let stripBottom = label * 1.35
        let cloudTop = stripBottom + 3
        let cloudBottom = cloudTop + label * 0.75
        let top = showClouds ? cloudBottom : stripBottom + 3
        let bottom = size.height - label * 1.5
        guard right > left, bottom > top else { return nil }
        return Layout(label: label, left: left, right: right, stripBottom: stripBottom,
                      cloudTop: cloudTop, cloudBottom: cloudBottom, top: top, bottom: bottom)
    }

    static func temperatureRange(points: [HourPoint], showFeelsLike: Bool) -> (low: Double, high: Double)? {
        guard !points.isEmpty else { return nil }
        let values = points.map(\.temperature) + (showFeelsLike ? points.map(\.apparent) : [])
        var low = floor((values.min() ?? 0) / 5) * 5
        var high = ceil((values.max() ?? 0) / 5) * 5
        if low == high { low -= 5; high += 5 }
        return (low, high)
    }

    var body: some View {
        Canvas { context, size in
            guard points.count > 1, let range = Self.temperatureRange(points: points, showFeelsLike: showFeelsLike) else { return }
            let low = range.low
            let high = range.high
            let maxWind = showWind ? max(20, ceil((points.map(\.wind).max() ?? 0) / 5) * 5) : nil
            guard let layout = Self.layout(in: size, compact: compact, showClouds: showClouds,
                                           widgetLayout: widgetLayout, low: low, high: high, maxWind: maxWind) else { return }
            let label = layout.label
            let left = layout.left
            let right = layout.right
            let stripBottom = layout.stripBottom
            let cloudTop = layout.cloudTop
            let cloudBottom = layout.cloudBottom
            let top = layout.top
            let bottom = layout.bottom
            let width = right - left
            let height = bottom - top
            func x(_ i: Int) -> CGFloat { left + CGFloat(i) * width / CGFloat(points.count - 1) }
            func y(_ value: Double) -> CGFloat { bottom - CGFloat((value - low) / (high - low)) * height }
            var surface = context
            if widgetLayout {
                let panel = CGRect(x: left, y: 2, width: width, height: bottom - 2)
                let radius = min(compact ? 8.0 : 12.0, panel.height / 4)
                surface.clip(to: RoundedRectangle(cornerRadius: radius).path(in: panel))
            }
            surface.fill(Path(CGRect(x: left, y: top, width: width, height: height)), with: .color(ChartColors.day))

            let days = Array(Set(points.map(\.day))).sorted()
            let weekdayFormatter = DateFormatter()
            weekdayFormatter.locale = Locale(identifier: language == "de" ? "de_DE" : "en_US")
            weekdayFormatter.timeZone = TimeZone(secondsFromGMT: 0)
            weekdayFormatter.dateFormat = "EEE"
            var weekdayLabels: [(String, CGFloat)] = []
            var dayBoundaries: [CGFloat] = []
            for (index, day) in days.enumerated() {
                guard let first = points.firstIndex(where: { $0.day == day }), let last = points.lastIndex(where: { $0.day == day }) else { continue }
                let dayLeft = first == 0 ? left : (x(first - 1) + x(first)) / 2
                let dayRight = last == points.count - 1 ? right : (x(last) + x(last + 1)) / 2
                let shade = index.isMultiple(of: 2) ? Color(red: 0.16, green: 0.34, blue: 0.56) : Color(red: 0.10, green: 0.22, blue: 0.39)
                let strip = Path(CGRect(x: dayLeft, y: 2, width: dayRight - dayLeft, height: stripBottom - 2))
                surface.fill(strip, with: .color(shade))
                surface.stroke(strip, with: .color(Color(red: 163 / 255, green: 207 / 255, blue: 1).opacity(82 / 255)), lineWidth: label * 0.05)
                let date = ISO8601DateFormatter().date(from: day + "T12:00:00Z") ?? .now
                weekdayLabels.append((weekdayFormatter.string(from: date), (dayLeft + dayRight) / 2))
                if index > 0 { dayBoundaries.append(dayLeft) }
            }

            for i in 0..<(points.count - 1) {
                let segmentWidth = x(i + 1) - x(i) + 1
                let night = points[i].hour >= 21 || points[i].hour < 6
                if showClouds {
                    let band = Path(CGRect(x: x(i), y: cloudTop, width: segmentWidth, height: cloudBottom - cloudTop))
                    surface.fill(band, with: .color(ChartColors.cloudBand(night: night, cover: points[i].clouds)))
                }
                if night {
                    surface.fill(Path(CGRect(x: x(i), y: top, width: segmentWidth, height: height)), with: .color(ChartColors.night))
                }
            }

            for i in 1..<(points.count - 1) {
                let previousNight = points[i - 1].hour >= 21 || points[i - 1].hour < 6
                let night = points[i].hour >= 21 || points[i].hour < 6
                if previousNight != night {
                    line(&surface, from: CGPoint(x: x(i), y: top), to: CGPoint(x: x(i), y: bottom),
                         color: Color(red: 158 / 255, green: 204 / 255, blue: 1).opacity(209 / 255), width: label * 0.07)
                }
            }
            for boundary in dayBoundaries {
                line(&surface, from: CGPoint(x: boundary, y: stripBottom), to: CGPoint(x: boundary, y: bottom),
                     color: Color(red: 163 / 255, green: 207 / 255, blue: 1).opacity(77 / 255), width: label * 0.05)
            }
            if showClouds {
                line(&surface, from: CGPoint(x: left, y: cloudTop), to: CGPoint(x: right, y: cloudTop),
                     color: Color(red: 220 / 255, green: 220 / 255, blue: 220 / 255).opacity(80 / 255), width: 0.5)
            }

            var tick = low
            while tick <= high {
                let yy = y(tick)
                if !showClouds || tick != high {
                    line(&surface, from: CGPoint(x: left, y: yy), to: CGPoint(x: right, y: yy),
                         color: .white.opacity(48 / 255), width: label * 0.05)
                }
                draw("\(Int(tick))°", at: CGPoint(x: left - label * 0.9, y: yy), font: label * 0.8, color: .white, in: &context)
                tick += 5
            }
            for (weekday, center) in weekdayLabels {
                draw(weekday, at: CGPoint(x: center, y: stripBottom / 2), font: label * 0.82, color: .white, in: &context)
            }

            let maxRain = max(1, points.map(\.rain).max() ?? 1)
            let barWidth = max(1.5, width / CGFloat(points.count) - 2)
            for (i, point) in points.enumerated() {
                let chanceHeight = height * 0.36 * min(100, max(0, point.probability)) / 100
                let rainHeight = height * 0.36 * min(1, max(0, point.rain / maxRain))
                surface.fill(Path(CGRect(x: x(i) - barWidth / 2, y: bottom - chanceHeight, width: barWidth, height: chanceHeight)), with: .color(ChartColors.rain.opacity(0.28)))
                surface.fill(Path(CGRect(x: x(i) - barWidth / 2, y: bottom - rainHeight, width: barWidth, height: rainHeight)), with: .color(ChartColors.rain.opacity(0.95)))
            }

            var apparent = Path()
            var temperature = Path()
            for (i, point) in points.enumerated() {
                let t = CGPoint(x: x(i), y: y(point.temperature))
                if i == 0 { temperature.move(to: t) }
                else { temperature.addLine(to: t) }
                if showFeelsLike {
                    let a = CGPoint(x: x(i), y: y(point.apparent))
                    if i == 0 { apparent.move(to: a) } else { apparent.addLine(to: a) }
                }
            }
            if showFeelsLike {
                surface.stroke(apparent, with: .color(ChartColors.apparent), style: StrokeStyle(lineWidth: compact ? 1.5 : 2.3, dash: [4, 4]))
            }
            surface.stroke(temperature, with: .linearGradient(ChartColors.temperatureGradient, startPoint: CGPoint(x: left, y: bottom), endPoint: CGPoint(x: left, y: top)), style: StrokeStyle(lineWidth: compact ? 2.2 : 3.5, lineCap: .round, lineJoin: .round))

            if showWind {
                let windLimit = maxWind ?? 20
                func windY(_ value: Double) -> CGFloat { bottom - CGFloat(value / windLimit) * height }
                var windPath = Path()
                for (i, point) in points.enumerated() {
                    let p = CGPoint(x: x(i), y: windY(point.wind))
                    if i == 0 { windPath.move(to: p) } else { windPath.addLine(to: p) }
                }
                surface.stroke(windPath, with: .color(ChartColors.wind), style: StrokeStyle(lineWidth: compact ? 1.5 : 2.6, lineCap: .round, lineJoin: .round))
                var windTick = 0.0
                while windTick <= windLimit {
                    draw("\(Int(windTick))", at: CGPoint(x: right + label * 0.65, y: windY(windTick)), font: label * 0.65, color: ChartColors.wind, in: &context)
                    windTick += compact ? 10 : 5
                }
            }

            for (i, point) in points.enumerated() where point.hour % 6 == 0 {
                line(&context, from: CGPoint(x: x(i), y: bottom), to: CGPoint(x: x(i), y: bottom + label * 0.3), color: .white.opacity(0.45), width: 1)
                draw("\(point.hour)", at: CGPoint(x: x(i), y: bottom + label * 0.9), font: label * 0.7, color: .white, in: &context)
            }
            if let (currentDay, currentHour) = localTimeForNow(), let index = points.firstIndex(where: { $0.day == currentDay && $0.hour >= currentHour }) {
                line(&surface, from: CGPoint(x: x(index), y: top), to: CGPoint(x: x(index), y: bottom), color: Color(red: 0.45, green: 1, blue: 0.12), width: compact ? 1 : 2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(language == "de" ? "Dreitägige Wetterkurve" : "Three-day weather chart")
        .accessibilityValue(accessibilitySummary)
        .accessibilityIdentifier("weatherChart")
    }

    private var accessibilitySummary: String {
        let de = language == "de"
        let locale = Locale(identifier: de ? "de_DE" : "en_US")
        let weekdayFormatter = DateFormatter()
        weekdayFormatter.locale = locale
        weekdayFormatter.timeZone = TimeZone(secondsFromGMT: 0)
        weekdayFormatter.dateFormat = "EEE"
        return Array(Set(points.map(\.day))).sorted().compactMap { day in
            let hours = points.filter { $0.day == day }
            guard !hours.isEmpty else { return nil }
            let date = ISO8601DateFormatter().date(from: day + "T12:00:00Z") ?? .now
            let name = weekdayFormatter.string(from: date)
            let low = WeatherData.rounded(hours.map(\.temperature).min() ?? 0)
            let high = WeatherData.rounded(hours.map(\.temperature).max() ?? 0)
            let chance = WeatherData.rounded(hours.map(\.probability).max() ?? 0)
            let rain = hours.map(\.rain).reduce(0, +).formatted(.number.precision(.fractionLength(1)).locale(locale))
            var result = de ? "\(name): \(low) bis \(high) Grad, Regenwahrscheinlichkeit bis \(chance) Prozent, Niederschlag \(rain) Millimeter" : "\(name): \(low) to \(high) degrees, rain chance up to \(chance) percent, precipitation \(rain) millimeters"
            if showClouds {
                let clouds = WeatherData.rounded(hours.map(\.clouds).max() ?? 0)
                result += de ? ", Wolken bis \(clouds) Prozent" : ", clouds up to \(clouds) percent"
            }
            if showWind {
                let wind = WeatherData.rounded(hours.map(\.wind).max() ?? 0)
                result += de ? ", Wind bis \(wind) Kilometer pro Stunde" : ", wind up to \(wind) kilometers per hour"
            }
            return result
        }.joined(separator: "; ")
    }

    private func localTimeForNow() -> (String, Int)? {
        guard let zone = TimeZone(identifier: timezone) else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd'T'HH"
        let parts = formatter.string(from: .now).split(separator: "T")
        guard parts.count == 2, let hour = Int(parts[1]) else { return nil }
        return (String(parts[0]), hour)
    }

    private func draw(_ value: String, at point: CGPoint, font: CGFloat, color: Color, in context: inout GraphicsContext) {
        context.draw(Text(value).font(.system(size: font, weight: .medium)).foregroundStyle(color), at: point)
    }

    private func line(_ context: inout GraphicsContext, from start: CGPoint, to end: CGPoint, color: Color, width: CGFloat) {
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        context.stroke(path, with: .color(color), lineWidth: width)
    }
}
