import { FlatList, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { ZoneCard } from "../components/ZoneCard"
import { colors } from "../theme/tokens"

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
      <Text style={styles.heading}>북마크한 장소</Text>
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
    backgroundColor: colors.bg,
  },
  heading: {
    marginTop: 10,
    marginHorizontal: 14,
    color: colors.text,
    fontWeight: "800",
    fontSize: 18,
  },
  list: {
    padding: 12,
    paddingTop: 10,
  },
  empty: {
    textAlign: "center",
    marginTop: 40,
    color: colors.textMuted,
  },
})
