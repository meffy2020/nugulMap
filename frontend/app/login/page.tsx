"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import Image from "next/image"
import { Loader2 } from "lucide-react"

export default function LoginPage() {
  const [isLoading, setIsLoading] = useState<string | null>(null)

  const handleSocialLogin = (provider: "kakao" | "naver" | "google") => {
    setIsLoading(provider)
    // 백엔드 OAuth2 인증 엔드포인트로 리다이렉트
    // 환경 변수 NEXT_PUBLIC_API_BASE_URL (예: https://api.nugulmap.com) 사용
    const backendUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "https://api.nugulmap.com";
    window.location.href = `${backendUrl}/api/oauth2/authorization/${provider}`;
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6 relative overflow-hidden">
      {/* Decorative background elements */}
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-primary/5 rounded-full blur-3xl" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-primary/5 rounded-full blur-3xl" />

      <Card className="w-full max-w-md shadow-[0_20px_50px_rgba(0,0,0,0.1)] border-none rounded-[3rem] overflow-hidden bg-background/80 backdrop-blur-xl relative z-10">
        <CardHeader className="text-center space-y-6 pt-12 pb-8">
          <div className="flex justify-center">
            <div className="w-24 h-24 bg-primary rounded-[2rem] flex items-center justify-center shadow-2xl rotate-3 hover:rotate-0 transition-transform duration-500">
              <Image src="/images/pin.png" alt="NugulMap Logo" width={48} height={48} className="invert brightness-0" />
            </div>
          </div>
          <div className="space-y-2">
            <CardTitle className="text-5xl font-black text-foreground tracking-tighter" style={{ fontFamily: "'Righteous', sans-serif" }}>
              NugulMap
            </CardTitle>
            <CardDescription className="text-lg text-muted-foreground font-medium">대한민국 모든 너구리들의 쉼터</CardDescription>
          </div>
        </CardHeader>

        <CardContent className="space-y-4 px-8 pb-12">
          <Button
            onClick={() => handleSocialLogin("kakao")}
            disabled={isLoading !== null}
            className="w-full font-black py-7 h-auto rounded-2xl transition-all hover:shadow-xl hover:scale-[1.02] active:scale-95 border-none text-base"
            style={{
              backgroundColor: "#FEE500",
              color: "#3C1E1E",
            }}
          >
            {isLoading === "kakao" ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <div className="flex items-center gap-3">
                <svg className="w-6 h-6" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 3C6.5 3 2 6.6 2 11c0 2.8 1.9 5.3 4.8 6.7L6 21.5c-.1.4.3.7.6.5l3.6-2.5c.6.1 1.2.2 1.8.2 5.5 0 10-3.6 10-8S17.5 3 12 3z" />
                </svg>
                카카오로 3초만에 시작
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("naver")}
            disabled={isLoading !== null}
            className="w-full font-black py-7 h-auto rounded-2xl transition-all hover:shadow-xl hover:scale-[1.02] active:scale-95 border-none text-base"
            style={{
              backgroundColor: "#03C75A",
              color: "#FFFFFF",
            }}
          >
            {isLoading === "naver" ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-6 h-6 bg-white rounded-md flex items-center justify-center">
                  <span className="text-[#03C75A] text-xs font-black">N</span>
                </div>
                네이버로 로그인
              </div>
            )}
          </Button>

          <Button
            onClick={() => handleSocialLogin("google")}
            disabled={isLoading !== null}
            className="w-full font-black py-7 h-auto rounded-2xl transition-all hover:shadow-xl hover:scale-[1.02] active:scale-95 bg-white text-foreground border-2 border-border/50 hover:bg-gray-50 text-base"
          >
            {isLoading === "google" ? (
              <Loader2 className="w-5 h-5 animate-spin" />
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
                구글로 로그인
              </div>
            )}
          </Button>

          <div className="text-center pt-8">
            <p className="text-[10px] text-muted-foreground font-bold leading-relaxed uppercase tracking-widest opacity-60">
              By continuing, you agree to our <br/>
              <span className="text-foreground hover:underline cursor-pointer">Terms of Service</span> & <span className="text-foreground hover:underline cursor-pointer">Privacy Policy</span>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
