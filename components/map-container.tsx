"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X, MapPin } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import Script from "next/script"

import { fetchZones, type SmokingZone } from "@/lib/api"
import Image from "next/image"

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
        setShowMapError(false)
      } catch (err) {
        console.error("[v0] 카카오맵 생성 실패:", err)
      }
    })
  }, [kakaoLoaded])

  useEffect(() => {
    if (mapInstance && zones.length > 0) {
      currentMarkers.current.forEach((marker) => marker.setMap(null))
      currentMarkers.current = []

      zones.forEach((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          map: mapInstance,
        })

        const infoWindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:5px;font-size:12px;">${zone.address}</div>`,
        })

        window.kakao.maps.event.addListener(marker, "click", () => {
          infoWindow.open(mapInstance, marker)
          setSelectedMarker(zone as LocationMarker)
        })

        currentMarkers.current.push(marker)
      })
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
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false`}
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
          <div className="absolute bottom-0 left-0 right-0 z-50 animate-in slide-in-from-bottom-4 fade-in duration-300">
            <Card className="mx-4 mb-4 bg-card/95 backdrop-blur-sm border-border shadow-xl rounded-lg">
              <CardContent className="p-3">
                {selectedMarker.image && (
                  <div className="mb-2 rounded-md overflow-hidden">
                    <Image
                      src={selectedMarker.image || "/placeholder.svg"}
                      alt={`${selectedMarker.address} 흡연구역 사진`}
                      width={200}
                      height={120}
                      className="w-full h-auto object-cover"
                    />
                  </div>
                )}
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1">
                    <div className="text-sm font-medium text-card-foreground">{selectedMarker.address}</div>
                    <div className="text-xs text-muted-foreground">{selectedMarker.description}</div>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 w-6 p-0 text-muted-foreground"
                    onClick={() => setSelectedMarker(null)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
                <div className="flex flex-wrap gap-1">
                  <span className="text-xs px-2 py-1 bg-primary/20 text-primary rounded-full">
                    {selectedMarker.type}
                  </span>
                  {selectedMarker.subtype && (
                    <span className="text-xs px-2 py-1 bg-secondary/50 text-secondary-foreground rounded-full">
                      {selectedMarker.subtype}
                    </span>
                  )}
                  <span className="text-xs px-2 py-1 bg-muted text-muted-foreground rounded-full">
                    {selectedMarker.region}
                  </span>
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
