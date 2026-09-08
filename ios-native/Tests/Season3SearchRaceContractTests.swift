import Foundation

private final class Season3SearchRaceURLProtocol: URLProtocol, @unchecked Sendable {
    private static let insightRequestSignal: (
        stream: AsyncStream<Void>,
        continuation: AsyncStream<Void>.Continuation
    ) = {
        var continuation: AsyncStream<Void>.Continuation!
        let stream = AsyncStream<Void> { continuation = $0 }
        return (stream, continuation)
    }()

    static func waitForInsightRequest() async {
        var iterator = insightRequestSignal.stream.makeAsyncIterator()
        _ = await iterator.next()
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let isInsightSearch = request.url?.path == "/api/insights/map"
            && request.url?.query?.contains("keyword=") == true
        if isInsightSearch {
            Self.insightRequestSignal.continuation.yield(())
            Thread.sleep(forTimeInterval: 0.5)
        }

        let statusCode: Int
        let body: Data
        if isInsightSearch {
            statusCode = 200
            body = Data(
                """
                {
                  "success": true,
                  "data": {
                    "hotplaces": {"places": [], "sources": []},
                    "events": {
                      "events": [{
                        "id": "race-popup",
                        "title": "성수 경쟁 조건 팝업",
                        "kind": "popup",
                        "startDate": "2026-07-10",
                        "endDate": "2026-07-20",
                        "latitude": 37.5445,
                        "longitude": 127.0555,
                        "address": "서울특별시 성동구 연무장길 1",
                        "source": "SEOUL_CULTURE_API",
                        "detailUrl": "https://example.com/race-popup",
                        "attribution": "서울특별시 문화행사 정보",
                        "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                        "license": "공공누리 제1유형",
                        "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do"
                      }],
                      "sources": ["SEOUL_CULTURE_API"]
                    }
                  }
                }
                """.utf8
            )
        } else if request.url?.path == "/api/insights/map" {
            statusCode = 200
            body = Data(
                #"{"success":true,"data":{"hotplaces":{"places":[],"sources":[]},"events":{"events":[],"sources":[]}}}"#.utf8
            )
        } else {
            statusCode = 200
            body = Data(#"{"success":true,"data":{"zones":[]}}"#.utf8)
        }

        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

@main
private enum Season3SearchRaceContractTests {
    @MainActor
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [Season3SearchRaceURLProtocol.self]
        let model = ZoneExplorerModel(
            apiClient: NugulAPIClient(
                baseURL: URL(string: "https://example.test")!,
                session: URLSession(configuration: configuration)
            )
        )
        model.query = "성수 팝업"

        let searchTask = Task { await model.search() }
        await Season3SearchRaceURLProtocol.waitForInsightRequest()

        await model.loadInsights(in: .centralSeoul, force: true)
        await model.loadZones(in: .seongsu, force: true)
        _ = await searchTask.value

        expect(model.eventInsight.events.first?.id == "race-popup", "지도 영역 갱신 중에도 팝업 검색 응답 유지")
        print("PASS: Season3SearchRaceContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
