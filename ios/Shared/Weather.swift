import Foundation

enum WeatherError: Error {
    case invalidResponse
    case invalidLocation
}

struct Place: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let label: String
    let latitude: Double
    let longitude: Double
    let timezone: String

    static let munich = Place(id: "48.137,11.576", name: "Munich", label: "Munich, Bavaria, Germany", latitude: 48.137, longitude: 11.576, timezone: "Europe/Berlin")
    static let berlin = Place(id: "52.5200,13.4050", name: "Berlin", label: "Berlin, Germany", latitude: 52.520, longitude: 13.405, timezone: "Europe/Berlin")
}

struct Preferences: Codable {
    var places: [Place] = [.berlin]
    var activeID: String = Place.berlin.id
    var showClouds = true
    var showWind = false
    var showFeelsLike = false
    var language = "en"

    var activePlace: Place? { places.first(where: { $0.id == activeID }) ?? places.first }
    func widgetPlace(configuredID: String?) -> Place? {
        places.first(where: { $0.id == configuredID }) ?? activePlace
    }

    mutating func normalize() {
        places = Array(places.filter { !$0.name.isEmpty && $0.latitude.isFinite && $0.longitude.isFinite && (-90...90).contains($0.latitude) && (-180...180).contains($0.longitude) }.prefix(3))
        if !places.contains(where: { $0.id == activeID }) { activeID = places.first?.id ?? "" }
        if language != "de" { language = "en" }
    }

    mutating func remove(_ place: Place) -> Place? {
        guard places.contains(where: { $0.id == place.id }) else { return activePlace }
        let wasActive = activeID == place.id
        places.removeAll { $0.id == place.id }
        if wasActive { activeID = places.first?.id ?? "" }
        return activePlace
    }

    mutating func removeActive() -> Place? {
        guard let activePlace else { return nil }
        return remove(activePlace)
    }
}

extension Preferences {
    private enum CodingKeys: String, CodingKey {
        case places, activeID, showClouds, showWind, showFeelsLike, language
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        places = try values.decode([Place].self, forKey: .places)
        activeID = try values.decode(String.self, forKey: .activeID)
        showClouds = try values.decode(Bool.self, forKey: .showClouds)
        showWind = try values.decode(Bool.self, forKey: .showWind)
        showFeelsLike = try values.decodeIfPresent(Bool.self, forKey: .showFeelsLike) ?? false
        language = try values.decode(String.self, forKey: .language)
    }
}

struct Current: Codable {
    let temperature: Double
    let apparent: Double
    let code: Int
    let wind: Double
    let humidity: Double

    enum CodingKeys: String, CodingKey {
        case temperature = "temperature_2m", apparent = "apparent_temperature", code = "weather_code"
        case wind = "wind_speed_10m", humidity = "relative_humidity_2m"
    }
}

struct Hourly: Codable {
    let time: [String]
    let temperature: [Double]
    let apparent: [Double]
    let probability: [Double]
    let rain: [Double]
    let code: [Int]
    let wind: [Double]
    let clouds: [Double]?

    enum CodingKeys: String, CodingKey {
        case time, rain = "precipitation", code = "weather_code", clouds = "cloud_cover"
        case temperature = "temperature_2m", apparent = "apparent_temperature"
        case probability = "precipitation_probability", wind = "wind_speed_10m"
    }
}

struct Forecast: Codable {
    let current: Current
    let hourly: Hourly
    var timezone: String? = nil
}

struct HourPoint: Identifiable {
    let id: String
    let temperature: Double
    let apparent: Double
    let probability: Double
    let rain: Double
    let wind: Double
    let clouds: Double
    var day: String { String(id.prefix(10)) }
    var hour: Int { Int(id.dropFirst(11).prefix(2)) ?? 0 }
}

struct CachedForecast: Codable {
    let forecast: Forecast
    let fetchedAt: Date
}

enum WeatherData {
    struct HourPreview: Equatable {
        let hour: Int
        let temperature: Int
        let code: Int
    }

    static func nextHours(_ forecast: Forecast, for place: Place, at date: Date, count: Int = 6) -> [HourPreview] {
        guard let zone = TimeZone(identifier: forecast.timezone ?? place.timezone) else { return [] }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        let localNow = formatter.string(from: date)
        let hourly = forecast.hourly
        return hourly.time.indices.filter { hourly.time[$0] > localNow &&
            $0 < hourly.temperature.count && $0 < hourly.code.count }
            .prefix(max(0, count)).map { index in
                HourPreview(hour: Int(hourly.time[index].dropFirst(11).prefix(2)) ?? 0,
                            temperature: rounded(hourly.temperature[index]), code: hourly.code[index])
            }
    }

    static func startsToday(_ forecast: Forecast, for place: Place, at date: Date = .now) -> Bool {
        guard let zone = TimeZone(identifier: forecast.timezone ?? place.timezone),
              let first = forecast.hourly.time.first else { return false }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd"
        return String(first.prefix(10)) == formatter.string(from: date)
    }

    static func timelineDates(for cache: CachedForecast?, place: Place, now: Date) -> [Date] {
        guard let cache, startsToday(cache.forecast, for: place, at: now),
              let zone = TimeZone(identifier: cache.forecast.timezone ?? place.timezone) else { return [now] }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        guard let midnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) else { return [now] }
        var dates = [now]
        let staleAt = cache.fetchedAt.addingTimeInterval(600)
        if staleAt > now && staleAt < midnight { dates.append(staleAt) }
        if let hourStart = calendar.dateInterval(of: .hour, for: now)?.start {
            for offset in 1...6 {
                if let nextHour = calendar.date(byAdding: .hour, value: offset, to: hourStart),
                   nextHour > now && nextHour < midnight { dates.append(nextHour) }
            }
        }
        if midnight > now { dates.append(midnight) }
        return Array(Set(dates)).sorted()
    }

    static func points(_ forecast: Forecast) throws -> [HourPoint] {
        let h = forecast.hourly
        let count = h.time.count
        guard count >= 72, [h.temperature.count, h.apparent.count, h.probability.count, h.rain.count, h.code.count, h.wind.count].allSatisfy({ $0 == count }), h.clouds == nil || h.clouds?.count == count else { throw WeatherError.invalidResponse }
        guard forecast.current.temperature.isFinite, forecast.current.apparent.isFinite else { throw WeatherError.invalidResponse }
        let firstDay = String(h.time[0].prefix(10))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let dayFormatter = DateFormatter()
        dayFormatter.calendar = calendar
        dayFormatter.locale = Locale(identifier: "en_US_POSIX")
        dayFormatter.timeZone = calendar.timeZone
        dayFormatter.dateFormat = "yyyy-MM-dd"
        guard let start = dayFormatter.date(from: firstDay) else { throw WeatherError.invalidResponse }
        let days = (0..<3).compactMap { calendar.date(byAdding: .day, value: $0, to: start).map(dayFormatter.string(from:)) }
        guard days.count == 3 else { throw WeatherError.invalidResponse }
        let selected = h.time.indices.filter { days.contains(String(h.time[$0].prefix(10))) }
        guard selected.count == 72 else { throw WeatherError.invalidResponse }
        let expected = days.flatMap { day in (0..<24).map { String(format: "%@T%02d:00", day, $0) } }
        guard selected.map({ h.time[$0] }) == expected else { throw WeatherError.invalidResponse }
        return try selected.map { i in
            let values = [h.temperature[i], h.apparent[i], h.probability[i], h.rain[i], h.wind[i], h.clouds?[i] ?? 0]
            guard values.allSatisfy(\.isFinite) else { throw WeatherError.invalidResponse }
            return HourPoint(id: h.time[i], temperature: values[0], apparent: values[1], probability: values[2], rain: values[3], wind: values[4], clouds: values[5])
        }
    }

    static func condition(_ code: Int, language: String) -> (symbol: String, text: String) {
        let de = language == "de"
        switch code {
        case 0: return ("sun.max.fill", de ? "Klar" : "Clear")
        case 1, 2: return ("cloud.sun.fill", de ? "Teilweise bewölkt" : "Partly cloudy")
        case 3: return ("cloud.fill", de ? "Bedeckt" : "Overcast")
        case 45, 48: return ("cloud.fog.fill", de ? "Nebel" : "Fog")
        case 51...57: return ("cloud.drizzle.fill", de ? "Nieselregen" : "Drizzle")
        case 61...67, 80...82: return ("cloud.rain.fill", de ? "Regen" : "Rain")
        case 71...77, 85, 86: return ("cloud.snow.fill", de ? "Schnee" : "Snow")
        case 95...99: return ("cloud.bolt.rain.fill", de ? "Gewitter" : "Thunderstorm")
        default: return ("cloud.fill", de ? "Unbekannt" : "Unknown")
        }
    }

    static func rounded(_ value: Double) -> Int { Int(value.rounded(.toNearestOrAwayFromZero)) }
}

enum SharedStore {
    static let group = "group.de.wean.wetterkurve"
    private static var defaults: UserDefaults { UserDefaults(suiteName: group) ?? .standard }

    static func preferences() -> Preferences {
        preferences(in: defaults)
    }

    static func preferences(in store: UserDefaults) -> Preferences {
        var result = (store.data(forKey: "preferences").flatMap { try? JSONDecoder().decode(Preferences.self, from: $0) }) ?? Preferences()
        result.normalize()
        return result
    }

    static func save(_ preferences: Preferences) {
        save(preferences, in: defaults)
    }

    static func save(_ preferences: Preferences, in store: UserDefaults) {
        var normalized = preferences
        normalized.normalize()
        store.set(try? JSONEncoder().encode(normalized), forKey: "preferences")
    }

    static func cached(_ place: Place) -> CachedForecast? {
        defaults.data(forKey: "forecast.\(place.id)").flatMap { try? JSONDecoder().decode(CachedForecast.self, from: $0) }
    }

    static func save(_ forecast: Forecast, for place: Place) {
        defaults.set(try? JSONEncoder().encode(CachedForecast(forecast: forecast, fetchedAt: .now)), forKey: "forecast.\(place.id)")
    }
}

private struct GeocodingResponse: Decodable {
    struct Hit: Decodable {
        let name: String?
        let latitude: Double?
        let longitude: Double?
        let admin1: String?
        let country: String?
        let timezone: String?
    }
    let results: [Hit]?
}

enum WeatherAPI {
    static func forecast(for place: Place) async throws -> Forecast {
        var url = URLComponents(string: "https://api.open-meteo.com/v1/forecast")!
        url.queryItems = [
            .init(name: "latitude", value: String(place.latitude)), .init(name: "longitude", value: String(place.longitude)),
            .init(name: "current", value: "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m"),
            .init(name: "hourly", value: "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,cloud_cover"),
            .init(name: "forecast_days", value: "3"), .init(name: "timezone", value: place.timezone)
        ]
        let result: Forecast = try await fetch(url.url!)
        _ = try WeatherData.points(result)
        return result
    }

    static func search(_ query: String, language: String) async throws -> [Place] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        var url = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")!
        url.queryItems = [.init(name: "name", value: trimmed), .init(name: "count", value: "8"), .init(name: "language", value: language), .init(name: "format", value: "json")]
        let response: GeocodingResponse = try await fetch(url.url!)
        return response.results?.compactMap { hit in
            guard let name = hit.name, !name.isEmpty, let lat = hit.latitude, let lon = hit.longitude, lat.isFinite, lon.isFinite, (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
            let label = ([name, hit.admin1, hit.country].compactMap { $0?.isEmpty == false ? $0 : nil }).joined(separator: ", ")
            return Place(id: String(format: "%.4f,%.4f", lat, lon), name: name, label: label, latitude: lat, longitude: lon, timezone: hit.timezone ?? "auto")
        } ?? []
    }

    private static func fetch<T: Decodable>(_ url: URL) async throws -> T {
        var request = URLRequest(url: url)
        request.setValue("Wetterkurve/1.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 20
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw WeatherError.invalidResponse }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
