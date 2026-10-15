import AppIntents
import SwiftUI
import WidgetKit

struct WidgetPlace: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Saved place"
    static var defaultQuery = PlaceQuery()
    var id: String
    var name: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct PlaceQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [WidgetPlace] {
        SharedStore.preferences().places.filter { identifiers.contains($0.id) }.map { WidgetPlace(id: $0.id, name: $0.name) }
    }

    func suggestedEntities() async throws -> [WidgetPlace] {
        SharedStore.preferences().places.map { WidgetPlace(id: $0.id, name: $0.name) }
    }
}

struct PlaceIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Wetterkurve place"
    static var description = IntentDescription("Choose one of your saved places.")
    @Parameter(title: "Place") var place: WidgetPlace?
}

struct WeatherEntry: TimelineEntry {
    let date: Date
    let place: Place
    let preferences: Preferences
    let cached: CachedForecast?
}

struct WeatherProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> WeatherEntry {
        WeatherEntry(date: .now, place: .berlin, preferences: Preferences(), cached: nil)
    }

    func snapshot(for configuration: PlaceIntent, in context: Context) async -> WeatherEntry {
        let preferences = SharedStore.preferences()
        let place = preferences.widgetPlace(configuredID: configuration.place?.id)
        return WeatherEntry(date: .now, place: place ?? .berlin, preferences: preferences,
                            cached: place.flatMap(SharedStore.cached))
    }

    func timeline(for configuration: PlaceIntent, in context: Context) async -> Timeline<WeatherEntry> {
        let preferences = SharedStore.preferences()
        guard let place = preferences.widgetPlace(configuredID: configuration.place?.id) else {
            return emptyTimeline(preferences)
        }
        var cache = SharedStore.cached(place)
        let needsRefresh = cache.map { Date.now.timeIntervalSince($0.fetchedAt) >= 600 || !WeatherData.startsToday($0.forecast, for: place) } ?? true
        if needsRefresh {
            if let forecast = try? await WeatherAPI.forecast(for: place),
               SharedStore.preferences().widgetPlace(configuredID: configuration.place?.id)?.id == place.id {
                SharedStore.save(forecast, for: place)
                cache = SharedStore.cached(place)
            }
        }
        let latestPreferences = SharedStore.preferences()
        guard let latestPlace = latestPreferences.widgetPlace(configuredID: configuration.place?.id) else {
            return emptyTimeline(latestPreferences)
        }
        if latestPlace.id != place.id { cache = SharedStore.cached(latestPlace) }
        let now = Date.now
        let entries = WeatherData.timelineDates(for: cache, place: latestPlace, now: now).map {
            WeatherEntry(date: $0, place: latestPlace, preferences: latestPreferences, cached: cache)
        }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(20 * 60)))
    }

    private func emptyTimeline(_ preferences: Preferences) -> Timeline<WeatherEntry> {
        Timeline(entries: [WeatherEntry(date: .now, place: .berlin, preferences: preferences, cached: nil)], policy: .never)
    }

}

private struct WeatherWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetContentMargins) private var contentMargins
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let entry: WeatherEntry

    private var de: Bool { entry.preferences.language == "de" }
    private var noPlace: Bool { entry.preferences.places.isEmpty }
    private var expired: Bool { entry.cached.map { !WeatherData.startsToday($0.forecast, for: entry.place, at: entry.date) } ?? false }
    private var current: Current? { expired ? nil : entry.cached?.forecast.current }
    private var condition: (symbol: String, text: String) { WeatherData.condition(current?.code ?? -1, language: entry.preferences.language) }
    private var temperature: String { current.map { "\(WeatherData.rounded($0.temperature))°" } ?? "–°" }
    private var chartPoints: [HourPoint]? {
        guard !expired, let forecast = entry.cached?.forecast else { return nil }
        return try? WeatherData.points(forecast)
    }
    private var nextHours: [WeatherData.HourPreview] {
        guard !expired, let forecast = entry.cached?.forecast else { return [] }
        return WeatherData.nextHours(forecast, for: entry.place, at: entry.date)
    }
    private var todayRange: String? {
        guard let points = chartPoints else { return nil }
        let temperatures = points.prefix(24).map(\.temperature)
        guard let low = temperatures.min(), let high = temperatures.max() else { return nil }
        return "\(WeatherData.rounded(low))°/\(WeatherData.rounded(high))° C"
    }
    private var todaySummary: String? {
        guard let points = chartPoints else { return nil }
        let temperatures = points.prefix(24).map(\.temperature)
        guard let low = temperatures.min(), let high = temperatures.max() else { return nil }
        return "H:\(WeatherData.rounded(high))° \(de ? "T" : "L"):\(WeatherData.rounded(low))°"
    }
    private var stale: Bool { entry.cached.map { entry.date.timeIntervalSince($0.fetchedAt) >= 600 || expired } ?? false }
    private var staleText: String {
        guard let cache = entry.cached else { return "" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: de ? "de_DE" : "en_US")
        formatter.timeZone = TimeZone(identifier: cache.forecast.timezone ?? entry.place.timezone) ?? .current
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return (expired ? (de ? "Abgelaufen: " : "Expired: ") : (de ? "Stand: " : "Saved: ")) + formatter.string(from: cache.fetchedAt)
    }
    private var footerText: String {
        if stale { return "Open-Meteo · \(staleText)" }
        guard let fetched = entry.cached?.fetchedAt else { return "Open-Meteo" }
        return "Open-Meteo · \(de ? "Aktualisiert" : "Updated") \(fetched.formatted(date: .omitted, time: .shortened))"
    }

    var body: some View {
        Group {
            if noPlace { emptyState }
            else if family == .systemSmall { small }
            else if family == .systemMedium { hourlyCard }
            else { chart }
        }
        .dynamicTypeSize(family == .systemMedium ? .large : dynamicTypeSize)
        .containerBackground(for: .widget) {
            if family != .systemLarge {
                LinearGradient(colors: [Color(red: 0, green: 0.49, blue: 0.72),
                                        Color(red: 0.39, green: 0.70, blue: 0.84)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            } else {
                Color(red: 0.125, green: 0.165, blue: 0.227)
            }
        }
        .foregroundStyle(.white)
        .widgetURL(URL(string: "wetterkurve://open"))
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Wetterkurve").font(.headline)
            Spacer(minLength: 0)
            Text(de ? "Ort in der App hinzufügen" : "Add a place in the app")
                .font(.subheadline).lineLimit(2)
        }
        .padding(12)
        .padding(contentMargins)
        .accessibilityElement(children: .combine)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(entry.place.name).font(.headline).lineLimit(1)
                Spacer()
                Image(systemName: condition.symbol).symbolRenderingMode(.multicolor).font(.title2)
            }
            Text(temperature).font(.system(size: 43, weight: .light, design: .rounded)).minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            Text(stale ? staleText : (current == nil ? (de ? "Wetter nicht verfügbar" : "Weather unavailable") : condition.text))
                .font(.caption).foregroundStyle(.white.opacity(0.78)).lineLimit(2)
            if let todaySummary { Text(todaySummary).font(.caption2) }
        }
        .padding(12)
        .padding(contentMargins)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(entry.place.name), \(temperature), \(current == nil ? (de ? "Wetter nicht verfügbar" : "Weather unavailable") : condition.text)\(stale ? ", \(staleText)" : "")")
    }

    private var hourlyCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.place.name).font(.system(size: 18, weight: .semibold)).lineLimit(1)
                    Text(temperature).font(.system(size: 43, weight: .light, design: .rounded))
                }
                Spacer(minLength: 6)
                VStack(alignment: .trailing, spacing: 1) {
                    Image(systemName: condition.symbol).symbolRenderingMode(.multicolor).font(.title3)
                    Text(current == nil ? (de ? "Wetter nicht verfügbar" : "Weather unavailable") : condition.text)
                        .font(.system(size: 13, weight: .medium)).lineLimit(1)
                    if let todaySummary { Text(todaySummary).font(.system(size: 11)) }
                }
            }
            Spacer(minLength: 0)
            if nextHours.isEmpty {
                Text(expired ? (de ? "Vorhersage abgelaufen" : "Forecast expired") : (de ? "Keine Stunden verfügbar" : "No hours available"))
                    .font(.caption)
            } else {
                HStack(spacing: 0) {
                    ForEach(Array(nextHours.enumerated()), id: \.offset) { item in
                        let hour = item.element
                        VStack(spacing: 3) {
                            Text("\(hour.hour)\(de ? " UHR" : ":00")").font(.system(size: 10, weight: .medium))
                            Image(systemName: WeatherData.condition(hour.code, language: entry.preferences.language).symbol)
                                .symbolRenderingMode(.multicolor).font(.system(size: 20))
                            Text("\(hour.temperature)°").font(.system(size: 13, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            Text(footerText)
                .font(.system(size: 9))
                .foregroundStyle(.white.opacity(0.82))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
    }

    private var chart: some View {
        VStack(alignment: .leading, spacing: family == .systemMedium ? 2 : 4) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: family == .systemLarge ? 3 : 1) {
                    Text(entry.place.name)
                        .font(family == .systemLarge ? .title3.bold() : .system(size: 16, weight: .semibold))
                        .lineLimit(1).minimumScaleFactor(0.75)
                    Text(condition.text)
                        .font(family == .systemLarge ? .subheadline : .system(size: 11))
                        .foregroundStyle(.white.opacity(0.78))
                        .lineLimit(1).minimumScaleFactor(0.8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    HStack(spacing: 5) {
                        Image(systemName: condition.symbol)
                            .symbolRenderingMode(.multicolor)
                            .font(family == .systemLarge ? .system(size: 28) : .system(size: 16))
                        Text(temperature)
                            .font(family == .systemLarge ? .system(size: 34, weight: .medium, design: .rounded) : .system(size: 20, weight: .bold, design: .rounded))
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                    if family == .systemLarge, let todayRange {
                        Text(todayRange).font(.caption2)
                            .foregroundStyle(.white.opacity(0.78))
                            .lineLimit(1)
                    }
                }
            }
            .padding(.leading, family == .systemLarge ? 26 : 16)
            .padding(.trailing, family == .systemLarge ? 23 : 15)
            if let points = chartPoints {
                WeatherChart(points: points, language: entry.preferences.language, timezone: entry.place.timezone, showClouds: entry.preferences.showClouds, showWind: entry.preferences.showWind, showFeelsLike: entry.preferences.showFeelsLike, compact: family == .systemMedium, widgetLayout: true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Spacer()
                Text(expired ? (de ? "Vorhersage abgelaufen" : "Forecast expired") : (de ? "Wetter nicht verfügbar" : "Weather unavailable")).font(.caption)
                Spacer()
            }
            Text(footerText)
                .font(family == .systemLarge ? .caption2 : .system(size: 10))
                .foregroundStyle(.white.opacity(0.7))
                .lineLimit(1).minimumScaleFactor(0.75)
                .padding(.horizontal, 8)
        }
        .padding(.horizontal, 6)
        .padding(.top, family == .systemMedium ? 12 : max(contentMargins.top, 16))
        .padding(.bottom, family == .systemMedium ? 12 : max(contentMargins.bottom, 16))
        .accessibilityElement(children: .contain)
    }
}

struct WetterkurveWidget: Widget {
    let kind = "WetterkurveWidget"
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: PlaceIntent.self, provider: WeatherProvider()) { entry in
            WeatherWidgetView(entry: entry)
        }
        .configurationDisplayName("Wetterkurve")
        .description("Current weather, next six hours, and a three-day chart for a saved place.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

@main
struct WetterkurveWidgetBundle: WidgetBundle {
    var body: some Widget { WetterkurveWidget() }
}
