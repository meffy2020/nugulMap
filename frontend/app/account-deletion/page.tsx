import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "계정 삭제 안내 | 너굴맵",
  description: "너굴맵 계정 삭제 요청 안내",
}

export default function AccountDeletionPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">계정 삭제 안내</h1>
          <p className="text-sm text-muted-foreground">앱과 웹에서 계정 삭제를 요청할 수 있습니다.</p>
        </header>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">앱에서 삭제하기</h2>
          <ol className="list-decimal space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>너굴맵 앱을 열고 로그인합니다.</li>
            <li>지도 상단의 프로필 버튼을 누릅니다.</li>
            <li>내 정보 탭에서 계정 삭제를 선택합니다.</li>
            <li>삭제 안내를 확인한 뒤 삭제를 확정합니다.</li>
          </ol>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">웹으로 요청하기</h2>
          <p className="text-sm leading-7 text-muted-foreground">
            앱에 접근할 수 없다면 계정 이메일, 로그인 제공자, 삭제 요청 내용을
            <a className="mx-1 font-semibold text-foreground underline" href="mailto:support@nugulmap.com">
              support@nugulmap.com
            </a>
            으로 보내주세요. 본인 확인 후 계정 삭제를 처리합니다.
          </p>
        </section>

        <section className="rounded-lg border bg-card p-5 text-sm leading-7">
          계정 삭제 시 로그인 토큰, 프로필 정보, 계정 식별 정보가 삭제됩니다. 사용자가 등록한
          공익성 장소 정보나 운영상 필요한 기록은 법령, 분쟁 대응, 서비스 무결성 유지를 위해
          제한적으로 보존될 수 있습니다.
        </section>
      </div>
    </main>
  )
}
