import Foundation

@main
private enum ImageURLContractTests {
    static func main() {
        let client = NugulAPIClient(baseURL: URL(string: "https://api.nugulmap.com")!)

        expect(
            client.imageURL(for: "zones/photo.jpg")?.absoluteString
                == "https://api.nugulmap.com/api/images/zones/photo.jpg",
            "상대 이미지 경로는 신뢰한 API 원본으로 해석"
        )
        expect(
            client.imageURL(for: "https://cdn.example.com/photo.jpg")?.host == "cdn.example.com",
            "공개 HTTPS 이미지 허용"
        )
        expect(client.imageURL(for: "http://cdn.example.com/photo.jpg") == nil, "외부 HTTP 이미지 차단")
        expect(client.imageURL(for: "file:///etc/passwd") == nil, "file 이미지 차단")
        expect(client.imageURL(for: "https://user:secret@cdn.example.com/photo.jpg") == nil, "userinfo 이미지 차단")
        expect(client.imageURL(for: "https://localhost/photo.jpg") == nil, "localhost 이미지 차단")
        expect(client.imageURL(for: "https://10.0.0.8/photo.jpg") == nil, "사설 IPv4 이미지 차단")
        expect(client.imageURL(for: "https://2130706433/photo.jpg") == nil, "숫자형 IPv4 이미지 차단")
        expect(client.imageURL(for: "https://0177.0.0.1/photo.jpg") == nil, "8진 IPv4 이미지 차단")
        expect(client.imageURL(for: "https://0x7f.0.0.1/photo.jpg") == nil, "16진 IPv4 이미지 차단")
        expect(client.imageURL(for: "https://[::1]/photo.jpg") == nil, "IPv6 loopback 이미지 차단")
        expect(client.imageURL(for: "https://[::ffff:127.0.0.1]/photo.jpg") == nil, "mapped IPv6 이미지 차단")

        let fixtureClient = NugulAPIClient(baseURL: URL(string: "http://127.0.0.1:18765")!)
        expect(
            fixtureClient.imageURL(for: "http://127.0.0.1:18765/api/images/fixture.png")?.path
                == "/api/images/fixture.png",
            "동일 API 원본은 로컬 UI 테스트에서도 허용"
        )
        expect(
            fixtureClient.imageURL(for: "http://127.0.0.1:18766/api/images/fixture.png") == nil,
            "다른 로컬 포트 이미지 차단"
        )

        print("PASS: ImageURLContractTests")
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else {
            fputs("FAIL: \(label)\n", stderr)
            Foundation.exit(1)
        }
    }
}
