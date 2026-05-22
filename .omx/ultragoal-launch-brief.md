NugulMap iOS-Parity Glass Mobile Release Buildout. Source of truth is .omx/plans/prd-mobile-ios-parity-glass-release-buildout.md and .omx/specs/mobile-ios-parity-glass-release-buildout.md.

Create exactly four durable goals and do not split bullets into separate goals.

Goal 1 title: iOS glass parity lane. Objective: polish ZoneMapView for fullscreen map-first layout, rounded/glass search/profile chrome, compact glass selected-zone/report/profile modal treatment, preserve marker synchronization from commit 27a488a, and produce iOS build/run plus screenshot evidence.

Goal 2 title: Android iOS-parity fullscreen map lane. Objective: finalize MapScreen and KakaoZoneMap as a fullscreen map-first UI with polished no-key fallback, key-present compatibility, no red missing-key failure panel, no MVP/MAP/test wording, correct top chrome divider, compact bottom card, and polished modal sheets.

Goal 3 title: Cross-platform verification lane. Objective: run Android assembleDebug, install and launch the emulator APK, capture Android screenshot and API load evidence, run iOS simulator build/run where available, capture iOS screenshot and marker evidence, and record generated artifact paths without committing tmp screenshots/logs.

Goal 4 title: Final cleanup commit push lane. Objective: keep backend/API contracts unchanged, avoid feature expansion/store submission/new secrets/new dependencies, remove generated artifacts from git status, run final quality checks, commit with Lore protocol, push main to origin/main, and verify local branch is clean against origin.
