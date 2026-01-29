"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X, MapPin, Loader2, Navigation } from "lucide-react"
import { Button } from "@/components/ui/button"
import Script from "next/script"

import { fetchZones, type SmokingZone, getImageUrl } from "@/lib/api"
import Image from "next/image"
import { cn } from "@/lib/utils"
import { Drawer } from "vaul"

declare global {
  interface Window {
    kakao: any
  }
}

interface LocationMarker extends SmokingZone {}

export interface MapContainerRef {
  handleZoneCreated: (zone: SmokingZone) => void
  centerOnLocation: (lat: number, lng: number) => void
  getCenter: () => { lat: number, lng: number }
}

export const MapContainer = forwardRef<MapContainerRef>((props, ref) => {
  const [selectedMarker, setSelectedMarker] = useState<LocationMarker | null>(null)
  const [zones, setZones] = useState<SmokingZone[]>([])
  const [loading, setLoading] = useState(true)
  const [kakaoLoaded, setKakaoLoaded] = useState(false)
  const [showMapError, setShowMapError] = useState(false)
  const mapRef = useRef<HTMLDivElement>(null)
  const [mapInstance, setMapInstance] = useState<any>(null)
  const clustererRef = useRef<any>(null)
  const currentMarkers = useRef<any[]>([])

  const KAKAO_APP_KEY = process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY

  // 1. 이미 스크립트가 로드되었는지 확인
  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      console.log("[v0] Kakao maps already loaded")
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
      } catch (err) {
        console.error("[v0] 카카오맵 생성 실패:", err)
      } finally {
        setLoading(false)
      }
    }

    window.kakao.maps.load(initMap)
  }, [kakaoLoaded]) // mapInstance를 의존성에서 제거하여 중복 호출 방지

  const loadZonesInView = async () => {
    if (!mapInstance) return
    try {
      const bounds = mapInstance.getBounds()
      const sw = bounds.getSouthWest()
      const ne = bounds.getNorthEast()
      const zonesData = await fetchZones(sw.getLat(), ne.getLat(), sw.getLng(), ne.getLng())
      setZones(zonesData)
    } catch (err) {
      console.error("[v0] Failed to fetch zones:", err)
    }
  }

  useEffect(() => {
    if (mapInstance) loadZonesInView()
  }, [mapInstance])

  useEffect(() => {
    if (!mapInstance) return
    const handleMapChange = () => loadZonesInView()
    window.kakao.maps.event.addListener(mapInstance, 'dragend', handleMapChange)
    window.kakao.maps.event.addListener(mapInstance, 'zoom_changed', handleMapChange)
    return () => {
      window.kakao.maps.event.removeListener(mapInstance, 'dragend', handleMapChange)
      window.kakao.maps.event.removeListener(mapInstance, 'zoom_changed', handleMapChange)
    }
  }, [mapInstance])

  useEffect(() => {
    if (mapInstance && Array.isArray(zones)) {
      if (clustererRef.current) clustererRef.current.clear()
      currentMarkers.current.forEach(m => m.setMap(null))
      
      const markers = zones.map((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        const imageSrc = "/images/pin.png"
        const imageSize = new window.kakao.maps.Size(40, 40)
        const imageOption = { offset: new window.kakao.maps.Point(20, 40) }
        const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize, imageOption)
        
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          image: markerImage,
          title: zone.name
        })

        window.kakao.maps.event.addListener(marker, "click", () => {
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
  }, [mapInstance, zones])

  useImperativeHandle(ref, () => ({
    handleZoneCreated: (newZone: SmokingZone) => {
      setZones((prev) => [...prev, newZone])
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(newZone.latitude, newZone.longitude))
      }
    },
    centerOnLocation: (lat: number, lng: number) => {
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(lat, lng))
        mapInstance.setLevel(3)
      }
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
    const url = `https://map.kakao.com/link/to/${selectedMarker.name},${selectedMarker.latitude},${selectedMarker.longitude}`
    window.open(url, '_blank')
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
                        {selectedMarker.name}
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

                  {selectedMarker.imageUrl && (
                    <div className="relative aspect-[16/10] w-full rounded-2xl overflow-hidden bg-zinc-50 border border-zinc-100">
                      <Image
                        src={getImageUrl(selectedMarker.imageUrl) || "/placeholder.svg"}
                        alt={selectedMarker.name}
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

export { MapContainer }