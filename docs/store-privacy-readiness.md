# NugulMap Store Privacy Readiness Inventory

> 기준일: 2026-05-22 KST  
> 범위: `android-native/`, `ios-native/`, shared backend API, and the existing Expo `mobile/` release baseline.  
> 목적: Google Play Data Safety, Apple App Privacy, privacy policy URL, store metadata, account deletion, and review-risk gates를 같은 데이터 인벤토리로 제출 전 검토한다.

## 1. Current submission verdict

| Gate | Current state | Public launch decision |
| --- | --- | --- |
| Privacy policy URL | Public URL not stored in repo | **HOLD** until a public, non-placeholder URL is published and entered in Play Console/App Store Connect |
| Play Data Safety form | Draft inventory below exists; console form not submitted from repo | **HOLD** until console answers match this inventory |
| Apple App Privacy | Draft inventory below exists; App Store Connect form not submitted from repo | **HOLD** until App Privacy answers match this inventory |
| Account deletion | Backend `DELETE /users/{id}` exists; native discoverable deletion UX is not proven by static scan | **HOLD** until in-app and web deletion/request paths are verified |
| Store metadata | App IDs/version values exist; descriptions/screenshots/support URLs are not proven here | **HOLD** until metadata checklist is complete |
| Review-risk gates | iOS uses Kakao/Naver/Google social login without detected Apple-equivalent login | **HOLD** until Guideline 4.8 is resolved or an approved exception is documented |

## 2. Data inventory from repository evidence

| Data element | Repo evidence | Play Data Safety draft category | Apple App Privacy draft category | Linked to user? | Shared? | Purpose | Notes / required confirmation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OAuth email | `User.email`, `UserResponse.email`, OAuth callback payloads | Personal info / email address | Contact Info / Email Address | Yes | OAuth provider involvement | Account management, authentication, app functionality | Confirm provider-specific disclosure text for Kakao/Naver/Google. |
| OAuth provider ID | `User.oauthId`, `oauthProvider` | User IDs / account identifiers | Identifiers / User ID | Yes | OAuth provider involvement | Authentication and fraud/security | Do not expose in store screenshots or support replies. |
| Nickname | `User.nickname`, profile setup | Personal info / name or user-provided identifier | Contact Info or Identifiers depending final wording | Yes | No external sharing proven | Profile display, reviews, zone attribution | Public visibility risk: nickname may appear in reviews/profile UI. |
| Profile image | `profileImage`, multipart `profileImage` upload | Photos/videos or user-generated content | User Content / Photos or Videos | Yes | No external sharing proven | Profile personalization | Privacy policy must explain image storage/deletion. |
| Smoking-zone coordinates/address | `Zone.latitude`, `Zone.longitude`, `Zone.address` | Location or user-generated content depending source | Location and/or User Content | Possibly, when user-created | No external sharing proven | Map display, zone creation, search | Distinguish public place coordinates from current device location. |
| Device current location / search bounds | iOS `NSLocationWhenInUseUsageDescription`, API bounds/radius params, Expo location dependency | Location | Precise Location or Coarse Location | Not necessarily if only transient; confirm server logs | No external sharing proven | Find nearby zones and map positioning | If transmitted or retained, disclose collection; if only on-device, document that. |
| Zone photo/image | `Zone.image`, multipart `image` upload | Photos/videos or user-generated content | User Content / Photos or Videos | Linked to submitting account | No external sharing proven | User zone submission | Policy must disclose moderation/removal/deletion behavior. |
| Review content | `ZoneReview.content`, author fields | User-generated content | User Content / Other User Content | Yes | Public within service | Reviews and community content | Free-text can contain personal data; disclose user responsibility/moderation. |
| Bookmarks / my zones | Mobile/profile flow and backend user-zone relations | App activity / saved items | Usage Data / Product Interaction | Yes | No external sharing proven | Personalization and account functionality | Confirm whether bookmark table/endpoint is active before final forms. |
| Access/refresh tokens | Native Keychain / app storage token paths | Security practices, not user-facing data category by itself | App Functionality / security credential handling | Yes | No sharing proven | Authentication/session persistence | Mention secure local storage; do not put token values in logs. |
| Diagnostics/logs/IP | Server/runtime logs not fully visible in repo | Diagnostics and/or device identifiers if retained | Diagnostics, Identifiers, or Location depending retention | Possible | Hosting/infra providers may process | Security, operations, abuse prevention | Final policy needs production log retention and processor list. |

## 3. Google Play Data Safety checklist

- [ ] Enter a public privacy policy URL in Play Console and in the app/store listing.
- [ ] Complete the Data Safety form even if any data is only optional or low-volume.
- [ ] Mark account creation as supported if OAuth signup/login remains available.
- [ ] Complete data-deletion questions and provide both in-app and web/out-of-app account deletion or deletion-request paths.
- [ ] Declare collected data from Section 2 by the broadest production behavior across regions, versions, and user states.
- [ ] For each data type, confirm collection, sharing, encryption in transit, deletion request support, and whether users can choose collection.
- [ ] Confirm third-party SDK/provider processing for Kakao Maps, Kakao/Naver/Google OAuth, hosting, object storage, crash/log tooling, and analytics if enabled.
- [ ] Do not claim “not collected” for location if precise/coarse coordinates are transmitted to backend or retained in logs.
- [ ] Keep Data Safety answers consistent with the privacy policy and in-app disclosures.

## 4. Apple App Privacy checklist

- [ ] Add the privacy policy URL in App Store Connect before submission.
- [ ] Answer App Privacy for app code and third-party partners integrated into the app.
- [ ] Declare whether each data type in Section 2 is linked to the user and whether it is used for tracking.
- [ ] If location is only processed on device, document why it is not collected; if sent to backend/logs, disclose the relevant location category.
- [ ] If user-uploaded photos, profile images, reviews, or zone descriptions are stored, disclose User Content and linked-user status.
- [ ] If diagnostics, crash logs, performance data, or IP-derived metadata are retained by production tooling, disclose them.
- [ ] Resolve App Review Guideline 4.8 before review: third-party/social login requires an equivalent privacy-preserving login option unless a valid exception applies.
- [ ] Ensure account deletion is easy to find in the native app and does not require unnecessary extra steps.

## 5. Store metadata checklist

| Metadata item | Required action | Current repo evidence |
| --- | --- | --- |
| App name/subtitle/short description | Draft final Korean and English copy; remove placeholders | Not proven in repo |
| Full description | Explain map/community purpose without unsupported safety or official-government claims | Not proven in repo |
| Category/content rating | Complete store questionnaires consistently with smoking-zone content | Not proven in repo |
| Screenshots/previews | Capture real native screens after OAuth/map/profile smoke | Not proven in repo |
| Support URL | Publish reachable support/contact page or mail path | Not proven in repo |
| Privacy policy URL | Publish public URL naming NugulMap/developer and data practices | Missing/blocker |
| Account deletion URL | Publish web/out-of-app deletion or request URL for Play and support flows | Missing/blocker |
| Review notes | Explain OAuth providers, demo/test account if required, location use, and any Apple login exception | Missing/blocker |

## 6. Account deletion acceptance checklist

- [ ] In-app path is discoverable from Settings/Profile on Android and iOS native apps.
- [ ] Web/out-of-app deletion or deletion-request URL exists and is entered in Play Console where required.
- [ ] Deletion covers user account, OAuth-linked account records, tokens, profile image, user-created zones/images where legally/product-wise deletable, reviews, and operational logs according to retention policy.
- [ ] User sees what will be deleted, what may be retained, and approximate processing time before confirming.
- [ ] Device smoke verifies login → deletion path → confirmation → token cleanup → blocked access with old token.

## 7. Review-risk gates before public submission

| Risk | Required evidence before go |
| --- | --- |
| Apple Guideline 4.8 social login | Apple-equivalent login added, or documented exception reviewed against current guideline text |
| Privacy label mismatch | Store answers reviewed against production backend logs, SDKs, OAuth providers, object storage, and analytics/crash tooling |
| User-generated content | Moderation/report/removal policy and support path documented if reviews/photos/descriptions are public |
| Location sensitivity | Location permission copy, privacy policy, and store labels consistently distinguish current device location from public zone coordinates |
| Account deletion | In-app and web paths verified, with deletion scope and retention wording ready for reviewers |
| Smoking-related content | Content rating and description avoid unsupported health/safety guarantees |

## 8. Official reference links

- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/15402170
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
