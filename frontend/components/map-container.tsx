// frontend/components/map-container.tsx

"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { fetchZones, type SmokingZone } from "@/lib/api" // 우리가 만든 api 함수를 import
import Image from "next/image"

// 컴포넌트 내부에서 사용할 데이터 타입
interface LocationMarker extends SmokingZone {}

export interface MapContainerRef {
  handleZoneCreated: (zone: SmokingZone) => void
  centerOnLocation: (lat: number, lng: number) => void
}

// props에서 zones를 제거합니다.
export const MapContainer = forwardRef<MapContainerRef>((props, ref) => {
  const [selectedMarker, setSelectedMarker] = useState<LocationMarker | null>(null)
  const [zones, setZones] = useState<SmokingZone[]>([]) // API로부터 받은 데이터를 저장할 state
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const mapRef = useRef<HTMLDivElement>(null)
  const [map, setMap] = useState<any>(null)
  const [markers, setMarkers] = useState<any[]>([])

  // 컴포넌트가 마운트될 때 API를 호출하는 useEffect
  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        // 기본 위치 (서울 시청) 기준으로 실제 API 호출
        const zonesData = await fetchZones(37.5665, 126.9780);
        console.log("Loaded zones from backend API:", zonesData);
        setZones(zonesData);
        setError(null);
      } catch (err) {
        console.error("Failed to load zones from API:", err);
        setError("흡연구역 데이터를 불러오는데 실패했습니다.");
        // API 실패 시 비어있는 배열로 설정
        setZones([]);
      } finally {
        setLoading(false);
      }
    };

    loadInitialData();
  }, []);

  // Leaflet 지도 초기화 로직 (기존과 동일)
  useEffect(() => {
    if (typeof window !== "undefined" && mapRef.current && !map) {
      const loadLeafletCSS = () => {
        if (!document.querySelector('link[href*="leaflet.css"]')) {
          const link = document.createElement("link")
          link.rel = "stylesheet"
          link.href = "https://unpkg.com/leaflet@1.7.1/dist/leaflet.css"
          document.head.appendChild(link)
        }
      }
      loadLeafletCSS()

      import("leaflet").then((L) => {
        delete (L.Icon.Default.prototype as any)._getIconUrl
        L.Icon.Default.mergeOptions({
          iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png",
          iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png",
          shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png",
        })

        const mapInstance = L.map(mapRef.current!).setView([37.5665, 126.978], 13)
        mapInstance.zoomControl.remove()
        L.tileLayer("https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png", {
          attribution: "",
          subdomains: "abcd",
          maxZoom: 20,
        }).addTo(mapInstance)
        setMap(mapInstance)
      })
    }
  }, [map])

  // zones 데이터가 변경될 때 마커를 다시 그리는 로직 (기존과 거의 동일)
  useEffect(() => {
    if (map && zones.length > 0) {
      markers.forEach((marker) => marker.remove())

      const newMarkers = zones.map((zone) => {
        const customIcon = (window as any).L?.divIcon({
          html: `<div class="custom-marker"><svg width="32" height="32" viewBox="0 0 24 24" fill="#D97742" stroke="#2C2C2C" strokeWidth="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg></div>`,
          className: "custom-div-icon",
          iconSize: [32, 32],
          iconAnchor: [16, 32],
          popupAnchor: [0, -32],
        })

        const marker = (window as any).L?.marker([zone.latitude, zone.longitude], { icon: customIcon })
          .addTo(map)
          .on("click", () => setSelectedMarker(zone as LocationMarker)) // zone을 바로 사용

        return marker
      })
      setMarkers(newMarkers)
    }
  }, [map, zones])

  // 부모 컴포넌트에서 호출할 함수들
  useImperativeHandle(ref, () => ({
    handleZoneCreated: (newZone: SmokingZone) => {
      setZones((prev) => [...prev, newZone]);
    },
    centerOnLocation: (lat: number, lng: number) => {
      if (map) {
        map.setView([lat, lng], 16, { animate: true, duration: 1 });
      }
    },
  }));

  // 이하 렌더링 로직은 기존과 거의 동일
  return (
    <div className="relative w-full h-full">
      <div ref={mapRef} className="w-full h-full z-10" style={{ filter: "hue-rotate(20deg) saturate(0.8)" }} />
      {loading && (
        <div className="absolute inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center z-20">
          <div className="text-foreground">지도 데이터를 불러오는 중...</div>
        </div>
      )}
      {error && (
        <div className="absolute top-4 left-4 right-4 bg-red-500/10 border border-red-500/20 text-red-100 p-3 rounded-md z-20">
          {error}
        </div>
      )}
      <style jsx>{`
        .custom-marker { transition: all 0.3s ease; cursor: pointer; }
        .custom-marker:hover { transform: scale(1.2); filter: drop-shadow(0 0 8px rgba(217, 119, 66, 0.6)); }
        .custom-div-icon { background: none !important; border: none !important; }
      `}</style>
      {selectedMarker && (
        <div className="absolute bottom-0 left-0 right-0 z-50 animate-in slide-in-from-bottom-4 fade-in duration-300">
          <Card className="mx-4 mb-4 bg-card/95 backdrop-blur-sm border-border shadow-xl rounded-t-xl">
            <CardContent className="p-4">
              {selectedMarker.image && (
                <div className="mb-4 rounded-lg overflow-hidden">
                  <Image
                    src={selectedMarker.image}
                    alt={`${selectedMarker.address} 흡연구역 사진`}
                    width={300}
                    height={200}
                    className="w-full h-48 object-cover"
                    priority
                  />
                </div>
              )}
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                  <div className="text-sm font-medium text-card-foreground mb-1">주소</div>
                  <div className="text-sm text-muted-foreground mb-3">{selectedMarker.address}</div>
                  <div className="text-sm font-medium text-card-foreground mb-1">상세 설명</div>
                  <div className="text-sm text-muted-foreground mb-3">{selectedMarker.description}</div>
                  <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground">
                    <div>지역: {selectedMarker.region}</div>
                    <div>타입: {selectedMarker.subtype}</div>
                    <div>등록자: {selectedMarker.user}</div>
                  </div>
                </div>
                <Button variant="ghost" size="sm" className="h-6 w-6 p-0" onClick={() => setSelectedMarker(null)}>
                  <X className="h-4 w-4" />
                </Button>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs px-2 py-1 bg-primary/20 text-primary rounded-full">{selectedMarker.type}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
})

MapContainer.displayName = "MapContainer"