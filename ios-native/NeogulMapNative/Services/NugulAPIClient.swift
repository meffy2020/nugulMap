import AuthenticationServices
import CryptoKit
import Foundation
import Security
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

enum NugulAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case server(statusCode: Int, message: String?)
    case decodingFailed
    case authCancelled
    case missingToken
    case appleIdentityUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "API 주소를 만들 수 없습니다."
        case .invalidResponse:
            return "서버 응답 형식이 올바르지 않습니다."
        case let .server(statusCode, message):
            if let message, !message.isEmpty {
                return "\(message) (\(statusCode))"
            }
            return "서버 요청에 실패했습니다. (\(statusCode))"
        case .decodingFailed:
            return "응답 데이터를 읽을 수 없습니다."
        case .authCancelled:
            return "로그인이 취소되었습니다."
        case .missingToken:
            return "로그인 토큰을 찾을 수 없습니다."
        case .appleIdentityUnavailable:
            return "Apple 로그인 정보를 확인할 수 없습니다."
        }
    }
}

enum OAuthProvider: String, CaseIterable, Identifiable {
    case kakao
    case naver
    case google

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .kakao:
            return "카카오"
        case .naver:
            return "네이버"
        case .google:
            return "구글"
        }
    }
}

struct OAuthLoginResult {
    let accessToken: String
    let refreshToken: String?
    let profileComplete: Bool
    let email: String?
}

private struct OAuthCallbackCode {
    let code: String
    let profileComplete: Bool
    let email: String?

    init(callbackURL: URL) throws {
        guard let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false) else {
            throw NugulAPIError.invalidURL
        }

        if let error = components.queryItems?.first(where: { $0.name == "error" })?.value, !error.isEmpty {
            throw NugulAPIError.server(statusCode: 400, message: error)
        }

        guard
            let code = components.queryItems?.first(where: { $0.name == "code" })?.value,
            !code.isEmpty
        else {
            throw NugulAPIError.missingToken
        }

        self.code = code
        self.profileComplete = components.queryItems?.first(where: { $0.name == "profileComplete" })?.value == "true"
        self.email = components.queryItems?.first(where: { $0.name == "email" })?.value
    }
}

struct PKCEPair {
    let verifier: String
    let challenge: String

    static func make() -> PKCEPair {
        let verifier = randomURLSafeString(byteCount: 32)
        let digest = SHA256.hash(data: Data(verifier.utf8))
        let challenge = Data(digest).base64URLEncodedString()
        return PKCEPair(verifier: verifier, challenge: challenge)
    }

    private static func randomURLSafeString(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)

        if status == errSecSuccess {
            return Data(bytes).base64URLEncodedString()
        }

        return UUID().uuidString.replacingOccurrences(of: "-", with: "")
            + UUID().uuidString.replacingOccurrences(of: "-", with: "")
    }
}


struct NugulAPIClient {
    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder

    init(baseURL: URL = AppConfig.apiBaseURL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
        self.decoder = JSONDecoder()
    }

    func exchangeMobileOAuthCode(code: String, codeVerifier: String) async throws -> OAuthLoginResult {
        var request = URLRequest(url: try makeURL(path: "/api/auth/mobile/exchange", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(MobileOAuthExchangeRequest(code: code, codeVerifier: codeVerifier))

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(APIEnvelope<MobileOAuthExchangePayload>.self, from: data)
        guard let payload = result.data, !payload.accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        return OAuthLoginResult(
            accessToken: payload.accessToken,
            refreshToken: payload.refreshToken,
            profileComplete: payload.profileComplete,
            email: payload.user?.email
        )
    }

    func exchangeAppleIdentityToken(
        identityToken: String,
        authorizationCode: String?,
        fullName: String?
    ) async throws -> OAuthLoginResult {
        var request = URLRequest(url: try makeURL(path: "/api/auth/apple/mobile", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(AppleMobileLoginRequest(
            identityToken: identityToken,
            authorizationCode: authorizationCode,
            fullName: fullName
        ))

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(APIEnvelope<MobileOAuthExchangePayload>.self, from: data)
        guard let payload = result.data, !payload.accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        return OAuthLoginResult(
            accessToken: payload.accessToken,
            refreshToken: payload.refreshToken,
            profileComplete: payload.profileComplete,
            email: payload.user?.email
        )
    }

    func fetchZonesByBounds(_ bounds: MapBounds = .centralSeoul) async throws -> [SmokingZone] {
        let response: APIEnvelope<ZoneCollectionPayload> = try await get(
            path: "/api/zones/bounds",
            queryItems: [
                URLQueryItem(name: "minLat", value: String(bounds.minLat)),
                URLQueryItem(name: "maxLat", value: String(bounds.maxLat)),
                URLQueryItem(name: "minLng", value: String(bounds.minLng)),
                URLQueryItem(name: "maxLng", value: String(bounds.maxLng))
            ]
        )

        return response.data?.zones ?? []
    }

    func fetchMapInsights(keyword: String? = nil, hotplaceLimit: Int = 8, eventLimit: Int = 8, bounds: MapBounds = .centralSeoul) async throws -> MapInsight {
        var queryItems = [
            URLQueryItem(name: "hotplaceLimit", value: String(hotplaceLimit)),
            URLQueryItem(name: "eventLimit", value: String(eventLimit)),
            URLQueryItem(name: "minLat", value: String(bounds.minLat)),
            URLQueryItem(name: "maxLat", value: String(bounds.maxLat)),
            URLQueryItem(name: "minLng", value: String(bounds.minLng)),
            URLQueryItem(name: "maxLng", value: String(bounds.maxLng))
        ]
        if let keyword, !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "keyword", value: keyword.trimmingCharacters(in: .whitespacesAndNewlines)))
        }

        let response: APIEnvelope<MapInsight> = try await get(path: "/api/insights/map", queryItems: queryItems)
        if response.success == false {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        return response.data ?? .empty
    }

    func searchZones(keyword: String) async throws -> [SmokingZone] {
        let trimmedKeyword = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedKeyword.isEmpty else {
            return try await fetchZonesByBounds()
        }

        let response: APIEnvelope<ZoneCollectionPayload> = try await get(
            path: "/api/zones/search",
            queryItems: [
                URLQueryItem(name: "keyword", value: trimmedKeyword)
            ]
        )

        return response.data?.zones ?? []
    }

    func fetchReviews(zoneID: Int) async throws -> [ZoneReview] {
        let response: APIEnvelope<ReviewCollectionPayload> = try await get(
            path: "/api/zones/\(zoneID)/reviews"
        )

        return response.data?.reviews ?? []
    }

    func createReview(
        zoneID: Int,
        content: String,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> ZoneReview {
        var request = URLRequest(url: try makeURL(path: "/api/zones/\(zoneID)/reviews", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(CreateReviewPayload(content: content))

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(APIEnvelope<ReviewPayload>.self, from: data)
        guard let review = result.data?.review else {
            throw NugulAPIError.decodingFailed
        }

        return review
    }

    func getCurrentUser(accessToken: String? = AuthTokenStore.loadAccessToken()) async -> UserProfile? {
        do {
            let response: APIEnvelope<UserPayload> = try await get(path: "/api/auth/me", accessToken: accessToken)
            return response.data?.user
        } catch {
            return nil
        }
    }

    func createZone(
        _ payload: CreateZonePayload,
        image: ZoneImageAttachment? = nil,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> SmokingZone {
        var request = URLRequest(url: try makeURL(path: "/api/zones", queryItems: []))
        let boundary = "Boundary-\(UUID().uuidString)"
        let encoder = JSONEncoder()

        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try makeMultipartBody(boundary: boundary, payload: payload, image: image, encoder: encoder)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(APIEnvelope<ZonePayload>.self, from: data)
        guard let zone = result.data?.zone else {
            throw NugulAPIError.decodingFailed
        }

        return zone
    }

    func fetchUserZones(accessToken: String? = AuthTokenStore.loadAccessToken()) async throws -> [SmokingZone] {
        let response: APIEnvelope<ZoneCollectionPayload> = try await get(path: "/api/zones/my", accessToken: accessToken)
        return response.data?.zones ?? []
    }

    func deleteZone(id: Int, accessToken: String? = AuthTokenStore.loadAccessToken()) async throws {
        var request = URLRequest(url: try makeURL(path: "/api/zones/\(id)", queryItems: []))
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
    }

    func deleteCurrentUser(accessToken: String? = AuthTokenStore.loadAccessToken()) async throws {
        var request = URLRequest(url: try makeURL(path: "/api/users/me", queryItems: []))
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
    }

    func completeProfileSetup(
        nickname: String,
        image: ZoneImageAttachment? = nil,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> UserProfile {
        var request = URLRequest(url: try makeURL(path: "/api/users/profile-setup", queryItems: []))
        let boundary = "Boundary-\(UUID().uuidString)"

        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = makeProfileSetupBody(boundary: boundary, nickname: nickname, image: image)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(APIEnvelope<UserPayload>.self, from: data)
        guard let user = result.data?.user else {
            throw NugulAPIError.decodingFailed
        }

        return user
    }

    func imageURL(for path: String?) -> URL? {
        guard let path, !path.isEmpty else {
            return nil
        }

        if let absoluteURL = URL(string: path), absoluteURL.scheme != nil {
            return absoluteURL
        }

        let normalizedPath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if normalizedPath.hasPrefix("api/images/") {
            return try? makeURL(path: "/\(normalizedPath)", queryItems: [])
        }

        if normalizedPath.hasPrefix("images/") {
            return try? makeURL(path: "/api/\(normalizedPath)", queryItems: [])
        }

        return try? makeURL(path: "/api/images/\(normalizedPath)", queryItems: [])
    }

    private func get<Response: Decodable>(
        path: String,
        queryItems: [URLQueryItem] = [],
        accessToken: String? = nil
    ) async throws -> Response {
        var request = URLRequest(url: try makeURL(path: path, queryItems: queryItems))
        request.httpMethod = "GET"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw NugulAPIError.decodingFailed
        }
    }

    private func makeURL(path: String, queryItems: [URLQueryItem]) throws -> URL {
        let normalizedBase = baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let normalizedPath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))

        guard var components = URLComponents(string: "\(normalizedBase)/\(normalizedPath)") else {
            throw NugulAPIError.invalidURL
        }

        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }

        guard let url = components.url else {
            throw NugulAPIError.invalidURL
        }

        return url
    }

    private func applyAuthHeader(_ accessToken: String?, to request: inout URLRequest) {
        guard let accessToken, !accessToken.isEmpty else {
            return
        }

        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
    }

    private func makeMultipartBody(
        boundary: String,
        payload: CreateZonePayload,
        image: ZoneImageAttachment?,
        encoder: JSONEncoder
    ) throws -> Data {
        var body = Data()
        let payloadData = try encoder.encode(payload)
        let payloadText = String(data: payloadData, encoding: .utf8) ?? "{}"

        body.append("--\(boundary)\r\n")
        body.append("Content-Disposition: form-data; name=\"data\"\r\n")
        body.append("Content-Type: application/json\r\n\r\n")
        body.append(payloadText)
        body.append("\r\n")

        if let image {
            body.append("--\(boundary)\r\n")
            body.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(image.filename)\"\r\n")
            body.append("Content-Type: \(image.mimeType)\r\n\r\n")
            body.append(image.data)
            body.append("\r\n")
        }

        body.append("--\(boundary)--\r\n")

        return body
    }

    private func makeProfileSetupBody(boundary: String, nickname: String, image: ZoneImageAttachment?) -> Data {
        var body = Data()
        body.append("--\(boundary)\r\n")
        body.append("Content-Disposition: form-data; name=\"nickname\"\r\n\r\n")
        body.append(nickname)
        body.append("\r\n")

        if let image {
            body.append("--\(boundary)\r\n")
            body.append("Content-Disposition: form-data; name=\"profileImage\"; filename=\"\(image.filename)\"\r\n")
            body.append("Content-Type: \(image.mimeType)\r\n\r\n")
            body.append(image.data)
            body.append("\r\n")
        }

        body.append("--\(boundary)--\r\n")
        return body
    }
}

final class OAuthWebSession: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    func signIn(with provider: OAuthProvider, apiClient: NugulAPIClient) async throws -> OAuthLoginResult {
        let pkce = PKCEPair.make()
        let callback = try await receiveAuthorizationCode(provider: provider, pkce: pkce)
        let result = try await apiClient.exchangeMobileOAuthCode(code: callback.code, codeVerifier: pkce.verifier)

        return OAuthLoginResult(
            accessToken: result.accessToken,
            refreshToken: result.refreshToken,
            profileComplete: result.profileComplete,
            email: result.email ?? callback.email
        )
    }

    private func receiveAuthorizationCode(provider: OAuthProvider, pkce: PKCEPair) async throws -> OAuthCallbackCode {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: AppConfig.oauthAuthorizationURL(provider: provider, codeChallenge: pkce.challenge),
                callbackURLScheme: AppConfig.oauthCallbackScheme
            ) { callbackURL, error in
                self.session = nil

                if let callbackURL {
                    do {
                        continuation.resume(returning: try OAuthCallbackCode(callbackURL: callbackURL))
                    } catch {
                        continuation.resume(throwing: error)
                    }
                    return
                }

                if error != nil {
                    continuation.resume(throwing: NugulAPIError.authCancelled)
                } else {
                    continuation.resume(throwing: NugulAPIError.missingToken)
                }
            }

            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.session = session

            if !session.start() {
                self.session = nil
                continuation.resume(throwing: NugulAPIError.authCancelled)
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        #if os(iOS)
        return UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first ?? ASPresentationAnchor()
        #elseif os(macOS)
        return NSApplication.shared.keyWindow ?? ASPresentationAnchor()
        #else
        return ASPresentationAnchor()
        #endif
    }
}

final class AppleSignInSession: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<AppleCredential, Error>?

    func signIn(authorization: ASAuthorization, apiClient: NugulAPIClient) async throws -> OAuthLoginResult {
        let credential = try appleCredential(from: authorization)
        return try await apiClient.exchangeAppleIdentityToken(
            identityToken: credential.identityToken,
            authorizationCode: credential.authorizationCode,
            fullName: credential.fullName
        )
    }

    func signIn(apiClient: NugulAPIClient) async throws -> OAuthLoginResult {
        let credential = try await requestCredential()
        return try await apiClient.exchangeAppleIdentityToken(
            identityToken: credential.identityToken,
            authorizationCode: credential.authorizationCode,
            fullName: credential.fullName
        )
    }

    private func requestCredential() async throws -> AppleCredential {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation

            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        defer {
            continuation = nil
        }

        do {
            continuation?.resume(returning: try appleCredential(from: authorization))
        } catch {
            continuation?.resume(throwing: error)
        }
    }

    private func appleCredential(from authorization: ASAuthorization) throws -> AppleCredential {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let identityTokenData = credential.identityToken,
            let identityToken = String(data: identityTokenData, encoding: .utf8)
        else {
            throw NugulAPIError.appleIdentityUnavailable
        }

        let authorizationCode = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        let fullName = PersonNameComponentsFormatter().string(from: credential.fullName ?? PersonNameComponents())
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return AppleCredential(
            identityToken: identityToken,
            authorizationCode: authorizationCode,
            fullName: fullName.isEmpty ? nil : fullName
        )
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: NugulAPIError.authCancelled)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        #if os(iOS)
        return UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first ?? ASPresentationAnchor()
        #elseif os(macOS)
        return NSApplication.shared.keyWindow ?? ASPresentationAnchor()
        #else
        return ASPresentationAnchor()
        #endif
    }
}

private struct AppleCredential {
    let identityToken: String
    let authorizationCode: String?
    let fullName: String?
}

enum AuthTokenStore {
    private static let service = "com.nugulmap.native.auth"
    private static let accessTokenAccount = "accessToken"
    private static let refreshTokenAccount = "refreshToken"

    static func saveAccessToken(_ token: String) {
        saveTokens(accessToken: token, refreshToken: nil)
    }

    static func saveTokens(accessToken: String, refreshToken: String?) {
        saveToken(accessToken, account: accessTokenAccount)

        if let refreshToken, !refreshToken.isEmpty {
            saveToken(refreshToken, account: refreshTokenAccount)
        }
    }

    static func loadAccessToken() -> String? {
        loadToken(account: accessTokenAccount)
    }

    static func loadRefreshToken() -> String? {
        loadToken(account: refreshTokenAccount)
    }

    static func deleteAccessToken() {
        deleteToken(account: accessTokenAccount)
    }

    static func deleteTokens() {
        deleteToken(account: accessTokenAccount)
        deleteToken(account: refreshTokenAccount)
    }

    private static func saveToken(_ token: String, account: String) {
        deleteToken(account: account)

        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data
        ]

        SecItemAdd(query as CFDictionary, nil)
    }

    private static func loadToken(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard
            status == errSecSuccess,
            let data = result as? Data
        else {
            return nil
        }

        return String(data: data, encoding: .utf8)
    }

    private static func deleteToken(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        SecItemDelete(query as CFDictionary)
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}


private struct APIEnvelope<DataPayload: Decodable>: Decodable {
    let success: Bool?
    let message: String?
    let data: DataPayload?
}

private struct ZoneCollectionPayload: Decodable {
    let zones: [SmokingZone]?
}

private struct ZonePayload: Decodable {
    let zone: SmokingZone?
}

private struct ReviewCollectionPayload: Decodable {
    let reviews: [ZoneReview]?
}

private struct ReviewPayload: Decodable {
    let review: ZoneReview?
}

private struct CreateReviewPayload: Encodable {
    let content: String
}

private struct UserPayload: Decodable {
    let user: UserProfile?
}

private struct MobileOAuthExchangeRequest: Encodable {
    let code: String
    let codeVerifier: String
}

private struct AppleMobileLoginRequest: Encodable {
    let identityToken: String
    let authorizationCode: String?
    let fullName: String?
}

private struct MobileOAuthExchangePayload: Decodable {
    let accessToken: String
    let refreshToken: String?
    let profileComplete: Bool
    let user: MobileOAuthUserSummary?
}

private struct MobileOAuthUserSummary: Decodable {
    let email: String?
}

private struct ServerErrorPayload: Decodable {
    let message: String?
}

private extension Data {
    mutating func append(_ string: String) {
        append(Data(string.utf8))
    }
}
