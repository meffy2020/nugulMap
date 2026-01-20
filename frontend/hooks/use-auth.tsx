"use client"

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react"
import { getCurrentUser, type UserProfile } from "@/lib/api"

interface AuthContextType {
  user: UserProfile | null
  isLoading: boolean
  login: () => void
  logout: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const refreshUser = async () => {
    setIsLoading(true)
    try {
      const currentUser = await getCurrentUser()
      setUser(currentUser)
    } catch (err) {
      console.error("Auth initialization failed:", err)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    refreshUser()
  }, [])

  const login = () => {
    const backendUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"
    window.location.href = `${backendUrl}/api/oauth2/authorization/kakao`
  }

  const logout = () => {
    // 백엔드 로그아웃 API 호출이 필요할 수 있으나, 일단 클라이언트 쿠키 만료 유도
    document.cookie = "accessToken=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;"
    document.cookie = "refreshToken=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;"
    setUser(null)
    window.location.href = "/"
  }

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
