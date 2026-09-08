# Isolated Android release verification — 2026-09-07

The unchanged release variant was copied to a private temporary directory and compiled with the **existing Android Debug certificate**. No upload key was created, no production signing setting was changed, and no test AAB was placed under the shipping project's `app/build/outputs/bundle/release` directory. These artifacts are **NOT FOR UPLOAD**.

Reproduce the compile with `scripts/verify-isolated-release.sh`. It prints a private temporary directory containing `NOT-FOR-UPLOAD.txt`, build logs and the copied project. The temporary copy may contain local SDK configuration and should remain private.

Observed results:

- `assembleRelease` and `bundleRelease`: successful.
- APK signer: `CN=Android Debug, O=Android, C=US`.
- Merged release manifest: targetSdk 36, debuggable false, cleartext false, Internet and foreground location only (plus the AndroidX internal non-exported receiver permission). No background location permission.
- APK and AAB each contain four native libraries. Both arm64 libraries (`libK3fAndroid.so`, `libandroidx.graphics.path.so`) have minimum LOAD alignment 16,384 bytes and GNU_RELRO. Both 32-bit libraries have GNU_RELRO; 32-bit LOAD alignment is not the 64-bit 16KB requirement.
- Android Build Tools 36.1 `zipalign -c -P 16 4` passed for the isolated APK.
- Official Google bundletool 1.18.1 reported `uncompressNativeLibraries.enabled=true` and `PAGE_ALIGNMENT_16K` for the isolated AAB.
- `scripts/test_native_page_alignment.py`: three tests passed, including misalignment/missing RELRO and truncated ELF inspection.
- The shipping project's `checkReleaseSigning` still fails on all four missing upload-signing values, as intended.

The native layout verifier is `scripts/verify-native-page-alignment.py <APK-or-AAB>`. It does not execute native libraries or claim device compatibility from static layout. ZIP alignment and bundle config were additionally inspected using the official tools described in [Android's 16KB guidance](https://developer.android.com/guide/practices/page-sizes). The bundletool download came from [Google's 1.18.1 release](https://github.com/google/bundletool/releases/tag/1.18.1).

Remaining release evidence: actual Play upload signing/Play signing hash, signed-track installation and real OAuth. The later 2026-09-08 pass below closes the isolated 16KB emulator runtime gap. Demo-contaminated production data also remains a separate release blocker.

## Isolated emulator runtime smoke

The release-variant APK signed with the existing debug certificate was installed on a dedicated cloned API36.1 emulator (`emulator-5556`), separate from the shared device used by another task. Package flags do not include DEBUGGABLE. Kakao tiles and smoking markers rendered with the release manifest.

Foreground permission was granted and a deterministic Android test GPS provider supplied `37.544600,127.055700` (5m declared accuracy). Pressing the actual current-location control recentered from central Seoul to Seongsu; the screenshot `current-location-seongsu.png` shows the resulting map and is **mock-location UI evidence**, not physical GPS accuracy evidence. Emulator console GPS injection did not reliably replace its cached overseas fix, so the explicit test provider was used and recorded as such.

Selecting the visible IS Biz Tower smoking booth and pressing `길찾기` resumed `com.google.android.apps.maps/com.google.android.maps.MapsActivity` with `ACTION_VIEW geo:37.548083,127.0575242?q=...`. This proves the release UI hands the selected coordinates to an external map. It does not prove turn-by-turn travel, availability of that facility, or a Play-signed installation.

The dedicated cloned emulator was closed after the checks; the shared emulator was left running for its other task.

## Completed 16KB runtime pass — 2026-09-08

The previous download time limit was removed, so the image was downloaded and verified. Google API36.1 `google_apis_ps16k/arm64-v8a` revision4 archive size was 1,936,180,596 bytes; published SHA1 `a48079a4c1c7781d157ff9ff1c0fc9d5ab3ffc58` matched. Download completed in255 seconds; 4.57GB was extracted to private temporary storage and the large ZIP was removed after validation.

A fresh dedicated AVD using that exact image booted with `sys.boot_completed=1` and **`getconf PAGE_SIZE=16384`**. The isolated release APK was installed; Kakao native tiles and smoking markers visibly rendered. Tapping a marker opened the selected zone detail with `길찾기`, `리뷰 보기`, and `장소 신고`; app PID remained alive throughout the interaction. See `release-16k-home.png`, `release-16k-detail.png` and machine-readable `runtime-16k.json`.

This is actual 16KB emulator runtime evidence, in addition to static ELF/ZIP/AAB checks. It still uses the existing Debug certificate and production data known to contain demo records, so these are engineering evidence, not store-submission screenshots or trusted-place evidence. The owned16KB emulator was shut down; shared emulator5554 was untouched during this pass. The verified extracted image remains in the private temporary staging directory for repeat testing. No project dependency, provider configuration, signing key or shipping output was changed.
