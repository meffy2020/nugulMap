import SwiftUI

struct SettingsView: View {
    var body: some View {
        List {
            Section("앱") {
                LabeledContent("이름", value: "너굴맵 Native")
                LabeledContent("플랫폼", value: "SwiftUI + KakaoMapsSDK")
            }

            Section("정책") {
                Link("개인정보 처리방침", destination: AppConfig.privacyPolicyURL)
                Link("계정 삭제 안내", destination: AppConfig.accountDeletionURL)
            }

            Section("API") {
                Text(AppConfig.apiBaseURL.absoluteString)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }

            Section("개발 상태") {
                Label("지도, 검색, 목록, 상세, 리뷰 조회 포함", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
        }
        .navigationTitle("설정")
    }
}
