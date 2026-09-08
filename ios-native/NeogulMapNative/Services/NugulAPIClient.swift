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
    case sessionExpired
    case oauthTransactionExpired
    case oauthStateMissing
    case oauthStateMismatch
    case secureStorageUnavailable
    case appleIdentityUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "서비스에 연결할 수 없습니다."
        case .invalidResponse:
            return "정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
        case let .server(statusCode, message):
            if let message = Self.userSafeServerMessage(message) {
                return message
            }
            _ = statusCode
            return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
        case .decodingFailed:
            return "정보를 표시하지 못했습니다."
        case .authCancelled:
            return "로그인이 취소되었습니다."
        case .missingToken:
            return "로그인 정보를 찾을 수 없습니다. 다시 로그인해주세요."
        case .sessionExpired:
            return "로그인이 만료되었습니다. 다시 로그인해주세요."
        case .oauthTransactionExpired:
            return "로그인 요청이 만료되었습니다. 다시 로그인해주세요."
        case .oauthStateMissing:
            return "로그인 요청을 확인할 수 없습니다. 다시 로그인해주세요."
        case .oauthStateMismatch:
            return "로그인 요청이 일치하지 않습니다. 다시 로그인해주세요."
        case .secureStorageUnavailable:
            return "로그인 정보를 안전하게 저장하지 못했습니다. 잠시 후 다시 시도해주세요."
        case .appleIdentityUnavailable:
            return "Apple 로그인 정보를 확인할 수 없습니다."
        }
    }

    private static func userSafeServerMessage(_ rawMessage: String?) -> String? {
        guard let message = rawMessage?.trimmingCharacters(in: .whitespacesAndNewlines),
              !message.isEmpty else {
            return nil
        }
        let lowered = message.lowercased()
        let technicalTokens = [
            "api", "provider", "configured", "http ", "https://", "localhost",
            "exception", "stacktrace", "java.", "spring", "socket", "dns",
            "unknownhost", "failed to connect", "timed out", " key", "키가"
        ]
        return technicalTokens.contains(where: lowered.contains) ? nil : message
    }
}

enum OAuthProvider: String, CaseIterable, Identifiable, Codable {
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
    let appleUserIdentifier: String?

    init(
        accessToken: String,
        refreshToken: String?,
        profileComplete: Bool,
        email: String?,
        appleUserIdentifier: String? = nil
    ) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.profileComplete = profileComplete
        self.email = email
        self.appleUserIdentifier = appleUserIdentifier
    }
}

struct AccountDeletionResult: Equatable {
    let manualAppleRevocationRequired: Bool
    let message: String?
}

struct AuthSessionStorage: @unchecked Sendable {
    let loadRefreshToken: () -> String?
    let saveTokens: (_ accessToken: String, _ refreshToken: String?) throws -> Void
    let deleteTokens: () -> Void

    static let keychain = AuthSessionStorage(
        loadRefreshToken: AuthTokenStore.loadRefreshToken,
        saveTokens: AuthTokenStore.saveTokens,
        deleteTokens: AuthTokenStore.deleteTokens
    )
}

private struct OAuthCallbackCode {
    let code: String?
    let clientState: String
    let providerError: String?
    let profileComplete: Bool
    let email: String?

    init(callbackURL: URL) throws {
        guard AppConfig.isOAuthCallbackURL(callbackURL) else {
            throw NugulAPIError.invalidURL
        }

        guard let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false) else {
            throw NugulAPIError.invalidURL
        }

        guard
            let clientState = components.queryItems?.first(where: { $0.name == "client_state" })?.value,
            !clientState.isEmpty
        else {
            throw NugulAPIError.oauthStateMissing
        }
        guard OAuthClientState.isValidFormat(clientState) else {
            throw NugulAPIError.oauthStateMismatch
        }

        let code = components.queryItems?.first(where: { $0.name == "code" })?.value
        self.code = code?.isEmpty == false ? code : nil
        self.clientState = clientState
        self.providerError = components.queryItems?.first(where: { $0.name == "error" })?.value
        self.profileComplete = components.queryItems?.first(where: { $0.name == "profileComplete" })?.value == "true"
        self.email = components.queryItems?.first(where: { $0.name == "email" })?.value
    }
}

struct PKCEPair {
    let verifier: String
    let challenge: String
    let clientState: String

    static func make() throws -> PKCEPair {
        let verifier = randomURLSafeString(byteCount: 32)
        let digest = SHA256.hash(data: Data(verifier.utf8))
        let challenge = Data(digest).base64URLEncodedString()
        return PKCEPair(
            verifier: verifier,
            challenge: challenge,
            clientState: try OAuthClientState.make()
        )
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

enum OAuthClientState {
    private static let minimumLength = 43
    private static let maximumLength = 128

    static func make(byteCount: Int = 32) throws -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            throw NugulAPIError.secureStorageUnavailable
        }

        let value = Data(bytes).base64URLEncodedString()
        guard isValidFormat(value) else {
            throw NugulAPIError.secureStorageUnavailable
        }
        return value
    }

    static func isValidFormat(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        guard (minimumLength...maximumLength).contains(bytes.count) else {
            return false
        }

        return bytes.allSatisfy { byte in
            (65...90).contains(byte) ||
                (97...122).contains(byte) ||
                (48...57).contains(byte) ||
                byte == 45 ||
                byte == 95
        }
    }

    static func constantTimeEquals(_ lhs: String, _ rhs: String) -> Bool {
        let lhsBytes = Array(lhs.utf8)
        let rhsBytes = Array(rhs.utf8)
        let comparisonCount = max(lhsBytes.count, rhsBytes.count)
        var difference = UInt(lhsBytes.count ^ rhsBytes.count)

        for index in 0..<comparisonCount {
            let lhsByte = index < lhsBytes.count ? lhsBytes[index] : 0
            let rhsByte = index < rhsBytes.count ? rhsBytes[index] : 0
            difference |= UInt(lhsByte ^ rhsByte)
        }

        return difference == 0
    }
}

struct PendingOAuthTransaction: Codable, Equatable {
    let provider: OAuthProvider
    let verifier: String
    let clientState: String
    let createdAt: Date
}

struct PendingOAuthTransactionStorage: @unchecked Sendable {
    let save: (PendingOAuthTransaction) throws -> Void
    let load: () throws -> PendingOAuthTransaction?
    let delete: () -> Void

    static let keychain = PendingOAuthTransactionStorage(
        save: PendingOAuthKeychainStore.save,
        load: PendingOAuthKeychainStore.load,
        delete: PendingOAuthKeychainStore.delete
    )
}

final class PendingOAuthTransactionStore: @unchecked Sendable {
    private let storage: PendingOAuthTransactionStorage
    private let now: () -> Date
    private let validityInterval: TimeInterval
    private let lock = NSLock()

    init(
        storage: PendingOAuthTransactionStorage = .keychain,
        now: @escaping () -> Date = Date.init,
        validityInterval: TimeInterval = 10 * 60
    ) {
        self.storage = storage
        self.now = now
        self.validityInterval = validityInterval
    }

    func begin(provider: OAuthProvider, pair: PKCEPair) throws {
        lock.lock()
        defer { lock.unlock() }
        guard OAuthClientState.isValidFormat(pair.clientState) else {
            throw NugulAPIError.oauthStateMismatch
        }
        try storage.save(
            PendingOAuthTransaction(
                provider: provider,
                verifier: pair.verifier,
                clientState: pair.clientState,
                createdAt: now()
            )
        )
    }

    func claim(
        clientState: String,
        expectedProvider: OAuthProvider? = nil
    ) throws -> PendingOAuthTransaction? {
        lock.lock()
        defer { lock.unlock() }

        guard let transaction = try storage.load() else {
            return nil
        }
        guard
            OAuthClientState.isValidFormat(clientState),
            OAuthClientState.isValidFormat(transaction.clientState),
            OAuthClientState.constantTimeEquals(clientState, transaction.clientState)
        else {
            throw NugulAPIError.oauthStateMismatch
        }

        let age = now().timeIntervalSince(transaction.createdAt)
        guard age >= -60, age <= validityInterval else {
            storage.delete()
            throw NugulAPIError.oauthTransactionExpired
        }
        guard expectedProvider == nil || transaction.provider == expectedProvider else {
            storage.delete()
            throw NugulAPIError.oauthTransactionExpired
        }
        storage.delete()
        return transaction
    }

    func cancel() {
        lock.lock()
        defer { lock.unlock() }
        storage.delete()
    }
}

private enum PendingOAuthKeychainStore {
    private static let service = "com.nugulmap.native.oauth"
    private static let account = "pendingPKCETransaction"

    static func save(_ transaction: PendingOAuthTransaction) throws {
        let data = try JSONEncoder().encode(transaction)
        delete()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data
        ]
        guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else {
            throw NugulAPIError.secureStorageUnavailable
        }
    }

    static func load() throws -> PendingOAuthTransaction? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = result as? Data else {
            throw NugulAPIError.secureStorageUnavailable
        }
        do {
            return try JSONDecoder().decode(PendingOAuthTransaction.self, from: data)
        } catch {
            delete()
            throw NugulAPIError.secureStorageUnavailable
        }
    }

    static func delete() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}


struct NugulAPIClient {
    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder
    private let authSessionStorage: AuthSessionStorage

    init(
        baseURL: URL = AppConfig.apiBaseURL,
        session: URLSession = .shared,
        authSessionStorage: AuthSessionStorage = .keychain
    ) {
        self.baseURL = baseURL
        self.session = session
        self.decoder = JSONDecoder()
        self.authSessionStorage = authSessionStorage
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

    func refreshSession(refreshToken: String) async throws -> SessionTokenPair {
        var request = URLRequest(url: try makeURL(path: "/api/auth/refresh", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(RefreshTokenRequestPayload(refreshToken: refreshToken))

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let result = try decoder.decode(RefreshTokenResponsePayload.self, from: data)
        guard result.success, let accessToken = result.accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.server(statusCode: 401, message: result.message)
        }

        return SessionTokenPair(accessToken: accessToken, refreshToken: result.refreshToken)
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

        let response: APIEnvelope<MapInsight> = try await get(
            path: "/api/insights/map",
            queryItems: queryItems,
            cachePolicy: .reloadIgnoringLocalCacheData
        )
        if response.success == false {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        return response.data ?? .empty
    }

    func fetchRecommendedPlaces(
        intent: RecommendationIntent,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int = 8,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> RecommendationPlaceResponse {
        let response: APIEnvelope<RecommendationPlaceResponse> = try await get(
            path: "/api/recommendations/places",
            queryItems: [
                URLQueryItem(name: "intent", value: intent.rawValue),
                URLQueryItem(name: "lat", value: String(latitude)),
                URLQueryItem(name: "lng", value: String(longitude)),
                URLQueryItem(name: "radiusMeters", value: String(min(max(radiusMeters, 100), 20_000))),
                URLQueryItem(name: "limit", value: String(min(max(limit, 1), 20)))
            ],
            accessToken: accessToken,
            cachePolicy: .reloadIgnoringLocalCacheData
        )
        if response.success == false {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        guard let payload = response.data, payload.intent == intent else {
            throw NugulAPIError.decodingFailed
        }
        return payload
    }

    func fetchRecommendationPreferences(
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> RecommendationPreferences {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        let response: APIEnvelope<RecommendationPreferences> = try await get(
            path: "/api/recommendations/preferences",
            accessToken: accessToken,
            cachePolicy: .reloadIgnoringLocalCacheData
        )
        guard response.success != false, let payload = response.data else {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        return payload
    }

    func updateRecommendationPreferences(
        _ requestPayload: RecommendationPreferenceUpdate,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> RecommendationPreferences {
        try await sendRecommendationJSON(
            path: "/api/recommendations/preferences",
            method: "PUT",
            payload: requestPayload,
            accessToken: accessToken
        )
    }

    func fetchSavedRecommendationItems(
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> [RecommendationSavedItem] {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        let response: APIEnvelope<[RecommendationSavedItem]> = try await get(
            path: "/api/recommendations/saved-items",
            accessToken: accessToken,
            cachePolicy: .reloadIgnoringLocalCacheData
        )
        guard response.success != false else {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        return response.data ?? []
    }

    func saveRecommendationItem(
        _ requestPayload: RecommendationSavedItemRequest,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> RecommendationSavedItem {
        try await sendRecommendationJSON(
            path: "/api/recommendations/saved-items",
            method: "POST",
            payload: requestPayload,
            accessToken: accessToken
        )
    }

    func deleteSavedRecommendationItem(
        provider: String,
        itemID: String,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        var request = URLRequest(
            url: try makeURL(
                path: "/api/recommendations/saved-items",
                queryItems: [
                    URLQueryItem(name: "itemType", value: RecommendationItemType.place.rawValue),
                    URLQueryItem(name: "provider", value: provider),
                    URLQueryItem(name: "itemId", value: itemID)
                ]
            )
        )
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)
        try await sendWithoutResponsePayload(request)
    }

    func recordRecommendationInteraction(
        _ requestPayload: RecommendationInteractionRequest,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> RecommendationInteraction {
        try await sendRecommendationJSON(
            path: "/api/recommendations/interactions",
            method: "POST",
            payload: requestPayload,
            accessToken: accessToken
        )
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

    func fetchLocationLetters(
        latitude: Double,
        longitude: Double,
        limit: Int = 30,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> LocationLetterNearby {
        let response: APIEnvelope<LocationLetterNearby> = try await get(
            path: "/api/location-letters",
            queryItems: [
                URLQueryItem(name: "lat", value: String(latitude)),
                URLQueryItem(name: "lng", value: String(longitude)),
                URLQueryItem(name: "limit", value: String(min(max(limit, 1), 50)))
            ],
            accessToken: accessToken,
            cachePolicy: .reloadIgnoringLocalCacheData
        )
        guard response.success != false else {
            throw NugulAPIError.server(statusCode: 200, message: response.message)
        }
        return response.data ?? .empty
    }

    func createLocationLetter(
        latitude: Double,
        longitude: Double,
        content: String,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> LocationLetter {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        var request = URLRequest(url: try makeURL(path: "/api/location-letters", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(LocationLetterCreatePayload(
            latitude: latitude,
            longitude: longitude,
            content: content
        ))

        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
        let result = try decoder.decode(APIEnvelope<LocationLetterPayload>.self, from: data)
        guard let letter = result.data?.letter else {
            throw NugulAPIError.decodingFailed
        }
        return letter
    }

    func deleteLocationLetter(
        letterID: Int,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        var request = URLRequest(
            url: try makeURL(path: "/api/location-letters/\(letterID)", queryItems: [])
        )
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)
        try await sendWithoutResponsePayload(request)
    }

    func setLocationLetterLiked(
        letterID: Int,
        liked: Bool,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> LocationLetterLikeState {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        var request = URLRequest(
            url: try makeURL(path: "/api/location-letters/\(letterID)/like", queryItems: [])
        )
        request.httpMethod = liked ? "PUT" : "DELETE"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
        let result = try decoder.decode(APIEnvelope<LocationLetterLikeState>.self, from: data)
        guard let state = result.data else {
            throw NugulAPIError.decodingFailed
        }
        return state
    }

    func reportLocationLetter(
        letterID: Int,
        reason: String,
        details: String? = nil,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }
        var request = URLRequest(
            url: try makeURL(path: "/api/location-letters/\(letterID)/reports", queryItems: [])
        )
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(LocationLetterReportPayload(
            reason: reason,
            details: details
        ))
        try await sendWithoutResponsePayload(request)
    }

    func fetchReviews(
        zoneID: Int,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> [ZoneReview] {
        let response: APIEnvelope<ReviewCollectionPayload> = try await get(
            path: "/api/zones/\(zoneID)/reviews",
            accessToken: accessToken
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

        let (data, response) = try await perform(request)
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

    func reportReview(
        zoneID: Int,
        reviewID: Int,
        reason: String,
        details: String? = nil,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        var request = URLRequest(
            url: try makeURL(path: "/api/zones/\(zoneID)/reviews/\(reviewID)/reports", queryItems: [])
        )
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(
            ReviewReportRequestPayload(
                reason: reason,
                details: details.flatMap { value in
                    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                    return trimmed.isEmpty ? nil : trimmed
                }
            )
        )

        try await sendWithoutResponsePayload(request)
    }

    func reportZone(
        zoneID: Int,
        reason: String,
        details: String? = nil,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        var request = URLRequest(url: try makeURL(path: "/api/zones/\(zoneID)/reports", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(
            ReviewReportRequestPayload(
                reason: reason,
                details: details.flatMap { value in
                    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                    return trimmed.isEmpty ? nil : trimmed
                }
            )
        )

        try await sendWithoutResponsePayload(request)
    }

    func blockUser(
        authorID: Int,
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        var request = URLRequest(url: try makeURL(path: "/api/users/\(authorID)/block", queryItems: []))
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        try await sendWithoutResponsePayload(request)
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

        let (data, response) = try await perform(request)
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

        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
    }

    func deleteCurrentUser(
        accessToken: String? = AuthTokenStore.loadAccessToken()
    ) async throws -> AccountDeletionResult {
        var request = URLRequest(url: try makeURL(path: "/api/users/me", queryItems: []))
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        guard !data.isEmpty,
              let result = try? decoder.decode(APIEnvelope<AccountDeletionPayload>.self, from: data) else {
            return AccountDeletionResult(manualAppleRevocationRequired: false, message: nil)
        }

        return AccountDeletionResult(
            manualAppleRevocationRequired: result.data?.manualAppleRevocationRequired ?? false,
            message: result.message
        )
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

        let (data, response) = try await perform(request)
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
        guard let path else {
            return nil
        }

        let value = path.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            return nil
        }

        if let absoluteURL = URL(string: value), absoluteURL.scheme != nil {
            if Self.hasSameOrigin(absoluteURL, as: baseURL) {
                return absoluteURL
            }
            return PublicURLPolicy.safeURL(value, allowedSchemes: ["https"])
        }

        let normalizedPath = value.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if normalizedPath.hasPrefix("api/images/") {
            return try? makeURL(path: "/\(normalizedPath)", queryItems: [])
        }

        if normalizedPath.hasPrefix("images/") {
            return try? makeURL(path: "/api/\(normalizedPath)", queryItems: [])
        }

        return try? makeURL(path: "/api/images/\(normalizedPath)", queryItems: [])
    }

    private static func hasSameOrigin(_ candidate: URL, as trustedBaseURL: URL) -> Bool {
        guard let candidateComponents = URLComponents(url: candidate, resolvingAgainstBaseURL: false),
              let baseComponents = URLComponents(url: trustedBaseURL, resolvingAgainstBaseURL: false),
              candidateComponents.user == nil,
              candidateComponents.password == nil,
              let candidateScheme = candidateComponents.scheme?.lowercased(),
              let baseScheme = baseComponents.scheme?.lowercased(),
              candidateScheme == baseScheme,
              let candidateHost = candidateComponents.host?.lowercased(),
              let baseHost = baseComponents.host?.lowercased(),
              candidateHost == baseHost else {
            return false
        }

        return effectivePort(for: candidateComponents, scheme: candidateScheme)
            == effectivePort(for: baseComponents, scheme: baseScheme)
    }

    private static func effectivePort(for components: URLComponents, scheme: String) -> Int? {
        if let port = components.port {
            return port
        }
        switch scheme {
        case "http":
            return 80
        case "https":
            return 443
        default:
            return nil
        }
    }

    private func get<Response: Decodable>(
        path: String,
        queryItems: [URLQueryItem] = [],
        accessToken: String? = nil,
        cachePolicy: URLRequest.CachePolicy = .useProtocolCachePolicy
    ) async throws -> Response {
        var request = URLRequest(
            url: try makeURL(path: path, queryItems: queryItems),
            cachePolicy: cachePolicy,
            timeoutInterval: 10
        )
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthHeader(accessToken, to: &request)

        let (data, response) = try await perform(request)
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

    private func sendRecommendationJSON<RequestPayload: Encodable, ResponsePayload: Decodable>(
        path: String,
        method: String,
        payload: RequestPayload,
        accessToken: String?
    ) async throws -> ResponsePayload {
        guard let accessToken, !accessToken.isEmpty else {
            throw NugulAPIError.missingToken
        }

        var request = URLRequest(url: try makeURL(path: path, queryItems: []))
        request.httpMethod = method
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyAuthHeader(accessToken, to: &request)
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }

        let envelope: APIEnvelope<ResponsePayload>
        do {
            envelope = try decoder.decode(APIEnvelope<ResponsePayload>.self, from: data)
        } catch {
            throw NugulAPIError.decodingFailed
        }
        guard envelope.success != false, let responsePayload = envelope.data else {
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: envelope.message)
        }
        return responsePayload
    }

    private func sendWithoutResponsePayload(_ request: URLRequest) async throws {
        let (data, response) = try await perform(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NugulAPIError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(ServerErrorPayload.self, from: data)
            throw NugulAPIError.server(statusCode: httpResponse.statusCode, message: serverMessage?.message)
        }
    }

    private func perform(_ request: URLRequest) async throws -> (Data, URLResponse) {
        let (data, response) = try await session.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            httpResponse.statusCode == 401,
            request.value(forHTTPHeaderField: "Authorization") != nil
        else {
            return (data, response)
        }

        let refreshedSession = try await refreshStoredSession()
        var retryRequest = request
        applyAuthHeader(refreshedSession.accessToken, to: &retryRequest)

        let (retryData, retryResponse) = try await session.data(for: retryRequest)
        if let retryHTTPResponse = retryResponse as? HTTPURLResponse, retryHTTPResponse.statusCode == 401 {
            authSessionStorage.deleteTokens()
            throw NugulAPIError.sessionExpired
        }
        return (retryData, retryResponse)
    }

    private func refreshStoredSession() async throws -> SessionTokenPair {
        guard let refreshToken = authSessionStorage.loadRefreshToken(), !refreshToken.isEmpty else {
            authSessionStorage.deleteTokens()
            throw NugulAPIError.sessionExpired
        }

        do {
            let refreshedSession = try await refreshSession(refreshToken: refreshToken)
            try authSessionStorage.saveTokens(
                refreshedSession.accessToken,
                refreshedSession.refreshToken ?? refreshToken
            )
            return refreshedSession
        } catch let NugulAPIError.server(statusCode, _) where statusCode == 400 || statusCode == 401 {
            authSessionStorage.deleteTokens()
            throw NugulAPIError.sessionExpired
        } catch NugulAPIError.missingToken {
            authSessionStorage.deleteTokens()
            throw NugulAPIError.sessionExpired
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
    private var isInteractiveSignInInProgress = false
    private let transactionStore: PendingOAuthTransactionStore

    init(transactionStore: PendingOAuthTransactionStore = PendingOAuthTransactionStore()) {
        self.transactionStore = transactionStore
        super.init()
    }

    var isAwaitingCallback: Bool {
        isInteractiveSignInInProgress
    }

    func signIn(with provider: OAuthProvider, apiClient: NugulAPIClient) async throws -> OAuthLoginResult {
        guard !isInteractiveSignInInProgress else {
            throw NugulAPIError.authCancelled
        }
        isInteractiveSignInInProgress = true
        defer { isInteractiveSignInInProgress = false }

        let pkce = try PKCEPair.make()
        try transactionStore.begin(provider: provider, pair: pkce)

        let callbackURL: URL
        do {
            callbackURL = try await receiveAuthorizationCode(provider: provider, pkce: pkce)
        } catch {
            transactionStore.cancel()
            throw error
        }

        guard let result = try await exchangeCallback(
            callbackURL,
            expectedProvider: provider,
            apiClient: apiClient
        ) else {
            throw NugulAPIError.oauthTransactionExpired
        }

        return result
    }

    func resumeSignIn(from callbackURL: URL, apiClient: NugulAPIClient) async throws -> OAuthLoginResult? {
        guard AppConfig.isOAuthCallbackURL(callbackURL), !isAwaitingCallback else {
            return nil
        }
        return try await exchangeCallback(callbackURL, expectedProvider: nil, apiClient: apiClient)
    }

    private func exchangeCallback(
        _ callbackURL: URL,
        expectedProvider: OAuthProvider?,
        apiClient: NugulAPIClient
    ) async throws -> OAuthLoginResult? {
        let callback = try OAuthCallbackCode(callbackURL: callbackURL)
        guard let transaction = try transactionStore.claim(
            clientState: callback.clientState,
            expectedProvider: expectedProvider
        ) else {
            return nil
        }
        if let providerError = callback.providerError, !providerError.isEmpty {
            throw NugulAPIError.server(statusCode: 400, message: providerError)
        }
        guard let code = callback.code else {
            throw NugulAPIError.missingToken
        }
        let result = try await apiClient.exchangeMobileOAuthCode(
            code: code,
            codeVerifier: transaction.verifier
        )

        return OAuthLoginResult(
            accessToken: result.accessToken,
            refreshToken: result.refreshToken,
            profileComplete: result.profileComplete,
            email: result.email ?? callback.email
        )
    }

    private func receiveAuthorizationCode(provider: OAuthProvider, pkce: PKCEPair) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: AppConfig.oauthAuthorizationURL(
                    provider: provider,
                    codeChallenge: pkce.challenge,
                    clientState: pkce.clientState
                ),
                callbackURLScheme: AppConfig.oauthCallbackScheme
            ) { callbackURL, error in
                self.session = nil

                if let callbackURL {
                    continuation.resume(returning: callbackURL)
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
        let result = try await apiClient.exchangeAppleIdentityToken(
            identityToken: credential.identityToken,
            authorizationCode: credential.authorizationCode,
            fullName: credential.fullName
        )
        return result.withAppleUserIdentifier(credential.userIdentifier)
    }

    func signIn(apiClient: NugulAPIClient) async throws -> OAuthLoginResult {
        let credential = try await requestCredential()
        let result = try await apiClient.exchangeAppleIdentityToken(
            identityToken: credential.identityToken,
            authorizationCode: credential.authorizationCode,
            fullName: credential.fullName
        )
        return result.withAppleUserIdentifier(credential.userIdentifier)
    }

    func credentialState(for userIdentifier: String) async -> ASAuthorizationAppleIDProvider.CredentialState? {
        await withCheckedContinuation { continuation in
            ASAuthorizationAppleIDProvider().getCredentialState(forUserID: userIdentifier) { state, error in
                continuation.resume(returning: error == nil ? state : nil)
            }
        }
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
            fullName: fullName.isEmpty ? nil : fullName,
            userIdentifier: credential.user
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
    let userIdentifier: String
}

enum AppleCredentialSessionPolicy {
    static func requiresLocalSignOut(for state: ASAuthorizationAppleIDProvider.CredentialState) -> Bool {
        switch state {
        case .revoked, .notFound, .transferred:
            return true
        case .authorized:
            return false
        @unknown default:
            return false
        }
    }
}

private extension OAuthLoginResult {
    func withAppleUserIdentifier(_ userIdentifier: String) -> OAuthLoginResult {
        OAuthLoginResult(
            accessToken: accessToken,
            refreshToken: refreshToken,
            profileComplete: profileComplete,
            email: email,
            appleUserIdentifier: userIdentifier
        )
    }
}

enum AuthTokenStore {
    private static let service = "com.nugulmap.native.auth"
    private static let accessTokenAccount = "accessToken"
    private static let refreshTokenAccount = "refreshToken"
    private static let appleUserIdentifierAccount = "appleUserIdentifier"

    static func saveAccessToken(_ token: String) throws {
        try saveTokens(accessToken: token, refreshToken: nil)
    }

    static func saveTokens(accessToken: String, refreshToken: String?) throws {
        do {
            try saveToken(accessToken, account: accessTokenAccount)

            if let refreshToken, !refreshToken.isEmpty {
                try saveToken(refreshToken, account: refreshTokenAccount)
            } else {
                deleteToken(account: refreshTokenAccount)
            }
        } catch {
            deleteTokens()
            throw NugulAPIError.secureStorageUnavailable
        }
    }

    static func replaceLoginSession(
        accessToken: String,
        refreshToken: String?,
        appleUserIdentifier: String?
    ) throws {
        deleteTokens()
        do {
            try saveTokens(accessToken: accessToken, refreshToken: refreshToken)
            if let appleUserIdentifier, !appleUserIdentifier.isEmpty {
                try saveToken(appleUserIdentifier, account: appleUserIdentifierAccount)
            } else {
                deleteToken(account: appleUserIdentifierAccount)
            }
        } catch {
            deleteTokens()
            throw NugulAPIError.secureStorageUnavailable
        }
    }

    static func loadAccessToken() -> String? {
        loadToken(account: accessTokenAccount)
    }

    static func loadRefreshToken() -> String? {
        loadToken(account: refreshTokenAccount)
    }

    static func loadAppleUserIdentifier() -> String? {
        loadToken(account: appleUserIdentifierAccount)
    }

    static func deleteAccessToken() {
        deleteToken(account: accessTokenAccount)
    }

    static func deleteTokens() {
        deleteToken(account: accessTokenAccount)
        deleteToken(account: refreshTokenAccount)
        deleteToken(account: appleUserIdentifierAccount)
    }

    private static func saveToken(_ token: String, account: String) throws {
        deleteToken(account: account)

        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data
        ]

        guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else {
            throw NugulAPIError.secureStorageUnavailable
        }
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

private struct ReviewReportRequestPayload: Encodable {
    let reason: String
    let details: String?
}

private struct UserPayload: Decodable {
    let user: UserProfile?
}

private struct AccountDeletionPayload: Decodable {
    let manualAppleRevocationRequired: Bool?
}

private struct MobileOAuthExchangeRequest: Encodable {
    let code: String
    let codeVerifier: String
}

struct SessionTokenPair: Equatable {
    let accessToken: String
    let refreshToken: String?
}

private struct RefreshTokenRequestPayload: Encodable {
    let refreshToken: String
}

private struct RefreshTokenResponsePayload: Decodable {
    let success: Bool
    let message: String?
    let accessToken: String?
    let refreshToken: String?
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

enum RecommendationIntent: String, Codable, CaseIterable, Identifiable {
    case cafe = "CAFE"
    case restaurant = "RESTAURANT"
    case date = "DATE"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .cafe: return "카페"
        case .restaurant: return "식당"
        case .date: return "데이트"
        }
    }
}

enum RecommendationDataStatus: String, Codable {
    case ready = "READY"
    case partial = "PARTIAL"
    case providerNotConfigured = "PROVIDER_NOT_CONFIGURED"
    case providerUnavailable = "PROVIDER_UNAVAILABLE"
}

enum RecommendationPersonalizationMode: String, Codable {
    case contextOnly = "CONTEXT_ONLY"
    case personalized = "PERSONALIZED"

    var allowsInteractionLogging: Bool { self == .personalized }
}

enum RecommendationPlaceProvider: String, Codable {
    case kakaoLocal = "KAKAO_LOCAL"
    case seoulOpenData = "SEOUL_OPEN_DATA"
    case tourAPI = "TOUR_API"
    case userSubmitted = "USER_SUBMITTED"
    case unknown = "UNKNOWN"
}

enum RecommendationReasonCode: String, Codable {
    case cafeIntentMatch = "CAFE_INTENT_MATCH"
    case restaurantIntentMatch = "RESTAURANT_INTENT_MATCH"
    case dateIntentMatch = "DATE_INTENT_MATCH"
    case preferredTagMatch = "PREFERRED_TAG_MATCH"
    case positiveHistoryMatch = "POSITIVE_HISTORY_MATCH"
    case withinOneKilometer = "WITHIN_ONE_KILOMETER"
    case freshCandidate = "FRESH_CANDIDATE"
    case trustedProvider = "TRUSTED_PROVIDER"
    case diverseCategory = "DIVERSE_CATEGORY"

    var title: String {
        switch self {
        case .cafeIntentMatch: return "카페 취향"
        case .restaurantIntentMatch: return "식사 취향"
        case .dateIntentMatch: return "데이트 코스"
        case .preferredTagMatch: return "선호 태그"
        case .positiveHistoryMatch: return "이전 관심"
        case .withinOneKilometer: return "1km 이내"
        case .freshCandidate: return "새로운 후보"
        case .trustedProvider: return "확인된 장소"
        case .diverseCategory: return "다양한 선택"
        }
    }
}

struct RecommendationPlaceResponse: Decodable, Equatable {
    let requestId: String
    let modelVersion: String
    let dataStatus: RecommendationDataStatus
    let personalizationMode: RecommendationPersonalizationMode
    let intent: RecommendationIntent
    let eligibleCandidateCount: Int
    let generatedAt: String
    let items: [RecommendationPlace]
}

struct RecommendationPlace: Decodable, Equatable {
    let id: String
    let provider: RecommendationPlaceProvider
    let name: String
    let category: String
    let address: String
    let latitude: Double
    let longitude: Double
    let distanceMeters: Int?
    let score: Double
    let reasonCodes: [RecommendationReasonCode]
    let sourceUrl: String

    var stableKey: String {
        "\(provider.rawValue.lowercased()):\(id.trimmingCharacters(in: .whitespacesAndNewlines))"
    }

    var safeSourceURL: URL? {
        PublicURLPolicy.safeURL(sourceUrl, allowedSchemes: ["http", "https"])
    }

    var distanceLabel: String? {
        guard let distanceMeters else { return nil }
        if distanceMeters < 1_000 {
            return "\(distanceMeters)m"
        }
        return String(format: "%.1fkm", Double(distanceMeters) / 1_000)
    }
}

enum RecommendationItemType: String, Codable {
    case place = "PLACE"
    case event = "EVENT"
    case hotplace = "HOTPLACE"
    case course = "COURSE"
}

enum RecommendationEventType: String, Codable {
    case impression = "IMPRESSION"
    case detailOpen = "DETAIL_OPEN"
    case save = "SAVE"
    case unsave = "UNSAVE"
    case route = "ROUTE"
    case notInterested = "NOT_INTERESTED"
}

struct RecommendationInteractionRequest: Encodable {
    let requestId: String
    let itemType: RecommendationItemType
    let provider: String
    let itemId: String
    let eventType: RecommendationEventType
    let intent: String
    let contextRegion: String?
    let modelVersion: String
    let occurredAt: String?
}

struct RecommendationInteraction: Decodable, Equatable {
    let id: Int
    let requestId: String
    let itemType: RecommendationItemType
    let provider: String
    let itemId: String
    let eventType: RecommendationEventType
    let intent: String
    let contextRegion: String?
    let modelVersion: String
    let occurredAt: String
}

struct RecommendationSavedItemRequest: Encodable {
    let itemType: RecommendationItemType
    let provider: String
    let itemId: String
    let title: String
    let category: String?
    let lat: Double
    let lng: Double
    let sourceUrl: String?
}

struct RecommendationSavedItem: Decodable, Equatable {
    let id: Int
    let itemType: RecommendationItemType
    let provider: String
    let itemId: String
    let title: String
    let category: String?
    let lat: Double
    let lng: Double
    let sourceUrl: String?
    let createdAt: String
    let updatedAt: String

    var stableKey: String {
        "\(provider.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()):\(itemId.trimmingCharacters(in: .whitespacesAndNewlines))"
    }
}

enum RecommendationBudgetLevel: String, Codable {
    case any = "ANY"
    case low = "LOW"
    case medium = "MEDIUM"
    case high = "HIGH"
}

struct RecommendationPreferences: Decodable, Equatable {
    let personalizationEnabled: Bool
    let preferredTags: [String]
    let dislikedTags: [String]
    let budgetLevel: RecommendationBudgetLevel?
    let maxDistanceMeters: Int?
    let updatedAt: String?
}

struct RecommendationPreferenceUpdate: Encodable {
    let personalizationEnabled: Bool
    let preferredTags: [String]
    let dislikedTags: [String]
    let budgetLevel: RecommendationBudgetLevel?
    let maxDistanceMeters: Int?
}
