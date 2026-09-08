import Foundation

private final class RecommendationStubURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var capturedRequests: [URLRequest] = []
    nonisolated(unsafe) static var capturedBodies: [Data] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequests.append(request)
        Self.capturedBodies.append(request.httpBody ?? request.httpBodyStream.map(Self.readAll) ?? Data())
        let path = request.url?.path ?? ""
        let method = request.httpMethod ?? "GET"
        let statusCode: Int
        let body: String

        switch (method, path) {
        case ("GET", "/api/recommendations/places"):
            statusCode = 200
            body = Self.placeResponse
        case ("GET", "/api/recommendations/preferences"):
            statusCode = 200
            body = Self.preferenceResponse(enabled: false)
        case ("PUT", "/api/recommendations/preferences"):
            statusCode = 200
            body = Self.preferenceResponse(enabled: true)
        case ("GET", "/api/recommendations/saved-items"):
            statusCode = 200
            body = "{\"success\":true,\"data\":[\(Self.savedItem)]}"
        case ("POST", "/api/recommendations/saved-items"):
            statusCode = 201
            body = "{\"success\":true,\"data\":\(Self.savedItem)}"
        case ("DELETE", "/api/recommendations/saved-items"):
            statusCode = 204
            body = ""
        case ("POST", "/api/recommendations/interactions"):
            statusCode = 201
            body = Self.interactionResponse
        default:
            statusCode = 404
            body = "{\"success\":false,\"message\":\"unexpected request\"}"
        }

        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        if !body.isEmpty {
            client?.urlProtocol(self, didLoad: Data(body.utf8))
        }
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func readAll(from stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }
        var result = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            result.append(buffer, count: count)
        }
        return result
    }

    private static let placeResponse = #"""
    {
      "success": true,
      "data": {
        "requestId": "rec-request-1",
        "modelVersion": "context-ranker-v1",
        "dataStatus": "READY",
        "personalizationMode": "CONTEXT_ONLY",
        "intent": "CAFE",
        "eligibleCandidateCount": 12,
        "generatedAt": "2026-09-01T03:00:00Z",
        "items": [{
          "id": "kakao-42",
          "provider": "KAKAO_LOCAL",
          "name": "서울숲 카페",
          "category": "카페",
          "address": "서울특별시 성동구 서울숲길 1",
          "latitude": 37.5445,
          "longitude": 127.0555,
          "distanceMeters": 420,
          "score": 0.91,
          "reasonCodes": ["CAFE_INTENT_MATCH", "WITHIN_ONE_KILOMETER"],
          "sourceUrl": "https://place.map.kakao.com/42"
        }]
      }
    }
    """#

    private static let savedItem = #"""
    {
      "id": 7,
      "itemType": "PLACE",
      "provider": "KAKAO_LOCAL",
      "itemId": "kakao-42",
      "title": "서울숲 카페",
      "category": "카페",
      "lat": 37.5445,
      "lng": 127.0555,
      "sourceUrl": "https://place.map.kakao.com/42",
      "createdAt": "2026-09-01T12:00:00",
      "updatedAt": "2026-09-01T12:00:00"
    }
    """#

    private static func preferenceResponse(enabled: Bool) -> String {
        """
        {"success":true,"data":{"personalizationEnabled":\(enabled),"preferredTags":["조용한"],"dislikedTags":[],"budgetLevel":"ANY","maxDistanceMeters":3000,"updatedAt":"2026-09-01T12:00:00"}}
        """
    }

    private static let interactionResponse = #"""
    {
      "success": true,
      "data": {
        "id": 9,
        "requestId": "rec-request-1",
        "itemType": "PLACE",
        "provider": "KAKAO_LOCAL",
        "itemId": "kakao-42",
        "eventType": "DETAIL_OPEN",
        "intent": "CAFE",
        "contextRegion": null,
        "modelVersion": "context-ranker-v1",
        "occurredAt": "2026-09-01T12:00:00"
      }
    }
    """#
}

@main
private enum RecommendationAPIContractTests {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RecommendationStubURLProtocol.self]
        let client = NugulAPIClient(
            baseURL: URL(string: "https://example.test")!,
            session: URLSession(configuration: configuration)
        )

        let response = try await client.fetchRecommendedPlaces(
            intent: .cafe,
            latitude: 37.5445,
            longitude: 127.0555,
            radiusMeters: 3_000,
            limit: 8,
            accessToken: nil
        )
        expect(response.requestId == "rec-request-1", "requestId 디코딩")
        expect(response.personalizationMode == .contextOnly, "기본 CONTEXT_ONLY 모드 디코딩")
        expect(response.items.first?.stableKey == "kakao_local:kakao-42", "provider+id 안정 키")
        expect(response.items.first?.reasonCodes == [.cafeIntentMatch, .withinOneKilometer], "추천 이유 디코딩")

        let placeRequest = RecommendationStubURLProtocol.capturedRequests[0]
        let placeQuery = queryDictionary(placeRequest)
        expect(placeRequest.value(forHTTPHeaderField: "Authorization") == nil, "비로그인 GET은 Bearer 생략")
        expect(placeQuery["intent"] == "CAFE", "intent 쿼리")
        expect(placeQuery["lat"] == "37.5445", "lat 쿼리")
        expect(placeQuery["lng"] == "127.0555", "lng 쿼리")
        expect(placeQuery["radiusMeters"] == "3000", "radiusMeters 쿼리")
        expect(placeQuery["limit"] == "8", "limit 쿼리")

        let preferences = try await client.fetchRecommendationPreferences(accessToken: "token")
        expect(!preferences.personalizationEnabled, "개인화 기본 OFF")
        let updated = try await client.updateRecommendationPreferences(
            RecommendationPreferenceUpdate(
                personalizationEnabled: true,
                preferredTags: preferences.preferredTags,
                dislikedTags: preferences.dislikedTags,
                budgetLevel: preferences.budgetLevel,
                maxDistanceMeters: preferences.maxDistanceMeters
            ),
            accessToken: "token"
        )
        expect(updated.personalizationEnabled, "개인화 opt-in 응답")

        let savedItems = try await client.fetchSavedRecommendationItems(accessToken: "token")
        expect(savedItems.first?.stableKey == "kakao_local:kakao-42", "저장 목록 디코딩")
        _ = try await client.saveRecommendationItem(
            RecommendationSavedItemRequest(
                itemType: .place,
                provider: "KAKAO_LOCAL",
                itemId: "kakao-42",
                title: "서울숲 카페",
                category: "카페",
                lat: 37.5445,
                lng: 127.0555,
                sourceUrl: "https://place.map.kakao.com/42"
            ),
            accessToken: "token"
        )
        try await client.deleteSavedRecommendationItem(
            provider: "KAKAO_LOCAL",
            itemID: "kakao-42",
            accessToken: "token"
        )

        let interaction = try await client.recordRecommendationInteraction(
            RecommendationInteractionRequest(
                requestId: "rec-request-1",
                itemType: .place,
                provider: "KAKAO_LOCAL",
                itemId: "kakao-42",
                eventType: .detailOpen,
                intent: "CAFE",
                contextRegion: nil,
                modelVersion: "context-ranker-v1",
                occurredAt: nil
            ),
            accessToken: "token"
        )
        expect(interaction.eventType == .detailOpen, "상호작용 응답 디코딩")
        expect(interaction.contextRegion == nil, "서버가 위치 문맥을 저장하지 않음")

        let requests = RecommendationStubURLProtocol.capturedRequests
        expect(requests[1].value(forHTTPHeaderField: "Authorization") == "Bearer token", "인증 API Bearer")
        expect(requests[2].httpMethod == "PUT", "설정 PUT")
        expect(requests[4].httpMethod == "POST", "저장 POST")
        expect(requests[5].httpMethod == "DELETE", "저장 DELETE")
        let deleteQuery = queryDictionary(requests[5])
        expect(deleteQuery["itemType"] == "PLACE", "저장 삭제 itemType")
        expect(deleteQuery["provider"] == "KAKAO_LOCAL", "저장 삭제 provider")
        expect(deleteQuery["itemId"] == "kakao-42", "저장 삭제 itemId")

        let interactionBody = try jsonDictionary(RecommendationStubURLProtocol.capturedBodies[6])
        expect(interactionBody["eventType"] as? String == "DETAIL_OPEN", "상호작용 eventType")
        expect(interactionBody["contextRegion"] == nil, "위치 문맥은 상호작용 요청에서 생략")
        expect(interactionBody["occurredAt"] == nil, "occurredAt은 서버 시각 사용")
        print("PASS: RecommendationAPIContractTests")
    }

    private static func queryDictionary(_ request: URLRequest) -> [String: String] {
        let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
        return Dictionary(uniqueKeysWithValues: (components?.queryItems ?? []).compactMap { item in
            item.value.map { (item.name, $0) }
        })
    }

    private static func jsonDictionary(_ body: Data) throws -> [String: Any] {
        guard let object = try JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            fatalError("JSON 요청 본문이 없습니다")
        }
        return object
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ message: String) {
        guard condition() else {
            fatalError("FAIL: \(message)")
        }
    }
}
