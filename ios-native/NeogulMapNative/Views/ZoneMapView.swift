import Combine
import CoreLocation
import MapKit
#if os(iOS)
@preconcurrency import KakaoMapsSDK
#endif
import PhotosUI
import SwiftUI
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct ZoneMapView: View {
    @ObservedObject var model: ZoneExplorerModel
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var locationProvider = LocationProvider()
    @State private var mapRegion: MKCoordinateRegion = .centralSeoul
    @State private var cameraCenter = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780)
    @State private var activeSheet: HomeSheet?
    @State private var didBootstrap = false
    @State private var suppressNextSettledFetch = false
    @State private var isMenuOpen = false

    var body: some View {
        ZStack {
            mapLayer
                .overlay(alignment: .top) {
                    topHeader
                        .padding(.horizontal, 16)
                        .padding(.top, safeAreaTop + 12)
                }
                .overlay(alignment: .bottom) {
                    bottomControls
                }

            if model.isLoading {
                LoadingOverlay()
            }

            if isMenuOpen {
                sideMenuOverlay
                    .transition(.move(edge: .leading).combined(with: .opacity))
                    .zIndex(10)
            }
        }
        .background(Color.nugulBackground)
        .ignoresSafeArea()
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .report:
                ReportZoneSheet(model: model, coordinate: cameraCenter)
                    .presentationDetents([.height(270), .medium])
                    .compactGlassSheet()
            case .login:
                LoginSheet(model: model)
                    .presentationDetents([.medium, .large])
                    .compactGlassSheet()
            case .profile:
                ProfileSheet(model: model)
                    .presentationDetents([.medium, .large])
                    .compactGlassSheet()
            case .profileSetup:
                ProfileSetupSheet(model: model)
                    .presentationDetents([.medium, .large])
                    .compactGlassSheet()
            case .settings:
                NavigationStack {
                    SettingsView()
                }
                .presentationDetents([.medium, .large])
                .compactGlassSheet()
            }
        }
        .onChange(of: model.pendingSignupEmail) { _, email in
            if email != nil {
                activeSheet = .profileSetup
            }
        }
        .task {
            await bootstrapIfNeeded()
            handlePendingIntentRoute()
        }
        .onChange(of: scenePhase) { _, newValue in
            if newValue == .active {
                handlePendingIntentRoute()
            }
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
    }

    private var mapLayer: some View {
        NugulKakaoMapView(
            region: $mapRegion,
            model: model,
            onRegionChanged: { region in
                cameraCenter = region.center
            },
            onRegionSettled: { region in
                if suppressNextSettledFetch {
                    suppressNextSettledFetch = false
                    return
                }

                Task {
                    await model.loadZones(in: .visibleRegion(region))
                }
            },
            onSelectZone: { zone in
                withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) {
                    model.select(zone)
                }
            }
        )
        .ignoresSafeArea()
    }

    private var topHeader: some View {
        UnifiedSearchMenuBar(
            query: $model.query,
            isLoading: model.isLoading,
            onMenu: {
                withAnimation(.spring(response: 0.36, dampingFraction: 0.86)) {
                    isMenuOpen = true
                }
            },
            onClear: {
                Task {
                    await model.resetSearch()
                    centerOnFirstZoneIfPossible(reloadZones: false)
                }
            },
            onSearch: {
                Task {
                    if let coordinate = await model.search() {
                        centerMap(on: coordinate, reloadZones: false)
                    } else {
                        centerOnFirstZoneIfPossible(reloadZones: false)
                    }
                }
            }
        )
    }

    private var sideMenuOverlay: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(0.24)
                .ignoresSafeArea()
                .onTapGesture {
                    closeMenu()
                }

            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("너굴맵")
                            .font(.system(size: 24, weight: .black))
                            .foregroundStyle(Color.nugulPrimary)
                        Text(model.currentUser?.displayName ?? "지도와 계정을 한 곳에서 관리")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.nugulMutedText)
                    }

                    Spacer()

                    Button(action: closeMenu) {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .black))
                            .foregroundStyle(Color.nugulMutedText)
                            .frame(width: 34, height: 34)
                            .background(Color.white.opacity(0.72), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("메뉴 닫기")
                }

                if let email = model.currentUser?.email {
                    Text(email)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineLimit(1)
                }

                Divider().opacity(0.45)

                SideMenuActionRow(
                    title: model.currentUser == nil ? "로그인 / 프로필" : "내 프로필",
                    subtitle: "계정, 닉네임, 내 활동 관리",
                    systemImage: "person.crop.circle.fill"
                ) {
                    closeMenu()
                    activeSheet = model.currentUser == nil ? .login : .profile
                }

                SideMenuActionRow(
                    title: "흡연구역 제보",
                    subtitle: "현재 지도 위치 기준으로 새 구역 등록",
                    systemImage: "plus.circle.fill"
                ) {
                    closeMenu()
                    activeSheet = model.currentUser == nil ? .login : .report
                }

                SideMenuActionRow(
                    title: "지도 새로고침",
                    subtitle: "현재 화면의 구역을 다시 불러오기",
                    systemImage: "arrow.clockwise.circle.fill"
                ) {
                    closeMenu()
                    Task {
                        await model.loadZones(in: .visibleRegion(mapRegion))
                    }
                }

                SideMenuActionRow(
                    title: "설정",
                    subtitle: "앱 정보와 API 환경 확인",
                    systemImage: "gearshape.fill"
                ) {
                    closeMenu()
                    activeSheet = .settings
                }

                Spacer()

                Text("상단은 하나의 검색창만 유지하고, 프로필과 설정은 메뉴에서 열립니다.")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.nugulMutedText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, safeAreaTop + 22)
            .padding(.horizontal, 20)
            .padding(.bottom, safeAreaBottom + 22)
            .frame(width: 326)
            .frame(maxHeight: .infinity)
            .background(.ultraThinMaterial)
            .background(Color.white.opacity(0.78))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .shadow(color: .black.opacity(0.22), radius: 24, x: 0, y: 12)
            .padding(.leading, 10)
        }
        .ignoresSafeArea()
    }

    private func closeMenu() {
        withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
            isMenuOpen = false
        }
    }

    private var bottomControls: some View {
        VStack(spacing: 12) {
            if let message = model.errorMessage ?? locationProvider.errorMessage {
                WebToastBanner(message: message)
                    .padding(.horizontal, 16)
            }

            HStack(alignment: .bottom, spacing: 14) {
                Button {
                    locationProvider.requestLocation { coordinate in
                        centerMap(on: coordinate)
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(.ultraThinMaterial)
                            .overlay(Circle().fill(Color.black.opacity(0.18)))
                            .overlay(Circle().stroke(Color.white.opacity(0.72), lineWidth: 1))
                            .shadow(color: .black.opacity(0.2), radius: 16, x: 0, y: 8)

                        if locationProvider.isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 52, height: 52)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("현재 위치")

                Spacer()

                Button {
                    model.selectedZone = nil
                    activeSheet = model.currentUser == nil ? .login : .report
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.system(size: 19, weight: .black))
                        Text("제보하기")
                            .font(.system(size: 15, weight: .black))
                    }
                    .foregroundStyle(.white)
                    .frame(height: 52)
                    .padding(.horizontal, 18)
                    .background(
                        LinearGradient(
                            colors: [Color.nugulPrimary, Color.nugulPrimary.opacity(0.86)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        in: Capsule(style: .continuous)
                    )
                    .overlay(Capsule(style: .continuous).stroke(Color.white.opacity(0.24), lineWidth: 1))
                    .shadow(color: Color.nugulPrimary.opacity(0.34), radius: 18, x: 0, y: 10)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("흡연구역 제보하기")
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, safeAreaBottom + 24)
    }

    private var safeAreaTop: CGFloat {
        #if os(iOS)
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets.top }
            .first ?? 0
        #else
        0
        #endif
    }

    private var safeAreaBottom: CGFloat {
        #if os(iOS)
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets.bottom }
            .first ?? 0
        #else
        0
        #endif
    }

    @MainActor
    private func bootstrapIfNeeded() async {
        guard !didBootstrap else {
            return
        }

        didBootstrap = true
        await model.bootstrap(initialBounds: .visibleRegion(mapRegion))
    }

    private func centerMap(on coordinate: CLLocationCoordinate2D, reloadZones: Bool = true) {
        cameraCenter = coordinate
        let focusedRegion = MKCoordinateRegion(
            center: coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.035, longitudeDelta: 0.035)
        )

        withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) {
            mapRegion = focusedRegion
        }

        if reloadZones {
            Task {
                await model.loadZones(in: .visibleRegion(focusedRegion))
            }
        } else {
            suppressNextSettledFetch = true
        }
    }

    private func centerOnFirstZoneIfPossible(reloadZones: Bool = true) {
        guard let firstZone = model.zones.first else {
            return
        }

        centerMap(on: firstZone.coordinate, reloadZones: reloadZones)
    }

    private func handleDeepLink(_ url: URL) {
        guard let route = PendingIntentRoute(url: url) else {
            return
        }

        apply(route)
    }

    private func handlePendingIntentRoute() {
        guard let route = PendingIntentRoute.consume() else {
            return
        }

        apply(route)
    }

    private func apply(_ route: PendingIntentRoute) {
        switch route {
        case .map:
            activeSheet = nil
        case .report:
            activeSheet = model.currentUser == nil ? .login : .report
        case .profile:
            activeSheet = model.currentUser == nil ? .login : .profile
        case let .search(keyword):
            model.query = keyword
            Task {
                if let coordinate = await model.search() {
                    centerMap(on: coordinate, reloadZones: false)
                } else {
                    centerOnFirstZoneIfPossible(reloadZones: false)
                }
            }
        }
    }
}

private enum HomeSheet: Identifiable {
    case report
    case login
    case profile
    case profileSetup
    case settings

    var id: String {
        switch self {
        case .report:
            return "report"
        case .login:
            return "login"
        case .profile:
            return "profile"
        case .profileSetup:
            return "profile-setup"
        case .settings:
            return "settings"
        }
    }
}

private extension MKCoordinateRegion {
    static let centralSeoul = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780),
        span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
    )
}

private struct UnifiedSearchMenuBar: View {
    @Binding var query: String
    let isLoading: Bool
    let onMenu: () -> Void
    let onClear: () -> Void
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 17, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                    .frame(width: 38, height: 38)
                    .background(Color.white.opacity(0.66), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("메뉴 열기")

            Rectangle()
                .fill(Color.nugulBorder)
                .frame(width: 1, height: 24)

            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.nugulMutedText)

            TextField("장소, 주소 검색...", text: $query)
                .font(.system(size: 16, weight: .medium))
                .submitLabel(.search)
                .onSubmit(onSearch)

            if !query.isEmpty {
                Button(action: onClear) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText)
                        .frame(width: 30, height: 30)
                        .background(Color.nugulMuted, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("검색어 지우기")
            }

            Rectangle()
                .fill(Color.nugulBorder)
                .frame(width: 1, height: 24)

            Button(action: onSearch) {
                if isLoading {
                    ProgressView()
                        .frame(width: 34)
                } else {
                    Text("검색")
                        .font(.system(size: 14, weight: .black))
                        .foregroundStyle(Color.nugulPrimary)
                        .frame(width: 34)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .frame(height: 56)
        .background(.ultraThinMaterial, in: Capsule(style: .continuous))
        .background(Color.white.opacity(0.72), in: Capsule(style: .continuous))
        .overlay(
            Capsule(style: .continuous)
                .stroke(Color.white.opacity(0.82), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.16), radius: 18, x: 0, y: 10)
    }
}

private struct SideMenuActionRow: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.nugulPrimary)
                    .frame(width: 38, height: 38)
                    .background(Color.nugulPrimary.opacity(0.11), in: Circle())

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: 16, weight: .black))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(subtitle)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineLimit(2)
                }

                Spacer()
            }
            .padding(12)
            .background(Color.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.white.opacity(0.68), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct ProfileAvatar: View {
    let user: UserProfile?

    var body: some View {
        ZStack {
            Circle()
                .fill(.ultraThinMaterial)
                .frame(width: 52, height: 52)
                .background(Color.white.opacity(0.66), in: Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.82), lineWidth: 1))
                .shadow(color: .black.opacity(0.14), radius: 16, x: 0, y: 8)

            Circle()
                .fill(Color.nugulPrimary.opacity(0.12))
                .frame(width: 42, height: 42)

            if let first = user?.displayName.first {
                Text(String(first))
                    .font(.system(size: 18, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
            } else {
                Image(systemName: "person.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.nugulPrimary)
            }
        }
    }
}

#if os(iOS)
private struct NugulKakaoMapView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let model: ZoneExplorerModel
    let onRegionChanged: (MKCoordinateRegion) -> Void
    let onRegionSettled: (MKCoordinateRegion) -> Void
    let onSelectZone: (SmokingZone) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let hostView = UIView(frame: .zero)
        hostView.backgroundColor = UIColor.nugulMapShell
        context.coordinator.parent = self
        context.coordinator.installIfNeeded(in: hostView)
        context.coordinator.syncState(animationEnabled: false)
        return hostView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.installIfNeeded(in: uiView)
        context.coordinator.syncState(animationEnabled: context.transaction.animation != nil)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.shutdown()
    }

    @MainActor
    final class Coordinator: NSObject, @preconcurrency MapControllerDelegate, @preconcurrency KakaoMapEventDelegate {
        private enum Constants {
            static let mapViewName = "nugul-kakao-map"
            static let appName = "openmap"
            static let viewInfoName = "map"
            static let labelLayerID = "nugul-smoking-zones"
            static let poiStyleID = "nugul-zone-marker"
            static let defaultKakaoLevel = 15
        }

        var parent: NugulKakaoMapView
        private weak var hostView: UIView?
        private var container: KMViewContainer?
        private var controller: KMController?
        private weak var kakaoMap: KakaoMap?
        private var labelLayer: LabelLayer?
        private var hasPreparedEngine = false
        private var hasRequestedMapView = false
        private var hasRegisteredPoiStyle = false
        private var renderedZoneSignature: Set<String> = []
        private var lastAppliedRegion: MKCoordinateRegion?
        private var isApplyingCameraFromSwiftUI = false
        private var eventHandlers: [any DisposableEventHandler] = []
        private var zonesCancellable: AnyCancellable?
        private var selectedCardView: UIView?
        private var boundModelID: ObjectIdentifier?
        private var didInstallQuietShell = false

        init(parent: NugulKakaoMapView) {
            self.parent = parent
        }

        func installIfNeeded(in hostView: UIView) {
            self.hostView = hostView

            guard container == nil else {
                hostView.bringSubviewToFront(container ?? UIView())
                return
            }

            guard let appKey = AppConfig.kakaoNativeAppKey else {
                installQuietShellIfNeeded(in: hostView)
                return
            }

            SDKInitializer.InitSDK(appKey: appKey)
            didInstallQuietShell = false
            hostView.subviews.forEach { $0.removeFromSuperview() }

            let initialFrame = hostView.bounds.isEmpty ? UIScreen.main.bounds : hostView.bounds
            let mapContainer = KMViewContainer(frame: initialFrame)
            mapContainer.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            mapContainer.backgroundColor = UIColor.nugulMapShell
            hostView.addSubview(mapContainer)

            let mapController = KMController(viewContainer: mapContainer)
            mapController.delegate = self
            mapController.proMotionSupport = true

            container = mapContainer
            controller = mapController
            hasPreparedEngine = false
            hasRequestedMapView = false

            startEngineIfReady()
        }

        func shutdown() {
            controller?.pauseEngine()
            controller?.resetEngine()
            controller?.delegate = nil
            kakaoMap?.eventDelegate = nil
            kakaoMap = nil
            eventHandlers.forEach { $0.dispose() }
            eventHandlers = []
            selectedCardView?.removeFromSuperview()
            selectedCardView = nil
            zonesCancellable = nil
            boundModelID = nil
            labelLayer = nil
            controller = nil
            container = nil
            hostView = nil
            hasPreparedEngine = false
            hasRequestedMapView = false
            hasRegisteredPoiStyle = false
            renderedZoneSignature = []
            lastAppliedRegion = nil
        }

        func syncState(animationEnabled: Bool) {
            if let hostView, let container {
                container.frame = hostView.bounds.isEmpty ? UIScreen.main.bounds : hostView.bounds
            }
            bindModelIfNeeded()
            startEngineIfReady()

            guard let kakaoMap else {
                return
            }

            syncPois(on: kakaoMap, zones: parent.model.zones)
            apply(region: parent.region, to: kakaoMap, animated: animationEnabled)
        }

        private func bindModelIfNeeded() {
            let modelID = ObjectIdentifier(parent.model)
            guard boundModelID != modelID else {
                return
            }

            boundModelID = modelID
            zonesCancellable = parent.model.$zones
                .receive(on: RunLoop.main)
                .sink { [weak self] zones in
                    Task { @MainActor [weak self] in
                        guard let self, let kakaoMap = self.kakaoMap else {
                            return
                        }

                        self.syncPois(on: kakaoMap, zones: zones)
                    }
                }
        }

        private func startEngineIfReady() {
            guard !hasPreparedEngine, let controller, let container else {
                return
            }

            if container.bounds.isEmpty {
                container.frame = UIScreen.main.bounds
            }

            hasPreparedEngine = true
            _ = controller.prepareEngine()
            controller.activateEngine()
        }

        func addViews() {
            guard !hasRequestedMapView, let controller else {
                return
            }

            hasRequestedMapView = true
            let defaultPoint = MapPoint(
                longitude: parent.region.center.longitude,
                latitude: parent.region.center.latitude
            )
            let viewInfo = MapviewInfo(
                viewName: Constants.mapViewName,
                appName: Constants.appName,
                viewInfoName: Constants.viewInfoName,
                defaultPosition: defaultPoint,
                defaultLevel: Constants.defaultKakaoLevel,
                enabled: true
            )
            if let size = container?.bounds.size, size.width > 0, size.height > 0 {
                controller.addView(viewInfo, viewSize: size)
            } else {
                controller.addView(viewInfo)
            }
        }

        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            guard viewName == Constants.mapViewName,
                  let mapView = controller?.getView(Constants.mapViewName) as? KakaoMap else {
                return
            }

            kakaoMap = mapView
            mapView.eventDelegate = self
            mapView.poiClickable = true
            mapView.setPoiEnabled(true)
            mapView.cameraAnimationEnabled = true
            installEventHandlers(on: mapView)
            configureLabels(on: mapView)
            syncState(animationEnabled: false)
            controller?.activateEngine()
        }

        func addViewFailed(_ viewName: String, viewInfoName: String) {
            installQuietShellIfNeeded(in: hostView)
        }

        func authenticationSucceeded() {
        }

        func authenticationFailed(_ errorCode: Int, desc: String) {
            installQuietShellIfNeeded(in: hostView)
        }

        func containerDidResized(_ size: CGSize) {
            guard size.width > 0, size.height > 0 else {
                return
            }

            syncState(animationEnabled: false)
        }

        private func installEventHandlers(on kakaoMap: KakaoMap) {
            eventHandlers.forEach { $0.dispose() }
            eventHandlers = [
                kakaoMap.addPoisTappedEventHandler(target: self) { target in
                    { param in
                        target.handlePoisTapped(param)
                    }
                },
                kakaoMap.addCameraStoppedEventHandler(target: self) { target in
                    { param in
                        target.handleCameraStopped(kakaoMap: param.view as? KakaoMap, by: param.by)
                    }
                }
            ]
        }

        private func handlePoisTapped(_ param: PoisInteractionEventParam) {
            handlePoiTap(layerID: param.layerID, poiID: param.poiID, position: param.position)
        }

        private func handlePoiTap(layerID: String, poiID: String, position: MapPoint) {
            guard layerID == Constants.labelLayerID else {
                return
            }

            let selectedZone: SmokingZone?
            if let zoneID = Int(poiID),
               let exactZone = parent.model.zones.first(where: { $0.id == zoneID }) {
                selectedZone = exactZone
            } else {
                let tappedCoordinate = position.wgsCoord
                selectedZone = parent.model.zones.min { lhs, rhs in
                    let lhsDistance = hypot(lhs.latitude - tappedCoordinate.latitude, lhs.longitude - tappedCoordinate.longitude)
                    let rhsDistance = hypot(rhs.latitude - tappedCoordinate.latitude, rhs.longitude - tappedCoordinate.longitude)
                    return lhsDistance < rhsDistance
                }
            }

            guard let selectedZone else {
                return
            }

            DispatchQueue.main.async { [weak self, selectedZone] in
                guard let self else {
                    return
                }

                self.showNativeCard(for: selectedZone)
                self.parent.onSelectZone(selectedZone)
            }
        }

        private func showNativeCard(for zone: SmokingZone) {
            guard let hostView else {
                return
            }

            selectedCardView?.removeFromSuperview()

            let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialLight))
            blur.translatesAutoresizingMaskIntoConstraints = false
            blur.layer.cornerRadius = 24
            blur.layer.masksToBounds = true
            blur.layer.borderWidth = 1
            blur.layer.borderColor = UIColor.white.withAlphaComponent(0.72).cgColor
            blur.layer.shadowColor = UIColor.black.cgColor
            blur.layer.shadowOpacity = 0.18
            blur.layer.shadowRadius = 22
            blur.layer.shadowOffset = CGSize(width: 0, height: 12)

            let icon = UIImageView(image: UIImage(systemName: "mappin.circle.fill"))
            icon.translatesAutoresizingMaskIntoConstraints = false
            icon.tintColor = UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 1)
            icon.contentMode = .scaleAspectFit

            let title = UILabel()
            title.translatesAutoresizingMaskIntoConstraints = false
            title.text = zone.title
            title.font = .systemFont(ofSize: 16, weight: .black)
            title.textColor = UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 1)
            title.numberOfLines = 1

            let subtitle = UILabel()
            subtitle.translatesAutoresizingMaskIntoConstraints = false
            subtitle.text = zone.address.isEmpty ? zone.summary : zone.address
            subtitle.font = .systemFont(ofSize: 12, weight: .semibold)
            subtitle.textColor = UIColor.darkGray.withAlphaComponent(0.78)
            subtitle.numberOfLines = 2

            let badge = UILabel()
            badge.translatesAutoresizingMaskIntoConstraints = false
            badge.text = zone.subtype.isEmpty ? zone.type : zone.subtype
            badge.font = .systemFont(ofSize: 10, weight: .black)
            badge.textColor = UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 1)
            badge.backgroundColor = UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 0.10)
            badge.layer.cornerRadius = 9
            badge.layer.masksToBounds = true
            badge.textAlignment = .center

            let closeButton = UIButton(type: .system)
            closeButton.translatesAutoresizingMaskIntoConstraints = false
            closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
            closeButton.tintColor = .darkGray
            closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.72)
            closeButton.layer.cornerRadius = 15
            closeButton.addAction(UIAction { [weak self] _ in
                self?.selectedCardView?.removeFromSuperview()
                self?.selectedCardView = nil
                self?.parent.model.selectedZone = nil
            }, for: .touchUpInside)

            let directionsButton = UIButton(type: .system)
            directionsButton.translatesAutoresizingMaskIntoConstraints = false
            directionsButton.setTitle("길찾기", for: .normal)
            directionsButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .black)
            directionsButton.tintColor = .white
            directionsButton.backgroundColor = UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 1)
            directionsButton.layer.cornerRadius = 13
            directionsButton.addAction(UIAction { _ in
                let encodedName = zone.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "NugulMap"
                if let url = URL(string: "http://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)") {
                    UIApplication.shared.open(url)
                }
            }, for: .touchUpInside)

            let textStack = UIStackView(arrangedSubviews: [title, subtitle])
            textStack.axis = .vertical
            textStack.spacing = 4
            textStack.translatesAutoresizingMaskIntoConstraints = false

            let headerStack = UIStackView(arrangedSubviews: [icon, textStack, badge, closeButton])
            headerStack.axis = .horizontal
            headerStack.alignment = .top
            headerStack.spacing = 10
            headerStack.translatesAutoresizingMaskIntoConstraints = false

            let contentStack = UIStackView(arrangedSubviews: [headerStack, directionsButton])
            contentStack.axis = .vertical
            contentStack.spacing = 12
            contentStack.translatesAutoresizingMaskIntoConstraints = false

            blur.contentView.addSubview(contentStack)
            hostView.addSubview(blur)
            selectedCardView = blur

            NSLayoutConstraint.activate([
                blur.leadingAnchor.constraint(equalTo: hostView.leadingAnchor, constant: 16),
                blur.trailingAnchor.constraint(equalTo: hostView.trailingAnchor, constant: -16),
                blur.bottomAnchor.constraint(equalTo: hostView.safeAreaLayoutGuide.bottomAnchor, constant: -86),

                contentStack.leadingAnchor.constraint(equalTo: blur.contentView.leadingAnchor, constant: 16),
                contentStack.trailingAnchor.constraint(equalTo: blur.contentView.trailingAnchor, constant: -16),
                contentStack.topAnchor.constraint(equalTo: blur.contentView.topAnchor, constant: 14),
                contentStack.bottomAnchor.constraint(equalTo: blur.contentView.bottomAnchor, constant: -14),

                icon.widthAnchor.constraint(equalToConstant: 38),
                icon.heightAnchor.constraint(equalToConstant: 38),
                closeButton.widthAnchor.constraint(equalToConstant: 30),
                closeButton.heightAnchor.constraint(equalToConstant: 30),
                badge.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
                badge.heightAnchor.constraint(equalToConstant: 24),
                directionsButton.heightAnchor.constraint(equalToConstant: 42)
            ])
        }

        private func handleCameraStopped(kakaoMap: KakaoMap?, by: MoveBy) {
            guard let kakaoMap else {
                return
            }

            cameraDidStopped(kakaoMap: kakaoMap, by: by)
        }

        func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, position: MapPoint) {
            handlePoiTap(layerID: layerID, poiID: poiID, position: position)
        }

        func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {}

        func cameraDidStopped(kakaoMap: KakaoMap, by: MoveBy) {
            guard let container else {
                return
            }

            let centerPoint = CGPoint(x: container.bounds.midX, y: container.bounds.midY)
            let center = kakaoMap.getPosition(centerPoint).wgsCoord
            let settledRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: center.latitude, longitude: center.longitude),
                span: parent.region.span
            )
            lastAppliedRegion = settledRegion
            parent.region = settledRegion
            parent.onRegionChanged(settledRegion)

            if isApplyingCameraFromSwiftUI {
                isApplyingCameraFromSwiftUI = false
                return
            }

            parent.onRegionSettled(settledRegion)
        }

        private func configureLabels(on kakaoMap: KakaoMap) {
            guard !hasRegisteredPoiStyle else {
                return
            }

            let manager = kakaoMap.getLabelManager()
            let markerImage = UIImage(named: "NugulMarker")?.resized(to: CGSize(width: 42, height: 42))
                ?? UIImage.nugulFallbackMarker(size: CGSize(width: 42, height: 42))
            let iconStyle = PoiIconStyle(
                symbol: markerImage,
                anchorPoint: CGPoint(x: 0.5, y: 1.0)
            )
            let perLevelStyle = PerLevelPoiStyle(iconStyle: iconStyle, padding: 0, level: 0)
            let style = PoiStyle(styleID: Constants.poiStyleID, styles: [perLevelStyle])
            manager.addPoiStyle(style)

            let layerOptions = LabelLayerOptions(
                layerID: Constants.labelLayerID,
                competitionType: .none,
                competitionUnit: .symbolFirst,
                orderType: .rank,
                zOrder: 10_000
            )
            let layer = manager.addLabelLayer(option: layerOptions)
            layer?.setClickable(true)
            labelLayer = layer
            hasRegisteredPoiStyle = true
        }

        private func syncPois(on kakaoMap: KakaoMap, zones: [SmokingZone]) {
            if !hasRegisteredPoiStyle {
                configureLabels(on: kakaoMap)
            }

            guard let labelLayer else {
                return
            }

            let nextSignature = Set(zones.map { zone in
                "\(zone.id):\(String(format: "%.6f", zone.latitude)):\(String(format: "%.6f", zone.longitude))"
            })
            guard nextSignature != renderedZoneSignature else {
                return
            }

            labelLayer.clearAllItems()
            for zone in zones {
                let option = PoiOptions(styleID: Constants.poiStyleID, poiID: "\(zone.id)")
                option.rank = 1_000
                option.clickable = true
                option.transformType = .default
                let poi = labelLayer.addPoi(
                    option: option,
                    at: MapPoint(longitude: zone.longitude, latitude: zone.latitude)
                )
                poi?.show()
            }
            renderedZoneSignature = nextSignature
        }

        private func apply(region: MKCoordinateRegion, to kakaoMap: KakaoMap, animated: Bool) {
            guard shouldApply(region) else {
                return
            }

            lastAppliedRegion = region
            isApplyingCameraFromSwiftUI = true
            let target = MapPoint(longitude: region.center.longitude, latitude: region.center.latitude)
            let update = CameraUpdate.make(
                target: target,
                zoomLevel: kakaoZoomLevel(for: region.span),
                mapView: kakaoMap
            )

            if animated {
                let options = CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 280)
                kakaoMap.animateCamera(cameraUpdate: update, options: options) { [weak self] in
                    self?.isApplyingCameraFromSwiftUI = false
                }
            } else {
                kakaoMap.moveCamera(update) { [weak self] in
                    self?.isApplyingCameraFromSwiftUI = false
                }
            }
        }

        private func shouldApply(_ target: MKCoordinateRegion) -> Bool {
            guard let current = lastAppliedRegion else {
                return true
            }

            return abs(target.center.latitude - current.center.latitude) > 0.0001 ||
                abs(target.center.longitude - current.center.longitude) > 0.0001 ||
                abs(target.span.latitudeDelta - current.span.latitudeDelta) > 0.0005 ||
                abs(target.span.longitudeDelta - current.span.longitudeDelta) > 0.0005
        }

        private func kakaoZoomLevel(for span: MKCoordinateSpan) -> Int {
            let delta = max(span.latitudeDelta, span.longitudeDelta)
            switch delta {
            case ..<0.01:
                return 18
            case ..<0.025:
                return 17
            case ..<0.05:
                return 16
            case ..<0.09:
                return 15
            case ..<0.16:
                return 14
            default:
                return 13
            }
        }

        private func installQuietShellIfNeeded(in hostView: UIView?) {
            guard let hostView, !didInstallQuietShell else {
                return
            }

            didInstallQuietShell = true
            hostView.subviews.forEach { $0.removeFromSuperview() }
            let shell = NugulQuietMapShell(frame: hostView.bounds)
            shell.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            hostView.addSubview(shell)
        }
    }
}

private final class NugulQuietMapShell: UIView {
    override class var layerClass: AnyClass {
        CAGradientLayer.self
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        guard let gradient = layer as? CAGradientLayer else {
            return
        }

        gradient.colors = [
            UIColor(red: 0.94, green: 0.96, blue: 0.93, alpha: 1).cgColor,
            UIColor(red: 0.82, green: 0.90, blue: 0.82, alpha: 1).cgColor,
            UIColor(red: 0.69, green: 0.80, blue: 0.70, alpha: 1).cgColor
        ]
        gradient.startPoint = CGPoint(x: 0.1, y: 0.0)
        gradient.endPoint = CGPoint(x: 1.0, y: 1.0)
    }
}

private extension UIColor {
    static let nugulMapShell = UIColor(red: 0.89, green: 0.93, blue: 0.88, alpha: 1)
}

private extension UIImage {
    func resized(to targetSize: CGSize) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    static func nugulFallbackMarker(size: CGSize) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.18, green: 0.38, blue: 0.21, alpha: 1).setFill()
            context.cgContext.fillEllipse(in: rect.insetBy(dx: 5, dy: 5))
            UIColor.white.setFill()
            context.cgContext.fillEllipse(in: rect.insetBy(dx: 14, dy: 14))
        }
    }
}
#endif

private extension View {
    @ViewBuilder
    func compactGlassSheet() -> some View {
        #if os(iOS)
        if #available(iOS 16.4, *) {
            self
                .presentationDragIndicator(.visible)
                .presentationBackground(.thinMaterial)
                .presentationCornerRadius(28)
        } else {
            self
                .presentationDragIndicator(.visible)
        }
        #else
        self
        #endif
    }
}



private struct MapTypeBadge: View {
    let zone: SmokingZone

    var body: some View {
        Text(zone.subtype.isEmpty ? zone.type : zone.subtype)
            .font(.system(size: 10, weight: .black))
            .foregroundStyle(Color.nugulPrimary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.nugulPrimary.opacity(0.08), in: Capsule())
    }
}

private struct SelectedZoneMapCard: View {
    let zone: SmokingZone
    let onClose: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.nugulPrimary.opacity(0.12))
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Color.nugulPrimary)
                }
                .frame(width: 42, height: 42)

                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(zone.title)
                            .font(.system(size: 17, weight: .black))
                            .foregroundStyle(Color.nugulPrimary)
                            .lineLimit(1)

                        MapTypeBadge(zone: zone)
                    }

                    Text(zone.address.isEmpty ? zone.summary : zone.address)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineLimit(2)

                    if !zone.summary.isEmpty {
                        Text(zone.summary)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.nugulMutedText.opacity(0.82))
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 8)

                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .black))
                        .foregroundStyle(Color.nugulMutedText)
                        .frame(width: 30, height: 30)
                        .background(Color.nugulMuted, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("선택한 흡연구역 닫기")
            }

            HStack(spacing: 10) {
                Button {
                    openDirections()
                } label: {
                    Label("길찾기", systemImage: "location.north.fill")
                        .font(.system(size: 14, weight: .black))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .foregroundStyle(.white)
                        .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                }
                .buttonStyle(.plain)

                Text(String(format: "%.5f, %.5f", zone.latitude, zone.longitude))
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.nugulMutedText)
                    .padding(.horizontal, 10)
                    .frame(height: 42)
                    .background(Color.nugulMuted, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .accessibilityLabel("좌표")
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .background(Color.white.opacity(0.86), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color.white.opacity(0.72), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.18), radius: 22, x: 0, y: 12)
    }

    private func openDirections() {
        let encodedName = zone.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "NugulMap"
        guard let url = URL(string: "http://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)") else {
            return
        }

        openURL(url)
    }
}

private struct WebToastBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
            Text(message)
                .font(.system(size: 12, weight: .bold))
                .lineLimit(2)
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(Color.white.opacity(0.94), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 7)
    }
}

private struct LoadingOverlay: View {
    var body: some View {
        ZStack {
            Color.white.opacity(0.42)
            ProgressView()
                .scaleEffect(1.2)
                .tint(Color.nugulPrimary)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

@MainActor
private final class LocationProvider: NSObject, ObservableObject, @MainActor CLLocationManagerDelegate {
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let manager = CLLocationManager()
    private var completion: ((CLLocationCoordinate2D) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func requestLocation(completion: @escaping (CLLocationCoordinate2D) -> Void) {
        self.completion = completion
        isLoading = true
        errorMessage = nil

        #if os(iOS)
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .restricted, .denied:
            isLoading = false
            errorMessage = "위치 권한을 허용했는지 확인해주세요."
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        @unknown default:
            isLoading = false
            errorMessage = "위치 권한 상태를 확인할 수 없습니다."
        }
        #else
        manager.requestLocation()
        #endif
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard isLoading else {
            return
        }

        #if os(iOS)
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestLocation()
        } else if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
            isLoading = false
            errorMessage = "위치 권한을 허용했는지 확인해주세요."
        }
        #else
        manager.requestLocation()
        #endif
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else {
            return
        }

        isLoading = false
        completion?(coordinate)
        completion = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        isLoading = false
        errorMessage = "위치 확인 실패"
        completion = nil
    }
}

private struct ReportZoneSheet: View {
    @ObservedObject var model: ZoneExplorerModel
    let coordinate: CLLocationCoordinate2D

    @Environment(\.dismiss) private var dismiss
    @State private var address = "위치 확인 중..."
    @State private var region = "서울특별시"
    @State private var selectedType = "부스"
    @State private var note = ""
    @State private var isAddressLoading = true
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoData: Data?

    private let zoneTypes: [ReportZoneType] = [
        ReportZoneType(id: "부스", icon: "building.2.fill"),
        ReportZoneType(id: "개방", icon: "tree.fill"),
        ReportZoneType(id: "실내", icon: "shippingbox.fill")
    ]

    var body: some View {
        let photoData = selectedPhotoData

        VStack(spacing: 0) {
            sheetHeader

            VStack(spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(isAddressLoading ? "확인 중..." : address)
                        .font(.system(size: 14, weight: .black))
                        .foregroundStyle(Color.nugulPrimary)
                        .lineLimit(1)
                    Spacer()
                }

                HStack(spacing: 12) {
                    HStack(spacing: 8) {
                        ForEach(zoneTypes) { type in
                            Button {
                                selectedType = type.id
                            } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: type.icon)
                                        .font(.system(size: 19, weight: .bold))
                                    Text(type.id)
                                        .font(.system(size: 9, weight: .black))
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .foregroundStyle(selectedType == type.id ? Color.nugulPrimary : Color.nugulMutedText)
                                .background(
                                    selectedType == type.id ? Color.nugulPrimary.opacity(0.06) : Color.white,
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(selectedType == type.id ? Color.nugulPrimary : Color.nugulBorder, lineWidth: selectedType == type.id ? 1.4 : 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        ReportPhotoSlot(data: photoData)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("사진 추가")
                }

                HStack(spacing: 8) {
                    TextField("팁 추가 (선택)", text: $note)
                        .font(.system(size: 13, weight: .medium))
                        .padding(.horizontal, 12)
                        .frame(height: 42)
                        .background(Color.nugulMuted.opacity(0.7), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    Button {
                        Task {
                            let submitted = await model.submitZone(
                                latitude: coordinate.latitude,
                                longitude: coordinate.longitude,
                                region: region,
                                subtype: selectedType,
                                address: address,
                                note: note,
                                image: selectedPhotoData.map { ImageAttachmentFactory.jpegAttachment(from: $0, filenamePrefix: "zone") }
                            )
                            if submitted || model.selectedZone != nil {
                                dismiss()
                            }
                        }
                    } label: {
                        if model.isSubmittingZone {
                            ProgressView()
                                .tint(.white)
                                .frame(width: 72, height: 42)
                        } else {
                            Text("등록")
                                .font(.system(size: 14, weight: .black))
                                .frame(width: 72, height: 42)
                        }
                    }
                    .foregroundStyle(.white)
                    .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .buttonStyle(.plain)
                    .disabled(model.isSubmittingZone || isAddressLoading)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 18)
        }
        .background(Color.white)
        .task(id: coordinateKey) {
            await resolveAddress()
        }
        .onChange(of: selectedPhotoItem) { _, newValue in
            Task {
                selectedPhotoData = try? await newValue?.loadTransferable(type: Data.self)
            }
        }
    }

    private var sheetHeader: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                    .frame(width: 40, height: 40)
                    .background(Color.white, in: Circle())
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                Text("흡연구역 등록")
                    .font(.system(size: 17, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                Text("핀을 정확한 위치에 맞춰주세요.")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText)
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.nugulBorder)
                .frame(height: 1)
        }
    }

    private var coordinateKey: String {
        "\(coordinate.latitude)-\(coordinate.longitude)"
    }

    private func resolveAddress() async {
        isAddressLoading = true
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)

        do {
            let placemarks = try await CLGeocoder().reverseGeocodeLocation(location, preferredLocale: Locale(identifier: "ko_KR"))
            if let placemark = placemarks.first {
                address = [
                    placemark.administrativeArea,
                    placemark.locality,
                    placemark.subLocality,
                    placemark.thoroughfare,
                    placemark.subThoroughfare
                ]
                .compactMap { $0 }
                .joined(separator: " ")
                region = placemark.administrativeArea ?? "서울특별시"
            } else {
                address = String(format: "선택 위치 %.5f, %.5f", coordinate.latitude, coordinate.longitude)
                region = "서울특별시"
            }
        } catch {
            address = String(format: "선택 위치 %.5f, %.5f", coordinate.latitude, coordinate.longitude)
            region = "서울특별시"
        }

        isAddressLoading = false
    }
}

private struct ReportZoneType: Identifiable {
    let id: String
    let icon: String
}

private struct SelectedPhotoPreview: View {
    let data: Data

    var body: some View {
        #if os(iOS)
        if let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
        } else {
            Image(systemName: "photo")
                .foregroundStyle(Color.nugulMutedText.opacity(0.5))
        }
        #elseif os(macOS)
        if let image = NSImage(data: data) {
            Image(nsImage: image)
                .resizable()
                .scaledToFill()
        } else {
            Image(systemName: "photo")
                .foregroundStyle(Color.nugulMutedText.opacity(0.5))
        }
        #else
        Image(systemName: "photo")
            .foregroundStyle(Color.nugulMutedText.opacity(0.5))
        #endif
    }
}

private struct ReportPhotoSlot: View {
    let data: Data?

    var body: some View {
        ZStack {
            if let data {
                SelectedPhotoPreview(data: data)
            } else {
                Image(systemName: "camera.fill")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText.opacity(0.58))
            }
        }
        .frame(width: 56, height: 52)
        .background(Color.nugulMuted.opacity(0.55), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(
                    data == nil ? Color.nugulMutedText.opacity(0.24) : Color.nugulPrimary,
                    style: StrokeStyle(lineWidth: 1.4, dash: data == nil ? [4, 4] : [])
                )
        )
    }
}

private struct ProfilePhotoSlot: View {
    let data: Data?

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.nugulMuted)
                .frame(width: 110, height: 110)

            if let data {
                SelectedPhotoPreview(data: data)
                    .frame(width: 110, height: 110)
                    .clipShape(Circle())
            } else {
                Image(systemName: "camera.fill")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText.opacity(0.5))
            }
        }
    }
}

private enum ImageAttachmentFactory {
    static func jpegAttachment(from data: Data, filenamePrefix: String) -> ZoneImageAttachment {
        let optimizedData: Data

        #if os(iOS)
        optimizedData = optimizedJPEGData(from: data) ?? data
        #else
        optimizedData = data
        #endif

        return ZoneImageAttachment(
            data: optimizedData,
            filename: "\(filenamePrefix)-\(Int(Date().timeIntervalSince1970)).jpg",
            mimeType: "image/jpeg"
        )
    }

    #if os(iOS)
    private static func optimizedJPEGData(from data: Data) -> Data? {
        guard let image = UIImage(data: data) else {
            return nil
        }

        let pixelWidth = CGFloat(image.cgImage?.width ?? Int(image.size.width * image.scale))
        let pixelHeight = CGFloat(image.cgImage?.height ?? Int(image.size.height * image.scale))
        let maxSide: CGFloat = 1280
        let scale = min(1, maxSide / max(pixelWidth, pixelHeight))
        let targetSize = CGSize(width: pixelWidth * scale, height: pixelHeight * scale)

        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.jpegData(withCompressionQuality: 0.8) { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
    #endif
}

private struct LoginSheet: View {
    @ObservedObject var model: ZoneExplorerModel

    var body: some View {
        ZStack {
            Color.nugulBackground.ignoresSafeArea()

            VStack(spacing: 26) {
                VStack(spacing: 18) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .fill(Color.nugulPrimary)
                            .frame(width: 96, height: 96)
                            .rotationEffect(.degrees(3))
                            .shadow(color: .black.opacity(0.20), radius: 20, x: 0, y: 14)

                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 46, weight: .black))
                            .foregroundStyle(.white)
                    }

                    VStack(spacing: 6) {
                        Text("NugulMap")
                            .font(.system(size: 46, weight: .black, design: .rounded))
                            .foregroundStyle(Color.nugulPrimary)
                        Text("대한민국 모든 너구리들의 쉼터")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(Color.nugulMutedText)
                    }
                }

                VStack(spacing: 12) {
                    socialButton(title: "카카오로 3초만에 시작", color: Color(red: 1.0, green: 0.90, blue: 0.0), foreground: Color(red: 0.24, green: 0.12, blue: 0.12), icon: "message.fill", provider: .kakao)
                    socialButton(title: "네이버로 로그인", color: Color(red: 0.01, green: 0.78, blue: 0.35), foreground: .white, iconText: "N", provider: .naver)
                    socialButton(title: "구글로 로그인", color: .white, foreground: Color.nugulPrimary, icon: "g.circle.fill", provider: .google, needsBorder: true)
                }

                if model.isAuthLoading {
                    ProgressView("로그인 처리 중")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText)
                }

                Text("By continuing, you agree to our\nTerms of Service & Privacy Policy")
                    .font(.system(size: 10, weight: .black))
                    .multilineTextAlignment(.center)
                    .textCase(.uppercase)
                    .foregroundStyle(Color.nugulMutedText.opacity(0.62))
                    .tracking(1.2)
            }
            .padding(28)
        }
    }

    private func socialButton(
        title: String,
        color: Color,
        foreground: Color,
        icon: String? = nil,
        iconText: String? = nil,
        provider: OAuthProvider,
        needsBorder: Bool = false
    ) -> some View {
        Button {
            Task {
                await model.signIn(with: provider)
            }
        } label: {
            HStack(spacing: 12) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 21, weight: .black))
                } else if let iconText {
                    Text(iconText)
                        .font(.system(size: 17, weight: .black))
                        .frame(width: 25, height: 25)
                        .foregroundStyle(color)
                        .background(foreground, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }

                Text(title)
                    .font(.system(size: 16, weight: .black))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 58)
            .foregroundStyle(foreground)
            .background(color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.nugulBorder, lineWidth: needsBorder ? 1.5 : 0)
            )
            .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 8)
        }
        .buttonStyle(.plain)
        .disabled(model.isAuthLoading)
    }
}

private struct ProfileSheet: View {
    @ObservedObject var model: ZoneExplorerModel
    @State private var selectedTab: ProfileTab = .profile

    var body: some View {
        VStack(spacing: 18) {
            header

            Picker("프로필 메뉴", selection: $selectedTab) {
                ForEach(ProfileTab.allCases) { tab in
                    Text(tab.title).tag(tab)
                }
            }
            .pickerStyle(.segmented)

            if selectedTab == .profile {
                profileContent
            } else {
                myZonesContent
            }

            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .background(Color.white)
        .task {
            await model.loadUserZones()
        }
    }

    private var header: some View {
        HStack(spacing: 18) {
            ProfileAvatar(user: model.currentUser)
                .scaleEffect(1.45)
                .frame(width: 72, height: 72)

            VStack(alignment: .leading, spacing: 5) {
                Text(model.currentUser?.displayName ?? "로그인이 필요합니다.")
                    .font(.system(size: 24, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                Text(model.currentUser?.email ?? "다시 로그인해주세요.")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .lineLimit(1)
            }

            Spacer()
        }
    }

    private var profileContent: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                SummaryPill(icon: "calendar", value: joinedDateText, label: "가입")
                SummaryPill(icon: "mappin.and.ellipse", value: "\(model.userZones.count)", label: "장소 제보")
            }

            VStack(spacing: 10) {
                ProfileField(title: "이메일 계정", value: model.currentUser?.email ?? "-")
                ProfileField(title: "닉네임", value: model.currentUser?.displayName ?? "-")
            }

            Button {
                model.logout()
            } label: {
                HStack {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                    Text("로그아웃")
                }
                .font(.system(size: 15, weight: .black))
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.red.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    private var myZonesContent: some View {
        Group {
            if model.isLoadingUserZones {
                ProgressView("내 장소 불러오는 중")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if model.userZones.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "mappin.slash")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText.opacity(0.35))
                    Text("아직 제보한 장소가 없습니다.")
                        .font(.system(size: 15, weight: .black))
                        .foregroundStyle(Color.nugulMutedText)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(model.userZones) { zone in
                            MyZoneCard(zone: zone, model: model)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private var joinedDateText: String {
        guard let createdAt = model.currentUser?.createdAt, !createdAt.isEmpty else {
            return "-"
        }

        return String(createdAt.prefix(10))
    }
}

private enum ProfileTab: String, CaseIterable, Identifiable {
    case profile
    case zones

    var id: String { rawValue }

    var title: String {
        switch self {
        case .profile:
            return "내 정보"
        case .zones:
            return "내가 등록한 장소"
        }
    }
}

private struct ProfileSetupSheet: View {
    @ObservedObject var model: ZoneExplorerModel
    @Environment(\.dismiss) private var dismiss
    @State private var nickname = ""
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedPhotoData: Data?

    var body: some View {
        let photoData = selectedPhotoData

        VStack(spacing: 22) {
            VStack(spacing: 8) {
                Text("프로필 설정")
                    .font(.system(size: 28, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                Text(model.pendingSignupEmail ?? "닉네임을 설정하면 가입이 완료됩니다.")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText)
            }
            .padding(.top, 24)

            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                ProfilePhotoSlot(data: photoData)
            }
            .buttonStyle(.plain)

            TextField("닉네임", text: $nickname)
                .font(.system(size: 16, weight: .bold))
                .padding(.horizontal, 14)
                .frame(height: 52)
                .background(Color.nugulMuted.opacity(0.75), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            Button {
                Task {
                    let image = selectedPhotoData.map {
                        ImageAttachmentFactory.jpegAttachment(from: $0, filenamePrefix: "profile")
                    }

                    if await model.completeProfileSetup(nickname: nickname, image: image) {
                        dismiss()
                    }
                }
            } label: {
                if model.isAuthLoading {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                } else {
                    Text("가입 완료")
                        .font(.system(size: 16, weight: .black))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                }
            }
            .foregroundStyle(.white)
            .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .buttonStyle(.plain)
            .disabled(model.isAuthLoading)

            Spacer()
        }
        .padding(.horizontal, 26)
        .background(Color.white)
        .onChange(of: selectedPhotoItem) { _, newValue in
            Task {
                selectedPhotoData = try? await newValue?.loadTransferable(type: Data.self)
            }
        }
    }
}

private struct SummaryPill: View {
    let icon: String
    let value: String
    let label: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
            VStack(alignment: .leading, spacing: 1) {
                Text(value)
                    .font(.system(size: 13, weight: .black))
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.nugulMuted.opacity(0.75), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct ProfileField: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 10, weight: .black))
                .foregroundStyle(Color.nugulMutedText)
            Text(value)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.nugulPrimary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.nugulMuted.opacity(0.65), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct MyZoneCard: View {
    let zone: SmokingZone
    @ObservedObject var model: ZoneExplorerModel

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.nugulMuted)
                if let imageURL = model.imageURL(for: zone) {
                    AsyncImage(url: imageURL) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        Image(systemName: "mappin")
                            .foregroundStyle(Color.nugulMutedText.opacity(0.3))
                    }
                } else {
                    Image(systemName: "mappin")
                        .foregroundStyle(Color.nugulMutedText.opacity(0.3))
                }
            }
            .frame(width: 82, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 5) {
                Text(zone.address)
                    .font(.system(size: 14, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                    .lineLimit(2)
                Text(zone.description.isEmpty ? "상세 설명이 없습니다." : zone.description)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.nugulMutedText)
                    .lineLimit(1)
                Text(zone.subtype.isEmpty ? zone.type : zone.subtype)
                    .font(.system(size: 10, weight: .black))
                    .foregroundStyle(Color.nugulMutedText.opacity(0.75))
            }

            Spacer()

            Button {
                Task {
                    await model.deleteUserZone(zone)
                }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .black))
                    .foregroundStyle(.red)
                    .frame(width: 32, height: 32)
                    .background(Color.red.opacity(0.08), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.nugulBorder, lineWidth: 1)
        )
    }
}

private extension Color {
    static let nugulBackground = Color(red: 1.0, green: 1.0, blue: 1.0)
    static let nugulPrimary = Color(red: 0.09, green: 0.09, blue: 0.09)
    static let nugulMuted = Color(red: 0.96, green: 0.96, blue: 0.96)
    static let nugulMutedText = Color(red: 0.32, green: 0.32, blue: 0.32)
    static let nugulBorder = Color(red: 0.90, green: 0.90, blue: 0.90)
}
