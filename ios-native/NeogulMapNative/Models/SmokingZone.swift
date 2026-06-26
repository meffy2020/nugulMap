import CoreLocation
import Foundation
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

    var eventLabel: String {
        let kindLabel: String
        switch kind {
        case "popup":
            kindLabel = "팝업"
        case "festival":
            kindLabel = "축제"
        default:
            kindLabel = "행사"
        }

        if let period, !period.isEmpty {
            return "\(kindLabel) · \(period)"
        }
        return kindLabel
    }

    var sourceLabel: String {
        switch source {
        case "KTO_TOUR_API":
            return "한국관광공사 TourAPI"
        case "SEOUL_CULTURE_API":
            return "서울 문화행사 API"
        case "CRAWLED_POPUP_TREND":
            return "크롤링 팝업 트렌드"
        default:
            return "이벤트 후보"
        }
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

    static let fallback: [SmokingZone] = [
        SmokingZone(
            id: 1,
            region: "서울특별시",
            type: "실외",
            subtype: "공원",
            description: "시청 근처 흡연구역",
            latitude: 37.5665,
            longitude: 126.9780,
            address: "서울특별시 중구 세종대로 1가",
            user: "관리자"
        ),
        SmokingZone(
            id: 2,
            region: "서울특별시",
            type: "실외",
            subtype: "거리",
            description: "광화문 주변 흡연구역",
            latitude: 37.5720,
            longitude: 126.9769,
            address: "서울특별시 종로구 세종로 1",
            user: "관리자"
        )
    ]
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
