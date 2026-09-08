# 흡연구역 전용 출시 검증 결과

작업 시작: 2026-09-07. 후속 검증: 2026-09-08 KST. **판정: 로컬 출시 후보 정리·검증 완료, 공개 출시는 HOLD.**

앱의 기능 정리만으로 출시 완료라고 판단하지 않았다. 운영 데이터·웹 배포·지원 연락처·배포 서명에서 차단 사항을 실제로 확인했다. 초기 준비 단계에서는 운영 배포, DB 변경, 스토어 제출, 커밋·푸시를 수행하지 않았다. 9월 8일 사용자가 출시를 명시적으로 승인해 아래 실제 연결 확인을 진행했다.

## 2026-09-08 출시 실행 중 확인한 차단 사항

- Google Play Console은 로그인되어 있으나 계정 확인 미완료로 첫 앱 생성이 잠겨 있다. 실제 Android 기기 확인과 연락처 인증이 필요하다. 앱을 만들거나 업로드하지 못했다.
- Xcode Apple Accounts는 로그인되지 않은 상태다. 실제 서명 archive도 개발 팀 미설정으로 실패했다. 기존 캐시의 Personal Team과 개발 인증서는 App Store 배포 권한을 입증하지 않는다.
- GitHub 운영 환경 `Production`에 `MODERATION_CONTACT_EMAIL=nugulmap@gmail.com`을 등록했다. API 배포용 `SSH_PRIVATE_KEY`는 저장소/운영 환경에 없으며, 이전 실제 배포 역시 같은 사유로 실패했다.
- 연결된 Vercel 팀에는 프로젝트 목록이 비어 있고 저장된 프로젝트 ID 조회는 404다. 로컬 CLI 인증은 403으로 거부됐다. 기존 서비스와 다른 새 프로젝트를 임의 생성하지 않았다.
- 흡연구역 전용 운영 Compose가 사용하지 않는 `SEOUL_CITYDATA_API_KEY`를 필수로 요구하던 조건을 제거했다. 실제 빈 키 설정 해석과 탐색 비활성 상태를 포함한 보안 테스트 18개가 통과했다.
- 사용자 작업 디렉터리를 보존하고 별도 `codex/smoking-only-launch-20260908` 브랜치에서 출시 소스의 독립 검증을 진행했다. 스토어 계정 인증·호스팅 접근이 복구되기 전까지 운영 DB나 공개 서비스를 변경하지 않는다.

## 변경 결과

- Android·iOS·웹·Expo 홈에서 핫플·팝업·추천·방명록 진입과 관련 자동 요청을 제거했다. 지도·현재 위치·장소 검색·상세·길찾기·제보·신고와 필요한 계정·정책 경로를 유지했다.
- Android 주소 검색을 공개 흡연구역 검색 API에 연결하고, 상세에서 빠져 있던 길찾기를 추가했다.
- iOS 지도 이동 후 미갱신, 접근성 마커 목록 고정, 실제 마커 터치 후 상세 sheet가 열리지 않던 문제를 실제 UI 테스트로 찾아 수정했다.
- 웹은 연속 검색 응답 순서, 늦은 최초 위치의 검색 덮어쓰기, SDK 준비 전 위치 이동, 실패 재시도·성공한 빈 결과, 위치 요청 제한시간·접근성 이름과 상세 dialog 이름을 보완했다. 초기 지도를 조금 넓혀 주변 마커를 볼 수 있게 했다.
- 서버의 부가 기능 controller는 명시적 opt-in 없이는 등록되지 않는다. 운영 Compose는 discovery와 핫플·이벤트 자동 수집을 끄며, 이전 collector는 기본 stack에서 제외한다. 기존 데이터 삭제·보관기간 처리와 스키마는 보존했다.
- 배포 검사는 흡연구역 조회·검색·상세·리뷰, 공개 정책·지원, 제외 API 비활성화와 알려진 테스트 데이터 노출을 검사한다.
- 프런트 기존 의존성을 호환 패치로 갱신해 보안 감사 4건을 0건으로 줄였다. 새 직접 의존성이나 메이저 업그레이드는 없다.
- 실제 MySQL 검증에서 운영 `validate`가 MySQL 설정에 덮어써지는 결함과 10개 TEXT 컬럼의 CLOB 매핑 불일치를 찾아 수정했다. MySQL 자동 예제 삽입을 끄고, 잘못된 한글 HTTP 헤더를 제거했다. 스키마나 기존 데이터는 변경하지 않았다.

## 검증 증거

| 대상 | 최종 결과 | 한계 |
| --- | --- | --- |
| Android | 126 단위 테스트, 홈 instrumentation, debug/release lint, 격리 테스트 서명 release APK/AAB, ELF·ZIP·AAB 16KB 정렬 검사와 검사기 테스트 3개 통과. 실제 16KB 에뮬레이터에서 release APK 설치·Kakao 지도·마커·상세 실행 확인. 모의 GPS로 성수 이동·실제 Google Maps 실행도 확인 | lint 경고 27개·hint 2개. Debug 인증서로만 서명했다. Play 제출 서명·실제 OAuth·실기기·Play 설치는 미완료 |
| iOS | 계약 13개, iPhone UI 9개, iPad UI 9개·가로 화면, Release simulator analyze, 서명 없는 arm64 기기용 archive 통과 | 계정·장소는 명시된 로컬 fixture, 실제 Kakao 타일. 실제 provider OAuth·GPS·서명/provisioning·TestFlight는 미완료 |
| 웹 | 테스트 19개, TypeScript·보안 헤더·production build, production/full 의존성 감사 통과 | 등록 origin으로 로컬 후보를 대체한 격리 브라우저 검증이며 운영 배포 증거가 아님 |
| Expo | 7 suites / 40 tests, TypeScript, strict production configuration 검사 통과 | 이번 스토어 대상은 독립 네이티브 앱. 아래 별도 패키지 보안 제한 참조 |
| API | Java 21 테스트 301개, 실패·건너뜀 0개, `bootJar` 성공. 실제 MySQL 8 격리 통합 검사 31개 통과 | 운영 DB·실제 공급자 OAuth·메일 회신은 별도 |
| 공통 검사 | Python 94개 + Compose 보안 테스트 17개, 실제 Compose 렌더 검사, diff 공백 검사 통과 | 계정·현장·운영 반영 완료를 뜻하지 않음 |
| 네이티브 준비 검사 | PASS 47 / MANUAL 9 / FAIL 2 | FAIL은 Android 서명 설정 부재와 AAB 부재 |
| 공개 운영 검사 | PASS 7 / FAIL 10 | 아래 운영 차단 사항으로 의도대로 실패 |

- [Android 화면·테스트 기록](../android-native/docs/release-evidence/README.md)
- [Android 격리 release·16KB 정렬·모의 GPS·외부 길찾기](../android-native/docs/release-evidence/isolated-release-verification.md)
- [iOS 실제 드래그·마커 터치·길찾기 실행 기록](../ios-native/review-evidence/smoking-release-20260907/README.md)
- [웹 등록 origin 후보 실행 기록](../frontend/docs/release-evidence/2026-09-07-registered-origin-smoke.md)
- [공개 운영 검사 원문](./release-evidence/2026-09-07-public-runtime.txt)
- [MySQL 8 실제 운영 프로필 통합 검사 31개](./release-evidence/2026-09-07-mysql-release-stack.txt)
- [1,061행 원본 대응·품질 감사와 검토 목록](./data-audit/README.md)

MySQL 검증에 사용한 `backend/api-server/build/libs/neogul-map-0.0.1-SNAPSHOT.jar`의 SHA-256은
`1143eebe08a2d03229b5a2d11c6475d02353a935f8e1b0bc302744e13872d45b`다.
핵심 후속 수정은 `application-mysql.yml`, `application-prod.yml`, 10개 TEXT 필드의 엔티티 매핑,
`UserController.java`, `scripts/verify-local-release-stack.py`와 배포 workflow에 있다.
상세 회귀 검증은 `DatabaseProfileSafetyTest`와 `MySqlTextMappingTest`가 담당한다.

## 공개 출시 차단 사항과 준비한 조치

1. **운영 테스트 데이터 10건.** 공개 표본 200건에서 ID 1–10의 주소·좌표·설명이 `data.sql`의 명시된 더미 시드와 모두 일치했다. 나머지 190건은 `final_zones_all.csv`의 주소·좌표와 일치했지만, 현장 이용 가능 여부는 증명되지 않았다.
   - [비공개 처리 SQL](../backend/api-server/src/main/resources/db/manual/20260907_quarantine_demo_zones.sql)을 준비했다. 정확한 주소·좌표·설명에 일치하는 공개 행만 PENDING으로 바꾸고 행·리뷰를 삭제하지 않는다. H2/JDBC에서 10건 격리, 수정된 자료 보존, 총 17행 유지와 반복 실행 안정성을 검증했다.
   - 실제 MySQL 8에서도 전체 스키마와 순서대로 정렬한 마이그레이션의 반복 실행, 10건 격리·행 보존을 확인했다. 격리 DB에서 공개 조회, 문의 접수·운영자 처리, 인증·타 계정 접근 거부, 제보·승인·공개, 리뷰·신고·차단, 계정/작성 콘텐츠 삭제와 삭제된 토큰 거부를 확인했다. 필수 컬럼을 제거한 부정 테스트는 운영 서버가 기동을 거부함을 확인했다. 이 검사는 배포 CI에 포함했다.
   - 배포 workflow는 백업·스키마·기존 마이그레이션 이후 실행하고 공개 더미 잔여 0건을 요구한다. **아직 운영에 실행하지 않았다.**
   - 원본 공공 CSV는 2019–2025 기준 자료가 섞여 있고 최종 CSV 1,061행과 Zone 모델에 개별 출처 URL·현장 확인 시각이 없다. 출시 지역 자료의 출처·최신성 재확인이 필요하다. 자료 전체를 현재 검증 완료라고 표시하지 않는다.
   - 후속 전수 감사에서 정확한 원본 주소 대응 1,058행, 무효 주소 3행, 지역 누락 849행, 같은 주소 원본에서 최종 좌표 근거를 확보하지 못한 행 900개를 기록했다. 원본 실내 값과 충돌하는 검토 신호 227행, 좌표 차이 7행을 분리했다. 보수적인 좌표 근거 후보 151행도 현장 검증 완료 자료가 아니며 운영에 반영하지 않았다.
2. **정책/지원 URL 4개가 404.** `/privacy`, `/account-deletion`, `/connected-accounts`, `/support`. `/terms`는 200이다. 다섯 경로 모두 로컬 production 산출물·HTTP 응답을 확인했으며, 기존 Vercel 경로로 새 후보를 배포하고 공개 URL을 다시 검사해야 한다.
3. **지원 연락처 운영 반영 필요.** 2026-09-08 사용자가 운영자명 `강승진`, 지원 이메일 `nugulmap@gmail.com`을 제공했다. 공개 정책·지원 페이지와 환경 설정 예시에 반영했다. 마지막 운영 조회의 `supportEmailConfigured=false`는 배포 후 다시 확인해야 한다. 실제 메일 수신·회신은 아직 검증하지 않았다.
4. **운영 서버는 아직 이전 범위.** insights status는 200, 추천·방명록은 401이어서 비활성화가 증명되지 않는다. 새 서버를 배포한 뒤 해당 공개 경로가 404/410이어야 한다.
5. **서명과 스토어 계정/기기.** 2026-09-08 사용자가 Google Play·Apple 개발자 계정을 모두 보유한다고 확인했다. 배포 국가는 대한민국만이다. Android upload-key 설정과 제출용 AAB, Apple Team/provisioning 및 signed archive/TestFlight는 아직 확인되지 않았다. Debug 인증서로 분리한 Android release와 서명 없는 iOS 기기 archive는 빌드했다. 실제 provider OAuth·GPS·Play 설치본, 스토어 개인정보 입력이 남는다. 계정·신고 기능의 로컬 검증은 실제 공급자 인증·운영 검증을 대신하지 않는다.
6. **스크린샷 제출 보류.** Android·웹의 운영 API 캡처에는 더미가 포함되고 iOS는 명시된 fixture다. 모두 UI 검증용이며 실제 장소 증거·최종 스토어 제출 이미지로 쓰지 않는다. 데이터 정리 후 서명 후보에서 다시 캡처해야 한다.

## 별도 Expo 패키지 제한

`mobile/`의 기존 Expo 54 의존성 감사는 low 1 / moderate 8 / high 19 / critical 2를 보고했다. 대부분 Expo/Metro CLI 도구의 전이 의존성이며 npm의 일부 해결안은 Expo 57 메이저 변경이다. 이번 독립 Kotlin/Swift 스토어 산출물에는 Expo 패키지가 포함되지 않는다. 이 작업에서 임의로 Expo SDK 메이저 마이그레이션을 하지 않았으며, Expo를 별도 출시 후보로 승격하려면 의존성 정리와 재검증이 선행되어야 한다.

## 운영 반영 순서

실제 지원 연락처·계정 설정 확인 → 검증된 DB 백업 → 준비된 스키마/테스트 데이터 비공개 마이그레이션과 API 배포 → 기존 Vercel 프런트 배포 → 공개 runtime 검사 → 출처/현장 자료 확인 → 서명 후보·실기기 검증·새 스크린샷 → 스토어 입력/검토 순서다. 현재 차단 사항이 닫히기 전에는 공개 출시하지 않는다.

스토어 한국어/영어 소개, 심사 메모, 개인정보 인벤토리와 공식 요구사항 링크는 [스토어 준비 문서](./store-privacy-readiness.md)에 정리했다.
