Fix Android launch screen release polish regression while preserving existing app contracts.

Scope:
- Remove launch-screen visible copy "지도 미리보기" and "N개 구역 표시 중" from Android no-key fallback.
- Prevent raw API/DNS/exception messages from appearing on the launch map surface or bottom toast.
- Make Android no-Kakao-key fallback a quiet iOS-like fullscreen map shell with glass/rounded controls, not a diagnostic card.
- Fix fallback marker count so it does not show fake markers inconsistent with actual loaded zones; if zones are zero, show a quiet empty map shell.
- If a real local KAKAO_NATIVE_APP_KEY is available, validate real Kakao map path; otherwise record credential-gated verification and validate no-key fallback.

Constraints:
- Android UI only unless verification exposes shared defects.
- No backend/API contract changes.
- No new feature expansion.
- Keep changes small, reversible, and release-oriented.
- Verify with Android build and emulator/screenshot when possible.
- Commit and push after verified.
