# NugulMap Season 2 product plan

## Goal

Season 2 turns the map from a smoking-zone lookup into a live city context map:

- Show how crowded Lotte World/Jamsil is, including an approximate people range when a live provider supplies it.
- Rank places that are hot right now across Seoul.
- Show popups, events, festivals, markets, concerts, and exhibitions from public APIs or crawler output.
- Keep the same contract available on web, Expo, iOS native, and Android native surfaces.

## User questions to support

| User question | Product response | Data path | Current implementation |
| --- | --- | --- | --- |
| `롯데월드 사람 많아?` | Focus Lotte World/Jamsil and show crowd level plus estimated people range. | `TELECOM_CROWD_API` first, then Seoul CityData, then static seed. | `/api/insights/hotplaces?keyword=롯데` and `/api/insights/map` |
| `지금 핫한 곳` | Rank current hot places across Seoul. | Live crowd signals sorted by crowd rank and people range; static hot-rank fallback only when live is unavailable. | `hot-now` keyword routed through hotplace intent. |
| `요즘 핫한 팝업 행사` | Show popups/events/festivals on the map. | Seoul Culture API, KTO TourAPI, and `POPUP_TRENDS_FILE` crawler output. | `/api/insights/events` and `/api/insights/map` |
| `요즘 뜨는 장소 어디` | Treat generic trending-place queries as event discovery, not only smoking-zone search. | Same public event/crawler path as popup/event queries. | Web, Expo, iOS, and Android search intent checks. |

## Provider contract

### Live crowd providers

At least one live crowd source is required for production launch:

- `TELECOM_CROWD_API_KEY` + `TELECOM_CROWD_URL_TEMPLATE`
- `SEOUL_CITYDATA_API_KEY`

`TELECOM_CROWD_URL_TEMPLATE` must be configurable because telecom and commercial providers expose different URL shapes. The backend supports these placeholders:

- `{placeId}`
- `{placeName}`
- `{seoulAreaCode}`
- `{seoulAreaName}`
- `{lat}`
- `{lng}`
- `{apiKey}`

The provider response must expose a crowd level and, for the Lotte World/Jamsil proof case, an estimated people count or range. Supported response aliases include `crowdLevel`, `congestionLevel`, `estimatedMinPeople`, `estimatedMaxPeople`, `populationRange`, `peopleRange`, and `visitorCount`.

### Public event providers

At least one public event source is required for production launch:

- `SEOUL_CULTURE_API_KEY`
- `KTO_TOUR_API_KEY`
- Fresh `SEOUL_CULTURE_API` or `KTO_TOUR_API` records collected into `POPUP_TRENDS_FILE`

The collector can ingest direct JSON APIs, Seoul Culture OpenAPI payloads, RSS/Atom feeds, JSON-LD event pages, embedded Next.js data, Open Graph event pages with configured coordinates, and manual seed records.

## API contract

| Endpoint | Purpose | Required proof |
| --- | --- | --- |
| `GET /api/insights/hotplaces` | Hotplace/crowd list filtered by keyword and bounds. | Lotte World query returns a live source with people range in live mode; fallback is explicitly labeled `STATIC_FALLBACK`. |
| `GET /api/insights/events` | Popup/event/festival list filtered by keyword and bounds. | Generic event queries return current or upcoming public/crawled records. |
| `GET /api/insights/map` | Combined hotplace, event, and provider status payload for map bootstrapping. | Web/mobile/native clients can render both layers without extra endpoint choreography. |
| `GET /api/insights/status` | Provider readiness and data-quality status. | Missing keys, missing telecom URL template, stale popup data, and public API state are visible to clients. |

## UX contract

- Web quick actions: `롯데월드 혼잡도`, `지금 핫한 곳`, `성수 팝업`.
- Map layers: zones, hotplaces, events, or all.
- Hotplace chips show source and people range when available.
- Event chips show type, period, source, and route action.
- Status text distinguishes `통신사 장소 혼잡도`, `서울 실시간 도시데이터`, `서울 문화행사 API`, `한국관광공사 TourAPI`, and `크롤링 팝업 트렌드`.
- Missing production keys must not look like success; the UI should label fallback/static data clearly.

## Verification gates

Local implementation is ready when all of these pass:

```bash
python3 backend/api-server/scripts/check-season2-readiness.py --check-compose --check-ui-parity --strict-popup-quality
backend/api-server/scripts/smoke-season2-mock-live.sh
cd frontend && npm run test:season2-intent && npm run lint && npm run build
cd backend/api-server && ./gradlew test --tests com.neogulmap.neogul_map.service.EventInsightServiceTest --tests com.neogulmap.neogul_map.service.HotplaceServiceTest --tests com.neogulmap.neogul_map.service.InsightStatusServiceTest --tests com.neogulmap.neogul_map.controller.InsightControllerTest
```

Production/live completion is ready only when this passes against real configured providers:

```bash
python3 backend/api-server/scripts/check-season2-readiness.py --require-live --strict-popup-quality --probe-live-provider --probe-public-event-provider --public-event-probe-limit 5
```

## Known launch blocker

The code can run in local/mock-live mode without production credentials. The Season 2 goal is not fully complete until a real live crowd provider and a real public event provider are configured and the strict live gate passes.
