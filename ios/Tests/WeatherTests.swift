import XCTest
import UIKit
import SwiftUI
@testable import Wetterkurve

final class WeatherTests: XCTestCase {
    func testFreshDefaultsAndSavedChoices() throws {
        let defaults = Preferences()
        XCTAssertEqual(defaults.activePlace?.name, "Berlin")
        XCTAssertTrue(defaults.showClouds)
        XCTAssertFalse(defaults.showWind)
        XCTAssertFalse(defaults.showFeelsLike)

        var saved = Preferences(places: [.munich], activeID: Place.munich.id, showClouds: false, showWind: true, language: "de")
        saved = try JSONDecoder().decode(Preferences.self, from: JSONEncoder().encode(saved))
        saved.normalize()
        XCTAssertEqual(saved.activePlace?.name, "Munich")
        XCTAssertFalse(saved.showClouds)
        XCTAssertTrue(saved.showWind)
        XCTAssertFalse(saved.showFeelsLike)
    }

    func testOlderPreferencesKeepChoicesAndDefaultFeelsLikeOff() throws {
        let existing = Preferences(places: [.munich], activeID: Place.munich.id,
                                   showClouds: false, showWind: true, language: "de")
        var payload = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(existing)) as? [String: Any])
        payload.removeValue(forKey: "showFeelsLike")
        let migrated = try JSONDecoder().decode(Preferences.self, from: JSONSerialization.data(withJSONObject: payload))
        XCTAssertEqual(migrated.places.map(\.id), [Place.munich.id])
        XCTAssertEqual(migrated.activeID, Place.munich.id)
        XCTAssertEqual(migrated.language, "de")
        XCTAssertFalse(migrated.showClouds)
        XCTAssertTrue(migrated.showWind)
        XCTAssertFalse(migrated.showFeelsLike)
    }

    func testFeelsLikeChoicePersistsInSharedStore() {
        let suite = "de.wean.wetterkurve.tests.feels.\(UUID().uuidString)"
        let store = UserDefaults(suiteName: suite)!
        defer { store.removePersistentDomain(forName: suite) }
        var preferences = SharedStore.preferences(in: store)
        preferences.showFeelsLike = true
        SharedStore.save(preferences, in: store)
        let reloaded = SharedStore.preferences(in: UserDefaults(suiteName: suite)!)
        XCTAssertTrue(reloaded.showFeelsLike)
        XCTAssertEqual(reloaded.activePlace?.id, Place.berlin.id)
        XCTAssertTrue(reloaded.showClouds)
        XCTAssertFalse(reloaded.showWind)
    }

    func testFeelsLikeOnlyChangesTemperatureAxisWhenShown() {
        let points = [HourPoint(id: "2026-10-09T00:00", temperature: 10, apparent: -30,
                                probability: 0, rain: 0, wind: 0, clouds: 0),
                      HourPoint(id: "2026-10-09T01:00", temperature: 17, apparent: 40,
                                probability: 0, rain: 0, wind: 0, clouds: 0)]
        let hidden = WeatherChart.temperatureRange(points: points, showFeelsLike: false)
        let shown = WeatherChart.temperatureRange(points: points, showFeelsLike: true)
        XCTAssertEqual(hidden?.low, 10)
        XCTAssertEqual(hidden?.high, 20)
        XCTAssertEqual(shown?.low, -30)
        XCTAssertEqual(shown?.high, 40)
    }

    @MainActor
    func testWidgetPlotClipsRainBarsAtBothLowerCorners() throws {
        let points = [HourPoint(id: "2026-10-09T00:00", temperature: 10, apparent: 8,
                                probability: 100, rain: 1, wind: 0, clouds: 0),
                      HourPoint(id: "2026-10-10T23:00", temperature: 15, apparent: 13,
                                probability: 100, rain: 1, wind: 0, clouds: 0)]
        let size = CGSize(width: 344, height: 276)
        let layout = try XCTUnwrap(WeatherChart.layout(in: size, compact: false, showClouds: false,
                                                        widgetLayout: true, low: 10, high: 15, maxWind: nil))
        let renderer = ImageRenderer(content: WeatherChart(points: points, language: "en", timezone: "Europe/Berlin",
                                                          showClouds: false, showWind: false, showFeelsLike: false,
                                                          widgetLayout: true).frame(width: size.width, height: size.height))
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.uiImage?.cgImage)
        let pixels = UnsafeMutablePointer<UInt8>.allocate(capacity: image.width * image.height * 4)
        pixels.initialize(repeating: 0, count: image.width * image.height * 4)
        defer { pixels.deallocate() }
        let context = try XCTUnwrap(CGContext(data: pixels, width: image.width, height: image.height,
                                              bitsPerComponent: 8, bytesPerRow: image.width * 4,
                                              space: CGColorSpaceCreateDeviceRGB(),
                                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.translateBy(x: 0, y: CGFloat(image.height))
        context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        func alpha(_ x: Int, _ yFromTop: Int) -> UInt8 {
            pixels[((image.height - 1 - yFromTop) * image.width + x) * 4 + 3]
        }
        let y = Int(layout.bottom) - 2
        XCTAssertLessThan(alpha(Int(layout.left) + 1, y), 16)
        XCTAssertLessThan(alpha(Int(layout.right) - 1, y), 16)
        XCTAssertGreaterThan(alpha(Int(layout.left) + 12, y), 200)
        XCTAssertGreaterThan(alpha(Int(layout.right) - 12, y), 200)
    }

    func testRemovingOnlyPlacePersistsExplicitEmptyState() {
        let suite = "de.wean.wetterkurve.tests.empty.\(UUID().uuidString)"
        let store = UserDefaults(suiteName: suite)!
        defer { store.removePersistentDomain(forName: suite) }
        var preferences = SharedStore.preferences(in: store)
        XCTAssertEqual(preferences.activePlace?.name, "Berlin")
        XCTAssertNil(preferences.removeActive())
        SharedStore.save(preferences, in: store)

        let reloaded = SharedStore.preferences(in: UserDefaults(suiteName: suite)!)
        XCTAssertTrue(reloaded.places.isEmpty)
        XCTAssertNil(reloaded.activePlace)
        XCTAssertEqual(reloaded.activeID, "")
        XCTAssertTrue(reloaded.showClouds)
        XCTAssertFalse(reloaded.showWind)
    }

    func testRemovingSelectedAmongMultiplePlacesPersistsFallbackAndChoices() {
        let suite = "de.wean.wetterkurve.tests.multiple.\(UUID().uuidString)"
        let store = UserDefaults(suiteName: suite)!
        defer { store.removePersistentDomain(forName: suite) }
        var preferences = Preferences(places: [.berlin, .munich], activeID: Place.munich.id,
                                      showClouds: false, showWind: true, language: "de")
        SharedStore.save(preferences, in: store)
        XCTAssertEqual(preferences.removeActive()?.id, Place.berlin.id)
        SharedStore.save(preferences, in: store)

        let reloaded = SharedStore.preferences(in: UserDefaults(suiteName: suite)!)
        XCTAssertEqual(reloaded.places.map(\.id), [Place.berlin.id])
        XCTAssertEqual(reloaded.activePlace?.id, Place.berlin.id)
        XCTAssertFalse(reloaded.showClouds)
        XCTAssertTrue(reloaded.showWind)
        XCTAssertEqual(reloaded.language, "de")
    }

    func testSwipingInactivePlaceKeepsSelectionAndPersists() {
        let suite = "de.wean.wetterkurve.tests.inactive.\(UUID().uuidString)"
        let store = UserDefaults(suiteName: suite)!
        defer { store.removePersistentDomain(forName: suite) }
        var preferences = Preferences(places: [.berlin, .munich], activeID: Place.munich.id,
                                      showClouds: false, showWind: true, language: "de")
        XCTAssertEqual(preferences.remove(.berlin)?.id, Place.munich.id)
        SharedStore.save(preferences, in: store)
        let reloaded = SharedStore.preferences(in: UserDefaults(suiteName: suite)!)
        XCTAssertEqual(reloaded.places.map(\.id), [Place.munich.id])
        XCTAssertEqual(reloaded.activeID, Place.munich.id)
        XCTAssertFalse(reloaded.showClouds)
        XCTAssertTrue(reloaded.showWind)
    }

    func testWidgetSelectionFallsBackAfterConfiguredPlaceIsRemoved() {
        var preferences = Preferences(places: [.berlin, .munich], activeID: Place.munich.id,
                                      showClouds: true, showWind: false, language: "en")
        XCTAssertEqual(preferences.widgetPlace(configuredID: Place.munich.id)?.id, Place.munich.id)
        XCTAssertEqual(preferences.removeActive()?.id, Place.berlin.id)
        XCTAssertEqual(preferences.widgetPlace(configuredID: Place.munich.id)?.id, Place.berlin.id)
        XCTAssertNil(preferences.removeActive())
        XCTAssertNil(preferences.widgetPlace(configuredID: Place.munich.id))
    }

    func testWidgetChartUsesAvailableAreaAtMediumAndLargeSizes() {
        let medium = WeatherChart.layout(in: CGSize(width: 344, height: 100), compact: true, showClouds: true,
                                         widgetLayout: true, low: 5, high: 20, maxWind: nil)
        let large = WeatherChart.layout(in: CGSize(width: 344, height: 276), compact: false, showClouds: true,
                                        widgetLayout: true, low: 5, high: 20, maxWind: nil)
        XCTAssertNotNil(medium)
        XCTAssertNotNil(large)
        XCTAssertGreaterThan((medium!.right - medium!.left) / 344, 0.85)
        XCTAssertGreaterThan(medium!.bottom - medium!.top, 40)
        XCTAssertGreaterThan((large!.right - large!.left) / 344, 0.85)
        XCTAssertGreaterThan(large!.bottom - large!.top, 200)
    }

    func testWidgetAxesFitNegativeTemperatureAndThreeDigitWind() {
        for compact in [false, true] {
            let layout = WeatherChart.layout(in: CGSize(width: 344, height: compact ? 100 : 276),
                                             compact: compact, showClouds: true, widgetLayout: true,
                                             low: -100, high: 20, maxWind: 100)!
            let temperatureFont = UIFont.systemFont(ofSize: layout.label * 0.8, weight: .medium)
            let leftTickWidth = ("-100°" as NSString).size(withAttributes: [.font: temperatureFont]).width
            XCTAssertGreaterThanOrEqual(layout.left - layout.label * 0.9 - leftTickWidth / 2, 0)
            let windFont = UIFont.systemFont(ofSize: layout.label * 0.65, weight: .medium)
            let rightTickWidth = ("100" as NSString).size(withAttributes: [.font: windFont]).width
            XCTAssertLessThanOrEqual(layout.right + layout.label * 0.65 + rightTickWidth / 2, 344)
            XCTAssertGreaterThan(layout.right - layout.left, 265)
        }
    }

    func testAppChartKeepsItsOriginalGeometry() {
        let layout = WeatherChart.layout(in: CGSize(width: 344, height: 300), compact: false, showClouds: true,
                                         widgetLayout: false, low: 5, high: 20, maxWind: nil)!
        XCTAssertEqual(layout.label, 22.5)
        XCTAssertEqual(layout.left, 42.75)
        XCTAssertEqual(layout.right, 299)
    }

    func testPreferencesConstrainPlacesAndLanguage() {
        var settings = Preferences(places: [.munich, .munich, .munich, .munich], activeID: "missing", showClouds: false, showWind: true, language: "fr")
        settings.normalize()
        XCTAssertEqual(settings.places.count, 3)
        XCTAssertEqual(settings.activePlace?.id, Place.munich.id)
        XCTAssertEqual(settings.language, "en")
        XCTAssertFalse(settings.showClouds)
    }

    func testForecastIsThreeAlignedDays() throws {
        let hours = (0..<96).map { i in
            let day = 1 + i / 24
            return String(format: "2026-10-%02dT%02d:00", day, i % 24)
        }
        let hourly = Hourly(time: hours, temperature: Array(repeating: 12, count: 96), apparent: Array(repeating: 10, count: 96), probability: Array(repeating: 30, count: 96), rain: Array(repeating: 0, count: 96), code: Array(repeating: 2, count: 96), wind: Array(repeating: 8, count: 96), clouds: Array(repeating: 60, count: 96))
        let forecast = Forecast(current: Current(temperature: 12, apparent: 10, code: 2, wind: 8, humidity: 70), hourly: hourly)
        let points = try WeatherData.points(forecast)
        XCTAssertEqual(points.count, 72)
        XCTAssertEqual(points.first?.id, "2026-10-01T00:00")
        XCTAssertEqual(points.last?.id, "2026-10-03T23:00")
        XCTAssertEqual(Set(points.map(\.day)).count, 3)
        XCTAssertEqual(Array(points.map(\.hour).prefix(25)), Array(0..<24) + [0])
    }

    func testRejectsMissingHourWithinThreeDayWindow() {
        let times = (0..<72).filter { $0 != 36 }.map { i in
            String(format: "2026-10-%02dT%02d:00", 1 + i / 24, i % 24)
        }
        let hourly = Hourly(time: times, temperature: Array(repeating: 12, count: 71), apparent: Array(repeating: 10, count: 71), probability: Array(repeating: 30, count: 71), rain: Array(repeating: 0, count: 71), code: Array(repeating: 2, count: 71), wind: Array(repeating: 8, count: 71), clouds: Array(repeating: 60, count: 71))
        let forecast = Forecast(current: Current(temperature: 12, apparent: 10, code: 2, wind: 8, humidity: 70), hourly: hourly)
        XCTAssertThrowsError(try WeatherData.points(forecast))
    }

    func testThreeDaysAcrossDeviceDaylightSavingChange() throws {
        let previous = NSTimeZone.default
        NSTimeZone.default = TimeZone(identifier: "Europe/Berlin")!
        defer { NSTimeZone.default = previous }
        let times = (0..<72).map { i in
            String(format: "2026-03-%02dT%02d:00", 28 + i / 24, i % 24)
        }
        let hourly = Hourly(time: times, temperature: Array(repeating: 12, count: 72), apparent: Array(repeating: 10, count: 72), probability: Array(repeating: 30, count: 72), rain: Array(repeating: 0, count: 72), code: Array(repeating: 2, count: 72), wind: Array(repeating: 8, count: 72), clouds: Array(repeating: 60, count: 72))
        let forecast = Forecast(current: Current(temperature: 12, apparent: 10, code: 2, wind: 8, humidity: 70), hourly: hourly)
        XCTAssertEqual(try WeatherData.points(forecast).count, 72)
    }

    func testCacheDayBoundaryUsesForecastPlaceTimeZone() {
        let hourly = Hourly(time: ["2026-10-01T00:00"], temperature: [12], apparent: [10], probability: [0], rain: [0], code: [0], wind: [5], clouds: [0])
        let forecast = Forecast(current: Current(temperature: 12, apparent: 10, code: 0, wind: 5, humidity: 60), hourly: hourly, timezone: "Pacific/Auckland")
        let parser = ISO8601DateFormatter()
        XCTAssertTrue(WeatherData.startsToday(forecast, for: .munich, at: parser.date(from: "2026-09-30T12:00:00Z")!))
        XCTAssertFalse(WeatherData.startsToday(forecast, for: .munich, at: parser.date(from: "2026-10-01T12:00:00Z")!))
    }

    func testWidgetTimelineMarksStaleAndExpiresAtPlaceMidnight() {
        let hourly = Hourly(time: ["2026-10-08T00:00"], temperature: [12], apparent: [10], probability: [0], rain: [0], code: [0], wind: [5], clouds: [0])
        let forecast = Forecast(current: Current(temperature: 12, apparent: 10, code: 0, wind: 5, humidity: 60), hourly: hourly, timezone: "Europe/Berlin")
        let parser = ISO8601DateFormatter()
        func date(_ value: String) -> Date { parser.date(from: value)! }
        let daytime = CachedForecast(forecast: forecast, fetchedAt: date("2026-10-08T11:55:00Z"))
        XCTAssertEqual(WeatherData.timelineDates(for: daytime, place: .munich, now: date("2026-10-08T12:00:00Z")),
                       [date("2026-10-08T12:00:00Z"), date("2026-10-08T12:05:00Z")] +
                       (13...18).map { date(String(format: "2026-10-08T%02d:00:00Z", $0)) } + [date("2026-10-08T22:00:00Z")])
        let late = CachedForecast(forecast: forecast, fetchedAt: date("2026-10-08T21:55:00Z"))
        XCTAssertEqual(WeatherData.timelineDates(for: late, place: .munich, now: date("2026-10-08T21:58:00Z")),
                       [date("2026-10-08T21:58:00Z"), date("2026-10-08T22:00:00Z")])
        XCTAssertEqual(WeatherData.timelineDates(for: late, place: .munich, now: date("2026-10-08T22:01:00Z")), [date("2026-10-08T22:01:00Z")])
    }

    func testRejectsIncompleteForecast() {
        let hourly = Hourly(time: ["2026-10-01T00:00", "2026-10-01T01:00"], temperature: [5], apparent: [4, 4], probability: [0, 0], rain: [0, 0], code: [0, 0], wind: [1, 1], clouds: nil)
        let forecast = Forecast(current: Current(temperature: 5, apparent: 4, code: 0, wind: 1, humidity: 50), hourly: hourly)
        XCTAssertThrowsError(try WeatherData.points(forecast))
    }

    func testMediumWidgetUsesNextSixHoursInPlaceTimeZone() {
        let times = (0..<24).map { String(format: "2026-10-08T%02d:00", $0) }
        let hourly = Hourly(time: times, temperature: (0..<24).map(Double.init),
                            apparent: Array(repeating: 10, count: 24), probability: Array(repeating: 0, count: 24),
                            rain: Array(repeating: 0, count: 24), code: Array(repeating: 2, count: 24),
                            wind: Array(repeating: 8, count: 24), clouds: nil)
        let forecast = Forecast(current: Current(temperature: 11, apparent: 10, code: 2, wind: 8, humidity: 70),
                                hourly: hourly, timezone: "Europe/Berlin")
        let now = ISO8601DateFormatter().date(from: "2026-10-08T08:35:00Z")!
        let preview = WeatherData.nextHours(forecast, for: .munich, at: now)
        XCTAssertEqual(preview.map(\.hour), [11, 12, 13, 14, 15, 16])
        XCTAssertEqual(preview.map(\.temperature), [11, 12, 13, 14, 15, 16])
        XCTAssertEqual(WeatherData.nextHours(forecast, for: .munich, at: now, count: 0), [])
    }
}
