import { StatusBar } from "expo-status-bar"
import { SafeAreaView, StyleSheet, Text, View, ActivityIndicator, Pressable } from "react-native"
import { useState } from "react"
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


export default function App() {
  const [activeTab, setActiveTab] = useState<TabKey>("map")
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isProfileOpen, setIsProfileOpen] = useState(false)
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

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
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

      <View style={styles.container}>
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
  initialLoading: {
    position: "absolute",
    right: 16,
    top: 14,
  },
})
