"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  testHealthCheck,
  testGetUsers,
  testCreateUser,
  testGetUser,
  testGetZones,
  testCreateZone,
  testGetZone,
  testSearchZones,
  testSearchNearbyZones,
  testUploadImage,
  testStorage,
  testCheckOAuth2Env,
  testGetOAuth2LoginUrls,
  testValidateToken,
  testRefreshToken,
  testGetCurrentUser,
  testGetCookies,
  type TestApiResponse,
} from "@/lib/api"

export default function TestPage() {
  const [results, setResults] = useState<Record<string, any>>({})
  const [loading, setLoading] = useState<Record<string, boolean>>({})
  const [formData, setFormData] = useState({
    userId: "",
    zoneId: "",
    keyword: "",
    lat: "37.5665",
    lon: "126.9780",
    radius: "1000",
    token: "",
    refreshToken: "",
    email: "",
    nickname: "",
    password: "",
    address: "",
    description: "",
    region: "서울특별시",
    type: "흡연구역",
    subtype: "실외",
    size: "중형",
    latitude: "37.5665",
    longitude: "126.9780",
    creator: "",
  })

  const handleApiCall = async (
    key: string,
    apiCall: () => Promise<TestApiResponse>
  ) => {
    setLoading((prev) => ({ ...prev, [key]: true }))
    try {
      const result = await apiCall()
      setResults((prev) => ({ ...prev, [key]: result }))
    } catch (error: any) {
      setResults((prev) => ({
        ...prev,
        [key]: { success: false, error: error.message },
      }))
    } finally {
      setLoading((prev) => ({ ...prev, [key]: false }))
    }
  }

  const handleFileUpload = async (
    key: string,
    apiCall: (file: File, type?: string) => Promise<TestApiResponse>
  ) => {
    const input = document.createElement("input")
    input.type = "file"
    input.accept = "image/*"
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      setLoading((prev) => ({ ...prev, [key]: true }))
      try {
        const result = await apiCall(file, "PROFILE")
        setResults((prev) => ({ ...prev, [key]: result }))
      } catch (error: any) {
        setResults((prev) => ({
          ...prev,
          [key]: { success: false, error: error.message },
        }))
      } finally {
        setLoading((prev) => ({ ...prev, [key]: false }))
      }
    }
    input.click()
  }

  const handleCreateZone = async () => {
    const formDataObj = new FormData()
    formDataObj.append("address", formData.address)
    formDataObj.append("description", formData.description || "")
    formDataObj.append("region", formData.region)
    formDataObj.append("type", formData.type)
    formDataObj.append("subtype", formData.subtype)
    formDataObj.append("size", formData.size)
    formDataObj.append("latitude", formData.latitude)
    formDataObj.append("longitude", formData.longitude)
    formDataObj.append("creator", formData.creator || "테스트유저")

    setLoading((prev) => ({ ...prev, createZone: true }))
    try {
      const result = await testCreateZone(formDataObj)
      setResults((prev) => ({ ...prev, createZone: result }))
    } catch (error: any) {
      setResults((prev) => ({
        ...prev,
        createZone: { success: false, error: error.message },
      }))
    } finally {
      setLoading((prev) => ({ ...prev, createZone: false }))
    }
  }

  return (
    <div className="min-h-screen bg-background p-8">
      <div className="max-w-7xl mx-auto">
        <h1 className="text-3xl font-bold mb-8">API 테스트 페이지</h1>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* 헬스 체크 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">헬스 체크</h2>
            <Button
              onClick={() => handleApiCall("health", testHealthCheck)}
              disabled={loading.health}
            >
              {loading.health ? "로딩 중..." : "헬스 체크"}
            </Button>
            {results.health && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(results.health, null, 2)}
              </pre>
            )}
          </Card>

          {/* 사용자 관련 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">사용자 API</h2>
            <div className="space-y-2">
              <Button
                onClick={() => handleApiCall("getUsers", testGetUsers)}
                disabled={loading.getUsers}
                className="w-full"
              >
                {loading.getUsers ? "로딩 중..." : "사용자 목록 조회"}
              </Button>
              <div className="flex gap-2">
                <input
                  type="number"
                  placeholder="사용자 ID"
                  value={formData.userId}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, userId: e.target.value }))
                  }
                  className="flex-1 px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("getUser", () =>
                      testGetUser(Number(formData.userId))
                    )
                  }
                  disabled={loading.getUser || !formData.userId}
                >
                  조회
                </Button>
              </div>
              <div className="space-y-2">
                <input
                  type="email"
                  placeholder="이메일"
                  value={formData.email}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, email: e.target.value }))
                  }
                  className="w-full px-3 py-2 border rounded"
                />
                <input
                  type="text"
                  placeholder="닉네임"
                  value={formData.nickname}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      nickname: e.target.value,
                    }))
                  }
                  className="w-full px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("createUser", () =>
                      testCreateUser({
                        email: formData.email,
                        nickname: formData.nickname,
                        password: formData.password,
                      })
                    )
                  }
                  disabled={loading.createUser || !formData.email || !formData.nickname}
                  className="w-full"
                >
                  {loading.createUser ? "로딩 중..." : "사용자 생성"}
                </Button>
              </div>
            </div>
            {(results.getUsers ||
              results.getUser ||
              results.createUser) && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(
                  results.getUsers || results.getUser || results.createUser,
                  null,
                  2
                )}
              </pre>
            )}
          </Card>

          {/* Zone 관련 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">Zone API</h2>
            <div className="space-y-2">
              <Button
                onClick={() => handleApiCall("getZones", testGetZones)}
                disabled={loading.getZones}
                className="w-full"
              >
                {loading.getZones ? "로딩 중..." : "Zone 목록 조회"}
              </Button>
              <div className="flex gap-2">
                <input
                  type="number"
                  placeholder="Zone ID"
                  value={formData.zoneId}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, zoneId: e.target.value }))
                  }
                  className="flex-1 px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("getZone", () =>
                      testGetZone(Number(formData.zoneId))
                    )
                  }
                  disabled={loading.getZone || !formData.zoneId}
                >
                  조회
                </Button>
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="검색 키워드"
                  value={formData.keyword}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, keyword: e.target.value }))
                  }
                  className="flex-1 px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("searchZones", () =>
                      testSearchZones(formData.keyword)
                    )
                  }
                  disabled={loading.searchZones || !formData.keyword}
                >
                  검색
                </Button>
              </div>
              <Button
                onClick={() =>
                  handleApiCall("searchNearbyZones", () =>
                    testSearchNearbyZones(
                      Number(formData.lat),
                      Number(formData.lon),
                      Number(formData.radius)
                    )
                  )
                }
                disabled={loading.searchNearbyZones}
                className="w-full"
              >
                {loading.searchNearbyZones ? "로딩 중..." : "반경 검색"}
              </Button>
            </div>
            {(results.getZones ||
              results.getZone ||
              results.searchZones ||
              results.searchNearbyZones) && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(
                  results.getZones ||
                    results.getZone ||
                    results.searchZones ||
                    results.searchNearbyZones,
                  null,
                  2
                )}
              </pre>
            )}
          </Card>

          {/* Zone 생성 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">Zone 생성</h2>
            <div className="space-y-2">
              <input
                type="text"
                placeholder="주소"
                value={formData.address}
                onChange={(e) =>
                  setFormData((prev) => ({ ...prev, address: e.target.value }))
                }
                className="w-full px-3 py-2 border rounded"
              />
              <input
                type="text"
                placeholder="설명 (선택)"
                value={formData.description}
                onChange={(e) =>
                  setFormData((prev) => ({
                    ...prev,
                    description: e.target.value,
                  }))
                }
                className="w-full px-3 py-2 border rounded"
              />
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  placeholder="위도"
                  value={formData.latitude}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      latitude: e.target.value,
                    }))
                  }
                  className="px-3 py-2 border rounded"
                />
                <input
                  type="text"
                  placeholder="경도"
                  value={formData.longitude}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      longitude: e.target.value,
                    }))
                  }
                  className="px-3 py-2 border rounded"
                />
              </div>
              <Button
                onClick={handleCreateZone}
                disabled={loading.createZone || !formData.address}
                className="w-full"
              >
                {loading.createZone ? "로딩 중..." : "Zone 생성"}
              </Button>
            </div>
            {results.createZone && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(results.createZone, null, 2)}
              </pre>
            )}
          </Card>

          {/* 이미지 및 Storage */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">이미지 & Storage</h2>
            <div className="space-y-2">
              <Button
                onClick={() =>
                  handleFileUpload("uploadImage", testUploadImage)
                }
                disabled={loading.uploadImage}
                className="w-full"
              >
                {loading.uploadImage ? "로딩 중..." : "이미지 업로드"}
              </Button>
              <Button
                onClick={() => handleFileUpload("testStorage", testStorage)}
                disabled={loading.testStorage}
                className="w-full"
              >
                {loading.testStorage ? "로딩 중..." : "Storage 테스트"}
              </Button>
            </div>
            {(results.uploadImage || results.testStorage) && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(
                  results.uploadImage || results.testStorage,
                  null,
                  2
                )}
              </pre>
            )}
          </Card>

          {/* OAuth2 관련 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">OAuth2</h2>
            <div className="space-y-2">
              <Button
                onClick={() =>
                  handleApiCall("checkOAuth2Env", testCheckOAuth2Env)
                }
                disabled={loading.checkOAuth2Env}
                className="w-full"
              >
                {loading.checkOAuth2Env ? "로딩 중..." : "환경 변수 확인"}
              </Button>
              <Button
                onClick={() =>
                  handleApiCall("getOAuth2LoginUrls", testGetOAuth2LoginUrls)
                }
                disabled={loading.getOAuth2LoginUrls}
                className="w-full"
              >
                {loading.getOAuth2LoginUrls ? "로딩 중..." : "로그인 URL 조회"}
              </Button>
            </div>
            {(results.checkOAuth2Env || results.getOAuth2LoginUrls) && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(
                  results.checkOAuth2Env || results.getOAuth2LoginUrls,
                  null,
                  2
                )}
              </pre>
            )}
          </Card>

          {/* 인증 관련 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">인증</h2>
            <div className="space-y-2">
              <Button
                onClick={() =>
                  handleApiCall("getCurrentUser", testGetCurrentUser)
                }
                disabled={loading.getCurrentUser}
                className="w-full"
              >
                {loading.getCurrentUser ? "로딩 중..." : "현재 사용자 조회"}
              </Button>
              <Button
                onClick={() => handleApiCall("getCookies", testGetCookies)}
                disabled={loading.getCookies}
                className="w-full"
              >
                {loading.getCookies ? "로딩 중..." : "쿠키 정보 조회"}
              </Button>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="토큰"
                  value={formData.token}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, token: e.target.value }))
                  }
                  className="flex-1 px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("validateToken", () =>
                      testValidateToken(formData.token)
                    )
                  }
                  disabled={loading.validateToken || !formData.token}
                >
                  검증
                </Button>
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Refresh Token"
                  value={formData.refreshToken}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      refreshToken: e.target.value,
                    }))
                  }
                  className="flex-1 px-3 py-2 border rounded"
                />
                <Button
                  onClick={() =>
                    handleApiCall("refreshToken", () =>
                      testRefreshToken(formData.refreshToken)
                    )
                  }
                  disabled={loading.refreshToken || !formData.refreshToken}
                >
                  재발급
                </Button>
              </div>
            </div>
            {(results.getCurrentUser ||
              results.getCookies ||
              results.validateToken ||
              results.refreshToken) && (
              <pre className="mt-4 p-4 bg-muted rounded text-sm overflow-auto">
                {JSON.stringify(
                  results.getCurrentUser ||
                    results.getCookies ||
                    results.validateToken ||
                    results.refreshToken,
                  null,
                  2
                )}
              </pre>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
