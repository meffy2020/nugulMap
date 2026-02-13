import { Image, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { getImageUrl } from "../services/nugulApi"

interface ZoneDetailModalProps {
  zone: SmokingZone | null
  onClose: () => void
  onOpenRoute: () => void
  onOpenShare: () => void
  onOpenReport: () => void
  onOpenReview: () => void
}

function normalizeText(value: string | null | undefined): string {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim()
}

export function ZoneDetailModal({
  zone,
  onClose,
  onOpenRoute,
  onOpenShare,
  onOpenReport,
  onOpenReview,
}: ZoneDetailModalProps) {
  if (!zone) {
    return null
  }

  const imageUrl = getImageUrl(zone.image)
  const address = normalizeText(zone.address)
  const description = normalizeText(zone.description)
  const genericDescription = normalizeText(`${zone.subtype || ""} 흡연구역`)
  const shouldShowDescription =
    description.length > 0 && description !== address && description !== genericDescription

  return (
    <Modal visible={Boolean(zone)} animationType="slide" transparent>
      <View style={styles.overlay}>
        <View style={styles.sheet}>
          <View style={styles.dragHandle} />
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
            <View style={styles.header}>
              <View style={styles.titleWrap}>
                <Text style={styles.title}>{zone.subtype || "흡연구역"}</Text>
                <View style={styles.chipRow}>
                  {zone.type ? (
                    <View style={styles.chip}>
                      <Text style={styles.chipText}>{zone.type}</Text>
                    </View>
                  ) : null}
                  {zone.region ? (
                    <View style={[styles.chip, styles.chipMuted]}>
                      <Text style={[styles.chipText, styles.chipTextMuted]}>{zone.region}</Text>
                    </View>
                  ) : null}
                </View>
              </View>

              <Pressable style={styles.closeIconButton} onPress={onClose}>
                <MaterialCommunityIcons name="close" size={20} color={colors.textMuted} />
              </Pressable>
            </View>

            <View style={styles.imageCard}>
              {imageUrl ? (
                <Image source={{ uri: imageUrl }} style={styles.image} />
              ) : (
                <View style={styles.imageEmpty}>
                  <MaterialCommunityIcons name="image-outline" size={20} color={colors.textMuted} />
                  <Text style={styles.imageEmptyText}>이미지 준비중</Text>
                </View>
              )}
            </View>

            <View style={styles.infoCard}>
              <View style={styles.infoRow}>
                <MaterialCommunityIcons name="map-marker-outline" size={18} color={colors.textMuted} />
                <Text style={styles.infoText}>{address || "주소 미제공"}</Text>
              </View>
              {shouldShowDescription ? (
                <View style={styles.infoRow}>
                  <MaterialCommunityIcons name="text-box-outline" size={18} color={colors.textMuted} />
                  <Text style={styles.infoText}>{description}</Text>
                </View>
              ) : null}
            </View>

            <Pressable style={styles.primaryButton} onPress={onOpenRoute}>
              <MaterialCommunityIcons name="navigation-variant-outline" size={18} color={colors.surface} />
              <Text style={styles.primaryButtonText}>길찾기</Text>
            </Pressable>

            <View style={styles.secondaryRow}>
              <Pressable style={styles.secondaryButton} onPress={onOpenShare}>
                <MaterialCommunityIcons name="share-variant-outline" size={17} color={colors.text} />
                <Text style={styles.secondaryButtonText}>공유</Text>
              </Pressable>
              <Pressable style={styles.secondaryButton} onPress={onOpenReport}>
                <MaterialCommunityIcons name="pencil-outline" size={17} color={colors.text} />
                <Text style={styles.secondaryButtonText}>제보</Text>
              </Pressable>
              <Pressable style={styles.secondaryButton} onPress={onOpenReview}>
                <MaterialCommunityIcons name="comment-edit-outline" size={17} color={colors.text} />
                <Text style={styles.secondaryButtonText}>리뷰</Text>
              </Pressable>
            </View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: "rgba(15,23,42,0.4)",
    justifyContent: "flex-end",
  },
  sheet: {
    maxHeight: "80%",
    backgroundColor: colors.surface,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    paddingHorizontal: 18,
    paddingTop: 10,
    paddingBottom: 24,
  },
  content: {
    gap: 12,
    paddingBottom: 12,
  },
  title: {
    fontSize: 21,
    fontWeight: "800",
    color: colors.text,
    marginBottom: 8,
  },
  header: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 10,
  },
  titleWrap: {
    flex: 1,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
  },
  chip: {
    backgroundColor: colors.primarySoft,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.full,
    paddingVertical: 4,
    paddingHorizontal: 10,
  },
  chipMuted: {
    backgroundColor: colors.surfaceMuted,
  },
  chipText: {
    color: colors.text,
    fontSize: 11,
    fontWeight: "700",
  },
  chipTextMuted: {
    color: colors.textMuted,
  },
  closeIconButton: {
    width: 32,
    height: 32,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primarySoft,
  },
  dragHandle: {
    alignSelf: "center",
    width: 48,
    height: 5,
    borderRadius: 99,
    marginBottom: 14,
    backgroundColor: colors.border,
  },
  imageCard: {
    borderRadius: radius.lg,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
  },
  image: {
    width: "100%",
    height: 180,
  },
  imageEmpty: {
    height: 130,
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
  imageEmptyText: {
    color: colors.textMuted,
    fontWeight: "600",
  },
  infoCard: {
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    padding: 12,
    gap: 10,
  },
  infoRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  infoText: {
    color: colors.text,
    lineHeight: 21,
    flex: 1,
  },
  primaryButton: {
    marginTop: 2,
    borderRadius: radius.lg,
    backgroundColor: colors.primary,
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 7,
  },
  primaryButtonText: {
    color: colors.surface,
    fontWeight: "700",
    fontSize: 15,
  },
  secondaryRow: {
    flexDirection: "row",
    gap: 8,
  },
  secondaryButton: {
    flex: 1,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    paddingVertical: 12,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 4,
  },
  secondaryButtonText: {
    color: colors.text,
    fontWeight: "700",
    fontSize: 12,
  },
})
