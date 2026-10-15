import XCTest

final class WetterkurveUITests: XCTestCase {
    func testFeelsLikeTogglePersistsAndAppLegendAlwaysHasSixKeys() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["English"].waitForExistence(timeout: 10))
        app.buttons["English"].tap()
        ensureSavedPlace(in: app)
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label IN %@", ["Updated just now", "Showing saved forecast"]), object: app.staticTexts["weatherStatus"])
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)

        let toggle = app.descendants(matching: .any)["feelsLikeToggle"]
        XCTAssertTrue(toggle.exists)
        let originalValue = toggle.value as? String
        XCTAssertNotNil(originalValue)
        for label in ["Temperature", "Feels like", "Rain chance", "Rain amount", "Cloud cover", "Wind (km/h)"] {
            let key = app.staticTexts[label]
            for _ in 0..<4 where !key.exists { app.swipeUp() }
            XCTAssertTrue(key.exists, "Missing legend key: \(label)")
        }
        toggle.tap()
        XCTAssertNotEqual(toggle.value as? String, originalValue)
        app.terminate()
        app.launch()
        let restoredToggle = app.descendants(matching: .any)["feelsLikeToggle"]
        XCTAssertTrue(restoredToggle.waitForExistence(timeout: 10))
        XCTAssertNotEqual(restoredToggle.value as? String, originalValue)
        restoredToggle.tap()
    }

    func testLargeTextKeepsThreeCityRowsSwipeable() {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        XCTAssertTrue(app.buttons["English"].waitForExistence(timeout: 10))
        app.buttons["English"].tap()
        while app.buttons["Remove selected"].exists { app.buttons["Remove selected"].tap() }
        addPlace("Berlin", in: app)
        addPlace("Munich", in: app)
        addPlace("Ludwigshafen", in: app)

        let last = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Ludwigshafen")).firstMatch
        XCTAssertTrue(last.waitForExistence(timeout: 10))
        for _ in 0..<3 where !last.isHittable { app.swipeDown() }
        let largeTextImage = XCTAttachment(screenshot: app.screenshot())
        largeTextImage.name = "Three city rows at accessibility text size"
        largeTextImage.lifetime = .keepAlways
        add(largeTextImage)
        XCTAssertTrue(last.isHittable)
        last.swipeLeft()
        XCTAssertTrue(app.buttons["Delete"].waitForExistence(timeout: 5))
        app.buttons["Delete"].tap()
        XCTAssertFalse(last.exists)
    }

    func testSwipeDeletesInactiveSelectedAndLastPlace() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["English"].waitForExistence(timeout: 10))
        app.buttons["English"].tap()
        while app.buttons["Remove selected"].exists { app.buttons["Remove selected"].tap() }
        addPlace("Berlin", in: app)
        addPlace("Munich", in: app)

        let berlin = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Berlin,")).firstMatch
        XCTAssertTrue(berlin.waitForExistence(timeout: 5))
        berlin.swipeLeft()
        XCTAssertTrue(app.buttons["Delete"].waitForExistence(timeout: 5))
        app.buttons["Delete"].tap()
        XCTAssertFalse(berlin.exists)
        XCTAssertTrue(app.staticTexts["Munich"].exists)

        addPlace("Berlin", in: app)
        let selectedBerlin = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Berlin,")).firstMatch
        XCTAssertTrue(selectedBerlin.waitForExistence(timeout: 5))
        selectedBerlin.swipeLeft()
        XCTAssertTrue(app.buttons["Delete"].waitForExistence(timeout: 5))
        app.buttons["Delete"].tap()
        XCTAssertFalse(selectedBerlin.exists)
        XCTAssertTrue(app.staticTexts["Munich"].waitForExistence(timeout: 5))

        let munich = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Munich,")).firstMatch
        XCTAssertTrue(munich.waitForExistence(timeout: 5))
        munich.swipeLeft()
        XCTAssertTrue(app.buttons["Delete"].waitForExistence(timeout: 5))
        app.buttons["Delete"].tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "No saved place")).firstMatch.waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "No saved place")).firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.textFields["placeSearch"].exists)
    }

    func testRemoveSelectedPlacePersistsEmptyState() {
        let app = XCUIApplication()
        app.launch()
        let english = app.buttons["English"]
        XCTAssertTrue(english.waitForExistence(timeout: 10))
        english.tap()
        ensureSavedPlace(in: app)

        let remove = app.buttons["Remove selected"]
        var removed = 0
        while remove.exists && removed < 3 {
            remove.tap()
            removed += 1
        }
        XCTAssertGreaterThan(removed, 0)
        XCTAssertFalse(remove.exists)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "No saved place")).firstMatch.exists)
        XCTAssertTrue(app.textFields["placeSearch"].exists)

        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "No saved place")).firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.textFields["placeSearch"].exists)
        XCTAssertFalse(app.buttons["Remove selected"].exists)
    }

    func testLanguagePlaceSearchAndForecast() {
        let app = XCUIApplication()
        app.launch()

        let english = app.buttons["English"]
        XCTAssertTrue(english.waitForExistence(timeout: 10))
        english.tap()
        ensureSavedPlace(in: app)

        let englishWeather = app.staticTexts["weatherStatus"]
        let englishReady = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label IN %@", ["Updated just now", "Showing saved forecast", "Gerade aktualisiert", "Gespeicherte Vorhersage"]), object: englishWeather)
        XCTAssertEqual(XCTWaiter.wait(for: [englishReady], timeout: 30), .completed)
        let englishTop = XCTAttachment(screenshot: app.screenshot())
        englishTop.name = "Wetterkurve English overview"
        englishTop.lifetime = .keepAlways
        add(englishTop)
        app.swipeUp()
        let englishImage = XCTAttachment(screenshot: app.screenshot())
        englishImage.name = "Wetterkurve English chart"
        englishImage.lifetime = .keepAlways
        add(englishImage)
        app.swipeDown()

        let german = app.buttons["Deutsch"]
        XCTAssertTrue(german.waitForExistence(timeout: 10))
        german.tap()
        XCTAssertTrue(app.staticTexts["Gespeicherte Orte"].exists)

        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["Gespeicherte Orte"].waitForExistence(timeout: 10))
        let berlin = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Berlin,")).firstMatch
        if berlin.exists {
            berlin.tap()
        } else {
            app.buttons["Ort hinzufügen"].tap()
            let search = app.textFields["placeSearch"]
            XCTAssertTrue(search.waitForExistence(timeout: 5))
            search.tap()
            search.typeText("Berlin")
            let result = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Berlin,")).firstMatch
            XCTAssertTrue(result.waitForExistence(timeout: 25))
            result.tap()
        }

        XCTAssertTrue(app.staticTexts["Berlin"].waitForExistence(timeout: 10))
        let status = app.staticTexts["weatherStatus"]
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label IN %@", ["Gerade aktualisiert", "Gespeicherte Vorhersage"]), object: status)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Wetterkurve Berlin weather"
        image.lifetime = .keepAlways
        add(image)

        app.swipeUp()
        let chartImage = XCTAttachment(screenshot: app.screenshot())
        chartImage.name = "Wetterkurve Berlin three-day chart"
        chartImage.lifetime = .keepAlways
        add(chartImage)
    }

    private func ensureSavedPlace(in app: XCUIApplication) {
        if app.buttons["Remove selected"].exists { return }
        let search = app.textFields["placeSearch"]
        if !search.exists { app.buttons["Add place"].tap() }
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap()
        search.typeText("Berlin")
        let result = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Berlin,")).firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 25))
        result.tap()
        XCTAssertTrue(app.buttons["Remove selected"].waitForExistence(timeout: 5))
    }

    private func addPlace(_ name: String, in app: XCUIApplication) {
        let search = app.textFields["placeSearch"]
        if !search.exists { app.buttons["Add place"].tap() }
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap()
        search.typeText(name)
        let result = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "\(name),")).firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 25))
        result.tap()
        XCTAssertTrue(app.buttons["Remove selected"].waitForExistence(timeout: 5))
    }
}
