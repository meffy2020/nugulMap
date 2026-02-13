import { useEffect, useState } from "react"
import {
  Alert,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import { deleteZone, fetchMyZones } from "../services/nugulApi"
import type { SmokingZone, UserProfile } from "../types"

interface ProfileModalProps {
  visible: boolean
  user: UserProfile | null
  accessToken: string | null
  onClose: () => void
  onSaveToken: (token: string) => Promise<boolean>
  onClearToken: () => Promise<void>
}

export function ProfileModal({
  visible,
  user,
  accessToken,
  onClose,
  onSaveToken,
  onClearToken,
}: ProfileModalProps) {
  const [tokenInput, setTokenInput] = useState("")
  const [myZones, setMyZones] = useState<SmokingZone[]>([])
  const [loadingZones, setLoadingZones] = useState(false)

  const loadMyZones = async () => {
    if (!accessToken) {
      setMyZones([])
      return
    }
    setLoadingZones(true)
    const zones = await fetchMyZones(accessToken)
    setMyZones(zones)
    setLoadingZones(false)
  }

  useEffect(() => {
    if (visible) {
      setTokenInput(accessToken ?? "")
      void loadMyZones()
    }
  }, [visible, accessToken])

  const submitToken = async () => {
    const ok = await onSaveToken(tokenInput)
    if (!ok) {
      Alert.alert("토큰 오류", "유효한 액세스 토큰이 아닙니다.")
      return
    }
    Alert.alert("저장 완료", "토큰을 저장했고 사용자 정보를 새로고침했습니다.")
    await loadMyZones()
  }

  const removeZone = async (id: number) => {
    if (!accessToken) return

    try {
      await deleteZone(id, accessToken)
      Alert.alert("삭제 완료", "제보 장소를 삭제했습니다.")
      await loadMyZones()
    } catch (error) {
      Alert.alert("삭제 실패", "삭제 중 오류가 발생했습니다.")
      console.warn("delete zone failed", error)
    }
  }

  return (
    <Modal visible={visible} animationType="slide" transparent={false}>
      <View style={styles.root}>
        <View style={styles.header}>
          <View>
            <Text style={styles.kicker}>Account</Text>
            <Text style={styles.title}>내 정보</Text>
          </View>
          <Pressable onPress={onClose}>
            <Text style={styles.close}>닫기</Text>
          </Pressable>
        </View>

        <View style={styles.section}>
          <Text style={styles.label}>액세스 토큰</Text>
          <TextInput
            value={tokenInput}
            onChangeText={setTokenInput}
            style={styles.input}
            multiline
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="Bearer 토큰 문자열을 입력"
          />
          <View style={styles.row}>
            <Pressable style={styles.button} onPress={() => void submitToken()}>
              <Text style={styles.buttonText}>토큰 저장</Text>
            </Pressable>
            <Pressable
              style={[styles.button, styles.secondary]}
              onPress={() => {
                void onClearToken()
                setTokenInput("")
                setMyZones([])
              }}
            >
              <Text style={styles.buttonText}>로그아웃</Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.subTitle}>계정</Text>
          <Text style={styles.value}>닉네임: {user?.nickname || "-"}</Text>
          <Text style={styles.value}>이메일: {user?.email || "-"}</Text>
        </View>

        <View style={[styles.section, styles.listSection]}>
          <Text style={styles.subTitle}>내가 등록한 장소</Text>
          {loadingZones ? <Text style={styles.value}>불러오는 중...</Text> : null}
          <FlatList
            data={myZones}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => (
              <View style={styles.zoneItem}>
                <View style={styles.zoneMeta}>
                  <Text style={styles.zoneTitle}>{item.subtype || item.type}</Text>
                  <Text style={styles.zoneAddress}>{item.address}</Text>
                </View>
                <Pressable style={styles.deleteButton} onPress={() => void removeZone(item.id)}>
                  <Text style={styles.deleteText}>삭제</Text>
                </Pressable>
              </View>
            )}
            ListEmptyComponent={<Text style={styles.value}>등록한 장소가 없습니다.</Text>}
          />
        </View>
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
  section: {
    padding: 16,
    marginHorizontal: 12,
    marginTop: 12,
    borderRadius: 16,
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#e2e8f0",
    gap: 8,
  },
  listSection: { flex: 1 },
  label: { color: "#334155", fontSize: 11, fontWeight: "800", textTransform: "uppercase", letterSpacing: 0.5 },
  subTitle: { color: "#0f172a", fontSize: 16, fontWeight: "800" },
  value: { color: "#334155" },
  input: {
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 10,
    minHeight: 76,
    textAlignVertical: "top",
    fontSize: 12,
  },
  row: { flexDirection: "row", gap: 8 },
  button: {
    flex: 1,
    backgroundColor: "#2563eb",
    borderRadius: 14,
    paddingVertical: 12,
    alignItems: "center",
  },
  secondary: { backgroundColor: "#0f172a" },
  buttonText: { color: "#ffffff", fontWeight: "700" },
  zoneItem: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#e2e8f0",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  zoneMeta: { flex: 1 },
  zoneTitle: { fontWeight: "700", color: "#0f172a" },
  zoneAddress: { fontSize: 12, color: "#475569" },
  deleteButton: { paddingHorizontal: 10, paddingVertical: 6, backgroundColor: "#fee2e2", borderRadius: 8 },
  deleteText: { color: "#b91c1c", fontWeight: "700", fontSize: 12 },
})
