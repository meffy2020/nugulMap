# PRD: Mobile iOS-Parity Glass Map Release Buildout

## Status
RALPLAN approved for execution after local architect/critic review. External subagent review was stopped after timeout; this plan is the execution source of truth.


## Consensus Review Result

### Architect verdict: APPROVE
- Scope is coherent: iOS remains the visual canon, Android mirrors the map-first hierarchy, and no API/store scope is added.
- The riskiest architectural boundary is Android Kakao credential availability; the plan treats the no-key state as a supported visual fallback while keeping the key-present map path compile-safe.
- Execution should avoid new dependencies; use existing SwiftUI material and Compose translucent surfaces/shadows.

### Critic verdict: APPROVE WITH GUARDS
- Do not claim launch-quality completion from build success alone; final proof must include screenshots, API-load evidence, clean source diff, commit, and push.
- Do not commit generated emulator/simulator artifacts unless explicitly requested; record their file paths in the final report.
- Verify that Android top chrome does not use awkward layout hacks such as a horizontal divider as a vertical separator if it causes visual defects.
- Preserve iOS marker synchronization from commit `27a488a`; styling must not reintroduce the marker-empty regression.

## Requirements Summary

NugulMap mobile native apps must present a polished map-first public-launch UI. iOS is the canonical visual target; Android must match the iOS fullscreen map hierarchy as closely as Compose/Kakao Maps allow.

### User-approved scope
- iOS: fullscreen map, rounded/glass search chrome, compact floating profile control, compact glass-style bottom card/modal treatment.
- Android: iOS-parity fullscreen map shell, floating rounded/glass search/profile chrome, compact glass selected-zone card, account/report/review flows moved into modal bottom sheets.
- Android Kakao key absent: show a release-quality fallback map preview with loaded zone count and no test-page feel.
- Android Kakao key present: real Kakao map uses the same overlay chrome and selected-zone behavior.

### Explicit non-goals
- No backend/API contract changes.
- No feature expansion for report/review; only visual placement/treatment of existing capabilities.
- No App Store / Play Console submission.

## RALPLAN-DR Summary

### Principles
1. **Map-first parity:** the first impression on both platforms must be a full-screen map shell, not a test dashboard.
2. **Existing contracts stay stable:** API, OAuth, report, and review contracts must remain unchanged.
3. **Credential-aware release readiness:** Android must look polished without local Kakao credentials and still support real Kakao rendering when credentials are present.
4. **Evidence before completion:** screenshots, build output, API logs, and pushed git state are required before calling the work done.
5. **Small reversible diff:** prefer Compose/SwiftUI layout changes and existing components over new dependencies or broad feature work.

### Decision Drivers
1. User trust: prior “done” claim was undermined by Android MVP UI; visible parity evidence is mandatory.
2. Launch presentation quality: public screenshots should not expose MVP/test wording or rough layout.
3. Verification constraints: local Android lacks `KAKAO_NATIVE_APP_KEY`, so the no-key path must be an accepted first-class visual state.

### Viable Options

#### Option A — iOS-canonical parity pass (recommended)
- Approach: Treat current iOS layout as canonical, refine iOS glass/card details, and make Android mirror the hierarchy using Compose and KakaoZoneMap fallback/real map paths.
- Pros: Directly matches user intent; keeps backend unchanged; gives concrete visual evidence on both platforms.
- Cons: Android may still not be pixel-identical because Kakao Maps and Compose differ from MapKit/SwiftUI materials.

#### Option B — Android-only rescue plus minimal iOS check
- Approach: Leave iOS mostly as-is and focus almost entirely on Android MVP-to-release conversion.
- Pros: Fastest way to fix the biggest visible gap.
- Cons: Risks missing user-requested iOS glass refinements and weakens the “iOS as canonical” decision.

#### Option C — Platform-native redesign
- Approach: Build separate native design systems for iOS and Android with similar brand tone but different layouts.
- Pros: Could feel more native on each platform.
- Cons: Conflicts with selected `ios-parity` answer; higher scope; harder to verify quickly.

## ADR

### Decision
Use **Option A: iOS-canonical parity pass**.

### Drivers
- User explicitly selected iOS parity in deep-interview.
- Android MVP/test screen is the primary launch-quality blocker.
- Credential limits require polished no-key fallback and key-present real-map compatibility.

### Alternatives considered
- Android-only rescue: rejected because user also requested iOS fullscreen/glass polish and parity.
- Platform-native divergence: rejected because it contradicts iOS-as-canonical scope and increases design ambiguity.

### Why chosen
It is the smallest plan that satisfies the clarified outcome: visible iOS-grade map-first UI on both platforms, no API changes, and hard evidence through simulator/emulator screenshots plus builds/logs.

### Consequences
- Android fallback map preview becomes a supported visual state, not a debug-only placeholder.
- Some exact glass effects may be approximated on Android with translucent surfaces/shadows unless new dependencies are later approved.
- Real Kakao map validation remains partially credential-gated if no key is provided locally.

### Follow-ups
- Later: supply real `KAKAO_NATIVE_APP_KEY` and run a second Android visual pass on real Kakao map labels/markers.
- Later: Play/App Store listing screenshots and data-safety/store submission remain separate work.

## Acceptance Criteria

### iOS
- [ ] `ios-native/NeogulMapNative/Views/ZoneMapView.swift` launches to a fullscreen map (`ZStack` with `mapLayer` behind chrome) and does not regress marker rendering.
- [ ] Search/profile chrome appears rounded and material/glass-like over the map.
- [ ] Selected-zone detail and report/profile/login sheets/cards feel compact and glass/material; no heavy plain full-page replacement for normal map browsing.
- [ ] iOS simulator screenshot is captured after API zones load and markers are visible.

### Android
- [ ] `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt` is fullscreen map-first, not `LazyColumn` dashboard-first.
- [ ] Top search/profile chrome overlays the map with rounded translucent surfaces.
- [ ] Selected zone appears in a compact bottom card; account/report/reviews are behind bottom sheets.
- [ ] `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt` supports both no-key polished fallback and key-present real map using the same parent shell.
- [ ] No debug/test strings like plain `MAP` or “MVP” are visible in the primary Android launch screen.
- [ ] Android emulator screenshot proves no-key fallback is polished and API zones load.

### Cross-platform verification
- [ ] iOS build/run succeeds.
- [ ] Android `:app:assembleDebug` succeeds and debug APK installs/runs on emulator.
- [ ] API zone load evidence exists for both platforms where possible; Android log must show `GET /api/zones/bounds` 200 or UI loaded count.
- [ ] Generated screenshots/log paths are recorded in final report.
- [ ] Generated screenshots/logs under `tmp/` are not committed unless explicitly requested.
- [ ] Changes are committed with Lore trailers and pushed to `origin/main`.

## Implementation Steps

1. **Audit current mobile UI state**
   - Inspect `ios-native/NeogulMapNative/Views/ZoneMapView.swift` lines around `ZoneMapView.body`, `topHeader`, `bottomControls`, `NugulMapKitView`.
   - Inspect `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt` and `KakaoZoneMap.kt` current uncommitted fullscreen/fallback changes.
   - Confirm no backend/API files need edits.

2. **Finalize Android fullscreen parity shell**
   - In `MapScreen.kt`, keep `Box(Modifier.fillMaxSize())` as the root visual shell.
   - Ensure `KakaoZoneMap(... modifier = Modifier.fillMaxSize())` is the background.
   - Ensure `TopMapChrome` and `BottomMapChrome` align to top/bottom using Box alignment, not layout hacks that expand the bottom chrome unexpectedly.
   - Replace or visually verify any separator/divider hack that renders incorrectly in the floating chrome.
   - Remove dashboard-style permanent panels from the launch screen; keep account/report/reviews in `ModalBottomSheet`.

3. **Polish Android no-key fallback map**
   - In `KakaoZoneMap.kt`, replace plain `MAP` fallback with a subtle map-preview canvas and marker dots.
   - Remove red error framing for missing Kakao key; missing key is a known credential state, not a user-facing failure.
   - Keep `운영 API에서 N개 구역 로드` visible enough for verification but not as a debug headline.
   - Treat missing `KAKAO_NATIVE_APP_KEY` as a credential state; do not show a red failure panel on the public launch screen.

4. **Polish iOS glass/card treatment**
   - In `ZoneMapView.swift`, review `topHeader`, `WebStyleSearchBar`, `ProfileAvatar`, `bottomControls`, and `ZoneDetailView` sheet presentation.
   - Prefer `.ultraThinMaterial`, opacity, rounded rectangles, and compact detents. Keep marker sync code from commit `27a488a` intact.
   - Do not change API/search/report/review contracts.

5. **Run builds and visual smoke tests**
   - iOS: run XcodeBuildMCP `build_run_sim` with existing defaults or re-establish defaults if needed; capture screenshot.
   - Android: run `./gradlew :app:assembleDebug`, install debug APK using SDK `adb`, launch package `com.nugulmap.nativeapp`, capture screenshot.
   - Capture log evidence: Android logcat filtered for `/api/zones/bounds` 200; iOS runtime log or visible markers/screenshot as API/marker evidence.

6. **Finalize, commit, push**
   - Remove generated artifacts from git status except intentional source/spec files; prefer final report paths over committing large screenshots/logs.
   - Commit changed source/spec files with Lore protocol.
   - Push `main` to `origin/main`.

## Risks and Mitigations

- **Risk: Generated artifacts accidentally committed.**
  - Mitigation: keep screenshots/logs under `tmp/` for evidence reporting only, then verify `git status --short` before commit.

- **Risk: Android real Kakao map cannot be visually verified without key.**
  - Mitigation: no-key fallback is a first-class accepted criterion; keep key-present code path structurally unchanged and build-compiled.
- **Risk: Android bottom chrome can accidentally fill the whole screen.**
  - Mitigation: require Box alignment and screenshot verification.
- **Risk: iOS marker sync regresses while styling.**
  - Mitigation: preserve `model.$zones` coordinator binding and verify markers in screenshot.
- **Risk: disk space blocks builds/questions.**
  - Mitigation: clean generated build directories before heavy verification and do not commit generated artifacts.

## Verification Plan

1. `cd android-native && ./gradlew :app:assembleDebug --console=plain`
2. `adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell monkey -p com.nugulmap.nativeapp -c android.intent.category.LAUNCHER 1`
4. `adb exec-out screencap -p > tmp/android-qa/nugulmap-android-final.png`
5. `adb logcat -d | rg 'zones/bounds|okhttp|FATAL|Exception'`
6. XcodeBuildMCP `build_run_sim` for iOS and screenshot capture.
7. `git status --short --branch`, commit, push, then verify `main...origin/main` clean.

## Available-Agent-Types Roster

- `explore`: quick repo facts and file/symbol mapping.
- `executor`: bounded implementation/refactor changes.
- `designer`: UI/UX layout critique and visual parity guidance.
- `verifier`: completion evidence, screenshots/log/build adequacy.
- `critic`: plan/design challenge and acceptance-risk review.
- `test-engineer`: Android/iOS smoke and regression test planning.

## Follow-up Staffing Guidance

### Recommended: `$ultragoal` + `$team`
- Ultragoal owns durable ledger and final completion evidence.
- Team lanes:
  1. **iOS lane** (`executor`, medium): ZoneMapView glass/card polish and simulator screenshot.
  2. **Android UI lane** (`executor` + optional `designer`, medium/high): MapScreen/KakaoZoneMap parity polish.
  3. **Verification lane** (`verifier` or `test-engineer`, high): builds, screenshots, API logs, clean git/push evidence.

### `$team` launch hint
```text
$ultragoal + $team execute .omx/plans/prd-mobile-ios-parity-glass-release-buildout.md with lanes: iOS, Android UI, Verification. Preserve no API changes, no feature expansion, no store submission.
```

### `$ralph` fallback
Use `$ralph` only if a single-owner sequential loop is explicitly preferred after this plan; it is not the recommended default for this parallelizable UI + verification work.

## Goal-Mode Follow-up Suggestions

- `$ultragoal` — default durable execution path.
- `$team` — recommended alongside Ultragoal because iOS, Android, and verification can run as separate lanes.
- `$performance-goal` — not applicable; this is not primarily a performance optimization.
- `$autoresearch-goal` — not applicable; this is not a research deliverable.

## Plan changelog
- Initial RALPLAN draft created from deep-interview answers and current source inspection.
- Local architect/critic consensus added after external subagent timeout; plan promoted to execution-ready.
