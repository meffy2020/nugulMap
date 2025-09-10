"use client"

import type React from "react"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Loader2, Check } from "lucide-react"
import { apiService, type ZoneRequest } from "@/lib/api"

interface AddLocationModalProps {
  isOpen: boolean
  onClose: () => void
  onZoneCreated?: (zone: any) => void
}

export function AddLocationModal({ isOpen, onClose, onZoneCreated }: AddLocationModalProps) {
  const [formData, setFormData] = useState({
    region: "",
    type: "",
    subtype: "",
    description: "",
    latitude: "37.5665",
    longitude: "126.9780",
    size: "소형",
    address: "",
    user: "익명", // Default user for now
    image: "",
  })

  const [isLoading, setIsLoading] = useState(false)
  const [showSuccess, setShowSuccess] = useState(false)
  const [focusedField, setFocusedField] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isOpen && !formData.address) {
      getCurrentLocationForForm()
    }
  }, [isOpen])

  const getCurrentLocationForForm = async () => {
    try {
      if (!navigator.geolocation) return

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const { latitude, longitude } = position.coords

          // Get address from coordinates
          try {
            const response = await fetch(
              `https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}&accept-language=ko`,
            )
            const data = await response.json()
            const address = data.display_name || `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`

            setFormData((prev) => ({
              ...prev,
              latitude: latitude.toString(),
              longitude: longitude.toString(),
              address: address,
            }))
          } catch (error) {
            console.log("[v0] Reverse geocoding failed:", error)
            setFormData((prev) => ({
              ...prev,
              latitude: latitude.toString(),
              longitude: longitude.toString(),
              address: `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`,
            }))
          }
        },
        (error) => {
          console.log("[v0] Location error:", error)
        },
        {
          enableHighAccuracy: true,
          timeout: 5000,
          maximumAge: 60000,
        },
      )
    } catch (error) {
      console.log("[v0] Location error:", error)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    setError(null)

    try {
      const zoneRequest: ZoneRequest = {
        region: formData.region,
        type: formData.type,
        subtype: formData.subtype,
        description: formData.description,
        latitude: Number.parseFloat(formData.latitude),
        longitude: Number.parseFloat(formData.longitude),
        size: formData.size,
        address: formData.address,
        user: formData.user,
        image: formData.image || undefined,
      }

      const newZone = await apiService.createZone(zoneRequest)
      console.log("[v0] Zone created successfully:", newZone)

      setShowSuccess(true)
      onZoneCreated?.(newZone)

      // Show success state briefly before closing
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
      region: "",
      type: "",
      subtype: "",
      description: "",
      latitude: "37.5665",
      longitude: "126.9780",
      size: "소형",
      address: "",
      user: "익명",
      image: "",
    })
    setFocusedField(null)
    setError(null)
    onClose()
  }

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md bg-card border-border animate-in slide-in-from-top-4 fade-in duration-500 ease-out">
        <DialogHeader>
          <DialogTitle className="text-card-foreground">신규 흡연구역 등록</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="relative space-y-2">
            <Label
              htmlFor="region"
              className={`text-card-foreground transition-all duration-200 ${
                focusedField === "region" || formData.region ? "text-primary text-sm" : "text-muted-foreground"
              }`}
            >
              지역
            </Label>
            <Input
              id="region"
              value={formData.region}
              onChange={(e) => setFormData({ ...formData, region: e.target.value })}
              onFocus={() => setFocusedField("region")}
              onBlur={() => setFocusedField(null)}
              placeholder="지역을 입력하세요 (예: 서울시 강남구)"
              className={`bg-input border-border text-foreground transition-all duration-300 ${
                focusedField === "region"
                  ? "border-primary ring-2 ring-primary/20 shadow-lg"
                  : "hover:border-primary/50"
              }`}
            />
          </div>

          <div className="relative space-y-2">
            <Label
              htmlFor="address"
              className={`text-card-foreground transition-all duration-200 ${
                focusedField === "address" || formData.address ? "text-primary text-sm" : "text-muted-foreground"
              }`}
            >
              주소
            </Label>
            <Input
              id="address"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
              onFocus={() => setFocusedField("address")}
              onBlur={() => setFocusedField(null)}
              placeholder="상세 주소를 입력하세요"
              className={`bg-input border-border text-foreground transition-all duration-300 ${
                focusedField === "address"
                  ? "border-primary ring-2 ring-primary/20 shadow-lg"
                  : "hover:border-primary/50"
              }`}
            />
          </div>

          <div className="relative space-y-2">
            <Label htmlFor="type" className="text-card-foreground">
              유형
            </Label>
            <Select value={formData.type} onValueChange={(value) => setFormData({ ...formData, type: value })}>
              <SelectTrigger className="bg-input border-border text-foreground transition-all duration-300 hover:border-primary/50 focus:border-primary focus:ring-2 focus:ring-primary/20">
                <SelectValue placeholder="유형을 선택하세요" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="지정구역">지정구역</SelectItem>
                <SelectItem value="일반구역">일반구역</SelectItem>
                <SelectItem value="24시간">24시간</SelectItem>
                <SelectItem value="실외구역">실외구역</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="subtype" className="text-card-foreground">
                세부유형
              </Label>
              <Select value={formData.subtype} onValueChange={(value) => setFormData({ ...formData, subtype: value })}>
                <SelectTrigger className="bg-input border-border text-foreground">
                  <SelectValue placeholder="세부유형" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="흡연부스">흡연부스</SelectItem>
                  <SelectItem value="흡연실">흡연실</SelectItem>
                  <SelectItem value="야외공간">야외공간</SelectItem>
                  <SelectItem value="기타">기타</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="size" className="text-card-foreground">
                크기
              </Label>
              <Select value={formData.size} onValueChange={(value) => setFormData({ ...formData, size: value })}>
                <SelectTrigger className="bg-input border-border text-foreground">
                  <SelectValue placeholder="크기" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="소형">소형</SelectItem>
                  <SelectItem value="중형">중형</SelectItem>
                  <SelectItem value="대형">대형</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="relative space-y-2">
            <Label
              htmlFor="description"
              className={`text-card-foreground transition-all duration-200 ${
                focusedField === "description" || formData.description
                  ? "text-primary text-sm"
                  : "text-muted-foreground"
              }`}
            >
              상세 설명
            </Label>
            <Textarea
              id="description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              onFocus={() => setFocusedField("description")}
              onBlur={() => setFocusedField(null)}
              placeholder="상세 설명을 입력하세요"
              className={`bg-input border-border text-foreground min-h-[80px] transition-all duration-300 ${
                focusedField === "description"
                  ? "border-primary ring-2 ring-primary/20 shadow-lg"
                  : "hover:border-primary/50"
              }`}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="latitude" className="text-card-foreground">
                위도
              </Label>
              <Input
                id="latitude"
                value={formData.latitude}
                disabled
                className="bg-muted border-border text-muted-foreground"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="longitude" className="text-card-foreground">
                경도
              </Label>
              <Input
                id="longitude"
                value={formData.longitude}
                disabled
                className="bg-muted border-border text-muted-foreground"
              />
            </div>
          </div>

          {error && (
            <div className="text-red-500 text-sm bg-red-50 dark:bg-red-900/20 p-3 rounded-md border border-red-200 dark:border-red-800">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={handleCancel}
              disabled={isLoading}
              className="border-border text-foreground hover:bg-secondary bg-transparent transition-all duration-200 hover:scale-105 disabled:opacity-50"
            >
              취소
            </Button>
            <Button
              type="submit"
              disabled={isLoading}
              className="bg-primary hover:bg-primary/90 text-primary-foreground transition-all duration-200 hover:scale-105 hover:shadow-lg disabled:opacity-50 min-w-[80px]"
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
  )
}
