"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle, useCallback } from "react"
import type { ReactNode } from "react"
import { CalendarDays, ExternalLink, Flame, MapPin, Loader2, Navigation, RefreshCw } from "lucide-react"
import { Button } from "@/components/ui/button"
import Script from "next/script"

import {
  fetchMapInsights,
  fetchZones,
  type EventInsight,
  type Hotplace,
  type HotplaceInsight,
  type InsightStatus,
  type SmokingZone,
  type TrendEvent,
  getImageUrl,
} from "@/lib/api"
import Image from "next/image"
import { cn } from "@/lib/utils"
import { Drawer } from "vaul"

declare global {
  interface Window {
    kakao: any
  }
}

interface LocationMarker extends SmokingZone {}

type InsightMarker =
  | { kind: "hotplace"; item: Hotplace }
  | { kind: "event"; item: TrendEvent }

type Season2LayerMode = "all" | "zones" | "hotplaces" | "events"
type InsightRefreshState = "idle" | "refreshing" | "error"

const INSIGHT_REFRESH_INTERVAL_MS = 180_000
const SEOUL_TIME_ZONE = "Asia/Seoul"

export interface MapContainerRef {
  handleZoneCreated: (zone: SmokingZone) => void
  centerOnLocation: (lat: number, lng: number) => void
  getCenter: () => { lat: number, lng: number }
  focusHotplace: (place: Hotplace) => void
  focusEvent: (event: TrendEvent) => void
  showHotplaceResults: (insight: HotplaceInsight) => void
  showEventResults: (insight: EventInsight) => void
}

export const MapContainer = forwardRef<MapContainerRef>((props, ref) => {
  const [selectedMarker, setSelectedMarker] = useState<LocationMarker | null>(null)
  const [selectedInsight, setSelectedInsight] = useState<InsightMarker | null>(null)
  const [zones, setZones] = useState<SmokingZone[]>([])
  const [hotplaceInsight, setHotplaceInsight] = useState<HotplaceInsight | null>(null)
  const [eventInsight, setEventInsight] = useState<EventInsight | null>(null)
  const [insightStatus, setInsightStatus] = useState<InsightStatus | null>(null)
  const [layerMode, setLayerMode] = useState<Season2LayerMode>("all")
  const [loading, setLoading] = useState(true)
  const [insightLoading, setInsightLoading] = useState(false)
  const [refreshState, setRefreshState] = useState<InsightRefreshState>("idle")
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null)
  const [kakaoLoaded, setKakaoLoaded] = useState(false)
  const [showMapError, setShowMapError] = useState(false)
  const mapRef = useRef<HTMLDivElement>(null)
  const [mapInstance, setMapInstance] = useState<any>(null)
  const clustererRef = useRef<any>(null)
  const currentMarkers = useRef<any[]>([])
  const currentInsightOverlays = useRef<any[]>([])
  const selectedInsightRef = useRef<InsightMarker | null>(null)
  const insightAbortControllerRef = useRef<AbortController | null>(null)
  const insightRequestIdRef = useRef(0)

  const KAKAO_APP_KEY = process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY
  const MARKER_IMAGE_SRC = process.env.NEXT_PUBLIC_MAP_MARKER_IMAGE_SRC || "/images/pin.png"
  const showSmokingZones = layerMode === "all" || layerMode === "zones"
  const showHotplaces = layerMode === "all" || layerMode === "hotplaces"
  const showEvents = layerMode === "all" || layerMode === "events"

  useEffect(() => {
    selectedInsightRef.current = selectedInsight
  }, [selectedInsight])

  // 1. 이미 스크립트가 로드되었는지 확인
  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      setKakaoLoaded(true)
    }

    // 3초 후 강제 로딩 종료 (무한 로딩 방지 안전장치)
    const timer = setTimeout(() => {
      setLoading(false)
    }, 3000)

    return () => clearTimeout(timer)
  }, [])

  // 2. 지도 초기화 로직
  useEffect(() => {
    if (!kakaoLoaded || !mapRef.current || !window.kakao || !window.kakao.maps) {
      return
    }

    const initMap = () => {
      if (!mapRef.current) {
        setLoading(false)
        return
      }

      try {
        // 이미 자식 노드(지도 엘리먼트)가 있다면 초기화 스킵
        if (mapRef.current.hasChildNodes() && mapInstance) {
          setLoading(false)
          return
        }

        const options = {
          center: new window.kakao.maps.LatLng(37.5665, 126.978),
          level: 3,
        }
        const map = new window.kakao.maps.Map(mapRef.current!, options)
        setMapInstance(map)
        
        if (window.kakao.maps.MarkerClusterer) {
          clustererRef.current = new window.kakao.maps.MarkerClusterer({
            map: map,
            averageCenter: true,
            minLevel: 6,
          })
        }
        setShowMapError(false)
      } catch {
        setShowMapError(true)
      } finally {
        setLoading(false)
      }
    }

    window.kakao.maps.load(initMap)
  }, [kakaoLoaded]) // mapInstance를 의존성에서 제거하여 중복 호출 방지

  const loadZonesInView = useCallback(async () => {
    if (!mapInstance) return
    const requestId = insightRequestIdRef.current + 1
    insightRequestIdRef.current = requestId
    insightAbortControllerRef.current?.abort()
    const controller = new AbortController()
    insightAbortControllerRef.current = controller

    try {
      const bounds = mapInstance.getBounds()
      const sw = bounds.getSouthWest()
      const ne = bounds.getNorthEast()
      const mapBounds = {
        minLat: sw.getLat(),
        maxLat: ne.getLat(),
        minLng: sw.getLng(),
        maxLng: ne.getLng(),
      }
      setInsightLoading(true)
      setRefreshState("refreshing")
      const [zonesData, mapInsight] = await Promise.all([
        fetchZones(mapBounds.minLat, mapBounds.maxLat, mapBounds.minLng, mapBounds.maxLng),
        fetchMapInsights(8, 8, mapBounds, undefined, { signal: controller.signal }),
      ])
      if (requestId !== insightRequestIdRef.current) return

      const selected = selectedInsightRef.current
      setZones(zonesData)
      setHotplaceInsight(selected?.kind === "hotplace" ? mergeFocusedHotplaceInsight(selected.item, mapInsight.hotplaces) : mapInsight.hotplaces)
      setEventInsight(selected?.kind === "event" ? mergeFocusedEventInsight(selected.item, mapInsight.events) : mapInsight.events)
      setInsightStatus(mapInsight.status)
      setLastRefreshedAt(mapInsight.updatedAt || new Date().toISOString())
      setRefreshState("idle")
    } catch (error) {
      if (isAbortError(error)) return
      if (requestId === insightRequestIdRef.current) {
        setRefreshState("error")
      }
    } finally {
      if (requestId === insightRequestIdRef.current) {
        setInsightLoading(false)
      }
    }
  }, [mapInstance])

  useEffect(() => {
    if (mapInstance) void loadZonesInView()
  }, [mapInstance, loadZonesInView])

  useEffect(() => {
    if (!mapInstance) return

    const refreshVisibleMap = () => {
      if (document.visibilityState === "visible") {
        void loadZonesInView()
      }
    }
    const intervalId = window.setInterval(refreshVisibleMap, INSIGHT_REFRESH_INTERVAL_MS)
    document.addEventListener("visibilitychange", refreshVisibleMap)

    return () => {
      window.clearInterval(intervalId)
      document.removeEventListener("visibilitychange", refreshVisibleMap)
    }
  }, [mapInstance, loadZonesInView])

  useEffect(() => {
    return () => {
      insightRequestIdRef.current += 1
      insightAbortControllerRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    if (!showSmokingZones) {
      setSelectedMarker(null)
    }
    if (selectedInsight?.kind === "hotplace" && !showHotplaces) {
      selectedInsightRef.current = null
      setSelectedInsight(null)
    }
    if (selectedInsight?.kind === "event" && !showEvents) {
      selectedInsightRef.current = null
      setSelectedInsight(null)
    }
  }, [selectedInsight, showEvents, showHotplaces, showSmokingZones])

  useEffect(() => {
    if (!mapInstance) return
    const handleMapChange = () => void loadZonesInView()
    window.kakao.maps.event.addListener(mapInstance, 'dragend', handleMapChange)
    window.kakao.maps.event.addListener(mapInstance, 'zoom_changed', handleMapChange)
    return () => {
      window.kakao.maps.event.removeListener(mapInstance, 'dragend', handleMapChange)
      window.kakao.maps.event.removeListener(mapInstance, 'zoom_changed', handleMapChange)
    }
  }, [mapInstance, loadZonesInView])

  const selectHotplace = (place: Hotplace) => {
    const selection: InsightMarker = { kind: "hotplace", item: place }
    selectedInsightRef.current = selection
    setSelectedInsight(selection)
    if (mapInstance) {
      mapInstance.panTo(new window.kakao.maps.LatLng(place.latitude, place.longitude))
    }
  }

  const selectEvent = (event: TrendEvent) => {
    const selection: InsightMarker = { kind: "event", item: event }
    selectedInsightRef.current = selection
    setSelectedInsight(selection)
    if (mapInstance) {
      mapInstance.panTo(new window.kakao.maps.LatLng(event.latitude, event.longitude))
    }
  }

  const fitInsightResults = (items: Array<{ latitude: number; longitude: number }>) => {
    if (!mapInstance || !window.kakao?.maps || items.length === 0) return
    if (items.length === 1) {
      mapInstance.setCenter(new window.kakao.maps.LatLng(items[0].latitude, items[0].longitude))
      mapInstance.setLevel(3)
      return
    }

    const bounds = new window.kakao.maps.LatLngBounds()
    items.forEach((item) => {
      bounds.extend(new window.kakao.maps.LatLng(item.latitude, item.longitude))
    })
    mapInstance.setBounds(bounds)
  }

  useEffect(() => {
    if (mapInstance && Array.isArray(zones)) {
      if (clustererRef.current) clustererRef.current.clear()
      currentMarkers.current.forEach(m => m.setMap(null))
      currentMarkers.current = []
      if (!showSmokingZones) return
      
      const markers = zones.map((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        const imageSrc = MARKER_IMAGE_SRC
        const imageSize = new window.kakao.maps.Size(40, 40)
        const imageOption = { offset: new window.kakao.maps.Point(20, 40) }
        const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize, imageOption)
        
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          image: markerImage,
          title: zoneTitle(zone)
        })

        window.kakao.maps.event.addListener(marker, "click", () => {
          selectedInsightRef.current = null
          setSelectedInsight(null)
          setSelectedMarker(zone as LocationMarker)
          mapInstance.panTo(markerPosition)
        })
        return marker
      })

      if (clustererRef.current) {
        clustererRef.current.addMarkers(markers)
      } else {
        markers.forEach(m => m.setMap(mapInstance))
      }
      currentMarkers.current = markers
    }
  }, [mapInstance, zones, MARKER_IMAGE_SRC, showSmokingZones])

  useEffect(() => {
    if (!mapInstance || !window.kakao?.maps) return
    currentInsightOverlays.current.forEach((overlay) => overlay.setMap(null))
    currentInsightOverlays.current = []

    const overlays: any[] = []
    if (showHotplaces) {
      ;(hotplaceInsight?.places || []).slice(0, 8).forEach((place) => {
        overlays.push(createInsightOverlay(mapInstance, "hotplace", place, () => selectHotplace(place)))
      })
    }
    if (showEvents) {
      ;(eventInsight?.events || []).slice(0, 8).forEach((event) => {
        overlays.push(createInsightOverlay(mapInstance, "event", event, () => selectEvent(event)))
      })
    }
    currentInsightOverlays.current = overlays
  }, [mapInstance, hotplaceInsight, eventInsight, showHotplaces, showEvents])

  useImperativeHandle(ref, () => ({
    handleZoneCreated: (newZone: SmokingZone) => {
      setZones((prev) => [...prev, newZone])
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(newZone.latitude, newZone.longitude))
        window.setTimeout(() => void loadZonesInView(), 200)
      }
    },
    centerOnLocation: (lat: number, lng: number) => {
      if (mapInstance) {
        setSelectedMarker(null)
        selectedInsightRef.current = null
        setSelectedInsight(null)
        mapInstance.setCenter(new window.kakao.maps.LatLng(lat, lng))
        mapInstance.setLevel(3)
        window.setTimeout(() => void loadZonesInView(), 200)
      }
    },
    focusHotplace: (place: Hotplace) => {
      setLayerMode("hotplaces")
      setSelectedMarker(null)
      const selection: InsightMarker = { kind: "hotplace", item: place }
      selectedInsightRef.current = selection
      setSelectedInsight(selection)
      setHotplaceInsight((current) => ({
        places: mergeHotplaceFirst(place, current?.places || []),
        dataFreshness: current?.dataFreshness || "VERIFIED_SELECTION",
        updatedAt: current?.updatedAt || place.updatedAt || "",
        sources: current?.sources || [],
      }))
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(place.latitude, place.longitude))
        mapInstance.setLevel(3)
        window.setTimeout(() => void loadZonesInView(), 200)
      }
    },
    focusEvent: (event: TrendEvent) => {
      setLayerMode("events")
      setSelectedMarker(null)
      const selection: InsightMarker = { kind: "event", item: event }
      selectedInsightRef.current = selection
      setSelectedInsight(selection)
      setEventInsight((current) => ({
        events: mergeEventFirst(event, current?.events || []),
        dataFreshness: current?.dataFreshness || "VERIFIED_SELECTION",
        updatedAt: current?.updatedAt || "",
        sources: current?.sources || [],
      }))
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(event.latitude, event.longitude))
        mapInstance.setLevel(3)
        window.setTimeout(() => void loadZonesInView(), 200)
      }
    },
    showHotplaceResults: (insight: HotplaceInsight) => {
      setLayerMode("hotplaces")
      setSelectedMarker(null)
      selectedInsightRef.current = null
      setSelectedInsight(null)
      setHotplaceInsight(insight)
      fitInsightResults(insight.places)
    },
    showEventResults: (insight: EventInsight) => {
      setLayerMode("events")
      setSelectedMarker(null)
      selectedInsightRef.current = null
      setSelectedInsight(null)
      setEventInsight(insight)
      fitInsightResults(insight.events)
    },
    getCenter: () => {
      if (mapInstance) {
        const center = mapInstance.getCenter()
        return { lat: center.getLat(), lng: center.getLng() }
      }
      return { lat: 37.5665, lng: 126.978 }
    },
  }))

  const handleDirections = () => {
    if (!selectedMarker) return
    const url = `https://map.kakao.com/link/to/${encodeURIComponent(zoneTitle(selectedMarker))},${selectedMarker.latitude},${selectedMarker.longitude}`
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="relative w-full h-full">
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false&libraries=services,clusterer`}
        strategy="afterInteractive"
        onLoad={() => setKakaoLoaded(true)}
        onError={() => setShowMapError(true)}
      />
      
      {showMapError ? (
        <div className="w-full h-full flex items-center justify-center bg-muted text-center p-6 text-zinc-500">
          <p>지도를 불러올 수 없습니다.</p>
        </div>
      ) : (
        <div ref={mapRef} className="w-full h-full" />
      )}

      {loading && (
        <div className="absolute inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center z-20 pointer-events-none">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
        </div>
      )}

      <div className="absolute left-4 top-[calc(env(safe-area-inset-top,0px)_+_7.75rem)] z-30 w-[calc(100vw_-_2rem)] max-w-[390px] rounded-2xl border border-white/70 bg-white/95 p-3 shadow-xl">
        <div className="mb-2 flex items-center justify-between gap-2">
          <p className="truncate text-[11px] font-black text-zinc-500">현재 지도</p>
          <div className="flex min-w-0 items-center gap-1">
            <p className="truncate text-[11px] font-black text-zinc-500">{formatInsightStatus(insightStatus)}</p>
            <button
              type="button"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-500 disabled:opacity-50"
              onClick={() => void loadZonesInView()}
              disabled={refreshState === "refreshing"}
              aria-label="지도 혼잡도와 행사 정보 새로고침"
            >
              <RefreshCw className={cn("h-4 w-4", refreshState === "refreshing" && "animate-spin")} aria-hidden="true" />
            </button>
          </div>
        </div>
        <p
          className={cn(
            "mb-2 text-[11px] font-bold",
            refreshState === "error" ? "text-red-700" : "text-zinc-500"
          )}
          role="status"
          aria-live="polite"
          aria-atomic="true"
        >
          {formatRefreshStatus(refreshState, lastRefreshedAt)}
        </p>
        <div className="grid grid-cols-4 gap-1 rounded-xl bg-zinc-100 p-1">
          <LayerModeButton mode="all" activeMode={layerMode} onSelect={setLayerMode} icon={<MapPin className="h-3.5 w-3.5" />}>
            전체
          </LayerModeButton>
          <LayerModeButton mode="zones" activeMode={layerMode} onSelect={setLayerMode} icon={<MapPin className="h-3.5 w-3.5" />}>
            흡연
          </LayerModeButton>
          <LayerModeButton mode="hotplaces" activeMode={layerMode} onSelect={setLayerMode} icon={<Flame className="h-3.5 w-3.5" />}>
            핫플
          </LayerModeButton>
          <LayerModeButton mode="events" activeMode={layerMode} onSelect={setLayerMode} icon={<CalendarDays className="h-3.5 w-3.5" />}>
            팝업
          </LayerModeButton>
        </div>

        {showHotplaces ? (
          <div className="mt-3">
            <InsightPanelHeader
              icon={<Flame className="h-4 w-4 text-orange-600" />}
              title="지금 핫한 곳"
              status={formatHotplacePanelStatus(hotplaceInsight, insightLoading)}
            />
            <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
              {(hotplaceInsight?.places || []).slice(0, 6).map((place) => (
                <button
                  key={place.id}
                  className="min-w-[128px] rounded-xl bg-orange-50 px-3 py-2 text-left"
                  onClick={() => selectHotplace(place)}
                >
                  <p className="truncate text-xs font-black text-zinc-900">{place.name}</p>
                  <p className="mt-1 truncate text-[11px] font-bold text-zinc-500">{formatCrowdLabel(place)}</p>
                  <p className="mt-1 truncate text-[10px] font-bold text-zinc-500">{formatHotplaceFreshness(place)}</p>
                </button>
              ))}
              {!hotplaceInsight?.places?.length && (
                <div className="min-w-[128px] rounded-xl bg-zinc-50 px-3 py-2 text-xs font-bold text-zinc-500">
                  주변 핫플 확인 중
                </div>
              )}
            </div>
          </div>
        ) : null}

        {showEvents && (eventInsight?.events || []).length ? (
          <div className="mt-3 border-t border-zinc-100 pt-3">
            <InsightPanelHeader
              icon={<CalendarDays className="h-4 w-4 text-sky-700" />}
              title="팝업·행사·축제"
              status={formatEventPanelStatus(eventInsight)}
            />
            <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
              {(eventInsight?.events || []).slice(0, 6).map((event) => (
                <button
                  key={event.id}
                  className="min-w-[144px] rounded-xl bg-sky-50 px-3 py-2 text-left"
                  onClick={() => selectEvent(event)}
                >
                  <p className="truncate text-xs font-black text-zinc-900">{event.title}</p>
                  <p className="mt-1 truncate text-[11px] font-bold text-zinc-500">{formatEventLabel(event)}</p>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </div>

      {selectedInsight ? (
        <div
          className="absolute bottom-28 left-4 right-4 z-40 mx-auto max-w-md rounded-2xl border border-zinc-100 bg-white/95 p-4 shadow-2xl"
          role="dialog"
          aria-label={`${insightTitle(selectedInsight)} 상세 정보`}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-base font-black text-zinc-950">{insightTitle(selectedInsight)}</p>
              <p className="mt-1 text-sm font-black text-primary">{insightLabel(selectedInsight)}</p>
            </div>
            <button
              className="rounded-full bg-zinc-100 px-3 py-1 text-xs font-black text-zinc-500"
              onClick={() => {
                selectedInsightRef.current = null
                setSelectedInsight(null)
              }}
            >
              닫기
            </button>
          </div>
          <p className="mt-2 line-clamp-2 text-sm font-semibold text-zinc-500">{insightAddress(selectedInsight) || "주소 정보 없음"}</p>
          {selectedInsight.kind === "event" ? (
            <EventScheduleDetail event={selectedInsight.item} />
          ) : insightDetail(selectedInsight) ? (
            <p className="mt-2 line-clamp-2 text-sm font-bold text-zinc-800">{insightDetail(selectedInsight)}</p>
          ) : null}
          <div className="mt-4 flex items-center justify-between gap-3">
            <p className="truncate text-xs font-black text-zinc-500">{insightSource(selectedInsight)}</p>
            <div className="flex shrink-0 items-center gap-2">
              {selectedInsight.kind === "event" ? (
                safeHttpUrl(selectedInsight.item.detailUrl) ? (
                  <Button asChild size="sm" variant="outline" className="rounded-xl font-black">
                    <a
                      href={safeHttpUrl(selectedInsight.item.detailUrl) || undefined}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <ExternalLink className="h-4 w-4" aria-hidden="true" />
                      원문 보기
                    </a>
                  </Button>
                ) : (
                  <span className="rounded-xl bg-zinc-100 px-3 py-2 text-xs font-black text-zinc-500">원문 없음</span>
                )
              ) : null}
              <Button
                size="sm"
                className="rounded-xl font-black"
                onClick={() => openInsightDirections(selectedInsight)}
              >
                <Navigation className="mr-1 h-4 w-4" />
                길찾기
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <Drawer.Root 
        open={!!selectedMarker} 
        onOpenChange={(open) => !open && setSelectedMarker(null)}
      >
        <Drawer.Portal>
          <Drawer.Overlay className="fixed inset-0 bg-black/40 z-[100]" />
          <Drawer.Content className="bg-white flex flex-col rounded-t-[24px] max-h-[85dvh] fixed bottom-0 left-0 right-0 z-[101] outline-none">
            <div className="mx-auto w-12 h-1.5 flex-shrink-0 rounded-full bg-zinc-200 my-4" />
            
            <div className="px-5 pb-8 overflow-y-auto">
              {selectedMarker && (
                <div className="max-w-md mx-auto space-y-5">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h2 className="text-xl font-bold text-zinc-900 leading-tight">
                        {zoneTitle(selectedMarker)}
                      </h2>
                      {selectedMarker.type && (
                        <span className={cn(
                          "px-2 py-0.5 rounded text-[10px] font-bold shrink-0",
                          (selectedMarker.type.includes("INDOOR") || selectedMarker.type.includes("실내")) ? "bg-blue-50 text-blue-600" :
                          (selectedMarker.type.includes("BOOTH") || selectedMarker.type.includes("부스")) ? "bg-green-50 text-green-600" :
                          (selectedMarker.type.includes("OPEN") || selectedMarker.type.includes("개방") || selectedMarker.type.includes("실외")) ? "bg-orange-50 text-orange-600" :
                          "hidden"
                        )}>
                          {(selectedMarker.type.includes("INDOOR") || selectedMarker.type.includes("실내")) ? "실내" :
                           (selectedMarker.type.includes("BOOTH") || selectedMarker.type.includes("부스")) ? "부스" : "개방형"}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-zinc-500">{selectedMarker.address}</p>
                  </div>

                  {(selectedMarker.imageUrl || selectedMarker.image) && (
                    <div className="relative aspect-[16/10] w-full rounded-2xl overflow-hidden bg-zinc-50 border border-zinc-100">
                      <Image
                        src={getImageUrl(selectedMarker.imageUrl || selectedMarker.image) || "/placeholder.svg"}
                        alt={zoneTitle(selectedMarker)}
                        fill
                        className="object-cover"
                      />
                    </div>
                  )}

                  <div className="bg-zinc-50 rounded-2xl p-4 flex items-start gap-3">
                    <MapPin className="w-5 h-5 text-zinc-400 shrink-0 mt-0.5" />
                    <p className="text-sm text-zinc-600 leading-relaxed">
                      {selectedMarker.description || "등록된 상세 설명이 없습니다."}
                    </p>
                  </div>

                  <Button 
                    onClick={handleDirections}
                    className="w-full h-14 text-lg font-bold rounded-2xl gap-2 shadow-lg shadow-zinc-100" 
                    size="lg"
                  >
                    <Navigation className="w-5 h-5" />
                    길찾기 시작
                  </Button>
                </div>
              )}
            </div>
            <div className="h-[env(safe-area-inset-bottom)]" />
          </Drawer.Content>
        </Drawer.Portal>
      </Drawer.Root>
    </div>
  )
})

MapContainer.displayName = "MapContainer"

function InsightPanelHeader({ icon, title, status }: { icon: ReactNode; title: string; status: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-2">
        {icon}
        <p className="truncate text-sm font-black text-zinc-900">{title}</p>
      </div>
      <span className="rounded-full bg-zinc-100 px-2 py-1 text-[10px] font-black text-zinc-500">{status}</span>
    </div>
  )
}

function EventScheduleDetail({ event }: { event: TrendEvent }) {
  return (
    <div className="mt-3 rounded-xl bg-sky-50 p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-black text-sky-900">일정</p>
        <span className="rounded-full bg-white px-2 py-1 text-[10px] font-black text-sky-800">
          {formatEventTiming(event)}
        </span>
      </div>
      <dl className="mt-2 grid grid-cols-2 gap-2 text-xs">
        <div>
          <dt className="font-bold text-zinc-500">시작일</dt>
          <dd className="mt-1 font-black text-zinc-900">{formatEventDate(event.startDate)}</dd>
        </div>
        <div>
          <dt className="font-bold text-zinc-500">종료일</dt>
          <dd className="mt-1 font-black text-zinc-900">{formatEventDate(event.endDate)}</dd>
        </div>
      </dl>
      <p className="mt-2 text-[11px] font-bold text-zinc-500">
        {event.collectedAt ? `수집 ${formatDateTime(event.collectedAt)}` : "수집 시각 없음"}
      </p>
    </div>
  )
}

function LayerModeButton({
  mode,
  activeMode,
  onSelect,
  icon,
  children,
}: {
  mode: Season2LayerMode
  activeMode: Season2LayerMode
  onSelect: (mode: Season2LayerMode) => void
  icon: ReactNode
  children: ReactNode
}) {
  const isActive = activeMode === mode
  return (
    <button
      type="button"
      className={cn(
        "flex h-8 min-w-0 items-center justify-center gap-1 rounded-lg px-2 text-[11px] font-black transition",
        isActive ? "bg-white text-zinc-950 shadow-sm" : "text-zinc-500"
      )}
      onClick={() => onSelect(mode)}
      aria-pressed={isActive}
    >
      {icon}
      <span className="truncate">{children}</span>
    </button>
  )
}

function createInsightOverlay(map: any, kind: "hotplace" | "event", item: Hotplace | TrendEvent, onClick: () => void) {
  const container = document.createElement("button")
  container.type = "button"
  container.className =
    kind === "hotplace"
      ? "rounded-full border border-white bg-orange-600 px-3 py-1.5 text-xs font-black text-white shadow-lg"
      : "rounded-full border border-white bg-sky-700 px-3 py-1.5 text-xs font-black text-white shadow-lg"
  container.textContent = kind === "hotplace" ? (item as Hotplace).name : (item as TrendEvent).title
  container.setAttribute(
    "aria-label",
    kind === "hotplace"
      ? `${(item as Hotplace).name}, ${formatCrowdLabel(item as Hotplace)}`
      : `${(item as TrendEvent).title}, ${formatEventLabel(item as TrendEvent)}`
  )
  container.addEventListener("click", onClick)

  const overlay = new window.kakao.maps.CustomOverlay({
    position: new window.kakao.maps.LatLng(item.latitude, item.longitude),
    content: container,
    yAnchor: 1.25,
    zIndex: kind === "hotplace" ? 4 : 3,
  })
  overlay.setMap(map)
  return overlay
}

function formatCrowdLabel(place: Hotplace): string {
  if ((place.freshnessStatus === "CURRENT" || place.freshnessStatus === "DELAYED") && place.crowdLevel && place.crowdLevel !== "UNKNOWN") {
    const count =
      place.estimatedMinPeople != null && place.estimatedMaxPeople != null
        ? ` · ${place.estimatedMinPeople.toLocaleString()}-${place.estimatedMaxPeople.toLocaleString()}명`
        : ""
    const crowd = `${place.crowdLevel}${count}`
    return place.freshnessStatus === "DELAYED" ? `측정 지연 · ${crowd}` : crowd
  }
  if (place.freshnessStatus === "STATIC") return "참고 후보"
  if (place.freshnessStatus === "STALE") return "오래된 관측"
  return "혼잡도 상태 확인 필요"
}

function formatHotplaceFreshness(place: Hotplace): string {
  const source = place.source === "TELECOM_CROWD"
    ? "통신사"
    : place.source === "SEOUL_CITYDATA" ? "서울 도시데이터" : "공급자"
  const observedAt = place.updatedAt ? `관측 ${formatDateTime(place.updatedAt)}` : "관측 시각 없음"
  switch (place.freshnessStatus) {
    case "CURRENT":
      return `현재 · ${source} · ${observedAt}`
    case "DELAYED":
      return `측정 지연 · ${observedAt}`
    case "STATIC":
      return "정적 참고 후보"
    case "STALE":
      return `오래된 관측 · ${observedAt}`
    default:
      return `측정 상태 확인 필요 · ${observedAt}`
  }
}

function formatEventLabel(event: TrendEvent): string {
  const kind = event.kind === "popup" ? "팝업" : event.kind === "festival" ? "축제" : "행사"
  return `${kind} · ${formatEventDateRange(event)}`
}

function formatInsightStatus(status: InsightStatus | null): string {
  if (!status) {
    return "확인 중"
  }

  const hotplace = formatProviderMode(status.hotplaceMode, "혼잡")
  const events = formatEventStatus(status)
  return [hotplace, events].filter(Boolean).join(" · ")
}

function formatEventStatus(status: InsightStatus): string {
  const hasLiveTourApi = status.ktoTourApi?.qualityStatus === "OK"
  const hasSeoulCultureApi = status.seoulCultureApi?.qualityStatus === "OK"
  const liveEventApiLabel = formatLiveEventApiLabel(hasLiveTourApi, hasSeoulCultureApi)
  const popupCount = status.popupTrends?.qualityStatus === "OK" ? status.popupTrends.recordCount : 0

  if (liveEventApiLabel && popupCount > 0) {
    return `${liveEventApiLabel} · 팝업 ${popupCount}건`
  }
  if (liveEventApiLabel) {
    return liveEventApiLabel
  }
  if (status.seoulCultureApiKeyConfigured) {
    return "팝업 확인 중"
  }
  if (status.popupTrends?.qualityStatus === "STALE") {
    return "팝업 오래된 데이터"
  }
  if (popupCount > 0) {
    return `팝업 ${popupCount}건`
  }
  return formatProviderMode(status.eventMode, "행사")
}

function formatLiveEventApiLabel(hasLiveTourApi: boolean, hasSeoulCultureApi: boolean): string | null {
  return hasLiveTourApi || hasSeoulCultureApi ? "행사 최신 정보" : null
}

function formatProviderMode(mode: string, label: string): string {
  switch (mode) {
    case "LIVE_READY":
      return `${label} 실시간`
    case "LIVE_OR_CRAWLED_READY":
      return `${label} 최신 정보`
    case "LIVE_CONFIGURED_UNVERIFIED":
      return `${label} 확인중`
    case "LIVE_CONFIGURED_ERROR":
      return `${label} 갱신 지연`
    default:
      return `${label} 후보`
  }
}

function formatHotplacePanelStatus(insight: HotplaceInsight | null, isLoading: boolean): string {
  if (isLoading) {
    return "갱신 중"
  }
  const places = insight?.places || []
  const currentCount = places.filter((place) => place.freshnessStatus === "CURRENT").length
  const delayedCount = places.filter((place) => place.freshnessStatus === "DELAYED").length
  const staticCount = places.filter((place) => place.freshnessStatus === "STATIC").length
  const staleCount = places.filter((place) => place.freshnessStatus === "STALE").length
  const unknownCount = places.length - currentCount - delayedCount - staticCount - staleCount
  const parts = [
    currentCount > 0 ? `현재 ${currentCount}` : null,
    delayedCount > 0 ? `지연 ${delayedCount}` : null,
    staticCount > 0 ? `후보 ${staticCount}` : null,
    staleCount > 0 ? `오래됨 ${staleCount}` : null,
    unknownCount > 0 ? `확인 필요 ${unknownCount}` : null,
  ].filter(Boolean)
  return parts.length ? parts.join(" · ") : "참고 후보"
}

function formatEventPanelStatus(insight: EventInsight | null): string {
  if (insight?.dataFreshness?.includes("STALE")) {
    return "오래된 데이터"
  }
  if (insight?.dataFreshness === "LIVE_OR_PARTIAL") {
    return "최신 정보"
  }
  if (insight?.dataFreshness === "CRAWLED_OR_PARTIAL") {
    return "확인된 정보"
  }
  return "참고 후보"
}

function insightTitle(selection: InsightMarker): string {
  return selection.kind === "hotplace" ? selection.item.name : selection.item.title
}

function insightLabel(selection: InsightMarker): string {
  return selection.kind === "hotplace" ? formatCrowdLabel(selection.item) : formatEventLabel(selection.item)
}

function insightAddress(selection: InsightMarker): string {
  return selection.item.address || ""
}

function insightDetail(selection: InsightMarker): string | undefined {
  if (selection.kind === "event") {
    return undefined
  }

  const crowdMessage = selection.item.crowdMessage
  const safeCrowdMessage = crowdMessage && !/(API|키가|URL|연동|설정)/i.test(crowdMessage)
    ? crowdMessage
    : undefined
  const parts = [
    safeCrowdMessage,
    selection.item.updatedAt ? `관측 ${formatDateTime(selection.item.updatedAt)}` : undefined,
  ].filter(Boolean)
  return parts.length ? parts.join(" · ") : undefined
}

function insightSource(selection: InsightMarker): string {
  if (selection.kind === "event") {
    if (selection.item.source === "KTO_TOUR_API") {
      return "한국관광공사"
    }
    if (selection.item.source === "SEOUL_CULTURE_API") {
      return "서울시 문화행사"
    }
    return selection.item.source === "CRAWLED_POPUP_TREND" ? "공식 팝업 안내" : "이벤트 참고 후보"
  }
  if (selection.item.source === "TELECOM_CROWD") {
    return "장소 혼잡 정보"
  }
  return selection.item.source === "SEOUL_CITYDATA" ? "서울시 실시간 인구" : "핫플 참고 후보"
}

function openInsightDirections(selection: InsightMarker) {
  const title = insightTitle(selection)
  const { latitude, longitude } = selection.item
  window.open(`https://map.kakao.com/link/to/${encodeURIComponent(title)},${latitude},${longitude}`, "_blank", "noopener,noreferrer")
}

function formatRefreshStatus(state: InsightRefreshState, lastRefreshedAt: string | null): string {
  if (state === "refreshing") {
    return "혼잡도와 행사 정보를 갱신 중입니다."
  }
  if (state === "error") {
    return lastRefreshedAt
      ? `갱신 실패 · ${formatDateTime(lastRefreshedAt)} 결과를 유지합니다.`
      : "갱신 실패 · 기존 지도 결과를 유지합니다."
  }
  return lastRefreshedAt
    ? `마지막 지도 갱신 ${formatDateTime(lastRefreshedAt)}`
    : "혼잡도와 행사 정보를 확인 중입니다."
}

function formatEventDateRange(event: TrendEvent): string {
  const startDate = normalizeIsoDate(event.startDate)
  const endDate = normalizeIsoDate(event.endDate)
  if (startDate && endDate) {
    return startDate === endDate ? formatEventDate(startDate) : `${formatEventDate(startDate)}–${formatEventDate(endDate)}`
  }
  if (startDate) return `${formatEventDate(startDate)}부터`
  if (endDate) return `${formatEventDate(endDate)}까지`
  return event.period?.trim() || "일정 미정"
}

function formatEventTiming(event: TrendEvent): string {
  const today = seoulToday()
  const startDate = normalizeIsoDate(event.startDate)
  const endDate = normalizeIsoDate(event.endDate)

  if (endDate && today > endDate) return "종료"
  if (startDate && today < startDate) return `D-${daysBetween(today, startDate)}`
  if ((startDate && today >= startDate) || (!startDate && endDate && today <= endDate)) return "진행 중"
  return "일정 확인 필요"
}

function formatEventDate(value: string | null): string {
  const normalized = normalizeIsoDate(value)
  if (!normalized) return "미정"
  const [year, month, day] = normalized.split("-").map(Number)
  return `${year}. ${month}. ${day}.`
}

function normalizeIsoDate(value: string | null): string | null {
  if (!value) return null
  const trimmed = value.trim()
  if (/^\d{8}$/.test(trimmed)) {
    return `${trimmed.slice(0, 4)}-${trimmed.slice(4, 6)}-${trimmed.slice(6, 8)}`
  }
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}-${match[2]}-${match[3]}` : null
}

function seoulToday(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: SEOUL_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function daysBetween(from: string, to: string): number {
  const [fromYear, fromMonth, fromDay] = from.split("-").map(Number)
  const [toYear, toMonth, toDay] = to.split("-").map(Number)
  return Math.max(
    0,
    Math.round(
      (Date.UTC(toYear, toMonth - 1, toDay) - Date.UTC(fromYear, fromMonth - 1, fromDay)) / 86_400_000
    )
  )
}

function formatDateTime(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: SEOUL_TIME_ZONE,
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(parsed)
}

function safeHttpUrl(value: string | null | undefined): string | null {
  if (!value?.trim()) return null
  try {
    const url = new URL(value.trim())
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : null
  } catch {
    return null
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError"
}

function mergeFocusedHotplaceInsight(place: Hotplace, insight: HotplaceInsight): HotplaceInsight {
  return {
    ...insight,
    places: mergeHotplaceFirst(place, insight.places || []),
  }
}

function mergeFocusedEventInsight(event: TrendEvent, insight: EventInsight): EventInsight {
  return {
    ...insight,
    events: mergeEventFirst(event, insight.events || []),
  }
}

function mergeHotplaceFirst(place: Hotplace, places: Hotplace[]): Hotplace[] {
  return [place, ...places.filter((item) => item.id !== place.id)].slice(0, 8)
}

function mergeEventFirst(event: TrendEvent, events: TrendEvent[]): TrendEvent[] {
  return [event, ...events.filter((item) => item.id !== event.id)].slice(0, 8)
}

function zoneTitle(zone: SmokingZone): string {
  return zone.name || zone.address || `흡연구역 ${zone.id}`
}
