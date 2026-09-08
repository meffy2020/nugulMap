import Foundation

@main
private enum Season3InsightPresentationTests {
    static func main() {
        let reviewDate = ISO8601DateFormatter().date(from: "2026-07-12T12:00:00+09:00")!
        let currentPlace = Hotplace(
            id: "current",
            name: "성수 연무장길",
            category: "hotplace",
            crowdLevel: "붐빔",
            crowdMessage: "사람이 많습니다.",
            estimatedMinPeople: 1_200,
            estimatedMaxPeople: 1_500,
            latitude: 37.5446,
            longitude: 127.0557,
            address: "서울 성동구",
            source: "SEOUL_CITYDATA",
            sourcePlaceCode: "POI001",
            updatedAt: "2026-07-10T10:00:00+09:00",
            freshnessStatus: "CURRENT",
            ageSeconds: 90
        )
        expect(currentPlace.comparisonStatusLabel == "방금 업데이트 · 1분 경과", "CURRENT 비교 상태")

        let popup = TrendEvent(
            id: "popup-1",
            title: "성수 여름 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: "2026-07-20",
            latitude: 37.543,
            longitude: 127.052,
            address: "서울 성동구 연무장길",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: "culture-123",
            detailUrl: "https://example.com/popup/123",
            collectedAt: "2026-07-10T09:30:00+09:00",
            attribution: "서울특별시 문화행사 정보",
            sourceUrl: "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            license: "공공누리 제1유형",
            licenseUrl: "https://www.kogl.or.kr/info/licenseType1.do"
        )
        expect(popup.kindLabel == "팝업", "팝업 종류")
        expect(popup.isPopup, "팝업 kind 판별")
        expect(popup.isVerifiedSeongsuPopup(at: reviewDate), "성수 위치와 제목을 확인한 팝업")
        expect(popup.dateRangeLabel == "2026-07-10 ~ 2026-07-20", "팝업 기간")
        expect(popup.sourceLabel == "서울특별시 문화행사 정보", "서버 attribution을 사용자용 출처로 표시")
        expect(popup.safeDetailURL?.absoluteString == "https://example.com/popup/123", "안전한 원문 URL")
        expect(
            popup.safeAttributionURL?.absoluteString
                == "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "안전한 공식 데이터셋 URL"
        )
        expect(popup.licenseLabel == "공공누리 제1유형", "사용자용 이용조건")
        expect(popup.safeLicenseURL?.host == "www.kogl.or.kr", "안전한 이용조건 URL")

        let decodedPopup = try? JSONDecoder().decode(
            TrendEvent.self,
            from: Data(
                """
                {
                  "id": "decoded-popup",
                  "title": "성수 디코딩 팝업",
                  "latitude": 37.543,
                  "longitude": 127.052,
                  "source": "SEOUL_CULTURE_API",
                  "attribution": "서울특별시 문화행사 정보",
                  "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                  "license": "공공누리 제1유형",
                  "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do"
                }
                """.utf8
            )
        )
        expect(decodedPopup?.attribution == "서울특별시 문화행사 정보", "팝업 응답 attribution 디코딩")
        expect(decodedPopup?.sourceLabel == "서울특별시 문화행사 정보", "디코딩한 attribution 출처 라벨")
        expect(
            decodedPopup?.sourceUrl == "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "팝업 응답 sourceUrl 디코딩"
        )
        expect(decodedPopup?.license == "공공누리 제1유형", "팝업 응답 license 디코딩")
        expect(decodedPopup?.licenseUrl == "https://www.kogl.or.kr/info/licenseType1.do", "팝업 응답 licenseUrl 디코딩")

        let incompletePopup = TrendEvent(
            id: "popup-incomplete",
            title: "성수 링크 없는 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: "2026-07-20",
            latitude: 37.543,
            longitude: 127.052,
            address: "서울 성동구 연무장길",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: "provider-id-only",
            detailUrl: nil,
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(!incompletePopup.isVerifiedSeongsuPopup, "기간과 개별 원문 링크가 완전한 팝업만 표시")

        let invalidDatePopup = TrendEvent(
            id: "popup-invalid-date",
            title: "성수 잘못된 날짜 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-02-30",
            endDate: "2026-03-10",
            latitude: 37.543,
            longitude: 127.052,
            address: "서울 성동구 연무장길",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: "culture-invalid-date",
            detailUrl: "https://example.com/popup/invalid-date",
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(!invalidDatePopup.isVerifiedSeongsuPopup, "실재하지 않는 달력 날짜 제외")

        let reversedDatePopup = TrendEvent(
            id: "popup-reversed-date",
            title: "성수 역전 기간 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-20",
            endDate: "2026-07-10",
            latitude: 37.543,
            longitude: 127.052,
            address: "서울 성동구 연무장길",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: "culture-reversed-date",
            detailUrl: "https://example.com/popup/reversed-date",
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(!reversedDatePopup.isVerifiedSeongsuPopup, "종료일이 시작일보다 빠른 팝업 제외")

        let unsafePopup = TrendEvent(
            id: "popup-2",
            title: "잘못된 링크",
            kind: "festival",
            period: nil,
            startDate: nil,
            endDate: nil,
            latitude: 37.543,
            longitude: 127.052,
            address: nil,
            imageUrl: nil,
            source: "CRAWLED_POPUP_TREND",
            sourceContentId: "javascript:alert(1)",
            detailUrl: nil,
            collectedAt: nil
        )
        expect(unsafePopup.safeDetailURL == nil, "위험 URL 차단")
        expect(!unsafePopup.isPopup, "팝업 외 kind 판별")
        expect(!unsafePopup.isVerifiedSeongsuPopup, "팝업 외 종류는 성수 팝업에서 제외")
        expect(
            unsafePopup.isEligibleForSeason3EventSearch(allowsNonPopupEvents: true),
            "축제·공연을 포함하는 넓은 행사 검색은 일반 행사를 유지"
        )
        expect(
            !unsafePopup.isEligibleForSeason3EventSearch(allowsNonPopupEvents: false),
            "팝업 전용 검색은 일반 행사를 제외"
        )
        expect(
            !incompletePopup.isEligibleForSeason3EventSearch(allowsNonPopupEvents: true),
            "넓은 행사 검색에서도 검증되지 않은 popup은 제외"
        )

        let localLinkPopup = TrendEvent(
            id: "popup-local-link",
            title: "성수 내부 링크 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: "2026-07-20",
            latitude: 37.543,
            longitude: 127.052,
            address: "서울 성동구 연무장길",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: nil,
            detailUrl: "http://127.0.0.1/private",
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(localLinkPopup.safeDetailURL == nil, "내부망 원문 URL 차단")
        expect(!localLinkPopup.isVerifiedSeongsuPopup, "내부망 링크 팝업 제외")

        let gangnamPopup = TrendEvent(
            id: "popup-gangnam",
            title: "강남 브랜드 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: "2026-07-20",
            latitude: 37.503822,
            longitude: 127.00453,
            address: "서울 서초구 신반포로",
            imageUrl: nil,
            source: "SHINSEGAE_OFFICIAL",
            sourceContentId: "gangnam-popup",
            detailUrl: "https://example.com/popup/gangnam",
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(!gangnamPopup.isVerifiedSeongsuPopup, "성수 밖 팝업은 기본 탭에서 제외")

        let eungbongPopup = TrendEvent(
            id: "popup-eungbong",
            title: "응봉 브랜드 팝업",
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: "2026-07-20",
            latitude: 37.5500,
            longitude: 127.0340,
            address: "서울 성동구 응봉동",
            imageUrl: nil,
            source: "SEOUL_CULTURE_API",
            sourceContentId: "eungbong-popup",
            detailUrl: "https://example.com/popup/eungbong",
            collectedAt: "2026-07-10T09:30:00+09:00"
        )
        expect(!eungbongPopup.isVerifiedSeongsuPopup, "성동구 단독 일치인 응봉동 팝업 제외")
        expect(!Season3SearchIntent.allowsNonPopupEvents(query: "성수 팝업"), "팝업 검색은 팝업만 표시")
        expect(Season3SearchIntent.allowsNonPopupEvents(query: "서울 축제"), "명시적 축제 검색은 넓은 행사 허용")
        expect(Season3SearchIntent.allowsNonPopupEvents(query: "대학로 공연"), "명시적 공연 검색은 넓은 행사 허용")

        expect(
            Season3SearchIntent(query: "지금 어디가 붐비는지 알려줘") == .crowd(keyword: "hot-now"),
            "현재 혼잡 자연어 의도"
        )
        expect(
            Season3SearchIntent(query: "현재 어디가 붐비는지 실시간으로 확인") == .crowd(keyword: "hot-now"),
            "사용자 문장 실시간 혼잡 의도"
        )
        expect(
            Season3SearchIntent(query: "롯데월드 혼잡도") == .crowd(keyword: "롯데월드"),
            "장소 혼잡 자연어 의도"
        )
        expect(
            Season3SearchIntent(query: "성수 팝업 언제부터 언제까지?") == .event(keyword: "성수"),
            "팝업 기간 자연어 의도"
        )
        expect(
            Season3SearchIntent(query: "대학로 공연") == .event(keyword: "대학로"),
            "공연 자연어 의도"
        )
        expect(Season3SearchIntent(query: "서울역") == nil, "일반 장소 검색 유지")

        print("PASS: Season3InsightPresentationTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
