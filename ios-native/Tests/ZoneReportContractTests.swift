import Foundation

private final class ZoneReportStubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var responseData = Data()
    nonisolated(unsafe) static var statusCode = 201
    nonisolated(unsafe) static var capturedRequest: URLRequest?
    nonisolated(unsafe) static var capturedBody = Data()

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.capturedRequest = request
        Self.capturedBody = request.httpBody ?? request.httpBodyStream.map(Self.readAll) ?? Data()
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

    private static func readAll(from stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }

        var result = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else {
                break
            }
            result.append(buffer, count: count)
        }
        return result
    }
}

@main
private enum ZoneReportContractTests {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ZoneReportStubURLProtocol.self]
        let client = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration)
        )

        ZoneReportStubURLProtocol.statusCode = 201
        ZoneReportStubURLProtocol.responseData = Data(
            #"{"success":true,"message":"장소 신고가 접수되었습니다.","data":{"report":{"id":9}}}"#.utf8
        )
        try await client.reportZone(
            zoneID: 301,
            reason: "OFFENSIVE",
            details: "부적절한 사진",
            accessToken: "access-token"
        )

        let request = ZoneReportStubURLProtocol.capturedRequest
        expect(request?.url?.path == "/api/zones/301/reports", "장소 신고 경로")
        expect(request?.httpMethod == "POST", "장소 신고 POST")
        expect(request?.value(forHTTPHeaderField: "Authorization") == "Bearer access-token", "장소 신고 인증")
        let body = try JSONSerialization.jsonObject(with: ZoneReportStubURLProtocol.capturedBody) as? [String: String]
        expect(body?["reason"] == "OFFENSIVE", "장소 신고 사유")
        expect(body?["details"] == "부적절한 사진", "장소 신고 상세")

        ZoneReportStubURLProtocol.statusCode = 409
        ZoneReportStubURLProtocol.responseData = Data(#"{"message":"이미 신고한 장소입니다."}"#.utf8)
        do {
            try await client.reportZone(zoneID: 301, reason: "DUPLICATE", accessToken: "access-token")
            expect(false, "중복 신고 오류")
        } catch {
            expect(error.localizedDescription == "이미 신고한 장소입니다.", "중복 신고 서버 메시지")
        }

        do {
            try await client.reportZone(zoneID: 301, reason: "INACCURATE", accessToken: nil)
            expect(false, "비로그인 신고 오류")
        } catch {
            expect(error.localizedDescription == "로그인 정보를 찾을 수 없습니다. 다시 로그인해주세요.", "비로그인 신고 메시지")
        }

        print("PASS: ZoneReportContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
