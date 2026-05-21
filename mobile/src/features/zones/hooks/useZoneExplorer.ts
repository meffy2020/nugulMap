import { useCallback, useEffect, useRef, useState } from "react"
import AsyncStorage from "@react-native-async-storage/async-storage"
import * as Location from "expo-location"
import { fetchZonesByBounds } from "../../../services/nugulApi"
import { KOREA_DEFAULT_REGION, type MapBounds, type MapRegion, type SmokingZone } from "../../../types"

const FAVORITES_KEY = "@nugulmap:favorites:v1"
const REGION_REFRESH_DEBOUNCE_MS = 700
const REGION_MOVE_THRESHOLD = 0.00015
const REGION_DELTA_THRESHOLD = 0.00008
const INITIAL_LOAD_ERROR_MESSAGE = "초기 흡연구역 정보를 불러오지 못했습니다."
const REGION_REFRESH_ERROR_MESSAGE = "주변 흡연구역 새로고침에 실패했습니다."

function parseToRegion(latitude: number, longitude: number): MapRegion {
  return {
    latitude,
    longitude,
    latitudeDelta: 0.05,
    longitudeDelta: 0.05,
  }
}

function toBounds(region: MapRegion): MapBounds {
  return {
    minLat: region.latitude - region.latitudeDelta / 2,
    maxLat: region.latitude + region.latitudeDelta / 2,
    minLng: region.longitude - region.longitudeDelta / 2,
    maxLng: region.longitude + region.longitudeDelta / 2,
  }
}

function isMeaningfulRegionChange(prev: MapRegion, next: MapRegion): boolean {
  return (
    Math.abs(prev.latitude - next.latitude) > REGION_MOVE_THRESHOLD ||
    Math.abs(prev.longitude - next.longitude) > REGION_MOVE_THRESHOLD ||
    Math.abs(prev.latitudeDelta - next.latitudeDelta) > REGION_DELTA_THRESHOLD ||
    Math.abs(prev.longitudeDelta - next.longitudeDelta) > REGION_DELTA_THRESHOLD
  )
}

async function loadFavoriteIds(): Promise<Set<number>> {
  try {
    const saved = await AsyncStorage.getItem(FAVORITES_KEY)
    if (!saved) return new Set()

    const parsed = JSON.parse(saved) as number[]
    return new Set(parsed)
  } catch {
    return new Set()
  }
}

async function resolveInitialRegion(defaultRegion: MapRegion): Promise<MapRegion> {
  try {
    const locationPermission = await Location.requestForegroundPermissionsAsync()
    if (locationPermission.status !== "granted") return defaultRegion

    const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
    return parseToRegion(current.coords.latitude, current.coords.longitude)
  } catch {
    return defaultRegion
  }
}

export function useZoneExplorer() {
  const [region, setRegion] = useState<MapRegion>(KOREA_DEFAULT_REGION)
  const [zones, setZones] = useState<SmokingZone[]>([])
  const [selectedZone, setSelectedZone] = useState<SmokingZone | null>(null)
  const [detailZone, setDetailZone] = useState<SmokingZone | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set())
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const loadingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const regionRef = useRef<MapRegion>(KOREA_DEFAULT_REGION)

  useEffect(() => {
    let isMounted = true

    void (async () => {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const [nextRegion, nextFavoriteIds] = await Promise.all([
          resolveInitialRegion(KOREA_DEFAULT_REGION),
          loadFavoriteIds(),
        ])

        if (!isMounted) return
        regionRef.current = nextRegion
        setRegion(nextRegion)
        setFavoriteIds(nextFavoriteIds)

        const nextZones = await fetchZonesByBounds(toBounds(nextRegion))
        if (!isMounted) return
        setZones(nextZones)
      } catch (error) {
        console.warn("zone explorer initial load failed", error)
        if (isMounted) {
          setErrorMessage(INITIAL_LOAD_ERROR_MESSAGE)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    })()

    return () => {
      isMounted = false
      if (loadingTimer.current) {
        clearTimeout(loadingTimer.current)
      }
    }
  }, [])

  const refreshZones = useCallback(async (nextRegion: MapRegion) => {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const nextZones = await fetchZonesByBounds(toBounds(nextRegion))
      setZones(nextZones)
    } catch (error) {
      console.warn("zone refresh failed", error)
      setErrorMessage(REGION_REFRESH_ERROR_MESSAGE)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const handleRegionChangeComplete = useCallback(
    (nextRegion: MapRegion) => {
      if (!isMeaningfulRegionChange(regionRef.current, nextRegion)) {
        return
      }

      regionRef.current = nextRegion
      setRegion(nextRegion)

      if (loadingTimer.current) {
        clearTimeout(loadingTimer.current)
      }

      loadingTimer.current = setTimeout(() => {
        void refreshZones(nextRegion)
      }, REGION_REFRESH_DEBOUNCE_MS)
    },
    [refreshZones],
  )

  const toggleFavorite = useCallback(
    async (id: number) => {
      const next = new Set(favoriteIds)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }

      setFavoriteIds(next)
      try {
        await AsyncStorage.setItem(FAVORITES_KEY, JSON.stringify(Array.from(next)))
      } catch (error) {
        console.warn("favorite save failed", error)
      }
    },
    [favoriteIds],
  )

  const openDetail = useCallback((zone: SmokingZone) => {
    setDetailZone(zone)
    setSelectedZone(zone)
  }, [])

  const closeDetail = useCallback(() => {
    setDetailZone(null)
    setSelectedZone(null)
  }, [])

  const prependZone = useCallback((zone: SmokingZone) => {
    setZones((prev) => [zone, ...prev.filter((item) => item.id !== zone.id)])
  }, [])

  const refreshCurrentRegion = useCallback(async () => {
    await refreshZones(regionRef.current)
  }, [refreshZones])

  const clearError = useCallback(() => {
    setErrorMessage(null)
  }, [])

  return {
    region,
    zones,
    selectedZone,
    detailZone,
    isLoading,
    favoriteIds,
    errorMessage,
    handleRegionChangeComplete,
    toggleFavorite,
    openDetail,
    closeDetail,
    prependZone,
    refreshCurrentRegion,
    clearError,
  }
}
