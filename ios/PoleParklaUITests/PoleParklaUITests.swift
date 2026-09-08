import XCTest

@MainActor
final class PoleParklaUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testTwoStepOnboardingSavesLanguageProfileAndRecipientWithoutKeyboardOverlap() {
        let app = launch("--ui-testing-onboarding")
        XCTAssertTrue(app.staticTexts["Clear evidence. Your decision."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["How privacy works"].exists)
        app.buttons["English"].tap()
        app.buttons["onboarding.continue"].tap()

        let progress = app.otherElements["onboarding.progress"]
        let name = app.textFields["Name"]
        let phone = app.textFields["Phone"]
        let recipient = app.textFields["Recipient"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        XCTAssertTrue(phone.exists)
        XCTAssertTrue(recipient.exists)
        XCTAssertEqual(app.pageIndicators.count, 0)
        XCTAssertFalse(progress.frame.intersects(phone.frame))

        name.tap()
        name.typeText("UI Tester")
        let next = app.keyboards.buttons.matching(NSPredicate(format: "label ==[c] 'next'")).firstMatch
        XCTAssertTrue(next.waitForExistence(timeout: 3))
        next.tap()
        phone.typeText("+3725555555")

        let done = app.buttons["onboarding.done"]
        XCTAssertTrue(done.isHittable)
        done.tap()
        XCTAssertTrue(app.buttons["camera.review"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["camera.gallery"].exists)
    }

    func testCameraDoesNotOpenWizardAutomaticallyAndSupportsZeroOneThreePhotos() {
        var app = launch()
        let review = app.buttons["camera.review"]
        XCTAssertTrue(review.waitForExistence(timeout: 5))
        XCTAssertFalse(review.isEnabled)
        XCTAssertEqual(app.staticTexts["camera.photoCount"].label, "0/3")

        app.terminate()
        app = launch("--ui-testing-camera-one")
        XCTAssertEqual(app.staticTexts["camera.photoCount"].waitForExistence(timeout: 5), true)
        XCTAssertEqual(app.staticTexts["camera.photoCount"].label, "1/3")
        XCTAssertTrue(app.buttons["camera.review"].isEnabled)
        XCTAssertFalse(app.staticTexts["Check the vehicle"].exists)

        app.terminate()
        app = launch("--ui-testing-camera-three")
        XCTAssertTrue(app.staticTexts["All 3 slots are full. Delete a photo to replace it."].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["camera.photoCount"].label, "3/3")
        XCTAssertFalse(app.buttons["camera.gallery"].isEnabled)
    }

    func testDeleteToReplaceFreesCameraSlot() {
        let app = launch("--ui-testing-camera-three")
        let delete = app.buttons["camera.slot.delete.0"]
        XCTAssertTrue(delete.waitForExistence(timeout: 5))
        XCTAssertEqual(delete.label, "Delete photo 1")
        delete.tap()
        XCTAssertTrue(app.staticTexts["Delete this photo?"].waitForExistence(timeout: 3))
        app.sheets["Delete this photo?"].buttons["Delete"].tap()
        XCTAssertTrue(app.staticTexts["camera.photoCount"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["camera.photoCount"].label, "2/3")
        XCTAssertTrue(app.buttons["camera.gallery"].isEnabled)
        XCTAssertFalse(app.staticTexts["All 3 slots are full. Delete a photo to replace it."].exists)
    }

    func testCameraUnavailableStillKeepsGalleryWorking() {
        let app = launch()
        XCTAssertTrue(app.buttons["camera.gallery"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["camera.gallery"].isEnabled)
        XCTAssertTrue(app.staticTexts["Camera is unavailable. You can choose photos instead."].exists)
    }

    func testFullWizardRequiresExplicitConfirmationAndProblemHasNoTemplateManagement() {
        let app = launch("--ui-testing-report")
        XCTAssertTrue(app.staticTexts["Check the vehicle"].waitForExistence(timeout: 5))
        app.buttons["Save and confirm vehicle"].tap()
        XCTAssertTrue(app.staticTexts["Check the vehicle"].exists)
        fillVehicleAndPlace(app)
        XCTAssertFalse(app.buttons["Add custom template"].exists)
        app.buttons["Parked on a cycle path"].tap()
        XCTAssertTrue(app.staticTexts["Ready to open in mail"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["summary.photos"].exists)
        XCTAssertTrue(app.buttons["summary.vehicle"].exists)
        XCTAssertTrue(app.buttons["summary.location"].exists)
        XCTAssertTrue(app.buttons["summary.problem"].exists)
        XCTAssertTrue(app.buttons["summary.recipient"].exists)
        XCTAssertTrue(app.buttons["summary.sender"].exists)
    }

    func testSummaryCardsOpenNativeEditorsAndDismissKeepsValues() {
        let app = launch("--ui-testing-ready")
        XCTAssertTrue(app.buttons["summary.photos"].waitForExistence(timeout: 5))

        app.buttons["summary.photos"].tap()
        XCTAssertTrue(app.navigationBars["Photos"].waitForExistence(timeout: 3))
        app.navigationBars.buttons["Done"].tap()

        app.buttons["summary.vehicle"].tap()
        let plate = app.textFields["Registration number"]
        XCTAssertTrue(plate.waitForExistence(timeout: 3))
        plate.tap()
        plate.typeText("X")
        app.navigationBars.buttons["Cancel"].tap()
        app.buttons["summary.vehicle"].tap()
        XCTAssertEqual(app.textFields["Registration number"].value as? String, "003 PUK")
        app.navigationBars.buttons["Cancel"].tap()

        app.buttons["summary.location"].tap()
        let address = app.textFields["Address"]
        XCTAssertTrue(address.waitForExistence(timeout: 3))
        address.tap()
        address.typeText(" changed")
        app.navigationBars.buttons["Cancel"].tap()
        app.buttons["summary.location"].tap()
        XCTAssertEqual(app.textFields["Address"].value as? String, "Lastekodu tn 42, Tallinn")
        app.navigationBars.buttons["Cancel"].tap()

        let originalViolation = app.buttons["summary.problem"].label
        app.buttons["summary.problem"].tap()
        XCTAssertTrue(app.staticTexts["Choose the violation"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["Add custom template"].exists)
        app.navigationBars.buttons["Cancel"].tap()
        XCTAssertEqual(app.buttons["summary.problem"].label, originalViolation)

        app.buttons["summary.recipient"].tap()
        let recipient = app.textFields["Recipient"]
        XCTAssertTrue(recipient.waitForExistence(timeout: 3))
        recipient.tap()
        recipient.typeText(".invalid")
        app.navigationBars.buttons["Cancel"].tap()
        app.buttons["summary.recipient"].tap()
        XCTAssertEqual(app.textFields["Recipient"].value as? String, "korrapidaja@tallinnlv.ee")
        app.navigationBars.buttons["Cancel"].tap()

        let sender = app.buttons["summary.sender"]
        app.swipeUp()
        XCTAssertTrue(sender.isHittable)
        sender.tap()
        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText(" changed")
        app.navigationBars.buttons["Cancel"].tap()
        app.buttons["summary.sender"].tap()
        XCTAssertEqual(app.textFields["Name"].value as? String, "UI Tester")
        app.navigationBars.buttons["Cancel"].tap()
    }

    func testOptionalVehicleDetailsAndCoordinatesAreCollapsedAndCloudIsAbsent() {
        let app = launch("--ui-testing-report")
        XCTAssertTrue(app.buttons["vehicle.optional.toggle"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.textFields["vehicle.make"].exists)
        XCTAssertFalse(app.textFields["vehicle.model"].exists)
        XCTAssertFalse(app.buttons["Try cloud recognition"].exists)
        app.buttons["vehicle.optional.toggle"].tap()
        let make = app.textFields["vehicle.make"]
        let model = app.textFields["vehicle.model"]
        XCTAssertTrue(make.waitForExistence(timeout: 3))
        XCTAssertTrue(model.exists)
        make.tap()
        make.typeText("Honda")
        app.buttons["vehicle.optional.toggle"].tap()
        XCTAssertFalse(make.exists)
        XCTAssertFalse(model.exists)
        XCTAssertTrue(app.staticTexts["Honda"].waitForExistence(timeout: 3))

        let plate = app.textFields["Registration number"]
        plate.tap()
        plate.typeText("003 PUK")
        app.buttons["Save and confirm vehicle"].tap()
        XCTAssertTrue(app.buttons["place.coordinates.toggle"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.textFields["Latitude"].exists)
        XCTAssertFalse(app.textFields["Longitude"].exists)
        app.buttons["place.coordinates.toggle"].tap()
        XCTAssertTrue(app.textFields["Latitude"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.textFields["Longitude"].exists)
    }

    func testAddressCandidatesRemainAfterUsingMapAddress() {
        let app = launch("--ui-testing-report", "--ui-testing-map-address")
        XCTAssertTrue(app.textFields["Registration number"].waitForExistence(timeout: 5))
        app.textFields["Registration number"].tap()
        app.textFields["Registration number"].typeText("003 PUK")
        app.buttons["Save and confirm vehicle"].tap()

        let openMap = app.buttons["place.map.open"]
        XCTAssertTrue(openMap.waitForExistence(timeout: 3))
        openMap.tap()
        let useAddress = app.buttons["map.use.address"]
        XCTAssertTrue(useAddress.waitForExistence(timeout: 5))
        useAddress.tap()

        XCTAssertTrue(app.buttons["place.candidate.STREET:lastekodu tn, tallinn"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["place.candidate.BUILDING:lastekodu tn 42, tallinn"].exists)
        XCTAssertEqual(app.textFields["Address"].value as? String, "Lastekodu tn 42, Tallinn")
    }

    func testSettingsCategoriesUseSaveDismissAndTemplatesLiveOnlyHere() {
        let app = launch()
        app.tabBars.buttons["Settings"].tap()
        for identifier in ["settings.language", "settings.profile", "settings.recipient", "settings.templates", "settings.privacy"] {
            XCTAssertTrue(app.buttons[identifier].waitForExistence(timeout: 3), identifier)
        }
        XCTAssertFalse(app.buttons["settings.recognition"].exists)

        app.buttons["settings.profile"].tap()
        XCTAssertTrue(app.buttons["Save"].waitForExistence(timeout: 3))
        app.navigationBars.buttons["Cancel"].tap()

        app.buttons["settings.privacy"].tap()
        XCTAssertTrue(app.staticTexts["Local storage"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["Cloud recognition"].exists)
        XCTAssertFalse(app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'Worker'")).firstMatch.exists)
        app.navigationBars.buttons["Done"].tap()

        app.buttons["settings.templates"].tap()
        app.navigationBars.buttons["Add custom template"].tap()
        let name = app.textFields["Template name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("Blocked access")
        let description = app.textFields["Estonian description"]
        description.tap()
        description.typeText("Sõiduk blokeerib läbipääsu.")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Blocked access"].waitForExistence(timeout: 3))
    }

    func testHistoryOpenDeleteAndNewReportActions() {
        var app = launch("--ui-testing-history")
        let report = app.buttons["history.report.ui-test-report"]
        XCTAssertTrue(report.waitForExistence(timeout: 5))
        report.tap()
        XCTAssertTrue(app.staticTexts["Check the vehicle"].waitForExistence(timeout: 3))
        app.buttons["Close"].tap()
        app.buttons["New report"].tap()
        XCTAssertTrue(app.buttons["camera.review"].waitForExistence(timeout: 3))

        app.terminate()
        app = launch("--ui-testing-history")
        let menu = app.buttons["history.report.menu.ui-test-report"]
        XCTAssertEqual(menu.label, "More actions")
        menu.tap()
        app.buttons["Delete"].tap()
        XCTAssertTrue(app.staticTexts["Delete this report?"].waitForExistence(timeout: 3))
        app.buttons["Delete"].tap()
        XCTAssertTrue(app.staticTexts["No reports yet"].waitForExistence(timeout: 3))
    }

    func testMockedMailFallbackChangesStatusOnlyAfterExplicitYes() {
        let app = launch("--ui-testing-ready", "--ui-testing-mail-fallback")
        XCTAssertTrue(app.buttons["Open mail app"].waitForExistence(timeout: 5))
        app.buttons["Open mail app"].tap()
        XCTAssertTrue(app.staticTexts["Did a mail draft open?"].waitForExistence(timeout: 3))
        app.buttons["No"].tap()
        XCTAssertTrue(app.staticTexts["Ready to open in mail"].exists)
        app.buttons["Open mail app"].tap()
        app.buttons["Yes"].tap()
        app.buttons["Close"].tap()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["Opened in mail"].waitForExistence(timeout: 3))
    }

    private func launch(_ arguments: String...) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"] + arguments + ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private func fillVehicleAndPlace(_ app: XCUIApplication) {
        let plate = app.textFields["Registration number"]
        plate.tap()
        plate.typeText("003 PUK")
        app.buttons["Save and confirm vehicle"].tap()
        let address = app.textFields["Address"]
        XCTAssertTrue(address.waitForExistence(timeout: 3))
        address.tap()
        address.typeText("Lastekodu tn 42, Tallinn")
        app.buttons["Confirm place"].tap()
        XCTAssertTrue(app.staticTexts["Choose the violation"].waitForExistence(timeout: 3))
    }
}
