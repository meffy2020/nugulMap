import CoreLocation
import Foundation
import Darwin
import MapKit

struct MapBounds: Equatable {
    let minLat: Double
    let maxLat: Double
    let minLng: Double
    let maxLng: Double

    static let centralSeoul = MapBounds(
        minLat: 37.5165,
        maxLat: 37.6165,
        minLng: 126.9280,
        maxLng: 127.0280
    )

    static let seoulCity = MapBounds(
        minLat: 37.4130,
        maxLat: 37.7150,
        minLng: 126.7340,
        maxLng: 127.2690
    )

    static let seongsu = MapBounds(
        minLat: 37.532,
        maxLat: 37.558,
        minLng: 127.032,
        maxLng: 127.072
    )

    static func around(latitude: Double, longitude: Double, latitudeDelta: Double = 0.08, longitudeDelta: Double = 0.08) -> MapBounds {
        MapBounds(
            minLat: latitude - latitudeDelta / 2,
            maxLat: latitude + latitudeDelta / 2,
            minLng: longitude - longitudeDelta / 2,
            maxLng: longitude + longitudeDelta / 2
        )
    }

    static func visibleRegion(_ region: MKCoordinateRegion, paddingRatio: Double = 0.12) -> MapBounds {
        let latitudePadding = max(region.span.latitudeDelta * paddingRatio, 0.01)
        let longitudePadding = max(region.span.longitudeDelta * paddingRatio, 0.01)

        return MapBounds(
            minLat: region.center.latitude - region.span.latitudeDelta / 2 - latitudePadding,
            maxLat: region.center.latitude + region.span.latitudeDelta / 2 + latitudePadding,
            minLng: region.center.longitude - region.span.longitudeDelta / 2 - longitudePadding,
            maxLng: region.center.longitude + region.span.longitudeDelta / 2 + longitudePadding
        )
    }
}

enum Season3SearchIntent: Equatable {
    case crowd(keyword: String)
    case event(keyword: String?)

    init?(query: String) {
        let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalized.isEmpty else {
            return nil
        }

        if normalized == "hot-now" {
            self = .crowd(keyword: "hot-now")
            return
        }

        if Self.eventTriggerTerms.contains(where: normalized.contains) {
            self = .event(keyword: Self.locationKeyword(from: normalized, removing: Self.eventTerms))
            return
        }

        if Self.crowdTriggerTerms.contains(where: normalized.contains) {
            self = .crowd(
                keyword: Self.locationKeyword(from: normalized, removing: Self.crowdTerms) ?? "hot-now"
            )
            return
        }

        return nil
    }

    var keyword: String? {
        switch self {
        case let .crowd(keyword):
            return keyword
        case let .event(keyword):
            return keyword
        }
    }

    static func allowsNonPopupEvents(query: String) -> Bool {
        let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return broadEventTriggerTerms.contains(where: normalized.contains)
    }

    func preferredCoordinate(
        in insight: MapInsight,
        allowingNonPopupEvents: Bool
    ) -> CLLocationCoordinate2D? {
        switch self {
        case .crowd:
            return insight.hotplaces.places.first.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
        case .event:
            return insight.events.events
                .first(where: {
                    $0.isEligibleForSeason3EventSearch(
                        allowsNonPopupEvents: allowingNonPopupEvents
                    )
                })
                .map {
                    CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
                }
        }
    }

    private static let crowdTerms = [
        "사람 많은", "사람 많", "붐비는지", "붐비나요", "붐비는", "붐벼", "붐빔", "붐비",
        "혼잡도", "혼잡", "핫한", "핫플", "실시간으로", "실시간", "확인", "현재", "지금",
        "어디가", "어디", "알려줘", "보여줘"
    ]

    private static let eventTerms = [
        "팝업스토어", "언제부터", "언제까지", "팝업", "행사", "축제", "공연", "기간", "언제",
        "어디서", "어디", "열려", "진행", "알려줘", "보여줘"
    ]

    private static let crowdTriggerTerms = [
        "사람 많은", "사람 많", "붐비는지", "붐비나요", "붐비는", "붐벼", "붐빔", "붐비",
        "혼잡도", "혼잡", "핫한", "핫플"
    ]

    private static let eventTriggerTerms = ["팝업스토어", "팝업", "행사", "축제", "공연"]
    private static let broadEventTriggerTerms = ["행사", "축제", "공연"]

    private static func locationKeyword(from query: String, removing terms: [String]) -> String? {
        var value = query
        for term in terms.sorted(by: { $0.count > $1.count }) {
            value = value.replacingOccurrences(of: term, with: " ")
        }
        value = value.components(separatedBy: .punctuationCharacters).joined(separator: " ")
        value = value.components(separatedBy: .symbols).joined(separator: " ")
        let compact = value.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        return compact.isEmpty ? nil : compact
    }
}

struct HotplaceInsight: Decodable, Equatable {
    let places: [Hotplace]
    let dataFreshness: String?
    let updatedAt: String?
    let sources: [String]?

    static let empty = HotplaceInsight(places: [], dataFreshness: nil, updatedAt: nil, sources: [])
}

struct Hotplace: Decodable, Equatable, Identifiable {
    let id: String
    let name: String
    let category: String?
    let crowdLevel: String?
    let crowdMessage: String?
    let estimatedMinPeople: Int?
    let estimatedMaxPeople: Int?
    let latitude: Double
    let longitude: Double
    let address: String?
    let source: String?
    let sourcePlaceCode: String?
    let updatedAt: String?
    let freshnessStatus: String?
    let ageSeconds: Int?

    var crowdLabel: String {
        if let crowdLevel, !crowdLevel.isEmpty, crowdLevel != "UNKNOWN" {
            if let estimatedMinPeople, let estimatedMaxPeople {
                return "\(crowdLevel) · \(Self.peopleFormatter.string(from: NSNumber(value: estimatedMinPeople)) ?? "\(estimatedMinPeople)")-\(Self.peopleFormatter.string(from: NSNumber(value: estimatedMaxPeople)) ?? "\(estimatedMaxPeople)")명"
            }
            return crowdLevel
        }

        return category == "popup" ? "팝업 후보" : "핫플 후보"
    }

    var compactMapLabel: String {
        guard
            let estimatedMinPeople,
            let estimatedMaxPeople,
            estimatedMinPeople > 0,
            estimatedMaxPeople > 0
        else {
            return String(name.prefix(12))
        }

        return "\(String(name.prefix(8))) \(Self.compactPeopleRange(min: estimatedMinPeople, max: estimatedMaxPeople))"
    }

    var sourceLabel: String {
        switch source {
        case "TELECOM_CROWD":
            return "통신사 장소 혼잡도"
        case "SEOUL_CITYDATA":
            return "서울 실시간 도시데이터"
        default:
            return "핫플 후보"
        }
    }

    var freshnessLabel: String? {
        let status: String
        switch freshnessStatus {
        case "CURRENT":
            status = "방금 업데이트"
        case "DELAYED":
            status = "측정 지연"
        case "STATIC":
            return "참고 후보"
        case "UNKNOWN":
            return "참고 후보"
        default:
            return nil
        }

        guard let ageSeconds, ageSeconds >= 0 else {
            return status
        }
        return "\(status) · \(ageSeconds / 60)분 경과"
    }

    var comparisonStatusLabel: String {
        freshnessLabel ?? "참고 후보"
    }

    private static let peopleFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        return formatter
    }()

    private static func compactPeopleRange(min: Int, max: Int) -> String {
        if min == max {
            return compactPeopleCount(min)
        }
        return "\(compactPeopleCount(min))-\(compactPeopleCount(max))"
    }

    private static func compactPeopleCount(_ count: Int) -> String {
        if count >= 10_000 {
            let value = Double(count) / 10_000.0
            let rounded = (value * 10).rounded() / 10
            if rounded.truncatingRemainder(dividingBy: 1) == 0 {
                return "\(Int(rounded))만"
            }
            return "\(rounded)만"
        }

        return "\(max(1, Int((Double(count) / 1_000.0).rounded())))천"
    }
}

struct EventInsight: Decodable, Equatable {
    let events: [TrendEvent]
    let dataFreshness: String?
    let updatedAt: String?
    let sources: [String]?

    static let empty = EventInsight(events: [], dataFreshness: nil, updatedAt: nil, sources: [])
}

struct TrendEvent: Decodable, Equatable, Identifiable {
    let id: String
    let title: String
    let kind: String?
    let period: String?
    let startDate: String?
    let endDate: String?
    let latitude: Double
    let longitude: Double
    let address: String?
    let imageUrl: String?
    let source: String?
    let sourceContentId: String?
    let detailUrl: String?
    let collectedAt: String?
    let attribution: String?
    let sourceUrl: String?
    let license: String?
    let licenseUrl: String?

    init(
        id: String,
        title: String,
        kind: String?,
        period: String?,
        startDate: String?,
        endDate: String?,
        latitude: Double,
        longitude: Double,
        address: String?,
        imageUrl: String?,
        source: String?,
        sourceContentId: String?,
        detailUrl: String?,
        collectedAt: String?,
        attribution: String? = nil,
        sourceUrl: String? = nil,
        license: String? = nil,
        licenseUrl: String? = nil
    ) {
        self.id = id
        self.title = title
        self.kind = kind
        self.period = period
        self.startDate = startDate
        self.endDate = endDate
        self.latitude = latitude
        self.longitude = longitude
        self.address = address
        self.imageUrl = imageUrl
        self.source = source
        self.sourceContentId = sourceContentId
        self.detailUrl = detailUrl
        self.collectedAt = collectedAt
        self.attribution = attribution
        self.sourceUrl = sourceUrl
        self.license = license
        self.licenseUrl = licenseUrl
    }

    var isPopup: Bool {
        kind?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "popup"
    }

    var isVerifiedSeongsuPopup: Bool {
        isVerifiedSeongsuPopup(at: Date())
    }

    func isEligibleForSeason3EventSearch(
        allowsNonPopupEvents: Bool,
        at referenceDate: Date = Date()
    ) -> Bool {
        if isPopup {
            return isVerifiedSeongsuPopup(at: referenceDate)
        }
        return allowsNonPopupEvents
    }

    func isVerifiedSeongsuPopup(at referenceDate: Date) -> Bool {
        guard isPopup,
              title.range(of: "팝업", options: [.caseInsensitive, .diacriticInsensitive]) != nil
                || title.range(of: "popup", options: [.caseInsensitive, .diacriticInsensitive]) != nil else {
            return false
        }

        let location = [title, address]
            .compactMap { $0 }
            .joined(separator: " ")
            .lowercased()
        let hasSeongsuLocation = ["성수", "서울숲", "연무장"]
            .contains(where: location.contains)
        let isInsideSeongsu = latitude >= MapBounds.seongsu.minLat
            && latitude <= MapBounds.seongsu.maxLat
            && longitude >= MapBounds.seongsu.minLng
            && longitude <= MapBounds.seongsu.maxLng
        let hasCompleteDates: Bool
        if let start = Self.parseISODate(startDate), let end = Self.parseISODate(endDate) {
            hasCompleteDates = start <= end
        } else {
            hasCompleteDates = false
        }
        let hasNotEnded: Bool
        if let end = Self.parseISODate(endDate, timeZone: Self.koreaTimeZone) {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = Self.koreaTimeZone
            hasNotEnded = end >= calendar.startOfDay(for: referenceDate)
        } else {
            hasNotEnded = false
        }
        let hasAddress = address?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        let hasApprovedProvenance = source == Self.approvedPopupSource
            && attribution == Self.approvedPopupAttribution
            && safeAttributionURL?.absoluteString == Self.approvedPopupSourceURL
            && license == Self.approvedPopupLicense
            && safeLicenseURL?.absoluteString == Self.approvedPopupLicenseURL
        let hasSafeDetailURL = safeDetailURL != nil
        let isTobaccoFree = !Self.containsTobaccoTerms(
            in: [title, address, source, attribution, detailUrl, sourceUrl, license, licenseUrl]
        )
        return hasSeongsuLocation
            && isInsideSeongsu
            && hasCompleteDates
            && hasNotEnded
            && hasAddress
            && hasApprovedProvenance
            && hasSafeDetailURL
            && isTobaccoFree
    }

    var kindLabel: String {
        switch kind {
        case "popup":
            return "팝업"
        case "festival":
            return "축제"
        default:
            return "행사"
        }
    }

    var eventLabel: String {
        if let period, !period.isEmpty {
            return "\(kindLabel) · \(period)"
        }
        return kindLabel
    }

    var dateRangeLabel: String? {
        let start = startDate?.trimmingCharacters(in: .whitespacesAndNewlines)
        let end = endDate?.trimmingCharacters(in: .whitespacesAndNewlines)
        switch (start?.isEmpty == false ? start : nil, end?.isEmpty == false ? end : nil) {
        case let (start?, end?):
            return "\(start) ~ \(end)"
        case let (start?, nil):
            return "\(start)부터"
        case let (nil, end?):
            return "\(end)까지"
        default:
            return nil
        }
    }

    var collectionLabel: String? {
        guard let collectedAt else {
            return nil
        }
        let value = collectedAt.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : "수집 \(value)"
    }

    var sourceLabel: String {
        if let attribution {
            let label = attribution.trimmingCharacters(in: .whitespacesAndNewlines)
            if !label.isEmpty {
                return label
            }
        }
        switch source {
        case "KTO_TOUR_API":
            return "한국관광공사"
        case "SEOUL_CULTURE_API":
            return "서울특별시"
        case "CRAWLED_POPUP_TREND":
            return "팝업 공식 채널"
        default:
            return "행사 정보 제공처"
        }
    }

    var licenseLabel: String? {
        guard let license else {
            return nil
        }
        let value = license.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            return nil
        }
        switch value.uppercased() {
        case "KOGL_TYPE_1", "KOGL-1":
            return "공공누리 제1유형"
        case "KOGL_TYPE_2", "KOGL-2":
            return "공공누리 제2유형"
        case "KOGL_TYPE_3", "KOGL-3":
            return "공공누리 제3유형"
        case "KOGL_TYPE_4", "KOGL-4":
            return "공공누리 제4유형"
        case "CC_BY_4_0":
            return "CC BY 4.0"
        default:
            return value
        }
    }

    var safeDetailURL: URL? {
        Self.safeHTTPURL(detailUrl)
    }

    var safeAttributionURL: URL? {
        Self.safeHTTPURL(sourceUrl)
    }

    var safeLicenseURL: URL? {
        Self.safeHTTPURL(licenseUrl)
    }

    @available(*, deprecated, renamed: "safeDetailURL")
    var safeSourceURL: URL? {
        safeDetailURL
    }

    private static func safeHTTPURL(_ rawValue: String?) -> URL? {
        PublicURLPolicy.safeURL(rawValue, allowedSchemes: ["http", "https"])
    }

    private static let koreaTimeZone = TimeZone(identifier: "Asia/Seoul")!

    private static let approvedPopupSource = "SEOUL_CULTURE_API"
    private static let approvedPopupAttribution = "서울특별시 문화행사 정보"
    private static let approvedPopupSourceURL =
        "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do"
    private static let approvedPopupLicense = "공공누리 제1유형"
    private static let approvedPopupLicenseURL = "https://www.kogl.or.kr/info/licenseType1.do"

    private static let tobaccoTerms = [
        "담배", "흡연", "니코틴", "전자담배", "전자 담배", "궐련", "연초", "시가", "베이프", "베핑",
        "tobacco", "nicotine", "cigarette", "cigar", "vape", "vaping", "smoking"
    ]

    private static func containsTobaccoTerms(in values: [String?]) -> Bool {
        let text = values
            .compactMap { $0?.removingPercentEncoding ?? $0 }
            .joined(separator: " ")
            .lowercased()
        return tobaccoTerms.contains(where: text.contains)
    }

    private static func parseISODate(_ rawValue: String?) -> Date? {
        parseISODate(rawValue, timeZone: TimeZone(secondsFromGMT: 0)!)
    }

    private static func parseISODate(_ rawValue: String?, timeZone: TimeZone) -> Date? {
        guard let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }
        guard value.range(
            of: #"^\d{4}-\d{2}-\d{2}$"#,
            options: .regularExpression
        ) != nil else {
            return nil
        }

        let parts = value.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]),
              let month = Int(parts[1]),
              let day = Int(parts[2]) else {
            return nil
        }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = DateComponents(
            calendar: calendar,
            timeZone: calendar.timeZone,
            year: year,
            month: month,
            day: day
        )
        guard let date = calendar.date(from: components) else {
            return nil
        }

        let verified = calendar.dateComponents([.year, .month, .day], from: date)
        guard verified.year == year, verified.month == month, verified.day == day else {
            return nil
        }
        return date
    }
}

enum PublicURLPolicy {
    static func safeURL(_ rawValue: String?, allowedSchemes: Set<String>) -> URL? {
        guard let rawValue else {
            return nil
        }

        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty,
              let components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              allowedSchemes.contains(scheme),
              let host = components.host,
              !host.isEmpty,
              components.user == nil,
              components.password == nil,
              isPublicHost(host) else {
            return nil
        }

        return components.url
    }

    static func isPublicHost(_ rawHost: String) -> Bool {
        let host = rawHost
            .trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .lowercased()
        if host == "localhost"
            || host.hasSuffix(".localhost")
            || host.hasSuffix(".local")
            || host.hasSuffix(".internal") {
            return false
        }

        if host.contains(":"), let isPublic = publicIPv6Literal(host) {
            return isPublic
        }

        if host.contains(":") {
            return false
        }

        if let isPublic = publicIPv4Literal(host) {
            return isPublic
        }

        let looksNumeric = host.first?.isNumber == true
            && host.allSatisfy { character in
                character.isNumber
                    || character == "."
                    || character == "x"
                    || ("a"..."f").contains(String(character))
            }
        return !looksNumeric
    }

    private static func publicIPv4Literal(_ host: String) -> Bool? {
        var address = in_addr()
        guard inet_aton(host, &address) == 1 else {
            return nil
        }
        return isPublicIPv4(UInt32(bigEndian: address.s_addr))
    }

    private static func isPublicIPv4(_ address: UInt32) -> Bool {
        let first = Int((address >> 24) & 0xff)
        let second = Int((address >> 16) & 0xff)
        return first != 0
            && first != 10
            && first != 127
            && !(first == 100 && (64...127).contains(second))
            && !(first == 169 && second == 254)
            && !(first == 172 && (16...31).contains(second))
            && !(first == 192 && second == 0)
            && !(first == 192 && second == 168)
            && !(first == 198 && (18...19).contains(second))
            && !(first == 198 && second == 51 && Int((address >> 8) & 0xff) == 100)
            && !(first == 203 && second == 0 && Int((address >> 8) & 0xff) == 113)
            && first < 224
    }

    private static func publicIPv6Literal(_ host: String) -> Bool? {
        var address = in6_addr()
        guard inet_pton(AF_INET6, host, &address) == 1 else {
            return nil
        }
        let bytes = withUnsafeBytes(of: &address) { Array($0) }
        guard bytes.count == 16 else {
            return false
        }

        if bytes.allSatisfy({ $0 == 0 })
            || (bytes.dropLast().allSatisfy({ $0 == 0 }) && bytes.last == 1)
            || (bytes[0] & 0xfe) == 0xfc
            || (bytes[0] == 0xfe && (bytes[1] & 0xc0) == 0x80)
            || bytes[0] == 0xff {
            return false
        }

        let isIPv4Mapped = bytes[0..<10].allSatisfy({ $0 == 0 })
            && bytes[10] == 0xff
            && bytes[11] == 0xff
        let isIPv4Compatible = bytes[0..<12].allSatisfy({ $0 == 0 })
        if isIPv4Mapped || isIPv4Compatible {
            let ipv4 = UInt32(bytes[12]) << 24
                | UInt32(bytes[13]) << 16
                | UInt32(bytes[14]) << 8
                | UInt32(bytes[15])
            return isPublicIPv4(ipv4)
        }

        return true
    }
}

struct InsightProviderStatus: Decodable, Equatable {
    let configured: Bool
    let qualityStatus: String?
    let lastSuccessAt: String?
    let lastFailureAt: String?
    let detail: String?
}

struct PopupTrendStatus: Decodable, Equatable {
    let fileConfigured: Bool
    let fileExists: Bool
    let recordCount: Int
    let latestCollectedAt: String?
    let qualityStatus: String?
    let detail: String?
}

struct InsightStatus: Decodable, Equatable {
    let seoulCityDataKeyConfigured: Bool
    let telecomCrowdKeyConfigured: Bool
    let telecomCrowdUrlTemplateConfigured: Bool
    let ktoTourApiKeyConfigured: Bool
    let seoulCultureApiKeyConfigured: Bool
    let hotplaceMode: String?
    let eventMode: String?
    let seoulCityData: InsightProviderStatus?
    let telecomCrowd: InsightProviderStatus?
    let ktoTourApi: InsightProviderStatus?
    let seoulCultureApi: InsightProviderStatus?
    let popupTrends: PopupTrendStatus?
    let checkedAt: String?
}

struct MapInsight: Decodable, Equatable {
    let hotplaces: HotplaceInsight
    let events: EventInsight
    let status: InsightStatus?
    let updatedAt: String?

    static let empty = MapInsight(hotplaces: .empty, events: .empty, status: nil, updatedAt: nil)
}

struct SmokingZone: Decodable, Hashable, Identifiable {
    let id: Int
    let region: String
    let type: String
    let subtype: String
    let description: String
    let size: String?
    let date: String?
    let latitude: Double
    let longitude: Double
    let address: String
    let user: String
    let image: String?
    let imageUrl: String?

    var preferredImagePath: String? {
        if let imageUrl, !imageUrl.isEmpty {
            return imageUrl
        }

        return image
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var title: String {
        if !address.isEmpty {
            return address
        }

        if !subtype.isEmpty {
            return subtype
        }

        if !type.isEmpty {
            return type
        }

        return "흡연구역"
    }

    var summary: String {
        [region, type, subtype]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    init(
        id: Int,
        region: String,
        type: String,
        subtype: String,
        description: String,
        size: String? = nil,
        date: String? = nil,
        latitude: Double,
        longitude: Double,
        address: String,
        user: String,
        image: String? = nil,
        imageUrl: String? = nil
    ) {
        self.id = id
        self.region = region
        self.type = type
        self.subtype = subtype
        self.description = description
        self.size = size
        self.date = date
        self.latitude = latitude
        self.longitude = longitude
        self.address = address
        self.user = user
        self.image = image
        self.imageUrl = imageUrl
    }

    enum CodingKeys: String, CodingKey {
        case id
        case region
        case type
        case subtype
        case description
        case size
        case date
        case latitude
        case longitude
        case address
        case user
        case image
        case imageUrl
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = container.decodeLossyInt(forKey: .id)
        region = container.decodeLossyString(forKey: .region)
        type = container.decodeLossyString(forKey: .type)
        subtype = container.decodeLossyString(forKey: .subtype)
        description = container.decodeLossyString(forKey: .description)
        size = container.decodeLossyOptionalString(forKey: .size)
        date = container.decodeLossyOptionalString(forKey: .date)
        latitude = container.decodeLossyDouble(forKey: .latitude)
        longitude = container.decodeLossyDouble(forKey: .longitude)
        address = container.decodeLossyString(forKey: .address)
        user = container.decodeLossyString(forKey: .user, fallback: "익명사용자")
        image = container.decodeLossyOptionalString(forKey: .image)
        imageUrl = container.decodeLossyOptionalString(forKey: .imageUrl)
    }

}

struct CreateZonePayload: Encodable {
    let region: String
    let type: String
    let subtype: String
    let description: String
    let latitude: Double
    let longitude: Double
    let address: String
    let user: String
}

struct ZoneImageAttachment {
    let data: Data
    let filename: String
    let mimeType: String
}

struct UserProfile: Decodable, Hashable, Identifiable {
    let id: Int
    let email: String
    let nickname: String?
    let profileImage: String?
    let profileImageUrl: String?
    let createdAt: String

    var preferredProfileImagePath: String? {
        if let profileImageUrl, !profileImageUrl.isEmpty {
            return profileImageUrl
        }

        return profileImage
    }

    var displayName: String {
        if let nickname, !nickname.isEmpty {
            return nickname
        }

        return email
    }
}

extension KeyedDecodingContainer {
    func decodeLossyString(forKey key: Key, fallback: String = "") -> String {
        if let value = try? decode(String.self, forKey: key) {
            return value
        }

        if let value = try? decode(Int.self, forKey: key) {
            return String(value)
        }

        if let value = try? decode(Double.self, forKey: key) {
            return String(value)
        }

        return fallback
    }

    func decodeLossyOptionalString(forKey key: Key) -> String? {
        if (try? decodeNil(forKey: key)) == true {
            return nil
        }

        let value = decodeLossyString(forKey: key)
        return value.isEmpty ? nil : value
    }

    func decodeLossyInt(forKey key: Key, fallback: Int = 0) -> Int {
        if let value = try? decode(Int.self, forKey: key) {
            return value
        }

        if let value = try? decode(String.self, forKey: key), let parsed = Int(value) {
            return parsed
        }

        return fallback
    }

    func decodeLossyDouble(forKey key: Key, fallback: Double = 0) -> Double {
        if let value = try? decode(Double.self, forKey: key) {
            return value
        }

        if let value = try? decode(String.self, forKey: key), let parsed = Double(value) {
            return parsed
        }

        return fallback
    }
}
