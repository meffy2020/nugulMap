import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "개인정보 처리방침 | 너굴맵",
  description: "너굴맵 개인정보 처리방침",
}

const sections = [
  {
    title: "1. 수집하는 개인정보",
    body: [
      "소셜 로그인 제공자에서 전달되는 이메일, 제공자 사용자 식별자, 닉네임과 프로필 이미지를 수집합니다.",
      "사용자가 흡연구역을 제보하거나 리뷰를 작성하면 주소, 좌표, 사진, 설명, 리뷰 내용과 작성자 정보가 저장될 수 있습니다.",
      "현재 위치는 주변 흡연구역을 보여주기 위해 사용되며, 서버 요청이나 운영 로그에 포함될 수 있습니다.",
    ],
  },
  {
    title: "2. 이용 목적",
    body: [
      "계정 생성, 로그인 유지, 프로필 표시, 흡연구역 조회와 제보, 리뷰 작성, 서비스 보안과 장애 대응에 사용합니다.",
      "위치 정보는 현재 지도 주변의 흡연구역을 찾고 지도 화면을 맞추는 데 사용합니다.",
    ],
  },
  {
    title: "3. 제3자 및 처리 위탁",
    body: [
      "로그인은 Apple, Kakao, Naver, Google 등 사용자가 선택한 제공자를 통해 처리됩니다.",
      "지도, 이미지 저장, 서버 호스팅, 운영 로그 처리를 위해 외부 인프라와 SDK가 사용될 수 있습니다.",
      "너굴맵은 법령상 요구되거나 사용자가 동의한 경우를 제외하고 개인정보를 임의로 판매하지 않습니다.",
    ],
  },
  {
    title: "4. 보관 및 삭제",
    body: [
      "계정 정보는 회원 탈퇴 시 삭제합니다. 다만 법령 준수, 분쟁 대응, 서비스 보안에 필요한 최소 정보는 정해진 기간 동안 보관될 수 있습니다.",
      "앱의 프로필 화면에서 계정 삭제를 시작할 수 있으며, 웹 삭제 요청은 /account-deletion 안내를 따릅니다.",
    ],
  },
  {
    title: "5. 이용자 권리",
    body: [
      "이용자는 앱에서 로그아웃하거나 계정 삭제를 요청할 수 있습니다.",
      "개인정보 열람, 정정, 삭제, 처리 정지 요청은 support@nugulmap.com 으로 보낼 수 있습니다.",
    ],
  },
]

export default function PrivacyPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">개인정보 처리방침</h1>
          <p className="text-sm text-muted-foreground">시행일: 2026년 6월 13일</p>
        </header>

        <section className="rounded-lg border bg-card p-5 text-sm leading-7">
          너굴맵은 흡연구역 위치 조회, 제보, 리뷰, 계정 기능을 제공하기 위해 필요한 범위에서만
          개인정보를 처리합니다. 실제 App Store와 Play Store 제출 정보는 이 문서와 동일하게
          유지합니다.
        </section>

        {sections.map((section) => (
          <section key={section.title} className="space-y-3">
            <h2 className="text-xl font-bold">{section.title}</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
              {section.body.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </section>
        ))}

        <section className="rounded-lg border bg-muted/40 p-5 text-sm leading-7">
          문의: <a className="font-semibold underline" href="mailto:support@nugulmap.com">support@nugulmap.com</a>
        </section>
      </div>
    </main>
  )
}
