export interface SmokingZone {
  id: number
  region: string
  type: string
  subtype: string
  description: string
  size?: string
  date?: string
  latitude: number
  longitude: number
  address: string
  user: string
  image: string | null
}

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
  freshnessStatus?: string | null
  ageSeconds?: number | null
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
  detailUrl?: string | null
  collectedAt?: string | null
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

export type RecommendationIntent = "CAFE" | "RESTAURANT" | "DATE"
export type RecommendationDataStatus = "READY" | "PARTIAL" | "PROVIDER_NOT_CONFIGURED" | "PROVIDER_UNAVAILABLE"
export type RecommendationPersonalizationMode = "CONTEXT_ONLY" | "PERSONALIZED"
export type RecommendationEventType = "DETAIL_OPEN" | "SAVE" | "UNSAVE" | "ROUTE" | "NOT_INTERESTED"

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

export interface InsightProviderStatus {
  configured: boolean
  qualityStatus: string
  lastSuccessAt: string | null
  lastFailureAt: string | null
  detail: string
}

export interface PopupTrendStatus {
  fileConfigured: boolean
  fileExists: boolean
  recordCount: number
  latestCollectedAt: string | null
  qualityStatus: string
  detail: string
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
  popupTrends: PopupTrendStatus
  checkedAt: string
}

export interface UserProfile {
  id: number
  email: string
  nickname: string | null
  profileImage: string | null
  createdAt: string
}

export interface ZoneReview {
  id: number
  zoneId: number
  authorId: number | null
  authorNickname: string
  authorEmail?: string
  authorProfileImage: string | null
  content: string
  createdAt: string
  updatedAt: string
}

export interface MapRegion {
  latitude: number
  longitude: number
  latitudeDelta: number
  longitudeDelta: number
}

export interface MapBounds {
  minLat: number
  maxLat: number
  minLng: number
  maxLng: number
}

export const KOREA_DEFAULT_REGION: MapRegion = {
  latitude: 37.5665,
  longitude: 126.978,
  latitudeDelta: 0.05,
  longitudeDelta: 0.05,
}
