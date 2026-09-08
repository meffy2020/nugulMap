import CoreLocation
import Foundation

struct LocationLetter: Decodable, Hashable, Identifiable {
    let id: Int
    let authorId: Int?
    let authorLabel: String
    let content: String
    let mine: Bool
    let createdAt: String
    let likeCount: Int
    let likedByMe: Bool

    private enum CodingKeys: String, CodingKey {
        case id
        case authorId
        case authorLabel
        case content
        case mine
        case createdAt
        case likeCount
        case likedByMe
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        authorId = try container.decodeIfPresent(Int.self, forKey: .authorId)
        authorLabel = try container.decodeIfPresent(String.self, forKey: .authorLabel) ?? "익명사용자"
        content = try container.decode(String.self, forKey: .content)
        mine = try container.decodeIfPresent(Bool.self, forKey: .mine) ?? false
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        likeCount = max(try container.decodeIfPresent(Int.self, forKey: .likeCount) ?? 0, 0)
        likedByMe = try container.decodeIfPresent(Bool.self, forKey: .likedByMe) ?? false
    }

    private init(
        id: Int,
        authorId: Int?,
        authorLabel: String,
        content: String,
        mine: Bool,
        createdAt: String,
        likeCount: Int,
        likedByMe: Bool
    ) {
        self.id = id
        self.authorId = authorId
        self.authorLabel = authorLabel
        self.content = content
        self.mine = mine
        self.createdAt = createdAt
        self.likeCount = likeCount
        self.likedByMe = likedByMe
    }

    func updatingLike(count: Int, likedByMe: Bool) -> LocationLetter {
        LocationLetter(
            id: id,
            authorId: authorId,
            authorLabel: authorLabel,
            content: content,
            mine: mine,
            createdAt: createdAt,
            likeCount: max(count, 0),
            likedByMe: likedByMe
        )
    }
}

struct LocationLetterNearby: Decodable, Hashable {
    let letters: [LocationLetter]
    let count: Int
    let radiusMeters: Int

    static let empty = LocationLetterNearby(letters: [], count: 0, radiusMeters: 180)
}

struct LocationLetterAnchor: Identifiable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
}

struct LocationLetterCreatePayload: Encodable {
    let latitude: Double
    let longitude: Double
    let content: String
}

struct LocationLetterPayload: Decodable {
    let letter: LocationLetter
}

struct LocationLetterLikeState: Decodable {
    let letterId: Int
    let likeCount: Int
    let likedByMe: Bool
}

struct LocationLetterReportPayload: Encodable {
    let reason: String
    let details: String?
}
