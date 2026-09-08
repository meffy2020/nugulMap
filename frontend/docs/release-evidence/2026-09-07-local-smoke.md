# Local smoking-only web smoke — 2026-09-07

This screenshot is **failed map evidence, not release-ready map evidence**.

Candidate: production Next.js build served at `http://127.0.0.1:3197`, Chrome headless, 390 × 844 viewport, fresh browser context without location permission. Search input: `시청`.

- The shell contains search, profile, current location and zone submission. No discovery controls appeared.
- No requests to `/api/insights`, `/api/recommendations` or `/api/location-letters` occurred on startup or search.
- No page JavaScript exceptions occurred.
- The Kakao SDK request failed in Chrome with `net::ERR_BLOCKED_BY_ORB` because the provider returned JSON instead of JavaScript.
- Direct SDK response using the configured public key and local Referer: **HTTP 401**, `AccessDeniedError`, `domain mismatched! caller=http://127.0.0.1:3197. check out registered web domains.`
- The same key with Referer `https://nugulmap.com/` returned **HTTP 200**, `text/javascript`.
- The public key is present and has the expected format. This is a **local domain registration mismatch**, not a missing/invalid key or a build CSP error. No key values are included in this evidence.
- The browser also reported `net::ERR_FAILED` for the authenticated user lookup from the local origin; that request was not diagnosed further here.

Actual rendered map tiles, markers, location denial fallback, and directions remain unverified in this local run. Repeat on the registered release origin before claiming the browser map is ready. No provider domain settings were modified and no deployment was made.

Local production HTTP checks returned 200 with `X-Content-Type-Options: nosniff` for `/`, `/privacy`, `/account-deletion`, `/connected-accounts`, `/support?zoneId=123`, and `/terms`. The support page retained the zone number in the message field.

Screenshot: `2026-09-07-local-smoking-map-blocked.png`.
