"use client"

import type React from "react"
import { useState, useEffect, useRef } from "react"
import Script from "next/script"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Loader2, Check, Camera, X } from "lucide-react"
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
    })
    marker.setMap(map)
    markerRef.current = marker

    // 현재 위치 가져오기
    getCurrentLocationForForm()
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
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto bg-card border-border">
          <DialogHeader>
            <DialogTitle className="text-card-foreground">신규 흡연구역 등록</DialogTitle>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="address" className="text-card-foreground">
                주소
              </Label>
              <Input
                id="address"
                value={formData.address}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                placeholder="주소를 입력하세요"
                className="bg-input border-border text-foreground"
              />
            </div>

            <div className="space-y-2">
              <Label className="text-card-foreground">위치</Label>
              <div ref={mapContainerRef} className="w-full h-64 bg-muted rounded-lg border border-border" />
            </div>

            <div className="grid grid-cols-2 gap-4">
              {/* 왼쪽: 유형 선택 */}
              <div className="space-y-2">
                <Label className="text-card-foreground">유형</Label>
                <div className="grid grid-cols-2 gap-2">
                  {ZONE_TYPES.map((type) => (
                    <Button
                      key={type.value}
                      type="button"
                      variant={formData.type === type.value ? "default" : "outline"}
                      className={`${
                        formData.type === type.value
                          ? "bg-primary text-primary-foreground"
                          : "bg-background text-foreground border-border"
                      } transition-all`}
                      onClick={() => setFormData({ ...formData, type: type.value })}
                    >
                      {type.label}
                    </Button>
                  ))}
                </div>
              </div>

              {/* 오른쪽: 사진 추가 */}
              <div className="space-y-2">
                <Label className="text-card-foreground">사진</Label>
                {!imagePreview ? (
                  <div className="space-y-2">
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full h-20 border-2 border-dashed border-border hover:border-primary transition-all bg-transparent"
                      onClick={startCamera}
                    >
                      <div className="flex flex-col items-center gap-1">
                        <Camera className="h-6 w-6" />
                        <span className="text-sm">사진 추가</span>
                      </div>
                    </Button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={handleFileSelect}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full bg-transparent"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      파일 선택
                    </Button>
                  </div>
                ) : (
                  <div className="relative">
                    <img
                      src={imagePreview || "/placeholder.svg"}
                      alt="Preview"
                      className="w-full h-32 object-cover rounded-lg border border-border"
                    />
                    <Button
                      type="button"
                      variant="destructive"
                      size="icon"
                      className="absolute top-2 right-2 h-6 w-6"
                      onClick={removeImage}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
            </div>

            {error && (
              <div className="text-red-500 text-sm bg-red-50 p-3 rounded-md border border-red-200">{error}</div>
            )}

            <div className="flex justify-end gap-3 pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                disabled={isLoading}
                className="border-border text-foreground bg-transparent"
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={isLoading}
                className="bg-primary hover:bg-primary/90 text-primary-foreground min-w-[80px]"
              >
                {isLoading ? (
                  <div className="flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    저장 중...
                  </div>
                ) : showSuccess ? (
                  <div className="flex items-center gap-2">
                    <Check className="h-4 w-4" />
                    완료!
                  </div>
                ) : (
                  "저장"
                )}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {showCamera && (
        <Dialog open={showCamera} onOpenChange={stopCamera}>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>사진 촬영</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <video ref={videoRef} autoPlay playsInline className="w-full rounded-lg border border-border" />
              <div className="flex gap-2">
                <Button type="button" onClick={capturePhoto} className="flex-1">
                  사진 촬영
                </Button>
                <Button type="button" variant="outline" onClick={stopCamera} className="flex-1 bg-transparent">
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
