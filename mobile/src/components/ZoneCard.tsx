import { Pressable, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"

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
    backgroundColor: colors.surface,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 12,
    shadowColor: colors.dark,
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
    color: colors.text,
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
    color: colors.primary,
    fontWeight: "700",
  },
  metaMuted: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "600",
  },
  dot: {
    color: colors.border,
  },
  description: {
    color: colors.textMuted,
    marginBottom: 8,
  },
  address: {
    fontSize: 12,
    color: colors.textMuted,
  },
  badge: {
    fontSize: 16,
    color: colors.border,
  },
  badgeActive: {
    color: colors.warning,
  },
  badgeWrap: {
    minWidth: 32,
    height: 32,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surfaceMuted,
  },
  badgeWrapActive: {
    borderColor: colors.border,
    backgroundColor: colors.primarySoft,
  },
})
