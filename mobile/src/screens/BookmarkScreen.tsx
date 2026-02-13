import { FlatList, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { ZoneCard } from "../components/ZoneCard"

interface BookmarkScreenProps {
  zones: SmokingZone[]
  favoriteIds: Set<number>
  onSelectZone: (zone: SmokingZone) => void
  onToggleFavorite: (zoneId: number) => void
}

export function BookmarkScreen({
  zones,
  favoriteIds,
  onSelectZone,
  onToggleFavorite,
}: BookmarkScreenProps) {
  const bookmarked = zones.filter((zone) => favoriteIds.has(zone.id))

  return (
    <View style={styles.wrap}>
      <FlatList
        data={bookmarked}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <ZoneCard
            zone={item}
            isFavorite
            onSelect={() => onSelectZone(item)}
            onToggleFavorite={() => onToggleFavorite(item.id)}
          />
        )}
        ListEmptyComponent={<Text style={styles.empty}>아직 북마크한 장소가 없습니다.</Text>}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
  list: {
    padding: 12,
  },
  empty: {
    textAlign: "center",
    marginTop: 40,
    color: "#64748b",
  },
})
