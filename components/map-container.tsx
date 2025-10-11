"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

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
  const [error, setError] = useState<string | null>(null)
  const mapRef = useRef<HTMLDivElement>(null)
  const [mapInstance, setMapInstance] = useState<any>(null)
  const currentMarkers = useRef<any[]>([])

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true)
        const zonesData = await fetchZones(37.5665, 126.978) // 서울 시청 기본 위치
        console.log("[v0] Loaded zones from backend API:", zonesData)
        setZones(zonesData)
        setError(null)
      } catch (err) {
        console.error("[v0] Failed to load zones from API:", err)
        setError("흡연구역 데이터를 불러오는데 실패했습니다.")
        setTimeout(() => {
          setError(null)
        }, 3000)
        setZones([])
      } finally {
        setLoading(false)
      }
    }

    loadInitialData()
  }, [])

  useEffect(() => {
    const KAKAO_APP_KEY = process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY
    if (!KAKAO_APP_KEY) {
      console.error("[v0] 카카오맵 API 키가 설정되지 않았습니다. NEXT_PUBLIC_KAKAOMAP_APIKEY 환경 변수를 확인하세요.")
      setError("카카오맵 API 키가 설정되지 않았습니다. 환경 변수를 확인하세요.")
      setLoading(false)
      return
    }

    if (typeof window === "undefined" || !mapRef.current) return

    if (window.kakao && window.kakao.maps) {
      console.log("[v0] 카카오맵 SDK가 이미 로드되어 있습니다. 지도 초기화 시작.")
      const options = {
        center: new window.kakao.maps.LatLng(37.5665, 126.978),
        level: 3,
      }
      const map = new window.kakao.maps.Map(mapRef.current!, options)
      setMapInstance(map)
      setLoading(false)
      return
    }

    const script = document.createElement("script")
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false`
    script.async = true

    script.onload = () => {
      console.log("[v0] 카카오맵 SDK 스크립트 로드 완료.")
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => {
          console.log("[v0] 카카오맵 라이브러리 로드 완료. 지도 초기화 시작.")
          if (!mapRef.current) {
            console.error("[v0] mapRef.current가 null입니다.")
            return
          }
          const options = {
            center: new window.kakao.maps.LatLng(37.5665, 126.978),
            level: 3,
          }
          const map = new window.kakao.maps.Map(mapRef.current!, options)
          setMapInstance(map)
          setLoading(false)
          console.log("[v0] 지도 인스턴스 생성 완료.")
        })
      } else {
        console.error("[v0] window.kakao.maps가 정의되지 않았습니다.")
        setError("카카오맵 라이브러리 로드에 실패했습니다.")
        setLoading(false)
      }
    }

    script.onerror = (e) => {
      console.error("[v0] 카카오맵 SDK 스크립트 로드 실패. 네트워크 또는 API 키를 확인하세요.", e)
      setError("카카오맵 SDK 로드에 실패했습니다. API 키 또는 네트워크를 확인하세요.")
      setLoading(false)
    }

    document.head.appendChild(script)

    return () => {
      if (script.parentNode) {
        script.parentNode.removeChild(script)
      }
    }
  }, [])

  useEffect(() => {
    if (mapInstance && zones.length > 0) {
      // 기존 마커 제거
      currentMarkers.current.forEach((marker) => marker.setMap(null))
      currentMarkers.current = []

      zones.forEach((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude)
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          map: mapInstance,
        })

        // 마커 클릭 시 정보창 표시
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

  return (
    <div className="relative w-full h-full">
      <div ref={mapRef} className="w-full h-full z-10" />

      {loading && (
        <div className="absolute inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center z-20">
          <div className="text-foreground">지도 데이터를 불러오는 중...</div>
        </div>
      )}

      {error && (
        <div
          className="absolute top-20 left-1/2 transform -translate-x-1/2 w-80 border-2 border-red-600 text-white p-4 rounded-lg z-[1001] shadow-2xl"
          style={{ backgroundColor: "rgb(127, 29, 29)" }}
        >
          <div className="text-center font-medium">{error}</div>
        </div>
      )}

      {selectedMarker && (
        <div className="absolute bottom-0 left-0 right-0 z-50 animate-in slide-in-from-bottom-4 fade-in duration-300">
          <Card className="mx-4 mb-4 bg-card/95 backdrop-blur-sm border-border shadow-xl rounded-lg animate-in zoom-in-90 duration-300">
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
                <span className="text-xs px-2 py-1 bg-primary/20 text-primary rounded-full">{selectedMarker.type}</span>
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
  )
})

MapContainer.displayName = "MapContainer"
