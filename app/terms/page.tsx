"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { ScrollArea } from "@/components/ui/scroll-area"
import { ChevronRight } from "lucide-react"
import { useRouter } from "next/navigation"
import Image from "next/image"

export default function TermsPage() {
  const router = useRouter()
  const [agreements, setAgreements] = useState({
    all: false,
    service: false,
    privacy: false,
    marketing: false,
  })

  const handleAllCheck = (checked: boolean) => {
    setAgreements({
      all: checked,
      service: checked,
      privacy: checked,
      marketing: checked,
    })
  }

  const handleIndividualCheck = (key: keyof typeof agreements, checked: boolean) => {
    const newAgreements = { ...agreements, [key]: checked }
    newAgreements.all = newAgreements.service && newAgreements.privacy && newAgreements.marketing
    setAgreements(newAgreements)
  }

  const canProceed = agreements.service && agreements.privacy

  const handleSubmit = () => {
    if (canProceed) {
      console.log("[v0] Terms agreed:", agreements)
      router.push("/signup")
    }
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md shadow-lg border-2">
        <CardHeader className="text-center space-y-4">
          <div className="flex justify-center">
            <Image src="/images/logodesign.png" alt="너굴맵 로고" width={80} height={80} className="object-contain" />
          </div>
          <div className="space-y-2">
            <CardTitle className="text-2xl font-bold text-foreground">약관 동의</CardTitle>
            <CardDescription className="text-muted-foreground">서비스 이용을 위해 약관에 동의해주세요</CardDescription>
          </div>
        </CardHeader>

        <CardContent className="space-y-6 pb-8">
          <div className="space-y-4">
            <div className="flex items-center space-x-3 p-4 bg-muted rounded-lg border-2 border-border">
              <Checkbox
                id="all"
                checked={agreements.all}
                onCheckedChange={handleAllCheck}
                className="border-2 data-[state=checked]:bg-primary data-[state=checked]:border-primary"
              />
              <label htmlFor="all" className="text-sm font-bold text-foreground cursor-pointer flex-1 leading-relaxed">
                전체 동의
              </label>
            </div>

            <div className="space-y-3">
              <div className="flex items-start space-x-3 p-3 rounded-lg hover:bg-muted/50 transition-colors">
                <Checkbox
                  id="service"
                  checked={agreements.service}
                  onCheckedChange={(checked) => handleIndividualCheck("service", checked as boolean)}
                  className="mt-0.5 border-2 data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                />
                <div className="flex-1 space-y-1">
                  <label htmlFor="service" className="text-sm text-foreground cursor-pointer flex items-center gap-1">
                    <span className="font-medium">(필수)</span> 서비스 이용약관
                  </label>
                  <ScrollArea className="h-20 w-full rounded border border-border bg-background p-3">
                    <div className="text-xs text-muted-foreground space-y-2 leading-relaxed">
                      <p className="font-semibold text-foreground">제1조 (목적)</p>
                      <p>
                        본 약관은 너굴맵(이하 "회사")이 제공하는 흡연구역 정보 서비스(이하 "서비스")의 이용과 관련하여
                        회사와 이용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.
                      </p>
                      <p className="font-semibold text-foreground mt-3">제2조 (정의)</p>
                      <p>
                        본 약관에서 사용하는 용어의 정의는 다음과 같습니다. "서비스"란 회사가 제공하는 흡연구역 위치
                        정보 조회 및 등록 서비스를 의미합니다.
                      </p>
                      <p className="font-semibold text-foreground mt-3">제3조 (약관의 효력 및 변경)</p>
                      <p>
                        본 약관은 서비스를 이용하고자 하는 모든 회원에 대하여 그 효력을 발생합니다. 회사는 필요한 경우
                        약관을 변경할 수 있으며, 변경된 약관은 공지사항을 통해 고지합니다.
                      </p>
                    </div>
                  </ScrollArea>
                </div>
                <ChevronRight className="w-4 h-4 text-muted-foreground flex-shrink-0 mt-1" />
              </div>

              <div className="flex items-start space-x-3 p-3 rounded-lg hover:bg-muted/50 transition-colors">
                <Checkbox
                  id="privacy"
                  checked={agreements.privacy}
                  onCheckedChange={(checked) => handleIndividualCheck("privacy", checked as boolean)}
                  className="mt-0.5 border-2 data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                />
                <div className="flex-1 space-y-1">
                  <label htmlFor="privacy" className="text-sm text-foreground cursor-pointer flex items-center gap-1">
                    <span className="font-medium">(필수)</span> 개인정보 처리방침
                  </label>
                  <ScrollArea className="h-20 w-full rounded border border-border bg-background p-3">
                    <div className="text-xs text-muted-foreground space-y-2 leading-relaxed">
                      <p className="font-semibold text-foreground">1. 개인정보의 수집 및 이용목적</p>
                      <p>
                        회사는 다음의 목적을 위하여 개인정보를 처리합니다: 회원 가입 및 관리, 서비스 제공, 고객 문의사항
                        처리
                      </p>
                      <p className="font-semibold text-foreground mt-3">2. 수집하는 개인정보 항목</p>
                      <p>닉네임, 프로필 사진, 소셜 로그인 정보(카카오/네이버/구글 계정 정보)</p>
                      <p className="font-semibold text-foreground mt-3">3. 개인정보의 보유 및 이용기간</p>
                      <p>회원 탈퇴 시까지 보유하며, 관련 법령에 따라 일정 기간 보관할 수 있습니다.</p>
                      <p className="font-semibold text-foreground mt-3">4. 개인정보의 제3자 제공</p>
                      <p>회사는 이용자의 동의 없이 개인정보를 제3자에게 제공하지 않습니다.</p>
                    </div>
                  </ScrollArea>
                </div>
                <ChevronRight className="w-4 h-4 text-muted-foreground flex-shrink-0 mt-1" />
              </div>

              <div className="flex items-start space-x-3 p-3 rounded-lg hover:bg-muted/50 transition-colors">
                <Checkbox
                  id="marketing"
                  checked={agreements.marketing}
                  onCheckedChange={(checked) => handleIndividualCheck("marketing", checked as boolean)}
                  className="mt-0.5 border-2 data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                />
                <div className="flex-1 space-y-1">
                  <label htmlFor="marketing" className="text-sm text-foreground cursor-pointer flex items-center gap-1">
                    <span className="font-medium text-muted-foreground">(선택)</span> 마케팅 정보 수신 동의
                  </label>
                  <p className="text-xs text-muted-foreground leading-relaxed">
                    이벤트, 프로모션 등 마케팅 정보를 받아볼 수 있습니다
                  </p>
                </div>
              </div>
            </div>
          </div>

          <Button
            onClick={handleSubmit}
            disabled={!canProceed}
            className="w-full bg-primary hover:bg-primary/90 text-primary-foreground font-medium py-6 h-auto rounded-lg transition-all hover:shadow-md disabled:opacity-50"
          >
            다음
          </Button>

          <p className="text-xs text-center text-muted-foreground leading-relaxed">
            필수 약관에 동의하지 않으면 서비스를 이용할 수 없습니다
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
