"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import Image from "next/image"

export default function LoginPage() {
  const [isLoading, setIsLoading] = useState<string | null>(null)

  const handleSocialLogin = async (provider: "kakao" | "naver" | "google") => {
    setIsLoading(provider)

    // TODO: Implement actual social login logic
    console.log(`[v0] ${provider} login initiated`)

    // Simulate API call
    setTimeout(() => {
      setIsLoading(null)
      console.log(`[v0] ${provider} login completed`)
    }, 2000)
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center space-y-6">
          <div className="flex justify-center">
            <Image src="/main-logo.png" alt="너굴맵 로고" width={80} height={80} className="object-contain" />
          </div>
          <div>
            <CardTitle className="text-2xl font-bold text-foreground">너굴맵</CardTitle>
            <CardDescription className="text-muted-foreground mt-2">흡연구역을 쉽게 찾아보세요</CardDescription>
          </div>
        </CardHeader>

        <CardContent className="space-y-4">
          <Button
            onClick={() => handleSocialLogin("kakao")}
            disabled={isLoading !== null}
            className="w-full bg-[#FEE500] hover:bg-[#FEE500]/90 text-black font-medium py-3 h-auto"
          >
            {isLoading === "kakao" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-black/20 border-t-black rounded-full animate-spin" />
                카카오 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-5 h-5 bg-black rounded-full flex items-center justify-center">
                  <span className="text-[#FEE500] text-xs font-bold">K</span>
                </div>
                카카오로 시작하기
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("naver")}
            disabled={isLoading !== null}
            className="w-full bg-[#03C75A] hover:bg-[#03C75A]/90 text-white font-medium py-3 h-auto"
          >
            {isLoading === "naver" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                네이버 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-5 h-5 bg-white rounded flex items-center justify-center">
                  <span className="text-[#03C75A] text-xs font-bold">N</span>
                </div>
                네이버로 시작하기
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("google")}
            disabled={isLoading !== null}
            className="w-full bg-white hover:bg-gray-50 text-gray-700 border border-gray-300 font-medium py-3 h-auto"
          >
            {isLoading === "google" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-gray-300 border-t-gray-700 rounded-full animate-spin" />
                구글 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-5 h-5 bg-gradient-to-r from-blue-500 via-red-500 to-yellow-500 rounded flex items-center justify-center">
                  <span className="text-white text-xs font-bold">G</span>
                </div>
                구글로 시작하기
              </div>
            )}
          </Button>

          <div className="text-center pt-4">
            <p className="text-sm text-muted-foreground">
              로그인하면 <span className="text-accent hover:underline cursor-pointer">서비스 약관</span> 및{" "}
              <span className="text-accent hover:underline cursor-pointer">개인정보 처리방침</span>에 동의하는 것으로
              간주됩니다.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
