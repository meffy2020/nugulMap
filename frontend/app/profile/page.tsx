"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ArrowLeft, Camera, MapPin, Calendar, Edit3, Save, X } from "lucide-react"
import Link from "next/link"
import Image from "next/image"

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
  const [isEditing, setIsEditing] = useState(false)
  const [userInfo, setUserInfo] = useState({
    name: "김철수",
    email: "kim@example.com",
    nickname: "흡연왕",
    bio: "깨끗한 흡연 문화를 만들어가는 너굴맵 사용자입니다.",
    joinDate: "2024-01-15",
    totalZones: 12,
    profileImage: "/neutral-user-avatar.png",
  })

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
  ])

  const handleSave = () => {
    // TODO: Implement API call to update user profile
    console.log("[v0] Saving user profile:", userInfo)
    setIsEditing(false)
  }

  const handleCancel = () => {
    // Reset form or fetch original data
    setIsEditing(false)
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
                  {!isEditing ? (
                    <Button onClick={() => setIsEditing(true)} variant="outline" size="sm">
                      <Edit3 className="h-4 w-4 mr-2" />
                      편집
                    </Button>
                  ) : (
                    <div className="flex gap-2">
                      <Button onClick={handleSave} size="sm">
                        <Save className="h-4 w-4 mr-2" />
                        저장
                      </Button>
                      <Button onClick={handleCancel} variant="outline" size="sm">
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
                      <AvatarImage src={userInfo.profileImage || "/placeholder.svg"} alt={userInfo.name} />
                      <AvatarFallback className="text-2xl">{userInfo.name[0]}</AvatarFallback>
                    </Avatar>
                    {isEditing && (
                      <Button
                        size="sm"
                        className="absolute -bottom-2 -right-2 h-8 w-8 rounded-full p-0"
                        variant="secondary"
                      >
                        <Camera className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                  <div className="flex-1 space-y-2">
                    {isEditing ? (
                      <div className="space-y-4">
                        <div>
                          <Label htmlFor="name">이름</Label>
                          <Input
                            id="name"
                            value={userInfo.name}
                            onChange={(e) => setUserInfo({ ...userInfo, name: e.target.value })}
                          />
                        </div>
                        <div>
                          <Label htmlFor="nickname">닉네임</Label>
                          <Input
                            id="nickname"
                            value={userInfo.nickname}
                            onChange={(e) => setUserInfo({ ...userInfo, nickname: e.target.value })}
                          />
                        </div>
                      </div>
                    ) : (
                      <>
                        <h2 className="text-2xl font-bold">{userInfo.name}</h2>
                        <p className="text-muted-foreground">@{userInfo.nickname}</p>
                        <div className="flex items-center gap-4 text-sm text-muted-foreground">
                          <div className="flex items-center gap-1">
                            <Calendar className="h-4 w-4" />
                            {userInfo.joinDate} 가입
                          </div>
                          <div className="flex items-center gap-1">
                            <MapPin className="h-4 w-4" />
                            {userInfo.totalZones}개 장소 등록
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
                    value={userInfo.email}
                    disabled={!isEditing}
                    onChange={(e) => setUserInfo({ ...userInfo, email: e.target.value })}
                    className={!isEditing ? "bg-muted" : ""}
                  />
                </div>

                <div>
                  <Label htmlFor="bio">소개</Label>
                  <Textarea
                    id="bio"
                    value={userInfo.bio}
                    disabled={!isEditing}
                    onChange={(e) => setUserInfo({ ...userInfo, bio: e.target.value })}
                    className={!isEditing ? "bg-muted" : ""}
                    rows={3}
                  />
                </div>
              </CardContent>
            </Card>

            {/* Statistics Card */}
            <Card>
              <CardHeader>
                <CardTitle>활동 통계</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="space-y-2">
                    <div className="text-2xl font-bold text-primary">{userInfo.totalZones}</div>
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
            </Card>
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
                                src={zone.image || "/placeholder.svg"}
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
