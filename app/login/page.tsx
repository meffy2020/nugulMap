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
      <Card className="w-full max-w-md shadow-lg border-2">
        <CardHeader className="text-center space-y-6 pb-8">
          <div className="flex justify-center">
            <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center">
              <Image src="/main-logo.jpg" alt="너굴맵 로고" width={64} height={64} className="object-contain" />
            </div>
          </div>
          <div className="space-y-2">
            <CardTitle className="text-3xl font-bold text-primary">너굴맵</CardTitle>
            <CardDescription className="text-base text-muted-foreground">흡연구역을 쉽게 찾아보세요</CardDescription>
          </div>
        </CardHeader>

        <CardContent className="space-y-4 pb-8">
          <Button
            onClick={() => handleSocialLogin("kakao")}
            disabled={isLoading !== null}
            className="w-full bg-[#FEE500] hover:bg-[#FEE500]/90 text-black font-medium py-6 h-auto rounded-lg transition-all hover:shadow-md"
          >
            {isLoading === "kakao" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-black/20 border-t-black rounded-full animate-spin" />
                카카오 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-6 h-6 bg-black rounded-full flex items-center justify-center">
                  <span className="text-[#FEE500] text-sm font-bold">K</span>
                </div>
                카카오로 시작하기
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("naver")}
            disabled={isLoading !== null}
            className="w-full bg-[#03C75A] hover:bg-[#03C75A]/90 text-white font-medium py-6 h-auto rounded-lg transition-all hover:shadow-md"
          >
            {isLoading === "naver" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                네이버 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-6 h-6 bg-white rounded flex items-center justify-center">
                  <span className="text-[#03C75A] text-sm font-bold">N</span>
                </div>
                네이버로 시작하기
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("google")}
            disabled={isLoading !== null}
            variant="outline"
            className="w-full font-medium py-6 h-auto rounded-lg transition-all hover:shadow-md hover:border-primary"
          >
            {isLoading === "google" ? (
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 border-2 border-primary/20 border-t-primary rounded-full animate-spin" />
                구글 로그인 중...
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <svg className="w-6 h-6" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                  />
                </svg>
                구글로 시작하기
              </div>
            )}
          </Button>

          <div className="text-center pt-6 border-t">
            <p className="text-xs text-muted-foreground leading-relaxed">
              로그인하면 <span className="text-accent hover:underline cursor-pointer font-medium">서비스 약관</span> 및{" "}
              <span className="text-accent hover:underline cursor-pointer font-medium">개인정보 처리방침</span>에
              동의하는 것으로 간주됩니다.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
