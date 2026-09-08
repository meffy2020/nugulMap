# 흡연구역 출시 검증 — 2026-09-07

현재 화면은 흡연구역, 장소·주소 검색, 현재 위치, 제보, 계정, 설정만 제공한다.
시작·로그인·위치 이동·검색에서 인사이트/추천/편지 API를 자동 호출하지 않는다.

## 최종 결과

계약 테스트 13개 PASS. 동일 최종 코드의 iPhone UI 테스트 9개 모두 PASS. Release 시뮬레이터 analyze 성공. 결과는 `contracts.log`와 `ui-verification.log`에 기록했다. 전체 XCTest bundle은 `/tmp/nugul-smoking-ios-verified.xcresult`에 있다.

## 실제 시뮬레이터 증거

- `home.png`: 첫 화면, 시청 좌표의 검증용 흡연구역 마커.
- `after-pan.png`: 실제 Kakao 지도 드래그 후 같은 마커. XCTest가 드래그 전후 fixture 서버의 `/api/zones/bounds` 요청 횟수 증가를 검증했다.
- `zone-detail.png`: 화면의 마커를 직접 터치한 뒤 열린 상세, 장소 신고, 길찾기.

화면은 iPhone 17 Pro / iOS 26.5 시뮬레이터에서 촬영했다. Kakao 지도 타일은 실제 SDK 렌더링이며 흡연구역·계정·리뷰 응답은 로컬 fixture다. `시뮬레이터 테스트 위치`는 검증용이며 운영 데이터가 아니다.
길찾기 버튼을 터치해 Apple Maps가 foreground로 전환되는 것까지 XCTest로 확인했다. 외부 지도에서 실제 경로 계산·내비게이션은 이 검증의 범위가 아니다.

## 검증 중 수정한 실제 문제

- 지도 이동 종료 콜백이 흡연구역을 다시 조회하지 않던 문제: 현재 표시 영역으로 조회하도록 수정.
- 접근성 지도 목록이 초기 빈 응답 상태에 남던 문제: 목록이 모델을 직접 관찰하도록 수정.
- 실제 마커 터치 시 native 미리보기만 열리고 상세 sheet가 뜨지 않던 문제: 독립적으로 모델을 관찰하는 상세 presenter로 수정.

## 접근성 검증 범위

실제 컨트롤의 대비, 터치 영역, 레이블, 텍스트 등은 XCTest로 확인한다. 렌더링되지 않는 SwiftUI 접근성 대체 목록의 대비 오탐만 식별자로 한정해 제외한다. SDK 래스터 지도 글자를 AX 요소에 연결할 수 없는 OCR 항목은 가로 지도와 부분 sheet 감사에서 제외하고 화면을 직접 확인했다.

UI 테스트는 시뮬레이터의 기본 ad-hoc 서명으로 실행한다. `CODE_SIGNING_ALLOWED=NO`를 UI 테스트에 넣으면 Keychain 로그인 fixture 저장이 실패하므로 사용하지 않는다. 위치 거부 테스트는 테스트 시작 전에 해당 권한을 초기화한다.

실기기 GPS, 실제 OAuth 제공자 로그인, 운영 데이터 정확성, 배포용 서명 archive, TestFlight 및 App Store 제출은 별도 검증이 필요하다. 이번 작업은 해당 외부 검증 완료를 주장하지 않는다.


## 추가 검증: 기기용 archive 및 iPad

- `device-archive.json` / `device-archive.log`: 실제 `generic/platform=iOS` Release archive 검증 성공. Mach-O `platform IOS`, arm64, 최소 iOS 17.0. 시뮬레이터 산출물이 아니다.
- archive 경로: `/tmp/nugul-smoking-device-archive/NeogulMapNative.xcarchive`.
- `codesign` 결과는 **서명 없음**이며 provisioning profile도 없다. 배포용 서명 archive나 TestFlight 업로드가 아니다.
- archive 내부 Info.plist는 운영 API `https://api.nugulmap.com`, 설정된 Kakao 키, 흡연구역 위치 목적 문구를 포함한다. 키 값은 기록하지 않았다.
- archive 내부 privacy manifest는 추적 없음, 여섯 기능용 데이터 유형, ProductInteraction 제외를 확인했다. Release 바이너리에는 UI-test 환경변수 hook 문자열이 없다.
- iPad Pro 13-inch (M5) / iOS 26.5 전체 UI 테스트 **9개 PASS, 0 failures** (116.3초). `ipad-ui-verification.log` 참조. XCTest bundle: `/tmp/nugul-smoking-ipad.xcresult`.
- `ipad-home.png`, `ipad-landscape.png`, `ipad-zone-detail.png`: 실제 타일/로컬 fixture로 세로·가로 및 상세 화면을 확인했다.
- iPad 가로 스크린샷의 앱 영역 cropping 오류를 whole-screen capture로 수정했고 가로 검사를 추가 재실행해 PASS했다. 앱 동작 수정은 추가로 필요하지 않았다.

상기 iPad 데이터도 로컬 fixture이며 실제 흡연구역의 운영 검증 증거가 아니다. 배포 서명, 실제 기기와 OAuth, 운영 정책·지원 연락처 및 운영 데이터 검증은 여전히 별도다.
