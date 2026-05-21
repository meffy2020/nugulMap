import Foundation

struct ZoneReview: Decodable, Hashable, Identifiable {
    let id: Int
    let zoneId: Int
    let authorId: Int?
    let authorNickname: String
    let authorEmail: String?
    let authorProfileImage: String?
    let content: String
    let createdAt: String
    let updatedAt: String

    enum CodingKeys: String, CodingKey {
        case id
        case zoneId
        case authorId
        case authorNickname
        case authorEmail
        case authorProfileImage
        case content
        case createdAt
        case updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let created = container.decodeLossyString(forKey: .createdAt)

        id = container.decodeLossyInt(forKey: .id)
        zoneId = container.decodeLossyInt(forKey: .zoneId)
        authorId = container.decodeLossyOptionalInt(forKey: .authorId)
        authorNickname = container.decodeLossyString(
            forKey: .authorNickname,
            fallback: container.decodeLossyString(forKey: .authorEmail, fallback: "익명")
        )
        authorEmail = container.decodeLossyOptionalString(forKey: .authorEmail)
        authorProfileImage = container.decodeLossyOptionalString(forKey: .authorProfileImage)
        content = container.decodeLossyString(forKey: .content)
        createdAt = created
        updatedAt = container.decodeLossyString(forKey: .updatedAt, fallback: created)
    }
}

private extension KeyedDecodingContainer {
    func decodeLossyOptionalInt(forKey key: Key) -> Int? {
        if (try? decodeNil(forKey: key)) == true {
            return nil
        }

        if let value = try? decode(Int.self, forKey: key) {
            return value
        }

        if let value = try? decode(String.self, forKey: key), let parsed = Int(value) {
            return parsed
        }

        return nil
    }
}
