import { Pressable, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"

interface ZoneCardProps {
  zone: SmokingZone
  isFavorite: boolean
  onSelect: () => void
  onToggleFavorite: () => void
}

export function ZoneCard({ zone, isFavorite, onSelect, onToggleFavorite }: ZoneCardProps) {
  return (
    <Pressable style={styles.card} onPress={onSelect}>
      <View style={styles.header}>
        <Text style={styles.title}>{zone.subtype || zone.region}</Text>
        <Pressable onPress={onToggleFavorite} style={[styles.badgeWrap, isFavorite && styles.badgeWrapActive]}>
          <Text style={[styles.badge, isFavorite && styles.badgeActive]}>{isFavorite ? "★" : "☆"}</Text>
        </Pressable>
      </View>
      <View style={styles.metaRow}>
        <Text style={styles.meta}>{zone.type}</Text>
        <Text style={styles.dot}>•</Text>
        <Text style={styles.metaMuted}>{zone.region}</Text>
      </View>
      <Text style={styles.description}>{zone.description || "설명이 없습니다."}</Text>
      <Text style={styles.address}>{zone.address}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    padding: 16,
    backgroundColor: "#ffffff",
    borderRadius: 20,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    marginBottom: 12,
    shadowColor: "#0f172a",
    shadowOpacity: 0.05,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6,
  },
  title: {
    fontSize: 16,
    fontWeight: "800",
    color: "#0f172a",
    flexShrink: 1,
    marginRight: 8,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginBottom: 8,
  },
  meta: {
    color: "#2563eb",
    fontWeight: "700",
  },
  metaMuted: {
    color: "#64748b",
    fontSize: 12,
    fontWeight: "600",
  },
  dot: {
    color: "#94a3b8",
  },
  description: {
    color: "#334155",
    marginBottom: 8,
  },
  address: {
    fontSize: 12,
    color: "#64748b",
  },
  badge: {
    fontSize: 16,
    color: "#94a3b8",
  },
  badgeActive: {
    color: "#f59e0b",
  },
  badgeWrap: {
    minWidth: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8fafc",
  },
  badgeWrapActive: {
    borderColor: "#fde68a",
    backgroundColor: "#fef3c7",
  },
})
