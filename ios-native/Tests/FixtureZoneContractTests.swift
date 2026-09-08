import Foundation

@main
struct FixtureZoneContractTests {
    private struct Envelope: Decodable {
        struct Payload: Decodable {
            let zones: [SmokingZone]
        }

        let success: Bool
        let data: Payload
    }

    static func main() throws {
        let testFile = URL(fileURLWithPath: #filePath)
        let fixture = testFile
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("UITests/Fixtures/api/zones/bounds")
        let decoded = try JSONDecoder().decode(Envelope.self, from: Data(contentsOf: fixture))

        guard decoded.success, decoded.data.zones.count == 2 else {
            fatalError("흡연구역 UI fixture 계약 실패")
        }
        guard decoded.data.zones[0].id == 42 else {
            fatalError("흡연구역 UI fixture ID 계약 실패")
        }
        print("PASS: FixtureZoneContractTests")
    }
}
