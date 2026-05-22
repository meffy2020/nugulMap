# Mobile iOS-Parity Glass Map Release Buildout Spec

## Deep-interview outcome

Ambiguity: 18% → below standard threshold. Requirements are execution-ready.

## Primary goal

Bring the mobile native experience to iOS design parity:

- iOS is the canonical target design.
- Android should match iOS as closely as possible, including fullscreen map composition, rounded/glass search chrome, and bottom glass card/modal treatment.
- Android must be built up from the current MVP/test-screen feel to release-quality UI.

## Design direction

### iOS

- Fullscreen map-first screen.
- Rounded search field with glass/material feel.
- Top profile/account action remains compact and floating.
- Bottom selected-zone card/modal should be a smaller glass-style card, not a heavy plain sheet.
- The visual language should feel polished enough for public screenshots.

### Android

- Match iOS layout and hierarchy as much as native Android/Compose allows.
- Main screen should be fullscreen map-first, not a vertical test page.
- Search/profile controls should float on top with rounded/glass-style surfaces.
- Selected-zone detail should appear as a compact bottom glass card.
- Account, report, and reviews should move into bottom sheets/modals instead of permanently occupying the main screen.
- If `KAKAO_NATIVE_APP_KEY` is absent, fallback map must still look like a release-quality map preview rather than a plain `MAP` test box.
- If `KAKAO_NATIVE_APP_KEY` is present, real Kakao map should use the same overlay chrome and selected-zone behavior.

## Non-goals / boundaries

- No backend/API contract changes.
- No new feature expansion for report/review beyond existing capabilities; only placement and visual treatment may change.
- No App Store or Play Console submission in this pass.
- Existing API endpoints and auth flow contracts must remain compatible.

## Android Kakao key policy

- Implement both paths:
  1. No key: polished fallback map preview with loaded zone count and no test-page feel.
  2. Key present: actual Kakao map uses the same fullscreen shell and floating chrome.

## Required completion evidence

- iOS simulator screenshot showing:
  - fullscreen map,
  - rounded/glass search UI,
  - glass-style bottom card/modal treatment.
- Android emulator screenshot showing:
  - iOS-parity fullscreen map shell,
  - polished no-key fallback state if no Kakao key is available,
  - compact bottom glass card.
- Build evidence:
  - iOS build/run or equivalent simulator build success.
  - Android `assembleDebug` and emulator install/run success.
- API evidence:
  - operating API zone load confirmed in logs or visible UI for both platforms where possible.
- Git evidence:
  - changes committed and pushed to `origin/main`.

## Known current facts

- iOS marker bug was fixed and pushed in commit `27a488a`.
- Android previously had a vertical MVP screen; it is not acceptable as release UI.
- Android local environment currently has no `KAKAO_NATIVE_APP_KEY`, so no-key fallback evidence is mandatory.
- Disk space has been tight; avoid large generated artifacts and clean build outputs after verification when safe.
