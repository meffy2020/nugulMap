import { Image, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { getImageUrl } from "../services/nugulApi"

interface ZoneDetailModalProps {
  zone: SmokingZone | null
  isFavorite: boolean
  onClose: () => void
  onToggleFavorite: () => void
  onOpenRoute: () => void
  onOpenShare: () => void
  onOpenReport: () => void
  onOpenReview: () => void
}

export function ZoneDetailModal({
  zone,
  isFavorite,
  onClose,
  onToggleFavorite,
  onOpenRoute,
  onOpenShare,
  onOpenReport,
  onOpenReview,
}: ZoneDetailModalProps) {
  if (!zone) {
    return null
  }

  const imageUrl = getImageUrl(zone.image)

  return (
    <Modal visible={Boolean(zone)} animationType="slide" transparent>
      <View style={styles.overlay}>
        <View style={styles.sheet}>
          <View style={styles.dragHandle} />
          <ScrollView>
            <View style={styles.header}>
              <Text style={styles.title}>{zone.subtype}</Text>
              <Pressable style={styles.favoriteButton} onPress={onToggleFavorite}>
                <MaterialCommunityIcons
                  name={isFavorite ? "heart" : "heart-outline"}
                  size={22}
                  color={isFavorite ? colors.destructive : colors.textMuted}
                />
              </Pressable>
            </View>
            <Text style={styles.meta}>{zone.type} · {zone.region}</Text>

            {imageUrl ? (
              <Image
                source={{ uri: imageUrl }}
                style={styles.image}
              />
            ) : null}

            <Text style={styles.label}>설명</Text>
            <Text style={styles.value}>{zone.description || "등록된 설명이 없습니다."}</Text>

            <Text style={styles.label}>주소</Text>
            <Text style={styles.value}>{zone.address || "주소 미제공"}</Text>

            <Text style={styles.label}>등록자</Text>
            <Text style={styles.value}>{zone.user || "익명"}</Text>

            <View style={styles.actionRow}>
              <Pressable style={styles.button} onPress={onOpenRoute}>
                <MaterialCommunityIcons name="map-marker-distance" size={18} color={colors.surface} />
                <Text style={styles.buttonText}>길찾기</Text>
              </Pressable>
              <Pressable style={styles.button} onPress={onOpenShare}>
                <MaterialCommunityIcons name="share-variant" size={18} color={colors.surface} />
                <Text style={styles.buttonText}>공유</Text>
              </Pressable>
            </View>
            <View style={styles.actionRow}>
              <Pressable style={styles.outline} onPress={onOpenReport}>
                <MaterialCommunityIcons name="pencil-outline" size={18} color={colors.text} />
                <Text style={[styles.buttonText, styles.outlineText]}>제보</Text>
              </Pressable>
              <Pressable style={styles.outline} onPress={onOpenReview}>
                <MaterialCommunityIcons name="comment-edit-outline" size={18} color={colors.text} />
                <Text style={[styles.buttonText, styles.outlineText]}>리뷰</Text>
              </Pressable>
            </View>

            <Pressable style={[styles.secondaryButton]} onPress={onToggleFavorite}>
              <Text style={styles.secondaryButtonText}>{isFavorite ? "북마크 해제" : "북마크 등록"}</Text>
            </Pressable>

            <Pressable style={styles.closeButton} onPress={onClose}>
              <Text style={styles.buttonText}>닫기</Text>
            </Pressable>
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
    padding: 22,
  },
  title: {
    fontSize: 24,
    fontWeight: "800",
    color: colors.text,
    marginBottom: 4,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  favoriteButton: {
    width: 34,
    height: 34,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surfaceMuted,
  },
  dragHandle: {
    alignSelf: "center",
    width: 48,
    height: 5,
    borderRadius: 99,
    marginBottom: 10,
    backgroundColor: colors.border,
  },
  meta: {
    color: colors.textMuted,
    marginBottom: 14,
    fontWeight: "700",
  },
  image: {
    width: "100%",
    height: 190,
    borderRadius: 10,
    marginBottom: 12,
    backgroundColor: colors.surfaceMuted,
  },
  label: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "700",
    marginTop: 10,
    marginBottom: 4,
    textTransform: "uppercase",
    letterSpacing: 0.6,
  },
  value: {
    color: colors.text,
    lineHeight: 20,
  },
  button: {
    marginTop: 10,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    paddingVertical: 13,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 6,
    flex: 1,
  },
  actionRow: {
    flexDirection: "row",
    gap: 8,
  },
  secondaryButton: {
    marginTop: 10,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    paddingVertical: 13,
    alignItems: "center",
  },
  secondaryButtonText: {
    color: colors.text,
    fontWeight: "700",
  },
  closeButton: {
    backgroundColor: colors.destructive,
    marginTop: 10,
    borderRadius: radius.md,
    paddingVertical: 13,
    alignItems: "center",
  },
  outline: {
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buttonText: {
    color: colors.surface,
    fontWeight: "700",
  },
  outlineText: {
    color: colors.text,
  },
})
