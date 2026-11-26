import XCTest

final class DesignLabUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    private func makeLibraryApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["DESIGN_LAB_OPEN_STUDY"] = "0"
        return app
    }

    func testInspectAndFitArtwork() {
        let app = makeLibraryApp()
        app.launch()
        let coral = app.buttons["Coral study"]
        XCTAssertTrue(coral.waitForExistence(timeout: 12))
        coral.tap()
        let plus = app.buttons["Zoom in"]
        XCTAssertTrue(plus.waitForExistence(timeout: 8))
        let fit = app.buttons["Fit artwork"]
        let minus = app.buttons["Zoom out"]
        let grid = app.buttons["Alignment grid"]
        XCTAssertEqual(fit.value as? String, "100%")
        XCTAssertFalse(minus.isEnabled)
        for element in [minus, fit, plus, grid] {
            XCTAssertGreaterThanOrEqual(element.frame.width, 43.9)
            XCTAssertGreaterThanOrEqual(element.frame.height, 43.9)
        }
        for _ in 0..<4 { plus.tap() }
        XCTAssertEqual(fit.value as? String, "200%")
        XCTAssertFalse(plus.isEnabled)
        fit.tap()
        XCTAssertEqual(fit.value as? String, "100%")
        XCTAssertTrue(plus.isEnabled)
        XCTAssertEqual(grid.value as? String, "Off")
        grid.tap()
        XCTAssertEqual(grid.value as? String, "On")
        grid.tap()
        XCTAssertEqual(grid.value as? String, "Off")
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(coral.waitForExistence(timeout: 8))
    }

    func testAboutDescribesLocalOnlyReference() {
        let app = makeLibraryApp()
        app.launch()
        app.buttons["About this design lab"].tap()
        XCTAssertTrue(app.staticTexts["A design reference, not a file editor."].waitForExistence(timeout: 8))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["Coral study"].waitForExistence(timeout: 8))
    }

    func testLandscapeKeepsCanvasControlsReachable() {
        let device = XCUIDevice.shared
        let app = makeLibraryApp()
        device.orientation = .portrait
        defer { device.orientation = .portrait }
        app.launch()
        app.buttons["Coral study"].tap()
        device.orientation = .landscapeLeft
        let fit = app.buttons["Fit artwork"]
        XCTAssertTrue(fit.waitForExistence(timeout: 8))
        XCTAssertTrue(fit.isHittable)
        app.buttons["Zoom in"].tap()
        XCTAssertEqual(fit.value as? String, "125%")
        fit.tap()
        XCTAssertEqual(fit.value as? String, "100%")
        let window = app.windows.firstMatch
        let landscape = NSPredicate { _, _ in window.frame.width > window.frame.height }
        wait(for: [XCTNSPredicateExpectation(predicate: landscape, object: nil)], timeout: 12)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Landscape canvas"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testSearchFiltersToNamedStudy() {
        let app = makeLibraryApp()
        app.launch()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 8))
        search.tap()
        search.typeText("Orbit")
        XCTAssertTrue(app.buttons["Quiet orbit"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["Coral study"].exists)
    }
}
