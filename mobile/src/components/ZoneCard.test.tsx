import { fireEvent, render, screen } from "@testing-library/react-native"
import { ZoneCard } from "./ZoneCard"

const zone = {
  id: 7,
  region: "서울특별시",
  type: "실외",
  subtype: "시청 앞",
  description: "흡연구역 설명",
  latitude: 37.56,
  longitude: 126.97,
  address: "서울특별시 중구 세종대로 110",
  user: "tester@nugulmap.com",
  image: null,
}

describe("ZoneCard", () => {
  it("triggers onSelect and onToggleFavorite", () => {
    const onSelect = jest.fn()
    const onToggleFavorite = jest.fn()

    render(
      <ZoneCard
        zone={zone}
        isFavorite={false}
        onSelect={onSelect}
        onToggleFavorite={onToggleFavorite}
      />,
    )

    fireEvent.press(screen.getByText("시청 앞"))
    fireEvent.press(screen.getByText("☆"))

    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onToggleFavorite).toHaveBeenCalledTimes(1)
  })
})
