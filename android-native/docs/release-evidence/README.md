# Android smoking-map validation — 2026-09-07

**출시용 사용 금지 — 2026-09-07 데이터 감사:** 운영 표본에서 알려진 더미 시드 10건이 발견되었습니다. 상세 화면의 종로 69(ID 4)는 그 시드와 주소·좌표·설명이 일치합니다. 이 이미지들은 UI 동작 증거만 보존하며, 실제 이용 가능한 장소나 스토어 제출용 데이터 증거가 아닙니다. 더미 비공개 처리와 장소 검증 후 서명된 출시 후보에서 다시 촬영해야 합니다.

Scope: smoking-zone map, optional current location, public address/region zone search, zone detail and directions, reporting, reviews, account and policy screens. Hotplace/popup/recommendation/letter home activation and related background refresh calls were removed. Existing experimental helper files remain unexposed.

Verified with JDK 21:

- Baseline before edits: 121 unit tests passed.
- Final: 126 unit tests passed, zero failures/skips.
- `lintDebug`, `lintRelease` passed: 27 warnings and 2 hints each, no errors. Existing dependency/update hints remain; no dependency upgrades were added.
- Debug APK assembled and installed on API 36.1 emulator.
- `SmokingHomeTest` instrumentation passed: search, current-location and report actions exist; popup/hotplace/letter controls absent.
- Actual Kakao map and smoking markers rendered against `https://api.nugulmap.com`. Foreground location permissions were ungranted at startup. Explicit recenter opened the system permission dialog; after denial, manual search still returned a visible empty-result state without crash.
- Four current home/detail/menu/settings captures were visually reviewed. The zone-detail capture includes the new directions button. The follow-up isolated release smoke confirmed that this button opens Google Maps with the selected coordinates; actual travel was not tested.
- Asset build + verifier pass for the exact four screenshots, icon and feature graphic. Historical screenshots are excluded.
- Three Python permission-readiness regression tests passed, including rejection of background location and foreground location without a runtime gate.

Release blockers: `bundleRelease` fails because `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` are absent. No upload keys were generated. Readiness result: 13 PASS, 2 FAIL (signing/AAB), 4 MANUAL. The isolated release APK/AAB passed 16KB layout checks and the APK ran on a 16KB emulator. Play signing hash, real provider OAuth, signed Play installation and verification of that final shipping artifact remain external release gates. Debug screenshots are review candidates and must be recaptured from the signed Play candidate before submission.

## 추가 로컬 검증

업로드용 서명을 만들지 않고 기존 debug 인증서로 별도 임시 복사본의 release APK/AAB를 빌드했습니다. 실제 release manifest·ELF/ZIP 16KB 정렬·AAB 정렬 설정, 격리 에뮬레이터의 현재 위치 이동과 외부 지도 연결을 확인했습니다. 세부 근거와 한계는 `isolated-release-verification.md`에 기록합니다. 이는 Play 서명·16KB 실기기·데이터 검증 조건을 대체하지 않습니다.


2026-09-08 추가: 공식16KB 시스템 이미지를 검증해 격리 에뮬레이터에서 `PAGE_SIZE=16384`와 release 변형 APK의 카카오 지도·흡연구역 상세 동작을 확인했습니다. `runtime-16k.json` 및 `isolated-release-verification.md` 참조. 이전의 16KB 런타임 미검증 항목은 이 격리 실행으로 해소됐으며, Play 서명·현장 데이터 검증은 여전히 별도 조건입니다.
