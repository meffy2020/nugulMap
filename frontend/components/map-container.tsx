"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X, MapPin } from "lucide-react"
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

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        const zonesData = await fetchZones(37.5665, 126.978)
        setZones(zonesData)
      } catch (err) {
        console.error("[v0] Failed to load zones:", err)
        setZones([])
      } finally {
        setLoading(false)
      }
    }

    loadInitialData()
  }, [])

  useEffect(() => {
    if (!kakaoLoaded || !mapRef.current || !window.kakao || !window.kakao.maps) {
      return
    }

    window.kakao.maps.load(() => {
      if (!mapRef.current) return

      try {
        const options = {
          center: new window.kakao.maps.LatLng(37.5665, 126.978),
          level: 3,
        }
        const map = new window.kakao.maps.Map(mapRef.current!, options)
        setMapInstance(map)
        
        // 클러스터러 초기화
        if (window.kakao.maps.MarkerClusterer) {
          const clusterer = new window.kakao.maps.MarkerClusterer({
            map: map,
            averageCenter: true,
            minLevel: 6,
            styles: [{
              width: '40px', height: '40px',
              background: 'rgba(23, 23, 23, 0.9)',
              borderRadius: '20px',
              color: '#fff',
              textAlign: 'center',
              fontWeight: 'bold',
              lineHeight: '41px'
            }]
          })
          clustererRef.current = clusterer
        }
        
        setShowMapError(false)
      } catch (err) {
        console.error("[v0] 카카오맵 생성 실패:", err)
      }
    })
  }, [kakaoLoaded])

  useEffect(() => {
    if (mapInstance && zones.length > 0) {
      // 기존 마커 및 클러스터 정리
      if (clustererRef.current) {
        clustererRef.current.clear()
      }
      currentMarkers.current.forEach(m => m.setMap(null))
      
      const markers = zones.map((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        
        // 커스텀 마커 이미지 설정
        const imageSrc = "/images/pin.png"
        const imageSize = new window.kakao.maps.Size(40, 40)
        const imageOption = { offset: new window.kakao.maps.Point(20, 40) }
        
        const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize, imageOption)
        
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          image: markerImage
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
  }))

  const handleKakaoLoad = () => {
    setKakaoLoaded(true)
    setShowMapError(false)
  }

  const handleKakaoError = () => {
    setShowMapError(true)
    setLoading(false)
  }

  if (!KAKAO_APP_KEY) {
    return (
      <div className="w-full h-full flex items-center justify-center bg-muted">
        <div className="text-center p-6 max-w-md">
          <MapPin className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
          <p className="text-foreground font-medium mb-2">지도 설정이 필요합니다</p>
          <p className="text-sm text-muted-foreground">Vars 섹션에서 NEXT_PUBLIC_KAKAOMAP_APIKEY를 설정해주세요</p>
        </div>
      </div>
    )
  }

  return (
    <>
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false&libraries=services,clusterer`}
        strategy="afterInteractive"
        onLoad={handleKakaoLoad}
        onError={handleKakaoError}
      />
      <div className="relative w-full h-full">
        {showMapError ? (
          <div className="w-full h-full flex items-center justify-center bg-muted">
            <div className="text-center p-6 max-w-md">
              <MapPin className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
              <p className="text-foreground font-medium mb-2">지도를 불러올 수 없습니다</p>
              <p className="text-sm text-muted-foreground mb-4">
                Kakao Developers 콘솔에서 현재 도메인을 Web 플랫폼에 등록해주세요
              </p>
              <p className="text-xs text-muted-foreground">
                등록된 흡연구역 {zones.length}개는 정상적으로 관리되고 있습니다
              </p>
            </div>
          </div>
        ) : (
          <div ref={mapRef} className="w-full h-full z-10" />
        )}

        {loading && !showMapError && (
          <div className="absolute inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center z-20">
            <div className="text-foreground">지도 데이터를 불러오는 중...</div>
          </div>
        )}

        {selectedMarker && (
          <div className="absolute bottom-0 left-0 right-0 z-50 animate-in slide-in-from-bottom-full duration-500">
            <div className="mx-auto max-w-md">
              <Card className="mx-4 mb-8 bg-background/95 backdrop-blur-xl border-none shadow-[0_-10px_40px_-15px_rgba(0,0,0,0.3)] rounded-[2.5rem] overflow-hidden group">
                <div className="w-12 h-1.5 bg-muted rounded-full mx-auto mt-3 mb-1 opacity-50" />
                <CardContent className="p-6">
                  {selectedMarker.image && (
                    <div className="mb-4 rounded-[1.5rem] overflow-hidden shadow-sm aspect-video relative">
                      <Image
                        src={getImageUrl(selectedMarker.image) || "/placeholder.svg"}
                        alt={`${selectedMarker.address} 흡연구역 사진`}
                        fill
                        className="object-cover"
                      />
                    </div>
                  )}
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex-1">
                      <div className="text-xl font-black text-foreground tracking-tight mb-1">{selectedMarker.address}</div>
                      <div className="text-sm text-muted-foreground font-medium leading-relaxed">{selectedMarker.description || "상세 설명이 없습니다."}</div>
                    </div>
                    <Button
                      variant="secondary"
                      size="icon"
                      className="h-10 w-10 rounded-full bg-muted/50 hover:bg-muted"
                      onClick={() => setSelectedMarker(null)}
                    >
                      <X className="h-5 w-5" />
                    </Button>
                  </div>
                  <div className="flex flex-wrap gap-2 mb-2">
                    <div className="px-4 py-2 bg-primary text-primary-foreground rounded-2xl text-xs font-bold shadow-md">
                      {selectedMarker.type}
                    </div>
                    {selectedMarker.subtype && (
                      <div className="px-4 py-2 bg-secondary text-secondary-foreground rounded-2xl text-xs font-bold border border-border/50">
                        {selectedMarker.subtype}
                      </div>
                    )}
                    <div className="px-4 py-2 bg-muted text-muted-foreground rounded-2xl text-xs font-bold">
                      {selectedMarker.region}
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        )}
      </div>
    </>
  )
})

MapContainer.displayName = "MapContainer"