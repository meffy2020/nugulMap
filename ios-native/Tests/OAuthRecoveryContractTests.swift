import AuthenticationServices
import Foundation

private final class OAuthRecoveryStubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var requests: [URLRequest] = []
    nonisolated(unsafe) static var capturedBody = Data()

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.requests.append(request)
        Self.capturedBody = request.httpBody ?? request.httpBodyStream.map(Self.readAll) ?? Data()
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        let body = Data(
            #"{"success":true,"data":{"accessToken":"recovered-access","refreshToken":"recovered-refresh","profileComplete":true,"user":{"email":"user@example.com"}}}"#.utf8
        )
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
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
private enum OAuthRecoveryContractTests {
    static func main() async throws {
        let now = Date(timeIntervalSince1970: 1_789_000_000)
        let clientState = String(repeating: "s", count: 43)
        var persistedTransaction: PendingOAuthTransaction?
        let storage = PendingOAuthTransactionStorage(
            save: { persistedTransaction = $0 },
            load: { persistedTransaction },
            delete: { persistedTransaction = nil }
        )
        let transactionStore = PendingOAuthTransactionStore(
            storage: storage,
            now: { now },
            validityInterval: 10 * 60
        )
        try transactionStore.begin(
            provider: .google,
            pair: PKCEPair(
                verifier: "persisted-verifier",
                challenge: "challenge",
                clientState: clientState
            )
        )
        expect(persistedTransaction?.provider == .google, "OAuth provider 저장")
        expect(persistedTransaction?.verifier == "persisted-verifier", "PKCE verifier 저장")
        expect(persistedTransaction?.clientState == clientState, "OAuth client_state 저장")

        let generatedPair = try PKCEPair.make()
        let secondGeneratedPair = try PKCEPair.make()
        let allowedClientStateCharacters = CharacterSet(
            charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        )
        expect((43...128).contains(generatedPair.clientState.count), "client_state 길이 43~128")
        expect(
            generatedPair.clientState.unicodeScalars.allSatisfy(allowedClientStateCharacters.contains),
            "client_state base64url 문자 집합"
        )
        expect(generatedPair.clientState != secondGeneratedPair.clientState, "client_state 매 요청 신규 생성")

        let authorizationURL = AppConfig.oauthAuthorizationURL(
            provider: .google,
            codeChallenge: "challenge",
            clientState: clientState
        )
        let authorizationItems = URLComponents(
            url: authorizationURL,
            resolvingAgainstBaseURL: false
        )?.queryItems
        expect(
            authorizationItems?.first(where: { $0.name == "client_state" })?.value == clientState,
            "authorization URL에 client_state 전달"
        )

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [OAuthRecoveryStubURLProtocol.self]
        let apiClient = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration)
        )
        let recoveredSession = OAuthWebSession(transactionStore: transactionStore)
        let callbackURL = URL(
            string: "nugulmap://oauth/callback?code=recovered-code&profileComplete=true&email=user@example.com&client_state=\(clientState)"
        )!

        OAuthRecoveryStubURLProtocol.requests = []
        let result = try await recoveredSession.resumeSignIn(from: callbackURL, apiClient: apiClient)
        expect(result?.accessToken == "recovered-access", "프로세스 재시작 뒤 OAuth 교환")
        expect(result?.refreshToken == "recovered-refresh", "복구된 refresh token")
        expect(persistedTransaction == nil, "PKCE verifier 일회성 소비")
        expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "OAuth code 단일 교환")

        let payload = try JSONSerialization.jsonObject(with: OAuthRecoveryStubURLProtocol.capturedBody) as? [String: String]
        expect(payload?["code"] == "recovered-code", "복구 callback code 전달")
        expect(payload?["codeVerifier"] == "persisted-verifier", "저장된 verifier 전달")

        let duplicateResult = try await recoveredSession.resumeSignIn(from: callbackURL, apiClient: apiClient)
        expect(duplicateResult == nil, "동일 callback 재사용 차단")
        expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "중복 callback 네트워크 차단")

        let guardedState = String(repeating: "g", count: 43)
        try transactionStore.begin(
            provider: .naver,
            pair: PKCEPair(
                verifier: "guarded-verifier",
                challenge: "guarded-challenge",
                clientState: guardedState
            )
        )

        let missingStateCallback = URL(string: "nugulmap://oauth/callback?code=missing-state-code")!
        do {
            _ = try await recoveredSession.resumeSignIn(from: missingStateCallback, apiClient: apiClient)
            expect(false, "누락 client_state 거부")
        } catch NugulAPIError.oauthStateMissing {
            expect(persistedTransaction?.clientState == guardedState, "누락 state가 유효 트랜잭션을 소비하지 않음")
            expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "누락 state 네트워크 차단")
        }

        let mismatchedState = String(repeating: "m", count: 43)
        let mismatchedSuccessCallback = URL(
            string: "nugulmap://oauth/callback?code=mismatched-state-code&client_state=\(mismatchedState)"
        )!
        do {
            _ = try await recoveredSession.resumeSignIn(from: mismatchedSuccessCallback, apiClient: apiClient)
            expect(false, "불일치 client_state 성공 callback 거부")
        } catch NugulAPIError.oauthStateMismatch {
            expect(persistedTransaction?.clientState == guardedState, "불일치 성공 callback이 트랜잭션을 소비하지 않음")
            expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "불일치 성공 callback 네트워크 차단")
        }

        let mismatchedFailureCallback = URL(
            string: "nugulmap://oauth/callback?error=access_denied&client_state=\(mismatchedState)"
        )!
        do {
            _ = try await recoveredSession.resumeSignIn(from: mismatchedFailureCallback, apiClient: apiClient)
            expect(false, "불일치 client_state 실패 callback 거부")
        } catch NugulAPIError.oauthStateMismatch {
            expect(persistedTransaction?.clientState == guardedState, "불일치 실패 callback이 트랜잭션을 소비하지 않음")
            expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "불일치 실패 callback 네트워크 차단")
        }

        let matchingFailureCallback = URL(
            string: "nugulmap://oauth/callback?error=access_denied&client_state=\(guardedState)"
        )!
        do {
            _ = try await recoveredSession.resumeSignIn(from: matchingFailureCallback, apiClient: apiClient)
            expect(false, "동일 client_state 실패 callback 오류 반환")
        } catch let NugulAPIError.server(statusCode, message) {
            expect(statusCode == 400 && message == "access_denied", "provider 실패 오류 유지")
            expect(persistedTransaction == nil, "검증된 실패 callback 트랜잭션 일회성 소비")
            expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "provider 실패 callback 교환 차단")
        }

        let processingFailureState = String(repeating: "p", count: 43)
        try transactionStore.begin(
            provider: .kakao,
            pair: PKCEPair(
                verifier: "processing-failure-verifier",
                challenge: "processing-failure-challenge",
                clientState: processingFailureState
            )
        )
        let processingFailureCallback = URL(
            string: "nugulmap://oauth/callback?error=oauth2_processing_failed&client_state=\(processingFailureState)"
        )!
        do {
            _ = try await recoveredSession.resumeSignIn(from: processingFailureCallback, apiClient: apiClient)
            expect(false, "서버 처리 실패 callback 오류 반환")
        } catch let NugulAPIError.server(statusCode, message) {
            expect(statusCode == 400 && message == "oauth2_processing_failed", "서버 처리 실패 오류 유지")
            expect(persistedTransaction == nil, "서버 처리 실패 callback 트랜잭션 일회성 소비")
            expect(OAuthRecoveryStubURLProtocol.requests.count == 1, "서버 처리 실패 callback 교환 차단")
        }

        let longMFAState = String(repeating: "l", count: 43)
        persistedTransaction = PendingOAuthTransaction(
            provider: .google,
            verifier: "long-mfa-verifier",
            clientState: longMFAState,
            createdAt: now.addingTimeInterval(-599)
        )
        let longMFATransaction = try transactionStore.claim(clientState: longMFAState)
        expect(longMFATransaction?.verifier == "long-mfa-verifier", "10분 이내 긴 MFA callback 허용")
        expect(persistedTransaction == nil, "긴 MFA callback 트랜잭션 일회성 소비")

        let expiredState = String(repeating: "e", count: 43)
        persistedTransaction = PendingOAuthTransaction(
            provider: .kakao,
            verifier: "expired-verifier",
            clientState: expiredState,
            createdAt: now.addingTimeInterval(-601)
        )
        do {
            _ = try transactionStore.claim(clientState: expiredState)
            expect(false, "만료된 verifier 거부")
        } catch NugulAPIError.oauthTransactionExpired {
            expect(persistedTransaction == nil, "만료 verifier 제거")
        }

        expect(
            !AppleCredentialSessionPolicy.requiresLocalSignOut(for: .authorized),
            "유효한 Apple 자격은 세션 유지"
        )
        expect(
            AppleCredentialSessionPolicy.requiresLocalSignOut(for: .revoked),
            "해제된 Apple 자격은 로그아웃"
        )
        expect(
            AppleCredentialSessionPolicy.requiresLocalSignOut(for: .notFound),
            "찾을 수 없는 Apple 자격은 로그아웃"
        )
        expect(
            AppleCredentialSessionPolicy.requiresLocalSignOut(for: .transferred),
            "이전된 Apple 자격은 재로그인 요구"
        )

        print("PASS: OAuthRecoveryContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
