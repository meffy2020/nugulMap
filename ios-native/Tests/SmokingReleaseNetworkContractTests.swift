import Foundation

private final class SmokingReleaseURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var recordedPaths: [String] = []
    static var paths: [String] {
        lock.lock()
        defer { lock.unlock() }
        return recordedPaths
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        Self.recordedPaths.append(request.url?.path ?? "")
        Self.lock.unlock()
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"success":true,"data":{"zones":[]}}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@main
private enum SmokingReleaseNetworkContractTests {
    @MainActor
    static func main() async {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [SmokingReleaseURLProtocol.self]
        let model = ZoneExplorerModel(apiClient: NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration)
        ))
        await model.bootstrap()
        await model.loadInitialZones()
        await model.loadZones(around: 37.5445, longitude: 127.0555)
        await model.resetSearch()
        model.query = " "
        _ = await model.search()
        let paths = SmokingReleaseURLProtocol.paths
        expect(!paths.contains { $0.contains("insight") || $0.contains("recommend") || $0.contains("location-letter") },
               "시작·위치 이동·검색 초기화에서 부가 기능 요청 없음")
        expect(paths.contains("/api/zones/bounds"), "실제 흡연구역 조회 유지")
        expect(model.zones.isEmpty, "빈 응답을 가짜 장소로 대체하지 않음")
        print("PASS: SmokingReleaseNetworkContractTests")
    }
    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else { fputs("FAIL: \(label)\n", stderr); Foundation.exit(1) }
    }
}
