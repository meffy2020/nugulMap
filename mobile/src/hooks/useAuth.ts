import { useCallback, useEffect, useState } from "react"
import AsyncStorage from "@react-native-async-storage/async-storage"
import { Linking } from "react-native"
import Constants from "expo-constants"
import { getCurrentUser, validateToken } from "../services/nugulApi"
import type { UserProfile } from "../types"

const ACCESS_TOKEN_KEY = "@nugulmap:access-token:v1"
const EXPO_EXTRA = (Constants.expoConfig?.extra || {}) as {
  apiBaseUrl?: string
  oauthRedirectUri?: string
}
const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ||
  EXPO_EXTRA.apiBaseUrl ||
  "https://api.nugulmap.com"
const OAUTH_REDIRECT_URI =
  process.env.EXPO_PUBLIC_OAUTH_REDIRECT_URI ||
  EXPO_EXTRA.oauthRedirectUri ||
  "nugulmap://oauth/callback"

export type SocialLoginProvider = "kakao" | "naver" | "google"

function parseQueryParams(url: string): Record<string, string> {
  const questionIndex = url.indexOf("?")
  if (questionIndex < 0) return {}

  const hashIndex = url.indexOf("#", questionIndex)
  const rawQuery = hashIndex >= 0 ? url.slice(questionIndex + 1, hashIndex) : url.slice(questionIndex + 1)
  if (!rawQuery) return {}

  const params = new URLSearchParams(rawQuery)
  const entries: Record<string, string> = {}
  params.forEach((value, key) => {
    entries[key] = value
  })
  return entries
}

function normalizeUriWithoutQuery(uri: string): string {
  const normalized = String(uri || "").split("?")[0].trim()
  return normalized.endsWith("/") ? normalized.slice(0, -1) : normalized
}

export function useAuth() {
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isAuthenticating, setIsAuthenticating] = useState(false)
  const [authMessage, setAuthMessage] = useState<string | null>(null)

  useEffect(() => {
    void (async () => {
      const token = await AsyncStorage.getItem(ACCESS_TOKEN_KEY)
      if (!token) {
        setIsLoading(false)
        return
      }

      setAccessToken(token)
      const me = await getCurrentUser(token)
      setUser(me)
      setIsLoading(false)
    })()
  }, [])

  const saveToken = useCallback(async (token: string): Promise<boolean> => {
    const normalized = token.trim()
    if (!normalized) return false

    const valid = await validateToken(normalized)
    if (!valid) return false

    await AsyncStorage.setItem(ACCESS_TOKEN_KEY, normalized)
    setAccessToken(normalized)
    const me = await getCurrentUser(normalized)
    setUser(me)
    return true
  }, [])

  const handleOAuthCallbackUrl = useCallback(
    async (url: string) => {
      const expected = normalizeUriWithoutQuery(OAUTH_REDIRECT_URI).toLowerCase()
      const actual = normalizeUriWithoutQuery(url).toLowerCase()
      if (actual !== expected) {
        return
      }

      setIsAuthenticating(true)
      const params = parseQueryParams(url)
      const accessTokenFromCallback = params.accessToken
      const errorMessage = params.error

      if (errorMessage) {
        setAuthMessage(`로그인 실패: ${errorMessage}`)
        setIsAuthenticating(false)
        return
      }

      if (!accessTokenFromCallback) {
        setAuthMessage("로그인 응답에 accessToken 이 없습니다.")
        setIsAuthenticating(false)
        return
      }

      const saved = await saveToken(accessTokenFromCallback)
      if (!saved) {
        setAuthMessage("토큰 검증에 실패했습니다. 다시 로그인해 주세요.")
        setIsAuthenticating(false)
        return
      }

      const needsSignup = params.profileComplete === "false"
      setAuthMessage(needsSignup ? "로그인은 완료되었지만 추가 프로필 설정이 필요합니다." : "로그인 성공")
      setIsAuthenticating(false)
    },
    [saveToken],
  )

  useEffect(() => {
    const subscription = Linking.addEventListener("url", ({ url }) => {
      void handleOAuthCallbackUrl(url)
    })

    void (async () => {
      const initialUrl = await Linking.getInitialURL()
      if (initialUrl) {
        await handleOAuthCallbackUrl(initialUrl)
      }
    })()

    return () => {
      subscription.remove()
    }
  }, [handleOAuthCallbackUrl])

  const clearToken = async () => {
    await AsyncStorage.removeItem(ACCESS_TOKEN_KEY)
    setAccessToken(null)
    setUser(null)
    setAuthMessage(null)
  }

  const refreshUser = async () => {
    if (!accessToken) {
      setUser(null)
      return
    }
    const me = await getCurrentUser(accessToken)
    setUser(me)
  }

  const startSocialLogin = async (provider: SocialLoginProvider): Promise<void> => {
    setIsAuthenticating(true)
    setAuthMessage(null)
    try {
      const loginUrl = `${API_BASE_URL}/api/oauth2/authorization/${provider}?redirect_uri=${encodeURIComponent(OAUTH_REDIRECT_URI)}`
      await Linking.openURL(loginUrl)
      setIsAuthenticating(false)
    } catch {
      setAuthMessage("로그인 페이지를 열 수 없습니다.")
      setIsAuthenticating(false)
    }
  }

  const clearAuthMessage = () => {
    setAuthMessage(null)
  }

  return {
    accessToken,
    user,
    isLoading,
    isLoggedIn: Boolean(accessToken),
    isAuthenticating,
    authMessage,
    saveToken,
    clearToken,
    refreshUser,
    startSocialLogin,
    clearAuthMessage,
  }
}
