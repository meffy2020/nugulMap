import { useEffect, useRef, useState } from "react"
import AsyncStorage from "@react-native-async-storage/async-storage"
import * as Location from "expo-location"
import { fetchZonesByBounds } from "../../../services/nugulApi"
import { KOREA_DEFAULT_REGION, type MapBounds, type MapRegion, type SmokingZone } from "../../../types"

const FAVORITES_KEY = "@nugulmap:favorites:v1"
const REGION_REFRESH_DEBOUNCE_MS = 700
const REGION_MOVE_THRESHOLD = 0.00015
const REGION_DELTA_THRESHOLD = 0.00008

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
  const saved = await AsyncStorage.getItem(FAVORITES_KEY)
  if (!saved) return new Set()

  try {
    const parsed = JSON.parse(saved) as number[]
    return new Set(parsed)
  } catch {
    return new Set()
  }
}

async function resolveInitialRegion(defaultRegion: MapRegion): Promise<MapRegion> {
  const locationPermission = await Location.requestForegroundPermissionsAsync()
  if (locationPermission.status !== "granted") return defaultRegion

  const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
  return parseToRegion(current.coords.latitude, current.coords.longitude)
}

export function useZoneExplorer() {
  const [region, setRegion] = useState<MapRegion>(KOREA_DEFAULT_REGION)
  const [zones, setZones] = useState<SmokingZone[]>([])
  const [selectedZone, setSelectedZone] = useState<SmokingZone | null>(null)
  const [detailZone, setDetailZone] = useState<SmokingZone | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set())
  const loadingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    void (async () => {
      setIsLoading(true)

      const [nextRegion, nextFavoriteIds] = await Promise.all([
        resolveInitialRegion(region),
        loadFavoriteIds(),
      ])

      setRegion(nextRegion)
      setFavoriteIds(nextFavoriteIds)
      const nextZones = await fetchZonesByBounds(toBounds(nextRegion))
      setZones(nextZones)
      setIsLoading(false)
    })()

    return () => {
      if (loadingTimer.current) {
        clearTimeout(loadingTimer.current)
      }
    }
  }, [])

  const refreshZones = async (nextRegion: MapRegion) => {
    setIsLoading(true)
    const nextZones = await fetchZonesByBounds(toBounds(nextRegion))
    setZones(nextZones)
    setIsLoading(false)
  }

  const handleRegionChangeComplete = (nextRegion: MapRegion) => {
    if (!isMeaningfulRegionChange(region, nextRegion)) {
      return
    }

    setRegion(nextRegion)

    if (loadingTimer.current) {
      clearTimeout(loadingTimer.current)
    }

    loadingTimer.current = setTimeout(() => {
      void refreshZones(nextRegion)
    }, REGION_REFRESH_DEBOUNCE_MS)
  }

  const toggleFavorite = async (id: number) => {
    const next = new Set(favoriteIds)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }

    setFavoriteIds(next)
    await AsyncStorage.setItem(FAVORITES_KEY, JSON.stringify(Array.from(next)))
  }

  const openDetail = (zone: SmokingZone) => {
    setDetailZone(zone)
    setSelectedZone(zone)
  }

  const closeDetail = () => {
    setDetailZone(null)
    setSelectedZone(null)
  }

  const prependZone = (zone: SmokingZone) => {
    setZones((prev) => [zone, ...prev.filter((item) => item.id !== zone.id)])
  }

  const refreshCurrentRegion = async () => {
    await refreshZones(region)
  }

  return {
    region,
    zones,
    selectedZone,
    detailZone,
    isLoading,
    favoriteIds,
    handleRegionChangeComplete,
    toggleFavorite,
    openDetail,
    closeDetail,
    prependZone,
    refreshCurrentRegion,
  }
}
