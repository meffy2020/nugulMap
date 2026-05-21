import { fireEvent, render, screen, waitFor } from "@testing-library/react-native"
import { Alert } from "react-native"
import { ZoneReviewModal } from "./ZoneReviewModal"
import { createZoneReview, fetchZoneReviews, getImageUrl } from "../services/nugulApi"
import type { SmokingZone, UserProfile, ZoneReview } from "../types"

jest.mock("../services/nugulApi", () => ({
  createZoneReview: jest.fn(),
  fetchZoneReviews: jest.fn(),
  getImageUrl: jest.fn(() => null),
}))

const mockedFetchZoneReviews = jest.mocked(fetchZoneReviews)
const mockedCreateZoneReview = jest.mocked(createZoneReview)
const mockedGetImageUrl = jest.mocked(getImageUrl)

const zone: SmokingZone = {
  id: 7,
  region: "서울특별시",
  type: "실외",
  subtype: "시청 앞",
  description: "현장 후기 테스트",
  latitude: 37.5665,
  longitude: 126.978,
  address: "서울특별시 중구 세종대로 110",
  user: "tester@nugulmap.com",
  image: null,
}

const user: UserProfile = {
  id: 12,
  email: "tester@nugulmap.com",
  nickname: "tester",
  profileImage: null,
  createdAt: "2026-04-06T09:00:00.000Z",
}

const reviews: ZoneReview[] = [
  {
    id: 1,
    zoneId: 7,
    authorId: 3,
    authorNickname: "리뷰어A",
    authorEmail: "a@test.com",
    authorProfileImage: null,
    content: "깔끔하고 찾기 쉬워요.",
    createdAt: "2026-04-01T09:30:00.000Z",
    updatedAt: "2026-04-01T09:30:00.000Z",
  },
]

describe("ZoneReviewModal", () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockedGetImageUrl.mockReturnValue(null)
  })

  it("loads and renders existing reviews", async () => {
    mockedFetchZoneReviews.mockResolvedValue(reviews)

    render(
      <ZoneReviewModal
        visible
        zone={zone}
        accessToken="token-123"
        user={user}
        onClose={jest.fn()}
      />,
    )

    expect(screen.getByText("리뷰를 불러오는 중...")).toBeTruthy()

    await screen.findByText("깔끔하고 찾기 쉬워요.")
    expect(screen.getByText("리뷰어A")).toBeTruthy()
    expect(mockedFetchZoneReviews).toHaveBeenCalledWith(7)
  })

  it("shows an empty state when there are no reviews", async () => {
    mockedFetchZoneReviews.mockResolvedValue([])

    render(
      <ZoneReviewModal
        visible
        zone={zone}
        accessToken="token-123"
        user={user}
        onClose={jest.fn()}
      />,
    )

    await screen.findByText("아직 등록된 리뷰가 없습니다. 첫 리뷰를 남겨보세요.")
  })

  it("submits a new review and prepends it to the list", async () => {
    mockedFetchZoneReviews.mockResolvedValue(reviews)
    mockedCreateZoneReview.mockResolvedValue({
      id: 2,
      zoneId: 7,
      authorId: 12,
      authorNickname: "tester",
      authorEmail: "tester@nugulmap.com",
      authorProfileImage: null,
      content: "새 리뷰입니다.",
      createdAt: "2026-04-06T12:00:00.000Z",
      updatedAt: "2026-04-06T12:00:00.000Z",
    })

    render(
      <ZoneReviewModal
        visible
        zone={zone}
        accessToken="token-999"
        user={user}
        onClose={jest.fn()}
      />,
    )

    await screen.findByText("깔끔하고 찾기 쉬워요.")

    fireEvent.changeText(screen.getByPlaceholderText("접근성, 청결도, 혼잡도 등을 남겨주세요."), " 새 리뷰입니다. ")
    fireEvent.press(screen.getByTestId("zone-review-submit"))

    await waitFor(() => {
      expect(mockedCreateZoneReview).toHaveBeenCalledWith(7, { content: "새 리뷰입니다." }, "token-999")
    })

    await screen.findByText("새 리뷰입니다.")
    expect(screen.getByText("tester")).toBeTruthy()
  })

  it("alerts when submit is attempted without login", async () => {
    mockedFetchZoneReviews.mockResolvedValue([])
    const alertSpy = jest.spyOn(Alert, "alert").mockImplementation(jest.fn())

    render(<ZoneReviewModal visible zone={zone} accessToken={null} user={null} onClose={jest.fn()} />)

    await screen.findByText("아직 등록된 리뷰가 없습니다. 첫 리뷰를 남겨보세요.")
    fireEvent.changeText(screen.getByPlaceholderText("로그인 후 리뷰를 작성할 수 있습니다."), "로그인 필요")
    fireEvent.press(screen.getByTestId("zone-review-submit"))

    expect(alertSpy).not.toHaveBeenCalled()
    expect(mockedCreateZoneReview).not.toHaveBeenCalled()

    alertSpy.mockRestore()
  })
})
