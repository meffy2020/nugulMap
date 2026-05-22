import Combine
import Foundation
import MapKit

@MainActor
final class ZoneExplorerModel: ObservableObject {
    @Published var zones: [SmokingZone]
    @Published private(set) var isLoading = false
    @Published private(set) var isSubmittingZone = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var reviewsByZoneID: [Int: [ZoneReview]] = [:]
    @Published private(set) var loadingReviewZoneID: Int?
    @Published private(set) var currentUser: UserProfile?
    @Published private(set) var userZones: [SmokingZone] = []
    @Published private(set) var isAuthLoading = false
    @Published private(set) var isLoadingUserZones = false
    @Published var pendingSignupEmail: String?
    @Published var query = ""
    @Published var selectedZone: SmokingZone?

    private let apiClient: NugulAPIClient
    private let oauthSession = OAuthWebSession()
    private let lastSubmitStorageKey = "nugul_last_submit"
    private let submissionCooldownSeconds: TimeInterval = 30
    private var zoneRequestSequence = 0
    private var inFlightZoneBoundsKey: String?
    private var lastLoadedZoneBoundsKey: String?

    init(apiClient: NugulAPIClient = NugulAPIClient()) {
        self.apiClient = apiClient
        self.zones = []
    }

    func bootstrap(initialBounds: MapBounds = .centralSeoul) async {
        await loadZones(in: initialBounds)
        currentUser = await apiClient.getCurrentUser()
        if currentUser != nil {
            await loadUserZones()
        }
    }

    func loadInitialZones() async {
        await loadZones(in: .centralSeoul)
    }

    func loadZones(around latitude: Double, longitude: Double) async {
        await loadZones(in: .around(latitude: latitude, longitude: longitude))
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

        if let placeCoordinate = await searchMapPlace(keyword: trimmedQuery) {
            do {
                let bounds = MapBounds.around(latitude: placeCoordinate.latitude, longitude: placeCoordinate.longitude)
                let fetchedZones = try await apiClient.fetchZonesByBounds(bounds)
                guard requestID == zoneRequestSequence else {
                    return nil
                }
                zones = fetchedZones
                isLoading = false
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
            isLoading = false
            return zones.first?.coordinate
        } catch {
            guard requestID == zoneRequestSequence else {
                return nil
            }
            zones = []
            errorMessage = error.localizedDescription
        }

        isLoading = false
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

    func loadReviews(for zone: SmokingZone) async {
        if reviewsByZoneID[zone.id] != nil || loadingReviewZoneID == zone.id {
            return
        }

        loadingReviewZoneID = zone.id

        do {
            reviewsByZoneID[zone.id] = try await apiClient.fetchReviews(zoneID: zone.id)
        } catch {
            reviewsByZoneID[zone.id] = []
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
            return response.mapItems.first?.placemark.coordinate
        } catch {
            return nil
        }
    }

    func signIn(with provider: OAuthProvider) async -> Bool {
        isAuthLoading = true
        errorMessage = nil

        do {
            let result = try await oauthSession.signIn(with: provider, apiClient: apiClient)
            AuthTokenStore.saveTokens(accessToken: result.accessToken, refreshToken: result.refreshToken)
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
        pendingSignupEmail = nil
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
            userZones = try await apiClient.fetchUserZones()
        } catch {
            userZones = []
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
