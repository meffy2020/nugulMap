"use client"

import type React from "react"
import { useState, useEffect, useRef } from "react"
import Script from "next/script"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Loader2, Check, Camera, X, ImageIcon } from "lucide-react"
import { createZone, type CreateZonePayload } from "@/lib/api"

interface AddLocationModalProps {
  isOpen: boolean
  onClose: () => void
  onZoneCreated?: (zone: any) => void
}

// 흡연구역 유형 상수
const ZONE_TYPES = [
  { value: "지정구역", label: "지정구역" },
  { value: "일반구역", label: "일반구역" },
  { value: "24시간", label: "24시간" },
  { value: "실외구역", label: "실외구역" },
]

export function AddLocationModal({ isOpen, onClose, onZoneCreated }: AddLocationModalProps) {
  const [formData, setFormData] = useState({
    type: "",
    subtype: "흡연부스",
    description: "",
    latitude: 37.5665,
    longitude: 126.978,
    address: "",
    region: "서울특별시",
    user: "익명",
  })

  const [isLoading, setIsLoading] = useState(false)
  const [showSuccess, setShowSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [showCamera, setShowCamera] = useState(false)

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (isOpen && window.kakao?.maps && mapContainerRef.current && !mapRef.current) {
      initializeMap()
    }
  }, [isOpen])

  useEffect(() => {
    if (mapRef.current && markerRef.current) {
      const position = new window.kakao.maps.LatLng(formData.latitude, formData.longitude)
      mapRef.current.setCenter(position)
      markerRef.current.setPosition(position)
    }
  }, [formData.latitude, formData.longitude])

  const initializeMap = () => {
    if (!window.kakao?.maps || !mapContainerRef.current) return

    const container = mapContainerRef.current
    const options = {
      center: new window.kakao.maps.LatLng(formData.latitude, formData.longitude),
      level: 3,
    }

    const map = new window.kakao.maps.Map(container, options)
    mapRef.current = map

    // 마커 생성
    const markerPosition = new window.kakao.maps.LatLng(formData.latitude, formData.longitude)
    const marker = new window.kakao.maps.Marker({
      position: markerPosition,
      draggable: true,
    })
    marker.setMap(map)
    markerRef.current = marker

    const geocoder = new window.kakao.maps.services.Geocoder()

    // 지도 클릭 이벤트
    window.kakao.maps.event.addListener(map, "click", (mouseEvent: any) => {
      const latlng = mouseEvent.latLng
      marker.setPosition(latlng)
      updateAddressFromCoords(latlng.getLat(), latlng.getLng())
    })

    // 마커 드래그 종료 이벤트
    window.kakao.maps.event.addListener(marker, "dragend", () => {
      const latlng = marker.getPosition()
      updateAddressFromCoords(latlng.getLat(), latlng.getLng())
    })

    // 초기 주소 가져오기
    getCurrentLocationForForm()
  }

  const updateAddressFromCoords = (lat: number, lng: number) => {
    if (!window.kakao?.maps?.services) return

    const geocoder = new window.kakao.maps.services.Geocoder()
    geocoder.coord2Address(lng, lat, (result: any, status: any) => {
      if (status === window.kakao.maps.services.Status.OK && result[0]) {
        const addr = result[0].address
        setFormData((prev) => ({
          ...prev,
          latitude: lat,
          longitude: lng,
          address: addr.address_name || `${lat.toFixed(6)}, ${lng.toFixed(6)}`,
          region: addr.region_1depth_name || prev.region,
        }))
      } else {
        setFormData((prev) => ({
          ...prev,
          latitude: lat,
          longitude: lng,
          address: `${lat.toFixed(6)}, ${lng.toFixed(6)}`,
        }))
      }
    })
  }

  const getCurrentLocationForForm = async () => {
    try {
      if (!navigator.geolocation) return

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const { latitude, longitude } = position.coords

          setFormData((prev) => ({
            ...prev,
            latitude,
            longitude,
            address: `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`,
          }))

          // 카카오맵으로 주소 변환 시도
          if (window.kakao?.maps?.services) {
            const geocoder = new window.kakao.maps.services.Geocoder()
            geocoder.coord2Address(longitude, latitude, (result: any, status: any) => {
              if (status === window.kakao.maps.services.Status.OK && result[0]) {
                const addr = result[0].address
                setFormData((prev) => ({
                  ...prev,
                  address: addr.address_name || prev.address,
                  region: addr.region_1depth_name || prev.region,
                }))
              }
            })
          }
        },
        (error) => {
          console.error("[v0] Location error:", error)
        },
      )
    } catch (error) {
      console.error("[v0] Location error:", error)
    }
  }

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
      })
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        streamRef.current = stream
      }
      setShowCamera(true)
    } catch (err) {
      console.error("[v0] Camera access denied:", err)
      alert("카메라 접근 권한이 필요합니다.")
    }
  }

  const capturePhoto = () => {
    if (!videoRef.current) return

    const canvas = document.createElement("canvas")
    canvas.width = videoRef.current.videoWidth
    canvas.height = videoRef.current.videoHeight
    const ctx = canvas.getContext("2d")
    if (ctx) {
      ctx.drawImage(videoRef.current, 0, 0)
      canvas.toBlob((blob) => {
        if (blob) {
          const file = new File([blob], "photo.jpg", { type: "image/jpeg" })
          setImageFile(file)
          setImagePreview(URL.createObjectURL(blob))
          stopCamera()
        }
      }, "image/jpeg")
    }
  }

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
    setShowCamera(false)
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      setImageFile(file)
      setImagePreview(URL.createObjectURL(file))
    }
  }

  const removeImage = () => {
    setImageFile(null)
    setImagePreview(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ""
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!formData.type) {
      setError("유형을 선택해주세요.")
      return
    }

    setIsLoading(true)
    setError(null)

    try {
      const zonePayload: CreateZonePayload = {
        region: formData.region,
        type: formData.type,
        subtype: formData.subtype,
        description: formData.description,
        latitude: formData.latitude,
        longitude: formData.longitude,
        address: formData.address,
        user: formData.user,
      }

      const newZone = await createZone(zonePayload, imageFile || undefined)
      console.log("[v0] Zone created successfully:", newZone)

      setShowSuccess(true)
      onZoneCreated?.(newZone)

      setTimeout(() => {
        setShowSuccess(false)
        handleCancel()
      }, 1000)
    } catch (err) {
      console.error("[v0] Error creating zone:", err)
      setError(err instanceof Error ? err.message : "흡연구역 생성에 실패했습니다.")
    } finally {
      setIsLoading(false)
    }
  }

  const handleCancel = () => {
    setFormData({
      type: "",
      subtype: "흡연부스",
      description: "",
      latitude: 37.5665,
      longitude: 126.978,
      address: "",
      region: "서울특별시",
      user: "익명",
    })
    removeImage()
    stopCamera()
    setError(null)
    mapRef.current = null
    markerRef.current = null
    onClose()
  }

  return (
    <>
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY}&libraries=services&autoload=false`}
        strategy="beforeInteractive"
        onLoad={() => {
          if (window.kakao?.maps) {
            window.kakao.maps.load(() => {
              console.log("[v0] Kakao Maps loaded")
            })
          }
        }}
      />

      <Dialog open={isOpen} onOpenChange={onClose}>
        <DialogContent className="w-full max-w-lg max-h-[90vh] overflow-y-auto bg-background border-none shadow-2xl p-0 gap-0 rounded-3xl overflow-hidden">
          <div className="p-6 bg-primary text-primary-foreground">
            <DialogHeader>
              <DialogTitle className="text-2xl font-bold flex items-center gap-2">
                <MapPin className="w-6 h-6" />
                새로운 흡연구역 제보
              </DialogTitle>
              <p className="text-primary-foreground/80 text-sm mt-1">정확한 위치와 사진을 공유하여 너구리들을 도와주세요!</p>
            </DialogHeader>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-6">
            <div className="space-y-4">
              <div className="bg-muted/30 p-4 rounded-2xl border border-border/50 space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="address" className="text-xs font-bold uppercase tracking-wider text-muted-foreground ml-1">
                    위치 주소
                  </Label>
                  <Input
                    id="address"
                    value={formData.address}
                    onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                    placeholder="주소를 입력하거나 지도에서 핀을 옮기세요"
                    className="bg-background border-border/50 h-12 rounded-xl focus:ring-primary/20"
                  />
                </div>

                <div className="space-y-2">
                  <Label className="text-xs font-bold uppercase tracking-wider text-muted-foreground ml-1">위치 확인</Label>
                  <div ref={mapContainerRef} className="w-full h-48 bg-muted rounded-xl border border-border/50 overflow-hidden shadow-inner" />
                  <p className="text-[10px] text-muted-foreground text-center italic">지도를 클릭하거나 핀을 드래그하여 위치를 조정할 수 있습니다.</p>
                </div>
              </div>

              <div className="space-y-3">
                <Label className="text-sm font-bold ml-1">구역 유형</Label>
                <div className="grid grid-cols-2 gap-2">
                  {ZONE_TYPES.map((type) => (
                    <Button
                      key={type.value}
                      type="button"
                      variant={formData.type === type.value ? "default" : "outline"}
                      className={cn(
                        "h-14 rounded-2xl transition-all border-2",
                        formData.type === type.value 
                          ? "bg-primary text-primary-foreground border-primary shadow-md scale-[1.02]" 
                          : "bg-background border-border/50 hover:border-primary/30"
                      )}
                      onClick={() => setFormData({ ...formData, type: type.value })}
                    >
                      {type.label}
                    </Button>
                  ))}
                </div>
              </div>

              <div className="space-y-3">
                <Label className="text-sm font-bold ml-1">현장 사진</Label>
                {!imagePreview ? (
                  <div className="grid grid-cols-2 gap-3">
                    <Button
                      type="button"
                      variant="outline"
                      className="h-28 rounded-2xl border-2 border-dashed border-border/50 hover:border-primary/50 hover:bg-primary/5 transition-all flex flex-col gap-2"
                      onClick={startCamera}
                    >
                      <Camera className="h-6 w-6" />
                      <span className="text-xs font-medium">카메라 촬영</span>
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      className="h-28 rounded-2xl border-2 border-dashed border-border/50 hover:border-primary/50 hover:bg-primary/5 transition-all flex flex-col gap-2"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <ImageIcon className="h-6 w-6" />
                      <span className="text-xs font-medium">갤러리 선택</span>
                    </Button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={handleFileSelect}
                    />
                  </div>
                ) : (
                  <div className="relative group">
                    <img
                      src={imagePreview || "/placeholder.svg"}
                      alt="Preview"
                      className="w-full h-56 object-cover rounded-2xl border border-border shadow-md"
                    />
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity rounded-2xl flex items-center justify-center">
                       <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        className="rounded-full shadow-lg"
                        onClick={removeImage}
                      >
                        <X className="h-4 w-4 mr-1" /> 사진 삭제
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {error && (
              <div className="text-destructive text-xs font-medium bg-destructive/10 p-3 rounded-xl border border-destructive/20 animate-shake">
                ⚠️ {error}
              </div>
            )}

            <div className="flex gap-3 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                disabled={isLoading}
                className="flex-1 h-14 rounded-2xl border-2 font-bold"
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={isLoading || !formData.type}
                className="flex-[2] h-14 rounded-2xl font-bold shadow-xl transition-transform active:scale-95"
              >
                {isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin mr-2" />
                ) : showSuccess ? (
                  <Check className="h-5 w-5 mr-2" />
                ) : null}
                {showSuccess ? "등록 완료!" : "제보하기"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {showCamera && (
        <Dialog open={showCamera} onOpenChange={stopCamera}>
          <DialogContent className="w-full max-w-md">
            <DialogHeader>
              <DialogTitle>사진 촬영</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <video ref={videoRef} autoPlay playsInline className="w-full rounded-lg border border-border" />
              <div className="flex flex-col gap-2">
                <Button type="button" onClick={capturePhoto} className="w-full h-12">
                  사진 촬영
                </Button>
                <Button type="button" variant="outline" onClick={stopCamera} className="w-full h-12 bg-transparent">
                  취소
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </>
  )
}