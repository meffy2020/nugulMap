import Foundation

@main
private enum MapDicePickerTests {
    static func main() {
        let viewport = MapPickerViewport(
            centerLatitude: 37.55,
            centerLongitude: 127.05,
            latitudeDelta: 0.02,
            longitudeDelta: 0.02
        )
        let inside = candidate("hotplace:inside", latitude: 37.55, longitude: 127.05)
        let nearBoundary = candidate("event:near-boundary", latitude: 37.559, longitude: 127.059)
        let outside = candidate("hotplace:outside", latitude: 37.57, longitude: 127.05)
        let invalid = candidate("event:invalid", latitude: .nan, longitude: 127.05)

        let visible = MapDicePicker.visibleCandidates(
            [inside, nearBoundary, outside, invalid, inside],
            in: viewport
        )
        expect(visible == [inside, nearBoundary], "현재 지도 경계 안의 고유 후보만 유지")

        let recentHistory = MapPickerHistory(
            recentKeys: ["hotplace:a", "hotplace:b", "hotplace:c"]
        )
        let candidates = ["hotplace:a", "hotplace:b", "hotplace:c", "hotplace:d"].map {
            candidate($0)
        }
        let freshPick = MapDicePicker.pick(
            from: candidates,
            history: recentHistory,
            randomIndex: { _ in 0 }
        )
        expect(freshPick?.key == "hotplace:d", "최근 3개 밖의 후보를 우선 선택")

        let exhaustedCandidates = ["hotplace:a", "hotplace:b", "hotplace:c"].map {
            candidate($0)
        }
        let fallbackPick = MapDicePicker.pick(
            from: exhaustedCandidates,
            history: recentHistory,
            randomIndex: { _ in 0 }
        )
        expect(fallbackPick?.key != "hotplace:a", "모든 후보가 최근 기록이어도 직전 결과는 제외")

        let onlyCandidate = candidate("event:only")
        let singlePick = MapDicePicker.pick(
            from: [onlyCandidate],
            history: MapPickerHistory(recentKeys: [onlyCandidate.key]),
            randomIndex: { _ in 0 }
        )
        expect(singlePick == onlyCandidate, "후보가 하나면 반복 선택 허용")

        let noPick = MapDicePicker.pick(
            from: [],
            history: MapPickerHistory(),
            randomIndex: { _ in 0 }
        )
        expect(noPick == nil, "빈 후보에서는 선택하지 않음")

        var history = MapPickerHistory()
        ["hotplace:a", "event:b", "hotplace:c", "event:d", "hotplace:c"].forEach {
            history.record($0)
        }
        expect(
            history.recentKeys == ["hotplace:c", "event:d", "event:b"],
            "최근 기록은 중복 없이 최신순 3개 유지"
        )

        print("PASS: MapDicePickerTests")
    }

    private static func candidate(
        _ key: String,
        latitude: Double = 37.55,
        longitude: Double = 127.05
    ) -> MapPickerCandidate {
        MapPickerCandidate(key: key, latitude: latitude, longitude: longitude)
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
