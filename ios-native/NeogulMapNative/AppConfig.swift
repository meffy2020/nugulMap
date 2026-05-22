import Foundation

enum AppConfig {
    private static let fallbackAPIBaseURL = URL(string: "https://api.nugulmap.com")!
    static let oauthCallbackScheme = "nugulmap"
    static let oauthCallbackURL = URL(string: "nugulmap://oauth/callback")!

    static var apiBaseURL: URL {
        guard
            let rawValue = Bundle.main.object(forInfoDictionaryKey: "NUGUL_API_BASE_URL") as? String,
            !rawValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            let url = URL(string: rawValue.trimmingCharacters(in: .whitespacesAndNewlines))
        else {
            return fallbackAPIBaseURL
        }

        return url
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
