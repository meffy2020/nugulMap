"use client"

import { useState, useRef, useEffect } from "react"

import { MapContainer, type MapContainerRef } from "@/components/map-container"

import { FloatingActionButton } from "@/components/floating-action-button"

// import { AddLocationModal } from "@/components/add-location-modal" // 제거

import { FloatingUserProfile } from "@/components/floating-user-profile"

// import { CurrentLocationButton } from "@/components/current-location-button"

import { SearchBar } from "@/components/search-bar"

import { useAuth } from "@/hooks/use-auth"

import { useRouter } from "next/navigation"

import { searchZones } from "@/lib/api"

import { useToast } from "@/hooks/use-toast"



export default function HomePage() {

  // const [isModalOpen, setIsModalOpen] = useState(false) // 제거

  const mapRef = useRef<MapContainerRef>(null)

  const { user } = useAuth()

  const router = useRouter()

  const { toast } = useToast()



  // 앱 실행 시 자동으로 위치 권한 요청 및 이동

  useEffect(() => {

    if (navigator.geolocation) {

      navigator.geolocation.getCurrentPosition(

        (position) => {

          const { latitude, longitude } = position.coords

          console.log("[v0] Auto-location found:", latitude, longitude)

          if (mapRef.current?.centerOnLocation) {

            mapRef.current.centerOnLocation(latitude, longitude)

          }

        },

        (error) => {

          console.error("[v0] Auto-location error:", error)

          // 권한 거부 시 서울 시청 기본값 유지 (MapContainer 내부 기본값)

        }

      )

    }

  }, [])



    const handleAddClick = () => {



      if (!user) {



        router.push("/login")



        return



      }



      



      // 현재 지도 중심 좌표 가져오기



      let url = "/add"



      if (mapRef.current?.getCenter) {



        const center = mapRef.current.getCenter()



        url += `?lat=${center.lat}&lng=${center.lng}`



      }



      



      router.push(url)



    }



  



  // handleZoneCreated 함수 제거 (더 이상 여기서 안 씀)



  const handleLocationFound = (lat: number, lng: number) => {

    if (mapRef.current?.centerOnLocation) {

      mapRef.current.centerOnLocation(lat, lng)

    }

  }



  const handleSearch = async (query: string) => {

    if (!query.trim()) return



    try {

      const results = await searchZones(query)

      if (results.length > 0) {

        // 첫 번째 결과 위치로 이동

        const first = results[0]

        if (mapRef.current?.centerOnLocation) {

          mapRef.current.centerOnLocation(first.latitude, first.longitude)

        }

      } else {

        toast({

          title: "검색 결과 없음",

          description: "해당 키워드로 등록된 장소가 없습니다.",

        })

      }

    } catch (err) {

      console.error("Search failed:", err)

    }

  }



  return (

    <div className="relative h-screen w-full flex flex-col bg-background overflow-hidden">

      <div className="flex-1 relative">

        <MapContainer ref={mapRef} />



        <div className="absolute top-4 left-4 right-4 z-40 pointer-events-none flex flex-col gap-4">

          <div className="flex items-center gap-2 pointer-events-auto">

            <SearchBar onSearch={handleSearch} />

            <FloatingUserProfile />

          </div>

        </div>



        {/* <div className="absolute bottom-24 left-6 pointer-events-auto z-[9999]">

          <CurrentLocationButton onLocationFound={handleLocationFound} />

        </div> */}



        <div className="absolute bottom-8 right-6 pointer-events-auto z-40">

          <FloatingActionButton onClick={handleAddClick} />

        </div>



      </div>



      {/* 모달 제거 */}

    </div>

  )

}
