import { useEffect, useState } from "react"
import {
  Alert,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import * as ImagePicker from "expo-image-picker"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import {
  completeProfileSetup,
  deleteZone,
  fetchMyZones,
  getImageUrl,
  updateUserProfile,
  type UploadImageAsset,
} from "../services/nugulApi"
import type { SmokingZone, UserProfile } from "../types"
import { colors, radius } from "../theme/tokens"
import type { SocialLoginProvider } from "../hooks/useAuth"

const neutralAvatar = require("../../assets/images/neutral-user-avatar.png")

const SOCIAL_LOGIN_OPTIONS: Array<{
  provider: SocialLoginProvider
  label: string
  backgroundColor: string
  textColor: string
}> = [
  { provider: "kakao", label: "카카오로 시작하기", backgroundColor: "#FEE500", textColor: "#171717" },
  { provider: "naver", label: "네이버로 시작하기", backgroundColor: "#03C75A", textColor: "#ffffff" },
  { provider: "google", label: "구글로 시작하기", backgroundColor: "#171717", textColor: "#ffffff" },
]

function inferMimeType(fileName?: string | null): string {
  const normalized = String(fileName || "").toLowerCase()
  if (normalized.endsWith(".png")) return "image/png"
  return "image/jpeg"
}

function toUploadImageAsset(asset: ImagePicker.ImagePickerAsset): UploadImageAsset {
  const fallbackName = asset.uri.split("/").pop() || `profile-${Date.now()}.jpg`
  return {
    uri: asset.uri,
    name: asset.fileName || fallbackName,
    type: asset.mimeType || inferMimeType(asset.fileName || fallbackName),
  }
}

interface ProfileModalProps {
  visible: boolean
  user: UserProfile | null
  accessToken: string | null
  onClose: () => void
  onClearToken: () => Promise<void>
  onSocialLogin: (provider: SocialLoginProvider) => Promise<void>
  needsProfileSetup?: boolean
  onProfileUpdated?: () => Promise<void> | void
  onEditZone?: (zone: SmokingZone) => void
  authMessage: string | null
  onClearAuthMessage: () => void
  isAuthenticating: boolean
}

type ZoneEditorState = {
  id: number
  region: string
  type: string
  subtype: string
  description: string
  address: string
  latitude: string
  longitude: string
}

export function ProfileModal({
  visible,
  user,
  accessToken,
  onClose,
  onClearToken,
  onSocialLogin,
  needsProfileSetup = false,
  onProfileUpdated,
  onEditZone,
  authMessage,
  onClearAuthMessage,
  isAuthenticating,
}: ProfileModalProps) {
  const [myZones, setMyZones] = useState<SmokingZone[]>([])
  const [loadingZones, setLoadingZones] = useState(false)
  const [profileNickname, setProfileNickname] = useState("")
  const [selectedProfileImage, setSelectedProfileImage] = useState<UploadImageAsset | null>(null)
  const [savingProfile, setSavingProfile] = useState(false)
  const [isEditingProfile, setIsEditingProfile] = useState(false)
  const [editingZone, setEditingZone] = useState<ZoneEditorState | null>(null)
  const [savingZone, setSavingZone] = useState(false)
  const isSetupMode = Boolean(accessToken) && needsProfileSetup
  const currentProfileImage = user?.profileImage ? getImageUrl(user.profileImage) : null
  const profilePreviewUri = selectedProfileImage?.uri || currentProfileImage

  const loadMyZones = async () => {
    if (!accessToken || !user) {
      setMyZones([])
      return
    }
    setLoadingZones(true)
    try {
      const zones = await fetchMyZones(accessToken)
      setMyZones(zones)
    } finally {
      setLoadingZones(false)
    }
  }

  useEffect(() => {
    if (visible) {
      void loadMyZones()
    }
  }, [visible, accessToken, user])

  useEffect(() => {
    if (!visible) {
      setIsEditingProfile(false)
      setSelectedProfileImage(null)
      setProfileNickname(user?.nickname || "")
      return
    }

    setProfileNickname(user?.nickname || "")
    if (!isSetupMode) {
      setSelectedProfileImage(null)
    }
  }, [visible, user, isSetupMode])

  const openProfileImageLibrary = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!permission.granted) {
      Alert.alert("권한 필요", "사진을 선택하려면 사진 라이브러리 권한이 필요합니다.")
      return
    }

    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsEditing: true,
        quality: 0.8,
      })

      if (result.canceled || !result.assets?.length) return
      setSelectedProfileImage(toUploadImageAsset(result.assets[0]))
    } catch {
      Alert.alert("사진 선택 실패", "프로필 사진을 불러오지 못했습니다. 다시 시도해 주세요.")
    }
  }

  const openProfileCamera = async () => {
    const permission = await ImagePicker.requestCameraPermissionsAsync()
    if (!permission.granted) {
      Alert.alert("권한 필요", "촬영하려면 카메라 권한이 필요합니다.")
      return
    }

    try {
      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: ["images"],
        allowsEditing: true,
        quality: 0.8,
        cameraType: ImagePicker.CameraType.back,
      })

      if (result.canceled || !result.assets?.length) return
      setSelectedProfileImage(toUploadImageAsset(result.assets[0]))
    } catch {
      Alert.alert("촬영 실패", "카메라를 열지 못했습니다. 다시 시도해 주세요.")
    }
  }

  const saveProfile = async () => {
    if (!accessToken) {
      Alert.alert("로그인 필요", "로그인 후 프로필을 설정할 수 있습니다.")
      return
    }

    const nickname = profileNickname.trim()
    if (nickname.length < 2 || nickname.length > 20) {
      Alert.alert("닉네임 확인", "닉네임은 2자 이상 20자 이하여야 합니다.")
      return
    }

    setSavingProfile(true)
    try {
      if (isSetupMode) {
        await completeProfileSetup({ nickname }, accessToken, selectedProfileImage)
      } else if (user) {
        await updateUserProfile(user.id, { nickname }, accessToken, selectedProfileImage)
      } else {
        return
      }

      await onProfileUpdated?.()
      onClearAuthMessage()
      setSelectedProfileImage(null)
      setIsEditingProfile(false)
      Alert.alert(
        isSetupMode ? "프로필 설정 완료" : "프로필 저장 완료",
        isSetupMode ? "이제 모든 기능을 사용할 수 있습니다." : "프로필 정보를 업데이트했습니다.",
      )
    } catch (error) {
      Alert.alert(
        isSetupMode ? "프로필 설정 실패" : "프로필 저장 실패",
        isSetupMode ? "프로필 설정 중 오류가 발생했습니다." : "프로필 저장 중 오류가 발생했습니다.",
      )
      console.warn("profile save failed", error)
    } finally {
      setSavingProfile(false)
    }
  }

  const clearSelectedProfileImage = () => {
    setSelectedProfileImage(null)
  }

  const openEditZone = (zone: SmokingZone) => {
    if (onEditZone) {
      onEditZone(zone)
      return
    }

    setEditingZone({
      id: zone.id,
      region: zone.region || "",
      type: zone.type || "",
      subtype: zone.subtype || "",
      description: zone.description || "",
      address: zone.address || "",
      latitude: String(zone.latitude ?? ""),
      longitude: String(zone.longitude ?? ""),
    })
  }

  const closeEditZone = () => {
    setEditingZone(null)
  }

  const saveEditedZone = async () => {
    if (!accessToken) return
    if (!editingZone) return

    const latitude = Number(editingZone.latitude)
    const longitude = Number(editingZone.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      Alert.alert("위치 확인", "위도와 경도 값이 올바르지 않습니다.")
      return
    }

    setSavingZone(true)
    try {
      const response = await fetch(`https://api.nugulmap.com/api/zones/${editingZone.id}`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: (() => {
          const formData = new FormData()
          formData.append(
            "data",
            JSON.stringify({
              region: editingZone.region.trim(),
              type: editingZone.type.trim(),
              subtype: editingZone.subtype.trim(),
              description: editingZone.description.trim(),
              latitude,
              longitude,
              size: "M",
              address: editingZone.address.trim(),
              user: user?.nickname || "mobile-user",
            }),
          )
          return formData
        })(),
      })

      if (!response.ok) {
        throw new Error(`zone update failed: ${response.status}`)
      }

      Alert.alert("수정 완료", "제보 장소를 수정했습니다.")
      setEditingZone(null)
      await loadMyZones()
    } catch (error) {
      Alert.alert("수정 실패", "수정 중 오류가 발생했습니다.")
      console.warn("update zone failed", error)
    } finally {
      setSavingZone(false)
    }
  }

  const confirmRemoveZone = (zone: SmokingZone) => {
    Alert.alert("삭제 확인", `\"${zone.subtype || zone.type || "제보 장소"}\"를 삭제할까요?`, [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: () => {
          void removeZone(zone.id)
        },
      },
    ])
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

        {isSetupMode ? (
          <View style={styles.section}>
            <Text style={styles.subTitle}>추가 프로필 설정</Text>
            <Text style={styles.value}>닉네임과 프로필 사진을 설정하면 로그인 절차가 마무리됩니다.</Text>

            {authMessage ? (
              <View style={styles.messageBox}>
                <Text style={styles.messageText}>{authMessage}</Text>
                <Pressable onPress={onClearAuthMessage}>
                  <MaterialCommunityIcons name="close" size={16} color={colors.textMuted} />
                </Pressable>
              </View>
            ) : null}

            <View style={styles.profileEditorCard}>
              <View style={styles.profileEditorHeader}>
                <Image source={profilePreviewUri ? { uri: profilePreviewUri } : neutralAvatar} style={styles.avatar} />
                <View style={styles.profileMeta}>
                  <Text style={styles.nick}>{profileNickname.trim() || "닉네임을 입력해 주세요"}</Text>
                  <Text style={styles.email}>{user?.email || "OAuth 로그인 계정으로 가입을 마무리합니다."}</Text>
                </View>
              </View>

              <Text style={styles.fieldLabel}>닉네임</Text>
              <TextInput
                value={profileNickname}
                onChangeText={setProfileNickname}
                style={styles.fieldInput}
                placeholder="2~20자 닉네임"
                autoCapitalize="none"
              />

              <View style={styles.profileActionRow}>
                <Pressable style={styles.inlineActionButton} onPress={() => void openProfileImageLibrary()}>
                  <MaterialCommunityIcons name="image-outline" size={16} color={colors.text} />
                  <Text style={styles.inlineActionText}>사진 선택</Text>
                </Pressable>
                <Pressable style={styles.inlineActionButton} onPress={() => void openProfileCamera()}>
                  <MaterialCommunityIcons name="camera-outline" size={16} color={colors.text} />
                  <Text style={styles.inlineActionText}>카메라</Text>
                </Pressable>
                {selectedProfileImage ? (
                  <Pressable style={styles.inlineActionButton} onPress={clearSelectedProfileImage}>
                    <MaterialCommunityIcons name="close-circle-outline" size={16} color={colors.textMuted} />
                    <Text style={styles.inlineActionText}>취소</Text>
                  </Pressable>
                ) : null}
              </View>

              <Pressable style={styles.saveButton} onPress={() => void saveProfile()} disabled={savingProfile}>
                <Text style={styles.saveButtonText}>{savingProfile ? "설정 중..." : "프로필 설정 완료"}</Text>
              </Pressable>
              <Pressable style={styles.secondaryButton} onPress={() => void onClearToken()}>
                <Text style={styles.secondaryButtonText}>로그아웃</Text>
              </Pressable>
            </View>
          </View>
        ) : !user ? (
          <View style={styles.section}>
            <Text style={styles.subTitle}>간편 로그인</Text>
            <Text style={styles.value}>웹 버전과 동일한 OAuth 계정으로 바로 로그인할 수 있어요.</Text>

            {authMessage ? (
              <View style={styles.messageBox}>
                <Text style={styles.messageText}>{authMessage}</Text>
                <Pressable onPress={onClearAuthMessage}>
                  <MaterialCommunityIcons name="close" size={16} color={colors.textMuted} />
                </Pressable>
              </View>
            ) : null}

            {SOCIAL_LOGIN_OPTIONS.map((item) => (
              <Pressable
                key={item.provider}
                style={[styles.socialButton, { backgroundColor: item.backgroundColor }]}
                onPress={() => void onSocialLogin(item.provider)}
                disabled={isAuthenticating}
              >
                <Text style={[styles.socialButtonText, { color: item.textColor }]}>
                  {isAuthenticating ? "로그인 진행 중..." : item.label}
                </Text>
              </Pressable>
            ))}
          </View>
        ) : (
          <>
            <View style={styles.section}>
              <View style={styles.profileRow}>
                <Image source={user.profileImage ? { uri: getImageUrl(user.profileImage) || "" } : neutralAvatar} style={styles.avatar} />
                <View style={styles.profileMeta}>
                  <Text style={styles.nick}>{user.nickname || "닉네임 미설정"}</Text>
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
              {authMessage ? (
                <View style={styles.messageBox}>
                  <Text style={styles.messageText}>{authMessage}</Text>
                  <Pressable onPress={onClearAuthMessage}>
                    <MaterialCommunityIcons name="close" size={16} color={colors.textMuted} />
                  </Pressable>
                </View>
              ) : null}
              <View style={styles.profileButtonRow}>
                <Pressable
                  style={styles.secondaryButtonHalf}
                  onPress={() => {
                    setIsEditingProfile((prev) => !prev)
                    setProfileNickname(user.nickname || "")
                    setSelectedProfileImage(null)
                  }}
                >
                  <Text style={styles.secondaryButtonText}>{isEditingProfile ? "편집 닫기" : "프로필 편집"}</Text>
                </Pressable>
                <Pressable style={styles.secondaryButtonHalf} onPress={() => void onClearToken()}>
                  <Text style={styles.secondaryButtonText}>로그아웃</Text>
                </Pressable>
              </View>

              {isEditingProfile ? (
                <View style={styles.profileEditorCard}>
                  <View style={styles.profileEditorHeader}>
                    <Image source={profilePreviewUri ? { uri: profilePreviewUri } : neutralAvatar} style={styles.avatar} />
                    <View style={styles.profileMeta}>
                      <Text style={styles.nick}>{profileNickname.trim() || user.nickname || "닉네임을 입력해 주세요"}</Text>
                      <Text style={styles.email}>{user.email}</Text>
                    </View>
                  </View>

                  <Text style={styles.fieldLabel}>닉네임</Text>
                  <TextInput
                    value={profileNickname}
                    onChangeText={setProfileNickname}
                    style={styles.fieldInput}
                    placeholder="2~20자 닉네임"
                    autoCapitalize="none"
                  />

                  <View style={styles.profileActionRow}>
                    <Pressable style={styles.inlineActionButton} onPress={() => void openProfileImageLibrary()}>
                      <MaterialCommunityIcons name="image-outline" size={16} color={colors.text} />
                      <Text style={styles.inlineActionText}>사진 선택</Text>
                    </Pressable>
                    <Pressable style={styles.inlineActionButton} onPress={() => void openProfileCamera()}>
                      <MaterialCommunityIcons name="camera-outline" size={16} color={colors.text} />
                      <Text style={styles.inlineActionText}>카메라</Text>
                    </Pressable>
                    {selectedProfileImage ? (
                      <Pressable style={styles.inlineActionButton} onPress={clearSelectedProfileImage}>
                        <MaterialCommunityIcons name="close-circle-outline" size={16} color={colors.textMuted} />
                        <Text style={styles.inlineActionText}>취소</Text>
                      </Pressable>
                    ) : null}
                  </View>

                  <Pressable style={styles.saveButton} onPress={() => void saveProfile()} disabled={savingProfile}>
                    <Text style={styles.saveButtonText}>{savingProfile ? "저장 중..." : "프로필 저장"}</Text>
                  </Pressable>
                </View>
              ) : null}
            </View>

            <View style={[styles.section, styles.listSection]}>
              <Text style={styles.subTitle}>내가 등록한 장소</Text>
              {loadingZones ? <Text style={styles.value}>불러오는 중...</Text> : null}
              <FlatList
                data={myZones}
                keyExtractor={(item) => String(item.id)}
                renderItem={({ item }) => (
                  <View style={styles.zoneItem}>
                    <View style={styles.zoneThumbWrap}>
                      {item.image ? (
                        <Image
                          source={{ uri: getImageUrl(item.image) || "" }}
                          style={styles.zoneThumb}
                        />
                      ) : (
                        <View style={styles.zoneThumbPlaceholder}>
                          <MaterialCommunityIcons name="image-outline" size={14} color={colors.textMuted} />
                        </View>
                      )}
                    </View>
                    <View style={styles.zoneMeta}>
                      <Text style={styles.zoneTitle}>{item.subtype || item.type}</Text>
                      <Text style={styles.zoneAddress}>{item.address}</Text>
                    </View>
                    <View style={styles.zoneActions}>
                      <Pressable style={styles.editButton} onPress={() => openEditZone(item)}>
                        <MaterialCommunityIcons name="pencil-outline" size={14} color={colors.text} />
                        <Text style={styles.editText}>수정</Text>
                      </Pressable>
                      <Pressable style={styles.deleteButton} onPress={() => confirmRemoveZone(item)}>
                        <MaterialCommunityIcons name="trash-can-outline" size={14} color={colors.destructive} />
                        <Text style={styles.deleteText}>삭제</Text>
                      </Pressable>
                    </View>
                  </View>
                )}
                ListEmptyComponent={<Text style={styles.value}>등록한 장소가 없습니다.</Text>}
              />
            </View>
          </>
        )}
      </View>

      <Modal visible={Boolean(editingZone)} animationType="slide" transparent={false}>
        <View style={styles.editorRoot}>
          <View style={styles.editorHeader}>
            <View>
              <Text style={styles.kicker}>Edit Zone</Text>
              <Text style={styles.editorTitle}>제보 장소 수정</Text>
            </View>
            <Pressable onPress={closeEditZone}>
              <Text style={styles.close}>닫기</Text>
            </Pressable>
          </View>

          <ScrollView contentContainerStyle={styles.editorBody} keyboardShouldPersistTaps="handled">
            <Text style={styles.fieldLabel}>지역</Text>
            <TextInput
              value={editingZone?.region || ""}
              onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, region: text } : prev))}
              style={styles.fieldInput}
              placeholder="서울특별시"
            />

            <Text style={styles.fieldLabel}>유형</Text>
            <TextInput
              value={editingZone?.type || ""}
              onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, type: text } : prev))}
              style={styles.fieldInput}
              placeholder="실외"
            />

            <Text style={styles.fieldLabel}>세부 분류</Text>
            <TextInput
              value={editingZone?.subtype || ""}
              onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, subtype: text } : prev))}
              style={styles.fieldInput}
              placeholder="부스 / 거리 / 공원"
            />

            <Text style={styles.fieldLabel}>주소</Text>
            <TextInput
              value={editingZone?.address || ""}
              onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, address: text } : prev))}
              style={styles.fieldInput}
              placeholder="서울특별시 ..."
            />

            <Text style={styles.fieldLabel}>설명</Text>
            <TextInput
              value={editingZone?.description || ""}
              onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, description: text } : prev))}
              style={[styles.fieldInput, styles.multilineInput]}
              placeholder="현장 설명"
              multiline
              textAlignVertical="top"
            />

            <View style={styles.coordRow}>
              <View style={styles.coordColumn}>
                <Text style={styles.fieldLabel}>위도</Text>
                <TextInput
                  value={editingZone?.latitude || ""}
                  onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, latitude: text } : prev))}
                  style={styles.fieldInput}
                  keyboardType="decimal-pad"
                />
              </View>
              <View style={styles.coordColumn}>
                <Text style={styles.fieldLabel}>경도</Text>
                <TextInput
                  value={editingZone?.longitude || ""}
                  onChangeText={(text) => setEditingZone((prev) => (prev ? { ...prev, longitude: text } : prev))}
                  style={styles.fieldInput}
                  keyboardType="decimal-pad"
                />
              </View>
            </View>

            <Pressable style={styles.saveButton} onPress={() => void saveEditedZone()} disabled={savingZone}>
              <Text style={styles.saveButtonText}>{savingZone ? "저장 중..." : "수정 저장"}</Text>
            </Pressable>
          </ScrollView>
        </View>
      </Modal>
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
  socialButton: {
    borderRadius: radius.md,
    paddingVertical: 12,
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  socialButtonText: { fontWeight: "800" },
  messageBox: {
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    paddingVertical: 8,
    paddingHorizontal: 10,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  messageText: {
    flex: 1,
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "600",
  },
  secondaryButton: {
    marginTop: 2,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingVertical: 11,
    alignItems: "center",
  },
  profileButtonRow: {
    flexDirection: "row",
    gap: 8,
  },
  secondaryButtonHalf: {
    flex: 1,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingVertical: 11,
    alignItems: "center",
  },
  secondaryButtonText: { color: colors.text, fontWeight: "700" },
  profileEditorCard: {
    marginTop: 2,
    padding: 12,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    gap: 10,
  },
  profileEditorHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  profileActionRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  inlineActionButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: radius.full,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
  },
  inlineActionText: {
    color: colors.text,
    fontSize: 12,
    fontWeight: "700",
  },
  zoneItem: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  zoneThumbWrap: {
    width: 44,
    height: 44,
    borderRadius: radius.md,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  zoneThumb: {
    width: "100%",
    height: "100%",
  },
  zoneThumbPlaceholder: {
    width: "100%",
    height: "100%",
    alignItems: "center",
    justifyContent: "center",
  },
  zoneMeta: { flex: 1 },
  zoneTitle: { fontWeight: "700", color: colors.text },
  zoneAddress: { fontSize: 12, color: colors.textMuted },
  zoneActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  editButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
  },
  editText: { color: colors.text, fontWeight: "700", fontSize: 12 },
  deleteButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
  },
  deleteText: { color: colors.destructive, fontWeight: "700", fontSize: 12 },
  editorRoot: { flex: 1, backgroundColor: colors.bg },
  editorHeader: {
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
  editorTitle: { fontSize: 20, fontWeight: "800", color: colors.text },
  editorBody: {
    padding: 16,
    gap: 10,
  },
  fieldLabel: {
    color: colors.text,
    fontWeight: "700",
    fontSize: 13,
  },
  fieldInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: colors.text,
  },
  multilineInput: {
    minHeight: 96,
  },
  coordRow: {
    flexDirection: "row",
    gap: 10,
  },
  coordColumn: {
    flex: 1,
    gap: 6,
  },
  saveButton: {
    marginTop: 8,
    paddingVertical: 12,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: "center",
  },
  saveButtonText: {
    color: colors.surface,
    fontWeight: "800",
  },
})
