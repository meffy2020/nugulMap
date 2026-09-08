import Foundation

private final class MapInsightStubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var capturedRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.capturedRequest = request
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        let body = Data(
            """
            {
              "success": true,
              "data": {
                "hotplaces": {
                  "places": [],
                  "dataFreshness": "CURRENT",
                  "updatedAt": "2026-07-12T12:00:00+09:00",
                  "sources": []
                },
                "events": {
                  "events": [
                    {
                      "id": "seongsu-popup-review-fixture",
                      "title": "성수 여름 팝업",
                      "kind": "popup",
                      "period": "2026-07-10 ~ 2026-07-20",
                      "startDate": "2026-07-10",
                      "endDate": "2026-07-20",
                      "latitude": 37.5445,
                      "longitude": 127.0555,
                      "address": "서울특별시 성동구 연무장길 1",
                      "imageUrl": null,
                      "source": "SEOUL_CULTURE_API",
                      "sourceContentId": "fixture-seongsu-popup",
                      "detailUrl": "https://example.com/seongsu-popup",
                      "collectedAt": "2026-07-12T12:00:00+09:00",
                      "attribution": "서울특별시 문화행사 정보",
                      "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                      "license": "공공누리 제1유형",
                      "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do"
                    }
                  ],
                  "dataFreshness": "DAILY",
                  "updatedAt": "2026-07-12T12:00:00+09:00",
                  "sources": ["SEOUL_CULTURE_API"]
                },
                "status": null,
                "updatedAt": "2026-07-12T12:00:00+09:00"
              }
            }
            """.utf8
        )
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

@main
private enum MapInsightContractTests {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MapInsightStubURLProtocol.self]
        let client = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration)
        )

        let insight = try await client.fetchMapInsights(
            keyword: "성수 팝업",
            bounds: .seongsu
        )
        let reviewDate = ISO8601DateFormatter().date(from: "2026-07-12T12:00:00+09:00")!

        let event = insight.events.events.first
        expect(event?.title == "성수 여름 팝업", "팝업 응답 디코딩")
        expect(event?.isVerifiedSeongsuPopup(at: reviewDate) == true, "성수 팝업 검증 필터 통과")
        expect(event?.safeDetailURL?.absoluteString == "https://example.com/seongsu-popup", "공식 원문 링크 보존")
        expect(
            event?.safeAttributionURL?.absoluteString
                == "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
            "승인된 서울시 데이터셋 링크 보존"
        )
        expect(event?.licenseLabel == "공공누리 제1유형", "이용조건 사용자 라벨")
        expect(event?.safeLicenseURL?.host == "www.kogl.or.kr", "이용조건 링크 분리")

        let expiredPopup = makePopup(id: "expired", endDate: "2026-07-11")
        expect(!expiredPopup.isVerifiedSeongsuPopup(at: reviewDate), "KST 기준 종료일이 지난 팝업 제외")

        let lastDayPopup = makePopup(id: "last-day", endDate: "2026-07-12")
        expect(lastDayPopup.isVerifiedSeongsuPopup(at: reviewDate), "KST 종료일 당일까지 팝업 유지")

        let untrustedSourcePopup = makePopup(id: "wrong-source", source: "CRAWLED_POPUP_TREND")
        expect(!untrustedSourcePopup.isVerifiedSeongsuPopup(at: reviewDate), "서울문화 API 외 출처 제외")

        let sourceCaseVariationPopup = makePopup(id: "source-case", source: "seoul_culture_api")
        expect(!sourceCaseVariationPopup.isVerifiedSeongsuPopup(at: reviewDate), "출처 식별자 대소문자 변형 제외")

        let missingAttributionPopup = makePopup(id: "missing-attribution", attribution: nil)
        expect(!missingAttributionPopup.isVerifiedSeongsuPopup(at: reviewDate), "출처 표기 없는 팝업 제외")

        let variedAttributionPopup = makePopup(id: "varied-attribution", attribution: "서울특별시")
        expect(!variedAttributionPopup.isVerifiedSeongsuPopup(at: reviewDate), "공식 데이터셋 출처명 변형 제외")

        let missingSourceURLPopup = makePopup(id: "missing-source-url", sourceURL: nil)
        expect(!missingSourceURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "공식 데이터셋 링크 누락 제외")

        let alternateSourceURLPopup = makePopup(
            id: "alternate-source-url",
            sourceURL: "https://data.seoul.go.kr/"
        )
        expect(!alternateSourceURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "대체 공개 출처 URL 제외")

        let privateSourceURLPopup = makePopup(
            id: "private-source-url",
            sourceURL: "http://10.0.0.8/source"
        )
        expect(privateSourceURLPopup.safeAttributionURL == nil, "사설 IP 출처 URL 차단")
        expect(!privateSourceURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "사설 IP 출처 URL 팝업 제외")

        let numericSourceURLPopup = makePopup(
            id: "numeric-source-url",
            sourceURL: "http://2130706433/source"
        )
        expect(numericSourceURLPopup.safeAttributionURL == nil, "숫자형 출처 URL 차단")
        expect(!numericSourceURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "숫자형 출처 URL 팝업 제외")

        let missingLicensePopup = makePopup(id: "missing-license", license: nil)
        expect(!missingLicensePopup.isVerifiedSeongsuPopup(at: reviewDate), "이용조건 누락 제외")

        let variedLicensePopup = makePopup(id: "varied-license", license: "KOGL_TYPE_1")
        expect(!variedLicensePopup.isVerifiedSeongsuPopup(at: reviewDate), "이용조건 표기 변형 제외")

        let missingLicenseURLPopup = makePopup(id: "missing-license-url", licenseURL: nil)
        expect(!missingLicenseURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "이용조건 링크 누락 제외")

        let alternateLicenseURLPopup = makePopup(
            id: "alternate-license-url",
            licenseURL: "https://www.kogl.or.kr/"
        )
        expect(!alternateLicenseURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "대체 공개 이용조건 URL 제외")

        let privateLicenseURLPopup = makePopup(
            id: "private-license-url",
            licenseURL: "http://192.168.0.8/license"
        )
        expect(privateLicenseURLPopup.safeLicenseURL == nil, "사설 IP 이용조건 URL 차단")
        expect(!privateLicenseURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "사설 IP 이용조건 URL 팝업 제외")

        let hexadecimalLicenseURLPopup = makePopup(
            id: "hex-license-url",
            licenseURL: "http://0x7f.0.0.1/license"
        )
        expect(hexadecimalLicenseURLPopup.safeLicenseURL == nil, "16진 이용조건 URL 차단")
        expect(!hexadecimalLicenseURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "16진 이용조건 URL 팝업 제외")

        let tobaccoTitlePopup = makePopup(id: "tobacco-title", title: "성수 니코틴 팝업")
        expect(!tobaccoTitlePopup.isVerifiedSeongsuPopup(at: reviewDate), "니코틴 제목 팝업 제외")

        let tobaccoAddressPopup = makePopup(id: "tobacco-address", address: "서울 성동구 연무장길 담배 매장")
        expect(!tobaccoAddressPopup.isVerifiedSeongsuPopup(at: reviewDate), "담배 주소 팝업 제외")

        let tobaccoAttributionPopup = makePopup(id: "tobacco-attribution", attribution: "Vape 공식 채널")
        expect(!tobaccoAttributionPopup.isVerifiedSeongsuPopup(at: reviewDate), "담배 관련 attribution 팝업 제외")

        let tobaccoURLPopup = makePopup(
            id: "tobacco-url",
            detailURL: "https://example.com/nicotine/popup"
        )
        expect(!tobaccoURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "담배 관련 원문 URL 팝업 제외")

        let userInfoURLPopup = makePopup(
            id: "userinfo-url",
            detailURL: "https://user:password@example.com/popup"
        )
        expect(userInfoURLPopup.safeDetailURL == nil, "userinfo 원문 URL 차단")
        expect(!userInfoURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "userinfo URL 팝업 제외")

        let privateURLPopup = makePopup(
            id: "private-url",
            detailURL: "http://10.0.0.8/popup"
        )
        expect(privateURLPopup.safeDetailURL == nil, "사설 IP 원문 URL 차단")
        expect(!privateURLPopup.isVerifiedSeongsuPopup(at: reviewDate), "사설 IP URL 팝업 제외")

        let numericIPv4Popup = makePopup(
            id: "numeric-ipv4-url",
            detailURL: "http://2130706433/private"
        )
        expect(numericIPv4Popup.safeDetailURL == nil, "숫자형 IPv4 우회 차단")

        let octalIPv4Popup = makePopup(
            id: "octal-ipv4-url",
            detailURL: "http://0177.0.0.1/private"
        )
        expect(octalIPv4Popup.safeDetailURL == nil, "8진 IPv4 우회 차단")

        let hexadecimalIPv4Popup = makePopup(
            id: "hex-ipv4-url",
            detailURL: "http://0x7f.0.0.1/private"
        )
        expect(hexadecimalIPv4Popup.safeDetailURL == nil, "16진 IPv4 우회 차단")

        let bracketIPv6Popup = makePopup(
            id: "bracket-ipv6-url",
            detailURL: "http://[::1]/private"
        )
        expect(bracketIPv6Popup.safeDetailURL == nil, "bracket IPv6 loopback 차단")

        let mappedIPv6Popup = makePopup(
            id: "mapped-ipv6-url",
            detailURL: "http://[::ffff:127.0.0.1]/private",
            sourceURL: "http://2130706433/source",
            licenseURL: "http://[::ffff:127.0.0.1]/license"
        )
        expect(mappedIPv6Popup.safeDetailURL == nil, "IPv4-mapped IPv6 우회 차단")
        expect(mappedIPv6Popup.safeAttributionURL == nil, "숫자형 출처 URL 우회 차단")
        expect(mappedIPv6Popup.safeLicenseURL == nil, "mapped 이용조건 URL 우회 차단")
        expect(
            MapInsightStubURLProtocol.capturedRequest?.cachePolicy == .reloadIgnoringLocalCacheData,
            "실시간 인사이트 요청은 로컬 캐시 우회"
        )
        expect(
            MapInsightStubURLProtocol.capturedRequest?.url?.query?.contains("keyword=%EC%84%B1%EC%88%98%20%ED%8C%9D%EC%97%85") == true,
            "성수 팝업 검색어 전달"
        )

        print("PASS: MapInsightContractTests")
    }

    private static func makePopup(
        id: String,
        title: String = "성수 안전 팝업",
        endDate: String = "2026-07-20",
        address: String = "서울특별시 성동구 연무장길 1",
        source: String = "SEOUL_CULTURE_API",
        attribution: String? = "서울특별시 문화행사 정보",
        detailURL: String = "https://example.com/safe-popup",
        sourceURL: String? = "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
        license: String? = "공공누리 제1유형",
        licenseURL: String? = "https://www.kogl.or.kr/info/licenseType1.do"
    ) -> TrendEvent {
        TrendEvent(
            id: id,
            title: title,
            kind: "popup",
            period: nil,
            startDate: "2026-07-10",
            endDate: endDate,
            latitude: 37.5445,
            longitude: 127.0555,
            address: address,
            imageUrl: nil,
            source: source,
            sourceContentId: id,
            detailUrl: detailURL,
            collectedAt: "2026-07-12T12:00:00+09:00",
            attribution: attribution,
            sourceUrl: sourceURL,
            license: license,
            licenseUrl: licenseURL
        )
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
