import SwiftUI

struct SettingsView: View {
    var body: some View {
        List {
            Section {
                policyLink("개인정보 처리방침", destination: AppConfig.privacyPolicyURL)
                policyLink("계정 삭제 안내", destination: AppConfig.accountDeletionURL)
                policyLink("로그인 연결 관리", destination: AppConfig.connectedAccountsURL)
                policyLink("서비스 이용약관", destination: AppConfig.termsURL)
                policyLink("콘텐츠 신고 및 고객지원", destination: AppConfig.supportURL)
            }
        }
        .navigationTitle("설정")
    }

    private func policyLink(_ title: String, destination: URL) -> some View {
        Link(destination: destination) {
            Text(title)
                .font(.body)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityLabel(title)
    }
}
