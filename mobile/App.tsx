import { StatusBar } from "expo-status-bar"
import {
  ActivityIndicator,
  Alert,
  Image,
  Linking,
  Pressable,
  Share,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import { useState } from "react"
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
import { searchZones } from "./src/services/nugulApi"
import type { SmokingZone } from "./src/types"

const pinImage = require("./assets/images/pin.png")

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
    openDetail(zone)
    handleRegionChangeComplete({
      latitude: zone.latitude,
      longitude: zone.longitude,
      latitudeDelta: 0.02,
      longitudeDelta: 0.02,
    })
  }

  const handleTopSearch = async () => {
    if (!searchQuery.trim()) return

    setIsSearching(true)
    try {
      const results = await searchZones(searchQuery.trim(), region.latitude, region.longitude)
      if (!results.length) {
        Alert.alert("검색 결과 없음", "해당 키워드로 찾은 흡연구역이 없습니다.")
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
          selectedZone={selectedZone}
          isLoading={isLoading}
          onRegionChangeComplete={handleRegionChangeComplete}
          onSelectZone={openDetail}
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

      {errorMessage ? (
        <View style={[styles.errorBanner, { top: insets.top + 62 }]}>
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
