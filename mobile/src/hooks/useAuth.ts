import { useCallback, useEffect, useRef, useState } from "react"
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
const AUTH_FLOW_TIMEOUT_MS = 120_000

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
  const [needsProfileSetup, setNeedsProfileSetup] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isAuthenticating, setIsAuthenticating] = useState(false)
  const [authMessage, setAuthMessage] = useState<string | null>(null)
  const authFlowTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearAuthFlowTimer = useCallback(() => {
    if (!authFlowTimerRef.current) return
    clearTimeout(authFlowTimerRef.current)
    authFlowTimerRef.current = null
  }, [])

  const isProfileSetupRequired = useCallback((profile: UserProfile | null) => {
    if (!profile) return false
    return !String(profile.nickname || "").trim()
  }, [])

  useEffect(() => {
    let isMounted = true

    void (async () => {
      const token = await AsyncStorage.getItem(ACCESS_TOKEN_KEY)
      if (!token) {
        if (isMounted) {
          setIsLoading(false)
        }
        return
      }

      const valid = await validateToken(token)
      if (!valid) {
        await AsyncStorage.removeItem(ACCESS_TOKEN_KEY)
        if (isMounted) {
          setAccessToken(null)
          setUser(null)
          setAuthMessage("로그인이 만료되었습니다. 다시 로그인해 주세요.")
          setIsLoading(false)
        }
        return
      }

      if (!isMounted) return
      setAccessToken(token)
      const me = await getCurrentUser(token)
      if (!isMounted) return
      setUser(me)
      const needsSetup = isProfileSetupRequired(me)
      setNeedsProfileSetup(needsSetup)
      if (needsSetup) {
        setAuthMessage("로그인은 완료되었지만 추가 프로필 설정이 필요합니다.")
      }
      setIsLoading(false)
    })()

    return () => {
      isMounted = false
    }
  }, [isProfileSetupRequired])

  const saveToken = useCallback(async (token: string): Promise<boolean> => {
    try {
      const normalized = token.trim()
      if (!normalized) return false

      const valid = await validateToken(normalized)
      if (!valid) return false

      await AsyncStorage.setItem(ACCESS_TOKEN_KEY, normalized)
      setAccessToken(normalized)
      const me = await getCurrentUser(normalized)
      setUser(me)
      setNeedsProfileSetup(isProfileSetupRequired(me))
      return true
    } catch {
      return false
    }
  }, [isProfileSetupRequired])

  const handleOAuthCallbackUrl = useCallback(
    async (url: string) => {
      const expected = normalizeUriWithoutQuery(OAUTH_REDIRECT_URI).toLowerCase()
      const actual = normalizeUriWithoutQuery(url).toLowerCase()
      if (actual !== expected) {
        return
      }

      clearAuthFlowTimer()
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
      setNeedsProfileSetup(needsSignup)
      setAuthMessage(needsSignup ? "로그인은 완료되었지만 추가 프로필 설정이 필요합니다." : "로그인 성공")
      setIsAuthenticating(false)
    },
    [saveToken, clearAuthFlowTimer],
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

  useEffect(() => {
    return () => {
      clearAuthFlowTimer()
    }
  }, [clearAuthFlowTimer])

  const clearToken = async () => {
    clearAuthFlowTimer()
    await AsyncStorage.removeItem(ACCESS_TOKEN_KEY)
    setAccessToken(null)
    setUser(null)
    setNeedsProfileSetup(false)
    setAuthMessage(null)
    setIsAuthenticating(false)
  }

  const refreshUser = async () => {
    if (!accessToken) {
      setUser(null)
      return
    }
    const me = await getCurrentUser(accessToken)
    setUser(me)
    setNeedsProfileSetup(isProfileSetupRequired(me))
  }

  const startSocialLogin = async (provider: SocialLoginProvider): Promise<void> => {
    clearAuthFlowTimer()
    setIsAuthenticating(true)
    setAuthMessage(null)
    try {
      const loginUrl = `${API_BASE_URL}/api/oauth2/authorization/${provider}?redirect_uri=${encodeURIComponent(OAUTH_REDIRECT_URI)}`
      await Linking.openURL(loginUrl)
      authFlowTimerRef.current = setTimeout(() => {
        setIsAuthenticating(false)
        setAuthMessage((prev) => prev || "로그인이 완료되지 않았습니다. 다시 시도해 주세요.")
      }, AUTH_FLOW_TIMEOUT_MS)
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
    needsProfileSetup,
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
