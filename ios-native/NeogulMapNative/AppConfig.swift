import Foundation

enum AppConfig {
    private static let fallbackAPIBaseURL = URL(string: "https://api.nugulmap.com")!
    static let oauthCallbackScheme = "nugulmap"
    static let oauthCallbackURL = URL(string: "nugulmap://oauth/callback")!
    static let privacyPolicyURL = URL(string: "https://nugulmap.com/privacy")!
    static let accountDeletionURL = URL(string: "https://nugulmap.com/account-deletion")!

    static var kakaoNativeAppKey: String? {
        guard let rawValue = Bundle.main.object(forInfoDictionaryKey: "KAKAO_NATIVE_APP_KEY") as? String else {
            return nil
        }

        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value != "$(KAKAO_NATIVE_APP_KEY)" else {
            return nil
        }

        return value
    }

    static var apiBaseURL: URL {
        if let url = configuredURL(from: ProcessInfo.processInfo.environment["NUGUL_API_BASE_URL"]) {
            return url
        }

        if let url = configuredURL(from: ProcessInfo.processInfo.environment["SIMCTL_CHILD_NUGUL_API_BASE_URL"]) {
            return url
        }

        if let url = configuredURL(from: Bundle.main.object(forInfoDictionaryKey: "NUGUL_API_BASE_URL") as? String) {
            return url
        }

        return fallbackAPIBaseURL
    }

    private static func configuredURL(from rawValue: String?) -> URL? {
        guard
            let rawValue,
            !rawValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return nil
        }

        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value != "$(NUGUL_API_BASE_URL)" else {
            return nil
        }

        return URL(string: value)
    }

    static func oauthAuthorizationURL(provider: OAuthProvider, codeChallenge: String) -> URL {
        var components = URLComponents(
            url: apiBaseURL.appendingPathComponent("api/oauth2/authorization/\(provider.rawValue)"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "redirect_uri", value: oauthCallbackURL.absoluteString),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]
        return components.url!
    }
}
