import { Image, Linking, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native"
import type { SmokingZone } from "../types"

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

  const imageUrl = zone.image ? zone.image : null

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

            <Pressable style={[styles.button, styles.secondary]} onPress={onToggleFavorite}>
              <Text style={styles.buttonText}>{isFavorite ? "북마크 해제" : "북마크 등록"}</Text>
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
    backgroundColor: "#ffffff",
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: "800",
    color: "#0f172a",
    marginBottom: 6,
  },
  meta: {
    color: "#1d4ed8",
    marginBottom: 12,
  },
  image: {
    width: "100%",
    height: 190,
    borderRadius: 10,
    marginBottom: 12,
    backgroundColor: "#cbd5e1",
  },
  label: {
    color: "#475569",
    fontSize: 12,
    marginTop: 8,
    marginBottom: 4,
  },
  value: {
    color: "#0f172a",
  },
  button: {
    marginTop: 12,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
    backgroundColor: "#2563eb",
  },
  secondary: {
    backgroundColor: "#0f172a",
  },
  buttonText: {
    color: "#ffffff",
    fontWeight: "700",
  },
})
