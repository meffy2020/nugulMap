import { fireEvent, render, screen, waitFor } from "@testing-library/react-native"
import { Alert, Image } from "react-native"
import { ProfileModal } from "./ProfileModal"
import {
  completeProfileSetup,
  deleteZone,
  fetchMyZones,
  getImageUrl,
  updateUserProfile,
} from "../services/nugulApi"
import type { SmokingZone, UserProfile } from "../types"

jest.mock("../services/nugulApi", () => ({
  completeProfileSetup: jest.fn(),
  deleteZone: jest.fn(),
  fetchMyZones: jest.fn(),
  getImageUrl: jest.fn((image: string | null) => (image ? `https://cdn.test/${image}` : null)),
  updateUserProfile: jest.fn(),
}))

const mockedCompleteProfileSetup = jest.mocked(completeProfileSetup)
const mockedFetchMyZones = jest.mocked(fetchMyZones)
const mockedDeleteZone = jest.mocked(deleteZone)
const mockedGetImageUrl = jest.mocked(getImageUrl)
const mockedUpdateUserProfile = jest.mocked(updateUserProfile)

const user: UserProfile = {
  id: 12,
  email: "tester@nugulmap.com",
  nickname: "tester",
  profileImage: null,
  createdAt: "2024-01-10T00:00:00.000Z",
}

const zones: SmokingZone[] = [
  {
    id: 1,
    region: "서울특별시",
    type: "실외",
    subtype: "시청 앞",
    description: "설명",
    latitude: 37.5665,
    longitude: 126.978,
    address: "서울특별시 중구 세종대로 110",
    user: "tester",
    image: "zones/city-hall.jpg",
  },
  {
    id: 2,
    region: "서울특별시",
    type: "실내",
    subtype: "종로",
    description: "설명",
    latitude: 37.5705,
    longitude: 126.982,
    address: "서울특별시 종로구 세종대로 1",
    user: "tester",
    image: null,
  },
]

describe("ProfileModal", () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockedCompleteProfileSetup.mockResolvedValue(user)
    mockedGetImageUrl.mockImplementation((image: string | null | undefined) =>
      image ? `https://cdn.test/${image}` : null,
    )
    mockedUpdateUserProfile.mockResolvedValue(user)
  })

  it("submits profile setup when login is complete but profile is missing", async () => {
    const onProfileUpdated = jest.fn().mockResolvedValue(undefined)

    render(
      <ProfileModal
        visible
        user={{ ...user, nickname: null }}
        accessToken="token-123"
        onClose={jest.fn()}
        onClearToken={jest.fn().mockResolvedValue(undefined)}
        onSocialLogin={jest.fn().mockResolvedValue(undefined)}
        needsProfileSetup
        onProfileUpdated={onProfileUpdated}
        authMessage="로그인은 완료되었지만 추가 프로필 설정이 필요합니다."
        onClearAuthMessage={jest.fn()}
        isAuthenticating={false}
      />,
    )

    fireEvent.changeText(screen.getByPlaceholderText("2~20자 닉네임"), "새닉네임")
    fireEvent.press(screen.getByText("프로필 설정 완료"))

    await waitFor(() => {
      expect(mockedCompleteProfileSetup).toHaveBeenCalledWith(
        { nickname: "새닉네임" },
        "token-123",
        null,
      )
    })
    expect(onProfileUpdated).toHaveBeenCalledTimes(1)
  })

  it("loads my zones and renders image thumbnail and placeholder states", async () => {
    mockedFetchMyZones.mockResolvedValue(zones)

    render(
      <ProfileModal
        visible
        user={user}
        accessToken="token-123"
        onClose={jest.fn()}
        onClearToken={jest.fn().mockResolvedValue(undefined)}
        onSocialLogin={jest.fn().mockResolvedValue(undefined)}
        authMessage={null}
        onClearAuthMessage={jest.fn()}
        isAuthenticating={false}
      />,
    )

    expect(screen.getByText("불러오는 중...")).toBeTruthy()

    await screen.findByText("시청 앞")
    await screen.findByText("종로")

    expect(mockedFetchMyZones).toHaveBeenCalledWith("token-123")
    expect(screen.getByText("내가 등록한 장소")).toBeTruthy()
    expect(screen.getAllByText("수정")).toHaveLength(2)
    expect(screen.getAllByText("삭제")).toHaveLength(2)

    const images = screen.UNSAFE_getAllByType(Image)
    expect(images).toHaveLength(2)
    expect(images[1].props.source).toEqual({ uri: "https://cdn.test/zones/city-hall.jpg" })
  })

  it("forwards edit requests and confirms delete actions", async () => {
    const onEditZone = jest.fn()
    mockedFetchMyZones.mockResolvedValue([zones[0]])
    mockedDeleteZone.mockResolvedValue(undefined)

    const alertSpy = jest.spyOn(Alert, "alert").mockImplementation((title, message, buttons) => {
      if (title === "삭제 확인") {
        const deleteButton = buttons?.find((button) => button.style === "destructive")
        deleteButton?.onPress?.()
      }
    })

    render(
      <ProfileModal
        visible
        user={user}
        accessToken="token-123"
        onClose={jest.fn()}
        onClearToken={jest.fn().mockResolvedValue(undefined)}
        onSocialLogin={jest.fn().mockResolvedValue(undefined)}
        onEditZone={onEditZone}
        authMessage={null}
        onClearAuthMessage={jest.fn()}
        isAuthenticating={false}
      />,
    )

    await screen.findByText("시청 앞")

    fireEvent.press(screen.getAllByText("수정")[0])
    expect(onEditZone).toHaveBeenCalledWith(zones[0])

    fireEvent.press(screen.getAllByText("삭제")[0])

    await waitFor(() => {
      expect(mockedDeleteZone).toHaveBeenCalledWith(1, "token-123")
    })

    expect(alertSpy).toHaveBeenCalledWith(
      "삭제 확인",
      "\"시청 앞\"를 삭제할까요?",
      expect.any(Array),
    )

    alertSpy.mockRestore()
  })
})
