import { useEffect, useState } from "react"
import {
  Alert,
  FlatList,
  Image,
  Linking,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { deleteZone, fetchMyZones, getImageUrl } from "../services/nugulApi"
import type { SmokingZone, UserProfile } from "../types"
import { colors, radius } from "../theme/tokens"

const neutralAvatar = require("../../assets/images/neutral-user-avatar.png")
const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"

interface ProfileModalProps {
  visible: boolean
  user: UserProfile | null
  accessToken: string | null
  onClose: () => void
  onClearToken: () => Promise<void>
}

export function ProfileModal({
  visible,
  user,
  accessToken,
  onClose,
  onClearToken,
}: ProfileModalProps) {
  const [myZones, setMyZones] = useState<SmokingZone[]>([])
  const [loadingZones, setLoadingZones] = useState(false)

  const loadMyZones = async () => {
    if (!accessToken || !user) {
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
      void loadMyZones()
    }
  }, [visible, accessToken, user])

  const openLogin = async () => {
    const loginUrl = `${API_BASE_URL}/api/oauth2/authorization/kakao`
    try {
      await Linking.openURL(loginUrl)
    } catch {
      Alert.alert("오류", "로그인 페이지를 열 수 없습니다.")
    }
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
            <Text style={styles.title}>마이 프로필</Text>
          </View>
          <Pressable onPress={onClose}>
            <Text style={styles.close}>닫기</Text>
          </Pressable>
        </View>

        {!user ? (
          <View style={styles.section}>
            <Text style={styles.subTitle}>로그인이 필요합니다</Text>
            <Text style={styles.value}>웹 버전과 동일한 OAuth 로그인 페이지로 이동합니다.</Text>
            <Pressable style={styles.button} onPress={() => void openLogin()}>
              <Text style={styles.buttonText}>로그인하러 가기</Text>
            </Pressable>
          </View>
        ) : (
          <>
            <View style={styles.section}>
              <View style={styles.profileRow}>
                <Image source={user.profileImage ? { uri: getImageUrl(user.profileImage) || "" } : neutralAvatar} style={styles.avatar} />
                <View style={styles.profileMeta}>
                  <Text style={styles.nick}>{user.nickname}</Text>
                  <Text style={styles.email}>{user.email}</Text>
                </View>
              </View>
              <View style={styles.statRow}>
                <View style={styles.statBadge}>
                  <MaterialCommunityIcons name="map-marker-radius-outline" size={14} color={colors.textMuted} />
                  <Text style={styles.badgeText}>{myZones.length}개 장소</Text>
                </View>
                <View style={styles.statBadge}>
                  <MaterialCommunityIcons name="calendar-range-outline" size={14} color={colors.textMuted} />
                  <Text style={styles.badgeText}>{new Date(user.createdAt).toLocaleDateString()} 가입</Text>
                </View>
              </View>
              <Pressable style={styles.secondaryButton} onPress={() => void onClearToken()}>
                <Text style={styles.secondaryButtonText}>로그아웃</Text>
              </Pressable>
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
          </>
        )}
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  header: {
    paddingTop: 56,
    paddingHorizontal: 16,
    paddingBottom: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  kicker: {
    fontSize: 11,
    color: colors.textMuted,
    fontWeight: "800",
    letterSpacing: 1,
    textTransform: "uppercase",
  },
  title: { fontSize: 20, fontWeight: "800", color: colors.text },
  close: { color: colors.text, fontWeight: "800" },
  section: {
    padding: 16,
    marginHorizontal: 12,
    marginTop: 12,
    borderRadius: 16,
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 10,
  },
  listSection: { flex: 1 },
  subTitle: { color: colors.text, fontSize: 16, fontWeight: "800" },
  value: { color: colors.textMuted },
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  avatar: {
    width: 64,
    height: 64,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  profileMeta: { gap: 4 },
  nick: { color: colors.text, fontSize: 18, fontWeight: "800" },
  email: { color: colors.textMuted, fontSize: 13 },
  statRow: { flexDirection: "row", gap: 8 },
  statBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 7,
    backgroundColor: colors.surface,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
  },
  badgeText: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "700",
  },
  button: {
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: 12,
    alignItems: "center",
  },
  buttonText: { color: colors.surface, fontWeight: "700" },
  secondaryButton: {
    marginTop: 2,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingVertical: 11,
    alignItems: "center",
  },
  secondaryButtonText: { color: colors.text, fontWeight: "700" },
  zoneItem: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  zoneMeta: { flex: 1 },
  zoneTitle: { fontWeight: "700", color: colors.text },
  zoneAddress: { fontSize: 12, color: colors.textMuted },
  deleteButton: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
  },
  deleteText: { color: colors.destructive, fontWeight: "700", fontSize: 12 },
})
