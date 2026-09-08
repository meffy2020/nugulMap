# NugulMap Store Privacy Readiness Inventory

> 기준일: 2026-09-07 KST
> 범위: `android-native/`, `ios-native/`, shared backend API. Expo `mobile/`은 회귀 비교용 별도 패키지이며 네이티브 스토어 답변에 합산하지 않는다.
> 목적: Google Play Data Safety, Apple App Privacy, privacy policy URL, store metadata, account deletion, and review-risk gates를 같은 데이터 인벤토리로 제출 전 검토한다.

## 1. Current submission verdict

2026-09-08 사용자 확정 정보:

- 운영자 표시명: 강승진. 스토어 개발자 표시명은 등록된 개인/조직 계정 명의와 대조한다.
- 지원 이메일: nugulmap@gmail.com. 실제 수신·회신은 아직 검증하지 않았다.
- Google Play 및 Apple 개발자 계정: 둘 다 보유(사용자 확인). 콘솔 로그인·앱별 서명 권한은 별도 확인한다.
- 스토어 배포 국가: 대한민국만. 전국 장소의 현장 검증 완료를 의미하지 않는다.
- 배포 환경의 `MODERATION_CONTACT_EMAIL` 값은 위 이메일로 설정한다. 공개 정책·지원 페이지의 로컬 후보에 반영했다.

| Gate | Current state | Public launch decision |
| --- | --- | --- |
| Privacy policy URL | `/privacy` names NugulMap, data categories/purposes, providers, retention/deletion, security, rights and the public support channel | **MANUAL** until deployed URL is reachable, deployment processors/retention match production, and the URL is entered in both consoles |
| Play Data Safety form | Draft inventory below exists; console form not submitted from repo | **HOLD** until console answers match this inventory |
| Apple App Privacy | Draft inventory below exists; App Store Connect form not submitted from repo | **HOLD** until App Privacy answers match this inventory |
| Account deletion | Android Account > 계정 삭제 and iOS Profile > 계정 삭제 call authenticated `DELETE /api/users/me`; `/account-deletion` contains a direct fixed-category web request form | **MANUAL** until real-device deletion smoke, deployed form/API verification and operator response flow pass |
| Store metadata | Korean/English copy, review notes, App access instructions, rating facts and screenshot list are drafted in Sections 9–13 | **MANUAL** until developer identity/contact, screenshots and console fields are completed |
| Review-risk gates | iOS includes Sign in with Apple and server-side Apple identity token exchange | **MANUAL** until Apple capability/App ID and real-device login smoke pass |

## 2. Data inventory from repository evidence

| Data element | Repo evidence | Play Data Safety draft category | Apple App Privacy draft category | Linked to user? | Shared? | Purpose | Notes / required confirmation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OAuth email | `User.email`, `UserResponse.email`, OAuth callback payloads | Personal info / email address | Contact Info / Email Address | Yes | OAuth provider involvement | Account management, authentication, app functionality | Providers are Apple/Kakao/Naver/Google; final console answers must match the enabled release providers. |
| OAuth provider ID | `User.oauthId`, `oauthProvider` | User IDs / account identifiers | Identifiers / User ID | Yes | OAuth provider involvement | Authentication and fraud/security | Do not expose in store screenshots or support replies. |
| Nickname | `User.nickname`, profile setup | Personal info / name or user-provided identifier | Contact Info or Identifiers depending final wording | Yes | No external sharing proven | Profile display, reviews, zone attribution | Public visibility risk: nickname may appear in reviews/profile UI. |
| Profile image | `profileImage`, multipart `profileImage` upload | Photos/videos or user-generated content | User Content / Photos or Videos | Yes | No external sharing proven | Profile personalization | Privacy policy must explain image storage/deletion. |
| Smoking-zone coordinates/address | `Zone.latitude`, `Zone.longitude`, `Zone.address` | Location or user-generated content depending source | Location and/or User Content | Possibly, when user-created | No external sharing proven | Map display, zone creation, search | Distinguish public place coordinates from current device location. |
| Device current location / search bounds | iOS `NSLocationWhenInUseUsageDescription`, API bounds/radius params | Location | Precise Location or Coarse Location | Not necessarily if only transient; confirm server logs | No external sharing proven | Find nearby zones and map positioning | Both native apps request optional foreground location for nearby smoking areas. Search and manual map movement remain available after denial. Disclose the actual release permission and network behavior. |
| Zone photo/image | `Zone.image`, multipart `image` upload | Photos/videos or user-generated content | User Content / Photos or Videos | Linked to submitting account | No external sharing proven | User zone submission | Policy must disclose moderation/removal/deletion behavior. |
| Review content | `ZoneReview.content`, author fields | User-generated content | User Content / Other User Content | Yes | Public within service | Reviews and community content | Free-text can contain personal data; disclose user responsibility/moderation. |
| Access/refresh tokens and pending PKCE verifier | iOS Keychain, Android `EncryptedSharedPreferences`; Android backup/data-transfer rules exclude `nugul_auth_encrypted.xml` | Security practices, not user-facing data category by itself | App Functionality / security credential handling | Yes | No sharing proven | Authentication/session persistence | Keep excluded from backup and never put token/verifier values in logs. |
| Diagnostics/logs/IP | Server/runtime logs not fully visible in repo | Diagnostics and/or device identifiers if retained | Diagnostics, Identifiers, or Location depending retention | Possible | Hosting/infra providers may process | Security, operations, abuse prevention | Final policy needs production log retention and processor list. |
| Kakao Maps SDK network/service metadata (Android) | Kakao Maps SDK `2.14.0`, `KakaoMapSdk.init`, and the release merged manifest | **UNKNOWN:** IP-derived data, Device or other identifiers, and App interactions cannot be classified from the current vendor material alone | N/A for the iOS answer | Unknown | Unknown | Intended use is app functionality / map rendering | The reviewed Kakao Android guide requires `INTERNET` but does not provide an Android SDK-specific Play Data Safety disclosure. Do not claim no collection or no sharing until Kakao provides written SDK-specific answers or an authorized signed-release network observation establishes the transmitted fields and handling. |
| Kakao Maps SDK declared Other Data Types (iOS) | Embedded Kakao Maps SDK `PrivacyInfo.xcprivacy`; confirm again from the signed archive privacy report | N/A for the Android answer; audit the Android SDK separately in Play Console | Other Data Types | SDK manifest currently marks linked to user | Kakao SDK processing; contractual meaning requires confirmation | App functionality / map rendering | Treat this as the SDK vendor declaration, not proof of production transmission. Compare the signed archive privacy report, Kakao documentation/contract and App Store Connect answers before submission. |
| Support request | Public support form email/category/message/receipt | Personal info / email and user-provided content | Contact Info / Email and User Content | Yes | Hosting/infra providers may process | Support, privacy rights, deletion and report response | `PENDING`/`IN_PROGRESS` 동안 보관한다. `RESOLVED`/`REJECTED` 처리일로부터 30일이 지나면 다음 일일 정리 작업에서 삭제하므로 실제 삭제는 최대 24시간 뒤일 수 있다. 운영(prod)은 이 기준을 고정하며, 배포 후 스케줄러 실행과 운영자 접근 절차를 별도 확인한다. |

### 2.1 Package-specific store boundary

| Submission package | Device location permission | Store form boundary |
| --- | --- | --- |
| Android native `com.nugulmap.nativeapp` | Optional `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`; no background location | Device location is used on an explicit current-location request. Viewport bounds may reflect that location. Confirm Precise/Approximate Location handling in the signed release and disclose user-submitted public coordinates separately. SDK-specific handling still needs Section 2.2 verification. |
| iOS native `com.nugulmap.native` | Optional When In Use permission | Answer App Privacy from the signed iOS build and production logging behavior; distinguish transient device location from public place coordinates. |
| Expo baseline `com.nugulmap.mobile` | Separate legacy package configuration | Do not copy its permission inventory into either native submission. Audit it separately only if that package is submitted. |

### 2.2 Android Kakao Maps SDK / Play Data Safety evidence boundary

Confirmed from the current repository and generated release artifacts:

- `android-native/gradle/libs.versions.toml` pins Kakao Maps SDK `2.14.0`; the app initializes it with `KakaoMapSdk.init` for map rendering.
- The source manifest declares `INTERNET`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. AndroidX adds an app-scoped receiver signature permission. The signed release manifest must be inspected again before store submission; background location and advertising identifier permissions are outside the current feature scope.
- `android:usesCleartextTraffic="false"` is present in the release merged manifest. This is a build-configuration fact, not packet-level proof of every SDK request or of server-side retention.
- Android `CurrentLocationProvider` uses permission-gated `LocationManager` Network/GPS providers. The current-location button may move the viewport to device coordinates; subsequent bounds requests can therefore reflect location. Manual map movement and place search work independently.

The foreground-location implementation does not establish what Kakao Maps SDK transmits over its network connection. Confirm SDK handling separately from the app-owned location request.

| Data Safety question for Kakao Maps SDK | Current answer | Evidence required to close |
| --- | --- | --- |
| IP address processing or IP-derived location/identifier | **UNKNOWN** | Kakao's written SDK-specific response describing collection, use, retention and deletion, or an authorized network observation of the signed release plus the corresponding contractual handling |
| Device or other identifiers, excluding Advertising ID | **UNKNOWN** | Vendor field-level disclosure or authorized observation showing whether any persistent, resettable, installation, hardware, or request identifier leaves the device |
| App interactions such as map opens, camera moves, searches or tile requests | **UNKNOWN** | Vendor disclosure or authorized observation showing event fields, purpose and retention |
| Transfer to Kakao and whether it qualifies as Play “sharing” | **UNKNOWN** | Contractual role determination and vendor description of whether Kakao acts only as a service provider under the developer's instructions or uses data for its own purposes |
| Encryption in transit for all SDK-transmitted user data | **UNKNOWN** | Signed-release network evidence or a Kakao SDK-specific guarantee covering every relevant endpoint and data type; `usesCleartextTraffic=false` alone is insufficient |

Submission gate:

1. Obtain a Kakao answer or current official SDK disclosure that is specific to Kakao Maps SDK for Android and the shipped SDK version.
2. If that is unavailable, perform an authorized network observation on the signed release candidate and record endpoints, request metadata, identifiers, interaction events, transport security and repeatability. Do not infer server retention or downstream use from packets alone.
3. Reconcile the result with Google Play's definitions of collection, sharing, service provider, purposes, optionality and deletion. Record the evidence date and release version next to the Play Console answers.
4. Until these gates close, do not submit “no user data collected or shared” based only on the manifest, lack of location permission, or lack of `AD_ID`.

## 3. Google Play Data Safety checklist

- [ ] Deploy and enter `https://nugulmap.com/privacy` in Play Console and in the app/store listing.
- [ ] Complete the Data Safety form even if any data is only optional or low-volume.
- [ ] Mark account creation as supported if OAuth signup/login remains available.
- [ ] Complete data-deletion questions and provide the in-app deletion path plus `https://nugulmap.com/account-deletion`.
- [ ] Declare collected data from Section 2 by the broadest production behavior across regions, versions, and user states.
- [ ] For each data type, confirm collection, sharing, encryption in transit, deletion request support, and whether users can choose collection.
- [ ] Confirm third-party SDK/provider processing for Kakao Maps, Kakao/Naver/Google OAuth, hosting, object storage, crash/log tooling, and analytics if enabled.
- [ ] Verify the signed Android release manifest contains only justified foreground location and network permissions, no background location or advertising identifier permission, and `usesCleartextTraffic=false`.
- [ ] Close the Kakao Maps SDK `UNKNOWN` items in Section 2.2 through a written SDK-specific vendor answer or authorized signed-release network observation before choosing the relevant Data Safety answers.
- [x] Smoking-only clients have no recommendation save or interaction flow. Historical server records and their account-deletion/retention handling remain; verify the production deployment disables discovery APIs before finalizing the forms.
- [ ] Keep the final package-level `App interactions` sharing and optionality answers open until the Kakao Maps SDK `UNKNOWN` items are closed; the Play form aggregates all app and SDK behavior for the package.
- [ ] Do not claim “not collected” for location if precise/coarse coordinates are transmitted to backend or retained in logs.
- [ ] Do not generalize “no location permission” or “no `AD_ID` permission” into “no IP, identifiers, app interactions, or sharing.”
- [ ] Keep Data Safety answers consistent with the privacy policy and in-app disclosures.

## 4. Apple App Privacy checklist

- [ ] Add `https://nugulmap.com/privacy` in App Store Connect before submission.
- [ ] Answer App Privacy for app code and third-party partners integrated into the app.
- [ ] Reconcile Kakao Maps SDK `Other Data Types / linked / app functionality` from the signed archive privacy report with Kakao documentation/contract and the App Store Connect form.
- [x] Include the app `PrivacyInfo.xcprivacy` in the iOS target with UserDefaults required-reason code `CA92.1`.
- [x] Remove the app-owned recommendation Product Interaction declaration from the smoking-only manifest. Keep actual account, location and user-content declarations; reconcile the embedded SDK manifest separately.
- [ ] Mirror `Usage Data > Product Interaction` in App Store Connect. Initial personalization opt-in does not make ongoing primary-feature collection exempt from disclosure.
- [ ] Declare whether each data type in Section 2 is linked to the user and whether it is used for tracking.
- [ ] If location is only processed on device, document why it is not collected; if sent to backend/logs, disclose the relevant location category.
- [ ] If user-uploaded photos, profile images, reviews, or zone descriptions are stored, disclose User Content and linked-user status.
- [ ] If diagnostics, crash logs, performance data, or IP-derived metadata are retained by production tooling, disclose them.
- [ ] Verify Sign in with Apple works on a real device/TestFlight build.
- [x] Set `ITSAppUsesNonExemptEncryption=NO` for the current app code, which uses platform TLS and hashing but no non-exempt encryption implementation; reclassify if release dependencies change.
- [ ] Ensure account deletion remains easy to find in the native app and does not require unnecessary extra steps.

## 5. Store metadata checklist

| Metadata item | Required action | Current repo evidence |
| --- | --- | --- |
| App name/subtitle/short description | Enter the reviewed Korean/English draft | Sections 10–11 |
| Full description | Use the draft that distinguishes fresh observations from delayed/unavailable data | Sections 10–11 |
| Category/content rating | Complete current console questionnaires using the documented factual boundary | Section 13; final rating remains console-generated |
| Screenshots/previews | Capture real native screens after OAuth/map/profile smoke | Not proven in repo |
| Support URL | Publish reachable support/contact page or mail path | Both native settings screens link to `https://nugulmap.com/support`; production reachability remains manual |
| Privacy policy URL | Publish public URL naming NugulMap, processing scope, providers, retention/deletion, protection and contact | `frontend/app/privacy/page.tsx`, deploy as `https://nugulmap.com/privacy` |
| Account deletion URL | Publish direct web deletion-request form for Play and support flows | `frontend/app/account-deletion/page.tsx`, deploy as `https://nugulmap.com/account-deletion` |
| Review notes / App access | Explain unauthenticated map scope, OAuth, optional location, UGC controls and deletion | Sections 11–12; secure reviewer credentials remain manual |
| Play App Signing | Confirm package ownership, enroll the app, then register the Play app-signing certificate hash with Kakao/OAuth providers | Upload-key signing alone does not prove the certificate on Play-delivered APKs |

## 6. Account deletion acceptance checklist

- [ ] In-app path is discoverable from Account/Profile on Android and iOS native apps.
- [ ] Web/out-of-app deletion or deletion-request URL exists and is entered in Play Console where required.
- [ ] Deletion covers user account, OAuth-linked account records, tokens, profile image, user-created zones/images where legally/product-wise deletable, reviews, and operational logs according to retention policy.
- [ ] User sees what will be deleted, what may be retained, the identity-check path and where the processing result is sent.
- [ ] Device smoke verifies login → deletion path → confirmation → token cleanup → blocked access with old token.

## 7. Review-risk gates before public submission

| Risk | Required evidence before go |
| --- | --- |
| Apple Guideline 4.8 social login | Apple-equivalent login added, or documented exception reviewed against current guideline text |
| Privacy label mismatch | Store answers reviewed against production backend logs, SDKs, OAuth providers, object storage, and analytics/crash tooling |
| User-generated content | Android/iOS 장소·사진 신고, 리뷰 신고, 작성자 차단과 운영자 대기열·처리 API가 로컬 구현됨. 운영자는 `RESOLVE`, `DISMISS`, `REMOVE_CONTENT`로 처리할 수 있고, `REMOVE_CONTENT`는 신고 콘텐츠와 연결 신고 행을 함께 제거함. 실계정 runtime smoke, 배포 환경의 운영자 키, 실제 운영자 처리 절차, support path 공개가 추가로 필요 |
| Location sensitivity | Location permission copy, privacy policy, and store labels consistently distinguish current device location from public zone coordinates |
| Account deletion | In-app and web paths verified, with deletion scope and retention wording ready for reviewers |
| Smoking-related content | Content rating and description avoid unsupported health/safety guarantees |

UGC 운영 처리의 현재 증거 경계:

- 리뷰 신고 처리는 `PATCH /api/operator/moderation/reports/{reportId}`, 장소·사진 신고 처리는 `PATCH /api/operator/moderation/reports/zones/{reportId}`를 사용한다.
- 두 경로 모두 운영자 도구 전용 `X-Nugul-Operator-Key`가 필요하며, 이 값은 네이티브 앱에 포함하거나 사용자에게 노출하지 않는다.
- `REMOVE_CONTENT`는 신고된 리뷰 또는 장소와 같은 콘텐츠에 연결된 신고 행을 함께 제거한다. 장소 이미지가 있으면 이미지 삭제도 시도한다.
- 위 항목은 저장소의 로컬 구현 상태다. 운영 배포, 운영자 자격 증명 주입, 실운영 처리·지원 응답 체계, 스토어 승인 상태는 별도 검증 대상이다.

## 8. Official reference links

- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play Advertising ID: https://support.google.com/googleplay/android-developer/answer/6048248
- Google Play account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/15402170
- Google Play SDK data-use responsibility: https://developer.android.com/privacy-and-security/declare-data-use
- Kakao Maps SDK for Android getting started: https://apis.map.kakao.com/android_v2/docs/getting-started/
- Kakao privacy policy: https://www.kakao.com/policy/privacy
- Kakao Developers security: https://developers.kakao.com/terms/ko/security
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple privacy manifest files: https://developer.apple.com/documentation/bundleresources/privacy-manifest-files
- Apple collected-data types and purposes: https://developer.apple.com/documentation/bundleresources/describing-data-use-in-privacy-manifests
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/

## 9. Submission identity and public URLs

| Field | Submission value or gate |
| --- | --- |
| App name | 너굴맵(NugulMap) |
| iOS Bundle ID | `com.nugulmap.native` |
| Android Package ID | `com.nugulmap.nativeapp` |
| Privacy policy | `https://nugulmap.com/privacy` |
| Support | `https://nugulmap.com/support` |
| Account deletion | `https://nugulmap.com/account-deletion` |
| Social login connection management | `https://nugulmap.com/connected-accounts` |
| Terms | `https://nugulmap.com/terms` |
| Developer/legal entity display name | **MANUAL:** enter the actual store-account registration name. Do not infer it from the repository. |
| Public support email | Production Compose/deploy now fail fast without `MODERATION_CONTACT_EMAIL`, and the public runtime contract requires `supportEmailConfigured=true`. **MANUAL:** prove that the configured inbox is monitored and replies are handled. |

공개 Play/App Store 조회에서는 위 네이티브 ID의 기존 출시 앱이 확인되지 않았지만, draft·내부 테스트·미출시 레코드는 공개 조회로 판별할 수 없다. 업로드 키나 프로비저닝을 만들기 전에 대상 콘솔 계정에서 위 ID를 소유·예약할 수 있는지 확인한다. 이미 다른 ID로 빌드를 업로드한 앱을 업데이트해야 하는 경우에는 그 콘솔 ID를 먼저 기준으로 삼고, 클라이언트·OAuth·Kakao 등록을 함께 정렬한다.

The five URLs must return HTTPS `200` without authentication or redirect loops before console entry. `nugulmap.com` is deployed by the existing Vercel Git integration; the repository workflow verifies all five generated routes and then polls the public contract for about six minutes after frontend verification, without adding a duplicate frontend service to the API EC2 Compose stack. The retry only absorbs normal propagation races; a slower or broken Vercel deployment remains a failed external gate. Vercel project linkage, production-domain aliasing, and the resulting deployment remain external evidence. The deployed privacy policy must name the
actual infrastructure processors and match production log/support retention; source code cannot establish those operations facts.

## 10. Store listing copy

Korean:

- App name: `너굴맵 - 흡연구역 지도`
- App Store subtitle: `가까운 흡연구역 찾기`
- Play short description: `주변 흡연구역을 찾고 위치와 상세 정보를 지도에서 확인하세요.`
- Suggested category: Navigation / Maps & Navigation; secondary iOS category: Travel
- Keywords draft: `흡연구역,흡연실,지도,길찾기,서울,위치,장소`

```text
너굴맵은 가까운 흡연구역을 지도에서 찾는 앱입니다.

주요 기능
• 흡연구역 위치와 상세 정보 확인
• 현재 위치 주변 흡연구역 찾기
• 지도 앱으로 길찾기 연결
• 장소 검색과 지도 이동
• 이용자 장소 제보와 리뷰
• 부적절한 장소·사진·리뷰 신고와 작성자 차단
• 앱 안에서 계정 삭제

지도 조회는 로그인 없이 이용할 수 있습니다.

App Store에 추가: iOS 위치 권한은 선택 사항이며, 거부해도 검색과 지도 이동으로 장소를 볼 수 있습니다. 허용하면 현재 위치 주변의 장소를 빠르게 찾을 수 있습니다.

Google Play에 추가: Android 위치 권한은 선택 사항이며, 거부해도 검색과 지도 이동으로 흡연구역을 찾을 수 있습니다.

흡연구역은 현장 상황에 따라 폐쇄되거나 이용 시간이 달라질 수 있습니다. 현장 표지와 이용 안내를 확인해 주세요.

흡연구역 정보는 지정구역 준수와 간접흡연 노출 감소를 돕기 위한 안내이며, 담배·전자담배 판매나 홍보 기능을 제공하지 않습니다.
```

Do not claim this is an official government app or that smoking is permitted at every displayed point. On-site signs and current restrictions take priority.

English:

- App name: `NugulMap - Smoking Area Map`
- Subtitle / short description: `Find nearby smoking areas on a map.`

```text
NugulMap helps users find nearby smoking areas, view place details, and open directions.

Users can browse the map without an account. Account features include place submissions, reviews, content reporting, user blocking, and in-app account deletion.

Add to the App Store description: iOS location permission is optional and is used only to show nearby places. If denied, search and manual map movement remain available.

Add to the Google Play description: Android location permission is optional. Users can also find smoking areas with search and manual map movement.

Smoking areas may close or have access restrictions. Check on-site signs and current guidance before use.

Smoking-area information supports designated-area compliance and reduced secondhand-smoke exposure. The app does not sell or promote tobacco or vaping products.
```

## 11. App Store Review Notes draft

Use only after the production API and public URLs pass smoke. If account features need credentials, put active accounts in App Store Connect's
secure review fields, never in this repository or the public note.

```text
NugulMap is a map app for nearby smoking areas.

NugulMap does not sell, promote, or encourage tobacco or vaping. Smoking-area information is provided to help users follow designated-area rules and reduce secondhand-smoke exposure; on-site signs and applicable law take priority.

REVIEW WITHOUT SIGN-IN
1. Launch the app.
2. The map, place search and smoking-area details can be reviewed without an account.
3. Location permission is optional. If denied, the reviewer can still search and move the map manually.
4. Tap a smoking-area marker to review its address, details and directions.

ACCOUNT AND UGC REVIEW
1. Open the profile/account sheet from the map.
2. Sign in with Apple is available on iOS. Other supported providers are Kakao, Naver, and Google.
3. Sign-in is required only for place submissions, reviews, reports, blocking, and account deletion.
4. Before creating user content, the user must accept the Terms and Community Policy.
5. Public place descriptions, reviews, and public nicknames are screened by the server before publication. In a place detail view, the reviewer can report the place/photo, report a review, or block the review author.
6. Account deletion is available from Profile > My Info > Delete Account and removes the app account and linked user content as described in the deletion notice.

PLACE DATA
Smoking-area information can change. On-site signs and access restrictions take priority; users can report incorrect or closed places.

PUBLIC POLICIES
Privacy: https://nugulmap.com/privacy
Support and reporting: https://nugulmap.com/support
Account deletion: https://nugulmap.com/account-deletion
Terms: https://nugulmap.com/terms
```

Before submission:

- [ ] Provide a working Sign in with Apple path or a secure review account for authenticated features.
- [ ] If report/block needs two identities, provide active reviewer accounts A/B in the secure fields.
- [ ] Confirm accounts are not expired, locked, or waiting for human 2FA approval.
- [ ] Keep backend, image, map and OAuth callback services reachable throughout review.

## 12. Google Play App access draft

```text
The map, search and smoking-area details can be reviewed without signing in.

Sign-in is required for place submissions, reviews, reporting, blocking, and account deletion. Provide active reviewer credentials in the secure App access fields when these account features must be tested. Do not place credentials in the public store listing.

Android foreground location is optional. Reviewers can deny it and use search or manual map movement.

Account deletion path: Map > menu > Login/Profile > Delete Account.
Web deletion request: https://nugulmap.com/account-deletion
```

## 13. Rating facts, screenshots and manual gates

Answer the current console questionnaires from these facts:

- The app locates smoking areas but does not sell tobacco or encourage smoking.
- Users can publish places, photos, descriptions and reviews, so the app contains UGC.
- UGC creation requires Terms/Community Policy acceptance; the server applies blocked-term checks to public place descriptions, reviews and nicknames; clients provide reports and user blocking; operator APIs support follow-up action.
- iOS and Android foreground location permissions are optional and used for nearby smoking-area results. Search remains available after denial.
- Social login/account creation exists, with in-app and web account deletion paths.
- No advertising SDK or third-party advertising tracker is present in the current native dependency inventory. Re-audit if a release build adds one.
- The stores calculate the final age/content rating; do not hardcode a rating in repository docs.

Capture the following from each signed release candidate:

1. Smoking-area map with search and nearby markers
2. Selected smoking area with address and directions
3. Current-location action with permission granted
4. Search and manual map movement after denying location on both platforms
5. Place/photo report, review report and author block entry points
6. Terms/Community Policy acceptance before UGC
7. Account deletion entry and confirmation text

Final manual gates:

- [ ] Enter the real developer/legal entity name and a monitored support email in both consoles.
- [ ] Verify all five public URLs and a real support-form receipt/reply path.
- [ ] Reconcile Play Data Safety and Apple App Privacy with the production providers and retention configuration.
- [ ] Export the signed iOS archive privacy report and reconcile Kakao Maps SDK `Other Data Types` with the App Store Connect answers.
- [ ] Verify Apple capability/App ID, the Play app-signing certificate hash registered with Kakao/OAuth providers, and every production provider redirect.
- [ ] If the Play account is a newly created personal account subject to Google testing requirements, complete the required closed test and device verification before requesting production access.
- [ ] Smoke login, location denial, map, directions, report/block and deletion on a signed AAB and TestFlight build.
- [ ] Prepare a staffed moderation/support queue, operator key, staged rollout and previous-build rollback path. Simultaneous platform launch is not required.


## 14. 2026-09-07 official submission requirement refresh

- New phone apps and updates require target API 36 from 2026-08-31. This project targets 36; both release checkers now reject 35. [Google target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- Apple submissions require Xcode 26 and iOS 26 SDK from 2026-04-28. The deployment target is a different setting; signed archives still need SDK/signing validation. [Apple upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Neither store explicitly guarantees approval of a smoking-area utility. Tobacco sales/promotion and encouragement are restricted; keep the listing focused on designated-area information. [Apple review guidelines](https://developer.apple.com/app-store/review/guidelines/), [Google tobacco policy](https://support.google.com/googleplay/android-developer/answer/9878810?hl=en)
- Public user content still requires reporting, blocking and actual moderation/contact operations. Account deletion and public privacy/support URLs remain release gates. [Google UGC](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en), [Google user data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

These source checks do not replace store review or the outstanding production evidence.
