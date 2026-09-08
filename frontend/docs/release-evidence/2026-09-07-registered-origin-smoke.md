# Registered-origin candidate browser verification — 2026-09-07

This is a local production-build test, **not a deployment**. An isolated headless Chrome context intercepted GET requests for `https://nugulmap.com/` and served the local Next.js candidate on port 3197. Kakao SDK/tiles and `api.nugulmap.com` requests used real remote services. No DNS, provider settings, public website or production data changed.

Final candidate after dependency patches and all UX fixes:

- Initial level 4 map rendered real Kakao tiles and three smoking markers.
- Search for 종각역 moved the map and a subsequent bounds response returned six zones.
- Marker click opened the named details dialog with address, report link and directions button.
- Directions click generated an HTTPS Kakao `/link/to/` URL. `window.open` was intercepted to inspect that handler; actual external browser routing was not tested in this run.
- Two bounds calls, 25 tile requests, zero discovery API calls and zero page JavaScript exceptions.
- The context had no granted geolocation permission. Manual place search and marker selection worked.
- Localhost-only SDK loading still returns a provider domain mismatch; this registered-origin check isolates that configuration issue from candidate rendering.

Images: `2026-09-07-registered-origin-home.png`, `2026-09-07-registered-origin-detail.png`.

**Data warning:** the live API contains exact demo seed records. The captured details include a known demo. These images prove UI behavior only and must not be submitted as verified-place/store screenshots. Quarantine and source/field revalidation must precede final captures.
