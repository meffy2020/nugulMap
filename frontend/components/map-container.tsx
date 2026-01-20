"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X, MapPin } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import Script from "next/script"

import { fetchZones, type SmokingZone, getImageUrl } from "@/lib/api"
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
        
        // 커스텀 마커 이미지 설정
        const imageSrc = "/images/pin.png"
        const imageSize = new window.kakao.maps.Size(40, 40)
        const imageOption = { offset: new window.kakao.maps.Point(20, 40) }
        
        const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize, imageOption)
        
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          map: mapInstance,
          image: markerImage
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
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false&libraries=services,clusterer`}
        strategy="afterInteractive"
        onLoad={handleKakaoLoad}
        onError={handleKakaoError}
      />
... (rest of the replace string)
