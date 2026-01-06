"use client"

import { useState, useEffect } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ArrowLeft, Camera, MapPin, Calendar, Edit3, Save, X, Loader2 } from "lucide-react"
import Link from "next/link"
import Image from "next/image"
import { useRouter } from "next/navigation"

import { fetchUserProfile, updateUserNickname, updateUserProfileImage, getCurrentUser, getImageUrl, type UserProfile, type SmokingZone } from "@/lib/api"


const nicknameSchema = z.object({
  nickname: z.string().min(2, { message: "닉네임은 2자 이상이어야 합니다." }).max(20, { message: "닉네임은 20자 이하여야 합니다." }),
});

interface UserZone {
  id: number
  address: string
  description: string
  type: string
  subtype: string
  size: string
  date: string
  image?: string
}

export default function ProfilePage() {
  const router = useRouter()
  const [userId, setUserId] = useState<number | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [isLoadingProfile, setIsLoadingProfile] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [isEditingNickname, setIsEditingNickname] = useState(false);
  const [isUpdatingNickname, setIsUpdatingNickname] = useState(false);
  const [isUpdatingProfileImage, setIsUpdatingProfileImage] = useState(false);

  // 닉네임 폼 설정
  const nicknameForm = useForm<z.infer<typeof nicknameSchema>>({
    resolver: zodResolver(nicknameSchema),
    defaultValues: { nickname: "" },
  });

  // 현재 사용자 정보 가져오기 및 프로필 불러오기
  useEffect(() => {
    const loadUserProfile = async () => {
      setIsLoadingProfile(true);
      try {
        // 1. 현재 인증된 사용자 정보 가져오기
        const currentUser = await getCurrentUser();
        if (!currentUser) {
          setProfileError("로그인이 필요합니다.");
          router.push("/login");
          return;
        }
        
        setUserId(currentUser.id);
        
        // 2. 사용자 프로필 상세 정보 가져오기
        const profile = await fetchUserProfile(currentUser.id);
        setUserProfile(profile);
        nicknameForm.reset({ nickname: profile.nickname }); // 폼 초기값 설정
      } catch (err) {
        console.error("Failed to fetch user profile:", err);
        setProfileError("프로필 정보를 불러오는데 실패했습니다.");
      } finally {
        setIsLoadingProfile(false);
      }
    };
    loadUserProfile();
  }, [nicknameForm]);

  // 닉네임 저장 핸들러
  const handleNicknameSave = async (values: z.infer<typeof nicknameSchema>) => {
    if (!userProfile || !userId) return;
    setIsUpdatingNickname(true);
    try {
      const updatedProfile = await updateUserNickname(userId, values.nickname);
      setUserProfile(updatedProfile);
      setIsEditingNickname(false);
    } catch (err) {
      console.error("Failed to update nickname:", err);
      alert("닉네임 업데이트에 실패했습니다.");
    } finally {
      setIsUpdatingNickname(false);
    }
  };

  // 프로필 이미지 변경 핸들러
  const handleProfileImageChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!userProfile || !userId || !event.target.files || event.target.files.length === 0) return;

    const file = event.target.files[0];
    setIsUpdatingProfileImage(true);
    try {
      await updateUserProfileImage(userId, file);
      // 이미지 업데이트 후 프로필 정보 다시 불러오기
      const updatedProfile = await fetchUserProfile(userId);
      setUserProfile(updatedProfile);
    } catch (err) {
      console.error("Failed to update profile image:", err);
      alert("프로필 이미지 업데이트에 실패했습니다.");
    } finally {
      setIsUpdatingProfileImage(false);
    }
  };

  // 내 등록 장소 (현재는 하드코딩)
  const [userZones] = useState<UserZone[]>([
    {
      id: 1,
      address: "서울특별시 중구 명동길 26",
      description: "명동역 근처 지정 흡연구역입니다. 실외 공간으로 환기가 잘 됩니다.",
      type: "지정구역",
      subtype: "흡연부스",
      size: "중형",
      date: "2024-01-15",
      image: "/modern-outdoor-smoking-booth.png",
    },
    {
      id: 2,
      address: "서울특별시 중구 을지로 281",
      description: "동대문디자인플라자 인근 흡연 공간입니다.",
      type: "일반구역",
      subtype: "야외공간",
      size: "대형",
      date: "2024-01-16",
      image: "/modern-building-smoking-area.png",
    },
  ]);

  if (isLoadingProfile) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <span className="ml-2 text-foreground">프로필 불러오는 중...</span>
      </div>
    );
  }

  if (profileError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background text-red-500">
        <p>Error: {profileError}</p>
      </div>
    );
  }

  if (!userProfile) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background text-muted-foreground">
        <p>사용자 프로필을 찾을 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <div className="sticky top-0 z-40 bg-background/95 backdrop-blur-md border-b border-border">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center gap-4">
            <Link href="/">
              <Button variant="ghost" size="sm" className="hover:bg-accent/50">
                <ArrowLeft className="h-4 w-4 mr-2" />
                지도로 돌아가기
              </Button>
            </Link>
            <h1 className="text-xl font-semibold">프로필</h1>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <Tabs defaultValue="profile" className="space-y-6">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="profile">프로필 정보</TabsTrigger>
            <TabsTrigger value="zones">내 등록 장소</TabsTrigger>
          </TabsList>

          <TabsContent value="profile" className="space-y-6">
            {/* Profile Header Card */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>프로필 정보</CardTitle>
                  {!isEditingNickname ? (
                    <Button onClick={() => setIsEditingNickname(true)} variant="outline" size="sm">
                      <Edit3 className="h-4 w-4 mr-2" />
                      편집
                    </Button>
                  ) : (
                    <div className="flex gap-2">
                      <Button onClick={nicknameForm.handleSubmit(handleNicknameSave)} size="sm" disabled={isUpdatingNickname}>
                        {isUpdatingNickname ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Save className="h-4 w-4 mr-2" />}
                        저장
                      </Button>
                      <Button onClick={() => {
                        setIsEditingNickname(false);
                        nicknameForm.reset({ nickname: userProfile.nickname }); // 변경사항 되돌리기
                      }} variant="outline" size="sm" disabled={isUpdatingNickname}>
                        <X className="h-4 w-4 mr-2" />
                        취소
                      </Button>
                    </div>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="flex items-center gap-6">
                  <div className="relative">
                    <Avatar className="h-24 w-24">
                      <AvatarImage src={getImageUrl(userProfile.profileImage) || "/placeholder.svg"} alt={userProfile.nickname} />
                      <AvatarFallback className="text-2xl">{userProfile.nickname[0]}</AvatarFallback>
                    </Avatar>
                    {isEditingNickname && (
                      <Label
                        htmlFor="profile-image-upload"
                        className="absolute -bottom-2 -right-2 h-8 w-8 rounded-full p-0 bg-secondary text-secondary-foreground flex items-center justify-center cursor-pointer hover:bg-secondary/80"
                      >
                        <Camera className="h-4 w-4" />
                        <Input
                          id="profile-image-upload"
                          type="file"
                          className="sr-only"
                          onChange={handleProfileImageChange}
                          disabled={isUpdatingProfileImage}
                        />
                      </Label>
                    )}
                  </div>
                  <div className="flex-1 space-y-2">
                    {isEditingNickname ? (
                      <div className="space-y-4">
                        <div>
                          <Label htmlFor="nickname">닉네임</Label>
                          <Input
                            id="nickname"
                            {...nicknameForm.register("nickname")}
                            disabled={isUpdatingNickname}
                          />
                          {nicknameForm.formState.errors.nickname && (
                            <p className="text-red-500 text-xs mt-1">{nicknameForm.formState.errors.nickname.message}</p>
                          )}
                        </div>
                      </div>
                    ) : (
                      <>
                        <h2 className="text-2xl font-bold">{userProfile.nickname}</h2>
                        <p className="text-muted-foreground">{userProfile.email}</p>
                        <div className="flex items-center gap-4 text-sm text-muted-foreground">
                          <div className="flex items-center gap-1">
                            <Calendar className="h-4 w-4" />
                            {userProfile.createdAt} 가입
                          </div>
                        </div>
                      </>
                    )}
                  </div>
                </div>

                <div>
                  <Label htmlFor="email">이메일</Label>
                  <Input
                    id="email"
                    type="email"
                    value={userProfile.email}
                    disabled
                    className="bg-muted"
                  />
                </div>

                {/* TODO: 소개 (bio) 필드는 백엔드 UserResponse에 없으므로 임시로 제거하거나 백엔드 구현 후 추가 */}
                {/* TODO: 활동 통계 필드도 백엔드 UserResponse에 없으므로 임시로 제거하거나 백엔드 구현 후 추가 */}
              </CardContent>
            </Card>

            {/* Statistics Card (임시 제거) */}
            {/* <Card>
              <CardHeader>
                <CardTitle>활동 통계</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="space-y-2">
                    <div className="text-2xl font-bold text-primary">{userProfile.totalZones}</div>
                    <div className="text-sm text-muted-foreground">등록한 장소</div>
                  </div>
                  <div className="space-y-2">
                    <div className="text-2xl font-bold text-accent">24</div>
                    <div className="text-sm text-muted-foreground">도움이 된 리뷰</div>
                  </div>
                  <div className="space-y-2">
                    <div className="text-2xl font-bold text-secondary">7</div>
                    <div className="text-sm text-muted-foreground">활동 일수</div>
                  </div>
                </div>
              </CardContent>
            </Card> */}
          </TabsContent>

          <TabsContent value="zones" className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>내가 등록한 흡연구역</CardTitle>
                <CardDescription>총 {userZones.length}개의 장소를 등록했습니다.</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4">
                  {userZones.map((zone) => (
                    <Card key={zone.id} className="hover:shadow-md transition-shadow">
                      <CardContent className="p-4">
                        <div className="flex gap-4">
                          {zone.image && (
                            <div className="flex-shrink-0">
                              <Image
                                src={getImageUrl(zone.image) || "/placeholder.svg"}
                                alt={zone.address}
                                width={120}
                                height={80}
                                className="rounded-lg object-cover"
                              />
                            </div>
                          )}
                          <div className="flex-1 space-y-2">
                            <div className="flex items-start justify-between">
                              <h3 className="font-medium">{zone.address}</h3>
                              <div className="flex gap-2">
                                <Badge variant="secondary">{zone.type}</Badge>
                                <Badge variant="outline">{zone.subtype}</Badge>
                              </div>
                            </div>
                            <p className="text-sm text-muted-foreground">{zone.description}</p>
                            <div className="flex items-center gap-4 text-xs text-muted-foreground">
                              <span>크기: {zone.size}</span>
                              <span>등록일: {zone.date}</span>
                            </div>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
