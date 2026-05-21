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

const zoneWithImage = {
  ...zone,
  id: 8,
  subtype: "강남역",
  image: "zones/test-zone.jpg",
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

  it("renders and handles a zone with an image", () => {
    const onSelect = jest.fn()
    const onToggleFavorite = jest.fn()

    render(
      <ZoneCard
        zone={zoneWithImage}
        isFavorite
        onSelect={onSelect}
        onToggleFavorite={onToggleFavorite}
      />,
    )

    expect(screen.getByText("강남역")).toBeTruthy()
    expect(screen.getByText("★")).toBeTruthy()

    fireEvent.press(screen.getByText("강남역"))
    fireEvent.press(screen.getByText("★"))

    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onToggleFavorite).toHaveBeenCalledTimes(1)
  })
})
