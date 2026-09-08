import { fireEvent, render, screen } from "@testing-library/react-native"
import React from "react"
import { View } from "react-native"
import type { MapRegion, SmokingZone } from "../types"
import { MapScreen } from "./MapScreen"

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

function renderMapScreen(overrides: Partial<React.ComponentProps<typeof MapScreen>> = {}) {
  return render(
    <View style={{ height: 640, width: 360 }}>
      <MapScreen
        region={region}
        zones={[zone]}
        selectedZone={null}
        isLoading={false}
        onRegionChangeComplete={jest.fn()}
        onSelectZone={jest.fn()}
        {...overrides}
      />
    </View>,
  )
}

describe("MapScreen smoking-zone bridge", () => {
  beforeEach(() => {
    mockPostMessage.mockClear()
  })

  it("selects a smoking zone from a map marker", () => {
    const onSelectZone = jest.fn()
    renderMapScreen({ onSelectZone })
    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "markerPress", id: zone.id }) },
    })
    expect(onSelectZone).toHaveBeenCalledWith(zone)
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

  it("renders only smoking zones without discovery controls or payloads", () => {
    renderMapScreen()
    fireEvent(screen.getByTestId("kakao-webview"), "message", {
      nativeEvent: { data: JSON.stringify({ type: "ready" }) },
    })
    const payloads = mockPostMessage.mock.calls.map(([payload]) => JSON.parse(String(payload)))
    expect(payloads).toContainEqual({ type: "SET_ZONES", zones: [zone] })
    expect(payloads.some((payload) => payload.type === "SET_INSIGHTS")).toBe(false)
    expect(screen.queryByTestId("season2-layer-control")).toBeNull()
    expect(screen.getByTestId("kakao-webview").props.source.html).not.toMatch(/SET_INSIGHTS|hotplacePress|recommendationPress/)
  })
})
