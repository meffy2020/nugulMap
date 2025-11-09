// frontend/components/map-container.tsx

"use client"

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

import { fetchZones, type SmokingZone } from "@/lib/api"
import Image from "next/image"

// 카카오맵 관련 import (react-kakao-maps-sdk 컴포넌트는 더 이상 사용하지 않음)
// import { Map, MapMarker, CustomOverlayMap } from "react-kakao-maps-sdk"

// 컴포넌트 내부에서 사용할 데이터 타입
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
  const [mapInstance, setMapInstance] = useState<kakao.maps.Map | null>(null) // 카카오맵 인스턴스
  const currentMarkers = useRef<kakao.maps.Marker[]>([]); // 현재 지도에 표시된 마커들을 관리

  // 컴포넌트가 마운트될 때 API를 호출하는 useEffect
  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        const zonesData = await fetchZones(37.5665, 126.9780); // 서울 시청 기본 위치
        console.log("Loaded zones from backend API:", zonesData);
        setZones(zonesData);
        setError(null);
      } catch (err) {
        console.error("Failed to load zones from API:", err);
        setError("흡연구역 데이터를 불러오는데 실패했습니다.");
        setZones([]);
      } finally {
        setLoading(false);
      }
    };

    loadInitialData();
  }, []);

  // ‼️‼️‼️ 카카오맵 스크립트 직접 로드 및 지도 초기화 로직 ‼️‼️‼️
  useEffect(() => {
    const KAKAO_APP_KEY = process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY;
    if (!KAKAO_APP_KEY) {
      console.error("카카오맵 API 키가 설정되지 않았습니다.");
      setError("카카오맵 API 키가 설정되지 않았습니다.");
      return;
    }

    if (typeof window === "undefined" || !mapRef.current) return; // 클라이언트 환경에서만 실행

    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false`;
    script.async = true;
    document.head.appendChild(script);

    script.onload = () => {
      console.log("카카오맵 SDK 스크립트 로드 완료.");
      window.kakao.maps.load(() => {
        console.log("카카오맵 라이브러리 로드 완료. 지도 초기화 시작.");
        const options = {
          center: new window.kakao.maps.LatLng(37.5665, 126.9780), // 서울 시청
          level: 3,
        };
        const map = new window.kakao.maps.Map(mapRef.current!, options);
        setMapInstance(map);
        console.log("지도 인스턴스 생성 완료.", map);
      });
    };

    script.onerror = (e) => {
      console.error("카카오맵 SDK 스크립트 로드 실패:", e);
      setError("카카오맵 SDK 로드에 실패했습니다. API 키 또는 네트워크를 확인하세요.");
    };

    return () => {
      if (script.parentNode) {
        script.parentNode.removeChild(script);
      }
    };
  }, []);

  // zones 데이터가 변경될 때 마커를 다시 그리는 로직
  useEffect(() => {
    if (mapInstance && zones.length > 0) {
      // 기존 마커 제거
      currentMarkers.current.forEach(marker => marker.setMap(null));
      currentMarkers.current = [];

      zones.forEach((zone) => {
        const markerPosition = new window.kakao.maps.LatLng(zone.latitude, zone.longitude);
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          map: mapInstance,
        });

        // 마커 클릭 시 정보창 표시
        const infoWindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:5px;font-size:12px;">${zone.address}</div>`,
        });

        window.kakao.maps.event.addListener(marker, 'click', () => {
          infoWindow.open(mapInstance, marker);
          setSelectedMarker(zone as LocationMarker); // 상세 정보 표시를 위해 선택된 마커 설정
        });

        currentMarkers.current.push(marker);
      });
    }
  }, [mapInstance, zones]);

  // 부모 컴포넌트에서 호출할 함수들
  useImperativeHandle(ref, () => ({
    handleZoneCreated: (newZone: SmokingZone) => {
      setZones((prev) => [...prev, newZone]);
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(newZone.latitude, newZone.longitude));
      }
    },
    centerOnLocation: (lat: number, lng: number) => {
      if (mapInstance) {
        mapInstance.setCenter(new window.kakao.maps.LatLng(lat, lng));
        mapInstance.setLevel(3); // 적절한 줌 레벨 설정
      }
    },
  }));

  return (
    <div className="relative w-full h-full">
      {/* 지도가 그려질 영역 */}
      <div ref={mapRef} className="w-full h-full z-10" style={{ filter: "hue-rotate(20deg) saturate(0.8)" }} />

      {/* 로딩 및 에러 메시지 */}
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

      {/* 선택된 마커의 상세 정보 오버레이 (기존 Card 컴포넌트 사용) */}
      {selectedMarker && (
        <div className="absolute bottom-0 left-0 right-0 z-50 animate-in slide-in-from-bottom-4 fade-in duration-300">
          <Card className="mx-4 mb-4 bg-card/95 backdrop-blur-sm border-border shadow-xl rounded-lg animate-in zoom-in-90 duration-300">
            <CardContent className="p-3">
              {selectedMarker.image && (
                <div className="mb-2 rounded-md overflow-hidden">
                  <Image
                    src={selectedMarker.image}
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
                {selectedMarker.subtype && <span className="text-xs px-2 py-1 bg-secondary/50 text-secondary-foreground rounded-full">{selectedMarker.subtype}</span>}
                <span className="text-xs px-2 py-1 bg-muted text-muted-foreground rounded-full">{selectedMarker.region}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
})

MapContainer.displayName = "MapContainer"
