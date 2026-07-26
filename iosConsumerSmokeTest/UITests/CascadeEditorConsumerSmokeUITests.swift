import XCTest

final class CascadeEditorConsumerSmokeUITests: XCTestCase {
    @MainActor
    func testPackagedFrameworkLoadsResourcesAndPublicAPI() throws {
        let app = XCUIApplication()
        app.launch()

        let status = app.staticTexts["cascade.smoke.status"]
        XCTAssertTrue(status.waitForExistence(timeout: 20), "Smoke status label did not appear")

        let ready = NSPredicate(
            format: "label MATCHES %@",
            #"CASCADE_SMOKE_READY [0-9]+\.[0-9]+\.[0-9]+"#
        )
        let readyExpectation = XCTNSPredicateExpectation(predicate: ready, object: status)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 20),
            .completed,
            "SDK did not reach the ready state: \(status.label)"
        )
        XCTAssertFalse(status.label.contains("FAILED"), status.label)

        for accessibilityLabel in [
            "Hide Keyboard",
            "Indent Backward",
            "Indent Forward",
            "Link",
        ] {
            let node = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", accessibilityLabel))
                .firstMatch
            XCTAssertTrue(
                node.waitForExistence(timeout: 5),
                "Compose toolbar resource node '\(accessibilityLabel)' is missing"
            )
        }

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "CascadeEditor packaged consumer"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
