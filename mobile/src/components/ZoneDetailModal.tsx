import { Image, Linking, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"
import { getImageUrl } from "../services/nugulApi"

interface ZoneDetailModalProps {
  zone: SmokingZone | null
  isFavorite: boolean
  onClose: () => void
  onToggleFavorite: () => void
}

export function ZoneDetailModal({
  zone,
  isFavorite,
  onClose,
  onToggleFavorite,
}: ZoneDetailModalProps) {
  if (!zone) {
    return null
  }

  const openMap = () => {
    const url = `https://www.google.com/maps/search/?api=1&query=${zone.latitude},${zone.longitude}`
    void Linking.openURL(url)
  }

  const imageUrl = getImageUrl(zone.image)

  return (
    <Modal visible={Boolean(zone)} animationType="slide" transparent>
      <View style={styles.overlay}>
        <View style={styles.sheet}>
          <ScrollView>
            <Text style={styles.title}>{zone.subtype}</Text>
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

            <Pressable style={styles.button} onPress={openMap}>
              <Text style={styles.buttonText}>길찾기</Text>
            </Pressable>

            <Pressable style={[styles.button, styles.outline]} onPress={onToggleFavorite}>
              <Text style={[styles.buttonText, styles.outlineText]}>{isFavorite ? "북마크 해제" : "북마크 등록"}</Text>
            </Pressable>

            <Pressable style={[styles.button, styles.secondary]} onPress={onClose}>
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
    paddingVertical: 13,
    alignItems: "center",
    backgroundColor: colors.primary,
  },
  secondary: {
    backgroundColor: colors.destructive,
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
