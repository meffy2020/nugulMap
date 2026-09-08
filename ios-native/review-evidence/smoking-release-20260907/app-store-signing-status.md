# App Store 서명 확인 — 2026-09-08

실제 서명을 켠 기기용 Release archive를 실행했으나 `Signing for "NeogulMapNative" requires a development team`으로 실패했다(exit 65). 로그는 `/tmp/nugul-smoking-signing-attempt.log`다. 업로드 가능한 IPA는 생성되지 않았다.

## 현재 확인된 상태

- Xcode > Settings > Apple Accounts의 실제 화면에는 `Sign-in to an Apple Account`가 표시된다. 로그인된 계정 목록은 없다.
- 로컬 keychain에는 Apple Development 인증서 두 개가 있으며 Apple Distribution 인증서는 없다. 개인키나 비밀번호는 조회하지 않았다.
- Xcode에 남은 팀 캐시는 Personal Team 두 개뿐이다. 캐시는 현재 유료 멤버십의 증거가 아니다.
- 설치된 provisioning profile과 App Store Connect `.p8` API 키는 확인되지 않았다.
- 프로젝트는 자동 서명, `com.nugulmap.native`, 버전 `1.0 (1)`, 개발 팀 미설정이다.
- 앞선 unsigned arm64 기기 archive 및 iPhone/iPad 검증은 성공했지만 배포 서명·실기기·TestFlight 검증을 대신하지 않는다.

## 로그인 후 이어서 실행할 작업

1. Xcode > Settings > Apple Accounts에서 Apple Developer Program에 가입된 소유 계정으로 로그인한다. 현재 계정의 유료 멤버십 유무는 확인되지 않았다. Personal Team만 표시되면 App Store 배포에 사용할 수 없다. 멤버십 구매는 수행하지 않았다.
2. 표시된 유료 팀의 정확한 Team ID를 확인하고 `NUGUL_DEVELOPMENT_TEAM`으로 설정한다. 추측한 팀 ID나 법적 명칭을 넣지 않는다. Bundle ID와 Sign in with Apple capability 등록 상태를 확인한다.
3. 운영 API·정책 URL·검증된 흡연구역 데이터가 준비됐다는 출시 담당자의 확인 후 아래 명령으로 현재 소스를 archive한다. `CODE_SIGNING_ALLOWED=NO`는 넣지 않는다.

```sh
# 저장소 루트에서, 실제 확인된 유료 Team ID를 환경변수에 설정한 뒤 실행
: "${NUGUL_DEVELOPMENT_TEAM:?Verified Apple Developer Program Team ID required}"
xcodebuild -project ios-native/NeogulMapNative.xcodeproj \
  -scheme NeogulMapNative -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath /tmp/nugulmap-app-store/NeogulMapNative.xcarchive \
  -allowProvisioningUpdates \
  NUGUL_DEVELOPMENT_TEAM="$NUGUL_DEVELOPMENT_TEAM" archive
```

4. Xcode Organizer에서 App Store Connect의 동일 Bundle ID 앱을 선택해 Validate App, Distribute App을 실행한다. 기존 앱·빌드가 있으면 중복 버전/build 번호를 먼저 확인한다. TestFlight에서 실제 위치, Apple 로그인, 계정 삭제, 마커·상세·길찾기를 검증한 뒤 심사에 제출한다.
5. 사용자 확정값은 운영자 **강승진**, 지원 이메일 **supportnugulmap@gmail.com**, 배포 국가 **대한민국만**이다. App Store Connect의 법적 판매자 이름은 가입 계정의 검증된 법적 정보를 따른다.

검증용 fixture 스크린샷은 실제 운영 장소의 증거가 아니므로 현재 자료를 그대로 App Store 공개 스크린샷으로 제출하지 않는다. App Privacy 응답도 archive의 privacy manifest만 복사하지 않고 실제 앱·서버·SDK 수집 동작과 맞춘다.

Apple 공식 근거: [멤버십 비교](https://developer.apple.com/support/compare-memberships/), [개발자 계정 안내](https://developer.apple.com/help/account/basics/about-your-developer-account), [App Store Connect 배포](https://help.apple.com/xcode/mac/current/en.lproj/dev31de635e5.html).
