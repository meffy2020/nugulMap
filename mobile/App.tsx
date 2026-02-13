import { StatusBar } from "expo-status-bar"
import {
  ActivityIndicator,
  Alert,
  Image,
  Linking,
  Modal,
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
import { useZoneExplorer } from "./src/features/zones/hooks/useZoneExplorer"
import { AddZoneModal } from "./src/components/AddZoneModal"
import { ProfileModal } from "./src/components/ProfileModal"
import { AppMenuModal } from "./src/components/AppMenuModal"
import { useAuth } from "./src/hooks/useAuth"
import { colors, radius } from "./src/theme/tokens"
import { searchZones } from "./src/services/nugulApi"

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
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [isSearching, setIsSearching] = useState(false)
  const [isReviewOpen, setIsReviewOpen] = useState(false)
  const [reviewText, setReviewText] = useState("")
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
    handleRegionChangeComplete,
    openDetail,
    closeDetail,
    prependZone,
    refreshCurrentRegion,
  } = useZoneExplorer()

  const { accessToken, user, clearToken } = useAuth()

  const moveToCurrentLocation = async () => {
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
      openDetail(first)
      handleRegionChangeComplete({
        latitude: first.latitude,
        longitude: first.longitude,
        latitudeDelta: 0.02,
        longitudeDelta: 0.02,
      })
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
    setReviewText("")
    setIsReviewOpen(true)
  }

  const openReportForZone = () => {
    if (!detailZone) return
    setReportSeed({
      latitude: detailZone.latitude,
      longitude: detailZone.longitude,
      address: detailZone.address,
      subtype: detailZone.subtype,
    })
    setIsAddOpen(true)
  }

  return (
    <View style={styles.root}>
      <StatusBar style="dark" translucent />

      <MapScreen
        region={region}
        zones={zones}
        selectedZone={selectedZone}
        isLoading={isLoading}
        onRegionChangeComplete={handleRegionChangeComplete}
        onSelectZone={openDetail}
      />

      <View style={[styles.topOverlay, { top: insets.top + 8 }]}>
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

        <Pressable style={styles.menuFab} onPress={() => setIsMenuOpen(true)}>
          <MaterialCommunityIcons name="menu" size={22} color={colors.text} />
        </Pressable>
      </View>

      <View style={[styles.bottomOverlay, { bottom: insets.bottom + 18 }]}>
        <Pressable style={[styles.roundFab, styles.locationFab]} onPress={() => void moveToCurrentLocation()}>
          <MaterialCommunityIcons name="crosshairs-gps" size={22} color={colors.surface} />
        </Pressable>

        <Pressable style={styles.addFab} onPress={() => setIsAddOpen(true)}>
          <Image source={pinImage} style={styles.addFabIcon} resizeMode="contain" />
          <Text style={styles.addFabText}>흡연구역 제보</Text>
        </Pressable>
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
        initialLatitude={reportSeed?.latitude}
        initialLongitude={reportSeed?.longitude}
        initialAddress={reportSeed?.address}
        initialSubtype={reportSeed?.subtype}
        onClose={() => {
          setIsAddOpen(false)
          setReportSeed(null)
        }}
        onCreated={(zone) => {
          prependZone(zone)
          void refreshCurrentRegion()
        }}
      />

      <ProfileModal
        visible={isProfileOpen}
        user={user}
        accessToken={accessToken}
        onClose={() => setIsProfileOpen(false)}
        onClearToken={clearToken}
      />
      <AppMenuModal
        visible={isMenuOpen}
        user={user}
        onClose={() => setIsMenuOpen(false)}
        onOpenProfile={() => setIsProfileOpen(true)}
        onLogout={clearToken}
      />

      {isLoading ? (
        <View style={[styles.initialLoading, { top: insets.top + 10 }]}> 
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : null}

      <Modal visible={isReviewOpen} animationType="slide" transparent={false}>
        <View style={styles.reviewRoot}>
          <View style={styles.reviewHeader}>
            <Text style={styles.reviewTitle}>리뷰 작성</Text>
            <Pressable onPress={() => setIsReviewOpen(false)}>
              <Text style={styles.reviewClose}>닫기</Text>
            </Pressable>
          </View>
          <Text style={styles.reviewHint}>해당 위치에 대한 간단한 후기를 작성해 주세요.</Text>
          <TextInput
            value={reviewText}
            onChangeText={setReviewText}
            style={styles.reviewInput}
            placeholder="좋은 점 / 개선점 / 혼잡도 ..."
            multiline
            textAlignVertical="top"
          />
          <Pressable
            style={styles.reviewSubmit}
            onPress={() => {
              Alert.alert("리뷰 제출", "리뷰 API 연동 전이라 임시 저장되었습니다.")
              setReviewText("")
              setIsReviewOpen(false)
            }}
          >
            <Text style={styles.reviewSubmitText}>제출</Text>
          </Pressable>
        </View>
      </Modal>
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
  bottomOverlay: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
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
  reviewRoot: {
    flex: 1,
    backgroundColor: colors.bg,
    paddingTop: 56,
    paddingHorizontal: 16,
  },
  reviewHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  reviewTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: colors.text,
  },
  reviewClose: {
    color: colors.text,
    fontWeight: "700",
  },
  reviewHint: {
    color: colors.textMuted,
    marginBottom: 10,
  },
  reviewInput: {
    minHeight: 180,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    paddingVertical: 12,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  reviewSubmit: {
    marginTop: 12,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    alignItems: "center",
    paddingVertical: 12,
  },
  reviewSubmitText: {
    color: colors.surface,
    fontWeight: "700",
  },
})
