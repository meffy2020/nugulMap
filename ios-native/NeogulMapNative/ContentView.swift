import AuthenticationServices
import SwiftUI

struct ContentView: View {
    @StateObject private var model = ZoneExplorerModel()

    var body: some View {
        ZoneMapView(model: model)
            .onReceive(
                NotificationCenter.default.publisher(
                    for: ASAuthorizationAppleIDProvider.credentialRevokedNotification
                )
            ) { _ in
                model.handleAppleCredentialRevoked()
            }
    }
}
