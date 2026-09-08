import Foundation

struct MapPickerViewport: Equatable {
    let minLatitude: Double
    let maxLatitude: Double
    let minLongitude: Double
    let maxLongitude: Double

    init(
        centerLatitude: Double,
        centerLongitude: Double,
        latitudeDelta: Double,
        longitudeDelta: Double
    ) {
        let halfLatitudeDelta = max(latitudeDelta, 0) / 2
        let halfLongitudeDelta = max(longitudeDelta, 0) / 2
        minLatitude = centerLatitude - halfLatitudeDelta
        maxLatitude = centerLatitude + halfLatitudeDelta
        minLongitude = centerLongitude - halfLongitudeDelta
        maxLongitude = centerLongitude + halfLongitudeDelta
    }

    func contains(latitude: Double, longitude: Double) -> Bool {
        latitude.isFinite
            && longitude.isFinite
            && latitude >= minLatitude
            && latitude <= maxLatitude
            && longitude >= minLongitude
            && longitude <= maxLongitude
    }
}

struct MapPickerCandidate: Equatable {
    let key: String
    let latitude: Double
    let longitude: Double
}

struct MapPickerHistory: Equatable {
    static let defaultLimit = 3

    private(set) var recentKeys: [String]
    let limit: Int

    init(recentKeys: [String] = [], limit: Int = defaultLimit) {
        self.limit = max(1, limit)
        self.recentKeys = []
        for key in recentKeys.reversed() {
            record(key)
        }
    }

    mutating func record(_ key: String) {
        recentKeys.removeAll(where: { $0 == key })
        recentKeys.insert(key, at: 0)
        if recentKeys.count > limit {
            recentKeys.removeLast(recentKeys.count - limit)
        }
    }
}

enum MapDicePicker {
    static func visibleCandidates(
        _ candidates: [MapPickerCandidate],
        in viewport: MapPickerViewport
    ) -> [MapPickerCandidate] {
        var seenKeys = Set<String>()
        return candidates.filter { candidate in
            viewport.contains(latitude: candidate.latitude, longitude: candidate.longitude)
                && seenKeys.insert(candidate.key).inserted
        }
    }

    static func pick(
        from candidates: [MapPickerCandidate],
        history: MapPickerHistory,
        randomIndex: (Int) -> Int = { Int.random(in: 0..<$0) }
    ) -> MapPickerCandidate? {
        let uniqueCandidates = uniqueCandidates(candidates)
        guard !uniqueCandidates.isEmpty else {
            return nil
        }

        let recentKeys = Set(history.recentKeys)
        let candidatesOutsideRecentHistory = uniqueCandidates.filter { !recentKeys.contains($0.key) }
        let eligibleCandidates: [MapPickerCandidate]
        if !candidatesOutsideRecentHistory.isEmpty {
            eligibleCandidates = candidatesOutsideRecentHistory
        } else if uniqueCandidates.count > 1, let immediateKey = history.recentKeys.first {
            eligibleCandidates = uniqueCandidates.filter { $0.key != immediateKey }
        } else {
            eligibleCandidates = uniqueCandidates
        }

        let proposedIndex = randomIndex(eligibleCandidates.count)
        let safeIndex = min(max(proposedIndex, 0), eligibleCandidates.count - 1)
        return eligibleCandidates[safeIndex]
    }

    private static func uniqueCandidates(_ candidates: [MapPickerCandidate]) -> [MapPickerCandidate] {
        var seenKeys = Set<String>()
        return candidates.filter { seenKeys.insert($0.key).inserted }
    }
}
