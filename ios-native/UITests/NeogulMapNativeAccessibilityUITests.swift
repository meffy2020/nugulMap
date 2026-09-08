import XCTest

@MainActor
final class NeogulMapNativeAccessibilityUITests: XCTestCase {
    private let fixtureBaseURL = "http://127.0.0.1:18765"
    private let mapRepresentationLabels: Set<String> = [
        "흡연구역 지도",
        "실시간 핫플 지도",
        "팝업 지도",
        "추천 지도",
        "현재 지도에 표시할 흡연구역이 없습니다.",
        "현재 지도에 표시할 실시간 핫플이 없습니다.",
        "현재 지도에 표시할 성수 팝업이 없습니다.",
        "현재 지도에 표시할 추천 장소가 없습니다."
    ]
    // Partial sheets retain raster map tiles behind them; audit their actual controls
    // without running OCR against the non-accessibility SDK basemap labels.
    private let scrollableSheetAuditTypes: XCUIAccessibilityAuditType = [
        .contrast,
        .hitRegion,
        .sufficientElementDescription,
        .trait
    ]

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
    }

    func testHomeAccessibilityAudit() throws {
        let app = launchApp()

        XCTAssertTrue(app.textFields["장소·주소 검색"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["현재 위치"].exists)
        try performAppAccessibilityAudit(app)
        keepScreenshot(named: "home", app: app)
    }

    func testSupportedLandscapeKeepsPrimaryControlsAccessible() throws {
        let app = launchApp()
        XCTAssertTrue(app.textFields["장소·주소 검색"].waitForExistence(timeout: 15))

        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }

        let landscapePredicate = NSPredicate { _, _ in
            app.frame.width > app.frame.height
        }
        let landscapeExpectation = XCTNSPredicateExpectation(
            predicate: landscapePredicate,
            object: app
        )
        var landscapeResult = XCTWaiter.wait(for: [landscapeExpectation], timeout: 2)
        if landscapeResult != .completed {
            XCUIDevice.shared.orientation = .landscapeRight
            let retryExpectation = XCTNSPredicateExpectation(
                predicate: landscapePredicate,
                object: app
            )
            landscapeResult = XCTWaiter.wait(for: [retryExpectation], timeout: 5)
        }
        XCTAssertEqual(landscapeResult, .completed)

        let searchField = app.textFields["장소·주소 검색"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["장소·주소 검색"].exists)
        XCTAssertFalse(app.buttons["실시간 핫플 보기"].exists)
        XCTAssertFalse(app.buttons["팝업 보기"].exists)
        XCTAssertTrue(app.buttons["현재 위치"].exists)
        XCTAssertGreaterThan(app.frame.width, app.frame.height)
        XCTAssertGreaterThan(searchField.frame.width, searchField.frame.height * 3)
        XCTAssertTrue(app.frame.contains(searchField.frame))
        // Raster basemap labels have an equivalent accessibility marker list;
        // OCR cannot associate those SDK-drawn street labels with an AX element.
        try performAppAccessibilityAudit(app, auditTypes: .all.subtracting(.elementDetection))
        keepScreenshot(named: "home-landscape", app: app)
    }

    func testSettingsAccessibilityAudit() throws {
        let app = launchApp()

        let settingsButton = app.buttons["설정 열기"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 15))
        settingsButton.tap()

        XCTAssertTrue(app.navigationBars["설정"].waitForExistence(timeout: 10))
        XCTAssertTrue(accessibleElement(in: app, label: "개인정보 처리방침").exists)
        XCTAssertTrue(accessibleElement(in: app, label: "로그인 연결 관리").exists)
        try performAppAccessibilityAudit(app)

        app.collectionViews.firstMatch.swipeUp()
        XCTAssertTrue(
            accessibleElement(in: app, label: "콘텐츠 신고 및 고객지원")
                .waitForExistence(timeout: 5)
        )
        try performAppAccessibilityAudit(app)
        keepScreenshot(named: "settings", app: app)
    }

    func testLoginAndSignInWithAppleAccessibilityAudit() throws {
        let app = launchApp()

        let accountButton = app.buttons["계정 열기"]
        XCTAssertTrue(accountButton.waitForExistence(timeout: 15))
        accountButton.tap()

        XCTAssertTrue(app.buttons["sign-in-with-apple"].waitForExistence(timeout: 10))
        XCTAssertTrue(accessibleElement(in: app, label: "서비스 이용약관").exists)
        XCTAssertTrue(accessibleElement(in: app, label: "개인정보 처리방침").exists)
        try performAppAccessibilityAudit(app)
        keepScreenshot(named: "login", app: app)
    }

    func testAccountDeletionIsDiscoverableAndExplicit() throws {
        let app = launchApp(authenticated: true)

        let accountButton = app.buttons["계정 열기"]
        XCTAssertTrue(accountButton.waitForExistence(timeout: 15))
        accountButton.tap()

        let deleteButton = app.buttons["계정 삭제"]
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 10))
        try performAppAccessibilityAudit(app)
        deleteButton.tap()

        XCTAssertTrue(app.alerts["계정을 삭제할까요?"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["계정, 프로필, 로그인 토큰, 작성한 리뷰, 제보한 장소와 첨부 이미지가 모두 삭제됩니다."].exists)
        try performAppAccessibilityAudit(
            app,
            auditTypes: [
                .contrast,
                .elementDetection,
                .hitRegion,
                .sufficientElementDescription,
                .textClipped,
                .trait
            ]
        )
        keepScreenshot(named: "account-deletion-confirmation", app: app)
    }

    func testLocationDenialKeepsSearchAndManualNavigationAvailable() throws {
        let app = launchApp(resetLocationPermission: true)
        addUIInterruptionMonitor(withDescription: "Location permission") { alert in
            let denyLabels = ["허용 안 함", "Don’t Allow"]
            for label in denyLabels where alert.buttons[label].exists {
                alert.buttons[label].tap()
                return true
            }
            return false
        }

        let locationButton = app.buttons["현재 위치"]
        XCTAssertTrue(locationButton.waitForExistence(timeout: 15))
        locationButton.tap()
        locationButton.tap()

        let statusMessage = accessibleElement(in: app, identifier: "map-status-message")
        if !statusMessage.waitForExistence(timeout: 2) {
            XCTAssertTrue(app.buttons["현재 위치"].waitForExistence(timeout: 2))
            app.buttons["현재 위치"].tap()
        }
        XCTAssertTrue(statusMessage.waitForExistence(timeout: 6))
        XCTAssertEqual(statusMessage.label, "위치 권한을 허용했는지 확인해주세요.")
        XCTAssertTrue(app.textFields["장소·주소 검색"].exists)
        XCTAssertFalse(app.buttons["팝업 보기"].exists)
        try performAppAccessibilityAudit(app)
        keepScreenshot(named: "location-denied", app: app)
    }

    func testSmokingOnlyHomeHasNoExtraFeatures() throws {
        let app = launchApp()
        XCTAssertTrue(app.textFields["장소·주소 검색"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["추천 보기"].exists)
        XCTAssertFalse(app.buttons["실시간 핫플 보기"].exists)
        XCTAssertFalse(app.buttons["팝업 보기"].exists)
        XCTAssertFalse(app.buttons["주변 편지 열기"].exists)
        keepScreenshot(named: "smoking-only-home", app: app)
    }

    func testMapPanReloadsZonesAndMarkerOpensDirections() async throws {
        let app = launchApp()
        let marker = app.buttons["시뮬레이터 테스트 위치, 시뮬레이터 테스트 위치"]
        XCTAssertTrue(marker.waitForExistence(timeout: 15))
        keepScreenshot(named: "smoking-marker-home", app: app)
        let before = try await boundsRequestCount()
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: 0.5))
            .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.4, dy: 0.5)), withVelocity: .slow, thenHoldForDuration: 0.5)
        var after = before
        for _ in 0..<30 {
            after = try await boundsRequestCount()
            if after > before { break }
            try await Task.sleep(for: .milliseconds(200))
        }
        XCTAssertGreaterThan(after, before, "지도 이동 후 흡연구역 API를 다시 조회해야 합니다")
        XCTAssertTrue(marker.waitForExistence(timeout: 10))
        keepScreenshot(named: "smoking-marker-after-pan", app: app)
        // Restart at the known fixture center to test a physical marker tap
        // independently of Kakao's device-dependent pan inertia.
        app.terminate()
        app.launch()
        XCTAssertTrue(marker.waitForExistence(timeout: 15))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let directions = app.buttons["길찾기 시작"]
        XCTAssertTrue(directions.waitForExistence(timeout: 10))
        if !directions.isHittable { app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(directions.isHittable)
        keepScreenshot(named: "smoking-marker-detail", app: app)
        directions.tap()
        let maps = XCUIApplication(bundleIdentifier: "com.apple.Maps")
        XCTAssertTrue(maps.wait(for: .runningForeground, timeout: 15))
        keepScreenshot(named: "smoking-directions-open", app: maps)
    }

    private func boundsRequestCount() async throws -> Int {
        let (data, _) = try await URLSession.shared.data(from: URL(string: fixtureBaseURL + "/__test/bounds-request-count")!)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Int]
        return try XCTUnwrap(object?["count"])
    }

    func testUgcConsentReportAndBlockControlsAreReviewable() throws {
        let app = launchApp(authenticated: true, selectedZoneID: 42)

        XCTAssertTrue(app.buttons["장소 또는 사진 신고"].waitForExistence(timeout: 10))
        XCTAssertTrue(
            app.buttons["필수 약관 및 커뮤니티 운영정책 동의"]
                .waitForExistence(timeout: 10)
        )
        XCTAssertTrue(app.buttons["신고"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["차단"].waitForExistence(timeout: 10))
        let detailScrollView = app.scrollViews.firstMatch
        XCTAssertTrue(detailScrollView.exists)
        detailScrollView.swipeUp()
        keepScreenshot(named: "ugc-review-policy", app: app)
        try performAppAccessibilityAudit(
            app,
            auditTypes: scrollableSheetAuditTypes,
            visibleFrame: detailScrollView.frame
        )
        detailScrollView.swipeUp()
        try performAppAccessibilityAudit(
            app,
            auditTypes: scrollableSheetAuditTypes,
            visibleFrame: detailScrollView.frame
        )
        detailScrollView.swipeDown()

        app.buttons["신고"].tap()
        XCTAssertTrue(app.sheets["리뷰 신고"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["개인정보 노출"].exists)
        try performAppAccessibilityAudit(app, auditTypes: scrollableSheetAuditTypes)
        keepScreenshot(named: "ugc-review-report", app: app)
        app.buttons["개인정보 노출"].tap()
        XCTAssertTrue(app.sheets["리뷰 신고"].waitForNonExistence(timeout: 5))

        app.buttons["차단"].tap()
        XCTAssertTrue(app.alerts["사용자 차단"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["사용자 차단"].buttons["차단"].exists)
        try performAppAccessibilityAudit(app, auditTypes: scrollableSheetAuditTypes)
        keepScreenshot(named: "ugc-user-block", app: app)
    }

    @discardableResult
    private func launchApp(
        initialSearch: String? = nil,
        initialLayer: String? = nil,
        selectedRecommendationKey: String? = nil,
        authenticated: Bool = false,
        selectedZoneID: Int? = nil,
        locationLetters: Bool = false,
        resetLocationPermission: Bool = false
    ) -> XCUIApplication {
        let app = XCUIApplication()
        if resetLocationPermission { app.resetAuthorizationStatus(for: .location) }
        app.launchEnvironment["NUGUL_API_BASE_URL"] = fixtureBaseURL
        app.launchEnvironment["NUGUL_UI_TEST_RESET_SESSION"] = "1"
        app.launchEnvironment["NUGUL_INITIAL_SEARCH_QUERY"] = initialSearch ?? ""
        app.launchEnvironment["NUGUL_UI_TEST_INITIAL_LAYER"] = initialLayer ?? ""
        app.launchEnvironment["NUGUL_UI_TEST_SELECTED_RECOMMENDATION_KEY"] = selectedRecommendationKey ?? ""
        if initialLayer == "recommendations" {
            app.launchEnvironment["NUGUL_UI_TEST_RECOMMENDATION_FIXTURE"] = "1"
        }
        app.launchEnvironment["NUGUL_UI_TEST_SELECTED_ZONE_ID"] = selectedZoneID.map(String.init) ?? ""
        app.launchEnvironment["NUGUL_UI_TEST_LOCATION_LETTERS"] = locationLetters ? "1" : ""
        if authenticated {
            app.launchEnvironment["NUGUL_UI_TEST_AUTH_TOKEN"] = "ui-test-access-token"
        }
        app.launchArguments += ["-AppleLanguages", "(ko)", "-AppleLocale", "ko_KR"]
        app.launch()
        return app
    }

    private func keepScreenshot(named name: String, app: XCUIApplication) {
        // Whole-screen capture preserves iPad rotation geometry; app cropping can
        // apply portrait pixel bounds to an already-landscape surface.
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func accessibleElement(in app: XCUIApplication, label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label))
            .firstMatch
    }

    private func accessibleElement(in app: XCUIApplication, identifier: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", identifier))
            .firstMatch
    }

    private func accessibleElement(
        in app: XCUIApplication,
        containingLabels labels: [String]
    ) -> XCUIElement {
        let predicates = labels.map { NSPredicate(format: "label CONTAINS %@", $0) }
        return app.descendants(matching: .any)
            .matching(NSCompoundPredicate(andPredicateWithSubpredicates: predicates))
            .firstMatch
    }

    private func performAppAccessibilityAudit(
        _ app: XCUIApplication,
        auditTypes: XCUIAccessibilityAuditType = .all,
        visibleFrame: CGRect? = nil
    ) throws {
        var unhandledIssues: [String] = []
        try app.performAccessibilityAudit(for: auditTypes) { issue in
            let element = issue.element
            let label = element?.label ?? ""
            let isKnownMapRepresentationContrast = issue.auditType == .contrast
                && element?.identifier.hasPrefix("map-accessibility-representation-") == true
                && (element?.elementType == .staticText || element?.elementType == .button)
            if isKnownMapRepresentationContrast {
                print("Handled Kakao map representation contrast audit for \(label)")
                return true
            }

            let isClippedScrollContentContrast = issue.auditType == .contrast
                && visibleFrame.map { $0.contains(element?.frame ?? .null) } == false
            if isClippedScrollContentContrast {
                print("Handled clipped scroll content contrast audit for \(label)")
                return true
            }

            let description = "type=\(issue.auditType.rawValue) "
                + "elementType=\(element?.elementType.rawValue ?? 0) "
                + "identifier=\(element?.identifier ?? "") frame=\(String(describing: element?.frame)) "
                + "label=\(label) compact=\(issue.compactDescription) "
                + "detail=\(issue.detailedDescription)"
            unhandledIssues.append(description)
            print("Accessibility issue \(description)")
            return true
        }
        if !unhandledIssues.isEmpty {
            XCTFail("Unhandled accessibility issues:\n" + unhandledIssues.joined(separator: "\n"))
        }
    }
}
