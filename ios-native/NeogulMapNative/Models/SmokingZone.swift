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
        image: String? = nil
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
    let createdAt: String

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
