import type { Metadata } from "next"
import { SupportForm } from "../support/support-form"

export const metadata: Metadata = {
  title: "계정 삭제 안내 | 너굴맵",
  description: "너굴맵 계정 삭제 요청 안내",
}

export default function AccountDeletionPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">너굴맵 · NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">계정 삭제 안내</h1>
          <p className="text-sm text-muted-foreground">앱과 웹에서 계정 삭제를 요청할 수 있습니다.</p>
        </header>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">앱에서 삭제하기</h2>
          <ol className="list-decimal space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>너굴맵 앱을 열고 로그인합니다.</li>
            <li>iPhone에서는 지도 오른쪽의 계정 버튼을 누르고 내 정보 탭을 엽니다.</li>
            <li>Android에서는 지도 아래의 메뉴 버튼을 누르고 내 프로필 / 내 구역을 엽니다.</li>
            <li>계정 삭제를 선택합니다.</li>
            <li>삭제 안내를 확인한 뒤 삭제를 확정합니다.</li>
          </ol>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">웹으로 요청하기</h2>
          <p className="text-sm leading-7 text-muted-foreground">
            앱에 접근할 수 없다면 아래 폼에 계정 이메일, 로그인 제공자(Apple·Kakao·Naver·Google)와
            삭제 요청 내용을 적어 주세요. 본인 확인과 처리 결과는 입력한 이메일로 안내합니다.
          </p>
        </section>

        <SupportForm
          description="삭제할 계정의 이메일과 로그인 제공자를 문의 내용에 적어 주세요. 비밀번호나 인증 토큰은 입력하지 마세요."
          fixedCategory="ACCOUNT_DELETION"
          heading="웹 계정 삭제 요청"
          submitLabel="계정 삭제 요청 접수"
        />

        <section className="space-y-3">
          <h2 className="text-xl font-bold">삭제되는 정보</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>계정 이메일, 닉네임, 소셜 로그인 연결 식별자와 프로필 이미지</li>
            <li>서버에 저장된 계정 세션과 재인증에 필요한 연결 정보. 앱에서 삭제하면 해당 기기의 로그인 정보도 함께 정리됩니다.</li>
            <li>작성한 리뷰, 직접 등록한 장소 제보와 제보 이미지</li>
            <li>계정과 연결된 신고·차단 관계</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">삭제 후 제한적으로 남을 수 있는 정보</h2>
          <p className="text-sm leading-7 text-muted-foreground">
            법령 준수, 처리 중인 신고·분쟁 대응 또는 반복 악용 방지에 필요한 최소 운영 기록은 해당 목적이
            끝나거나 법정 보존기간이 끝날 때까지 제한적으로 보관한 뒤 삭제합니다. 소셜 로그인 제공자의
            계정 자체는 삭제되지 않으며 해당 제공자 설정에서 별도로 관리해야 합니다.
          </p>
        </section>

        <nav className="flex flex-wrap gap-4 text-sm font-semibold">
          <a className="underline" href="/privacy">개인정보 처리방침</a>
          <a className="underline" href="/support">일반 문의·콘텐츠 신고</a>
          <a className="underline" href="/terms">서비스 이용약관</a>
        </nav>
      </div>
    </main>
  )
}
