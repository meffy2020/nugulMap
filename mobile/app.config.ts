import { ExpoConfig } from "expo/config"

const IOS_BUILD_NUMBER = process.env.APP_IOS_BUILD_NUMBER || "1"
const androidVersionCode = Number(process.env.APP_ANDROID_VERSION_CODE || 1)
const kakaoWebviewBaseUrl = process.env.EXPO_PUBLIC_KAKAO_WEBVIEW_BASE_URL || "https://nugulmap.com"

const config: ExpoConfig = {
  name: "NugulMap",
  slug: "nugulmap-mobile",
  scheme: "nugulmap",
  version: "1.0.0",
  runtimeVersion: {
    policy: "appVersion",
  },
  icon: "./assets/images/pin.png",
  orientation: "portrait",
  userInterfaceStyle: "automatic",
  splash: {
    image: "./assets/images/pin.png",
    resizeMode: "contain",
    backgroundColor: "#ffffff",
  },
  ios: {
    bundleIdentifier: "com.nugulmap.mobile",
    supportsTablet: true,
    buildNumber: IOS_BUILD_NUMBER,
    config: {
      usesNonExemptEncryption: false,
    },
    infoPlist: {
      NSLocationWhenInUseUsageDescription: "현재 내 위치를 기준으로 흡연구역을 가까운 순으로 보여주기 위해 위치 권한이 필요합니다."
    }
  },
  android: {
    package: "com.nugulmap.mobile",
    versionCode: Number.isInteger(androidVersionCode) && androidVersionCode > 0 ? androidVersionCode : 1,
    adaptiveIcon: {
      foregroundImage: "./assets/images/pin.png",
      backgroundColor: "#ffffff",
    },
    permissions: ["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"],
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
    kakaoWebviewBaseUrl,
    kakaoMarkerImageUrl:
      process.env.EXPO_PUBLIC_KAKAO_MARKER_IMAGE_URL || `${kakaoWebviewBaseUrl}/images/pin.png`,
    oauthRedirectUri: process.env.EXPO_PUBLIC_OAUTH_REDIRECT_URI || "nugulmap://oauth/callback",
  }
}

export default config
