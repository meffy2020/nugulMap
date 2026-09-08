import Foundation

private final class StubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var responseData = Data()
    nonisolated(unsafe) static var statusCode = 200
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
            statusCode: Self.statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseData)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

@main
private enum AccountDeletionContractTests {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let client = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let serverMessage = "iPhone 설정 > Apple로 로그인에서 너굴맵 연결을 해제해주세요."
        StubURLProtocol.responseData = try JSONSerialization.data(withJSONObject: [
            "success": true,
            "message": serverMessage,
            "data": ["manualAppleRevocationRequired": true]
        ])

        let result = try await client.deleteCurrentUser(accessToken: "access-token")
        expect(result.manualAppleRevocationRequired, "수동 Apple 연결 해제 플래그")
        expect(result.message == serverMessage, "서버 안내문 보존")
        expect(StubURLProtocol.capturedRequest?.httpMethod == "DELETE", "DELETE 메서드")
        expect(
            StubURLProtocol.capturedRequest?.value(forHTTPHeaderField: "Authorization") == "Bearer access-token",
            "계정 삭제 인증 헤더"
        )

        StubURLProtocol.responseData = Data()
        let legacyResult = try await client.deleteCurrentUser(accessToken: "access-token")
        expect(!legacyResult.manualAppleRevocationRequired, "빈 2xx 레거시 응답 호환")
        expect(legacyResult.message == nil, "빈 응답 안내문 없음")

        print("PASS: AccountDeletionContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
