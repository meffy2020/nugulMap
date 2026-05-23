Android/iOS native map parity and verification task.

Split into Android and iOS lanes.

Android lane:
- Run and verify on Android emulator while working.
- Confirm Kakao map markers render and basic selection behavior works.
- Remove visible explanatory/diagnostic copy on map/menu/settings surfaces such as "지도 위에는 ...", "지도, 검색, 프로필...", "키가 없으면 ...", and similar “뭐 어쩌구로 됩니다” style guide text.
- Keep Android already using KakaoMap; no backend/API contract changes.

iOS lane:
- Convert iOS map surface from Apple MapKit to KakaoMap if feasible in current native project.
- Make markers and existing map selection/report/search-adjacent behavior work normally after conversion.
- Replace bottom modal/sheet selection presentation with an on-map bottom card style.
- Keep UI close to Android/iOS glass card launch quality; no unrelated feature expansion.

Verification and completion:
- Build Android and iOS.
- Launch Android emulator and iOS simulator, capture screenshots/evidence.
- Record blockers honestly if Kakao iOS SDK requires credential/package setup not present locally.
- Commit and push verified changes.
