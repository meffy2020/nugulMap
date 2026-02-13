import { useState } from "react"
import {
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import * as Location from "expo-location"
import { createZone, type CreateZonePayload } from "../services/nugulApi"
import type { SmokingZone } from "../types"

interface AddZoneModalProps {
  visible: boolean
  accessToken: string | null
  onClose: () => void
  onCreated: (zone: SmokingZone) => void
}

export function AddZoneModal({ visible, accessToken, onClose, onCreated }: AddZoneModalProps) {
  const [type, setType] = useState("BOOTH")
  const [subtype, setSubtype] = useState("부스")
  const [description, setDescription] = useState("")
  const [address, setAddress] = useState("")
  const [lat, setLat] = useState("")
  const [lng, setLng] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fillCurrentLocation = async () => {
    const permission = await Location.requestForegroundPermissionsAsync()
    if (permission.status !== "granted") {
      Alert.alert("권한 필요", "현재 위치를 사용하려면 위치 권한이 필요합니다.")
      return
    }

    const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
    setLat(String(current.coords.latitude))
    setLng(String(current.coords.longitude))
  }

  const submit = async () => {
    if (!accessToken) {
      Alert.alert("로그인 필요", "프로필 탭에서 토큰을 등록한 뒤 제보할 수 있습니다.")
      return
    }

    const latitude = Number(lat)
    const longitude = Number(lng)
    if (!address.trim() || Number.isNaN(latitude) || Number.isNaN(longitude)) {
      Alert.alert("입력 확인", "주소/위도/경도를 확인해주세요.")
      return
    }

    setIsSubmitting(true)
    try {
      const payload: CreateZonePayload = {
        region: "모바일",
        type,
        subtype,
        description: description || `${subtype} 흡연구역`,
        latitude,
        longitude,
        size: "M",
        address: address.trim(),
        user: "mobile-user",
      }

      const created = await createZone(payload, accessToken)
      onCreated(created)
      Alert.alert("등록 완료", "흡연구역이 등록되었습니다.")
      onClose()
    } catch (error) {
      Alert.alert("등록 실패", "등록 중 오류가 발생했습니다.")
      console.warn("create zone failed", error)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Modal visible={visible} animationType="slide" transparent={false}>
      <View style={styles.root}>
        <View style={styles.header}>
          <View>
            <Text style={styles.kicker}>Create</Text>
            <Text style={styles.title}>흡연구역 등록</Text>
          </View>
          <Pressable onPress={onClose}>
            <Text style={styles.close}>닫기</Text>
          </Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.label}>유형</Text>
          <View style={styles.row}>
            {[
              { value: "BOOTH", label: "부스" },
              { value: "OPEN", label: "개방" },
              { value: "INDOOR", label: "실내" },
            ].map((item) => (
              <Pressable
                key={item.value}
                style={[styles.chip, type === item.value && styles.chipActive]}
                onPress={() => {
                  setType(item.value)
                  setSubtype(item.label)
                }}
              >
                <Text style={[styles.chipText, type === item.value && styles.chipTextActive]}>{item.label}</Text>
              </Pressable>
            ))}
          </View>

          <Text style={styles.label}>주소</Text>
          <TextInput value={address} onChangeText={setAddress} style={styles.input} placeholder="예: 서울특별시 종로구 ..." />

          <Text style={styles.label}>설명</Text>
          <TextInput
            value={description}
            onChangeText={setDescription}
            style={[styles.input, styles.textArea]}
            multiline
            placeholder="현장 상태를 입력해 주세요."
          />

          <Text style={styles.label}>좌표</Text>
          <View style={styles.row}>
            <TextInput value={lat} onChangeText={setLat} style={[styles.input, styles.coord]} placeholder="위도" keyboardType="decimal-pad" />
            <TextInput value={lng} onChangeText={setLng} style={[styles.input, styles.coord]} placeholder="경도" keyboardType="decimal-pad" />
          </View>

          <Pressable style={[styles.button, styles.secondary]} onPress={() => void fillCurrentLocation()}>
            <Text style={styles.buttonText}>현재 위치로 채우기</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => void submit()} disabled={isSubmitting}>
            <Text style={styles.buttonText}>{isSubmitting ? "등록 중..." : "등록하기"}</Text>
          </Pressable>
        </ScrollView>
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#f8fafc" },
  header: {
    paddingTop: 56,
    paddingHorizontal: 16,
    paddingBottom: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderBottomLeftRadius: 22,
    borderBottomRightRadius: 22,
    shadowColor: "#0f172a",
    shadowOpacity: 0.08,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
  },
  kicker: {
    fontSize: 11,
    color: "#1d4ed8",
    fontWeight: "800",
    letterSpacing: 1,
    textTransform: "uppercase",
  },
  title: { fontSize: 20, fontWeight: "800", color: "#0f172a" },
  close: { color: "#2563eb", fontWeight: "800" },
  content: { padding: 16, gap: 10 },
  label: { color: "#334155", fontSize: 11, fontWeight: "800", marginTop: 10, textTransform: "uppercase", letterSpacing: 0.5 },
  input: {
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  textArea: { minHeight: 86, textAlignVertical: "top" },
  row: { flexDirection: "row", gap: 8 },
  coord: { flex: 1 },
  chip: {
    flex: 1,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingVertical: 12,
    alignItems: "center",
    backgroundColor: "#ffffff",
  },
  chipActive: {
    borderColor: "#1d4ed8",
    backgroundColor: "#dbeafe",
  },
  chipText: { color: "#334155", fontWeight: "700" },
  chipTextActive: { color: "#1d4ed8" },
  button: {
    marginTop: 8,
    backgroundColor: "#2563eb",
    borderRadius: 14,
    paddingVertical: 13,
    alignItems: "center",
  },
  secondary: { backgroundColor: "#0f172a" },
  buttonText: { color: "#ffffff", fontWeight: "700" },
})
