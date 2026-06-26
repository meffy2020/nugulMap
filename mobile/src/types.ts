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
