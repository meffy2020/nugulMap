"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X, MapPin, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import Script from "next/script"

import { fetchZones, type SmokingZone, getImageUrl } from "@/lib/api"
import Image from "next/image"
import { cn } from "@/lib/utils"

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

  // 초기 지도 설정
  useEffect(() => {
    if (!kakaoLoaded || !mapRef.current || !window.kakao || !window.kakao.maps) {
      return
    }

    const initMap = () => {
      if (!mapRef.current || mapInstance) return

      try {
        const options = {
          center: new window.kakao.maps.LatLng(37.5665, 126.978),
          level: 3,
        }
        const map = new window.kakao.maps.Map(mapRef.current!, options)
        setMapInstance(map)
        
        console.log("[v0] Main Map Initialized")

        if (window.kakao.maps.MarkerClusterer) {
          clustererRef.current = new window.kakao.maps.MarkerClusterer({
            map: map,
            averageCenter: true,
            minLevel: 6,
          })
        }
        setShowMapError(false)
        setLoading(false)
      } catch (err) {
        console.error("[v0] 카카오맵 생성 실패:", err)
      }
    }

    window.kakao.maps.load(initMap)
  }, [kakaoLoaded, mapInstance])

  // 데이터 로딩 함수 (Bounding Box 기준)
  const loadZonesInView = async () => {
    if (!mapInstance) return

    try {
      const bounds = mapInstance.getBounds()
      const sw = bounds.getSouthWest()
      const ne = bounds.getNorthEast()

      console.log(`[v0] Fetching zones within viewport: [${sw.getLat()}, ${sw.getLng()}] to [${ne.getLat()}, ${ne.getLng()}]`)
      const zonesData = await fetchZones(sw.getLat(), ne.getLat(), sw.getLng(), ne.getLng())
      
      setZones(zonesData)
    } catch (err) {
      console.error("[v0] Failed to fetch zones:", err)
    }
  }

  // 지도가 준비되면 첫 데이터 로드
  useEffect(() => {
    if (mapInstance) {
      loadZonesInView()
    }
  }, [mapInstance])

  // 지도 이벤트 리스너
  useEffect(() => {
    if (!mapInstance) return

    const handleMapChange = () => {
      loadZonesInView()
    }

    window.kakao.maps.event.addListener(mapInstance, 'dragend', handleMapChange)
    window.kakao.maps.event.addListener(mapInstance, 'zoom_changed', handleMapChange)

    return () => {
      window.kakao.maps.event.removeListener(mapInstance, 'dragend', handleMapChange)
      window.kakao.maps.event.removeListener(mapInstance, 'zoom_changed', handleMapChange)
    }
  }, [mapInstance])

  // 마커 렌더링 로직
  useEffect(() => {
    if (mapInstance && Array.isArray(zones)) {
      // 기존 마커 정리
      if (clustererRef.current) clustererRef.current.clear()
      currentMarkers.current.forEach(m => m.setMap(null))
      
      const markers = zones.map((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        
        // 커스텀 마커 이미지 설정
        const imageSrc = "/images/pin.png"
        const imageSize = new window.kakao.maps.Size(40, 40)
        const imageOption = { offset: new window.kakao.maps.Point(20, 40) } // 핀의 하단 중앙이 위치를 가리키도록 설정
        
        const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize, imageOption)
        
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          image: markerImage,
          title: zone.address
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

  return (
    <>
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false&libraries=services,clusterer`}
        strategy="afterInteractive"
        onLoad={() => setKakaoLoaded(true)}
        onError={() => setShowMapError(true)}
      />
      <div className="relative w-full h-full">
        {showMapError ? (
          <div className="w-full h-full flex items-center justify-center bg-muted text-center p-6">
            <p>지도를 불러올 수 없습니다. API 키 설정을 확인해주세요.</p>
          </div>
        ) : (
          <div ref={mapRef} className="w-full h-full z-10" />
        )}

        {loading && (
          <div className="absolute inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center z-20">
            <Loader2 className="w-8 h-8 animate-spin text-primary" />
          </div>
        )}

        {selectedMarker && (
          <div className="absolute bottom-0 left-0 right-0 z-50 p-4 animate-in slide-in-from-bottom-full duration-300">
            <Card className="max-w-md mx-auto bg-background/95 backdrop-blur-xl shadow-2xl rounded-[2rem] overflow-hidden">
              <div className="w-12 h-1 bg-muted rounded-full mx-auto mt-3 opacity-50" />
              <CardContent className="p-6">
                {selectedMarker.image && (
                  <div className="mb-4 rounded-2xl overflow-hidden aspect-video relative">
                    <Image src={getImageUrl(selectedMarker.image)} alt="Smoking zone" fill className="object-cover" />
                  </div>
                )}
                <div className="flex justify-between items-start">
                  <div>
                    <h3 className="text-xl font-black">{selectedMarker.address}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{selectedMarker.description}</p>
                  </div>
                  <Button variant="ghost" size="icon" onClick={() => setSelectedMarker(null)}>
                    <X className="w-5 h-5" />
                  </Button>
                </div>
                <div className="flex gap-2 mt-4">
                  <span className="px-3 py-1 bg-primary text-primary-foreground rounded-full text-xs font-bold">{selectedMarker.type}</span>
                  <span className="px-3 py-1 bg-muted rounded-full text-xs font-bold">{selectedMarker.region}</span>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </>
  )
})

MapContainer.displayName = "MapContainer"
