import AppIntents
import Foundation

enum PendingIntentRoute: Equatable {
    case map
    case report
    case profile
    case search(String)

    private static let storageKey = "NugulMapPendingIntentRoute"

    var storageValue: String {
        switch self {
        case .map:
            return "map"
        case .report:
            return "report"
        case .profile:
            return "profile"
        case let .search(keyword):
            return "search:\(keyword)"
        }
    }

    static func save(_ route: PendingIntentRoute) {
        UserDefaults.standard.set(route.storageValue, forKey: storageKey)
    }

    static func consume() -> PendingIntentRoute? {
        guard let value = UserDefaults.standard.string(forKey: storageKey) else {
            return nil
        }

        UserDefaults.standard.removeObject(forKey: storageKey)
        return PendingIntentRoute(storageValue: value)
    }

    init?(storageValue: String) {
        if storageValue == "map" {
            self = .map
        } else if storageValue == "report" {
            self = .report
        } else if storageValue == "profile" {
            self = .profile
        } else if storageValue.hasPrefix("search:") {
            self = .search(String(storageValue.dropFirst("search:".count)))
        } else {
            return nil
        }
    }

    init?(url: URL) {
        guard url.scheme == AppConfig.oauthCallbackScheme else {
            return nil
        }

        if url.host == "open" {
            switch url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
            case "report":
                self = .report
            case "profile":
                self = .profile
            default:
                self = .map
            }
            return
        }

        if url.host == "search" {
            let keyword = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?
                .first(where: { $0.name == "q" })?
                .value ?? ""
            self = .search(keyword)
            return
        }

        return nil
    }
}

enum NugulIntentDestination: String, AppEnum {
    case map
    case report
    case profile

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "너굴맵 화면")
    static let caseDisplayRepresentations: [NugulIntentDestination: DisplayRepresentation] = [
        .map: "지도",
        .report: "제보하기",
        .profile: "프로필"
    ]

    var route: PendingIntentRoute {
        switch self {
        case .map:
            return .map
        case .report:
            return .report
        case .profile:
            return .profile
        }
    }
}

struct OpenNugulMapIntent: AppIntent {
    static let title: LocalizedStringResource = "너굴맵 열기"
    static let description = IntentDescription("너굴맵을 원하는 화면으로 엽니다.")
    static let openAppWhenRun = true

    @Parameter(title: "화면")
    var destination: NugulIntentDestination

    init() {
        destination = .map
    }

    init(destination: NugulIntentDestination) {
        self.destination = destination
    }

    func perform() async throws -> some IntentResult {
        PendingIntentRoute.save(destination.route)
        return .result(dialog: "\(destinationText) 화면을 엽니다.")
    }

    private var destinationText: String {
        switch destination {
        case .map:
            return "지도"
        case .report:
            return "제보하기"
        case .profile:
            return "프로필"
        }
    }
}

struct SearchNugulZonesIntent: AppIntent {
    static let title: LocalizedStringResource = "흡연구역 검색"
    static let description = IntentDescription("너굴맵에서 장소명이나 주소로 흡연구역을 검색합니다.")
    static let openAppWhenRun = true

    @Parameter(title: "검색어")
    var keyword: String

    init() {
        keyword = ""
    }

    init(keyword: String) {
        self.keyword = keyword
    }

    func perform() async throws -> some IntentResult {
        PendingIntentRoute.save(.search(keyword))
        return .result(dialog: "\(keyword)을 검색합니다.")
    }
}

struct NugulZoneEntity: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "흡연구역")
    static let defaultQuery = NugulZoneEntityQuery()

    let id: String
    let title: String
    let address: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: LocalizedStringResource(stringLiteral: title),
            subtitle: LocalizedStringResource(stringLiteral: address)
        )
    }
}

struct NugulZoneEntityQuery: EntityQuery {
    func entities(for identifiers: [NugulZoneEntity.ID]) async throws -> [NugulZoneEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [NugulZoneEntity] {
        SmokingZone.fallback.map {
            NugulZoneEntity(id: String($0.id), title: $0.title, address: $0.address)
        }
    }
}

struct NugulMapShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenNugulMapIntent(destination: .map),
            phrases: [
                "\(.applicationName) 열어",
                "\(.applicationName) 지도 보여줘"
            ],
            shortTitle: "지도 열기",
            systemImageName: "map"
        )

        AppShortcut(
            intent: OpenNugulMapIntent(destination: .report),
            phrases: [
                "\(.applicationName) 제보 시작",
                "\(.applicationName) 흡연구역 제보"
            ],
            shortTitle: "제보하기",
            systemImageName: "plus.circle"
        )

        AppShortcut(
            intent: SearchNugulZonesIntent(),
            phrases: [
                "\(.applicationName)에서 흡연구역 검색",
                "\(.applicationName)에서 장소 검색"
            ],
            shortTitle: "흡연구역 검색",
            systemImageName: "magnifyingglass"
        )
    }
}
