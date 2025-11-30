"use client"

import type React from "react"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
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

    // TODO: Implement actual signup logic
    console.log("[v0] Signup:", { nickname, useDefaultProfile, hasCustomImage: !!profileImage })

    setTimeout(() => {
      setIsLoading(false)
      router.push("/")
    }, 2000)
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
