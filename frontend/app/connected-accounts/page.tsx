import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "로그인 연결 관리 | 너굴맵",
  description: "너굴맵 소셜 로그인 연결 확인과 해제 안내",
}

const providers = [
  {
    name: "Apple",
    description: "Apple 계정 설정의 ‘Apple로 로그인’에서 너굴맵 사용을 중단할 수 있습니다.",
    href: "https://account.apple.com/",
  },
  {
    name: "Google",
    description: "Google 계정의 서드 파티 연결에서 너굴맵의 연결 또는 접근 권한을 삭제할 수 있습니다.",
    href: "https://myaccount.google.com/connections",
  },
  {
    name: "Kakao",
    description: "카카오계정의 연결된 서비스 관리에서 너굴맵 연결을 확인하고 해제할 수 있습니다.",
    href: "https://accounts.kakao.com/weblogin/account/partner",
  },
  {
    name: "Naver",
    description: "네이버의 연결된 서비스 관리에서 정보 제공을 철회할 수 있습니다.",
    href: "https://nid.naver.com/user2/help/externalAuth.nhn?m=viewExternalAuth",
  },
] as const

export default function ConnectedAccountsPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">너굴맵 · NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">로그인 연결 관리</h1>
          <p className="text-sm leading-7 text-muted-foreground">
            너굴맵은 소셜 로그인 비밀번호를 저장하지 않습니다. 앱에서 로그아웃하면 이 기기의 너굴맵 세션은
            종료되지만, 로그인 제공자 계정의 연결 기록은 남아 있을 수 있습니다.
          </p>
        </header>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">제공자에서 연결 해제하기</h2>
          <div className="grid gap-3">
            {providers.map((provider) => (
              <article className="rounded-lg border bg-card p-5" key={provider.name}>
                <h3 className="font-bold">{provider.name}</h3>
                <p className="mt-2 text-sm leading-7 text-muted-foreground">{provider.description}</p>
                <a
                  className="mt-3 inline-flex min-h-11 items-center font-semibold underline"
                  href={provider.href}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  {provider.name} 연결 관리 열기
                </a>
              </article>
            ))}
          </div>
        </section>

        <section className="space-y-3 rounded-lg border bg-muted/40 p-5 text-sm leading-7">
          <h2 className="text-lg font-bold">너굴맵 데이터도 삭제하려면</h2>
          <p className="text-muted-foreground">
            제공자 연결 해제만으로 너굴맵 계정과 작성 데이터가 삭제되지는 않습니다. 앱의 계정 화면에서 계정 삭제를
            실행하거나 웹 삭제 요청을 함께 접수해 주세요.
          </p>
          <a className="font-semibold underline" href="/account-deletion">너굴맵 계정 삭제 안내</a>
        </section>

        <nav className="flex flex-wrap gap-4 text-sm font-semibold">
          <a className="underline" href="/privacy">개인정보 처리방침</a>
          <a className="underline" href="/support">고객지원</a>
          <a className="underline" href="/terms">서비스 이용약관</a>
        </nav>
      </div>
    </main>
  )
}
