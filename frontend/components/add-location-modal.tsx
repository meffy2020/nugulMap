"use client"

import type React from "react"
import { useState, useEffect } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Loader2, Check } from "lucide-react"
import { createZone, type SmokingZone, type CreateZonePayload } from "@/lib/api"

// Zod를 사용한 폼 유효성 검사 스키마 정의
const formSchema = z.object({
  address: z.string().min(5, { message: "주소는 5자 이상이어야 합니다." }),
  description: z.string().optional(),
  region: z.string().min(2, { message: "지역을 입력해주세요." }),
  type: z.string().min(1, { message: "유형을 선택해주세요." }),
  subtype: z.string().optional(),
  latitude: z.number(),
  longitude: z.number(),

  size: z.string().min(1, { message: "크기를 선택해주세요." }), // size 필드 추가
});

interface AddLocationModalProps {
  isOpen: boolean
  onClose: () => void
  onZoneCreated?: (zone: SmokingZone) => void
}

export function AddLocationModal({ isOpen, onClose, onZoneCreated }: AddLocationModalProps) {
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      address: "",
      description: "",
      region: "",
      type: "",
      subtype: "",
      latitude: 37.5665,
      longitude: 126.9780,
      size: "중형",
    },
  });

  // 모달이 열릴 때 현재 위치를 가져와 폼에 설정
  useEffect(() => {
    if (isOpen) {
      form.reset(); // 폼 초기화
      setError(null);
      setImageFile(null);
      getCurrentLocationForForm();
    }
  }, [isOpen, form]);

  const getCurrentLocationForForm = () => {
    if (!navigator.geolocation) return;

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        try {
          const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}&accept-language=ko`);
          const data = await response.json();
          const address = data.display_name || `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
          
          form.setValue("latitude", latitude);
          form.setValue("longitude", longitude);
          form.setValue("address", address);
        } catch (error) {
          console.error("Reverse geocoding failed:", error);
        }
      },
      (error) => console.error("Location error:", error)
    );
  };

  // 폼 제출 핸들러
  const onSubmit = async (values: z.infer<typeof formSchema>) => {
    setIsLoading(true);
    setError(null);

    try {
      // ‼️‼️‼️ 수정된 부분: DTO에 맞는 필드만 전송, date 필드 제거 ‼️‼️‼️
      const payload: CreateZonePayload = {
        ...values,
        user: "gemini-user", // 임시 사용자 이름
      };

      const newZone = await createZone(payload, imageFile || undefined);
      console.log("Zone created successfully:", newZone);

      setShowSuccess(true);
      onZoneCreated?.(newZone);

      setTimeout(() => {
        setShowSuccess(false);
        onClose();
      }, 1500);

    } catch (err) {
      console.error("Error creating zone:", err);
      setError(err instanceof Error ? err.message : "흡연구역 생성에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md bg-card border-border">
        <DialogHeader>
          <DialogTitle className="text-card-foreground">신규 흡연구역 등록</DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label htmlFor="address">주소</Label>
            <Input id="address" {...form.register("address")} placeholder="상세 주소를 입력하세요" />
            {form.formState.errors.address && <p className="text-red-500 text-xs mt-1">{form.formState.errors.address.message}</p>}
          </div>

          <div>
            <Label htmlFor="region">지역</Label>
            <Input id="region" {...form.register("region")} placeholder="지역을 입력하세요 (예: 서울시 강남구)" />
            {form.formState.errors.region && <p className="text-red-500 text-xs mt-1">{form.formState.errors.region.message}</p>}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="type">유형</Label>
              <Select onValueChange={(value) => form.setValue("type", value)} defaultValue={form.getValues("type")}>
                <SelectTrigger><SelectValue placeholder="유형을 선택하세요" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="지정구역">지정구역</SelectItem>
                  <SelectItem value="일반구역">일반구역</SelectItem>
                </SelectContent>
              </Select>
              {form.formState.errors.type && <p className="text-red-500 text-xs mt-1">{form.formState.errors.type.message}</p>}
            </div>
            <div>
              <Label htmlFor="size">크기</Label>
              <Select onValueChange={(value) => form.setValue("size", value)} defaultValue={form.getValues("size")}>
                <SelectTrigger><SelectValue placeholder="크기" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="소형">소형</SelectItem>
                  <SelectItem value="중형">중형</SelectItem>
                  <SelectItem value="대형">대형</SelectItem>
                </SelectContent>
              </Select>
              {form.formState.errors.size && <p className="text-red-500 text-xs mt-1">{form.formState.errors.size.message}</p>}
            </div>
          </div>

          <div>
            <Label htmlFor="description">상세 설명</Label>
            <Textarea id="description" {...form.register("description")} placeholder="상세 설명을 입력하세요" />
          </div>

          <div>
            <Label htmlFor="image">이미지</Label>
            <Input id="image" type="file" onChange={(e) => setImageFile(e.target.files ? e.target.files[0] : null)} />
          </div>

          {error && (
            <div className="text-red-500 text-sm p-3 rounded-md border border-red-200">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-4">
            <Button type="button" variant="outline" onClick={() => onClose()} disabled={isLoading}>취소</Button>
            <Button type="submit" disabled={isLoading} className="min-w-[80px]">
              {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : showSuccess ? <Check className="h-4 w-4" /> : "저장"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}