import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "서비스 이용약관 | 너굴맵",
  description: "너굴맵 서비스 이용약관과 커뮤니티 운영 기준",
}

const sections = [
  {
    title: "1. 서비스",
    body: [
      "너굴맵(NugulMap)은 공개 흡연구역, 혼잡도 기반 핫플, 성수 팝업 정보를 지도에서 찾을 수 있도록 제공합니다.",
      "지도 조회는 로그인 없이 이용할 수 있으며, 장소 제보·리뷰·신고·차단과 계정 관리는 로그인이 필요합니다.",
    ],
  },
  {
    title: "2. 지도와 외부 정보",
    body: [
      "흡연구역, 혼잡도와 팝업 정보는 공공 데이터, 제휴·공식 출처 또는 이용자 제보를 바탕으로 표시됩니다.",
      "너굴맵은 담배·전자담배를 판매·홍보하거나 흡연을 권장하지 않습니다. 흡연구역 정보는 지정구역 준수와 간접흡연 노출 감소를 돕기 위한 안내입니다.",
      "장소 운영, 행사 일정과 혼잡도는 바뀔 수 있습니다. 앱에 표시된 갱신 시각과 원문 링크를 확인해야 합니다.",
      "너굴맵은 특정 장소의 흡연 가능 여부, 대기 시간, 안전 또는 행사 입장을 보증하지 않습니다. 현장 안내와 관련 법령이 우선합니다.",
    ],
  },
  {
    title: "3. 이용자 게시물",
    body: [
      "장소 제보, 사진, 설명과 리뷰를 등록하기 전에 이 약관과 커뮤니티 운영 기준에 동의해야 합니다.",
      "이용자는 자신이 등록한 내용에 필요한 권리를 보유해야 하며, 사실과 다른 장소 정보나 타인의 권리를 침해하는 자료를 올리면 안 됩니다.",
      "게시물에는 본인이나 타인의 민감한 개인정보를 포함하지 않아야 합니다.",
    ],
  },
  {
    title: "4. 금지 콘텐츠와 행위",
    body: [
      "불법 정보, 성적 콘텐츠, 아동에게 유해한 콘텐츠, 혐오·괴롭힘·위협, 폭력 조장, 사칭을 금지합니다.",
      "개인정보 노출, 스팸·광고·도배, 악성 링크, 저작권·상표권 등 타인의 권리를 침해하는 콘텐츠를 금지합니다.",
      "신고 기능의 반복 악용, 서비스 접근 방해, 자동화된 대량 등록이나 보안 우회 시도를 금지합니다.",
    ],
  },
  {
    title: "5. 신고, 차단과 운영 조치",
    body: [
      "이용자는 앱에서 장소·사진·리뷰를 신고하고 리뷰 작성자를 차단할 수 있습니다.",
      "운영자는 신고를 검토하고 위반이 확인되면 콘텐츠 숨김·삭제 또는 추가 확인 조치를 할 수 있습니다.",
      "위험, 개인정보 노출 또는 법령 위반 우려가 큰 신고는 우선 처리할 수 있습니다.",
      "운영 조치에 관한 문의는 공개 지원 페이지에서 접수할 수 있습니다.",
    ],
  },
  {
    title: "6. 계정과 삭제",
    body: [
      "이용자는 앱의 프로필·계정 화면에서 계정을 삭제할 수 있고, 앱을 이용할 수 없으면 웹 삭제 페이지에서 요청할 수 있습니다.",
      "계정 삭제 범위와 제한 보관 기준은 개인정보 처리방침과 계정 삭제 안내를 따릅니다.",
      "약관을 위반한 게시물은 신고 검토 결과에 따라 숨김 또는 삭제될 수 있습니다.",
    ],
  },
  {
    title: "7. 권한과 서비스 변경",
    body: [
      "위치 권한은 선택 사항이며 거부해도 검색과 지도 이동으로 공개 장소 정보를 볼 수 있습니다.",
      "운영자는 장애, 보안, 법령 또는 데이터 제공자 변경에 대응하기 위해 일부 기능이나 표시 정보를 변경할 수 있습니다.",
      "약관의 중요한 변경은 시행 전에 앱 또는 이 페이지에서 알립니다.",
    ],
  },
] as const

export default function TermsPage() {
  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto max-w-3xl space-y-8">
        <header className="space-y-3">
          <p className="text-sm font-semibold text-muted-foreground">너굴맵 · NugulMap</p>
          <h1 className="text-3xl font-bold tracking-normal">서비스 이용약관</h1>
          <p className="text-sm text-muted-foreground">시행일: 2026년 7월 11일</p>
        </header>

        {sections.map((section) => (
          <section className="space-y-3" key={section.title}>
            <h2 className="text-xl font-bold">{section.title}</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
              {section.body.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </section>
        ))}

        <nav className="flex flex-wrap gap-4 border-t pt-5 text-sm font-semibold">
          <a className="underline" href="/privacy">개인정보 처리방침</a>
          <a className="underline" href="/account-deletion">계정 삭제 안내</a>
          <a className="underline" href="/support">지원 및 콘텐츠 신고</a>
        </nav>
      </div>
    </main>
  )
}
