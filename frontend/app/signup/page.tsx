"use client"

import type React from "react"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { updateUserNickname, updateUserProfileImage } from "@/lib/api"
import { Camera, User } from "lucide-react"
import { useRouter } from "next/navigation"
import Image from "next/image"

export default function SignupPage() {
  const router = useRouter()
  const [nickname, setNickname] = useState("")
  const [profileImage, setProfileImage] = useState<string | null>(null)
  const [useDefaultProfile, setUseDefaultProfile] = useState(true)
  const [isLoading, setIsLoading] = useState(false)

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onloadend = () => {
        setProfileImage(reader.result as string)
        setUseDefaultProfile(false)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleDefaultProfile = () => {
    setUseDefaultProfile(true)
    setProfileImage(null)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)

    const USER_ID = 1; // ‼️‼️‼️ 임시 사용자 ID: 실제 로그인 구현 시 동적으로 변경해야 합니다. ‼️‼️‼️

    try {
      // 1. 닉네임 업데이트
      await updateUserNickname(USER_ID, nickname)

      // 2. 프로필 이미지 업데이트 (사용자가 이미지를 선택한 경우)
      if (profileImage && !useDefaultProfile) {
        const imageFile = base64ToFile(profileImage, "profile-image")
        await updateUserProfileImage(USER_ID, imageFile)
      }

      // 3. 성공 시 메인 페이지로 이동
      router.push("/")
    } catch (error) {
      console.error("[v0] Signup failed:", error)
      // TODO: 사용자에게 에러 메시지 표시
    } finally {
      setIsLoading(false)
    }
  }

  function base64ToFile(base64: string, filename: string): File {
    const arr = base64.split(',')
    const mime = arr[0].match(/:(.*?);/)?.[1]
    const bstr = atob(arr[1])
    let n = bstr.length
    const u8arr = new Uint8Array(n)
    while (n--) {
      u8arr[n] = bstr.charCodeAt(n)
    }
    return new File([u8arr], filename, { type: mime })
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md shadow-lg border-2">
        <CardHeader className="text-center space-y-4">
          <div className="flex justify-center">
            <Image src="/images/pin.png" alt="NugulMap Logo" width={80} height={80} className="object-contain" />
          </div>
          <div className="space-y-2">
            <CardTitle className="text-2xl font-bold text-foreground">프로필 설정</CardTitle>
            <CardDescription className="text-muted-foreground">너굴맵에서 사용할 프로필을 설정해주세요</CardDescription>
          </div>
        </CardHeader>

        <CardContent className="space-y-6 pb-8">
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="flex flex-col items-center gap-4">
              <div className="relative">
                <Avatar className="w-24 h-24 border-4 border-border">
                  {profileImage && !useDefaultProfile ? (
                    <AvatarImage src={profileImage || "/placeholder.svg"} alt="프로필 이미지" />
                  ) : (
                    <AvatarFallback className="bg-muted">
                      <User className="w-12 h-12 text-muted-foreground" />
                    </AvatarFallback>
                  )}
                </Avatar>
                <label
                  htmlFor="profile-upload"
                  className="absolute bottom-0 right-0 bg-primary text-primary-foreground rounded-full p-2 cursor-pointer hover:bg-primary/90 transition-all shadow-md"
                >
                  <Camera className="w-4 h-4" />
                  <input
                    id="profile-upload"
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={handleImageUpload}
                  />
                </label>
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleDefaultProfile}
                className="text-xs border-2 bg-transparent"
              >
                기본 프로필로 설정
              </Button>
            </div>

            <div className="space-y-2">
              <Label htmlFor="nickname" className="text-foreground font-medium">
                닉네임
              </Label>
              <Input
                id="nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="사용할 닉네임을 입력하세요"
                className="bg-input border-border text-foreground border-2 focus:border-primary focus:ring-2 focus:ring-primary/20"
                required
                maxLength={20}
              />
              <p className="text-xs text-muted-foreground">2-20자 이내로 입력해주세요</p>
            </div>

            <Button
              type="submit"
              disabled={isLoading || !nickname.trim() || nickname.length < 2}
              className="w-full bg-primary hover:bg-primary/90 text-primary-foreground font-medium py-6 h-auto rounded-lg transition-all hover:shadow-md disabled:opacity-50"
            >
              {isLoading ? (
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 border-2 border-primary-foreground/20 border-t-primary-foreground rounded-full animate-spin" />
                  프로필 설정 중...
                </div>
              ) : (
                "시작하기"
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
