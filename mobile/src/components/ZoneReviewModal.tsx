import { useEffect, useMemo, useState } from "react"
import {
  ActivityIndicator,
  Alert,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { createZoneReview, fetchZoneReviews, getImageUrl } from "../services/nugulApi"
import type { SmokingZone, UserProfile, ZoneReview } from "../types"
import { colors, radius } from "../theme/tokens"

interface ZoneReviewModalProps {
  visible: boolean
  zone: SmokingZone | null
  accessToken: string | null
  user: UserProfile | null
  onClose: () => void
}

function formatReviewDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return "방금 전"
  }

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`
}

function getInitial(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed.slice(0, 1).toUpperCase() : "?"
}

export function ZoneReviewModal({ visible, zone, accessToken, user, onClose }: ZoneReviewModalProps) {
  const [reviews, setReviews] = useState<ZoneReview[]>([])
  const [draft, setDraft] = useState("")
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const isLoggedIn = Boolean(accessToken && user)

  const title = useMemo(() => zone?.subtype || zone?.address || "흡연구역 리뷰", [zone])

  const loadReviews = async () => {
    if (!zone) return

    setLoading(true)
    setErrorMessage(null)
    try {
      const nextReviews = await fetchZoneReviews(zone.id)
      setReviews(nextReviews)
    } catch {
      setErrorMessage("리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!visible || !zone) return
    void loadReviews()
  }, [visible, zone?.id])

  useEffect(() => {
    if (!visible) {
      setDraft("")
      setErrorMessage(null)
    }
  }, [visible])

  const handleSubmit = async () => {
    if (!zone) return
    if (!accessToken) {
      Alert.alert("로그인 필요", "로그인 후 리뷰를 작성할 수 있습니다.")
      return
    }

    const normalizedDraft = draft.replace(/\s+/g, " ").trim()
    if (!normalizedDraft) {
      Alert.alert("리뷰 확인", "리뷰 내용을 입력해 주세요.")
      return
    }

    setSubmitting(true)
    try {
      const createdReview = await createZoneReview(zone.id, { content: normalizedDraft }, accessToken)
      setReviews((current) => [createdReview, ...current])
      setDraft("")
    } catch (error) {
      const message = error instanceof Error ? error.message : "리뷰 등록에 실패했습니다."
      Alert.alert("리뷰 등록 실패", message)
    } finally {
      setSubmitting(false)
    }
  }

  if (!zone) {
    return null
  }

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <View style={styles.overlay}>
        <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
          <View style={styles.sheet}>
            <View style={styles.dragHandle} />
            <View style={styles.header}>
              <View style={styles.headerTextWrap}>
                <Text style={styles.kicker}>Reviews</Text>
                <Text style={styles.title}>{title}</Text>
                <Text style={styles.subtitle}>현장 이용 경험을 남겨주세요.</Text>
              </View>
              <Pressable style={styles.closeButton} onPress={onClose}>
                <MaterialCommunityIcons name="close" size={20} color={colors.textMuted} />
              </Pressable>
            </View>

            <View style={styles.listWrap}>
              {loading ? (
                <View style={styles.centerState}>
                  <ActivityIndicator size="small" color={colors.primary} />
                  <Text style={styles.centerStateText}>리뷰를 불러오는 중...</Text>
                </View>
              ) : errorMessage ? (
                <View style={styles.centerState}>
                  <Text style={styles.centerStateText}>{errorMessage}</Text>
                  <Pressable style={styles.retryButton} onPress={() => void loadReviews()}>
                    <Text style={styles.retryButtonText}>다시 시도</Text>
                  </Pressable>
                </View>
              ) : reviews.length ? (
                <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.reviewListContent}>
                  {reviews.map((review) => {
                    const avatarUri = getImageUrl(review.authorProfileImage)
                    return (
                      <View key={review.id} style={styles.reviewCard}>
                        <View style={styles.reviewHeader}>
                          <View style={styles.authorRow}>
                            {avatarUri ? (
                              <Image source={{ uri: avatarUri }} style={styles.avatarImage} />
                            ) : (
                              <View style={styles.avatarFallback}>
                                <Text style={styles.avatarFallbackText}>{getInitial(review.authorNickname)}</Text>
                              </View>
                            )}
                            <View style={styles.authorMeta}>
                              <Text style={styles.authorName}>{review.authorNickname}</Text>
                              <Text style={styles.authorDate}>{formatReviewDate(review.createdAt)}</Text>
                            </View>
                          </View>
                        </View>
                        <Text style={styles.reviewContent}>{review.content}</Text>
                      </View>
                    )
                  })}
                </ScrollView>
              ) : (
                <View style={styles.centerState}>
                  <MaterialCommunityIcons name="comment-outline" size={22} color={colors.textMuted} />
                  <Text style={styles.centerStateText}>아직 등록된 리뷰가 없습니다. 첫 리뷰를 남겨보세요.</Text>
                </View>
              )}
            </View>

            <View style={styles.composer}>
              <View style={styles.composerHeader}>
                <Text style={styles.composerTitle}>{isLoggedIn ? "리뷰 남기기" : "로그인 후 리뷰 작성 가능"}</Text>
                <Text style={styles.composerCount}>{draft.trim().length}/500</Text>
              </View>
              <TextInput
                value={draft}
                onChangeText={(nextValue) => setDraft(nextValue.slice(0, 500))}
                placeholder={isLoggedIn ? "접근성, 청결도, 혼잡도 등을 남겨주세요." : "로그인 후 리뷰를 작성할 수 있습니다."}
                placeholderTextColor={colors.textMuted}
                style={[styles.input, !isLoggedIn && styles.inputDisabled]}
                multiline
                textAlignVertical="top"
                editable={isLoggedIn && !submitting}
              />
              <View style={styles.composerFooter}>
                <Text style={styles.composerHint}>
                  {isLoggedIn ? "짧고 구체적인 후기가 다른 사용자에게 도움이 됩니다." : "리뷰 열람은 가능하지만 작성은 로그인 후에만 가능합니다."}
                </Text>
                <Pressable
                  style={[styles.submitButton, (!isLoggedIn || submitting) && styles.submitButtonDisabled]}
                  onPress={() => void handleSubmit()}
                  disabled={!isLoggedIn || submitting}
                  testID="zone-review-submit"
                >
                  <Text style={styles.submitButtonText}>{submitting ? "등록 중..." : "리뷰 등록"}</Text>
                </Pressable>
              </View>
            </View>
          </View>
        </KeyboardAvoidingView>
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
    maxHeight: "88%",
    backgroundColor: colors.surface,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    paddingHorizontal: 18,
    paddingTop: 10,
    paddingBottom: 18,
    gap: 14,
  },
  dragHandle: {
    alignSelf: "center",
    width: 48,
    height: 5,
    borderRadius: 99,
    backgroundColor: colors.border,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 10,
  },
  headerTextWrap: {
    flex: 1,
    gap: 4,
  },
  kicker: {
    color: colors.primary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 1,
    textTransform: "uppercase",
  },
  title: {
    color: colors.text,
    fontSize: 21,
    fontWeight: "800",
  },
  subtitle: {
    color: colors.textMuted,
    fontSize: 13,
    lineHeight: 18,
  },
  closeButton: {
    width: 34,
    height: 34,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primarySoft,
  },
  listWrap: {
    minHeight: 220,
    maxHeight: 320,
  },
  reviewListContent: {
    gap: 10,
    paddingBottom: 8,
  },
  reviewCard: {
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    padding: 14,
    gap: 10,
  },
  reviewHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  authorRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  avatarImage: {
    width: 38,
    height: 38,
    borderRadius: radius.full,
  },
  avatarFallback: {
    width: 38,
    height: 38,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primarySoft,
  },
  avatarFallbackText: {
    color: colors.primary,
    fontWeight: "800",
  },
  authorMeta: {
    gap: 2,
  },
  authorName: {
    color: colors.text,
    fontWeight: "700",
    fontSize: 14,
  },
  authorDate: {
    color: colors.textMuted,
    fontSize: 12,
  },
  reviewContent: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 21,
  },
  centerState: {
    minHeight: 220,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingHorizontal: 24,
  },
  centerStateText: {
    color: colors.textMuted,
    textAlign: "center",
    lineHeight: 20,
  },
  retryButton: {
    marginTop: 4,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: radius.full,
    backgroundColor: colors.primarySoft,
  },
  retryButtonText: {
    color: colors.primary,
    fontWeight: "700",
  },
  composer: {
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 14,
    gap: 10,
  },
  composerHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  composerTitle: {
    color: colors.text,
    fontWeight: "700",
  },
  composerCount: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "700",
  },
  input: {
    minHeight: 104,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: colors.text,
  },
  inputDisabled: {
    backgroundColor: colors.surfaceMuted,
  },
  composerFooter: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  composerHint: {
    flex: 1,
    color: colors.textMuted,
    fontSize: 12,
    lineHeight: 18,
  },
  submitButton: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
  },
  submitButtonDisabled: {
    opacity: 0.45,
  },
  submitButtonText: {
    color: colors.surface,
    fontWeight: "800",
  },
})
