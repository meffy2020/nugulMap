import { fireEvent, render, screen } from "@testing-library/react-native"
import { ZoneDetailModal } from "./ZoneDetailModal"
import { getImageUrl } from "../services/nugulApi"

jest.mock("../services/nugulApi", () => ({
  getImageUrl: jest.fn(),
}))

const mockedGetImageUrl = getImageUrl as jest.MockedFunction<typeof getImageUrl>

const zone = {
  id: 7,
  region: "서울특별시",
  type: "실외",
  subtype: "시청 앞",
  description: "시청 근처 흡연구역입니다.",
  latitude: 37.5665,
  longitude: 126.978,
  address: "서울특별시 중구 세종대로 110",
  user: "tester@nugulmap.com",
  image: null,
}

const imageZone = {
  ...zone,
  id: 8,
  subtype: "강남역",
  image: "zones/test-zone.jpg",
}

describe("ZoneDetailModal", () => {
  it("renders image-present state and primary actions", () => {
    const onClose = jest.fn()
    const onOpenRoute = jest.fn()
    const onOpenShare = jest.fn()
    const onOpenReport = jest.fn()
    const onOpenReview = jest.fn()

    mockedGetImageUrl.mockReturnValue("https://cdn.example.com/zones/test-zone.jpg")

    render(
      <ZoneDetailModal
        zone={imageZone}
        onClose={onClose}
        onOpenRoute={onOpenRoute}
        onOpenShare={onOpenShare}
        onOpenReport={onOpenReport}
        onOpenReview={onOpenReview}
      />,
    )

    expect(screen.getByText("강남역")).toBeTruthy()
    expect(screen.getByText("현장 사진")).toBeTruthy()
    expect(screen.getByText("등록된 현장 사진")).toBeTruthy()
    expect(screen.getByText("37.566500, 126.978000")).toBeTruthy()

    fireEvent.press(screen.getByText("길찾기"))
    fireEvent.press(screen.getByText("공유"))
    fireEvent.press(screen.getByText("제보"))
    fireEvent.press(screen.getByText("리뷰"))

    expect(onOpenRoute).toHaveBeenCalledTimes(1)
    expect(onOpenShare).toHaveBeenCalledTimes(1)
    expect(onOpenReport).toHaveBeenCalledTimes(1)
    expect(onOpenReview).toHaveBeenCalledTimes(1)
  })

  it("renders image-absent state", () => {
    mockedGetImageUrl.mockReturnValue(null)

    render(
      <ZoneDetailModal
        zone={zone}
        onClose={jest.fn()}
        onOpenRoute={jest.fn()}
        onOpenShare={jest.fn()}
        onOpenReport={jest.fn()}
        onOpenReview={jest.fn()}
      />,
    )

    expect(screen.getByText("시청 앞")).toBeTruthy()
    expect(screen.getByText("사진 없음")).toBeTruthy()
    expect(screen.getByText("아직 등록된 현장 사진이 없습니다.")).toBeTruthy()
  })
})
