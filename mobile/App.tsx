import { StatusBar } from "expo-status-bar"
import {
  ActivityIndicator,
  Alert,
  Image,
  Linking,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import { useEffect, useState } from "react"
import * as Location from "expo-location"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { SafeAreaProvider, useSafeAreaInsets } from "react-native-safe-area-context"
import { ZoneDetailModal } from "./src/components/ZoneDetailModal"
import { MapScreen } from "./src/screens/MapScreen"
import { ZoneListScreen } from "./src/screens/ZoneListScreen"
import { BookmarkScreen } from "./src/screens/BookmarkScreen"
import { useZoneExplorer } from "./src/features/zones/hooks/useZoneExplorer"
import { AddZoneModal } from "./src/components/AddZoneModal"
import { ProfileModal } from "./src/components/ProfileModal"
import { AppMenuModal } from "./src/components/AppMenuModal"
import { SimpleBottomTab } from "./src/components/SimpleBottomTab"
import { ZoneReviewModal } from "./src/components/ZoneReviewModal"
import type { TabKey } from "./src/navigation/tabs"
import { useAuth } from "./src/hooks/useAuth"
import { colors, radius } from "./src/theme/tokens"
import { fetchMapInsights, searchZones } from "./src/services/nugulApi"
import type { EventInsight, Hotplace, HotplaceInsight, InsightStatus, MapBounds, MapRegion, SmokingZone, TrendEvent } from "./src/types"

const pinImage = require("./assets/images/pin.png")

type InsightSelection = {
  title: string
  label: string
  detail?: string
  address: string
  latitude: number
  longitude: number
  source: string
}

export default function App() {
  return (
    <SafeAreaProvider>
      <AppContent />
    </SafeAreaProvider>
  )
}

function AppContent() {
  const insets = useSafeAreaInsets()
  const [activeTab, setActiveTab] = useState<TabKey>("map")
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [isSearching, setIsSearching] = useState(false)
  const [hotplaceInsight, setHotplaceInsight] = useState<HotplaceInsight | null>(null)
  const [eventInsight, setEventInsight] = useState<EventInsight | null>(null)
  const [insightStatus, setInsightStatus] = useState<InsightStatus | null>(null)
  const [selectedInsight, setSelectedInsight] = useState<InsightSelection | null>(null)
  const [isHotplaceLoading, setIsHotplaceLoading] = useState(false)
  const [reviewZone, setReviewZone] = useState<SmokingZone | null>(null)
  const [editingZone, setEditingZone] = useState<SmokingZone | null>(null)
  const [reportSeed, setReportSeed] = useState<{
    latitude: number
    longitude: number
    address?: string
    subtype?: string
  } | null>(null)

  const {
    region,
    zones,
    selectedZone,
    detailZone,
    isLoading,
    favoriteIds,
    errorMessage,
    handleRegionChangeComplete,
    toggleFavorite,
    openDetail,
    closeDetail,
    prependZone,
    refreshCurrentRegion,
    clearError,
  } = useZoneExplorer()

  const {
    accessToken,
    user,
    isLoggedIn,
    needsProfileSetup,
    clearToken,
    startSocialLogin,
    refreshUser,
    authMessage,
    clearAuthMessage,
    isAuthenticating,
    isLoading: isAuthLoading,
  } = useAuth()

  useEffect(() => {
    let isCancelled = false
    const timer = setTimeout(() => {
      void (async () => {
        setIsHotplaceLoading(true)
        const bounds = toInsightBounds(region)
        const mapInsight = await fetchMapInsights(undefined, 8, 8, bounds)
        if (!isCancelled) {
          setHotplaceInsight(mapInsight.hotplaces)
          setEventInsight(mapInsight.events)
          setInsightStatus(mapInsight.status)
          setIsHotplaceLoading(false)
        }
      })()
    }, 400)

    return () => {
      isCancelled = true
      clearTimeout(timer)
    }
  }, [region.latitude, region.longitude, region.latitudeDelta, region.longitudeDelta])

  const moveToCurrentLocation = async () => {
    try {
      const permission = await Location.requestForegroundPermissionsAsync()
      if (permission.status !== "granted") {
        Alert.alert("권한 필요", "현재 위치를 사용하려면 위치 권한이 필요합니다.")
        return
      }

      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
      handleRegionChangeComplete({
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
        latitudeDelta: region.latitudeDelta,
        longitudeDelta: region.longitudeDelta,
      })
    } catch {
      Alert.alert("위치 확인 실패", "현재 위치를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.")
    }
  }

  const focusZoneOnMap = (zone: SmokingZone) => {
    setActiveTab("map")
    setSelectedInsight(null)
    openDetail(zone)
    handleRegionChangeComplete({
      latitude: zone.latitude,
      longitude: zone.longitude,
      latitudeDelta: 0.02,
      longitudeDelta: 0.02,
    })
  }

  const focusHotplaceOnMap = (place: Hotplace) => {
    setActiveTab("map")
    setSelectedInsight(toHotplaceSelection(place))
    handleRegionChangeComplete({
      latitude: place.latitude,
      longitude: place.longitude,
      latitudeDelta: 0.018,
      longitudeDelta: 0.018,
    })
  }

  const focusEventOnMap = (event: TrendEvent) => {
    setActiveTab("map")
    setSelectedInsight(toEventSelection(event))
    handleRegionChangeComplete({
      latitude: event.latitude,
      longitude: event.longitude,
      latitudeDelta: 0.018,
      longitudeDelta: 0.018,
    })
  }

  const handleTopSearch = async () => {
    const query = searchQuery.trim()
    if (!query) return

    setIsSearching(true)
    try {
      const results = await searchZones(query, region.latitude, region.longitude)
      if (!results.length) {
        const mapInsight = await fetchMapInsights(query, 5, 5, toInsightBounds(region))
        setHotplaceInsight(mapInsight.hotplaces)
        setEventInsight(mapInsight.events)
        setInsightStatus(mapInsight.status)
        if (mapInsight.events.events.length) {
          focusEventOnMap(mapInsight.events.events[0])
          return
        }
        if (mapInsight.hotplaces.places.length) {
          focusHotplaceOnMap(mapInsight.hotplaces.places[0])
          return
        }

        Alert.alert("검색 결과 없음", "해당 키워드로 찾은 장소가 없습니다.")
        return
      }

      const first = results[0]
      focusZoneOnMap(first)
    } catch {
      Alert.alert("검색 실패", "네트워크 상태를 확인한 뒤 다시 시도해 주세요.")
    } finally {
      setIsSearching(false)
    }
  }

  const openRouteForZone = () => {
    if (!detailZone) return
    const mapUrl = `https://www.google.com/maps/dir/?api=1&destination=${detailZone.latitude},${detailZone.longitude}`
    void Linking.openURL(mapUrl)
  }

  const openShareForZone = async () => {
    if (!detailZone) return
    await Share.share({
      title: detailZone.subtype || detailZone.address,
      message: `${detailZone.subtype || "흡연구역"}\n${detailZone.address}\nhttps://www.google.com/maps/search/?api=1&query=${detailZone.latitude},${detailZone.longitude}`,
    })
  }

  const openReviewForZone = () => {
    if (!detailZone) return
    setReviewZone(detailZone)
    closeDetail()
  }

  const openReportForZone = () => {
    if (!detailZone) return
    setEditingZone(null)
    setReportSeed({
      latitude: detailZone.latitude,
      longitude: detailZone.longitude,
      address: detailZone.address,
      subtype: detailZone.subtype,
    })
    closeDetail()
    setIsAddOpen(true)
  }

  const openCreateZone = () => {
    setEditingZone(null)
    setReportSeed(null)
    setIsAddOpen(true)
  }

  const openEditZone = (zone: SmokingZone) => {
    setIsProfileOpen(false)
    setEditingZone(zone)
    setReportSeed(null)
    setIsAddOpen(true)
  }

  const handleZoneSaved = (zone: SmokingZone) => {
    prependZone(zone)
    void refreshCurrentRegion()
  }

  const closeZoneModal = () => {
    setIsAddOpen(false)
    setReportSeed(null)
    setEditingZone(null)
  }

  const activeScreenTitle = activeTab === "list" ? "전체 흡연구역" : "북마크한 장소"

  return (
    <View style={styles.root}>
      <StatusBar style="dark" translucent />

      {activeTab === "map" ? (
        <MapScreen
          region={region}
          zones={zones}
          hotplaces={hotplaceInsight?.places || []}
          events={eventInsight?.events || []}
          selectedZone={selectedZone}
          isLoading={isLoading}
          onRegionChangeComplete={handleRegionChangeComplete}
          onSelectZone={(zone) => {
            setSelectedInsight(null)
            openDetail(zone)
          }}
          onSelectHotplace={(place) => setSelectedInsight(toHotplaceSelection(place))}
          onSelectEvent={(event) => setSelectedInsight(toEventSelection(event))}
        />
      ) : null}

      {activeTab === "list" ? (
        <ZoneListScreen
          zones={zones}
          favoriteIds={favoriteIds}
          onSelectZone={focusZoneOnMap}
          onToggleFavorite={(zoneId) => void toggleFavorite(zoneId)}
        />
      ) : null}

      {activeTab === "bookmark" ? (
        <BookmarkScreen
          zones={zones}
          favoriteIds={favoriteIds}
          onSelectZone={focusZoneOnMap}
          onToggleFavorite={(zoneId) => void toggleFavorite(zoneId)}
        />
      ) : null}

      <View style={[styles.topOverlay, { top: insets.top + 8 }]}>
        {activeTab === "map" ? (
          <View style={styles.searchShell}>
            <MaterialCommunityIcons name="magnify" size={18} color={colors.textMuted} />
            <TextInput
              value={searchQuery}
              onChangeText={setSearchQuery}
              onSubmitEditing={() => void handleTopSearch()}
              placeholder="장소, 주소 검색..."
              placeholderTextColor={colors.textMuted}
              style={styles.searchInput}
            />
            {searchQuery ? (
              <Pressable onPress={() => setSearchQuery("")} style={styles.clearButton}>
                <MaterialCommunityIcons name="close" size={15} color={colors.textMuted} />
              </Pressable>
            ) : null}
            <View style={styles.searchDivider} />
            <Pressable onPress={() => void handleTopSearch()}>
              {isSearching ? (
                <ActivityIndicator size="small" color={colors.primary} />
              ) : (
                <Text style={styles.searchAction}>검색</Text>
              )}
            </Pressable>
          </View>
        ) : (
          <View style={styles.pageBadge}>
            <Text style={styles.pageBadgeText}>{activeScreenTitle}</Text>
          </View>
        )}

        <Pressable style={styles.menuFab} onPress={() => setIsMenuOpen(true)}>
          <MaterialCommunityIcons name="menu" size={22} color={colors.text} />
        </Pressable>
      </View>

      {activeTab === "map" ? (
        <View style={[styles.hotplacePanel, { top: insets.top + 62 }]}>
          <Text style={styles.season2Status} numberOfLines={1}>
            {formatInsightStatus(insightStatus)}
          </Text>
          <View style={styles.hotplaceHeader}>
            <View style={styles.hotplaceTitleRow}>
              <MaterialCommunityIcons name="fire" size={16} color={colors.primary} />
              <Text style={styles.hotplaceTitle}>지금 핫한 곳</Text>
            </View>
            <Text style={styles.hotplaceFreshness}>
              {formatHotplacePanelStatus(hotplaceInsight, isHotplaceLoading)}
            </Text>
          </View>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.hotplaceList}>
            {(hotplaceInsight?.places || []).map((place) => (
              <Pressable key={place.id} style={styles.hotplaceChip} onPress={() => focusHotplaceOnMap(place)}>
                <Text style={styles.hotplaceName} numberOfLines={1}>
                  {place.name}
                </Text>
                <Text style={styles.hotplaceMeta} numberOfLines={1}>
                  {formatCrowdLabel(place)}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
          {(eventInsight?.events || []).length ? (
            <>
              <View style={styles.eventDivider} />
              <View style={styles.hotplaceHeader}>
                <View style={styles.hotplaceTitleRow}>
                  <MaterialCommunityIcons name="calendar-star" size={16} color={colors.primary} />
                  <Text style={styles.hotplaceTitle}>팝업·행사·축제</Text>
                </View>
                <Text style={styles.hotplaceFreshness}>
                  {formatEventPanelStatus(eventInsight)}
                </Text>
              </View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.hotplaceList}>
                {(eventInsight?.events || []).map((event) => (
                  <Pressable key={event.id} style={styles.eventChip} onPress={() => focusEventOnMap(event)}>
                    <Text style={styles.hotplaceName} numberOfLines={1}>
                      {event.title}
                    </Text>
                    <Text style={styles.hotplaceMeta} numberOfLines={1}>
                      {formatEventLabel(event)}
                    </Text>
                  </Pressable>
                ))}
              </ScrollView>
            </>
          ) : null}
        </View>
      ) : null}

      {errorMessage ? (
        <View style={[styles.errorBanner, { top: insets.top + (activeTab === "map" ? 176 : 62) }]}>
          <MaterialCommunityIcons name="alert-circle-outline" size={17} color={colors.surface} />
          <Text style={styles.errorBannerText}>{errorMessage}</Text>
          <Pressable
            style={styles.errorRetryButton}
            onPress={() => {
              clearError()
              void refreshCurrentRegion()
            }}
          >
            <Text style={styles.errorRetryButtonText}>재시도</Text>
          </Pressable>
        </View>
      ) : null}

      {activeTab === "map" && selectedInsight ? (
        <View style={[styles.insightCard, { bottom: insets.bottom + 154 }]}>
          <View style={styles.insightCardHeader}>
            <View style={styles.insightTitleWrap}>
              <Text style={styles.insightTitle} numberOfLines={1}>
                {selectedInsight.title}
              </Text>
              <Text style={styles.insightLabel} numberOfLines={1}>
                {selectedInsight.label}
              </Text>
            </View>
            <Pressable style={styles.insightCloseButton} onPress={() => setSelectedInsight(null)}>
              <MaterialCommunityIcons name="close" size={17} color={colors.textMuted} />
            </Pressable>
          </View>
          <Text style={styles.insightAddress} numberOfLines={2}>
            {selectedInsight.address || "주소 정보 없음"}
          </Text>
          {selectedInsight.detail ? (
            <Text style={styles.insightDetail} numberOfLines={2}>
              {selectedInsight.detail}
            </Text>
          ) : null}
          <View style={styles.insightActionRow}>
            <Text style={styles.insightSource} numberOfLines={1}>
              {selectedInsight.source}
            </Text>
            <Pressable style={styles.insightRouteButton} onPress={() => openRouteForInsight(selectedInsight)}>
              <MaterialCommunityIcons name="navigation-variant-outline" size={15} color={colors.surface} />
              <Text style={styles.insightRouteText}>길찾기</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      {activeTab === "map" ? (
        <View style={[styles.mapActionOverlay, { bottom: insets.bottom + 86 }]}>
          <Pressable style={[styles.roundFab, styles.locationFab]} onPress={() => void moveToCurrentLocation()}>
            <MaterialCommunityIcons name="crosshairs-gps" size={22} color={colors.surface} />
          </Pressable>

          <Pressable style={styles.addFab} onPress={openCreateZone}>
            <Image source={pinImage} style={styles.addFabIcon} resizeMode="contain" />
            <Text style={styles.addFabText}>흡연구역 제보</Text>
          </Pressable>
        </View>
      ) : null}

      <View style={[styles.tabOverlay, { bottom: insets.bottom + 8 }]}>
        <SimpleBottomTab activeTab={activeTab} onChange={setActiveTab} />
      </View>

      <ZoneDetailModal
        zone={detailZone}
        onClose={closeDetail}
        onOpenRoute={openRouteForZone}
        onOpenShare={openShareForZone}
        onOpenReport={openReportForZone}
        onOpenReview={openReviewForZone}
      />

      <AddZoneModal
        visible={isAddOpen}
        accessToken={accessToken}
        editingZone={editingZone}
        initialLatitude={reportSeed?.latitude}
        initialLongitude={reportSeed?.longitude}
        initialAddress={reportSeed?.address}
        initialSubtype={reportSeed?.subtype}
        onClose={closeZoneModal}
        onCreated={handleZoneSaved}
        onUpdated={handleZoneSaved}
      />

      <ProfileModal
        visible={isProfileOpen}
        user={user}
        accessToken={accessToken}
        onClose={() => setIsProfileOpen(false)}
        onClearToken={clearToken}
        onSocialLogin={startSocialLogin}
        needsProfileSetup={needsProfileSetup}
        onProfileUpdated={refreshUser}
        onEditZone={openEditZone}
        authMessage={authMessage}
        onClearAuthMessage={clearAuthMessage}
        isAuthenticating={isAuthenticating}
      />

      <ZoneReviewModal
        visible={Boolean(reviewZone)}
        zone={reviewZone}
        accessToken={accessToken}
        user={user}
        onClose={() => setReviewZone(null)}
      />

      <AppMenuModal
        visible={isMenuOpen}
        user={user}
        isLoggedIn={isLoggedIn}
        onClose={() => setIsMenuOpen(false)}
        onOpenProfile={() => setIsProfileOpen(true)}
        onLogin={() => startSocialLogin("kakao")}
        onLogout={clearToken}
      />

      {isLoading || isAuthLoading ? (
        <View style={[styles.initialLoading, { top: insets.top + 10 }]}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : null}
    </View>
  )
}

function formatCrowdLabel(place: Hotplace): string {
  if (place.crowdLevel && place.crowdLevel !== "UNKNOWN") {
    const count =
      place.estimatedMinPeople != null && place.estimatedMaxPeople != null
        ? ` · ${place.estimatedMinPeople.toLocaleString()}-${place.estimatedMaxPeople.toLocaleString()}명`
        : ""
    return `${place.crowdLevel}${count}`
  }

  return place.category === "popup" ? "팝업 후보" : "핫플 후보"
}

function formatEventLabel(event: TrendEvent): string {
  const kind = event.kind === "popup" ? "팝업" : event.kind === "festival" ? "축제" : "행사"
  return event.period ? `${kind} · ${event.period}` : kind
}

function formatInsightStatus(status: InsightStatus | null): string {
  if (!status) {
    return "데이터 상태 확인 중"
  }

  return [formatProviderMode(status.hotplaceMode, "혼잡"), formatTelecomStatus(status), formatEventStatus(status)]
    .filter(Boolean)
    .join(" · ")
}

function formatTelecomStatus(status: InsightStatus): string | null {
  if (status.telecomCrowd?.qualityStatus === "OK") {
    return "통신사 연결"
  }
  if (status.telecomCrowdKeyConfigured && !status.telecomCrowdUrlTemplateConfigured) {
    return "통신사 URL 필요"
  }
  if (status.telecomCrowdKeyConfigured && status.telecomCrowd?.qualityStatus === "CONFIGURED_UNVERIFIED") {
    return "통신사 확인중"
  }
  if (status.telecomCrowd?.qualityStatus === "ERROR") {
    return "통신사 오류"
  }
  return null
}

function formatEventStatus(status: InsightStatus): string {
  const hasLiveTourApi = status.ktoTourApi?.qualityStatus === "OK"
  const hasSeoulCultureApi = status.seoulCultureApi?.qualityStatus === "OK"
  const liveEventApiLabel = formatLiveEventApiLabel(hasLiveTourApi, hasSeoulCultureApi)
  const popupCount = status.popupTrends?.qualityStatus === "OK" ? status.popupTrends.recordCount : 0

  if (liveEventApiLabel && popupCount > 0) {
    return `${liveEventApiLabel}+팝업 ${popupCount}건`
  }
  if (liveEventApiLabel) {
    return liveEventApiLabel
  }
  if (status.seoulCultureApiKeyConfigured) {
    return "서울문화 API 확인"
  }
  if (popupCount > 0) {
    return `팝업 ${popupCount}건`
  }
  return formatProviderMode(status.eventMode, "행사")
}

function formatLiveEventApiLabel(hasLiveTourApi: boolean, hasSeoulCultureApi: boolean): string | null {
  if (hasLiveTourApi && hasSeoulCultureApi) {
    return "행사 API"
  }
  if (hasSeoulCultureApi) {
    return "서울문화 API"
  }
  if (hasLiveTourApi) {
    return "TourAPI"
  }
  return null
}

function formatProviderMode(mode: string, label: string): string {
  switch (mode) {
    case "LIVE_READY":
    case "LIVE_OR_CRAWLED_READY":
      return `${label} 실시간`
    case "LIVE_CONFIGURED_UNVERIFIED":
      return `${label} 확인중`
    case "LIVE_CONFIGURED_ERROR":
      return `${label} 오류`
    default:
      return `${label} 후보`
  }
}

function formatHotplacePanelStatus(insight: HotplaceInsight | null, isLoading: boolean): string {
  if (isLoading) {
    return "갱신 중"
  }
  if ((insight?.places || []).some((place) => place.source === "TELECOM_CROWD")) {
    return "통신사"
  }
  if (insight?.dataFreshness === "LIVE_OR_PARTIAL") {
    return "실시간"
  }
  return "후보"
}

function formatEventPanelStatus(insight: EventInsight | null): string {
  if (insight?.dataFreshness === "LIVE_OR_PARTIAL") {
    return "API"
  }
  if (insight?.dataFreshness === "CRAWLED_OR_PARTIAL") {
    return "크롤링"
  }
  return "후보"
}

function toHotplaceSelection(place: Hotplace): InsightSelection {
  return {
    title: place.name,
    label: formatCrowdLabel(place),
    detail: formatHotplaceDetail(place),
    address: place.address,
    latitude: place.latitude,
    longitude: place.longitude,
    source: formatHotplaceSource(place.source),
  }
}

function toEventSelection(event: TrendEvent): InsightSelection {
  return {
    title: event.title,
    label: formatEventLabel(event),
    detail: event.sourceContentId ? `출처 ID ${event.sourceContentId}` : undefined,
    address: event.address,
    latitude: event.latitude,
    longitude: event.longitude,
    source: formatEventSource(event.source),
  }
}

function formatHotplaceSource(source: string): string {
  if (source === "TELECOM_CROWD") {
    return "통신사 장소 혼잡도"
  }
  if (source === "SEOUL_CITYDATA") {
    return "서울 실시간 도시데이터"
  }
  return "핫플 후보"
}

function formatEventSource(source: string): string {
  if (source === "KTO_TOUR_API") {
    return "한국관광공사 TourAPI"
  }
  if (source === "SEOUL_CULTURE_API") {
    return "서울 문화행사 API"
  }
  if (source === "CRAWLED_POPUP_TREND") {
    return "크롤링 팝업 트렌드"
  }
  return "이벤트 후보"
}

function formatHotplaceDetail(place: Hotplace): string | undefined {
  const parts = [
    place.crowdMessage && place.crowdMessage !== "실시간 혼잡도 키가 없거나 해당 장소 응답을 받을 수 없어 후보 장소만 표시합니다."
      ? place.crowdMessage
      : undefined,
    place.updatedAt ? `갱신 ${place.updatedAt}` : undefined,
    place.sourcePlaceCode ? `장소 ${place.sourcePlaceCode}` : undefined,
  ].filter(Boolean)

  return parts.length ? parts.join(" · ") : undefined
}

function openRouteForInsight(selection: InsightSelection) {
  const mapUrl = `https://www.google.com/maps/dir/?api=1&destination=${selection.latitude},${selection.longitude}`
  void Linking.openURL(mapUrl)
}

function toInsightBounds(region: MapRegion): MapBounds {
  return {
    minLat: region.latitude - region.latitudeDelta / 2,
    maxLat: region.latitude + region.latitudeDelta / 2,
    minLng: region.longitude - region.longitudeDelta / 2,
    maxLng: region.longitude + region.longitudeDelta / 2,
  }
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  topOverlay: {
    position: "absolute",
    left: 12,
    right: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    zIndex: 20,
  },
  searchShell: {
    flex: 1,
    backgroundColor: "rgba(255,255,255,0.96)",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.full,
    paddingHorizontal: 14,
    height: 46,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    shadowColor: colors.dark,
    shadowOpacity: 0.12,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  pageBadge: {
    flex: 1,
    height: 46,
    borderRadius: radius.full,
    backgroundColor: "rgba(255,255,255,0.96)",
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: colors.dark,
    shadowOpacity: 0.1,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
  pageBadgeText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "800",
  },
  searchInput: {
    flex: 1,
    color: colors.text,
    fontSize: 14,
    fontWeight: "600",
  },
  clearButton: {
    width: 22,
    height: 22,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surfaceMuted,
  },
  searchDivider: {
    width: 1,
    height: 20,
    backgroundColor: colors.border,
  },
  searchAction: {
    color: colors.primary,
    fontWeight: "700",
    fontSize: 13,
  },
  menuFab: {
    width: 46,
    height: 46,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.96)",
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: colors.dark,
    shadowOpacity: 0.12,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  errorBanner: {
    position: "absolute",
    left: 12,
    right: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: radius.md,
    backgroundColor: "rgba(23,23,23,0.92)",
    zIndex: 25,
  },
  errorBannerText: {
    flex: 1,
    color: colors.surface,
    fontSize: 12,
    fontWeight: "700",
  },
  errorRetryButton: {
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.25)",
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  errorRetryButtonText: {
    color: colors.surface,
    fontSize: 11,
    fontWeight: "800",
  },
  hotplacePanel: {
    position: "absolute",
    left: 12,
    right: 12,
    zIndex: 18,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "rgba(255,255,255,0.96)",
    paddingVertical: 10,
    shadowColor: colors.dark,
    shadowOpacity: 0.1,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  hotplaceHeader: {
    paddingHorizontal: 12,
    marginBottom: 8,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  season2Status: {
    marginBottom: 8,
    paddingHorizontal: 12,
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "800",
  },
  hotplaceTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  hotplaceTitle: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "800",
  },
  hotplaceFreshness: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "800",
  },
  hotplaceList: {
    paddingHorizontal: 12,
    gap: 8,
  },
  hotplaceChip: {
    width: 132,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    paddingVertical: 9,
  },
  eventChip: {
    width: 148,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 10,
    paddingVertical: 9,
  },
  eventDivider: {
    height: 1,
    backgroundColor: colors.border,
    marginTop: 10,
    marginBottom: 10,
  },
  hotplaceName: {
    color: colors.text,
    fontSize: 12,
    fontWeight: "800",
    marginBottom: 4,
  },
  hotplaceMeta: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "700",
  },
  insightCard: {
    position: "absolute",
    left: 16,
    right: 16,
    zIndex: 22,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "rgba(255,255,255,0.98)",
    padding: 14,
    shadowColor: colors.dark,
    shadowOpacity: 0.16,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 5,
  },
  insightCardHeader: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 10,
  },
  insightTitleWrap: {
    flex: 1,
  },
  insightTitle: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "900",
    marginBottom: 4,
  },
  insightLabel: {
    color: colors.primary,
    fontSize: 12,
    fontWeight: "800",
  },
  insightCloseButton: {
    width: 30,
    height: 30,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surfaceMuted,
  },
  insightAddress: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "600",
    lineHeight: 18,
    marginTop: 8,
  },
  insightDetail: {
    color: colors.text,
    fontSize: 12,
    fontWeight: "700",
    lineHeight: 18,
    marginTop: 6,
  },
  insightActionRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 10,
    marginTop: 12,
  },
  insightSource: {
    flex: 1,
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "800",
  },
  insightRouteButton: {
    height: 34,
    borderRadius: radius.full,
    paddingHorizontal: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: colors.primary,
  },
  insightRouteText: {
    color: colors.surface,
    fontSize: 12,
    fontWeight: "900",
  },
  mapActionOverlay: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    zIndex: 20,
  },
  tabOverlay: {
    position: "absolute",
    left: 0,
    right: 0,
    zIndex: 20,
  },
  roundFab: {
    width: 54,
    height: 54,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: colors.dark,
    shadowOpacity: 0.16,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 4,
  },
  locationFab: {
    backgroundColor: "rgba(23,23,23,0.78)",
    borderWidth: 2,
    borderColor: colors.surface,
  },
  addFab: {
    backgroundColor: colors.primary,
    height: 56,
    borderRadius: 16,
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 14,
    gap: 8,
    shadowColor: colors.dark,
    shadowOpacity: 0.2,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 5 },
    elevation: 4,
  },
  addFabIcon: {
    width: 18,
    height: 18,
    tintColor: colors.surface,
  },
  addFabText: {
    color: colors.surface,
    fontSize: 14,
    fontWeight: "800",
  },
  initialLoading: {
    position: "absolute",
    right: 16,
  },
})
