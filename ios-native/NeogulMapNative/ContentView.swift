import SwiftUI

struct ContentView: View {
    @StateObject private var model = ZoneExplorerModel()

    var body: some View {
        ZoneMapView(model: model)
    }
}
