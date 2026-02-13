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

export interface UserProfile {
  id: number
  email: string
  nickname: string
  profileImage: string | null
  createdAt: string
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
