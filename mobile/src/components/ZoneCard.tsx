import { Image, Pressable, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { getImageUrl } from "../services/nugulApi"
import { colors, radius } from "../theme/tokens"

interface ZoneCardProps {
  zone: SmokingZone
  isFavorite: boolean
  onSelect: () => void
  onToggleFavorite: () => void
}

export function ZoneCard({ zone, isFavorite, onSelect, onToggleFavorite }: ZoneCardProps) {
  const imageUrl = getImageUrl(zone.image)
  const hasImage = Boolean(imageUrl)

  return (
    <Pressable style={styles.card} onPress={onSelect}>
      <View style={styles.cardBody}>
        <View style={styles.previewWrap}>
          {imageUrl ? (
            <Image source={{ uri: imageUrl }} style={styles.previewImage} testID="zone-card-image" />
          ) : (
            <View style={styles.previewPlaceholder} testID="zone-card-image-placeholder">
              <Text style={styles.previewPlaceholderText}>사진 없음</Text>
            </View>
          )}
        </View>

        <View style={styles.content}>
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
            <View style={[styles.photoBadge, hasImage && styles.photoBadgeActive]}>
              <Text style={[styles.photoBadgeText, hasImage && styles.photoBadgeTextActive]}>
                {hasImage ? "사진 있음" : "사진 없음"}
              </Text>
            </View>
          </View>

          <Text style={styles.description} numberOfLines={2}>
            {zone.description || "설명이 없습니다."}
          </Text>
          <Text style={styles.address} numberOfLines={2}>
            {zone.address}
          </Text>
        </View>
      </View>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    padding: 14,
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
  cardBody: {
    flexDirection: "row",
    gap: 12,
  },
  previewWrap: {
    width: 88,
    height: 88,
    borderRadius: 16,
    overflow: "hidden",
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
  },
  previewImage: {
    width: "100%",
    height: "100%",
  },
  previewPlaceholder: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 8,
  },
  previewPlaceholderText: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "700",
  },
  content: {
    flex: 1,
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
    flexWrap: "wrap",
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
    lineHeight: 19,
  },
  address: {
    fontSize: 12,
    color: colors.textMuted,
    lineHeight: 18,
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
  photoBadge: {
    marginLeft: "auto",
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  photoBadgeActive: {
    backgroundColor: colors.primarySoft,
  },
  photoBadgeText: {
    color: colors.textMuted,
    fontSize: 10,
    fontWeight: "700",
  },
  photoBadgeTextActive: {
    color: colors.text,
  },
})
