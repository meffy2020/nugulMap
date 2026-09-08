# Play Console release attempt — 2026-09-08

User authorized release; operator 강승진, support nugulmap@gmail.com, Korea only.

## Verified account state

Read the existing authenticated Safari Play Console app-list tab. Account display name: NugulMap; account type: personal. No app record exists: the page says “첫 번째 앱 만들기”. App creation is locked: “새 앱을 만들려면 계정 확인을 완료하세요.”

The account home lists these required steps:

- Verify access to an actual Android mobile device by signing in through the Play Console mobile app.
- Verify the contact phone number after other required identity verification is complete/approved.

These are account-owner verification gates. The Console does not currently permit creating the app, configuring Play App Signing, or submitting a testing/production release. No new app, signing key, upload, purchase, or account change was made.

## Local signing state

Checked project signing configuration, process environment variable presence, and standard Android/Gradle/fastlane/gcloud credential locations without displaying secret values. All four required `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` settings are absent. Only the existing Android debug keystore was found; it is not a production upload key. No production AAB exists in the shipping output location.

Fresh `python3 android-native/scripts/check-launch-readiness.py`: 13 PASS, 2 FAIL, 4 MANUAL. The failures are missing upload-signing configuration and missing production AAB. Existing isolated release/16 KB evidence remains engineering verification, not a Play-submittable artifact.

## Next executable release steps after account verification

Confirm the new app package `com.nugulmap.nativeapp`, establish and securely retain the upload-key configuration, build/verify the actual signed AAB, configure Play App Signing and Kakao's Play-delivered signing certificate hash, then validate a Play-installed build. Restrict availability to South Korea and use nugulmap@gmail.com as the support contact. Store testing eligibility and production access must be checked in the newly created app record; do not assume internal testing alone grants production eligibility. The independent production-data and policy gates must also pass before submission.
