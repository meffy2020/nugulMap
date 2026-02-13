import { StatusBar } from "expo-status-bar"
import {
  SafeAreaView,
  StyleSheet,
  Text,
  View,
  ActivityIndicator,
  Pressable,
  TextInput,
  Alert,
} from "react-native"
import { useState } from "react"
import * as Location from "expo-location"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { ZoneDetailModal } from "./src/components/ZoneDetailModal"
import { SimpleBottomTab } from "./src/components/SimpleBottomTab"
import { MapScreen } from "./src/screens/MapScreen"
import { ZoneListScreen } from "./src/screens/ZoneListScreen"
import { BookmarkScreen } from "./src/screens/BookmarkScreen"
import { useZoneExplorer } from "./src/features/zones/hooks/useZoneExplorer"
import type { TabKey } from "./src/navigation/tabs"
import { AddZoneModal } from "./src/components/AddZoneModal"
import { ProfileModal } from "./src/components/ProfileModal"
import { useAuth } from "./src/hooks/useAuth"
import { colors, radius } from "./src/theme/tokens"
import { searchZones } from "./src/services/nugulApi"


export default function App() {
  const [activeTab, setActiveTab] = useState<TabKey>("map")
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [isSearching, setIsSearching] = useState(false)
  const {
    region,
    zones,
    selectedZone,
    detailZone,
    isLoading,
    favoriteIds,
    handleRegionChangeComplete,
    toggleFavorite,
    openDetail,
    closeDetail,
    prependZone,
    refreshCurrentRegion,
  } = useZoneExplorer()
  const { accessToken, user, saveToken, clearToken } = useAuth()

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

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      {activeTab !== "map" ? (
        <View style={styles.header}>
          <View>
            <Text style={styles.kicker}>NugulMap</Text>
            <Text style={styles.title}>대한민국 흡연구역 지도</Text>
          </View>
          <View style={styles.headerActions}>
            <Pressable style={[styles.headerButton, styles.darkButton]} onPress={() => setIsAddOpen(true)}>
              <Text style={styles.headerButtonText}>등록</Text>
            </Pressable>
            <Pressable style={styles.headerButton} onPress={() => setIsProfileOpen(true)}>
              <Text style={styles.headerButtonText}>내 정보</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      <View style={styles.container}>
        {activeTab === "map" ? (
          <View style={styles.mapPage}>
            <MapScreen
              region={region}
              zones={zones}
              selectedZone={selectedZone}
              isLoading={isLoading}
              onRegionChangeComplete={handleRegionChangeComplete}
              onSelectZone={openDetail}
            />

            <View style={styles.topOverlay}>
              <View style={styles.searchShell}>
                <MaterialCommunityIcons name="magnify" size={18} color="#64748b" />
                <TextInput
                  value={searchQuery}
                  onChangeText={setSearchQuery}
                  onSubmitEditing={() => void handleTopSearch()}
                  placeholder="위치/구역 검색"
                  placeholderTextColor="#94a3b8"
                  style={styles.searchInput}
                />
                {isSearching ? <ActivityIndicator size="small" color={colors.primary} /> : null}
              </View>
              <Pressable style={styles.profileFab} onPress={() => setIsProfileOpen(true)}>
                <MaterialCommunityIcons name="account-circle-outline" size={21} color="#0f172a" />
              </Pressable>
            </View>

            <View style={styles.bottomOverlay}>
              <Pressable style={[styles.roundFab, styles.locationFab]} onPress={() => void moveToCurrentLocation()}>
                <MaterialCommunityIcons name="crosshairs-gps" size={22} color="#0f172a" />
              </Pressable>
              <Pressable style={[styles.roundFab, styles.addFab]} onPress={() => setIsAddOpen(true)}>
                <MaterialCommunityIcons name="plus" size={24} color="#ffffff" />
              </Pressable>
            </View>
          </View>
        ) : null}

        {activeTab === "list" ? (
          <ZoneListScreen
            zones={zones}
            favoriteIds={favoriteIds}
            onSelectZone={openDetail}
            onToggleFavorite={toggleFavorite}
          />
        ) : null}

        {activeTab === "bookmark" ? (
          <BookmarkScreen
            zones={zones}
            favoriteIds={favoriteIds}
            onSelectZone={openDetail}
            onToggleFavorite={toggleFavorite}
          />
        ) : null}
      </View>

      <SimpleBottomTab activeTab={activeTab} onChange={setActiveTab} />

      <ZoneDetailModal
        zone={detailZone}
        isFavorite={detailZone ? favoriteIds.has(detailZone.id) : false}
        onClose={closeDetail}
        onToggleFavorite={() => detailZone && toggleFavorite(detailZone.id)}
      />
      <AddZoneModal
        visible={isAddOpen}
        accessToken={accessToken}
        onClose={() => setIsAddOpen(false)}
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
        onSaveToken={saveToken}
        onClearToken={clearToken}
      />

      {isLoading && (
        <View style={styles.initialLoading}>
          <ActivityIndicator color="#0f172a" />
        </View>
      )}
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: colors.surface,
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
    shadowColor: colors.dark,
    shadowOpacity: 0.07,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 3,
  },
  kicker: {
    fontSize: 11,
    fontWeight: "800",
    color: colors.primary,
    textTransform: "uppercase",
    letterSpacing: 1,
  },
  title: {
    fontSize: 17,
    fontWeight: "800",
    color: colors.text,
  },
  container: {
    flex: 1,
  },
  headerActions: {
    flexDirection: "row",
    gap: 8,
  },
  headerButton: {
    backgroundColor: colors.primary,
    borderRadius: radius.full,
    paddingHorizontal: 14,
    paddingVertical: 9,
    shadowColor: colors.primary,
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  darkButton: {
    backgroundColor: colors.dark,
  },
  headerButtonText: {
    color: "#ffffff",
    fontWeight: "700",
    fontSize: 12,
  },
  mapPage: {
    flex: 1,
  },
  topOverlay: {
    position: "absolute",
    top: 10,
    left: 12,
    right: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
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
  profileFab: {
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
    bottom: 22,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
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
    backgroundColor: "rgba(255,255,255,0.96)",
    borderWidth: 1,
    borderColor: colors.border,
  },
  addFab: {
    backgroundColor: colors.primary,
  },
  initialLoading: {
    position: "absolute",
    right: 16,
    top: 14,
  },
})
