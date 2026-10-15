import SwiftUI
import WidgetKit

@main
struct WetterkurveApp: App {
    var body: some Scene {
        WindowGroup { MainView() }
    }
}

@MainActor
@Observable
final class WeatherModel {
    var preferences = SharedStore.preferences()
    var cached: CachedForecast?
    var status = ""
    var searchQuery = ""
    var results: [Place] = []
    var searching = false
    var showSearch = false
    private var searchTask: Task<Void, Never>?

    init() {
        if let place = preferences.activePlace { cached = SharedStore.cached(place) }
        else { showSearch = true }
    }

    var de: Bool { preferences.language == "de" }
    var displayForecast: Forecast? {
        guard let place = preferences.activePlace, let forecast = cached?.forecast,
              WeatherData.startsToday(forecast, for: place) else { return nil }
        return forecast
    }
    func t(_ en: String, _ de: String) -> String { self.de ? de : en }

    func save() {
        SharedStore.save(preferences)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func select(_ place: Place) {
        preferences.activeID = place.id
        cached = SharedStore.cached(place)
        status = ""
        save()
        Task { await refresh() }
    }

    func add(_ place: Place) {
        guard preferences.places.count < 3, !preferences.places.contains(where: { $0.id == place.id }) else { return }
        preferences.places.append(place)
        showSearch = false
        searchQuery = ""
        results = []
        select(place)
    }

    func remove(_ place: Place) {
        guard preferences.places.contains(where: { $0.id == place.id }) else { return }
        let oldActiveID = preferences.activeID
        let next = preferences.remove(place)
        if oldActiveID == place.id, let next {
            select(next)
        } else if next == nil {
            cached = nil
            status = t("Add a place to see the weather.", "Füge einen Ort hinzu, um das Wetter zu sehen.")
            showSearch = true
            save()
        } else {
            save()
        }
    }

    func removeActive() {
        guard let active = preferences.activePlace else { return }
        remove(active)
    }

    func refresh(force: Bool = false) async {
        guard let place = preferences.activePlace else {
            cached = nil
            status = t("Add a place to see the weather.", "Füge einen Ort hinzu, um das Wetter zu sehen.")
            return
        }
        if !force, let cache = SharedStore.cached(place), Date.now.timeIntervalSince(cache.fetchedAt) < 600, WeatherData.startsToday(cache.forecast, for: place) {
            cached = cache
            status = t("Showing saved forecast", "Gespeicherte Vorhersage")
            return
        }
        status = t("Updating weather…", "Wetter wird aktualisiert…")
        do {
            let forecast = try await WeatherAPI.forecast(for: place)
            guard preferences.activeID == place.id else { return }
            SharedStore.save(forecast, for: place)
            cached = SharedStore.cached(place)
            status = t("Updated just now", "Gerade aktualisiert")
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            guard preferences.activeID == place.id else { return }
            cached = SharedStore.cached(place)
            status = displayForecast == nil ? t("Weather unavailable. Check your connection and try again.", "Wetter nicht verfügbar. Verbindung prüfen und erneut versuchen.") : t("Offline · showing saved forecast", "Offline · gespeicherte Vorhersage")
        }
    }

    func queryChanged() {
        searchTask?.cancel()
        let query = searchQuery
        if query.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 { results = []; searching = false; return }
        searching = true
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            do {
                let found = try await WeatherAPI.search(query, language: preferences.language)
                guard !Task.isCancelled, searchQuery == query else { return }
                results = found
                status = ""
            } catch {
                guard !Task.isCancelled else { return }
                results = []
                status = t("Place search unavailable. Try again.", "Ortssuche nicht verfügbar. Erneut versuchen.")
            }
            searching = false
        }
    }
}

struct MainView: View {
    @State private var model = WeatherModel()
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .body) private var placeRowHeight: CGFloat = 62
    private let background = Color(red: 0.125, green: 0.165, blue: 0.227)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Wetterkurve").font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                HStack {
                    languageButton("English", code: "en")
                    languageButton("Deutsch", code: "de")
                }
                Text(model.t("Saved places", "Gespeicherte Orte")).font(.headline).foregroundStyle(.white.opacity(0.78))
                if !model.preferences.places.isEmpty {
                    List {
                        ForEach(model.preferences.places) { place in
                            Button {
                                model.select(place)
                            } label: {
                                HStack {
                                    Text(place.label).lineLimit(2)
                                    Spacer()
                                    if place.id == model.preferences.activeID { Image(systemName: "checkmark.circle.fill") }
                                }
                                .font(.body)
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            }
                            .accessibilityLabel(place.label)
                            .accessibilityAddTraits(place.id == model.preferences.activeID ? [.isSelected] : [])
                            .listRowBackground(place.id == model.preferences.activeID ? Color.blue.opacity(0.44) : .white.opacity(0.08))
                            .listRowSeparator(.hidden)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) { model.remove(place) } label: {
                                    Label(model.t("Delete", "Löschen"), systemImage: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .scrollDisabled(true)
                    .frame(height: CGFloat(model.preferences.places.count) * placeRowHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                HStack {
                    if model.preferences.places.count < 3 {
                        Button(model.t("Add place", "Ort hinzufügen")) { model.showSearch.toggle() }
                            .buttonStyle(.bordered)
                    }
                    if !model.preferences.places.isEmpty {
                        Button(model.t("Remove selected", "Ausgewählten Ort entfernen"), role: .destructive) { model.removeActive() }
                            .buttonStyle(.bordered)
                    }
                }
                if model.showSearch { search }
                currentCard
                HStack {
                    Toggle(model.t("Clouds", "Wolken"), isOn: $model.preferences.showClouds).onChange(of: model.preferences.showClouds) { _, _ in model.save() }
                    Toggle(model.t("Wind", "Wind"), isOn: $model.preferences.showWind).onChange(of: model.preferences.showWind) { _, _ in model.save() }
                    Toggle(model.t("Feels like", "Gefühlt"), isOn: $model.preferences.showFeelsLike)
                        .accessibilityIdentifier("feelsLikeToggle")
                        .onChange(of: model.preferences.showFeelsLike) { _, _ in model.save() }
                }
                .toggleStyle(.button)
                if let place = model.preferences.activePlace, let forecast = model.displayForecast, let points = try? WeatherData.points(forecast) {
                    WeatherChart(points: points, language: model.preferences.language, timezone: place.timezone, showClouds: model.preferences.showClouds, showWind: model.preferences.showWind, showFeelsLike: model.preferences.showFeelsLike)
                        .frame(height: 300)
                        .background(.black.opacity(0.15), in: RoundedRectangle(cornerRadius: 16))
                    legend
                }
                Button {
                    Task { await model.refresh(force: true) }
                } label: {
                    Label(model.t("Refresh weather", "Wetter aktualisieren"), systemImage: "arrow.clockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(model.preferences.activePlace == nil)
                Text(model.status).font(.footnote).foregroundStyle(.white.opacity(0.8)).accessibilityIdentifier("weatherStatus")
                Text(model.t("Add a Wetterkurve widget from the Home Screen to see your saved place at a glance.", "Füge auf dem Home-Bildschirm ein Wetterkurve-Widget hinzu, um deinen gespeicherten Ort auf einen Blick zu sehen."))
                    .font(.footnote).foregroundStyle(.white.opacity(0.7))
                Link(model.t("Weather data: Open-Meteo (CC BY 4.0)", "Wetterdaten: Open-Meteo (CC BY 4.0)"), destination: URL(string: "https://open-meteo.com/")!)
                    .font(.footnote).foregroundStyle(.white.opacity(0.8))
                Link(model.t("Privacy policy", "Datenschutz"), destination: URL(string: "https://app.wean.de/wetterkurve/privacy.html")!)
                    .font(.footnote).foregroundStyle(.white.opacity(0.8))
            }
            .padding(20)
        }
        .foregroundStyle(.white)
        .background(background.ignoresSafeArea())
        .task { await model.refresh() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { Task { await model.refresh() } } }
    }

    private func languageButton(_ title: String, code: String) -> some View {
        Button(title) {
            model.preferences.language = code
            model.save()
        }
        .buttonStyle(.borderedProminent)
        .tint(model.preferences.language == code ? .blue : .gray.opacity(0.45))
        .accessibilityAddTraits(model.preferences.language == code ? [.isSelected] : [])
    }

    private var search: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField(model.t("Search place", "Ort suchen"), text: $model.searchQuery)
                .textFieldStyle(.roundedBorder)
                .foregroundStyle(.black)
                .autocorrectionDisabled()
                .onChange(of: model.searchQuery) { _, _ in model.queryChanged() }
                .accessibilityIdentifier("placeSearch")
            if model.searching { ProgressView().tint(.white) }
            ForEach(model.results) { place in
                Button(place.label) { model.add(place) }
                    .disabled(model.preferences.places.contains(where: { $0.id == place.id }))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 9))
            }
            Button(model.t("Close", "Schließen")) { model.showSearch = false }
        }
        .padding(14)
        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
    }

    private var currentCard: some View {
        let current = model.displayForecast?.current
        let condition = WeatherData.condition(current?.code ?? -1, language: model.preferences.language)
        return HStack(spacing: 14) {
            Image(systemName: condition.symbol).symbolRenderingMode(.multicolor).font(.system(size: 42))
                .accessibilityHidden(true)
            VStack(alignment: .leading) {
                Text(model.preferences.activePlace?.name ?? model.t("No saved place", "Kein gespeicherter Ort")).font(.title3.bold())
                Text(model.preferences.activePlace == nil ? model.t("Add a place to see the weather.", "Füge einen Ort hinzu, um das Wetter zu sehen.") : current == nil ? model.t("Weather unavailable", "Wetter nicht verfügbar") : condition.text)
                    .foregroundStyle(.white.opacity(0.8))
            }
            Spacer()
            Text(current.map { "\(WeatherData.rounded($0.temperature))°" } ?? "–°")
                .font(.system(.largeTitle, design: .rounded))
                .minimumScaleFactor(0.7)
        }
        .padding(18)
        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }

    private var legend: some View {
        let keys: [(LegendKey.Kind, String)] = [
            (.temperature, model.t("Temperature", "Temperatur")),
            (.apparent, model.t("Feels like", "Gefühlt")),
            (.chance, model.t("Rain chance", "Regenchance")),
            (.amount, model.t("Rain amount", "Regenmenge")),
            (.clouds, model.t("Cloud cover", "Wolken")),
            (.wind, "Wind (km/h)"),
        ]
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 16)], alignment: .leading, spacing: 8) {
            ForEach(keys.indices, id: \.self) { index in
                LegendKey(kind: keys[index].0, label: keys[index].1)
            }
        }
    }
}

private struct LegendKey: View {
    enum Kind: Equatable { case temperature, apparent, chance, amount, clouds, wind }

    let kind: Kind
    let label: String

    var body: some View {
        HStack(alignment: .center, spacing: 7) {
            Canvas { context, size in
                var line = Path()
                line.move(to: CGPoint(x: 0, y: size.height / 2))
                line.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                switch kind {
                case .temperature:
                    context.stroke(line, with: .linearGradient(ChartColors.temperatureGradient,
                                                                startPoint: .zero, endPoint: CGPoint(x: size.width, y: 0)),
                                   style: StrokeStyle(lineWidth: 3, lineCap: .round))
                case .apparent:
                    context.stroke(line, with: .color(ChartColors.apparent), style: StrokeStyle(lineWidth: 2.3, dash: [4, 4]))
                case .chance, .amount:
                    for index in 0..<3 {
                        let barHeight = CGFloat(4 + index * 3)
                        context.fill(Path(CGRect(x: CGFloat(3 + index * 8), y: size.height - barHeight,
                                                 width: 3, height: barHeight)),
                                     with: .color(ChartColors.rain.opacity(kind == .chance ? 0.28 : 0.95)))
                    }
                case .clouds:
                    for index in 0..<4 {
                        context.fill(Path(CGRect(x: CGFloat(index * 6), y: 0, width: 6, height: size.height)),
                                     with: .color(ChartColors.cloudBand(night: false, cover: Double(index) * 100 / 3)))
                    }
                case .wind:
                    context.stroke(line, with: .color(ChartColors.wind), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
                }
            }
            .frame(width: 24, height: 12)
            .accessibilityHidden(true)
            Text(label).font(.footnote).foregroundStyle(.white.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }
}
