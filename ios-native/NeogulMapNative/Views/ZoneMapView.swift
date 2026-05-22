import CoreLocation
import MapKit
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

    var body: some View {
        ZStack {
            mapLayer

            VStack(spacing: 0) {
                topHeader
                Spacer()
                bottomControls
            }

            if model.isLoading {
                LoadingOverlay()
            }
        }
        .background(Color.nugulBackground)
        .ignoresSafeArea()
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case let .zone(zone):
                ZoneDetailView(zone: zone, model: model)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            case .report:
                ReportZoneSheet(model: model, coordinate: cameraCenter)
                    .presentationDetents([.height(270), .medium])
                    .presentationDragIndicator(.visible)
            case .login:
                LoginSheet(model: model)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            case .profile:
                ProfileSheet(model: model)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            case .profileSetup:
                ProfileSetupSheet(model: model)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
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
        NugulMapKitView(
            region: $mapRegion,
            zones: model.zones,
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
                activeSheet = .zone(zone)
            }
        )
        .ignoresSafeArea()
    }

    private var topHeader: some View {
        VStack(spacing: 0) {
            Color.white.opacity(0.82)
                .frame(height: 0)
                .background(.ultraThinMaterial)

            HStack(spacing: 12) {
                WebStyleSearchBar(
                    query: $model.query,
                    isLoading: model.isLoading,
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

                Button {
                    activeSheet = model.currentUser == nil ? .login : .profile
                } label: {
                    ProfileAvatar(user: model.currentUser)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(model.currentUser == nil ? "로그인" : "프로필")
            }
            .frame(height: 64)
            .padding(.horizontal, 16)
            .background(.ultraThinMaterial)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(Color.nugulBorder)
                    .frame(height: 1)
            }
        }
        .padding(.top, safeAreaTop)
        .background(.ultraThinMaterial)
    }

    private var bottomControls: some View {
        VStack(spacing: 10) {
            if let message = model.errorMessage ?? locationProvider.errorMessage {
                WebToastBanner(message: message)
                    .padding(.horizontal, 16)
            }

            HStack(alignment: .bottom) {
                Button {
                    locationProvider.requestLocation { coordinate in
                        centerMap(on: coordinate)
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(Color.black.opacity(0.24))
                            .overlay(Circle().stroke(Color.white, lineWidth: 2))
                            .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 8)

                        if locationProvider.isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 21, weight: .bold))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 52, height: 52)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("현재 위치")

                Spacer()

                Button {
                    activeSheet = model.currentUser == nil ? .login : .report
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .black))
                        Text("제보하기")
                            .font(.system(size: 16, weight: .black))
                    }
                    .foregroundStyle(.white)
                    .frame(height: 56)
                    .padding(.horizontal, 18)
                    .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: .black.opacity(0.22), radius: 18, x: 0, y: 10)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("흡연구역 제보하기")
            }
            .padding(.horizontal, 24)
        }
        .padding(.bottom, safeAreaBottom + 34)
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
    case zone(SmokingZone)
    case report
    case login
    case profile
    case profileSetup

    var id: String {
        switch self {
        case let .zone(zone):
            return "zone-\(zone.id)"
        case .report:
            return "report"
        case .login:
            return "login"
        case .profile:
            return "profile"
        case .profileSetup:
            return "profile-setup"
        }
    }
}

private extension MKCoordinateRegion {
    static let centralSeoul = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780),
        span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
    )
}

private struct WebStyleSearchBar: View {
    @Binding var query: String
    let isLoading: Bool
    let onClear: () -> Void
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: 10) {
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
        .background(Color.white.opacity(0.95), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.nugulBorder, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.12), radius: 14, x: 0, y: 8)
    }
}

private struct ProfileAvatar: View {
    let user: UserProfile?

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.86))
                .frame(width: 48, height: 48)
                .overlay(Circle().stroke(Color.nugulBorder.opacity(0.7), lineWidth: 1))
                .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 7)

            Circle()
                .fill(Color.nugulPrimary.opacity(0.1))
                .frame(width: 40, height: 40)

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
private struct NugulMapKitView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let zones: [SmokingZone]
    let onRegionChanged: (MKCoordinateRegion) -> Void
    let onRegionSettled: (MKCoordinateRegion) -> Void
    let onSelectZone: (SmokingZone) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView(frame: .zero)
        mapView.delegate = context.coordinator
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = true
        mapView.showsScale = true
        mapView.register(MKAnnotationView.self, forAnnotationViewWithReuseIdentifier: Coordinator.zoneReuseIdentifier)
        mapView.register(MKMarkerAnnotationView.self, forAnnotationViewWithReuseIdentifier: Coordinator.clusterReuseIdentifier)
        context.coordinator.isApplyingRegionFromSwiftUI = true
        mapView.setRegion(region, animated: false)
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.syncAnnotations(zones, on: mapView)

        if context.coordinator.shouldApply(region, to: mapView.region) {
            context.coordinator.isApplyingRegionFromSwiftUI = true
            mapView.setRegion(region, animated: context.transaction.animation != nil)
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        static let zoneReuseIdentifier = "NugulZoneAnnotation"
        static let clusterReuseIdentifier = "NugulZoneClusterAnnotation"

        var parent: NugulMapKitView
        var renderedZoneIDs: Set<Int> = []
        var isApplyingRegionFromSwiftUI = false

        init(parent: NugulMapKitView) {
            self.parent = parent
        }

        func syncAnnotations(_ zones: [SmokingZone], on mapView: MKMapView) {
            let nextIDs = Set(zones.map(\.id))
            guard nextIDs != renderedZoneIDs else {
                return
            }

            let existingZoneAnnotations = mapView.annotations.compactMap { $0 as? ZonePointAnnotation }
            mapView.removeAnnotations(existingZoneAnnotations)
            mapView.addAnnotations(zones.map(ZonePointAnnotation.init))
            renderedZoneIDs = nextIDs
        }

        func shouldApply(_ target: MKCoordinateRegion, to current: MKCoordinateRegion) -> Bool {
            abs(target.center.latitude - current.center.latitude) > 0.0001 ||
                abs(target.center.longitude - current.center.longitude) > 0.0001 ||
                abs(target.span.latitudeDelta - current.span.latitudeDelta) > 0.0001 ||
                abs(target.span.longitudeDelta - current.span.longitudeDelta) > 0.0001
        }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            if isApplyingRegionFromSwiftUI {
                isApplyingRegionFromSwiftUI = false
                return
            }

            parent.region = mapView.region
            parent.onRegionChanged(mapView.region)
            parent.onRegionSettled(mapView.region)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if let cluster = annotation as? MKClusterAnnotation {
                let view = mapView.dequeueReusableAnnotationView(
                    withIdentifier: Self.clusterReuseIdentifier,
                    for: cluster
                ) as? MKMarkerAnnotationView ?? MKMarkerAnnotationView(annotation: cluster, reuseIdentifier: Self.clusterReuseIdentifier)

                view.annotation = cluster
                view.markerTintColor = .black
                view.glyphTintColor = .white
                view.glyphText = "\(cluster.memberAnnotations.count)"
                view.displayPriority = .required
                return view
            }

            guard let zoneAnnotation = annotation as? ZonePointAnnotation else {
                return nil
            }

            let view = mapView.dequeueReusableAnnotationView(
                withIdentifier: Self.zoneReuseIdentifier,
                for: zoneAnnotation
            )
            view.annotation = zoneAnnotation
            let markerImage = UIImage(named: "NugulMarker")
            view.image = markerImage?.resized(to: CGSize(width: 42, height: 42))
            view.centerOffset = CGPoint(x: 0, y: -21)
            view.canShowCallout = false
            view.clusteringIdentifier = nil
            view.displayPriority = .required
            view.collisionMode = .none
            return view
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            if let cluster = view.annotation as? MKClusterAnnotation {
                mapView.showAnnotations(cluster.memberAnnotations, animated: true)
                mapView.deselectAnnotation(cluster, animated: false)
                return
            }

            guard let annotation = view.annotation as? ZonePointAnnotation else {
                return
            }

            parent.onSelectZone(annotation.zone)
            mapView.deselectAnnotation(annotation, animated: false)
        }
    }
}

private final class ZonePointAnnotation: NSObject, MKAnnotation {
    let zone: SmokingZone

    var coordinate: CLLocationCoordinate2D {
        zone.coordinate
    }

    var title: String? {
        zone.title
    }

    init(zone: SmokingZone) {
        self.zone = zone
        super.init()
    }
}

private extension UIImage {
    func resized(to targetSize: CGSize) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
}
#endif

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
