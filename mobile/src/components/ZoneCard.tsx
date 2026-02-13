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
        <Pressable onPress={onToggleFavorite}>
          <Text style={[styles.badge, isFavorite && styles.badgeActive]}>{isFavorite ? "★" : "☆"}</Text>
        </Pressable>
      </View>
      <Text style={styles.meta}>{zone.type}</Text>
      <Text style={styles.description}>{zone.description || "설명이 없습니다."}</Text>
      <Text style={styles.address}>{zone.address}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    padding: 14,
    backgroundColor: "#ffffff",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    marginBottom: 10,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6,
  },
  title: {
    fontSize: 16,
    fontWeight: "700",
    color: "#0f172a",
    flexShrink: 1,
    marginRight: 8,
  },
  meta: {
    color: "#2563eb",
    marginBottom: 6,
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
    fontSize: 20,
    color: "#94a3b8",
  },
  badgeActive: {
    color: "#f59e0b",
  },
})
