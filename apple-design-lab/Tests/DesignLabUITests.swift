import XCTest

final class DesignLabUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testInspectAndFitArtwork() {
        let app = XCUIApplication()
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
        let app = XCUIApplication()
        app.launch()
        app.buttons["About this design lab"].tap()
        XCTAssertTrue(app.staticTexts["A design reference, not a file editor."].waitForExistence(timeout: 8))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["Coral study"].waitForExistence(timeout: 8))
    }

    func testSearchFiltersToNamedStudy() {
        let app = XCUIApplication()
        app.launch()
        app.swipeDown()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 8))
        search.tap()
        search.typeText("Orbit")
        XCTAssertTrue(app.buttons["Quiet orbit"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["Coral study"].exists)
    }
}
