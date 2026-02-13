import { ExpoConfig } from "expo/config"

const config: ExpoConfig = {
  name: "NugulMap",
  slug: "nugulmap-mobile",
  scheme: "nugulmap",
  version: "1.0.0",
  orientation: "portrait",
  userInterfaceStyle: "automatic",
  ios: {
    bundleIdentifier: "com.nugulmap.mobile",
    infoPlist: {
      NSLocationWhenInUseUsageDescription: "현재 내 위치를 기준으로 흡연구역을 가까운 순으로 보여주기 위해 위치 권한이 필요합니다."
    }
  },
  android: {
    package: "com.nugulmap.mobile",
    permissions: ["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"]
  },
  updates: {
    fallbackToCacheTimeout: 0
  },
  extra: {
    apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com",
    kakaoJavascriptKey:
      process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY ||
      process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY ||
      "",
    kakaoWebviewBaseUrl: process.env.EXPO_PUBLIC_KAKAO_WEBVIEW_BASE_URL || "https://nugulmap.local",
    kakaoMarkerImageUrl: process.env.EXPO_PUBLIC_KAKAO_MARKER_IMAGE_URL || "",
    oauthRedirectUri: process.env.EXPO_PUBLIC_OAUTH_REDIRECT_URI || "nugulmap://oauth/callback",
  }
}

export default config
