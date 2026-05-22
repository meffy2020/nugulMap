# Ultragoal Brief: NugulMap Android/iOS public launch readiness via Team

Use `.omx/plans/prd-nugulmap-mobile-public-launch.md` and `.omx/plans/test-spec-nugulmap-mobile-public-launch.md` as the source of truth.

Create exactly these durable goals:

1. Android launch readiness lane
   - Run/repair Android native static and build checks where possible.
   - Track signed AAB, Play Console, Kakao key/hash, 16KB page-size, and device smoke blockers.
   - Do not store credentials in repo.

2. iOS launch readiness lane
   - Run/repair iOS native static and simulator/build checks where possible.
   - Track DEVELOPMENT_TEAM/signing, archive/TestFlight, Apple social login Guideline 4.8, account deletion, and device smoke blockers.
   - Do not store credentials in repo.

3. Store-Privacy readiness lane
   - Produce/maintain Play Data Safety and Apple App Privacy data inventory/checklist.
   - Keep privacy policy URL, store metadata, account deletion, and review-risk blockers explicit.

4. Verification and go-no-go lane
   - Run baseline verification commands that are possible locally.
   - Maintain evidence, blocker matrix, and final go/no-go report.
   - Checkpoint Ultragoal from Team evidence.

Constraints:
- No new feature expansion except store-required compliance blockers.
- No actual production store submission.
- No secrets/credentials in repo.
- Team lanes: Android, iOS, Store-Privacy, Verification.
