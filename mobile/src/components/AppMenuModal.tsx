import { useEffect, useState } from "react"
import { Image, Modal, Pressable, StyleSheet, Text, View } from "react-native"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import type { UserProfile } from "../types"
import { colors, radius } from "../theme/tokens"
import { getImageUrl } from "../services/nugulApi"

const neutralAvatar = require("../../assets/images/neutral-user-avatar.png")

type MenuPage = "main" | "notice" | "settings"

interface AppMenuModalProps {
  visible: boolean
  user: UserProfile | null
  onClose: () => void
  onOpenProfile: () => void
  onLogout: () => Promise<void>
}

export function AppMenuModal({ visible, user, onClose, onOpenProfile, onLogout }: AppMenuModalProps) {
  const [page, setPage] = useState<MenuPage>("main")

  useEffect(() => {
    if (!visible) {
      setPage("main")
    }
  }, [visible])

  const renderMain = () => (
    <View style={styles.body}>
      <View style={styles.profileBlock}>
        <Image
          source={user?.profileImage ? { uri: getImageUrl(user.profileImage) || "" } : neutralAvatar}
          style={styles.avatar}
        />
        <View style={styles.profileText}>
          <Text style={styles.nick}>{user?.nickname || "게스트 사용자"}</Text>
          <Text style={styles.email}>{user?.email || "로그인 후 더 많은 기능을 사용할 수 있어요."}</Text>
        </View>
      </View>

      <Pressable
        style={styles.menuItem}
        onPress={() => {
          onClose()
          onOpenProfile()
        }}
      >
        <MaterialCommunityIcons name="account-circle-outline" size={20} color={colors.text} />
        <Text style={styles.menuLabel}>마이페이지</Text>
      </Pressable>

      <Pressable style={styles.menuItem} onPress={() => setPage("notice")}>
        <MaterialCommunityIcons name="bullhorn-outline" size={20} color={colors.text} />
        <Text style={styles.menuLabel}>공지사항</Text>
      </Pressable>

      <Pressable style={styles.menuItem} onPress={() => setPage("settings")}>
        <MaterialCommunityIcons name="cog-outline" size={20} color={colors.text} />
        <Text style={styles.menuLabel}>설정</Text>
      </Pressable>

      <Pressable
        style={[styles.menuItem, styles.logoutItem]}
        onPress={() => {
          void onLogout()
          onClose()
        }}
      >
        <MaterialCommunityIcons name="logout" size={20} color={colors.destructive} />
        <Text style={styles.logoutLabel}>로그아웃</Text>
      </Pressable>
    </View>
  )

  const renderNotice = () => (
    <View style={styles.body}>
      <Text style={styles.sectionTitle}>공지사항</Text>
      <View style={styles.noticeItem}>
        <Text style={styles.noticeTitle}>앱 업데이트 안내</Text>
        <Text style={styles.noticeText}>카카오 지도 전환 및 제보 UX 개선이 적용되었습니다.</Text>
      </View>
      <View style={styles.noticeItem}>
        <Text style={styles.noticeTitle}>데이터 품질 개선</Text>
        <Text style={styles.noticeText}>마커 노출 안정화를 위해 좌표 정규화가 적용되고 있습니다.</Text>
      </View>
      <Pressable style={styles.backButton} onPress={() => setPage("main")}>
        <Text style={styles.backButtonText}>메뉴로 돌아가기</Text>
      </Pressable>
    </View>
  )

  const renderSettings = () => (
    <View style={styles.body}>
      <Text style={styles.sectionTitle}>설정</Text>
      <View style={styles.noticeItem}>
        <Text style={styles.noticeTitle}>지도 설정</Text>
        <Text style={styles.noticeText}>현재 위치 기준 자동 이동은 꺼져 있습니다.</Text>
      </View>
      <View style={styles.noticeItem}>
        <Text style={styles.noticeTitle}>알림 설정</Text>
        <Text style={styles.noticeText}>알림 상세 옵션은 다음 버전에서 제공 예정입니다.</Text>
      </View>
      <Pressable style={styles.backButton} onPress={() => setPage("main")}>
        <Text style={styles.backButtonText}>메뉴로 돌아가기</Text>
      </Pressable>
    </View>
  )

  return (
    <Modal visible={visible} animationType="slide" transparent={false}>
      <View style={styles.root}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>{page === "main" ? "메뉴" : page === "notice" ? "공지사항" : "설정"}</Text>
          <Pressable onPress={onClose}>
            <Text style={styles.close}>닫기</Text>
          </Pressable>
        </View>
        {page === "main" ? renderMain() : null}
        {page === "notice" ? renderNotice() : null}
        {page === "settings" ? renderSettings() : null}
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  header: {
    paddingTop: 56,
    paddingHorizontal: 16,
    paddingBottom: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.surface,
  },
  headerTitle: {
    color: colors.text,
    fontSize: 20,
    fontWeight: "800",
  },
  close: {
    color: colors.text,
    fontWeight: "700",
  },
  body: {
    padding: 16,
    gap: 10,
  },
  profileBlock: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    marginBottom: 6,
  },
  avatar: {
    width: 54,
    height: 54,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
  },
  profileText: {
    flex: 1,
  },
  nick: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "800",
  },
  email: {
    color: colors.textMuted,
    fontSize: 12,
    marginTop: 2,
  },
  menuItem: {
    height: 52,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    paddingHorizontal: 14,
    alignItems: "center",
    flexDirection: "row",
    gap: 10,
  },
  menuLabel: {
    color: colors.text,
    fontWeight: "700",
    fontSize: 14,
  },
  logoutItem: {
    marginTop: 6,
  },
  logoutLabel: {
    color: colors.destructive,
    fontWeight: "700",
    fontSize: 14,
  },
  sectionTitle: {
    color: colors.text,
    fontWeight: "800",
    fontSize: 18,
    marginBottom: 6,
  },
  noticeItem: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    padding: 12,
    gap: 4,
  },
  noticeTitle: {
    color: colors.text,
    fontWeight: "700",
  },
  noticeText: {
    color: colors.textMuted,
    fontSize: 12,
  },
  backButton: {
    marginTop: 8,
    height: 44,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surfaceMuted,
  },
  backButtonText: {
    color: colors.text,
    fontWeight: "700",
  },
})
