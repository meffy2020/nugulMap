import Foundation

private final class AuthRefreshStubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var requests: [URLRequest] = []
    nonisolated(unsafe) static var rejectRefresh = false

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.requests.append(request)

        let path = request.url?.path
        let authorization = request.value(forHTTPHeaderField: "Authorization")
        let statusCode: Int
        let body: Data

        if path == "/api/auth/refresh" {
            if Self.rejectRefresh {
                statusCode = 401
                body = Data(#"{"success":false,"message":"refresh token expired"}"#.utf8)
            } else {
                statusCode = 200
                body = Data(
                    #"{"success":true,"accessToken":"fresh-access","refreshToken":"fresh-refresh"}"#.utf8
                )
            }
        } else if authorization == "Bearer expired-access" {
            statusCode = 401
            body = Data(#"{"success":false,"message":"access token expired"}"#.utf8)
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
private enum AuthRefreshContractTests {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AuthRefreshStubURLProtocol.self]

        var refreshToken: String? = "valid-refresh"
        var savedAccessToken: String?
        var savedRefreshToken: String?
        var deleteCount = 0
        var rejectsTokenSave = false
        let storage = AuthSessionStorage(
            loadRefreshToken: { refreshToken },
            saveTokens: { accessToken, nextRefreshToken in
                if rejectsTokenSave {
                    throw NugulAPIError.secureStorageUnavailable
                }
                savedAccessToken = accessToken
                savedRefreshToken = nextRefreshToken
                refreshToken = nextRefreshToken
            },
            deleteTokens: {
                deleteCount += 1
                refreshToken = nil
            }
        )
        let client = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration),
            authSessionStorage: storage
        )

        AuthRefreshStubURLProtocol.requests = []
        AuthRefreshStubURLProtocol.rejectRefresh = false
        let zones = try await client.fetchUserZones(accessToken: "expired-access")
        expect(zones.isEmpty, "401 뒤 원래 요청 재시도")
        expect(savedAccessToken == "fresh-access", "새 access token 저장")
        expect(savedRefreshToken == "fresh-refresh", "새 refresh token 저장")
        expect(deleteCount == 0, "정상 갱신에서 세션 유지")
        expect(AuthRefreshStubURLProtocol.requests.count == 3, "원 요청, 갱신, 단일 재시도")
        expect(
            AuthRefreshStubURLProtocol.requests.last?.value(forHTTPHeaderField: "Authorization") == "Bearer fresh-access",
            "재시도에 갱신된 인증 헤더"
        )

        refreshToken = "expired-refresh"
        AuthRefreshStubURLProtocol.requests = []
        AuthRefreshStubURLProtocol.rejectRefresh = true
        do {
            _ = try await client.fetchUserZones(accessToken: "expired-access")
            expect(false, "만료된 refresh token 오류")
        } catch NugulAPIError.sessionExpired {
            expect(deleteCount == 1, "만료된 refresh token에서 세션 삭제")
        }

        refreshToken = "valid-refresh"
        rejectsTokenSave = true
        AuthRefreshStubURLProtocol.requests = []
        AuthRefreshStubURLProtocol.rejectRefresh = false
        do {
            _ = try await client.fetchUserZones(accessToken: "expired-access")
            expect(false, "Keychain 저장 실패 전파")
        } catch NugulAPIError.secureStorageUnavailable {
            expect(AuthRefreshStubURLProtocol.requests.count == 2, "저장 실패 뒤 인증 요청 재시도 차단")
        }

        print("PASS: AuthRefreshContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
