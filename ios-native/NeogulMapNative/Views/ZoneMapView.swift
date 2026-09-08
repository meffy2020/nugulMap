import Combine
import AuthenticationServices
import AudioToolbox
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
    @Environment(\.accessibilityReduceMotion) private var accessibilityReduceMotion
    @StateObject private var locationProvider = LocationProvider()
    @State private var mapRegion: MKCoordinateRegion = .centralSeoul
    @State private var cameraCenter = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780)
    @State private var activeSheet: HomeSheet?
    @State private var locationLetterAnchor: LocationLetterAnchor?
    @State private var didBootstrap = false
    @State private var layerMode: Season2LayerMode = .zones
    @State private var selectedRecommendationIntent: RecommendationIntent = .cafe
    @State private var selectedInsight: Season3InsightSelection?
    @State private var broadEventSearchKeyword: String?
    @State private var didApplyInitialSearchRoute = false
    @State private var showsInitialSearchDetail = false
    @State private var initialSearchInsightItem: Season3InsightItem?
    @State private var pickerHistory = MapPickerHistory()
    @State private var isRollingPickerDice = false
    @State private var pickerDrawTask: Task<Void, Never>?
    @State private var pickerPhase: DrawPhase = .idle
    @State private var pickerPreviewTitle: String?
    @State private var pickerCandidateTrail: [MapPickerAnimationItem] = []
    @State private var pickerDiceState = DrawDiceAnimationState()
    @State private var mapImpactOffset: CGSize = .zero
    @State private var mapImpactRotation = 0.0

    init(model: ZoneExplorerModel) {
        self.model = model
        _layerMode = State(initialValue: .zones)
    }

    private var presentedLayerMode: Season2LayerMode { .zones }

    private var visibleZones: [SmokingZone] {
        presentedLayerMode.showsZones ? model.zones : []
    }

    private var visibleHotplaces: [Hotplace] {
        presentedLayerMode.showsHotplaces ? model.hotplaceInsight.places : []
    }

    private var visibleEvents: [TrendEvent] {
        guard presentedLayerMode.showsEvents else {
            return []
        }
        return model.eventInsight.events.filter {
            $0.isEligibleForSeason3EventSearch(
                allowsNonPopupEvents: broadEventSearchKeyword != nil
            )
        }
    }

    private var visibleRecommendations: [RecommendationPlace] {
        guard presentedLayerMode == .pero else {
            return []
        }
        return Array(model.visibleRecommendations.prefix(8))
    }

    private var currentMapPickerCandidates: [MapPickerCandidate] {
        let layerCandidates: [MapPickerCandidate]
        switch presentedLayerMode {
        case .zones, .hotplaces, .events:
            layerCandidates = []
        case .pero:
            layerCandidates = visibleRecommendations.map {
                MapPickerCandidate(
                    key: "recommendation:\($0.stableKey)",
                    latitude: $0.latitude,
                    longitude: $0.longitude
                )
            }
        }

        let viewport = MapPickerViewport(
            centerLatitude: mapRegion.center.latitude,
            centerLongitude: mapRegion.center.longitude,
            latitudeDelta: mapRegion.span.latitudeDelta,
            longitudeDelta: mapRegion.span.longitudeDelta
        )
        return MapDicePicker.visibleCandidates(layerCandidates, in: viewport)
    }

    var body: some View {
        ZStack {
            mapLayer
                .offset(mapImpactOffset)
                .rotationEffect(.degrees(mapImpactRotation))
                .scaleEffect(isRollingPickerDice && !accessibilityReduceMotion ? 1.004 : 1)
                .allowsHitTesting(!isRollingPickerDice)
                .overlay(alignment: .top) {
                    if !model.isPresentingLocationLetters {
                        VStack(spacing: 6) {
                            topHeader

                        }
                        .padding(.horizontal, 12)
                        .padding(.top, safeAreaTop + 8)
                        .disabled(isRollingPickerDice)
                    }
                }
                .overlay(alignment: .bottom) {
                    if !model.isPresentingLocationLetters {
                        bottomControls
                            .disabled(isRollingPickerDice)
                    }
                }

            if presentedLayerMode == .pero,
               isRollingPickerDice || pickerPhase != .idle {
                MapPickerDrawExperienceOverlay(
                    phase: pickerPhase,
                    previewTitle: pickerPreviewTitle,
                    candidateTrail: pickerCandidateTrail,
                    modeTitle: presentedLayerMode.title,
                    diceState: pickerDiceState,
                    reduceMotion: accessibilityReduceMotion
                )
                .allowsHitTesting(false)
                .transition(
                    .opacity.combined(
                        with: .scale(scale: accessibilityReduceMotion ? 1 : 0.94)
                    )
                )
            }

            if model.isLoading {
                LoadingOverlay()
            }
            ZoneDetailPresenter(model: model)

        }
        .background(Color.nugulBackground)
        .ignoresSafeArea()
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .report:
                ReportZoneSheet(model: model, coordinate: cameraCenter)
                    .presentationDetents([.height(340), .medium, .large])
                    .compactGlassSheet()
            case .login:
                LoginSheet(model: model)
                    .presentationDetents([.large])
                    .compactGlassSheet()
            case .profile:
                ProfileSheet(model: model)
                    .presentationDetents([.large])
                    .compactGlassSheet()
            case .profileSetup:
                ProfileSetupSheet(model: model)
                    .presentationDetents([.large])
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
            if layerMode == .pero {
                await loadRecommendations(in: mapRegion, force: true)
            }
            let appliedInitialSearch = await applyInitialSearchRouteIfNeeded()
            if !appliedInitialSearch {
                handlePendingIntentRoute()
            }
        }
        .onChange(of: scenePhase) { _, newValue in
            if newValue == .active {
                handlePendingIntentRoute()
                Task {
                    await model.validateAppleCredentialStateIfNeeded()
                    if presentedLayerMode.requiresInsights {
                        await refreshInsights()
                    }
                }
            }
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
        .onDisappear {
            pickerDrawTask?.cancel()
        }
    }

    private var mapLayer: some View {
        NugulKakaoMapView(
            region: $mapRegion,
            model: model,
            isSceneActive: scenePhase != .background,
            layerMode: presentedLayerMode,
            zones: visibleZones,
            hotplaces: visibleHotplaces,
            events: visibleEvents,
            recommendations: visibleRecommendations,
            onRegionChanged: { region in
                cameraCenter = region.center
            },
            onRegionSettled: { region in
                Task {
                    await model.loadZones(in: .visibleRegion(region))
                }
            },
            onSelectZone: { zone in
                withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) {
                    selectedInsight = nil
                    showsInitialSearchDetail = false
                    initialSearchInsightItem = nil
                    model.select(zone)
                }
            },
            onSelectHotplace: { place in
                selectHotplace(place, moveCamera: false)
            },
            onSelectEvent: { event in
                selectEvent(event, moveCamera: false)
            },
            onSelectRecommendation: { place in
                selectRecommendation(place, moveCamera: false)
            }
        )
        .ignoresSafeArea()
        .accessibilityRepresentation {
            MapMarkerAccessibilityList(
                model: model,
                mode: presentedLayerMode,
                zones: visibleZones,
                hotplaces: visibleHotplaces,
                events: visibleEvents,
                recommendations: visibleRecommendations,
                onSelectZone: { model.select($0) },
                onSelectHotplace: { selectHotplace($0, moveCamera: false) },
                onSelectEvent: { selectEvent($0, moveCamera: false) },
                onSelectRecommendation: { selectRecommendation($0, moveCamera: false) }
            )
        }
    }

    private var topHeader: some View {
        UnifiedSearchMenuBar(
            query: $model.query,
            isLoading: model.isLoading,
            onClear: {
                broadEventSearchKeyword = nil
                Task {
                    await model.resetSearch()
                    if layerMode == .events {
                        await loadInsightsForActiveLayer(
                            visibleBounds: .seoulCity,
                            force: true
                        )
                    }
                    centerOnFirstZoneIfPossible(reloadZones: false)
                }
            },
            onSearch: {
                Task {
                    prepareForSeason3SearchIfNeeded()
                    if let coordinate = await model.search() {
                        centerMap(on: coordinate, reloadZones: false)
                    } else {
                        centerOnFirstZoneIfPossible(reloadZones: false)
                    }
                }
            }
        )
    }

    private struct MapLayerControl: View {
        let activeMode: Season2LayerMode
        let selectedIntent: RecommendationIntent
        let personalizationMode: RecommendationPersonalizationMode?
        let canEnablePersonalization: Bool
        let onSelectMode: (Season2LayerMode) -> Void
        let onSelectIntent: (RecommendationIntent) -> Void
        let onEnablePersonalization: () -> Void
        let onDisablePersonalization: () -> Void
        @State private var showsPersonalizationConsent = false

        var body: some View {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if activeMode == .pero {
                        MapFilterChip(
                            title: Season2LayerMode.pero.filterTitle,
                            systemImage: Season2LayerMode.pero.filterIcon,
                            isSelected: true,
                            action: { onSelectMode(.pero) }
                        )
                        .accessibilityLabel("추천 보기")

                        ForEach(RecommendationIntent.allCases) { intent in
                            MapFilterChip(
                                title: intent.filterTitle,
                                systemImage: intent.filterIcon,
                                isSelected: selectedIntent == intent,
                                action: { onSelectIntent(intent) }
                            )
                            .accessibilityLabel("\(intent.filterTitle) 추천")
                        }

                        personalizationChip

                        Rectangle()
                            .fill(Color.black.opacity(0.12))
                            .frame(width: 1, height: 24)

                        ForEach([Season2LayerMode.zones, .hotplaces, .events]) { mode in
                            MapFilterChip(
                                title: mode.filterTitle,
                                systemImage: mode.filterIcon,
                                isSelected: false,
                                action: { onSelectMode(mode) }
                            )
                            .accessibilityLabel("\(mode.title) 보기")
                        }
                    } else {
                        ForEach(Season2LayerMode.filterOrder) { mode in
                            MapFilterChip(
                                title: mode.filterTitle,
                                systemImage: mode.filterIcon,
                                isSelected: activeMode == mode,
                                action: { onSelectMode(mode) }
                            )
                            .accessibilityLabel("\(mode.title) 보기")
                        }
                    }
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 4)
            }
            .scrollClipDisabled()
            .confirmationDialog("맞춤 추천을 켤까요?", isPresented: $showsPersonalizationConsent) {
                Button("동의하고 켜기", action: onEnablePersonalization)
                Button("취소", role: .cancel) {}
            } message: {
                Text("상세 보기, 저장, 길찾기, 관심 없음 행동을 추천 품질 개선에 사용합니다.")
            }
        }

        @ViewBuilder
        private var personalizationChip: some View {
            if canEnablePersonalization, personalizationMode == .contextOnly {
                MapFilterChip(
                    title: "맞춤",
                    systemImage: "person.crop.circle.badge.plus",
                    isSelected: false,
                    action: { showsPersonalizationConsent = true }
                )
                .accessibilityHint("추천 행동 기록에 동의하고 맞춤 추천을 켭니다")
            } else if canEnablePersonalization, personalizationMode == .personalized {
                MapFilterChip(
                    title: "맞춤 켬",
                    systemImage: "person.crop.circle.fill",
                    isSelected: true,
                    action: onDisablePersonalization
                )
                .accessibilityHint("추천 행동 기록을 중단하고 기본 추천으로 돌아갑니다")
            }
        }
    }

    private struct MapFilterChip: View {
        let title: String
        let systemImage: String?
        let isSelected: Bool
        let action: () -> Void

        var body: some View {
            Button(action: action) {
                HStack(spacing: 7) {
                    if let systemImage {
                        Image(systemName: systemImage)
                            .font(.system(size: 14, weight: .bold))
                    }
                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .lineLimit(1)
                }
                .foregroundStyle(isSelected ? Color.nugulPrimary : Color.nugulMutedText)
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(
                    isSelected ? Color.nugulMuted : Color.white.opacity(0.96),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(isSelected ? Color.black.opacity(0.28) : Color.black.opacity(0.08), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.11), radius: 7, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            .accessibilityValue(isSelected ? "선택됨" : "선택 안 됨")
        }
    }

    private var bottomControls: some View {
        MapBottomControls(
            model: model,
            locationProvider: locationProvider,
            layerMode: presentedLayerMode,
            selectedInsight: $selectedInsight,
            broadEventSearchKeyword: $broadEventSearchKeyword,
            showsInitialSearchDetail: $showsInitialSearchDetail,
            initialSearchInsightItem: $initialSearchInsightItem,
            pickerCandidateCount: currentMapPickerCandidates.count,
            isRollingPickerDice: isRollingPickerDice,
            pickerPhase: pickerPhase,
            safeAreaBottom: safeAreaBottom,
            onCenterLocation: { coordinate in
                centerMap(on: coordinate)
                model.setLocationErrorMessage("현재 위치로 이동했습니다.")
            },
            onOpenAccount: {
                activeSheet = model.currentUser == nil ? .login : .profile
            },
            onOpenUtility: { utility in
                switch utility {
                case .settings:
                    activeSheet = .settings

                }
            },
            onOpenReport: {
                activeSheet = model.currentUser == nil ? .login : .report
            },
            onPickInsight: {
                startInsightPicker()
            }
        )
    }

    private func requestCurrentLocationForLetters() {
        model.setLocationErrorMessage(nil)
        locationProvider.requestLocation(
            completion: { coordinate in
                centerMap(on: coordinate)
                presentLocationLetters(at: coordinate)
            },
            onFailure: { message in
                model.setLocationErrorMessage(message)
            }
        )
    }

    private func presentLocationLetters(at coordinate: CLLocationCoordinate2D) {
        model.setLocationLettersPresented(true)
        locationLetterAnchor = LocationLetterAnchor(coordinate: coordinate)
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
        await model.bootstrap(initialBounds: .seoulCity)
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
                let bounds = MapBounds.around(
                    latitude: coordinate.latitude,
                    longitude: coordinate.longitude
                )
                await model.loadZones(in: bounds)
                if presentedLayerMode.requiresInsights {
                    await loadInsightsForActiveLayer(visibleBounds: bounds)
                }
            }
        }
    }

    private func centerOnFirstZoneIfPossible(reloadZones: Bool = true) {
        guard let firstZone = model.zones.first else {
            return
        }

        centerMap(on: firstZone.coordinate, reloadZones: reloadZones)
    }

    @MainActor
    private func refreshInsights() async {
        await loadInsightsForActiveLayer(
            visibleBounds: .seoulCity,
            force: true
        )
    }

    @MainActor
    private func loadInsightsForActiveLayer(
        visibleBounds _: MapBounds,
        force: Bool = false
    ) async {
        switch presentedLayerMode {
        case .zones:
            return
        case .hotplaces:
            let intentKeyword: String? = {
                guard case let .crowd(keyword)? = Season3SearchIntent(query: model.query) else {
                    return nil
                }
                return keyword
            }()
            await model.loadInsights(
                in: .seoulCity,
                force: force,
                keyword: intentKeyword ?? "hot-now",
                lane: .hotplaces
            )
        case .events:
            if let broadEventSearchKeyword {
                await model.loadInsights(
                    in: .seoulCity,
                    force: force,
                    keyword: broadEventSearchKeyword,
                    lane: .events
                )
            } else {
                await model.loadInsights(
                    in: .seongsu,
                    force: force,
                    keyword: "성수 팝업",
                    lane: .events
                )
            }
        case .pero:
            await loadRecommendations(in: mapRegion, force: force)
        }
    }

    @MainActor
    private func loadRecommendations(
        in region: MKCoordinateRegion,
        force: Bool = false
    ) async {
        await model.loadRecommendations(
            intent: selectedRecommendationIntent,
            center: region.center,
            radiusMeters: recommendationRadiusMeters(for: region),
            force: force
        )
    }

    private func recommendationRadiusMeters(for region: MKCoordinateRegion) -> Int {
        let center = CLLocation(latitude: region.center.latitude, longitude: region.center.longitude)
        let north = CLLocation(
            latitude: region.center.latitude + region.span.latitudeDelta / 2,
            longitude: region.center.longitude
        )
        let east = CLLocation(
            latitude: region.center.latitude,
            longitude: region.center.longitude + region.span.longitudeDelta / 2
        )
        let visibleRadius = Int(ceil(max(center.distance(from: north), center.distance(from: east))))
        return min(max(visibleRadius, 100), 20_000)
    }

    private func selectRecommendationIntent(_ intent: RecommendationIntent) {
        guard selectedRecommendationIntent != intent else { return }
        selectedRecommendationIntent = intent
        selectedInsight = nil
        Task {
            await loadRecommendations(in: mapRegion, force: true)
        }
    }

    private func enableRecommendationPersonalization() {
        Task {
            guard await model.setRecommendationPersonalization(enabled: true) else { return }
            await loadRecommendations(in: mapRegion, force: true)
        }
    }

    private func disableRecommendationPersonalization() {
        Task {
            guard await model.setRecommendationPersonalization(enabled: false) else { return }
            await loadRecommendations(in: mapRegion, force: true)
        }
    }

    private func selectHotplace(_ place: Hotplace, moveCamera: Bool = true) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.88)) {
            model.selectedZone = nil
            model.dismissLatestSeason3SearchResult()
            initialSearchInsightItem = nil
            showsInitialSearchDetail = false
            selectedInsight = .hotplace(place.id)
        }
        if moveCamera {
            centerMap(
                on: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude),
                reloadZones: false
            )
        }
    }

    private func selectEvent(_ event: TrendEvent, moveCamera: Bool = true) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.88)) {
            model.selectedZone = nil
            model.dismissLatestSeason3SearchResult()
            initialSearchInsightItem = nil
            showsInitialSearchDetail = false
            selectedInsight = .event(event.id)
        }
        if moveCamera {
            centerMap(
                on: CLLocationCoordinate2D(latitude: event.latitude, longitude: event.longitude),
                reloadZones: false
            )
        }
    }

    private func selectRecommendation(_ place: RecommendationPlace, moveCamera: Bool = true) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.88)) {
            model.selectedZone = nil
            model.dismissLatestSeason3SearchResult()
            initialSearchInsightItem = nil
            showsInitialSearchDetail = false
            selectedInsight = .recommendation(place.stableKey)
        }
        Task {
            await model.recordRecommendationInteraction(.detailOpen, for: place)
        }
        if moveCamera {
            centerMap(
                on: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude),
                reloadZones: false
            )
        }
    }

    @MainActor
    private func startInsightPicker() {
        guard presentedLayerMode == .pero,
              !isRollingPickerDice,
              !model.isLoadingRecommendations,
              !currentMapPickerCandidates.isEmpty else {
            return
        }

        let candidatesAtRollStart = currentMapPickerCandidates
        guard let finalCandidate = MapDicePicker.pick(
            from: candidatesAtRollStart,
            history: pickerHistory
        ) else {
            return
        }
        let pool = candidatesAtRollStart.compactMap(animationItem(for:))
        guard let finalItem = pool.first(where: { $0.id == finalCandidate.key }) else {
            return
        }

        pickerDrawTask?.cancel()
        pickerDrawTask = Task { @MainActor in
            await animatePickerDraw(with: pool, finalItem: finalItem)
        }
    }

    @MainActor
    private func animatePickerDraw(
        with pool: [MapPickerAnimationItem],
        finalItem: MapPickerAnimationItem
    ) async {
        isRollingPickerDice = true
        pickerPreviewTitle = nil
        pickerCandidateTrail = []
        pickerDiceState = DrawDiceAnimationState()
        resetMapImpact()
        withAnimation(.snappy(duration: 0.18)) {
            model.dismissLatestSeason3SearchResult()
            selectedInsight = nil
            showsInitialSearchDetail = false
            initialSearchInsightItem = nil
        }

        let tickImpact = UIImpactFeedbackGenerator(style: .light)
        let lockImpact = UIImpactFeedbackGenerator(style: .medium)
        let success = UINotificationFeedbackGenerator()
        tickImpact.prepare()
        lockImpact.prepare()
        success.prepare()

        let run = drawRun(from: pool, finalItem: finalItem)
        let target = targetPoint(for: finalItem.candidate)

        withAnimation(.snappy(duration: accessibilityReduceMotion ? 0.12 : 0.18)) {
            pickerPhase = .scanning
            pickerPreviewTitle = "현재 범위 확인 중"
            pickerDiceState = DrawDiceAnimationState(
                normalizedPosition: CGPoint(x: 0.50, y: 0.46),
                rotation: -18,
                scale: 0.94,
                blur: 0,
                impact: 0
            )
        }
        playDrawTickSound()
        tickImpact.impactOccurred(intensity: 0.35)
        try? await Task.sleep(for: .milliseconds(accessibilityReduceMotion ? 120 : 220))

        guard !Task.isCancelled else {
            resetPickerDrawState()
            return
        }

        if accessibilityReduceMotion {
            withAnimation(.easeInOut(duration: 0.16)) {
                pickerPhase = .locking
                pickerPreviewTitle = finalItem.title
                pickerCandidateTrail = [finalItem]
                pickerDiceState.normalizedPosition = target
            }
            try? await Task.sleep(for: .milliseconds(180))
        } else {
            withAnimation(.snappy(duration: 0.12)) {
                pickerPhase = .shuffling
            }
            let intervals: [Int] = [56, 48, 44, 50, 58, 66, 74, 84, 98, 116, 140, 176, 230]
            let points = diceBouncePath(target: target, count: intervals.count)
            let shuffledRun = expandedDrawRun(from: run, targetCount: intervals.count)
            for (index, item) in shuffledRun.enumerated() {
                guard !Task.isCancelled else {
                    resetPickerDrawState()
                    return
                }
                let point = points[min(index, points.count - 1)]
                let isFinalStep = index == shuffledRun.indices.last
                withAnimation(
                    .interpolatingSpring(
                        stiffness: isFinalStep ? 210 : 360,
                        damping: isFinalStep ? 18 : 12
                    )
                ) {
                    pickerPreviewTitle = isFinalStep ? item.title : nil
                    pickerCandidateTrail = Array(shuffledRun.prefix(index + 1).suffix(3))
                    pickerDiceState = DrawDiceAnimationState(
                        normalizedPosition: point,
                        rotation: Double((index + 1) * 137),
                        scale: isFinalStep ? 1.10 : (index.isMultiple(of: 3) ? 1.03 : 0.96),
                        blur: isFinalStep ? 0 : CGFloat(max(0, 6 - index / 2)),
                        impact: isFinalStep ? 1 : 0.55
                    )
                }
                if isWallBounce(point) || isFinalStep {
                    triggerMapImpact(from: point, intensity: isFinalStep ? 1.15 : 0.72)
                    playDrawTickSound()
                    tickImpact.impactOccurred(intensity: isFinalStep ? 0.8 : 0.42)
                }
                try? await Task.sleep(
                    for: .milliseconds(intervals[min(index, intervals.count - 1)])
                )
            }
        }

        guard !Task.isCancelled else {
            resetPickerDrawState()
            return
        }

        withAnimation(.spring(response: 0.28, dampingFraction: 0.72)) {
            pickerPhase = .locking
            pickerPreviewTitle = finalItem.title
            pickerCandidateTrail = Array(pickerCandidateTrail.suffix(2)) + [finalItem]
            pickerDiceState.normalizedPosition = target
            pickerDiceState.rotation += 92
            pickerDiceState.scale = 1.18
            pickerDiceState.blur = 0
        }
        triggerMapImpact(from: target, intensity: 1.25)
        playDrawRevealSound()
        lockImpact.impactOccurred(intensity: 0.85)
        try? await Task.sleep(for: .milliseconds(accessibilityReduceMotion ? 120 : 260))

        guard !Task.isCancelled else {
            resetPickerDrawState()
            return
        }

        withAnimation(.snappy(duration: accessibilityReduceMotion ? 0.16 : 0.28)) {
            pickerHistory.record(finalItem.candidate.key)
            selectPickerCandidate(finalItem.candidate)
            pickerPhase = .revealed
            pickerDiceState.scale = 0.82
            pickerDiceState.impact = 0
        }
        playDrawRevealSound()
        success.notificationOccurred(.success)

        try? await Task.sleep(for: .milliseconds(accessibilityReduceMotion ? 120 : 520))
        withAnimation(.snappy(duration: 0.18)) {
            resetPickerDrawState()
        }
    }

    private func animationItem(for candidate: MapPickerCandidate) -> MapPickerAnimationItem? {
        if candidate.key.hasPrefix("recommendation:"),
           let place = visibleRecommendations
            .first(where: { "recommendation:\($0.stableKey)" == candidate.key }) {
            return MapPickerAnimationItem(
                candidate: candidate,
                title: place.name,
                badge: place.category.isEmpty ? selectedRecommendationIntent.title : place.category
            )
        }
        return nil
    }

    private func drawRun(
        from pool: [MapPickerAnimationItem],
        finalItem: MapPickerAnimationItem
    ) -> [MapPickerAnimationItem] {
        let count = min(max(pool.count, 1), 5)
        let leadingItems = pool
            .filter { $0.id != finalItem.id }
            .shuffled()
            .prefix(max(0, count - 1))
        return Array(leadingItems) + [finalItem]
    }

    private func expandedDrawRun(
        from run: [MapPickerAnimationItem],
        targetCount: Int
    ) -> [MapPickerAnimationItem] {
        guard !run.isEmpty else {
            return []
        }
        var expanded: [MapPickerAnimationItem] = []
        while expanded.count < targetCount {
            expanded.append(contentsOf: run.shuffled())
        }
        expanded = Array(expanded.prefix(targetCount))
        if let finalItem = run.last {
            expanded[expanded.count - 1] = finalItem
        }
        return expanded
    }

    private func targetPoint(for candidate: MapPickerCandidate) -> CGPoint {
        let viewport = MapPickerViewport(
            centerLatitude: mapRegion.center.latitude,
            centerLongitude: mapRegion.center.longitude,
            latitudeDelta: mapRegion.span.latitudeDelta,
            longitudeDelta: mapRegion.span.longitudeDelta
        )
        let longitudeRange = max(viewport.maxLongitude - viewport.minLongitude, 0.000_001)
        let latitudeRange = max(viewport.maxLatitude - viewport.minLatitude, 0.000_001)
        let x = (candidate.longitude - viewport.minLongitude) / longitudeRange
        let y = 1 - ((candidate.latitude - viewport.minLatitude) / latitudeRange)
        return CGPoint(
            x: min(max(x, 0.12), 0.88),
            y: min(max(y, 0.14), 0.78)
        )
    }

    private func diceBouncePath(target: CGPoint, count: Int) -> [CGPoint] {
        let wallPoints: [CGPoint] = [
            CGPoint(x: 0.18, y: 0.16),
            CGPoint(x: 0.84, y: 0.22),
            CGPoint(x: 0.76, y: 0.72),
            CGPoint(x: 0.12, y: 0.64),
            CGPoint(x: 0.28, y: 0.28),
            CGPoint(x: 0.88, y: 0.52),
            CGPoint(x: 0.44, y: 0.76),
            CGPoint(x: 0.16, y: 0.34),
            CGPoint(x: 0.72, y: 0.18)
        ].shuffled()
        var path = Array(wallPoints.prefix(max(0, count - 4)))
        path.append(CGPoint(x: (target.x + 0.18) / 2, y: min(0.80, max(0.16, target.y + 0.20))))
        path.append(CGPoint(x: min(0.88, max(0.12, target.x - 0.10)), y: min(0.78, max(0.14, target.y - 0.12))))
        path.append(CGPoint(x: min(0.88, max(0.12, target.x + 0.04)), y: min(0.78, max(0.14, target.y + 0.05))))
        path.append(target)
        return Array(path.prefix(count))
    }

    private func isWallBounce(_ point: CGPoint) -> Bool {
        point.x < 0.20 || point.x > 0.80 || point.y < 0.20 || point.y > 0.68
    }

    private func triggerMapImpact(from point: CGPoint, intensity: CGFloat) {
        let xDirection: CGFloat = point.x < 0.5 ? 1 : -1
        let yDirection: CGFloat = point.y < 0.5 ? 1 : -1
        withAnimation(.interpolatingSpring(stiffness: 520, damping: 18)) {
            mapImpactOffset = CGSize(
                width: xDirection * 7 * intensity,
                height: yDirection * 5 * intensity
            )
            mapImpactRotation = Double(xDirection * 0.32 * intensity)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
            resetMapImpact()
        }
    }

    private func resetMapImpact() {
        withAnimation(.interpolatingSpring(stiffness: 420, damping: 20)) {
            mapImpactOffset = .zero
            mapImpactRotation = 0
        }
    }

    private func resetPickerDrawState() {
        pickerPreviewTitle = nil
        pickerCandidateTrail = []
        pickerPhase = .idle
        isRollingPickerDice = false
        pickerDiceState = DrawDiceAnimationState()
        resetMapImpact()
    }

    private func playDrawTickSound() {
        AudioServicesPlaySystemSound(1104)
    }

    private func playDrawRevealSound() {
        AudioServicesPlaySystemSound(1025)
    }

    @MainActor
    private func selectPickerCandidate(_ candidate: MapPickerCandidate) {
        if candidate.key.hasPrefix("recommendation:"),
           let place = visibleRecommendations.first(where: {
               "recommendation:\($0.stableKey)" == candidate.key
           }) {
            selectRecommendation(place)
            announcePickerResult(place.name)
        }
    }

    private func announcePickerResult(_ title: String) {
        #if os(iOS)
        UIAccessibility.post(notification: .announcement, argument: "\(title)을 골랐어요")
        #endif
    }

    private func handleDeepLink(_ url: URL) {
        if AppConfig.isOAuthCallbackURL(url) {
            Task {
                await model.resumeOAuthSignIn(from: url)
            }
            return
        }

        guard let route = PendingIntentRoute(url: url) else {
            return
        }

        apply(route)
    }

    private func handlePendingIntentRoute() {
        guard ProcessInfo.processInfo.environment["NUGUL_INITIAL_SEARCH_QUERY"] == nil else {
            return
        }
        guard let route = PendingIntentRoute.consume() else {
            return
        }

        apply(route)
    }

    @MainActor
    private func applyInitialSearchRouteIfNeeded() async -> Bool {
        guard !didApplyInitialSearchRoute,
              let rawQuery = ProcessInfo.processInfo.environment["NUGUL_INITIAL_SEARCH_QUERY"],
              !rawQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }

        didApplyInitialSearchRoute = true
        model.query = rawQuery
        prepareForSeason3SearchIfNeeded()
        showsInitialSearchDetail = Season3SearchIntent(query: rawQuery) != nil
        if let coordinate = await model.search() {
            centerMap(on: coordinate, reloadZones: false)
        }

        let initialItem: Season3InsightItem? = switch layerMode {
        case .hotplaces:
            model.hotplaceInsight.places.first.map(Season3InsightItem.hotplace)
        case .events:
            visibleEvents.first.map(Season3InsightItem.event)
        case .zones, .pero:
            nil
        }
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            initialSearchInsightItem = initialItem
        }
        return true
    }

    private func apply(_ route: PendingIntentRoute) {
        switch route {
        case .map:
            selectLayerMode(.zones)
            activeSheet = nil
        case .report:
            activeSheet = model.currentUser == nil ? .login : .report
        case .profile:
            activeSheet = model.currentUser == nil ? .login : .profile
        case let .search(keyword):
            model.query = keyword
            Task {
                prepareForSeason3SearchIfNeeded()
                if let coordinate = await model.search() {
                    centerMap(on: coordinate, reloadZones: false)
                } else {
                    centerOnFirstZoneIfPossible(reloadZones: false)
                }
            }
        }
    }

    private func prepareForSeason3SearchIfNeeded() {
        layerMode = .zones
        model.selectedZone = nil
        model.dismissLatestSeason3SearchResult()
        selectedInsight = nil
    }

    private func selectLayerMode(_ mode: Season2LayerMode) {
        guard mode == .zones else { return }
        let resetsBroadEventSearch = mode == .events && broadEventSearchKeyword != nil
        let dismissesSearchResult = model.presentsLatestSeason3SearchResult
        guard layerMode != mode || resetsBroadEventSearch || dismissesSearchResult else {
            return
        }

        layerMode = mode
        broadEventSearchKeyword = nil
        model.dismissLatestSeason3SearchResult()
        model.selectedZone = nil
        selectedInsight = nil
        showsInitialSearchDetail = false
        initialSearchInsightItem = nil

        if mode == .events {
            centerMap(
                on: CLLocationCoordinate2D(latitude: 37.545, longitude: 127.052),
                reloadZones: false
            )
        }

        if mode.requiresInsights {
            Task {
                await loadInsightsForActiveLayer(
                    visibleBounds: .seoulCity,
                    force: true
                )
                if mode == .hotplaces, let first = model.hotplaceInsight.places.first {
                    centerMap(
                        on: CLLocationCoordinate2D(latitude: first.latitude, longitude: first.longitude),
                        reloadZones: false
                    )
                }
            }
        }
    }
}

private struct ZoneDetailPresenter: View {
    @ObservedObject var model: ZoneExplorerModel

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .sheet(item: $model.selectedZone) { zone in
                ZoneDetailView(zone: zone, model: model)
                    .presentationDetents([.medium, .large])
                    .compactGlassSheet()
            }
    }
}

private enum MapUtilityAction {
    case settings
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

private enum Season3InsightSelection: Equatable {
    case hotplace(String)
    case event(String)
    case recommendation(String)
}

private enum Season3InsightItem {
    case hotplace(Hotplace)
    case event(TrendEvent)
    case recommendation(RecommendationPlace)
}

private struct MapPickerAnimationItem: Identifiable, Equatable {
    let candidate: MapPickerCandidate
    let title: String
    let badge: String

    var id: String {
        candidate.key
    }
}

private enum DrawPhase: Equatable {
    case idle
    case scanning
    case shuffling
    case locking
    case revealed

    var buttonTitle: String {
        switch self {
        case .idle: return "한 곳 추천"
        case .scanning: return "스캔 중"
        case .shuffling: return "추천 중"
        case .locking: return "결정 중"
        case .revealed: return "완료"
        }
    }

    var accessibilityTitle: String {
        switch self {
        case .idle: return "장소 추천 준비"
        case .scanning: return "지도 범위 확인 중"
        case .shuffling: return "추천 장소를 고르는 중"
        case .locking: return "결과 확정 중"
        case .revealed: return "장소 추천 완료"
        }
    }

    var overlayTitle: String {
        switch self {
        case .idle: return "준비"
        case .scanning: return "현재 범위 확인 중"
        case .shuffling: return "후보 섞는 중"
        case .locking: return "여기로 결정"
        case .revealed: return "선택 완료"
        }
    }
}

private struct DrawDiceAnimationState: Equatable {
    var normalizedPosition: CGPoint = CGPoint(x: 0.50, y: 0.46)
    var rotation: Double = 0
    var scale: CGFloat = 0.92
    var blur: CGFloat = 0
    var impact: CGFloat = 0
}

private struct MapPickerDrawExperienceOverlay: View {
    let phase: DrawPhase
    let previewTitle: String?
    let candidateTrail: [MapPickerAnimationItem]
    let modeTitle: String
    let diceState: DrawDiceAnimationState
    let reduceMotion: Bool

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                RecommendationToken(phase: phase, state: diceState, reduceMotion: reduceMotion)
                    .position(
                        x: proxy.size.width * diceState.normalizedPosition.x,
                        y: proxy.size.height * diceState.normalizedPosition.y
                    )

                MapPickerDrawTicker(
                    phase: phase,
                    previewTitle: previewTitle,
                    candidateTrail: candidateTrail,
                    modeTitle: modeTitle,
                    reduceMotion: reduceMotion
                )
                .position(x: proxy.size.width * 0.5, y: proxy.size.height * 0.20)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        if let previewTitle, !previewTitle.isEmpty {
            return "\(phase.accessibilityTitle), \(previewTitle)"
        }
        return phase.accessibilityTitle
    }
}

private struct RecommendationToken: View {
    let phase: DrawPhase
    let state: DrawDiceAnimationState
    let reduceMotion: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white)
            Circle()
                .stroke(Color.black.opacity(0.09), lineWidth: 1)
            Image(systemName: "sparkles")
                .font(.system(size: 21, weight: .black))
                .foregroundStyle(Color.nugulPrimary)
        }
            .frame(width: 52, height: 52)
            .shadow(color: .black.opacity(0.22), radius: 14 + state.impact * 6, y: 8)
            .scaleEffect(state.scale)
            .blur(radius: reduceMotion ? 0 : state.blur)
            .overlay {
                if phase == .locking || phase == .revealed {
                    Circle()
                        .stroke(Color.nugulPrimary.opacity(0.34), lineWidth: 8)
                        .frame(width: 70, height: 70)
                        .scaleEffect(phase == .revealed ? 1.22 : 0.96)
                        .opacity(phase == .revealed ? 0 : 1)
                }
            }
            .animation(.spring(response: 0.24, dampingFraction: 0.68), value: phase)
    }
}

private struct MapPickerDrawTicker: View {
    let phase: DrawPhase
    let previewTitle: String?
    let candidateTrail: [MapPickerAnimationItem]
    let modeTitle: String
    let reduceMotion: Bool

    var body: some View {
        VStack(spacing: 8) {
            Text(phase.overlayTitle)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.nugulMutedText)

            Text(previewTitle ?? "현재 화면의 \(modeTitle) 후보")
                .font(.headline.weight(.black))
                .foregroundStyle(Color.nugulPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .id(previewTitle)
                .transition(.opacity.combined(with: .scale(scale: reduceMotion ? 1 : 0.95)))

            if !candidateTrail.isEmpty && !reduceMotion {
                HStack(spacing: 4) {
                    ForEach(candidateTrail.suffix(3)) { item in
                        Text(item.badge.prefix(4))
                            .font(.caption2.weight(.black))
                            .foregroundStyle(Color.nugulPrimary)
                            .lineLimit(1)
                            .padding(.horizontal, 8)
                            .frame(height: 24)
                            .background(Color.nugulMuted, in: Capsule())
                    }
                }
                .transition(.opacity)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .frame(maxWidth: 286)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .background(Color.white.opacity(0.84), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Color.white.opacity(0.72), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.14), radius: 18, y: 8)
    }
}

private struct MapMarkerAccessibilityList: View {
    @ObservedObject var model: ZoneExplorerModel
    let mode: Season2LayerMode
    let zones: [SmokingZone]
    let hotplaces: [Hotplace]
    let events: [TrendEvent]
    let recommendations: [RecommendationPlace]
    let onSelectZone: (SmokingZone) -> Void
    let onSelectHotplace: (Hotplace) -> Void
    let onSelectEvent: (TrendEvent) -> Void
    let onSelectRecommendation: (RecommendationPlace) -> Void

    var body: some View {
        VStack {
            Text("\(mode.title) 지도")
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("map-accessibility-representation-heading")

            switch mode {
            case .zones:
                markerButtons(for: Array(model.zones.prefix(30)))
            case .hotplaces:
                hotplaceButtons(for: Array(hotplaces.prefix(30)))
            case .events:
                eventButtons(for: Array(events.prefix(30)))
            case .pero:
                recommendationButtons(for: Array(recommendations.prefix(8)))
            }
        }
        .foregroundStyle(.white)
        .tint(.white)
        .padding(12)
        .background(Color.nugulPrimary)
    }

    @ViewBuilder
    private func markerButtons(for items: [SmokingZone]) -> some View {
        if items.isEmpty {
            Text("현재 지도에 표시할 흡연구역이 없습니다.")
                .accessibilityIdentifier("map-accessibility-representation-empty")
        } else {
            ForEach(items) { zone in
                Button("\(zone.title), \(zone.address)") {
                    onSelectZone(zone)
                }
                .accessibilityIdentifier("map-accessibility-representation-zone-\(zone.id)")
                .accessibilityHint("선택하여 흡연구역 상세 보기")
            }
        }
    }

    @ViewBuilder
    private func hotplaceButtons(for items: [Hotplace]) -> some View {
        if items.isEmpty {
            Text("현재 지도에 표시할 실시간 핫플이 없습니다.")
                .accessibilityIdentifier("map-accessibility-representation-empty")
        } else {
            ForEach(items) { place in
                Button("\(place.name), \(place.crowdLabel)") {
                    onSelectHotplace(place)
                }
                .accessibilityHint("선택하여 실시간 혼잡 상세 보기")
            }
        }
    }

    @ViewBuilder
    private func eventButtons(for items: [TrendEvent]) -> some View {
        if items.isEmpty {
            Text("현재 지도에 표시할 성수 팝업이 없습니다.")
                .accessibilityIdentifier("map-accessibility-representation-empty")
        } else {
            ForEach(items) { event in
                Button(eventAccessibilityLabel(event)) {
                    onSelectEvent(event)
                }
                .accessibilityHint("선택하여 팝업 기간과 원문 보기")
            }
        }
    }

    private func eventAccessibilityLabel(_ event: TrendEvent) -> String {
        [event.title, event.dateRangeLabel, event.address]
            .compactMap { value in
                guard let value, !value.isEmpty else {
                    return nil
                }
                return value
            }
            .joined(separator: ", ")
    }

    @ViewBuilder
    private func recommendationButtons(for items: [RecommendationPlace]) -> some View {
        if items.isEmpty {
            Text("현재 지도에 표시할 추천 장소가 없습니다.")
                .accessibilityIdentifier("map-accessibility-representation-empty")
        } else {
            ForEach(items, id: \.stableKey) { place in
                Button([place.name, place.category, place.distanceLabel].compactMap { $0 }.joined(separator: ", ")) {
                    onSelectRecommendation(place)
                }
                .accessibilityHint("선택하여 추천 이유와 장소 상세 보기")
            }
        }
    }
}

private struct UnifiedSearchMenuBar: View {
    @Binding var query: String
    let isLoading: Bool
    let onClear: () -> Void
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onSearch) {
                Group {
                    if isLoading {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(Color.nugulPrimary)
                    }
                }
                .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .contentShape(Rectangle())
            .accessibilityLabel(isLoading ? "검색 중" : "검색")

            TextField("장소·주소 검색", text: $query)
                .font(.body.weight(.medium))
                .frame(minHeight: 44)
                .submitLabel(.search)
                .onSubmit(onSearch)
                .accessibilityLabel("장소·주소 검색")

            if !query.isEmpty {
                Button(action: onClear) {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText)
                        .frame(width: 28, height: 28)
                        .background(Color.nugulMuted, in: Circle())
                }
                .buttonStyle(.plain)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .accessibilityLabel("검색어 지우기")
            }
        }
        .padding(.leading, 4)
        .padding(.trailing, 6)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 54)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 19, style: .continuous))
        .background(Color.white.opacity(0.94), in: RoundedRectangle(cornerRadius: 19, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 19, style: .continuous)
                .stroke(Color.black.opacity(0.06), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.14), radius: 12, x: 0, y: 7)
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
                .fill(Color.nugulPrimary)
                .frame(width: 42, height: 42)

            if let first = user?.displayName.first {
                Text(String(first))
                    .font(.title3.weight(.black))
                    .foregroundStyle(.white)
            } else {
                Image(systemName: "person.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
            }
        }
    }
}

private enum Season2LayerMode: CaseIterable, Identifiable {
    case zones
    case hotplaces
    case events
    case pero

    var id: String {
        switch self {
        case .zones:
            return "zones"
        case .hotplaces:
            return "hotplaces"
        case .events:
            return "events"
        case .pero:
            return "pero"
        }
    }

    var title: String {
        switch self {
        case .zones:
            return "흡연구역"
        case .hotplaces:
            return "실시간 핫플"
        case .events:
            return "팝업"
        case .pero:
            return "추천"
        }
    }

    static let filterOrder: [Season2LayerMode] = [.zones, .pero, .hotplaces, .events]

    var filterTitle: String {
        switch self {
        case .zones: return "흡연구역"
        case .hotplaces: return "핫플"
        case .events: return "팝업"
        case .pero: return "추천"
        }
    }

    var filterIcon: String {
        switch self {
        case .zones: return "mappin.and.ellipse"
        case .hotplaces: return "flame.fill"
        case .events: return "calendar"
        case .pero: return "sparkles"
        }
    }

    var showsZones: Bool {
        self == .zones
    }

    var showsHotplaces: Bool {
        self == .hotplaces
    }

    var showsEvents: Bool {
        self == .events
    }

    var requiresInsights: Bool {
        self != .zones
    }
}

private extension RecommendationIntent {
    var filterTitle: String {
        switch self {
        case .cafe: return "카페"
        case .restaurant: return "음식점"
        case .date: return "데이트"
        }
    }

    var filterIcon: String {
        switch self {
        case .cafe: return "cup.and.saucer.fill"
        case .restaurant: return "fork.knife"
        case .date: return "heart.fill"
        }
    }
}

#if os(iOS)
private struct NugulKakaoMapView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let model: ZoneExplorerModel
    let isSceneActive: Bool
    let layerMode: Season2LayerMode
    let zones: [SmokingZone]
    let hotplaces: [Hotplace]
    let events: [TrendEvent]
    let recommendations: [RecommendationPlace]
    let onRegionChanged: (MKCoordinateRegion) -> Void
    let onRegionSettled: (MKCoordinateRegion) -> Void
    let onSelectZone: (SmokingZone) -> Void
    let onSelectHotplace: (Hotplace) -> Void
    let onSelectEvent: (TrendEvent) -> Void
    let onSelectRecommendation: (RecommendationPlace) -> Void

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
            static let insightLayerID = "nugul-season2-insights"
            static let poiStyleID = "nugul-zone-marker"
            static let hotplacePoiStyleID = "nugul-hotplace-marker"
            static let eventPoiStyleID = "nugul-event-marker"
            static let recommendationPoiStyleID = "nugul-recommendation-marker"
            static let defaultKakaoLevel = 15
        }

        var parent: NugulKakaoMapView
        private weak var hostView: UIView?
        private var container: KMViewContainer?
        private var controller: KMController?
        private weak var kakaoMap: KakaoMap?
        private var labelLayer: LabelLayer?
        private var insightLayer: LabelLayer?
        private var hasPreparedEngine = false
        private var isEngineActive: Bool?
        private var hasRequestedMapView = false
        private var hasRegisteredPoiStyle = false
        private var renderedZoneSignature: Set<String> = []
        private var renderedInsightSignature: Set<String> = []
        private var lastAppliedRegion: MKCoordinateRegion?
        private var isApplyingCameraFromSwiftUI = false
        private var eventHandlers: [any DisposableEventHandler] = []
        private var zonesCancellable: AnyCancellable?
        private var hotplacesCancellable: AnyCancellable?
        private var eventsCancellable: AnyCancellable?
        private var recommendationsCancellable: AnyCancellable?
        private var selectedCardView: UIView?
        private var selectedCardPoiID: String?
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
                parent.model.reportMapFailure()
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
            isEngineActive = nil
            hasRequestedMapView = false

            startEngineIfReady()
        }

        func shutdown() {
            if isEngineActive == true {
                controller?.pauseEngine()
            }
            controller?.resetEngine()
            controller?.delegate = nil
            kakaoMap?.eventDelegate = nil
            kakaoMap = nil
            eventHandlers.forEach { $0.dispose() }
            eventHandlers = []
            selectedCardView?.removeFromSuperview()
            selectedCardView = nil
            selectedCardPoiID = nil
            zonesCancellable = nil
            hotplacesCancellable = nil
            eventsCancellable = nil
            recommendationsCancellable = nil
            boundModelID = nil
            labelLayer = nil
            controller = nil
            container = nil
            hostView = nil
            hasPreparedEngine = false
            isEngineActive = nil
            hasRequestedMapView = false
            hasRegisteredPoiStyle = false
            renderedZoneSignature = []
            renderedInsightSignature = []
            lastAppliedRegion = nil
        }

        func syncState(animationEnabled: Bool) {
            if let hostView, let container {
                container.frame = hostView.bounds.isEmpty ? UIScreen.main.bounds : hostView.bounds
                syncMapViewRect(to: container.bounds.size)
            }
            bindModelIfNeeded()
            startEngineIfReady()
            syncSceneActivity(isActive: parent.isSceneActive)

            guard let kakaoMap else {
                return
            }

            syncPois(
                on: kakaoMap,
                zones: parent.layerMode.showsZones ? parent.model.zones : []
            )
            syncVisibleInsightPois(on: kakaoMap)
            if parent.model.selectedZone == nil, selectedCardPoiID?.hasPrefix("zone:") == true {
                selectedCardView?.removeFromSuperview()
                selectedCardView = nil
                selectedCardPoiID = nil
            }
            dismissSelectedCardIfHidden()
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

                        self.syncPois(on: kakaoMap, zones: self.parent.layerMode.showsZones ? zones : [])
                        self.dismissSelectedCardIfHidden()
                    }
                }
            hotplacesCancellable = parent.model.$hotplaceInsight
                .receive(on: RunLoop.main)
                .sink { [weak self] _ in
                    Task { @MainActor [weak self] in
                        guard let self, let kakaoMap = self.kakaoMap else {
                            return
                        }

                        self.syncVisibleInsightPois(on: kakaoMap)
                        self.dismissSelectedCardIfHidden()
                    }
                }
            eventsCancellable = parent.model.$eventInsight
                .receive(on: RunLoop.main)
                .sink { [weak self] _ in
                    Task { @MainActor [weak self] in
                        guard let self, let kakaoMap = self.kakaoMap else {
                            return
                        }

                        self.syncVisibleInsightPois(on: kakaoMap)
                        self.dismissSelectedCardIfHidden()
                    }
                }
            recommendationsCancellable = parent.model.$recommendationResponse
                .receive(on: RunLoop.main)
                .sink { [weak self] _ in
                    Task { @MainActor [weak self] in
                        guard let self, let kakaoMap = self.kakaoMap else {
                            return
                        }

                        self.syncVisibleInsightPois(on: kakaoMap)
                        self.dismissSelectedCardIfHidden()
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
            syncSceneActivity(isActive: parent.isSceneActive)
        }

        private func syncSceneActivity(isActive: Bool) {
            guard hasPreparedEngine,
                  let controller,
                  isEngineActive != isActive else {
                return
            }

            if isActive {
                controller.activateEngine()
            } else {
                // 아직 활성화하지 않은 엔진은 준비 콜백 전에 멈추지 않는다.
                guard isEngineActive == true else {
                    return
                }
                controller.pauseEngine()
            }
            isEngineActive = isActive
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
            syncMapViewRect(to: container?.bounds.size)
            parent.model.clearMapFailure()
            mapView.eventDelegate = self
            mapView.poiClickable = true
            mapView.setPoiEnabled(true)
            mapView.cameraAnimationEnabled = true
            installEventHandlers(on: mapView)
            configureLabels(on: mapView)
            syncState(animationEnabled: false)
            // The engine may already be marked active before Kakao finishes adding the view.
            // Activate once more so the newly attached map starts drawing immediately.
            if parent.isSceneActive {
                controller?.activateEngine()
                isEngineActive = true
            }
        }

        func addViewFailed(_ viewName: String, viewInfoName: String) {
            installQuietShellIfNeeded(in: hostView)
            parent.model.reportMapFailure()
        }

        func authenticationSucceeded() {
            parent.model.clearMapFailure()
        }

        func authenticationFailed(_ errorCode: Int, desc: String) {
            installQuietShellIfNeeded(in: hostView)
            parent.model.reportMapFailure()
        }

        func containerDidResized(_ size: CGSize) {
            guard size.width > 0, size.height > 0 else {
                return
            }

            syncMapViewRect(to: size)
            syncState(animationEnabled: false)
        }

        private func syncMapViewRect(to size: CGSize?) {
            guard let size, size.width > 0, size.height > 0 else {
                return
            }
            kakaoMap?.viewRect = CGRect(origin: .zero, size: size)
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
            if layerID == Constants.insightLayerID {
                handleInsightTap(poiID: poiID)
                return
            }

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

        private func handleInsightTap(poiID: String) {
            if poiID.hasPrefix("hot:") {
                let id = String(poiID.dropFirst(4))
                guard let place = parent.hotplaces.first(where: { $0.id == id }) else {
                    return
                }
                selectedCardView?.removeFromSuperview()
                selectedCardView = nil
                selectedCardPoiID = nil
                parent.onSelectHotplace(place)
                return
            }

            if poiID.hasPrefix("event:") {
                let id = String(poiID.dropFirst(6))
                guard let event = parent.events.first(where: { $0.id == id }) else {
                    return
                }
                selectedCardView?.removeFromSuperview()
                selectedCardView = nil
                selectedCardPoiID = nil
                parent.onSelectEvent(event)
                return
            }

            if poiID.hasPrefix("rec:") {
                let stableKey = String(poiID.dropFirst(4))
                guard let place = parent.recommendations.first(where: { $0.stableKey == stableKey }) else {
                    return
                }
                selectedCardView?.removeFromSuperview()
                selectedCardView = nil
                selectedCardPoiID = nil
                parent.onSelectRecommendation(place)
            }
        }

        private func showNativeCard(for zone: SmokingZone) {
            guard let hostView else {
                return
            }

            selectedCardView?.removeFromSuperview()
            selectedCardPoiID = "zone:\(zone.id)"

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
            closeButton.layer.cornerRadius = 22
            closeButton.accessibilityLabel = "장소 상세 닫기"
            closeButton.addAction(UIAction { [weak self] _ in
                self?.selectedCardView?.removeFromSuperview()
                self?.selectedCardView = nil
                self?.selectedCardPoiID = nil
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
                if let url = URL(string: "https://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)") {
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
                closeButton.widthAnchor.constraint(equalToConstant: 44),
                closeButton.heightAnchor.constraint(equalToConstant: 44),
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
            let topLeft = kakaoMap.getPosition(
                CGPoint(x: container.bounds.minX + 1, y: container.bounds.minY + 1)
            ).wgsCoord
            let bottomRight = kakaoMap.getPosition(
                CGPoint(x: container.bounds.maxX - 1, y: container.bounds.maxY - 1)
            ).wgsCoord
            let latitudeDelta = abs(topLeft.latitude - bottomRight.latitude)
            let longitudeDelta = abs(topLeft.longitude - bottomRight.longitude)
            let visibleSpan = MKCoordinateSpan(
                latitudeDelta: latitudeDelta.isFinite && latitudeDelta > 0 ? latitudeDelta : parent.region.span.latitudeDelta,
                longitudeDelta: longitudeDelta.isFinite && longitudeDelta > 0 ? longitudeDelta : parent.region.span.longitudeDelta
            )
            let settledRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: center.latitude, longitude: center.longitude),
                span: visibleSpan
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
            let markerImage = UIImage.nugulInsightMarker(
                size: CGSize(width: 30, height: 30),
                fill: UIColor(red: 0.18, green: 0.34, blue: 0.22, alpha: 1),
                symbolName: "mappin"
            )
            let hotplaceImage = UIImage.nugulInsightMarker(
                size: CGSize(width: 30, height: 30),
                fill: UIColor(red: 0.93, green: 0.38, blue: 0.16, alpha: 1),
                symbolName: "flame.fill"
            )
            let eventImage = UIImage.nugulInsightMarker(size: CGSize(width: 30, height: 30), fill: UIColor(red: 0.17, green: 0.45, blue: 0.66, alpha: 1), symbolName: "calendar")
            let recommendationImage = UIImage.nugulInsightMarker(
                size: CGSize(width: 32, height: 32),
                fill: UIColor(red: 0.12, green: 0.13, blue: 0.15, alpha: 1),
                symbolName: "sparkles"
            )
            let iconStyle = PoiIconStyle(
                symbol: markerImage,
                anchorPoint: CGPoint(x: 0.5, y: 0.5)
            )
            let perLevelStyle = PerLevelPoiStyle(iconStyle: iconStyle, padding: 0, level: 0)
            let style = PoiStyle(styleID: Constants.poiStyleID, styles: [perLevelStyle])
            manager.addPoiStyle(style)
            manager.addPoiStyle(PoiStyle(styleID: Constants.hotplacePoiStyleID, styles: [
                PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: hotplaceImage, anchorPoint: CGPoint(x: 0.5, y: 0.5)), padding: 0, level: 0)
            ]))
            manager.addPoiStyle(PoiStyle(styleID: Constants.eventPoiStyleID, styles: [
                PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: eventImage, anchorPoint: CGPoint(x: 0.5, y: 0.5)), padding: 0, level: 0)
            ]))
            manager.addPoiStyle(PoiStyle(styleID: Constants.recommendationPoiStyleID, styles: [
                PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: recommendationImage, anchorPoint: CGPoint(x: 0.5, y: 0.5)), padding: 0, level: 0)
            ]))

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
            let insightOptions = LabelLayerOptions(
                layerID: Constants.insightLayerID,
                competitionType: .none,
                competitionUnit: .symbolFirst,
                orderType: .rank,
                zOrder: 10_200
            )
            let season2Layer = manager.addLabelLayer(option: insightOptions)
            season2Layer?.setClickable(true)
            insightLayer = season2Layer
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

        private func syncInsightPois(
            on kakaoMap: KakaoMap,
            hotplaces: [Hotplace],
            events: [TrendEvent],
            recommendations: [RecommendationPlace]
        ) {
            if !hasRegisteredPoiStyle {
                configureLabels(on: kakaoMap)
            }

            guard let insightLayer else {
                return
            }

            let visibleHotplaces = Array(hotplaces.prefix(8))
            let visibleEvents = Array(events.prefix(8))
            let visibleRecommendations = Array(recommendations.prefix(8))
            let nextSignature = Set(
                visibleHotplaces.map { "hot:\($0.id):\(String(format: "%.6f", $0.latitude)):\(String(format: "%.6f", $0.longitude)):\($0.compactMapLabel)" } +
                    visibleEvents.map { "event:\($0.id):\(String(format: "%.6f", $0.latitude)):\(String(format: "%.6f", $0.longitude))" } +
                    visibleRecommendations.map { "rec:\($0.stableKey):\(String(format: "%.6f", $0.latitude)):\(String(format: "%.6f", $0.longitude))" }
            )
            guard nextSignature != renderedInsightSignature else {
                return
            }

            insightLayer.clearAllItems()
            for place in visibleHotplaces {
                let option = PoiOptions(styleID: Constants.hotplacePoiStyleID, poiID: "hot:\(place.id)")
                option.rank = 1_200
                option.clickable = true
                let poi = insightLayer.addPoi(
                    option: option,
                    at: MapPoint(longitude: place.longitude, latitude: place.latitude)
                )
                poi?.show()
            }
            for event in visibleEvents {
                let option = PoiOptions(styleID: Constants.eventPoiStyleID, poiID: "event:\(event.id)")
                option.rank = 1_100
                option.clickable = true
                let poi = insightLayer.addPoi(
                    option: option,
                    at: MapPoint(longitude: event.longitude, latitude: event.latitude)
                )
                poi?.show()
            }
            for place in visibleRecommendations {
                let option = PoiOptions(
                    styleID: Constants.recommendationPoiStyleID,
                    poiID: "rec:\(place.stableKey)"
                )
                option.rank = 1_300
                option.clickable = true
                let poi = insightLayer.addPoi(
                    option: option,
                    at: MapPoint(longitude: place.longitude, latitude: place.latitude)
                )
                poi?.show()
            }
            renderedInsightSignature = nextSignature
        }

        private func syncVisibleInsightPois(on kakaoMap: KakaoMap) {
            syncInsightPois(
                on: kakaoMap,
                hotplaces: parent.hotplaces,
                events: parent.events,
                recommendations: parent.recommendations
            )
        }

        private func dismissSelectedCardIfHidden() {
            guard let selectedCardPoiID else {
                return
            }

            let visibleIDs = Set(
                parent.zones.map { "zone:\($0.id)" } +
                    parent.hotplaces.map { "hot:\($0.id)" } +
                    parent.events.map { "event:\($0.id)" } +
                    parent.recommendations.map { "rec:\($0.stableKey)" }
            )
            guard !visibleIDs.contains(selectedCardPoiID) else {
                return
            }

            selectedCardView?.removeFromSuperview()
            selectedCardView = nil
            self.selectedCardPoiID = nil
            if selectedCardPoiID.hasPrefix("zone:") {
                parent.model.selectedZone = nil
            }
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

    static func nugulInsightMarker(size: CGSize, fill: UIColor, symbolName: String) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            let rect = CGRect(origin: .zero, size: size)
            UIColor.white.setFill()
            UIBezierPath(ovalIn: rect.insetBy(dx: 1.5, dy: 1.5)).fill()
            fill.setFill()
            UIBezierPath(ovalIn: rect.insetBy(dx: 4.5, dy: 4.5)).fill()

            let symbolConfig = UIImage.SymbolConfiguration(pointSize: size.width * 0.42, weight: .black)
            guard let symbol = UIImage(systemName: symbolName, withConfiguration: symbolConfig)?.withTintColor(.white, renderingMode: .alwaysOriginal) else {
                return
            }
            let symbolSize = symbol.size
            let symbolOrigin = CGPoint(
                x: (size.width - symbolSize.width) / 2,
                y: (size.height - symbolSize.height) / 2
            )
            symbol.draw(at: symbolOrigin)
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
                        .frame(width: 44, height: 44)
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
                        .frame(height: 44)
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
        guard let url = URL(string: "https://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)") else {
            return
        }

        openURL(url)
    }
}

private struct MapBottomControls: View {
    @ObservedObject var model: ZoneExplorerModel
    @ObservedObject var locationProvider: LocationProvider
    let layerMode: Season2LayerMode
    @Binding var selectedInsight: Season3InsightSelection?
    @Binding var broadEventSearchKeyword: String?
    @Binding var showsInitialSearchDetail: Bool
    @Binding var initialSearchInsightItem: Season3InsightItem?
    let pickerCandidateCount: Int
    let isRollingPickerDice: Bool
    let pickerPhase: DrawPhase
    let safeAreaBottom: CGFloat
    let onCenterLocation: (CLLocationCoordinate2D) -> Void
    let onOpenAccount: () -> Void
    let onOpenUtility: (MapUtilityAction) -> Void
    let onOpenReport: () -> Void
    let onPickInsight: () -> Void

    private var visibleHotplaces: [Hotplace] {
        layerMode.showsHotplaces ? model.hotplaceInsight.places : []
    }

    private var visibleEvents: [TrendEvent] {
        guard layerMode.showsEvents else {
            return []
        }
        return model.eventInsight.events.filter {
            $0.isEligibleForSeason3EventSearch(
                allowsNonPopupEvents: broadEventSearchKeyword != nil
            )
        }
    }

    private var visibleRecommendations: [RecommendationPlace] {
        layerMode == .pero ? Array(model.visibleRecommendations.prefix(8)) : []
    }

    private var selectedInsightItem: Season3InsightItem? {
        if model.presentsLatestSeason3SearchResult {
            if let eventID = model.latestSeason3SearchEvent?.id,
               let event = visibleEvents.first(where: { $0.id == eventID }) {
                return .event(event)
            }
            if let place = model.latestSeason3SearchHotplace {
                return .hotplace(place)
            }
        }

        if showsInitialSearchDetail, let initialSearchInsightItem {
            switch initialSearchInsightItem {
            case .hotplace:
                return initialSearchInsightItem
            case let .event(event):
                return visibleEvents
                    .first(where: { $0.id == event.id })
                    .map(Season3InsightItem.event)
            case .recommendation:
                return nil
            }
        }

        if let selectedInsight {
            switch selectedInsight {
            case let .hotplace(id):
                return model.hotplaceInsight.places
                    .first(where: { $0.id == id })
                    .map(Season3InsightItem.hotplace)
            case let .event(id):
                return visibleEvents
                    .first(where: { $0.id == id })
                    .map(Season3InsightItem.event)
            case let .recommendation(stableKey):
                return visibleRecommendations
                    .first(where: { $0.stableKey == stableKey })
                    .map(Season3InsightItem.recommendation)
            }
        }

        guard showsInitialSearchDetail else {
            return nil
        }

        switch layerMode {
        case .hotplaces:
            return model.hotplaceInsight.places.first.map(Season3InsightItem.hotplace)
        case .events:
            return visibleEvents.first.map(Season3InsightItem.event)
        case .zones, .pero:
            return nil
        }
    }

    private var relevantStatusMessage: String? {
        let message = model.errorMessage
            ?? model.mapErrorMessage
            ?? model.locationErrorMessage
            ?? locationProvider.errorMessage
            ?? (layerMode == .pero ? model.recommendationErrorMessage : nil)

        switch message {
        case "현재 확인된 실시간 핫플이 없습니다." where layerMode != .hotplaces:
            return nil
        case "현재 확인된 성수 팝업이 없습니다." where layerMode != .events:
            return nil
        case "현재 화면에 추천할 장소가 없습니다." where layerMode != .pero:
            return nil
        default:
            return message
        }
    }

    private var recommendationEmptyMessage: String {
        if model.recommendationErrorMessage != nil {
            return "추천 장소를 불러오지 못했습니다."
        }
        switch model.recommendationResponse?.dataStatus {
        case .providerNotConfigured:
            return "추천 데이터 연결이 필요합니다."
        case .providerUnavailable:
            return "추천 데이터를 불러올 수 없습니다."
        case .ready, .partial, nil:
            return "현재 화면에 추천할 장소가 없습니다."
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            if let selectedInsightItem {
                Season3InsightDetailCard(
                    item: selectedInsightItem,
                    isSaved: recommendationPlace.map(model.isRecommendationSaved) ?? false,
                    onToggleSaved: toggleSavedRecommendation,
                    onNotInterested: markRecommendationNotInterested,
                    onRoute: recordRecommendationRoute,
                    onClose: dismissInsight
                )
                .padding(.horizontal, 16)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            if let message = relevantStatusMessage {
                WebToastBanner(message: message)
                    .padding(.horizontal, 16)
            }

            if layerMode == .events,
               !model.isLoadingInsights,
               visibleEvents.isEmpty {
                WebToastBanner(
                    message: model.insightErrorMessage == nil
                        ? "현재 확인된 성수 팝업이 없습니다."
                        : "성수 팝업을 불러오지 못했습니다."
                )
                .padding(.horizontal, 16)
            }

            if layerMode == .hotplaces,
               !model.isLoadingInsights,
               visibleHotplaces.isEmpty {
                WebToastBanner(
                    message: model.insightErrorMessage == nil
                        ? "현재 확인된 실시간 핫플이 없습니다."
                        : "실시간 핫플을 불러오지 못했습니다."
                )
                .padding(.horizontal, 16)
            }

            if layerMode == .pero,
               !model.isLoadingRecommendations,
               visibleRecommendations.isEmpty {
                WebToastBanner(
                    message: recommendationEmptyMessage
                )
                .padding(.horizontal, 16)
            }

            if selectedInsightItem == nil {
                HStack(alignment: .bottom, spacing: 10) {
                Button(action: requestLocation) {
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.96))
                            .overlay(Circle().stroke(Color.black.opacity(0.07), lineWidth: 1))
                            .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 7)

                        if locationProvider.isLoading {
                            ProgressView()
                                .tint(Color.nugulPrimary)
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(Color.nugulPrimary)
                        }
                    }
                    .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("현재 위치")

                Button(action: onOpenAccount) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color.nugulPrimary)
                        .frame(width: 48, height: 48)
                        .background(Color.white.opacity(0.96), in: Circle())
                        .overlay(Circle().stroke(Color.black.opacity(0.07), lineWidth: 1))
                        .shadow(color: .black.opacity(0.14), radius: 12, x: 0, y: 6)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("계정 열기")
                .accessibilityValue(model.currentUser == nil ? "로그인" : "내 프로필")

                Button(action: { onOpenUtility(.settings) }) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.nugulPrimary)
                        .frame(width: 48, height: 48)
                        .background(Color.white.opacity(0.96), in: Circle())
                        .overlay(Circle().stroke(Color.black.opacity(0.07), lineWidth: 1))
                        .shadow(color: .black.opacity(0.14), radius: 12, x: 0, y: 6)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("설정 열기")

                Spacer()

                if layerMode == .pero {
                    Button(action: onPickInsight) {
                        HStack(spacing: 8) {
                            if model.isLoadingRecommendations {
                                ProgressView()
                                    .tint(Color.nugulPrimary)
                            } else {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 17, weight: .black))
                            }
                            Text(pickerButtonTitle)
                                .font(.subheadline.weight(.bold))
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        .foregroundStyle(Color.nugulPrimary)
                        .frame(minHeight: 48)
                        .padding(.horizontal, 15)
                        .background(Color.white.opacity(0.96), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color.black.opacity(0.07), lineWidth: 1))
                        .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 7)
                    }
                    .buttonStyle(.plain)
                    .disabled(model.isLoadingRecommendations || isRollingPickerDice || pickerCandidateCount == 0)
                    .opacity(pickerCandidateCount == 0 && !model.isLoadingRecommendations ? 0.7 : 1)
                    .accessibilityIdentifier("map-recommendation-picker")
                    .accessibilityLabel(pickerAccessibilityLabel)
                    .accessibilityValue(pickerAccessibilityValue)
                    .accessibilityHint(
                        pickerCandidateCount == 0
                            ? "지도를 움직여 현재 화면 안의 후보를 찾아보세요."
                            : "현재 보이는 \(layerMode.title) 중 최근 선택을 피해 한 곳을 고릅니다."
                    )
                } else {
                    Button {
                        model.selectedZone = nil
                        onOpenReport()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                                .font(.system(size: 19, weight: .black))
                            Text("제보하기")
                                .font(.callout.weight(.black))
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        .foregroundStyle(.white)
                        .frame(minHeight: 52)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 4)
                        .background(Color.nugulPrimary, in: Capsule(style: .continuous))
                        .overlay(Capsule(style: .continuous).stroke(Color.white.opacity(0.24), lineWidth: 1))
                        .shadow(color: Color.nugulPrimary.opacity(0.34), radius: 18, x: 0, y: 10)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("흡연구역 제보하기")
                }
                }
                .padding(.horizontal, 20)
            }
        }
        .padding(.bottom, safeAreaBottom + 24)
    }

    private func dismissInsight() {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            model.dismissLatestSeason3SearchResult()
            selectedInsight = nil
            showsInitialSearchDetail = false
            initialSearchInsightItem = nil
        }
    }

    private var recommendationPlace: RecommendationPlace? {
        guard case let .recommendation(place)? = selectedInsightItem else {
            return nil
        }
        return place
    }

    private func toggleSavedRecommendation(_ place: RecommendationPlace) {
        guard model.currentUser != nil else {
            onOpenAccount()
            return
        }
        Task {
            _ = await model.toggleSavedRecommendation(place)
        }
    }

    private func markRecommendationNotInterested(_ place: RecommendationPlace) {
        dismissInsight()
        Task {
            await model.markRecommendationNotInterested(place)
        }
    }

    private func recordRecommendationRoute(_ place: RecommendationPlace) {
        Task {
            await model.recordRecommendationInteraction(.route, for: place)
        }
    }

    private func requestLocation() {
        model.setLocationErrorMessage(nil)
        locationProvider.requestLocation(
            completion: onCenterLocation,
            onFailure: { message in
                model.setLocationErrorMessage(message)
            }
        )
    }

    private var pickerButtonTitle: String {
        if model.isLoadingRecommendations {
            return "추천 불러오는 중"
        }
        if isRollingPickerDice {
            return pickerPhase.buttonTitle
        }
        if pickerCandidateCount == 0 {
            return "이 화면에 장소 없음"
        }
        return "한 곳 추천"
    }

    private var pickerAccessibilityLabel: String {
        "현재 화면에서 한 곳 추천"
    }

    private var pickerAccessibilityValue: String {
        if model.isLoadingRecommendations {
            return "추천 장소를 불러오는 중"
        }
        if isRollingPickerDice {
            return "선택 중"
        }
        return pickerCandidateCount == 0 ? "장소 없음" : "추천 장소 \(pickerCandidateCount)개"
    }
}

private struct Season3InsightDetailCard: View {
    let item: Season3InsightItem
    let isSaved: Bool
    let onToggleSaved: (RecommendationPlace) -> Void
    let onNotInterested: (RecommendationPlace) -> Void
    let onRoute: (RecommendationPlace) -> Void
    let onClose: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Capsule()
                .fill(Color.black.opacity(0.16))
                .frame(width: 38, height: 5)
                .frame(maxWidth: .infinity)

            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 7) {
                        Text(title)
                            .font(.headline.weight(.black))
                            .foregroundStyle(Color.nugulPrimary)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)

                        Text(badge)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 4)
                            .background(Color.nugulPrimary, in: Capsule())
                    }

                    if let address, !address.isEmpty {
                        Text(address)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.nugulMutedText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Spacer(minLength: 4)

                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .black))
                        .foregroundStyle(Color.nugulMutedText)
                        .frame(width: 36, height: 36)
                        .background(Color.nugulMuted, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("선택한 시즌3 장소 닫기")
            }

            detailContent

            if let recommendationPlace {
                HStack(spacing: 8) {
                    Button {
                        onToggleSaved(recommendationPlace)
                    } label: {
                        Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                            .font(.system(size: 17, weight: .bold))
                            .frame(width: 44, height: 44)
                            .foregroundStyle(Color.nugulPrimary)
                            .background(Color.nugulMuted, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(isSaved ? "\(title) 저장 취소" : "\(title) 저장")

                    if let detailURL {
                        Button {
                            openURL(detailURL)
                        } label: {
                            Image(systemName: "info.circle")
                                .font(.system(size: 18, weight: .bold))
                                .frame(width: 44, height: 44)
                                .foregroundStyle(Color.nugulPrimary)
                                .background(Color.nugulMuted, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(title) \(detailLinkTitle) 열기")
                    }

                    Menu {
                        Button("관심 없음", role: .destructive) {
                            onNotInterested(recommendationPlace)
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 17, weight: .bold))
                            .frame(width: 44, height: 44)
                            .foregroundStyle(Color.nugulPrimary)
                            .background(Color.nugulMuted, in: Circle())
                    }
                    .accessibilityLabel("추천 장소 메뉴")

                    Spacer(minLength: 4)

                    Button {
                        onRoute(recommendationPlace)
                        openDirections()
                    } label: {
                        Label("길찾기", systemImage: "location.north.fill")
                            .font(.subheadline.weight(.black))
                            .frame(minWidth: 118, minHeight: 44)
                            .foregroundStyle(.white)
                            .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(title) 길찾기 열기")
                }
            } else {
                HStack {
                    Spacer()
                    if let detailURL {
                        Button(detailLinkTitle) {
                            openURL(detailURL)
                        }
                        .buttonStyle(.bordered)
                    }
                    Button {
                        openDirections()
                    } label: {
                        Label("길찾기", systemImage: "location.north.fill")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }

            if attributionURL != nil || licenseURL != nil {
                HStack(spacing: 8) {
                    if let attributionURL {
                        Button {
                            openURL(attributionURL)
                        } label: {
                            VStack(spacing: 3) {
                                Image(systemName: "link")
                                Text("출처")
                                    .font(.body.weight(.bold))
                                    .lineLimit(nil)
                            }
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .foregroundStyle(.white)
                                .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("행사 출처 열기")
                    }

                    if let licenseURL {
                        Button {
                            openURL(licenseURL)
                        } label: {
                            VStack(spacing: 3) {
                                Image(systemName: "doc.text")
                                Text("이용조건")
                                    .font(.body.weight(.bold))
                                    .lineLimit(nil)
                            }
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .foregroundStyle(.white)
                                .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("행사 이용조건 열기")
                    }
                }
            }
        }
        .padding(15)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .background(Color.white.opacity(0.88), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Color.white.opacity(0.72), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.16), radius: 20, x: 0, y: 10)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var detailContent: some View {
        switch item {
        case let .hotplace(place):
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(place.crowdLabel)
                        .font(.subheadline.weight(.black))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(place.comparisonStatusLabel)
                        .font(.caption.weight(.black))
                        .foregroundStyle(accentColor)
                }

                if (place.freshnessStatus == "CURRENT" || place.freshnessStatus == "DELAYED"),
                   let crowdMessage = place.crowdMessage,
                   !crowdMessage.isEmpty {
                    Text(crowdMessage)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineLimit(2)
                }

                if let updatedAt = place.updatedAt, !updatedAt.isEmpty {
                    Season3MetadataLine(label: "측정", value: updatedAt)
                }
            }

        case let .event(event):
            VStack(alignment: .leading, spacing: 6) {
                if let startDate = event.startDate, !startDate.isEmpty {
                    Season3MetadataLine(label: "시작", value: startDate)
                }
                if let endDate = event.endDate, !endDate.isEmpty {
                    Season3MetadataLine(label: "종료", value: endDate)
                }
                if event.dateRangeLabel == nil, let period = event.period, !period.isEmpty {
                    Season3MetadataLine(label: "기간", value: period)
                }
                Season3MetadataLine(label: "출처", value: event.sourceLabel)
                if let licenseLabel = event.licenseLabel {
                    Season3MetadataLine(label: "이용조건", value: licenseLabel)
                }
            }
        case let .recommendation(place):
            VStack(alignment: .leading, spacing: 6) {
                if let distanceLabel = place.distanceLabel {
                    Season3MetadataLine(label: "거리", value: distanceLabel)
                }
                if !place.reasonCodes.isEmpty {
                    Season3MetadataLine(
                        label: "추천 이유",
                        value: place.reasonCodes.prefix(2).map(\.title).joined(separator: " · ")
                    )
                }
            }
        }
    }

    private var title: String {
        switch item {
        case let .hotplace(place):
            return place.name
        case let .event(event):
            return event.title
        case let .recommendation(place):
            return place.name
        }
    }

    private var badge: String {
        switch item {
        case let .hotplace(place):
            return place.comparisonStatusLabel
        case let .event(event):
            return event.kindLabel
        case let .recommendation(place):
            return place.category.isEmpty ? "추천" : place.category
        }
    }

    private var address: String? {
        switch item {
        case let .hotplace(place):
            return place.address
        case let .event(event):
            return event.address
        case let .recommendation(place):
            return place.address
        }
    }

    private var iconName: String {
        switch item {
        case .hotplace:
            return "person.3.fill"
        case .event:
            return "calendar.badge.clock"
        case .recommendation:
            return "sparkles"
        }
    }

    private var accentColor: Color {
        switch item {
        case .hotplace:
            return .orange
        case .event:
            return .blue
        case .recommendation:
            return Color.nugulPrimary
        }
    }

    private var detailURL: URL? {
        switch item {
        case let .event(event):
            return event.safeDetailURL
        case let .recommendation(place):
            return place.safeSourceURL
        case .hotplace:
            return nil
        }
    }

    private var detailLinkTitle: String {
        recommendationPlace == nil ? "원문" : "장소 정보"
    }

    private var recommendationPlace: RecommendationPlace? {
        guard case let .recommendation(place) = item else { return nil }
        return place
    }

    private var attributionURL: URL? {
        guard case let .event(event) = item else {
            return nil
        }
        return event.safeAttributionURL
    }

    private var licenseURL: URL? {
        guard case let .event(event) = item else {
            return nil
        }
        return event.safeLicenseURL
    }

    private var coordinate: CLLocationCoordinate2D {
        switch item {
        case let .hotplace(place):
            return CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
        case let .event(event):
            return CLLocationCoordinate2D(latitude: event.latitude, longitude: event.longitude)
        case let .recommendation(place):
            return CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
        }
    }

    private func openDirections() {
        var components = URLComponents(string: "https://maps.apple.com/")
        components?.queryItems = [
            URLQueryItem(name: "daddr", value: "\(coordinate.latitude),\(coordinate.longitude)"),
            URLQueryItem(name: "q", value: title)
        ]
        guard let url = components?.url else {
            return
        }
        openURL(url)
    }
}

private struct Season3MetadataLine: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .frame(minWidth: 52, alignment: .center)
                .background(Color.nugulPrimary, in: Capsule(style: .continuous))
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.nugulPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct WebToastBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.white)
                .padding(.top, 2)
            Text(message)
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(nil)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .layoutPriority(1)
                .frame(maxWidth: 220, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 7)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
        .accessibilityIdentifier("map-status-message")
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
private struct LocationLetterMapOverlay: View {
    @ObservedObject var model: ZoneExplorerModel
    let coordinate: CLLocationCoordinate2D
    let onDismiss: () -> Void
    let onOpenLogin: () -> Void

    @State private var currentIndex = 0
    @State private var isComposing = false
    @State private var draft = ""
    @FocusState private var isComposerFocused: Bool

    private var letters: [LocationLetter] {
        model.locationLetters.letters
    }

    private var selectedLetter: LocationLetter? {
        guard !letters.isEmpty else {
            return nil
        }
        return letters[min(currentIndex, letters.count - 1)]
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.white.opacity(0.66)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    Spacer()
                        .frame(height: max(proxy.safeAreaInsets.top + 128, proxy.size.height * 0.25))

                    callout
                        .frame(maxWidth: 520)
                        .padding(.horizontal, 54)

                    LocationLetterCalloutPointer()
                        .fill(Color.white)
                        .overlay {
                            LocationLetterCalloutPointer()
                                .stroke(Color.black.opacity(0.78), lineWidth: 1)
                        }
                        .frame(width: 28, height: 17)
                        .offset(y: -1)

                    Circle()
                        .fill(Color(red: 0.78, green: 0.39, blue: 0.35))
                        .frame(width: 14, height: 14)
                        .overlay {
                            Circle().stroke(Color.white, lineWidth: 2)
                        }
                        .padding(.top, 10)

                    if letters.count > 1 {
                        pageDots
                            .padding(.top, 16)
                    }

                    Spacer()
                }

                if letters.count > 1 {
                    HStack {
                        navigationButton(systemName: "chevron.left", label: "이전 편지", action: previous)
                        Spacer()
                        navigationButton(systemName: "chevron.right", label: "다음 편지", action: next)
                    }
                    .padding(.horizontal, 16)
                    .position(x: proxy.size.width / 2, y: proxy.size.height * 0.46)
                }

                VStack {
                    HStack {
                        Spacer()
                        VStack(spacing: 10) {
                            overlayActionButton(
                                systemName: "xmark",
                                label: "편지 닫기",
                                action: onDismiss
                            )
                            overlayActionButton(
                                systemName: isComposing ? "text.badge.xmark" : "square.and.pencil",
                                label: isComposing ? "편지 쓰기 취소" : "편지 쓰기"
                            ) {
                                if isComposing {
                                    isComposing = false
                                    isComposerFocused = false
                                } else if model.currentUser == nil {
                                    onOpenLogin()
                                } else {
                                    isComposing = true
                                    isComposerFocused = true
                                }
                            }
                        }
                    }
                    Spacer()
                }
                .padding(.top, proxy.safeAreaInsets.top + 10)
                .padding(.trailing, 14)
            }
        }
        .task(id: "\(coordinate.latitude),\(coordinate.longitude)") {
            await model.loadLocationLetters(at: coordinate)
        }
        .onChange(of: letters.map(\.id)) { _, ids in
            if ids.isEmpty {
                currentIndex = 0
            } else {
                currentIndex = min(currentIndex, ids.count - 1)
            }
        }
        .accessibilityIdentifier("location-letter-map-overlay")
    }

    private var callout: some View {
        VStack(alignment: .leading, spacing: 15) {
            if isComposing, model.currentUser != nil {
                composer
            } else {
                calloutContent
            }
            statusLine
        }
        .padding(17)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.black.opacity(0.78), lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.12), radius: 4, y: 2)
    }

    @ViewBuilder
    private var calloutContent: some View {
        if model.isLoadingLocationLetters {
            HStack(spacing: 10) {
                ProgressView()
                    .tint(.black)
                Text("불러오는 중")
                    .font(.subheadline)
                    .foregroundStyle(Color.black.opacity(0.64))
            }
            .frame(maxWidth: .infinity, minHeight: 72, alignment: .leading)
        } else if let selectedLetter {
            Text(selectedLetter.content)
                .font(.system(size: 18, weight: .regular, design: .serif))
                .foregroundStyle(Color.black.opacity(0.9))
                .lineSpacing(5)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Text(selectedLetter.authorLabel)
                    .font(.subheadline.weight(.semibold))
                Text(displayDate(selectedLetter.createdAt))
                    .font(.subheadline)
                    .foregroundStyle(Color.black.opacity(0.48))
                Spacer()
                Button {
                    if model.currentUser == nil {
                        onOpenLogin()
                    } else {
                        Task { _ = await model.toggleLocationLetterLike(selectedLetter) }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: selectedLetter.likedByMe ? "heart.fill" : "heart")
                        Text("\(selectedLetter.likeCount)")
                            .monospacedDigit()
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(selectedLetter.likedByMe ? Color.red.opacity(0.78) : Color.black.opacity(0.58))
                }
                .buttonStyle(.plain)
                .disabled(model.isUpdatingLocationLetterLike)
                .accessibilityLabel(selectedLetter.likedByMe ? "좋아요 취소" : "좋아요")

                if selectedLetter.mine || model.currentUser != nil {
                    letterMenu(selectedLetter)
                }
            }
        } else if let error = model.locationLetterErrorMessage {
            Text(error)
                .font(.body)
                .foregroundStyle(Color.black.opacity(0.76))
                .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
            Button("현재 위치 다시 확인") {
                Task { await model.loadLocationLetters(at: coordinate) }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.black)
        } else {
            Text("아직 남겨진 문장이 없습니다.")
                .font(.system(size: 18, weight: .regular, design: .serif))
                .foregroundStyle(Color.black.opacity(0.84))
                .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        if selectedLetter != nil || isComposing,
           let error = model.locationLetterErrorMessage {
            Text(error)
                .font(.caption)
                .foregroundStyle(Color.red.opacity(0.78))
        }
    }

    @ViewBuilder
    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
                TextField("남길 문장", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($isComposerFocused)
                    .font(.system(size: 15))
                    .padding(.vertical, 9)
                    .overlay(alignment: .bottom) {
                        Rectangle()
                            .fill(Color.black.opacity(0.24))
                            .frame(height: 1)
                    }
                    .onChange(of: draft) { _, value in
                        if value.count > 500 {
                            draft = String(value.prefix(500))
                        }
                    }

                Button(action: submit) {
                    Group {
                        if model.isSubmittingLocationLetter {
                            ProgressView().tint(.white)
                        } else {
                            Image(systemName: "arrow.up")
                                .font(.system(size: 15, weight: .bold))
                        }
                    }
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(Color.black, in: RoundedRectangle(cornerRadius: 9))
                }
                .buttonStyle(.plain)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.isSubmittingLocationLetter)
                .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.35 : 1)
                .accessibilityLabel("편지 남기기")
        }
    }

    private func overlayActionButton(
        systemName: String,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.black.opacity(0.78))
                .frame(width: 42, height: 42)
                .background(Color.white.opacity(0.92), in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var pageDots: some View {
        HStack(spacing: 6) {
            ForEach(Array(letters.indices.prefix(9)), id: \.self) { index in
                Circle()
                    .fill(index == currentIndex ? Color.black.opacity(0.76) : Color.white.opacity(0.88))
                    .frame(width: 7, height: 7)
            }
        }
        .accessibilityHidden(true)
    }

    private func navigationButton(systemName: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.black.opacity(0.62))
                .frame(width: 44, height: 44)
                .background(Color.white.opacity(0.92), in: Circle())
                .shadow(color: Color.black.opacity(0.08), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    @ViewBuilder
    private func letterMenu(_ letter: LocationLetter) -> some View {
        Menu {
            if letter.mine {
                Button(role: .destructive) {
                    Task { _ = await model.deleteLocationLetter(letter) }
                } label: {
                    Label("내 편지 거두기", systemImage: "trash")
                }
            } else if model.currentUser != nil {
                Menu("신고하기") {
                    reportButton("공격적인 표현", reason: "OFFENSIVE", letter: letter)
                    reportButton("괴롭힘", reason: "HARASSMENT", letter: letter)
                    reportButton("도배 또는 광고", reason: "SPAM", letter: letter)
                    reportButton("개인정보 노출", reason: "PERSONAL_INFORMATION", letter: letter)
                }
                Button(role: .destructive) {
                    Task { _ = await model.blockLocationLetterAuthor(letter) }
                } label: {
                    Label("이 사람의 편지 숨기기", systemImage: "person.crop.circle.badge.xmark")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.black.opacity(0.62))
                .frame(width: 34, height: 34)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("편지 메뉴")
    }

    private func reportButton(_ title: String, reason: String, letter: LocationLetter) -> some View {
        Button(title) {
            Task { _ = await model.reportLocationLetter(letter, reason: reason) }
        }
    }

    private func previous() {
        guard !letters.isEmpty else { return }
        currentIndex = (currentIndex - 1 + letters.count) % letters.count
    }

    private func next() {
        guard !letters.isEmpty else { return }
        currentIndex = (currentIndex + 1) % letters.count
    }

    private func submit() {
        let content = draft
        Task {
            if await model.submitLocationLetter(at: coordinate, content: content) {
                draft = ""
                currentIndex = 0
                isComposing = false
                isComposerFocused = false
            }
        }
    }

    private func displayDate(_ rawValue: String) -> String {
        let isoFormatter = ISO8601DateFormatter()
        let localFormatter = DateFormatter()
        localFormatter.locale = Locale(identifier: "en_US_POSIX")
        localFormatter.timeZone = .current
        localFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        let date = isoFormatter.date(from: rawValue)
            ?? ISO8601DateFormatter.withFractionalSeconds.date(from: rawValue)
            ?? localFormatter.date(from: String(rawValue.prefix(19)))
        guard let date else {
            return rawValue.prefix(10).replacingOccurrences(of: "-", with: ".")
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy. M. d."
        return formatter.string(from: date)
    }
}

private struct LocationLetterCalloutPointer: SwiftUI.Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
            path.closeSubpath()
        }
    }
}

private extension ISO8601DateFormatter {
    static var withFractionalSeconds: ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }
}

private final class LocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let manager = CLLocationManager()
    private var completion: ((CLLocationCoordinate2D) -> Void)?
    private var failure: ((String) -> Void)?
    private var hasRequestedLocation = false
    private var timeoutWorkItem: DispatchWorkItem?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func requestLocation(
        completion: @escaping (CLLocationCoordinate2D) -> Void,
        onFailure: @escaping (String) -> Void
    ) {
        self.completion = completion
        failure = onFailure
        isLoading = true
        errorMessage = nil
        hasRequestedLocation = false
        timeoutWorkItem?.cancel()
        let timeoutWorkItem = DispatchWorkItem { [weak self] in
            guard self?.isLoading == true else {
                return
            }
            self?.reportFailure("현재 위치 응답이 늦어지고 있습니다. 잠시 후 다시 시도해주세요.")
        }
        self.timeoutWorkItem = timeoutWorkItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: timeoutWorkItem)

        #if os(iOS)
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else {
            handleAuthorizationStatus()
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
        handleAuthorizationStatus()
        #else
        manager.requestLocation()
        #endif
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else {
            return
        }

        isLoading = false
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        completion?(coordinate)
        completion = nil
        failure = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        reportFailure("위치 확인 실패")
    }

    #if os(iOS)
    private func handleAuthorizationStatus() {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            guard !hasRequestedLocation else {
                return
            }
            hasRequestedLocation = true
            manager.requestLocation()
        case .restricted, .denied:
            reportFailure("위치 권한을 허용했는지 확인해주세요.")
        case .notDetermined:
            break
        @unknown default:
            reportFailure("위치 권한 상태를 확인할 수 없습니다.")
        }
    }
    #endif

    private func reportFailure(_ message: String) {
        isLoading = false
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        errorMessage = message
        completion = nil
        failure?(message)
        failure = nil
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
    @State private var isPolicyAccepted = false

    private let zoneTypes: [ReportZoneType] = [
        ReportZoneType(id: "부스", icon: "building.2.fill"),
        ReportZoneType(id: "개방", icon: "tree.fill"),
        ReportZoneType(id: "실내", icon: "shippingbox.fill")
    ]

    var body: some View {
        let photoData = selectedPhotoData

        VStack(spacing: 0) {
            sheetHeader

            ScrollView {
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

                    UgcPolicyConsentRow(isAccepted: $isPolicyAccepted)

                    HStack(spacing: 8) {
                        TextField("팁 추가 (선택)", text: $note)
                            .font(.system(size: 13, weight: .medium))
                            .padding(.horizontal, 12)
                            .frame(height: 44)
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
                        .background(
                            reportSubmitDisabled ? Color.nugulMutedText.opacity(0.35) : Color.nugulPrimary,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                        )
                        .buttonStyle(.plain)
                        .disabled(reportSubmitDisabled)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 18)
            }
            .scrollBounceBehavior(.basedOnSize)
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
                    .frame(width: 44, height: 44)
                    .background(Color.white, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("흡연구역 등록 닫기")

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

    private var reportSubmitDisabled: Bool {
        model.isSubmittingZone || isAddressLoading || !isPolicyAccepted
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

            ScrollView {
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
                                .font(.largeTitle.weight(.black))
                                .foregroundStyle(Color.nugulPrimary)
                            Text("대한민국 모든 너구리들의 쉼터")
                                .font(.headline.weight(.bold))
                                .foregroundStyle(Color.nugulMutedText)
                        }
                    }

                    VStack(spacing: 12) {
                        appleSignInButton
                        socialButton(title: "카카오로 3초만에 시작", color: Color(red: 1.0, green: 0.90, blue: 0.0), foreground: Color(red: 0.24, green: 0.12, blue: 0.12), icon: "message.fill", provider: .kakao)
                        socialButton(title: "네이버로 로그인", color: Color(red: 0.0, green: 0.42, blue: 0.18), foreground: .white, iconText: "N", provider: .naver)
                        socialButton(title: "구글로 로그인", color: .white, foreground: Color.nugulPrimary, icon: "g.circle.fill", provider: .google, needsBorder: true)
                    }

                    if model.isAuthLoading {
                        ProgressView("로그인 처리 중")
                            .font(.footnote.weight(.bold))
                            .foregroundStyle(Color.nugulMutedText)
                    }

                    if let message = model.errorMessage, !message.isEmpty {
                        Text(message)
                            .font(.footnote.weight(.bold))
                            .multilineTextAlignment(.center)
                            .foregroundStyle(Color.red.opacity(0.82))
                            .accessibilityLabel("로그인 오류: \(message)")
                    }

                    VStack(spacing: 4) {
                        Text("계속하면 서비스 약관과 개인정보 처리방침에 동의합니다.")
                            .font(.footnote.weight(.semibold))
                        VStack(spacing: 4) {
                            Link("서비스 이용약관", destination: AppConfig.termsURL)
                                .font(.body.weight(.semibold))
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            Link("개인정보 처리방침", destination: AppConfig.privacyPolicyURL)
                                .font(.body.weight(.semibold))
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                    }
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color.nugulMutedText)
                }
                .padding(28)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var appleSignInButton: some View {
        SignInWithAppleButton(.continue) { request in
            request.requestedScopes = [.fullName, .email]
        } onCompletion: { result in
            switch result {
            case let .success(authorization):
                Task {
                    await model.signInWithApple(authorization: authorization)
                }
            case let .failure(error):
                model.handleAppleSignInFailure(error)
            }
        }
        .signInWithAppleButtonStyle(.black)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 58)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .disabled(model.isAuthLoading)
        .accessibilityIdentifier("sign-in-with-apple")
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
                        .font(.title3.weight(.black))
                        .frame(width: 25, height: 25)
                        .foregroundStyle(foreground)
                        .background(color, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }

                Text(title)
                    .font(.headline.weight(.black))
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: 58)
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
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var selectedTab: ProfileTab = .profile
    @State private var showsDeleteAccountConfirmation = false
    @State private var accountDeletionNotice: String?

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
                ScrollView {
                    profileContent
                        .padding(.bottom, 24)
                }
            } else {
                myZonesContent
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .background(Color.white)
        .task {
            await model.loadUserZones()
        }
        .alert("Apple 로그인 연결 해제", isPresented: Binding(
            get: { accountDeletionNotice != nil },
            set: { isPresented in
                if !isPresented {
                    accountDeletionNotice = nil
                }
            }
        )) {
            Button("확인") {
                accountDeletionNotice = nil
                dismiss()
            }
        } message: {
            Text(accountDeletionNotice ?? "")
        }
        .alert("계정을 삭제하지 못했습니다", isPresented: Binding(
            get: { model.accountDeletionErrorMessage != nil },
            set: { isPresented in
                if !isPresented {
                    model.clearAccountDeletionError()
                }
            }
        )) {
            Button("확인") {
                model.clearAccountDeletionError()
            }
        } message: {
            Text(model.accountDeletionErrorMessage ?? "잠시 후 다시 시도해주세요.")
        }
    }

    private var header: some View {
        HStack(spacing: 18) {
            ProfileAvatar(user: model.currentUser)
                .scaleEffect(1.45)
                .frame(width: 72, height: 72)

            VStack(alignment: .leading, spacing: 5) {
                Text(model.currentUser?.displayName ?? "로그인이 필요합니다.")
                    .font(.title2.weight(.black))
                    .foregroundStyle(Color.nugulPrimary)
                Text(model.currentUser?.email ?? "다시 로그인해주세요.")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityHidden(true)
            }

            Spacer()
        }
    }

    private var profileContent: some View {
        VStack(spacing: 14) {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 12) {
                    SummaryPill(icon: "calendar", value: joinedDateText, label: "가입")
                    SummaryPill(icon: "mappin.and.ellipse", value: "\(model.userZones.count)", label: "장소 제보")
                }
            } else {
                HStack(spacing: 12) {
                    SummaryPill(icon: "calendar", value: joinedDateText, label: "가입")
                    SummaryPill(icon: "mappin.and.ellipse", value: "\(model.userZones.count)", label: "장소 제보")
                }
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
                .font(.headline.weight(.black))
                .foregroundStyle(Color.nugulDestructive)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Color.nugulDestructive.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)

            Button {
                showsDeleteAccountConfirmation = true
            } label: {
                HStack {
                    if model.isDeletingAccount {
                        ProgressView()
                            .tint(.red)
                    } else {
                        Image(systemName: "trash")
                    }
                    Text("계정 삭제")
                }
                .font(.headline.weight(.black))
                .foregroundStyle(Color.nugulDestructive)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Color.nugulDestructive.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(model.isDeletingAccount)
            .alert("계정을 삭제할까요?", isPresented: $showsDeleteAccountConfirmation) {
                Button("취소", role: .cancel) {}
                Button("삭제", role: .destructive) {
                    Task {
                        if await model.deleteAccount() {
                            if let notice = model.accountDeletionNotice {
                                accountDeletionNotice = notice
                            } else {
                                dismiss()
                            }
                        }
                    }
                }
            } message: {
                Text("계정, 프로필, 로그인 토큰, 작성한 리뷰, 제보한 장소와 첨부 이미지가 모두 삭제됩니다.")
            }
        }
    }

    private var myZonesContent: some View {
        Group {
            if model.isLoadingUserZones {
                ProgressView("내 장소 불러오는 중")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if model.userZones.isEmpty {
                if let message = model.userZonesErrorMessage {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundStyle(Color.orange)
                        Text(message)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.nugulMutedText)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "mappin.slash")
                            .font(.system(size: 42, weight: .bold))
                            .foregroundStyle(Color.nugulMutedText.opacity(0.35))
                        Text("아직 제보한 장소가 없습니다.")
                            .font(.system(size: 15, weight: .black))
                            .foregroundStyle(Color.nugulMutedText)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
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

        ScrollView {
            VStack(spacing: 22) {
                VStack(spacing: 8) {
                    Text("프로필 설정")
                        .font(.title.weight(.black))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(model.pendingSignupEmail ?? "닉네임을 설정하면 가입이 완료됩니다.")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(Color.nugulMutedText)
                        .multilineTextAlignment(.center)
                }

                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                    ProfilePhotoSlot(data: photoData)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("프로필 사진 선택")

                TextField("닉네임", text: $nickname)
                    .font(.body.weight(.bold))
                    .padding(.horizontal, 14)
                    .frame(minHeight: 52)
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
                            .frame(minHeight: 54)
                    } else {
                        Text("가입 완료")
                            .font(.headline.weight(.black))
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 54)
                    }
                }
                .foregroundStyle(.white)
                .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .buttonStyle(.plain)
                .disabled(model.isAuthLoading)
            }
            .padding(.horizontal, 26)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
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
                    .font(.headline.weight(.black))
                Text(label)
                    .font(.body.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.nugulMuted.opacity(0.75), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label)
        .accessibilityValue(value)
    }
}

private struct ProfileField: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.black))
                .foregroundStyle(Color.nugulMutedText)
            Text(value)
                .font(.body.weight(.bold))
                .foregroundStyle(Color.nugulPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.nugulMuted.opacity(0.65), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(accessibleProfileValue)
    }

    private var accessibleProfileValue: String {
        guard title == "이메일 계정" else {
            return value
        }
        return value
            .replacingOccurrences(of: "@", with: " 골뱅이 ")
            .replacingOccurrences(of: ".", with: " 점 ")
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
                    .frame(width: 44, height: 44)
                    .background(Color.red.opacity(0.08), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("내 장소 삭제")
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
    static let nugulDestructive = Color(red: 0.66, green: 0.05, blue: 0.08)
    static let nugulBorder = Color(red: 0.90, green: 0.90, blue: 0.90)
}
