// 백엔드의 ZoneResponse DTO와 일치하는 타입 정의
export interface SmokingZone {
  id: number
  name?: string
  region: string
  type: string
  subtype: string
  description: string
  size?: string
  latitude: number
  longitude: number
  address: string
  user: string
  image: string | null
  imageUrl?: string | null
}

export type HotplaceFreshnessStatus = "CURRENT" | "DELAYED" | "STATIC" | "UNKNOWN" | "STALE"

export interface Hotplace {
  id: string
  name: string
  category: string
  crowdLevel: string
  crowdMessage: string
  estimatedMinPeople: number | null
  estimatedMaxPeople: number | null
  latitude: number
  longitude: number
  address: string
  source: string
  sourcePlaceCode: string
  updatedAt: string | null
  freshnessStatus: HotplaceFreshnessStatus
  ageSeconds: number | null
}

export interface HotplaceInsight {
  places: Hotplace[]
  dataFreshness: string
  updatedAt: string
  sources: string[]
}

export interface TrendEvent {
  id: string
  title: string
  kind: string
  period: string
  startDate: string | null
  endDate: string | null
  latitude: number
  longitude: number
  address: string
  imageUrl: string | null
  source: string
  sourceContentId: string
  detailUrl: string | null
  collectedAt: string | null
}

export interface EventInsight {
  events: TrendEvent[]
  dataFreshness: string
  updatedAt: string
  sources: string[]
}

export interface MapInsight {
  hotplaces: HotplaceInsight
  events: EventInsight
  status: InsightStatus | null
  updatedAt: string
}

export interface InsightStatus {
  seoulCityDataKeyConfigured: boolean
  telecomCrowdKeyConfigured: boolean
  telecomCrowdUrlTemplateConfigured: boolean
  ktoTourApiKeyConfigured: boolean
  seoulCultureApiKeyConfigured: boolean
  hotplaceMode: string
  eventMode: string
  seoulCityData: InsightProviderStatus
  telecomCrowd: InsightProviderStatus
  ktoTourApi: InsightProviderStatus
  seoulCultureApi: InsightProviderStatus
  popupTrends: {
    fileConfigured: boolean
    fileExists: boolean
    recordCount: number
    latestCollectedAt: string | null
    qualityStatus: string
    detail: string
  }
  checkedAt: string
}

export interface InsightProviderStatus {
  configured: boolean
  qualityStatus: string
  lastSuccessAt: string | null
  lastFailureAt: string | null
  detail: string
}

export interface FetchMapInsightsOptions {
  signal?: AbortSignal
}

export type RecommendationIntent = "CAFE" | "RESTAURANT" | "DATE"
export type RecommendationDataStatus = "READY" | "PARTIAL" | "PROVIDER_NOT_CONFIGURED" | "PROVIDER_UNAVAILABLE"
export type RecommendationPersonalizationMode = "CONTEXT_ONLY" | "PERSONALIZED"
export type RecommendationEventType = "IMPRESSION" | "DETAIL_OPEN" | "SAVE" | "UNSAVE" | "ROUTE" | "NOT_INTERESTED"

export interface RecommendedPlace {
  id: string
  provider: string
  name: string
  category: string
  address: string
  latitude: number
  longitude: number
  distanceMeters: number | null
  score: number
  reasonCodes: string[]
  sourceUrl: string | null
}

export interface PlaceRecommendation {
  requestId: string
  modelVersion: string
  dataStatus: RecommendationDataStatus
  personalizationMode: RecommendationPersonalizationMode
  intent: RecommendationIntent
  eligibleCandidateCount: number
  generatedAt: string
  items: RecommendedPlace[]
}

export interface RecommendationPreference {
  personalizationEnabled: boolean
  preferredTags: string[]
  dislikedTags: string[]
  budgetLevel: "ANY" | "LOW" | "MEDIUM" | "HIGH" | null
  maxDistanceMeters: number | null
  updatedAt: string | null
}

export interface SavedRecommendationItem {
  id: number
  itemType: "PLACE" | "EVENT" | "HOTPLACE" | "COURSE"
  provider: string
  itemId: string
  title: string
  category: string | null
  lat: number
  lng: number
  sourceUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface FetchRecommendationOptions {
  radiusMeters?: number
  limit?: number
  signal?: AbortSignal
}

// Zone 생성 시 요청 DTO에 맞는 타입 정의
export type CreateZonePayload = Omit<SmokingZone, "id" | "image">

// 환경 변수가 있으면 최우선 사용, 없으면 브라우저에서는 운영 API 주소, 서버에서는 도커 내부 주소 사용
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL
  ? process.env.NEXT_PUBLIC_API_BASE_URL
  : typeof window !== "undefined"
    ? "https://api.nugulmap.com"
    : "http://nginx"

export async function fetchZones(minLat: number, maxLat: number, minLng: number, maxLng: number): Promise<SmokingZone[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/zones/bounds?minLat=${minLat}&maxLat=${maxLat}&minLng=${minLng}&maxLng=${maxLng}`,
  )
  if (!response.ok) {
    throw new Error(`Zone request failed: ${response.status}`)
  }

  const result = await response.json()
  if (result.success && result.data && Array.isArray(result.data.zones)) {
    return result.data.zones
  }
  throw new Error("Zone response format is invalid")
}

export async function fetchMapInsights(
  hotplaceLimit = 8,
  eventLimit = 8,
  bounds?: { minLat: number; maxLat: number; minLng: number; maxLng: number },
  keyword?: string,
  options: FetchMapInsightsOptions = {},
): Promise<MapInsight> {
  const params = new URLSearchParams({
    hotplaceLimit: String(hotplaceLimit),
    eventLimit: String(eventLimit),
  })
  if (keyword?.trim()) {
    params.set("keyword", keyword.trim())
  }
  if (bounds) {
    params.set("minLat", String(bounds.minLat))
    params.set("maxLat", String(bounds.maxLat))
    params.set("minLng", String(bounds.minLng))
    params.set("maxLng", String(bounds.maxLng))
  }

  const response = await fetch(`${API_BASE_URL}/api/insights/map?${params.toString()}`, {
    cache: "no-store",
    signal: options.signal,
  })
  if (!response.ok) throw new Error(`Map insights request failed: ${response.status}`)

  const result = await response.json()
  const data = result?.data || {}
  return {
    hotplaces: {
      places: Array.isArray(data.hotplaces?.places) ? data.hotplaces.places.map(normalizeHotplace) : [],
      dataFreshness: data.hotplaces?.dataFreshness || "UNAVAILABLE",
      updatedAt: data.hotplaces?.updatedAt || "",
      sources: Array.isArray(data.hotplaces?.sources) ? data.hotplaces.sources : [],
    },
    events: {
      events: Array.isArray(data.events?.events) ? data.events.events.map(normalizeTrendEvent) : [],
      dataFreshness: data.events?.dataFreshness || "UNAVAILABLE",
      updatedAt: data.events?.updatedAt || "",
      sources: Array.isArray(data.events?.sources) ? data.events.sources : [],
    },
    status: data.status || null,
    updatedAt: data.updatedAt || "",
  }
}

export async function fetchRecommendedPlaces(
  intent: RecommendationIntent,
  latitude: number,
  longitude: number,
  options: FetchRecommendationOptions = {},
): Promise<PlaceRecommendation> {
  const params = new URLSearchParams({
    intent,
    lat: String(latitude),
    lng: String(longitude),
    radiusMeters: String(options.radiusMeters ?? 3_000),
    limit: String(options.limit ?? 8),
  })
  const response = await fetch(`${API_BASE_URL}/api/recommendations/places?${params.toString()}`, {
    cache: "no-store",
    credentials: "include",
    signal: options.signal,
  })
  return recommendationData<PlaceRecommendation>(response, "추천 장소를 불러오지 못했습니다.")
}

export async function fetchRecommendationPreference(): Promise<RecommendationPreference> {
  const response = await fetch(`${API_BASE_URL}/api/recommendations/preferences`, {
    cache: "no-store",
    credentials: "include",
  })
  return recommendationData<RecommendationPreference>(response, "추천 설정을 불러오지 못했습니다.")
}

export async function updateRecommendationPreference(
  preference: Omit<RecommendationPreference, "updatedAt">,
): Promise<RecommendationPreference> {
  const response = await fetch(`${API_BASE_URL}/api/recommendations/preferences`, {
    method: "PUT",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(preference),
  })
  return recommendationData<RecommendationPreference>(response, "추천 설정을 저장하지 못했습니다.")
}

export async function fetchSavedRecommendationItems(): Promise<SavedRecommendationItem[]> {
  const response = await fetch(`${API_BASE_URL}/api/recommendations/saved-items`, {
    cache: "no-store",
    credentials: "include",
  })
  return recommendationData<SavedRecommendationItem[]>(response, "저장한 추천을 불러오지 못했습니다.")
}

export async function saveRecommendedPlace(place: RecommendedPlace): Promise<SavedRecommendationItem> {
  const response = await fetch(`${API_BASE_URL}/api/recommendations/saved-items`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      itemType: "PLACE",
      provider: place.provider,
      itemId: place.id,
      title: place.name,
      category: place.category,
      lat: place.latitude,
      lng: place.longitude,
      sourceUrl: place.sourceUrl,
    }),
  })
  return recommendationData<SavedRecommendationItem>(response, "추천 장소를 저장하지 못했습니다.")
}

export async function unsaveRecommendedPlace(place: RecommendedPlace): Promise<void> {
  const params = new URLSearchParams({
    itemType: "PLACE",
    provider: place.provider,
    itemId: place.id,
  })
  const response = await fetch(`${API_BASE_URL}/api/recommendations/saved-items?${params.toString()}`, {
    method: "DELETE",
    credentials: "include",
  })
  if (!response.ok) {
    throw new Error(await recommendationError(response, "저장한 추천을 삭제하지 못했습니다."))
  }
}

export async function recordRecommendationInteraction(
  recommendation: Pick<PlaceRecommendation, "requestId" | "modelVersion" | "intent">,
  place: RecommendedPlace,
  eventType: RecommendationEventType,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/recommendations/interactions`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      requestId: recommendation.requestId,
      itemType: "PLACE",
      provider: place.provider,
      itemId: place.id,
      eventType,
      intent: recommendation.intent,
      contextRegion: null,
      modelVersion: recommendation.modelVersion,
      occurredAt: null,
    }),
  })
  if (!response.ok) {
    throw new Error(await recommendationError(response, "추천 행동을 기록하지 못했습니다."))
  }
}

async function recommendationData<T>(response: Response, fallbackMessage: string): Promise<T> {
  const result = await response.json().catch(() => null)
  if (!response.ok || result?.success !== true || result.data == null) {
    throw new Error(result?.message || fallbackMessage)
  }
  return result.data as T
}

async function recommendationError(response: Response, fallbackMessage: string): Promise<string> {
  const result = await response.json().catch(() => null)
  return typeof result?.message === "string" && result.message.trim() ? result.message : fallbackMessage
}

function normalizeHotplace(value: Record<string, unknown>): Hotplace {
  const source = typeof value.source === "string" ? value.source : ""
  const supportedStatuses: HotplaceFreshnessStatus[] = ["CURRENT", "DELAYED", "STATIC", "UNKNOWN", "STALE"]
  const rawStatus = typeof value.freshnessStatus === "string" ? value.freshnessStatus.toUpperCase() : ""
  const freshnessStatus = supportedStatuses.includes(rawStatus as HotplaceFreshnessStatus)
    ? rawStatus as HotplaceFreshnessStatus
    : source.startsWith("STATIC") ? "STATIC" : "UNKNOWN"
  const ageSeconds = typeof value.ageSeconds === "number" && Number.isFinite(value.ageSeconds)
    ? Math.max(0, value.ageSeconds)
    : null

  return {
    ...(value as unknown as Hotplace),
    freshnessStatus,
    ageSeconds,
  }
}

function normalizeTrendEvent(value: Record<string, unknown>): TrendEvent {
  return {
    ...(value as unknown as TrendEvent),
    detailUrl: typeof value.detailUrl === "string" ? value.detailUrl : null,
    collectedAt: typeof value.collectedAt === "string" ? value.collectedAt : null,
  }
}

/**
 * 새로운 흡연구역을 서버에 생성합니다.
 * @param zoneData - 생성할 흡연구역의 데이터
 * @param imageFile - 업로드할 이미지 파일 (선택 사항)
 * @returns 생성된 SmokingZone 객체
 */
export async function createZone(zoneData: CreateZonePayload, imageFile?: File): Promise<SmokingZone> {
  const formData = new FormData()

  formData.append("data", new Blob([JSON.stringify(zoneData)], { type: "application/json" }))

  if (imageFile) {
    formData.append("image", imageFile)
  }

  const response = await fetch(`${API_BASE_URL}/api/zones`, {
    method: "POST",
    body: formData,
    credentials: "include",
  })

  const result = await response.json().catch(() => null)
  if (!response.ok || result?.success !== true || !result?.data?.zone) {
    throw new Error(result?.message || "흡연구역 등록에 실패했습니다.")
  }

  return result.data.zone
}

// ----------------------------------------------------------------------------
// 사용자 관련 API 함수
// ----------------------------------------------------------------------------

// 백엔드의 UserResponse DTO와 일치하는 타입 정의
export interface UserProfile {
  id: number
  email: string
  nickname: string
  profileImage: string | null
  createdAt: string
}

/**
 * 사용자 프로필 정보를 가져옵니다.
 * @param userId - 조회할 사용자의 ID
 * @returns UserProfile 객체
 */
export async function fetchUserProfile(userId: number): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}`, {
    credentials: "include",
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 사용자 닉네임을 업데이트합니다.
 * @param userId - 업데이트할 사용자의 ID
 * @param nickname - 새로운 닉네임
 * @returns 업데이트된 UserProfile 객체
 */
export async function updateUserNickname(userId: number, nickname: string): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ nickname }),
    credentials: "include",
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 회원가입 완료 처리 (닉네임 및 프로필 이미지 등록)
 */
export async function completeSignup(nickname: string, profileImage?: File): Promise<any> {
  const formData = new FormData()
  formData.append("nickname", nickname)
  if (profileImage) {
    formData.append("profileImage", profileImage)
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
    method: "POST",
    body: formData,
    credentials: "include",
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 현재 로그인된 사용자 정보 가져오기
 */
export async function getCurrentUser(): Promise<UserProfile | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      credentials: "include",
    })

    if (!response.ok) {
      return null
    }

    const result = await response.json()
    return result.data.user
  } catch (err) {
    console.error("Failed to get current user:", err)
    return null
  }
}

export async function logoutCurrentUser(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`Logout failed: ${response.status}`)
  }
}

/**
 * 이미지 URL 생성 헬퍼
 */
export function getImageUrl(imagePath: string | null | undefined): string | null {
  if (!imagePath) return null
  if (imagePath.startsWith("http")) return imagePath
  return `${API_BASE_URL}/api/images/${imagePath}`
}

/**
 * 사용자 등록 장소 가져오기
 */
export async function fetchUserZones(): Promise<SmokingZone[]> {
  const response = await fetch(`${API_BASE_URL}/api/zones/my`, {
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }

  const result = await response.json()
  return result.data.zones
}

/**
 * 장소 검색 (키워드)
 */
export async function searchZones(keyword: string, lat?: number, lng?: number): Promise<SmokingZone[]> {
  let url = `${API_BASE_URL}/api/zones/search?keyword=${encodeURIComponent(keyword)}`
  if (lat && lng) {
    url += `&lat=${lat}&lng=${lng}`
  }

  const response = await fetch(url, {
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }

  const result = await response.json()
  return result.data.zones
}

/**
 * 흡연구역 삭제
 */
export async function deleteZone(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/zones/${id}`, {
    method: "DELETE",
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }
}

export interface ZoneRequest {
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  size: string
  address: string
  user: string
  image?: string
}

class ApiService {
  async createZone(zoneData: ZoneRequest, imageFile?: File): Promise<SmokingZone> {
    const payload: CreateZonePayload = {
      region: zoneData.region,
      type: zoneData.type,
      subtype: zoneData.subtype,
      description: zoneData.description,
      latitude: zoneData.latitude,
      longitude: zoneData.longitude,
      address: zoneData.address,
      user: zoneData.user,
    }
    return createZone(payload, imageFile)
  }

  async getAllZones(latitude?: number, longitude?: number, radius?: number): Promise<SmokingZone[]> {
    const delta = radius ? Math.max(radius / 111_000, 0.01) : 0.05
    if (latitude !== undefined && longitude !== undefined) {
      return fetchZones(latitude - delta, latitude + delta, longitude - delta, longitude + delta)
    }
    // 기본 위치로 서울 시청 사용
    return fetchZones(37.5665 - delta, 37.5665 + delta, 126.978 - delta, 126.978 + delta)
  }
}

export const apiService = new ApiService()
