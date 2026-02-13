import { useEffect, useState } from "react"
import AsyncStorage from "@react-native-async-storage/async-storage"
import { getCurrentUser, validateToken } from "../services/nugulApi"
import type { UserProfile } from "../types"

const ACCESS_TOKEN_KEY = "@nugulmap:access-token:v1"

export function useAuth() {
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

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

  const saveToken = async (token: string): Promise<boolean> => {
    const normalized = token.trim()
    if (!normalized) return false

    const valid = await validateToken(normalized)
    if (!valid) return false

    await AsyncStorage.setItem(ACCESS_TOKEN_KEY, normalized)
    setAccessToken(normalized)
    const me = await getCurrentUser(normalized)
    setUser(me)
    return true
  }

  const clearToken = async () => {
    await AsyncStorage.removeItem(ACCESS_TOKEN_KEY)
    setAccessToken(null)
    setUser(null)
  }

  const refreshUser = async () => {
    if (!accessToken) {
      setUser(null)
      return
    }
    const me = await getCurrentUser(accessToken)
    setUser(me)
  }

  return {
    accessToken,
    user,
    isLoading,
    isLoggedIn: Boolean(accessToken),
    saveToken,
    clearToken,
    refreshUser,
  }
}
