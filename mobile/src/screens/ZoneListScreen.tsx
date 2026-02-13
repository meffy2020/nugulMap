import { useMemo, useState } from "react"
import { FlatList, StyleSheet, Text, TextInput, View } from "react-native"
import type { SmokingZone } from "../types"
import { ZoneCard } from "../components/ZoneCard"
import { searchZones } from "../services/nugulApi"

interface ZoneListScreenProps {
  zones: SmokingZone[]
  favoriteIds: Set<number>
  onSelectZone: (zone: SmokingZone) => void
  onToggleFavorite: (zoneId: number) => void
}

export function ZoneListScreen({
  zones,
  favoriteIds,
  onSelectZone,
  onToggleFavorite,
}: ZoneListScreenProps) {
  const [query, setQuery] = useState("")
  const [remoteResults, setRemoteResults] = useState<SmokingZone[] | null>(null)

  const search = async (nextQuery: string) => {
    setQuery(nextQuery)
    if (!nextQuery.trim()) {
      setRemoteResults(null)
      return
    }

    const result = await searchZones(nextQuery)
    setRemoteResults(result)
  }

  const items = useMemo(() => {
    if (remoteResults) {
      return remoteResults
    }

    const q = query.trim().toLowerCase()
    if (!q) return zones

    return zones.filter((zone) => {
      return [
        zone.region,
        zone.address,
        zone.subtype,
        zone.type,
        zone.description,
      ].some((value) => value.toLowerCase().includes(q))
    })
  }, [query, remoteResults, zones])

  return (
    <View style={styles.wrap}>
      <Text style={styles.heading}>전체 흡연구역</Text>
      <TextInput
        value={query}
        onChangeText={search}
        placeholder="검색(이름/주소/설명)"
        style={styles.input}
      />
      <FlatList
        data={items}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <ZoneCard
            zone={item}
            isFavorite={favoriteIds.has(item.id)}
            onSelect={() => onSelectZone(item)}
            onToggleFavorite={() => onToggleFavorite(item.id)}
          />
        )}
        ListEmptyComponent={<Text style={styles.empty}>표시할 구역이 없습니다.</Text>}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
  heading: {
    marginTop: 10,
    marginHorizontal: 14,
    color: "#0f172a",
    fontWeight: "800",
    fontSize: 18,
  },
  input: {
    backgroundColor: "#ffffff",
    marginTop: 10,
    marginHorizontal: 12,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 11,
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
