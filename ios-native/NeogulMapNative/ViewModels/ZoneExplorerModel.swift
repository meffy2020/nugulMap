import AuthenticationServices
import Combine
import Foundation
import MapKit

enum InsightLoadLane: String {
    case all
    case hotplaces
    case events
}

@MainActor
final class ZoneExplorerModel: ObservableObject {
    @Published var zones: [SmokingZone]
    @Published private(set) var hotplaceInsight: HotplaceInsight = .empty
    @Published private(set) var eventInsight: EventInsight = .empty
    @Published private(set) var insightStatus: InsightStatus?
    @Published private(set) var isLoadingInsights = false
    @Published private(set) var insightErrorMessage: String?
    @Published private(set) var lastInsightRefreshAt: Date?
    @Published private(set) var latestSeason3SearchHotplace: Hotplace?
    @Published private(set) var latestSeason3SearchEvent: TrendEvent?
    @Published private(set) var presentsLatestSeason3SearchResult = false
    @Published private(set) var recommendationResponse: RecommendationPlaceResponse?
    @Published private(set) var recommendationPreferences: RecommendationPreferences?
    @Published private(set) var savedRecommendationKeys: Set<String> = []
    @Published private(set) var hiddenRecommendationKeys: Set<String> = []
    @Published private(set) var isLoadingRecommendations = false
    @Published private(set) var recommendationErrorMessage: String?
    @Published private(set) var locationErrorMessage: String?
    @Published private(set) var isLoading = false
    @Published private(set) var isSubmittingZone = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var mapErrorMessage: String?
    @Published private(set) var reviewsByZoneID: [Int: [ZoneReview]] = [:]
    @Published private(set) var reviewLoadErrorByZoneID: [Int: String] = [:]
    @Published private(set) var loadingReviewZoneID: Int?
    @Published private(set) var reviewModerationMessage: String?
    @Published private(set) var zoneModerationMessage: String?
    @Published private(set) var locationLetters: LocationLetterNearby = .empty
    @Published private(set) var isPresentingLocationLetters = false
    @Published private(set) var isLoadingLocationLetters = false
    @Published private(set) var isSubmittingLocationLetter = false
    @Published private(set) var isUpdatingLocationLetterLike = false
    @Published private(set) var locationLetterMessage: String?
    @Published private(set) var locationLetterErrorMessage: String?
    @Published private(set) var currentUser: UserProfile?
    @Published private(set) var userZones: [SmokingZone] = []
    @Published private(set) var userZonesErrorMessage: String?
    @Published private(set) var isAuthLoading = false
    @Published private(set) var isDeletingAccount = false
    @Published private(set) var accountDeletionNotice: String?
    @Published private(set) var accountDeletionErrorMessage: String?
    @Published private(set) var isLoadingUserZones = false
    @Published var pendingSignupEmail: String?
    @Published var query = ""
    @Published var selectedZone: SmokingZone?

    private let apiClient: NugulAPIClient
    private let oauthSession = OAuthWebSession()
    private let appleSignInSession = AppleSignInSession()
    private let lastSubmitStorageKey = "nugul_last_submit"
    private let submissionCooldownSeconds: TimeInterval = 30
    private var zoneRequestSequence = 0
    private var insightRequestSequence = 0
    private var recommendationRequestSequence = 0
    private var locationLetterRequestSequence = 0
    private var activeSeason3SearchRequestID: Int?
    private var inFlightZoneBoundsKey: String?
    private var lastLoadedZoneBoundsKey: String?
    private var inFlightInsightBoundsKey: String?
    private var lastLoadedInsightBoundsKey: String?
    private var inFlightRecommendationKey: String?
    private var lastLoadedRecommendationKey: String?

    init(apiClient: NugulAPIClient = NugulAPIClient()) {
        self.apiClient = apiClient

        #if DEBUG
        if let selectedZoneID = ProcessInfo.processInfo.environment["NUGUL_UI_TEST_SELECTED_ZONE_ID"]
            .flatMap(Int.init) {
            let reviewFixtureZone = SmokingZone(
                id: selectedZoneID,
                region: "서울특별시 성동구",
                type: "OPEN",
                subtype: "개방",
                description: "성수 지도 심사용 흡연구역",
                date: "2026-07-12",
                latitude: 37.5445,
                longitude: 127.0555,
                address: "서울특별시 성동구 연무장길 12",
                user: "운영팀"
            )
            self.zones = [reviewFixtureZone]
            self.selectedZone = reviewFixtureZone
        } else {
            self.zones = []
        }
        #else
        self.zones = []
        #endif

        #if DEBUG
        let environment = ProcessInfo.processInfo.environment
        if environment["NUGUL_UI_TEST_RESET_SESSION"] == "1" {
            AuthTokenStore.deleteTokens()
            UserDefaults.standard.removeObject(forKey: "NugulMapPendingIntentRoute")

            if let accessToken = environment["NUGUL_UI_TEST_AUTH_TOKEN"]?
                .trimmingCharacters(in: .whitespacesAndNewlines),
               !accessToken.isEmpty {
                try? AuthTokenStore.replaceLoginSession(
                    accessToken: accessToken,
                    refreshToken: nil,
                    appleUserIdentifier: nil
                )
            }
        }
        if environment["NUGUL_UI_TEST_RECOMMENDATION_FIXTURE"] == "1" {
            recommendationResponse = RecommendationPlaceResponse(
                requestId: "ios-ui-preview-20260901",
                modelVersion: "context-ranker-v1",
                dataStatus: .ready,
                personalizationMode: .contextOnly,
                intent: .cafe,
                eligibleCandidateCount: 3,
                generatedAt: "2026-09-01T12:00:00+09:00",
                items: [
                    RecommendationPlace(
                        id: "seoul-book-cafe",
                        provider: .kakaoLocal,
                        name: "서울책방 카페",
                        category: "카페",
                        address: "서울특별시 중구 세종대로 110",
                        latitude: 37.5669,
                        longitude: 126.9784,
                        distanceMeters: 180,
                        score: 0.96,
                        reasonCodes: [.cafeIntentMatch, .withinOneKilometer],
                        sourceUrl: "https://place.map.kakao.com/1"
                    ),
                    RecommendationPlace(
                        id: "deoksugung-coffee",
                        provider: .kakaoLocal,
                        name: "덕수궁 돌담길 커피",
                        category: "카페",
                        address: "서울특별시 중구 덕수궁길 15",
                        latitude: 37.5659,
                        longitude: 126.9752,
                        distanceMeters: 420,
                        score: 0.91,
                        reasonCodes: [.cafeIntentMatch, .trustedProvider],
                        sourceUrl: "https://place.map.kakao.com/2"
                    ),
                    RecommendationPlace(
                        id: "cityhall-roastery",
                        provider: .kakaoLocal,
                        name: "시청 로스터리",
                        category: "커피전문점",
                        address: "서울특별시 중구 서소문로 120",
                        latitude: 37.5638,
                        longitude: 126.9768,
                        distanceMeters: 650,
                        score: 0.87,
                        reasonCodes: [.cafeIntentMatch, .diverseCategory],
                        sourceUrl: "https://place.map.kakao.com/3"
                    )
                ]
            )
        }
        #endif
    }

    func bootstrap(initialBounds: MapBounds = .centralSeoul) async {
        await loadZones(in: initialBounds)
        await validateAppleCredentialStateIfNeeded()
        currentUser = await apiClient.getCurrentUser()
        if currentUser != nil {
            await loadUserZones()
        }
    }

    func loadInitialZones() async {
        await loadZones(in: .centralSeoul)
    }

    func setLocationErrorMessage(_ message: String?) {
        locationErrorMessage = message
    }

    func dismissLatestSeason3SearchResult() {
        presentsLatestSeason3SearchResult = false
    }

    var visibleRecommendations: [RecommendationPlace] {
        (recommendationResponse?.items ?? []).filter { !hiddenRecommendationKeys.contains($0.stableKey) }
    }

    func isRecommendationSaved(_ place: RecommendationPlace) -> Bool {
        savedRecommendationKeys.contains(place.stableKey)
    }

    func loadRecommendations(
        intent: RecommendationIntent,
        center: CLLocationCoordinate2D,
        radiusMeters: Int,
        force: Bool = false
    ) async {
        let normalizedRadius = min(max(radiusMeters, 100), 20_000)
        let requestKey = String(
            format: "%@:%0.5f:%0.5f:%d",
            intent.rawValue,
            center.latitude,
            center.longitude,
            normalizedRadius
        )
        if !force {
            if inFlightRecommendationKey == requestKey {
                return
            }
            if lastLoadedRecommendationKey == requestKey, recommendationResponse != nil {
                return
            }
        }

        recommendationRequestSequence += 1
        let requestID = recommendationRequestSequence
        inFlightRecommendationKey = requestKey
        isLoadingRecommendations = true
        recommendationErrorMessage = nil
        defer {
            if requestID == recommendationRequestSequence {
                if inFlightRecommendationKey == requestKey {
                    inFlightRecommendationKey = nil
                }
                isLoadingRecommendations = false
            }
        }

        do {
            let response = try await apiClient.fetchRecommendedPlaces(
                intent: intent,
                latitude: center.latitude,
                longitude: center.longitude,
                radiusMeters: normalizedRadius,
                limit: 8,
                accessToken: currentUser == nil ? nil : AuthTokenStore.loadAccessToken()
            )
            guard requestID == recommendationRequestSequence else {
                return
            }
            recommendationResponse = response
            lastLoadedRecommendationKey = requestKey
            recommendationErrorMessage = nil
        } catch {
            guard requestID == recommendationRequestSequence else {
                return
            }
            recommendationErrorMessage = error.localizedDescription
        }
    }

    func loadRecommendationAccountState() async {
        guard currentUser != nil, AuthTokenStore.loadAccessToken() != nil else {
            recommendationPreferences = nil
            savedRecommendationKeys = []
            return
        }

        async let preferencesTask = try? apiClient.fetchRecommendationPreferences()
        async let savedItemsTask = try? apiClient.fetchSavedRecommendationItems()
        let (preferences, savedItems) = await (preferencesTask, savedItemsTask)
        recommendationPreferences = preferences
        if let savedItems {
            savedRecommendationKeys = Set(savedItems.map(\.stableKey))
        }
    }

    @discardableResult
    func setRecommendationPersonalization(enabled: Bool) async -> Bool {
        guard currentUser != nil, AuthTokenStore.loadAccessToken() != nil else {
            return false
        }

        recommendationErrorMessage = nil
        do {
            let current: RecommendationPreferences
            if let recommendationPreferences {
                current = recommendationPreferences
            } else {
                current = try await apiClient.fetchRecommendationPreferences()
            }
            recommendationPreferences = try await apiClient.updateRecommendationPreferences(
                RecommendationPreferenceUpdate(
                    personalizationEnabled: enabled,
                    preferredTags: current.preferredTags,
                    dislikedTags: current.dislikedTags,
                    budgetLevel: current.budgetLevel,
                    maxDistanceMeters: current.maxDistanceMeters
                )
            )
            return true
        } catch {
            recommendationErrorMessage = error.localizedDescription
            return false
        }
    }

    @discardableResult
    func toggleSavedRecommendation(_ place: RecommendationPlace) async -> Bool {
        guard currentUser != nil, AuthTokenStore.loadAccessToken() != nil else {
            return false
        }

        let wasSaved = isRecommendationSaved(place)
        recommendationErrorMessage = nil
        do {
            if wasSaved {
                try await apiClient.deleteSavedRecommendationItem(
                    provider: place.provider.rawValue,
                    itemID: place.id
                )
                savedRecommendationKeys.remove(place.stableKey)
                await recordRecommendationInteraction(.unsave, for: place)
            } else {
                _ = try await apiClient.saveRecommendationItem(
                    RecommendationSavedItemRequest(
                        itemType: .place,
                        provider: place.provider.rawValue,
                        itemId: place.id,
                        title: place.name,
                        category: place.category.isEmpty ? nil : place.category,
                        lat: place.latitude,
                        lng: place.longitude,
                        sourceUrl: place.sourceUrl.isEmpty ? nil : place.sourceUrl
                    )
                )
                savedRecommendationKeys.insert(place.stableKey)
                await recordRecommendationInteraction(.save, for: place)
            }
            return true
        } catch {
            recommendationErrorMessage = error.localizedDescription
            return false
        }
    }

    func markRecommendationNotInterested(_ place: RecommendationPlace) async {
        hiddenRecommendationKeys.insert(place.stableKey)
        await recordRecommendationInteraction(.notInterested, for: place)
    }

    func recordRecommendationInteraction(
        _ eventType: RecommendationEventType,
        for place: RecommendationPlace
    ) async {
        guard currentUser != nil,
              let response = recommendationResponse,
              recommendationPreferences?.personalizationEnabled == true,
              response.personalizationMode.allowsInteractionLogging,
              response.items.contains(where: { $0.stableKey == place.stableKey }) else {
            return
        }

        _ = try? await apiClient.recordRecommendationInteraction(
            RecommendationInteractionRequest(
                requestId: response.requestId,
                itemType: .place,
                provider: place.provider.rawValue,
                itemId: place.id,
                eventType: eventType,
                intent: response.intent.rawValue,
                contextRegion: nil,
                modelVersion: response.modelVersion,
                occurredAt: nil
            )
        )
    }

    func loadZones(around latitude: Double, longitude: Double) async {
        let bounds = MapBounds.around(latitude: latitude, longitude: longitude)
        await loadZones(in: bounds)
    }

    func loadZones(in bounds: MapBounds, force: Bool = false) async {
        let boundsKey = zoneBoundsKey(bounds)
        if !force {
            if inFlightZoneBoundsKey == boundsKey {
                return
            }

            if lastLoadedZoneBoundsKey == boundsKey, !zones.isEmpty {
                return
            }
        }

        zoneRequestSequence += 1
        let requestID = zoneRequestSequence
        inFlightZoneBoundsKey = boundsKey
        isLoading = true
        errorMessage = nil

        do {
            let fetchedZones = try await apiClient.fetchZonesByBounds(bounds)
            guard requestID == zoneRequestSequence else {
                if inFlightZoneBoundsKey == boundsKey {
                    inFlightZoneBoundsKey = nil
                }
                return
            }
            zones = fetchedZones
            lastLoadedZoneBoundsKey = boundsKey
        } catch {
            guard requestID == zoneRequestSequence else {
                if inFlightZoneBoundsKey == boundsKey {
                    inFlightZoneBoundsKey = nil
                }
                return
            }
            errorMessage = error.localizedDescription
        }

        if inFlightZoneBoundsKey == boundsKey {
            inFlightZoneBoundsKey = nil
        }
        isLoading = false
    }

    func loadInsights(
        in bounds: MapBounds,
        force: Bool = false,
        keyword: String? = nil,
        lane: InsightLoadLane = .all
    ) async {
        guard activeSeason3SearchRequestID == nil else {
            return
        }

        let normalizedKeyword = keyword?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let boundsKey = "\(zoneBoundsKey(bounds)):\(lane.rawValue):\(normalizedKeyword.lowercased())"
        if !force {
            if inFlightInsightBoundsKey == boundsKey {
                return
            }

            if lastLoadedInsightBoundsKey == boundsKey,
               (!hotplaceInsight.places.isEmpty || !eventInsight.events.isEmpty) {
                return
            }
        }

        insightRequestSequence += 1
        let requestID = insightRequestSequence
        inFlightInsightBoundsKey = boundsKey
        isLoadingInsights = true
        insightErrorMessage = nil

        do {
            let fetchedInsight = try await apiClient.fetchMapInsights(
                keyword: normalizedKeyword.isEmpty ? nil : normalizedKeyword,
                hotplaceLimit: lane == .events ? 1 : 8,
                eventLimit: lane == .hotplaces ? 1 : 12,
                bounds: bounds
            )
            guard requestID == insightRequestSequence else {
                if inFlightInsightBoundsKey == boundsKey {
                    inFlightInsightBoundsKey = nil
                }
                return
            }
            applyInsight(fetchedInsight, boundsKey: boundsKey, lane: lane)
        } catch {
            guard requestID == insightRequestSequence else {
                if inFlightInsightBoundsKey == boundsKey {
                    inFlightInsightBoundsKey = nil
                }
                return
            }
            insightErrorMessage = error.localizedDescription
        }

        if inFlightInsightBoundsKey == boundsKey {
            inFlightInsightBoundsKey = nil
        }
        isLoadingInsights = false
    }

    @discardableResult
    func search() async -> CLLocationCoordinate2D? {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            await loadInitialZones()
            return nil
        }

        zoneRequestSequence += 1
        let requestID = zoneRequestSequence
        isLoading = true
        errorMessage = nil
        defer {
            if requestID == zoneRequestSequence {
                isLoading = false
            }
        }

        if let placeCoordinate = await searchMapPlace(keyword: trimmedQuery) {
            do {
                let bounds = MapBounds.around(latitude: placeCoordinate.latitude, longitude: placeCoordinate.longitude)
                let fetchedZones = try await apiClient.fetchZonesByBounds(bounds)
                guard requestID == zoneRequestSequence else {
                    return nil
                }
                zones = fetchedZones
                return placeCoordinate
            } catch {
                guard requestID == zoneRequestSequence else {
                    return nil
                }
                errorMessage = error.localizedDescription
            }
        }

        do {
            let fetchedZones = try await apiClient.searchZones(keyword: trimmedQuery)
            guard requestID == zoneRequestSequence else {
                return nil
            }
            zones = fetchedZones
            return zones.first?.coordinate
        } catch {
            guard requestID == zoneRequestSequence else {
                return nil
            }
            errorMessage = error.localizedDescription
        }

        return nil
    }

    func resetSearch() async {
        query = ""
        await loadInitialZones()
    }

    private func zoneBoundsKey(_ bounds: MapBounds) -> String {
        String(
            format: "%.5f:%.5f:%.5f:%.5f",
            bounds.minLat,
            bounds.maxLat,
            bounds.minLng,
            bounds.maxLng
        )
    }

    private func applyInsight(
        _ insight: MapInsight,
        boundsKey: String? = nil,
        lane: InsightLoadLane = .all
    ) {
        if lane != .events {
            hotplaceInsight = insight.hotplaces
        }
        if lane != .hotplaces {
            eventInsight = insight.events
        }
        insightStatus = insight.status
        insightErrorMessage = nil
        lastInsightRefreshAt = Date()

        let hasLaneResults = switch lane {
        case .all:
            !insight.hotplaces.places.isEmpty || !insight.events.events.isEmpty
        case .hotplaces:
            !insight.hotplaces.places.isEmpty
        case .events:
            !insight.events.events.isEmpty
        }
        if let boundsKey, hasLaneResults {
            lastLoadedInsightBoundsKey = boundsKey
        }
    }

    func submitZone(
        latitude: Double,
        longitude: Double,
        region: String,
        subtype: String,
        address: String,
        note: String,
        image: ZoneImageAttachment? = nil
    ) async -> Bool {
        guard !isSubmittingZone else {
            return false
        }

        isSubmittingZone = true
        errorMessage = nil

        let normalizedType: String
        switch subtype {
        case "개방":
            normalizedType = "OPEN"
        case "실내":
            normalizedType = "INDOOR"
        default:
            normalizedType = "BOOTH"
        }

        let normalizedAddress = address.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedRegion = region.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "서울특별시"
            : region.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload = CreateZonePayload(
            region: normalizedRegion,
            type: normalizedType,
            subtype: subtype,
            description: note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "\(normalizedAddress)에 위치한 \(subtype)형 흡연구역"
                : note.trimmingCharacters(in: .whitespacesAndNewlines),
            latitude: latitude,
            longitude: longitude,
            address: normalizedAddress,
            user: currentUser?.displayName ?? "익명사용자"
        )

        guard AuthTokenStore.loadAccessToken() != nil else {
            errorMessage = "제보하려면 로그인이 필요합니다."
            isSubmittingZone = false
            return false
        }

        let now = Date().timeIntervalSince1970
        let lastSubmit = UserDefaults.standard.double(forKey: lastSubmitStorageKey)
        if lastSubmit > 0, now - lastSubmit < submissionCooldownSeconds {
            errorMessage = "30초 후에 다시 등록할 수 있습니다."
            isSubmittingZone = false
            return false
        }

        do {
            let zone = try await apiClient.createZone(payload, image: image)
            UserDefaults.standard.set(now, forKey: lastSubmitStorageKey)
            zones.append(zone)
            userZones.append(zone)
            selectedZone = zone
            isSubmittingZone = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isSubmittingZone = false
            return false
        }
    }

    func select(_ zone: SmokingZone) {
        selectedZone = zone
    }

    func reviews(for zone: SmokingZone) -> [ZoneReview] {
        reviewsByZoneID[zone.id] ?? []
    }

    func setLocationLettersPresented(_ isPresented: Bool) {
        isPresentingLocationLetters = isPresented
    }

    func loadLocationLetters(at coordinate: CLLocationCoordinate2D) async {
        locationLetterRequestSequence += 1
        let requestID = locationLetterRequestSequence
        isLoadingLocationLetters = true
        locationLetterErrorMessage = nil
        locationLetterMessage = nil

        do {
            let response = try await apiClient.fetchLocationLetters(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                accessToken: currentUser == nil ? nil : AuthTokenStore.loadAccessToken()
            )
            guard requestID == locationLetterRequestSequence else {
                return
            }
            locationLetters = response
            isLoadingLocationLetters = false
        } catch {
            guard requestID == locationLetterRequestSequence else {
                return
            }
            locationLetters = .empty
            isLoadingLocationLetters = false
            locationLetterErrorMessage = "이곳의 편지를 불러오지 못했습니다."
        }
    }

    func submitLocationLetter(
        at coordinate: CLLocationCoordinate2D,
        content: String
    ) async -> Bool {
        let normalized = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else {
            locationLetterErrorMessage = "남기고 싶은 문장을 적어주세요."
            return false
        }
        guard AuthTokenStore.loadAccessToken() != nil else {
            locationLetterErrorMessage = "편지를 남기려면 로그인이 필요합니다."
            return false
        }

        isSubmittingLocationLetter = true
        locationLetterErrorMessage = nil
        locationLetterMessage = nil
        do {
            let letter = try await apiClient.createLocationLetter(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                content: normalized
            )
            locationLetters = LocationLetterNearby(
                letters: [letter] + locationLetters.letters.filter { $0.id != letter.id },
                count: locationLetters.count + 1,
                radiusMeters: locationLetters.radiusMeters
            )
            isSubmittingLocationLetter = false
            locationLetterMessage = "이 자리에 문장을 남겼습니다."
            return true
        } catch {
            isSubmittingLocationLetter = false
            locationLetterErrorMessage = error.localizedDescription
            return false
        }
    }

    func deleteLocationLetter(_ letter: LocationLetter) async -> Bool {
        guard letter.mine else {
            locationLetterErrorMessage = "내가 남긴 편지만 지울 수 있습니다."
            return false
        }
        do {
            try await apiClient.deleteLocationLetter(letterID: letter.id)
            locationLetters = LocationLetterNearby(
                letters: locationLetters.letters.filter { $0.id != letter.id },
                count: max(locationLetters.count - 1, 0),
                radiusMeters: locationLetters.radiusMeters
            )
            locationLetterMessage = "편지를 거두었습니다."
            return true
        } catch {
            locationLetterErrorMessage = error.localizedDescription
            return false
        }
    }

    func toggleLocationLetterLike(_ letter: LocationLetter) async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            locationLetterErrorMessage = "좋아요를 누르려면 로그인이 필요합니다."
            return false
        }
        guard !isUpdatingLocationLetterLike else {
            return false
        }

        isUpdatingLocationLetterLike = true
        locationLetterErrorMessage = nil
        do {
            let state = try await apiClient.setLocationLetterLiked(
                letterID: letter.id,
                liked: !letter.likedByMe
            )
            locationLetters = LocationLetterNearby(
                letters: locationLetters.letters.map { existing in
                    guard existing.id == state.letterId else {
                        return existing
                    }
                    return existing.updatingLike(
                        count: state.likeCount,
                        likedByMe: state.likedByMe
                    )
                },
                count: locationLetters.count,
                radiusMeters: locationLetters.radiusMeters
            )
            isUpdatingLocationLetterLike = false
            return true
        } catch {
            isUpdatingLocationLetterLike = false
            locationLetterErrorMessage = error.localizedDescription
            return false
        }
    }

    func reportLocationLetter(_ letter: LocationLetter, reason: String) async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            locationLetterErrorMessage = "신고하려면 로그인이 필요합니다."
            return false
        }
        do {
            try await apiClient.reportLocationLetter(letterID: letter.id, reason: reason)
            locationLetterMessage = "신고가 접수되었습니다."
            return true
        } catch {
            locationLetterErrorMessage = error.localizedDescription
            return false
        }
    }

    func blockLocationLetterAuthor(_ letter: LocationLetter) async -> Bool {
        guard let authorID = letter.authorId else {
            locationLetterErrorMessage = "차단할 사용자를 확인할 수 없습니다."
            return false
        }
        guard currentUser?.id != authorID else {
            locationLetterErrorMessage = "내 계정은 차단할 수 없습니다."
            return false
        }
        guard AuthTokenStore.loadAccessToken() != nil else {
            locationLetterErrorMessage = "차단하려면 로그인이 필요합니다."
            return false
        }
        do {
            try await apiClient.blockUser(authorID: authorID)
            let filtered = locationLetters.letters.filter { $0.authorId != authorID }
            locationLetters = LocationLetterNearby(
                letters: filtered,
                count: filtered.count,
                radiusMeters: locationLetters.radiusMeters
            )
            locationLetterMessage = "이 사용자의 편지를 숨겼습니다."
            return true
        } catch {
            locationLetterErrorMessage = error.localizedDescription
            return false
        }
    }

    func loadReviews(for zone: SmokingZone) async {
        if reviewsByZoneID[zone.id] != nil || loadingReviewZoneID == zone.id {
            return
        }

        loadingReviewZoneID = zone.id
        reviewLoadErrorByZoneID[zone.id] = nil

        do {
            let accessToken = currentUser == nil ? nil : AuthTokenStore.loadAccessToken()
            reviewsByZoneID[zone.id] = try await apiClient.fetchReviews(
                zoneID: zone.id,
                accessToken: accessToken
            )
        } catch {
            reviewLoadErrorByZoneID[zone.id] = "리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
        }

        loadingReviewZoneID = nil
    }

    func submitReview(for zone: SmokingZone, content: String) async -> Bool {
        let trimmedContent = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedContent.isEmpty else {
            errorMessage = "리뷰 내용을 입력해주세요."
            return false
        }

        guard AuthTokenStore.loadAccessToken() != nil else {
            errorMessage = "리뷰를 작성하려면 로그인이 필요합니다."
            return false
        }

        do {
            let review = try await apiClient.createReview(zoneID: zone.id, content: trimmedContent)
            var reviews = reviewsByZoneID[zone.id] ?? []
            reviews.insert(review, at: 0)
            reviewsByZoneID[zone.id] = reviews
            errorMessage = nil
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func reportReview(_ review: ZoneReview, in zone: SmokingZone, reason: String) async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            reviewModerationMessage = "리뷰를 신고하려면 로그인이 필요합니다."
            return false
        }

        do {
            try await apiClient.reportReview(
                zoneID: zone.id,
                reviewID: review.id,
                reason: reason
            )
            reviewModerationMessage = "신고가 접수되었습니다."
            return true
        } catch {
            reviewModerationMessage = error.localizedDescription
            return false
        }
    }

    func reportZone(_ zone: SmokingZone, reason: String, details: String? = nil) async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            zoneModerationMessage = "장소를 신고하려면 로그인이 필요합니다."
            return false
        }

        do {
            try await apiClient.reportZone(zoneID: zone.id, reason: reason, details: details)
            zoneModerationMessage = "장소 신고가 접수되었습니다."
            return true
        } catch {
            zoneModerationMessage = error.localizedDescription
            return false
        }
    }

    func blockReviewAuthor(_ review: ZoneReview) async -> Bool {
        guard let authorID = review.authorId else {
            reviewModerationMessage = "차단할 사용자를 확인할 수 없습니다."
            return false
        }
        guard currentUser?.id != authorID else {
            reviewModerationMessage = "본인 계정은 차단할 수 없습니다."
            return false
        }
        guard AuthTokenStore.loadAccessToken() != nil else {
            reviewModerationMessage = "사용자를 차단하려면 로그인이 필요합니다."
            return false
        }

        do {
            try await apiClient.blockUser(authorID: authorID)
            for zoneID in Array(reviewsByZoneID.keys) {
                reviewsByZoneID[zoneID]?.removeAll { $0.authorId == authorID }
            }
            reviewModerationMessage = "사용자를 차단했습니다. 해당 사용자의 리뷰는 더 이상 표시되지 않습니다."
            return true
        } catch {
            reviewModerationMessage = error.localizedDescription
            return false
        }
    }

    func imageURL(for zone: SmokingZone) -> URL? {
        apiClient.imageURL(for: zone.preferredImagePath)
    }

    func imageURL(for user: UserProfile?) -> URL? {
        apiClient.imageURL(for: user?.preferredProfileImagePath)
    }

    private func searchMapPlace(keyword: String) async -> CLLocationCoordinate2D? {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = keyword
        request.region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780),
            span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
        )

        do {
            let response = try await MKLocalSearch(request: request).start()
            guard let item = response.mapItems.first else {
                return nil
            }
            #if os(macOS)
            if #available(macOS 26.0, *) {
                return item.location.coordinate
            }
            return nil
            #else
            if #available(iOS 26.0, *) {
                return item.location.coordinate
            }
            return item.placemark.coordinate
            #endif
        } catch {
            return nil
        }
    }

    func signIn(with provider: OAuthProvider) async -> Bool {
        isAuthLoading = true
        errorMessage = nil

        do {
            let result = try await oauthSession.signIn(with: provider, apiClient: apiClient)
            try AuthTokenStore.replaceLoginSession(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken,
                appleUserIdentifier: nil
            )
            pendingSignupEmail = result.profileComplete ? nil : result.email
            currentUser = await apiClient.getCurrentUser(accessToken: result.accessToken)

            if currentUser != nil {
                await loadUserZones()
            }

            isAuthLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isAuthLoading = false
            return false
        }
    }

    func signInWithApple(authorization: ASAuthorization) async -> Bool {
        isAuthLoading = true
        errorMessage = nil

        do {
            let result = try await appleSignInSession.signIn(authorization: authorization, apiClient: apiClient)
            try AuthTokenStore.replaceLoginSession(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken,
                appleUserIdentifier: result.appleUserIdentifier
            )
            pendingSignupEmail = result.profileComplete ? nil : result.email
            currentUser = await apiClient.getCurrentUser(accessToken: result.accessToken)

            if currentUser != nil {
                await loadUserZones()
            }

            isAuthLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isAuthLoading = false
            return false
        }
    }

    func resumeOAuthSignIn(from callbackURL: URL) async {
        guard AppConfig.isOAuthCallbackURL(callbackURL), !oauthSession.isAwaitingCallback else {
            return
        }

        isAuthLoading = true
        errorMessage = nil
        defer { isAuthLoading = false }

        do {
            guard let result = try await oauthSession.resumeSignIn(from: callbackURL, apiClient: apiClient) else {
                return
            }
            try AuthTokenStore.replaceLoginSession(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken,
                appleUserIdentifier: nil
            )
            pendingSignupEmail = result.profileComplete ? nil : result.email
            currentUser = await apiClient.getCurrentUser(accessToken: result.accessToken)
            if currentUser != nil {
                await loadUserZones()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func handleAppleSignInFailure(_ error: Error) {
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            return
        }
        errorMessage = "Apple 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."
    }

    func completeProfileSetup(nickname: String, image: ZoneImageAttachment? = nil) async -> Bool {
        let trimmedNickname = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmedNickname.count >= 2 else {
            errorMessage = "닉네임은 2자 이상이어야 합니다."
            return false
        }

        isAuthLoading = true
        errorMessage = nil

        do {
            currentUser = try await apiClient.completeProfileSetup(nickname: trimmedNickname, image: image)
            pendingSignupEmail = nil
            await loadUserZones()
            isAuthLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isAuthLoading = false
            return false
        }
    }

    func logout() {
        AuthTokenStore.deleteTokens()
        currentUser = nil
        userZones = []
        recommendationPreferences = nil
        savedRecommendationKeys = []
        userZonesErrorMessage = nil
        pendingSignupEmail = nil
        accountDeletionErrorMessage = nil
    }

    func handleAppleCredentialRevoked() {
        AuthTokenStore.deleteTokens()
        currentUser = nil
        userZones = []
        recommendationPreferences = nil
        savedRecommendationKeys = []
        userZonesErrorMessage = nil
        pendingSignupEmail = nil
        accountDeletionErrorMessage = nil
        errorMessage = "Apple 로그인 연결이 해제되어 로그아웃했습니다."
    }

    func validateAppleCredentialStateIfNeeded() async {
        guard let userIdentifier = AuthTokenStore.loadAppleUserIdentifier() else {
            return
        }
        guard let state = await appleSignInSession.credentialState(for: userIdentifier) else {
            return
        }
        if AppleCredentialSessionPolicy.requiresLocalSignOut(for: state) {
            handleAppleCredentialRevoked()
        }
    }

    func reportMapFailure() {
        mapErrorMessage = "지도를 불러오지 못했습니다. 네트워크와 지도 설정을 확인해주세요."
    }

    func clearMapFailure() {
        mapErrorMessage = nil
    }

    func deleteAccount() async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            accountDeletionErrorMessage = "계정을 삭제하려면 로그인이 필요합니다."
            return false
        }

        isDeletingAccount = true
        accountDeletionNotice = nil
        accountDeletionErrorMessage = nil

        do {
            let result = try await apiClient.deleteCurrentUser()
            AuthTokenStore.deleteTokens()
            currentUser = nil
            userZones = []
            recommendationPreferences = nil
            savedRecommendationKeys = []
            pendingSignupEmail = nil
            accountDeletionErrorMessage = nil
            if result.manualAppleRevocationRequired {
                accountDeletionNotice = result.message?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
                    ? result.message
                    : "iPhone 설정에서 Apple 계정의 'Apple로 로그인' 목록을 열고 너굴맵 연결을 해제해주세요."
            }
            isDeletingAccount = false
            return true
        } catch {
            accountDeletionErrorMessage = error.localizedDescription
            isDeletingAccount = false
            return false
        }
    }

    func clearAccountDeletionError() {
        accountDeletionErrorMessage = nil
    }

    func loadUserZones() async {
        guard AuthTokenStore.loadAccessToken() != nil else {
            userZones = []
            return
        }

        isLoadingUserZones = true
        defer {
            isLoadingUserZones = false
        }

        do {
            userZonesErrorMessage = nil
            userZones = try await apiClient.fetchUserZones()
        } catch {
            userZonesErrorMessage = "내가 등록한 장소를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
        }
    }

    func deleteUserZone(_ zone: SmokingZone) async -> Bool {
        guard AuthTokenStore.loadAccessToken() != nil else {
            errorMessage = "삭제하려면 로그인이 필요합니다."
            return false
        }

        do {
            try await apiClient.deleteZone(id: zone.id)
            zones.removeAll { $0.id == zone.id }
            userZones.removeAll { $0.id == zone.id }
            if selectedZone?.id == zone.id {
                selectedZone = nil
            }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
