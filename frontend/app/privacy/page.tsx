import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "개인정보 처리방침 | 너굴맵",
  description: "너굴맵 개인정보 처리방침과 문의·삭제 방법",
}

const dataRows = [
  {
    category: "계정·로그인",
    data: "이메일, 소셜 로그인 제공자와 사용자 식별자, 닉네임, 프로필 이미지, 인증 토큰",
    purpose: "계정 생성, 로그인 유지, 프로필 표시, 부정 이용 방지",
    retention: "계정 삭제 시 삭제. 인증 토큰은 로그아웃·계정 삭제 시 기기에서 삭제하며 유효기간이 지나면 만료",
  },
  {
    category: "위치·지도 조회",
    data: "허용한 현재 위치, 지도 중심과 조회 범위, 공개 장소 좌표",
    purpose: "주변 흡연구역·핫플·팝업 조회와 지도 위치 맞춤",
    retention: "현재 위치를 이동 이력으로 계속 저장하지 않음. 조회 요청과 운영 로그는 장애·보안 대응에 필요한 기간만 보관",
  },
  {
    category: "장소 제보·리뷰",
    data: "장소 주소·좌표·사진·설명, 리뷰 내용, 작성자 식별자, 작성 시각",
    purpose: "지도 정보 공개, 리뷰 표시, 품질 확인과 운영",
    retention: "게시물 삭제 또는 계정 삭제 시 삭제. 신고 검토 중인 자료는 처리와 분쟁 대응이 끝날 때까지 제한 보관 가능",
  },
  {
    category: "신고·차단",
    data: "신고자 식별자, 대상 장소·리뷰·사용자, 신고 사유와 상세 내용, 처리 상태와 시각",
    purpose: "유해 콘텐츠 검토, 사용자 보호, 반복 악용 방지",
    retention: "신고 처리와 필요한 후속 조치가 끝날 때까지 보관한 뒤 목적이 끝나면 삭제",
  },
  {
    category: "지원 요청",
    data: "회신 이메일, 문의 유형과 내용, 접수번호",
    purpose: "계정 삭제, 개인정보 권리 요청, 이용 문의와 신고 회신",
    retention: "처리 중에는 보관하며 완료·반려 처리일로부터 30일이 지나면 다음 일일 정리 작업에서 삭제(최대 24시간 이내)",
  },
  {
    category: "운영·보안 기록",
    data: "네트워크 주소, 요청 시각과 경로, 오류 정보",
    purpose: "서비스 장애 대응, 보안과 부정 이용 탐지",
    retention: "운영·보안 목적에 필요한 최소 기간 또는 법령상 보존기간이 끝나면 삭제",
  },
] as const

const providers = [
  {
    provider: "Apple, Kakao, Naver, Google",
    role: "사용자가 선택한 소셜 로그인과 계정 연결",
    data: "로그인 요청, 제공자 사용자 식별자, 제공 범위에 포함된 이메일·프로필 정보",
  },
  {
    provider: "Kakao Maps",
    role: "지도 화면, 위치·좌표 표시와 장소 이동",
    data: "지도 조회에 필요한 좌표·조회 범위와 네트워크 정보",
  },
  {
    provider: "운영 서버·데이터베이스·이미지 저장소",
    role: "API 제공, 계정·게시물 저장, 업로드 이미지 보관",
    data: "위 표의 서비스 제공에 필요한 정보. 운영 환경에서 설정된 사업자가 처리할 수 있음",
  },
] as const

export default function PrivacyPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-4xl space-y-9">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">너굴맵 · NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">개인정보 처리방침</h1>
          <p className="text-sm text-muted-foreground">시행일: 2026년 7월 12일</p>
        </header>

        <section className="grid gap-3 rounded-lg border bg-card p-5 text-sm leading-7 sm:grid-cols-[9rem_1fr]">
          <strong>서비스</strong>
          <span>너굴맵(NugulMap)</span>
          <strong>개인정보 처리자</strong>
          <span>너굴맵 운영자</span>
          <strong>공개 문의 채널</strong>
          <a className="font-semibold underline" href="/support">너굴맵 지원 및 콘텐츠 신고</a>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">1. 처리하는 정보, 목적과 보관</h2>
          <p className="text-sm leading-7 text-muted-foreground">
            지도 조회는 로그인하지 않아도 이용할 수 있습니다. 계정, 제보, 리뷰, 신고 기능을 사용할 때만
            해당 기능에 필요한 정보를 처리합니다.
          </p>
          <div className="overflow-x-auto rounded-lg border">
            <table className="min-w-[760px] w-full border-collapse text-left text-sm leading-6">
              <thead className="bg-muted/60">
                <tr>
                  <th className="p-3 font-bold">구분</th>
                  <th className="p-3 font-bold">처리 정보</th>
                  <th className="p-3 font-bold">목적</th>
                  <th className="p-3 font-bold">보관·삭제</th>
                </tr>
              </thead>
              <tbody>
                {dataRows.map((row) => (
                  <tr className="border-t align-top" key={row.category}>
                    <th className="p-3 font-semibold">{row.category}</th>
                    <td className="p-3 text-muted-foreground">{row.data}</td>
                    <td className="p-3 text-muted-foreground">{row.purpose}</td>
                    <td className="p-3 text-muted-foreground">{row.retention}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">2. 외부 제공자와 처리 범위</h2>
          <div className="overflow-x-auto rounded-lg border">
            <table className="min-w-[700px] w-full border-collapse text-left text-sm leading-6">
              <thead className="bg-muted/60">
                <tr>
                  <th className="p-3 font-bold">제공자·처리 영역</th>
                  <th className="p-3 font-bold">역할</th>
                  <th className="p-3 font-bold">관련 정보</th>
                </tr>
              </thead>
              <tbody>
                {providers.map((provider) => (
                  <tr className="border-t align-top" key={provider.provider}>
                    <th className="p-3 font-semibold">{provider.provider}</th>
                    <td className="p-3 text-muted-foreground">{provider.role}</td>
                    <td className="p-3 text-muted-foreground">{provider.data}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-sm leading-7 text-muted-foreground">
            팝업·문화행사 정보는 서울 열린데이터광장, 한국관광공사 TourAPI 또는 주최자·시설의 공식 공지에서
            제목, 장소, 기간과 원문 링크를 가져옵니다. 이 공개 행사 정보는 이용자 계정의 방문 이력과 결합하지 않습니다.
          </p>
          <p className="text-sm leading-7 text-muted-foreground">
            너굴맵은 개인정보를 판매하지 않으며 제3자 광고 추적에 사용하지 않습니다. 법령상 요구, 이용자 동의 또는
            서비스 제공에 필요한 처리 위탁이 아닌 목적으로 개인정보를 제공하지 않습니다.
          </p>
          <p className="text-sm leading-7 text-muted-foreground">
            외부 제공자가 개인정보를 처리할 때에는 너굴맵의 지시와 공개된 처리 목적 범위에서만 처리하도록 하고,
            이 방침과 관련 법령이 요구하는 것과 동일하거나 동등한 수준으로 정보를 보호하는지 확인합니다.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">3. 위치 정보</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>현재 위치는 사용자가 운영체제 권한을 허용한 경우에만 주변 장소와 지도 위치를 보여주는 데 사용합니다.</li>
            <li>권한을 거부해도 검색과 지도 이동으로 공개 장소 정보를 볼 수 있습니다.</li>
            <li>운영체제 설정에서 위치 권한을 언제든 끌 수 있습니다.</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">4. 공개 게시물, 신고와 차단</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>장소 제보, 사진, 설명, 닉네임과 리뷰는 다른 이용자에게 공개될 수 있습니다.</li>
            <li>본인이나 타인의 개인정보, 불법·유해 콘텐츠, 괴롭힘, 사칭, 광고·도배를 게시하면 안 됩니다.</li>
            <li>앱에서 장소·사진·리뷰를 신고하고 리뷰 작성자를 차단할 수 있습니다.</li>
            <li>운영자는 신고를 검토해 콘텐츠 숨김·삭제 또는 추가 확인 조치를 할 수 있습니다.</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">5. 계정 삭제와 이용자 권리</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>앱의 프로필·계정 화면에서 계정 삭제를 직접 요청할 수 있습니다.</li>
            <li><a className="font-semibold text-foreground underline" href="/account-deletion">웹 계정 삭제 페이지</a>에서도 요청할 수 있습니다.</li>
            <li>계정 삭제 시 계정·프로필·연결 정보, 작성 리뷰, 직접 등록한 장소와 이미지가 삭제됩니다.</li>
            <li>소셜 로그인 제공자의 계정 자체는 삭제되지 않습니다. 제공자 계정 관리는 해당 제공자 설정을 이용해야 합니다.</li>
            <li><a className="font-semibold text-foreground underline" href="/connected-accounts">로그인 연결 관리 안내</a>에서 제공자별 연결 해제 경로를 확인할 수 있습니다.</li>
            <li>열람, 정정, 삭제, 처리 정지와 동의 철회 요청은 <a className="font-semibold text-foreground underline" href="/support">지원 페이지</a>에서 접수합니다.</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">6. 보호 조치</h2>
          <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
            <li>공개 운영 환경에서 HTTPS로 통신합니다.</li>
            <li>인증 정보는 iOS Keychain 또는 Android 암호화 저장소에 보관하며, Android 인증 저장 파일은 백업·기기 전송 대상에서 제외합니다.</li>
            <li>운영자 처리 경로는 별도 접근 키와 권한 확인으로 일반 이용자 경로와 분리합니다.</li>
            <li>소셜 로그인 방식이므로 너굴맵이 로그인 비밀번호를 직접 수집하지 않습니다.</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl font-bold">7. 변경과 문의</h2>
          <p className="text-sm leading-7 text-muted-foreground">
            처리 항목, 제공자 또는 보관 기준이 달라지면 시행 전에 이 페이지에서 변경 내용을 알립니다.
            개인정보 문의, 권리 요청과 콘텐츠 신고는 아래 공개 채널에서 접수합니다.
          </p>
          <div className="rounded-lg border bg-muted/40 p-5 text-sm leading-7">
            <a className="font-semibold underline" href="/support">너굴맵 지원 및 콘텐츠 신고</a>
          </div>
        </section>
      </div>
    </main>
  )
}
