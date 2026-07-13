import type { Metadata } from "next"
import { SupportForm } from "./support-form"

export const metadata: Metadata = {
  title: "지원 및 콘텐츠 신고 | 너굴맵",
  description: "너굴맵 이용 문의, 계정 지원, 콘텐츠와 사용자 신고 안내",
}

export default function SupportPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">너굴맵 · NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">지원 및 콘텐츠 신고</h1>
          <p className="text-sm leading-7 text-muted-foreground">
            계정·위치 정보 문의와 부적절한 리뷰, 장소 제보 또는 사용자 신고를 접수합니다.
          </p>
        </header>

        <section className="grid gap-3 rounded-lg border bg-card p-5 text-sm leading-7 sm:grid-cols-[8rem_1fr]">
          <strong>서비스</strong>
          <span>너굴맵(NugulMap)</span>
          <strong>지원 채널</strong>
          <span>이 페이지의 공개 문의 폼</span>
          <strong>회신 방법</strong>
          <span>입력한 이메일로 접수 결과와 필요한 후속 절차 안내</span>
        </section>

        <SupportForm />

        <section className="space-y-3">
          <h2 className="text-xl font-bold">리뷰·장소·사용자 신고</h2>
          <ol className="list-decimal space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>앱의 신고 메뉴에서 신고 사유를 선택해 제출합니다.</li>
            <li>앱을 이용할 수 없다면 대상 장소나 리뷰를 식별할 수 있는 링크 또는 화면 정보를 문의 내용에 적습니다.</li>
            <li>개인정보 노출, 불법 콘텐츠, 괴롭힘, 광고·도배 등 구체적인 사유를 적습니다.</li>
          </ol>
          <p className="rounded-lg border bg-muted/40 p-4 text-sm leading-7">
            운영자는 신고를 접수한 뒤 대상과 사유를 검토합니다. 개인정보 노출과 안전 관련 신고를
            우선 확인하며, 위반이 확인되면 콘텐츠 숨김·삭제 또는 추가 확인 조치를 합니다.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">처리 기준</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>접수가 완료되면 화면에 접수번호가 표시됩니다.</li>
            <li>내용 확인이나 본인 확인이 필요하면 입력한 이메일로 요청합니다.</li>
            <li>신고자와 신고 대상의 개인정보는 처리에 필요한 범위에서만 확인합니다.</li>
            <li>검토 결과와 운영 조치는 개인정보 보호와 악용 방지를 위해 일부만 안내될 수 있습니다.</li>
          </ul>
        </section>

        <nav className="flex flex-wrap gap-4 text-sm font-semibold">
          <a className="underline" href="/privacy">개인정보 처리방침</a>
          <a className="underline" href="/account-deletion">계정 삭제 안내</a>
          <a className="underline" href="/terms">서비스 이용약관</a>
        </nav>
      </div>
    </main>
  )
}
