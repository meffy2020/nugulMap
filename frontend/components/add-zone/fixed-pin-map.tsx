"use client"

import { useEffect, useRef, useState, forwardRef, useImperativeHandle } from "react"
import Script from "next/script"
import Image from "next/image"

interface FixedPinMapProps {
  initialLat?: number
  initialLng?: number
  onLocationChange: (lat: number, lng: number) => void
  onMapLoad?: (map: any) => void
  bottomOffset?: number
}

export interface FixedPinMapRef {
  centerOnLocation: (lat: number, lng: number) => void
}

export const FixedPinMap = forwardRef<FixedPinMapRef, FixedPinMapProps>(({ 
  initialLat = 37.5665, 
  initialLng = 126.978, 
  onLocationChange,
  onMapLoad,
  bottomOffset = 0
}, ref) => {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const [isDragging, setIsDragging] = useState(false)

  useImperativeHandle(ref, () => ({
    centerOnLocation: (lat: number, lng: number) => {
      if (mapRef.current) {
        mapRef.current.setCenter(new window.kakao.maps.LatLng(lat, lng))
      }
    }
  }))

  // Initialize Map
  const initializeMap = () => {
    if (!window.kakao?.maps || !mapContainerRef.current) {
      console.warn("[v0] Kakao maps not loaded yet or container missing")
      return
    }

    if (mapRef.current) return // 이미 초기화됨

    const options = {
      center: new window.kakao.maps.LatLng(initialLat, initialLng),
      level: 3,
    }

    const map = new window.kakao.maps.Map(mapContainerRef.current, options)
    mapRef.current = map
    console.log("[v0] Kakao Map initialized successfully")
    if (onMapLoad) onMapLoad(map)

    // Event Listeners
    window.kakao.maps.event.addListener(map, "dragstart", () => {
      setIsDragging(true)
    })

    window.kakao.maps.event.addListener(map, "dragend", () => {
      setIsDragging(false)
      updateCenter()
    })

    window.kakao.maps.event.addListener(map, "idle", () => {
      if (!isDragging) {
        updateCenter()
      }
    })
  }

  const updateCenter = () => {
    if (!mapRef.current) return
    const center = mapRef.current.getCenter()
    const lat = center.getLat()
    const lng = center.getLng()
    onLocationChange(lat, lng)
  }

  return (
    <>
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY}&libraries=services&autoload=false`}
        strategy="afterInteractive"
        onLoad={() => {
          console.log("[v0] Kakao Script Loaded")
          window.kakao.maps.load(() => {
            initializeMap()
          })
        }}
      />
      <div className="relative w-full h-full" style={{ height: '100%', width: '100%' }}>
        <div ref={mapContainerRef} className="w-full h-full bg-muted/20" style={{ height: '100%', width: '100%' }} />
        
        {/* Fixed Center Pin */}
        <div 
          className="absolute left-1/2 -translate-x-1/2 -translate-y-full z-10 pointer-events-none flex flex-col items-center justify-end"
          style={{ top: `calc(50% - ${bottomOffset / 2}px)` }}
        >
           {/* Custom Pin Image */}
           <div className={`transition-transform duration-200 ${isDragging ? "-translate-y-3 scale-110" : "translate-y-0 scale-100"}`}>
             <div className="relative w-12 h-12 filter drop-shadow-xl">
                <Image 
                  src="/images/pin.png" 
                  alt="Center Pin" 
                  fill 
                  className="object-contain"
                  priority
                />
             </div>
           </div>
           {/* Shadow effect */}
           <div className="w-2 h-1 bg-black/20 rounded-full blur-[1px] mt-[-2px]" />
        </div>
      </div>
    </>
  )
})

FixedPinMap.displayName = "FixedPinMap"