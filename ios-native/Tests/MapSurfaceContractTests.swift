import Foundation

@main
private enum MapSurfaceContractTests {
    static func main() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let mapViewPath = iosRoot.appendingPathComponent("NeogulMapNative/Views/ZoneMapView.swift")
        let zoneDetailPath = iosRoot.appendingPathComponent("NeogulMapNative/Views/ZoneDetailView.swift")
        let apiClientPath = iosRoot.appendingPathComponent("NeogulMapNative/Services/NugulAPIClient.swift")
        let contentViewPath = iosRoot.appendingPathComponent("NeogulMapNative/ContentView.swift")
        let explorerModelPath = iosRoot.appendingPathComponent("NeogulMapNative/ViewModels/ZoneExplorerModel.swift")
        let reviewModelPath = iosRoot.appendingPathComponent("NeogulMapNative/Models/ZoneReview.swift")
        let privacyManifestPath = iosRoot.appendingPathComponent("NeogulMapNative/PrivacyInfo.xcprivacy")
        let intentsPath = iosRoot.appendingPathComponent("NeogulMapNative/Intents/NugulMapIntents.swift")
        let mapView = try String(contentsOf: mapViewPath, encoding: .utf8)
        let zoneDetail = try String(contentsOf: zoneDetailPath, encoding: .utf8)
        let apiClient = try String(contentsOf: apiClientPath, encoding: .utf8)
        let authTokenStore = sourceSection(apiClient, from: "enum AuthTokenStore", to: "private extension Data")
        let contentView = try String(contentsOf: contentViewPath, encoding: .utf8)
        let explorerModel = try String(contentsOf: explorerModelPath, encoding: .utf8)
        let reviewModel = try String(contentsOf: reviewModelPath, encoding: .utf8)
        let privacyManifestData = try Data(contentsOf: privacyManifestPath)
        let privacyManifest = String(decoding: privacyManifestData, as: UTF8.self)
        let intents = try String(contentsOf: intentsPath, encoding: .utf8)
        expect(mapView.contains("private var presentedLayerMode: Season2LayerMode { .zones }"), "출시 지도 흡연구역 고정")
        expect(!mapView.contains("MapLayerControl("), "레이어 선택 진입점 제거")
        expect(!mapView.contains("onOpenUtility(.locationLetters)"), "편지 진입점 제거")
        expect(!mapView.contains(".fullScreenCover("), "편지 화면 연결 제거")
        expect(!explorerModel.contains("await loadRecommendationAccountState()"), "로그인 후 추천 자동 요청 없음")
        let settled = sourceSection(mapView, from: "onRegionSettled:", to: "onSelectZone:")
        expect(settled.contains("await model.loadZones(in: .visibleRegion(region))"), "지도 이동 종료 시 표시 영역 흡연구역 갱신")
        expect(!settled.contains("loadRecommendations"), "지도 이동 시 추천 자동 요청 없음")
        let search = sourceSection(explorerModel, from: "func search()", to: "func resetSearch()")
        expect(!search.contains("fetchMapInsights") && !search.contains("loadInsights"), "검색은 흡연구역만 조회")

        let magnifierCount = mapView.components(separatedBy: "Image(systemName: \"magnifyingglass\")").count - 1
        expect(magnifierCount == 1, "검색 필드 오른쪽 검색 버튼에 돋보기 하나만 표시")
        expect(!mapView.contains("Text(\"검색\")"), "검색 텍스트 버튼 제거")
        expect(
            mapView.contains("Button(action: onSearch) {") &&
                mapView.contains("Image(systemName: \"magnifyingglass\")"),
            "돋보기를 검색 실행 버튼으로 연결"
        )
        expect(mapView.contains("ScrollView(.horizontal, showsIndicators: false)"), "지도 필터를 한 줄 가로 칩으로 표시")
        expect(mapView.contains("private struct MapFilterChip"), "개별 흰색 필터 칩 사용")
        expect(mapView.contains(".frame(minHeight: 44)"), "필터 칩 44pt 터치 영역 유지")
        expect(mapView.contains(".frame(minHeight: 54)"), "검색 필드의 지도 앱형 높이")
        expect(!mapView.contains("Image(systemName: \"line.3.horizontal\")"), "상단 검색창 왼쪽 햄버거 제거")
        expect(!mapView.contains("let onMenu: () -> Void"), "검색창 메뉴 의존성 제거")
        expect(mapView.contains(".accessibilityLabel(\"계정 열기\")"), "하단 계정 버튼 VoiceOver 라벨")
        expect(mapView.contains(".accessibilityLabel(\"설정 열기\")"), "하단 설정 버튼 VoiceOver 라벨")
        expect(mapView.contains(".frame(width: 48, height: 48)"), "하단 계정·설정 48pt 터치 영역")
        expect(mapView.contains(".accessibilityLabel(\"내 장소 삭제\")"), "내 장소 삭제 44pt 접근성 컨트롤")
        expect(mapView.contains(".accessibilityLabel(\"흡연구역 등록 닫기\")"), "제보 화면 닫기 접근성 컨트롤")
        expect(mapView.contains("closeButton.accessibilityLabel = \"장소 상세 닫기\""), "UIKit 상세 카드 닫기 VoiceOver 라벨")
        expect(mapView.contains("closeButton.widthAnchor.constraint(equalToConstant: 44)"), "UIKit 상세 카드 닫기 44pt 너비")
        expect(mapView.contains("closeButton.heightAnchor.constraint(equalToConstant: 44)"), "UIKit 상세 카드 닫기 44pt 높이")
        expect(mapView.contains(".accessibilityLabel(\"프로필 사진 선택\")"), "프로필 사진 선택 VoiceOver 라벨")
        let reportZoneSheet = sourceSection(mapView, from: "private struct ReportZoneSheet", to: "private struct ReportZoneType")
        let loginSheet = sourceSection(mapView, from: "private struct LoginSheet", to: "private struct ProfileSheet")
        let profileSheet = sourceSection(mapView, from: "private struct ProfileSheet", to: "private enum ProfileTab")
        let profileSetupSheet = sourceSection(mapView, from: "private struct ProfileSetupSheet", to: "private struct SummaryPill")
        expect(reportZoneSheet.contains("ScrollView"), "흡연구역 등록 sheet 큰 글자·compact-height 스크롤")
        expect(
            mapView.contains(".presentationDetents([.height(340), .medium, .large])"),
            "흡연구역 등록 sheet 컴팩트 높이 유지와 큰 화면 fallback"
        )
        expect(loginSheet.contains("ScrollView"), "로그인 sheet compact-height 스크롤")
        expect(profileSheet.contains("ScrollView"), "프로필 sheet 계정 삭제까지 스크롤")
        expect(profileSetupSheet.contains("ScrollView"), "가입 설정 sheet compact-height 스크롤")
        expect(
            mapView.components(separatedBy: ".presentationDetents([.large])").count - 1 >= 3,
            "로그인·프로필·가입 설정 sheet 큰 기본 높이"
        )
        expect(profileSheet.contains("accountDeletionErrorMessage"), "계정 삭제 실패를 Profile sheet 내부에 표시")
        expect(!mapView.contains("Color.nugulMutedText.opacity(0.72)"), "로그인 법적 문구 대비 저하 제거")
        expect(mapView.contains("Link(\"서비스 이용약관\", destination: AppConfig.termsURL)"), "로그인 화면 서비스 약관 링크")
        expect(mapView.contains("Link(\"개인정보 처리방침\", destination: AppConfig.privacyPolicyURL)"), "로그인 화면 개인정보 처리방침 링크")
        expect(
            loginSheet.components(separatedBy: ".frame(minHeight: 44)").count - 1 >= 2,
            "로그인 정책 링크 44pt 터치 영역"
        )
        expect(mapView.contains(".accessibilityIdentifier(\"map-status-message\")"), "지도 상태 메시지 접근성 식별자")
        expect(mapView.contains(".accessibilityElement(children: .combine)"), "지도 상태 메시지 한 요소로 읽기")
        expect(explorerModel.contains("@Published private(set) var locationErrorMessage"), "위치 거부 안내를 화면 모델이 발행")
        expect(mapView.contains("DispatchQueue.main.asyncAfter(deadline: .now() + 10"), "현재 위치 요청 10초 제한")
        expect(mapView.contains("현재 위치 응답이 늦어지고 있습니다"), "현재 위치 요청 timeout 안내")
        expect(mapView.contains("model.setLocationErrorMessage(\"현재 위치로 이동했습니다.\")"), "현재 위치 이동 성공 안내")
        expect(mapView.contains(".sheet(item: $model.selectedZone)"), "지도 마커에서 리뷰 상세 화면 진입")
        expect(
            zoneDetail.contains("Button(action: onReport)") &&
                zoneDetail.contains("Text(\"신고\")") &&
                zoneDetail.contains(".frame(minWidth: 44, minHeight: 44)"),
            "리뷰 신고 버튼 44pt 터치 영역 노출"
        )
        expect(
            zoneDetail.contains("Button(role: .destructive, action: onBlock)") &&
                zoneDetail.contains("Text(\"차단\")") &&
                zoneDetail.contains(".frame(minWidth: 44, minHeight: 44)"),
            "리뷰 작성자 차단 버튼 44pt 터치 영역 노출"
        )
        expect(zoneDetail.contains("canReport: review.authorId == nil"), "작성자 식별자가 없는 레거시 리뷰도 신고 가능")
        expect(zoneDetail.contains("canBlock: review.authorId != nil"), "작성자가 확인되는 경우에만 사용자 차단")
        expect(
            apiClient.contains("/api/zones/\\(zoneID)/reviews/\\(reviewID)/reports"),
            "리뷰 신고 API 연결"
        )
        expect(apiClient.contains("/api/users/\\(authorID)/block"), "사용자 차단 API 연결")
        expect(apiClient.contains("/api/zones/\\(zoneID)/reports"), "장소 신고 API 연결")
        expect(apiClient.contains("userSafeServerMessage"), "iOS 서버 오류 사용자 문구 필터")
        ["api", "provider", "configured", "exception", "localhost", "키가"].forEach { token in
            expect(apiClient.contains("\"\(token)\""), "iOS 기술 용어 필터: \(token)")
        }
        expect(zoneDetail.contains("Text(\"장소 신고\")"), "장소 신고 진입점")
        expect(zoneDetail.contains(".accessibilityLabel(\"장소 또는 사진 신고\")"), "장소 신고 VoiceOver 라벨")
        expect(zoneDetail.contains("submitZoneReport(reason: \"INACCURATE\")"), "장소 신고 사유 선택")
        expect(zoneDetail.contains(".frame(minWidth: 100, minHeight: 44)"), "장소 신고 44pt 터치 영역")
        expect(apiClient.contains("manualAppleRevocationRequired"), "Apple 수동 연결 해제 응답 처리")
        expect(apiClient.contains("authorizationCode: authorizationCode"), "Apple 로그인 authorizationCode 전달")
        expect(mapView.contains("Apple 로그인 연결 해제"), "계정 삭제 후 Apple 연결 해제 안내")
        expect(mapView.contains("model.handleAppleSignInFailure(error)"), "Apple 로그인 실패를 사용자에게 표시")
        expect(mapView.contains("model.accountDeletionNotice"), "서버 계정 삭제 안내문 표시")
        expect(contentView.contains("credentialRevokedNotification"), "Apple 연결 해제 알림 시 로컬 세션 정리")
        expect(apiClient.contains("PendingOAuthTransactionStore"), "OAuth PKCE verifier 보안 저장소")
        expect(apiClient.contains("kSecAttrAccessibleWhenUnlockedThisDeviceOnly"), "로그인 보안 정보 기기 전용 Keychain 보호")
        expect(authTokenStore.contains("guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else"), "Keychain 저장 실패 감지")
        expect(authTokenStore.contains("static func replaceLoginSession(") && authTokenStore.contains(") throws {"), "로그인 세션 저장 실패 전파")
        expect(
            authTokenStore.contains("catch {\n            deleteTokens()\n            throw NugulAPIError.secureStorageUnavailable"),
            "로그인 세션 일부 저장 실패 시 Keychain rollback"
        )
        expect(
            explorerModel.components(separatedBy: "try AuthTokenStore.replaceLoginSession(").count - 1 == 3,
            "모든 로그인 경로가 Keychain 저장 성공 뒤에만 사용자 상태 갱신"
        )
        expect(apiClient.contains("let clientState: String"), "OAuth client_state 트랜잭션 저장")
        expect(apiClient.contains("static func constantTimeEquals"), "OAuth client_state 상수 시간 비교")
        expect(
            apiClient.contains("transactionStore.claim(") &&
                apiClient.contains("clientState: callback.clientState"),
            "검증된 client_state로 callback claim"
        )
        expect(apiClient.contains("isInteractiveSignInInProgress"), "활성 OAuth callback과 복구 callback의 중복 교환 차단")
        expect(mapView.contains("model.resumeOAuthSignIn(from: url)"), "프로세스 재시작 뒤 OAuth callback 복구")
        expect(explorerModel.contains("validateAppleCredentialStateIfNeeded"), "앱 시작·복귀 시 Apple 자격 상태 확인")
        expect(apiClient.contains("getCredentialState(forUserID: userIdentifier)"), "Apple 사용자별 credential state 조회")
        expect(apiClient.contains("userIdentifier: credential.user"), "Apple 사용자 식별자 보관 연결")
        expect(explorerModel.contains("reviewLoadErrorByZoneID"), "리뷰 실패와 빈 결과 구분")
        expect(explorerModel.contains("userZonesErrorMessage"), "내 장소 실패와 빈 결과 구분")
        expect(explorerModel.contains("mapErrorMessage"), "지도 인증 실패를 빈 지도와 구분")
        expect(!mapView.contains("http://maps.apple.com"), "지도 길찾기 링크 HTTPS 사용")
        expect(!zoneDetail.contains("http://maps.apple.com"), "상세 길찾기 링크 HTTPS 사용")
        expect(zoneDetail.contains("개인정보, 욕설, 광고는 등록할 수 없습니다"), "리뷰 작성 전 공개 콘텐츠 안내")
        expect(zoneDetail.contains("필수 · 이용약관 및 커뮤니티 운영정책에 동의합니다"), "UGC 필수 정책 동의")
        expect(zoneDetail.contains("Link(\"약관·운영정책 보기\", destination: AppConfig.termsURL)"), "UGC 정책 링크")
        expect(zoneDetail.contains("|| !isReviewPolicyAccepted"), "리뷰 등록 전 정책 동의 강제")
        expect(mapView.contains("UgcPolicyConsentRow(isAccepted: $isPolicyAccepted)"), "장소 제보 정책 동의")
        expect(mapView.contains("model.isSubmittingZone || isAddressLoading || !isPolicyAccepted"), "장소 제보 전 정책 동의 강제")
        expect(zoneDetail.contains(".frame(minWidth: 44, minHeight: 44)"), "신고/차단 44pt 터치 영역")
        expect(!reviewModel.contains("authorEmail"), "공개 리뷰 응답 이메일 비수집")
        expect(intents.contains("fetchZonesByBounds(.centralSeoul)"), "App Shortcut 장소 제안도 실제 API 사용")
        expect(!intents.contains("SmokingZone.fallback"), "App Shortcut 가짜 장소 fallback 제거")
        [
            "NSPrivacyCollectedDataTypeName",
            "NSPrivacyCollectedDataTypeEmailAddress",
            "NSPrivacyCollectedDataTypeUserID",
            "NSPrivacyCollectedDataTypePreciseLocation",
            "NSPrivacyCollectedDataTypePhotosorVideos",
            "NSPrivacyCollectedDataTypeOtherUserContent"
        ].forEach { privacyType in
            expect(privacyManifest.contains(privacyType), "개인정보 매니페스트: \(privacyType)")
        }
        let privacyRoot = try PropertyListSerialization.propertyList(
            from: privacyManifestData,
            options: [],
            format: nil
        ) as? [String: Any]
        let collectedDataTypes = privacyRoot?["NSPrivacyCollectedDataTypes"] as? [[String: Any]]
        let productInteraction = collectedDataTypes?.first {
            $0["NSPrivacyCollectedDataType"] as? String == "NSPrivacyCollectedDataTypeProductInteraction"
        }
        expect(productInteraction == nil, "추천 행동 수집 선언 제거")

        print("PASS: MapSurfaceContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }

    private static func sourceSection(_ source: String, from start: String, to end: String) -> String {
        guard let startRange = source.range(of: start),
              let endRange = source.range(of: end, range: startRange.upperBound..<source.endIndex) else {
            return ""
        }
        return String(source[startRange.lowerBound..<endRange.lowerBound])
    }
}
