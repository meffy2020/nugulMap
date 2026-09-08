import Foundation

private final class RecommendationRaceURLProtocol: URLProtocol, @unchecked Sendable {
    private static let firstRequestSignal: (
        stream: AsyncStream<Void>,
        continuation: AsyncStream<Void>.Continuation
    ) = {
        var continuation: AsyncStream<Void>.Continuation!
        let stream = AsyncStream<Void> { continuation = $0 }
        return (stream, continuation)
    }()

    static func waitForFirstRequest() async {
        var iterator = firstRequestSignal.stream.makeAsyncIterator()
        _ = await iterator.next()
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let latitude = query.first(where: { $0.name == "lat" })?.value ?? ""
        let isFirstViewport = latitude == "37.1"
        if isFirstViewport {
            Self.firstRequestSignal.continuation.yield(())
            Thread.sleep(forTimeInterval: 0.5)
        }

        let requestID = isFirstViewport ? "stale-request" : "latest-request"
        let name = isFirstViewport ? "이전 화면 장소" : "현재 화면 장소"
        let body = Data(
            """
            {
              "success": true,
              "data": {
                "requestId": "\(requestID)",
                "modelVersion": "context-ranker-v1",
                "dataStatus": "READY",
                "personalizationMode": "CONTEXT_ONLY",
                "intent": "CAFE",
                "eligibleCandidateCount": 1,
                "generatedAt": "2026-09-01T03:00:00Z",
                "items": [{
                  "id": "\(requestID)",
                  "provider": "KAKAO_LOCAL",
                  "name": "\(name)",
                  "category": "카페",
                  "address": "서울특별시",
                  "latitude": 37.2,
                  "longitude": 127.2,
                  "distanceMeters": 100,
                  "score": 0.9,
                  "reasonCodes": ["CAFE_INTENT_MATCH"],
                  "sourceUrl": "https://place.map.kakao.com/1"
                }]
              }
            }
            """.utf8
        )
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
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
private enum RecommendationRaceContractTests {
    @MainActor
    static func main() async {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RecommendationRaceURLProtocol.self]
        let model = ZoneExplorerModel(
            apiClient: NugulAPIClient(
                baseURL: URL(string: "https://example.test")!,
                session: URLSession(configuration: configuration)
            )
        )

        let staleTask = Task {
            await model.loadRecommendations(
                intent: .cafe,
                center: .init(latitude: 37.1, longitude: 127.1),
                radiusMeters: 3_000,
                force: true
            )
        }
        await RecommendationRaceURLProtocol.waitForFirstRequest()

        await model.loadRecommendations(
            intent: .cafe,
            center: .init(latitude: 37.2, longitude: 127.2),
            radiusMeters: 3_000,
            force: true
        )
        await staleTask.value

        expect(model.recommendationResponse?.requestId == "latest-request", "이전 viewport 응답 무시")
        expect(model.visibleRecommendations.first?.name == "현재 화면 장소", "최신 viewport 후보 유지")
        expect(!model.isLoadingRecommendations, "최신 요청 종료 후 로딩 해제")
        print("PASS: RecommendationRaceContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
