import React from "react"
import { fireEvent, render, screen } from "@testing-library/react-native"
import { View } from "react-native"
import { MapScreen } from "./MapScreen"
import type { Hotplace, MapRegion, SmokingZone, TrendEvent } from "../types"

const mockPostMessage = jest.fn()

jest.mock("react-native-webview", () => {
  const React = require("react")
  const { View } = require("react-native")

  return React.forwardRef((props: Record<string, unknown>, ref: React.Ref<unknown>) => {
    React.useImperativeHandle(ref, () => ({
      postMessage: mockPostMessage,
    }))
    return React.createElement(View, { ...props, testID: "kakao-webview" })
  })
})

jest.mock("expo-constants", () => ({
  expoConfig: {
    extra: {
      kakaoJavascriptKey: "test-kakao-key",
      kakaoWebviewBaseUrl: "https://nugulmap.test",
    },
  },
}))

const region: MapRegion = {
  latitude: 37.5111,
  longitude: 127.0982,
  latitudeDelta: 0.05,
  longitudeDelta: 0.05,
}

const zone: SmokingZone = {
  id: 1,
  region: "서울",
  type: "실외",
  subtype: "테스트 구역",
  description: "설명",
  latitude: 37.51,
  longitude: 127.09,
  address: "서울 송파구",
  user: "tester",
  image: null,
}

const hotplace: Hotplace = {
  id: "lotte-world",
  name: "롯데월드·잠실",
  category: "theme_park",
  crowdLevel: "약간 붐빔",
  crowdMessage: "이동에 여유가 필요합니다.",
  estimatedMinPeople: 12000,
  estimatedMaxPeople: 14000,
  latitude: 37.5111,
  longitude: 127.0982,
  address: "서울 송파구 올림픽로 240",
  source: "SEOUL_CITYDATA",
  sourcePlaceCode: "잠실 관광특구",
  updatedAt: "2026-06-18T12:00:00Z",
}

const event: TrendEvent = {
  id: "popup-seongsu",
  title: "성수 팝업",
  kind: "popup",
  period: "최근 후보",
  startDate: null,
  endDate: null,
  latitude: 37.5446,
  longitude: 127.0557,
  address: "서울 성동구 성수동2가",
  imageUrl: null,
  source: "CRAWLED_POPUP_TREND",
  sourceContentId: "popup-seongsu",
}

function renderMapScreen(overrides: Partial<React.ComponentProps<typeof MapScreen>> = {}) {
  return render(
    <View style={{ height: 640, width: 360 }}>
      <MapScreen
        region={region}
        zones={[zone]}
        hotplaces={[hotplace]}
        events={[event]}
        selectedZone={null}
        isLoading={false}
        onRegionChangeComplete={jest.fn()}
        onSelectZone={jest.fn()}
        onSelectHotplace={jest.fn()}
        onSelectEvent={jest.fn()}
        {...overrides}
      />
    </View>,
  )
}

describe("MapScreen Season 2 bridge", () => {
  beforeEach(() => {
    mockPostMessage.mockClear()
  })

  it("selects a hotplace from a Kakao WebView overlay press", () => {
    const onSelectHotplace = jest.fn()

    renderMapScreen({ onSelectHotplace })

    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "hotplacePress", id: "lotte-world" }) },
    })

    expect(onSelectHotplace).toHaveBeenCalledWith(hotplace)
  })

  it("selects an event from a Kakao WebView overlay press", () => {
    const onSelectEvent = jest.fn()

    renderMapScreen({ onSelectEvent })

    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "eventPress", id: "popup-seongsu" }) },
    })

    expect(onSelectEvent).toHaveBeenCalledWith(event)
  })

  it("forwards visible Kakao map region changes", () => {
    const onRegionChangeComplete = jest.fn()

    renderMapScreen({ onRegionChangeComplete })

    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: {
        data: JSON.stringify({
          type: "regionChange",
          latitude: 37.52,
          longitude: 127.1,
          latitudeDelta: 0.03,
          longitudeDelta: 0.04,
        }),
      },
    })

    expect(onRegionChangeComplete).toHaveBeenCalledWith({
      latitude: 37.52,
      longitude: 127.1,
      latitudeDelta: 0.03,
      longitudeDelta: 0.04,
    })
  })

  it("filters bridge payloads when switching to hotplace layer", () => {
    renderMapScreen()

    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "ready" }) },
    })
    mockPostMessage.mockClear()

    fireEvent.press(screen.getByTestId("season2-layer-hotplaces"))

    const payloads = mockPostMessage.mock.calls.map(([payload]) => JSON.parse(String(payload)))
    expect(payloads).toContainEqual({ type: "SET_ZONES", zones: [] })
    expect(payloads).toContainEqual({ type: "SET_INSIGHTS", hotplaces: [hotplace], events: [] })
  })

  it("filters bridge payloads when switching to event layer", () => {
    renderMapScreen()

    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "ready" }) },
    })
    mockPostMessage.mockClear()

    fireEvent.press(screen.getByTestId("season2-layer-events"))

    const payloads = mockPostMessage.mock.calls.map(([payload]) => JSON.parse(String(payload)))
    expect(payloads).toContainEqual({ type: "SET_ZONES", zones: [] })
    expect(payloads).toContainEqual({ type: "SET_INSIGHTS", hotplaces: [], events: [event] })
  })

  it("includes estimated people ranges in hotplace overlay labels", () => {
    renderMapScreen()

    const source = screen.getByTestId("kakao-webview").props.source
    expect(source.html).toContain("formatHotplaceOverlayLabel")
    expect(source.html).toContain("estimatedMinPeople")
    expect(source.html).toContain("formatPeopleRange")
  })
})
