"use client"

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react"
import { getCurrentUser, logoutCurrentUser, type UserProfile } from "@/lib/api"

interface AuthContextType {
  user: UserProfile | null
  isLoading: boolean
  login: () => void
  logout: () => Promise<void>
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

  const logout = async () => {
    try {
      await logoutCurrentUser()
      setUser(null)
      window.location.href = "/"
    } catch {
      window.alert("로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.")
    }
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
