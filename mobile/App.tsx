import { StatusBar } from "expo-status-bar"
import { SafeAreaView, StyleSheet, Text, View, ActivityIndicator } from "react-native"
import { useState } from "react"
import { ZoneDetailModal } from "./src/components/ZoneDetailModal"
import { SimpleBottomTab } from "./src/components/SimpleBottomTab"
import { MapScreen } from "./src/screens/MapScreen"
import { ZoneListScreen } from "./src/screens/ZoneListScreen"
import { BookmarkScreen } from "./src/screens/BookmarkScreen"
import { useZoneExplorer } from "./src/features/zones/hooks/useZoneExplorer"
import type { TabKey } from "./src/navigation/tabs"


export default function App() {
  const [activeTab, setActiveTab] = useState<TabKey>("map")
  const {
    region,
    zones,
    selectedZone,
    detailZone,
    isLoading,
    favoriteIds,
    favoriteIdList,
    handleRegionChangeComplete,
    toggleFavorite,
    openDetail,
    closeDetail,
  } = useZoneExplorer()

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      <View style={styles.header}>
        <Text style={styles.title}>너굴맵</Text>
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
    backgroundColor: "#ffffff",
  },
  header: {
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: "#e2e8f0",
  },
  title: {
    fontSize: 20,
    fontWeight: "800",
    color: "#0f172a",
  },
  container: {
    flex: 1,
  },
  initialLoading: {
    position: "absolute",
    right: 16,
    top: 14,
  },
})
