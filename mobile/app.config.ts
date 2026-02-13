import { ExpoConfig } from "expo/config"

const config: ExpoConfig = {
  name: "NugulMap",
  slug: "nugulmap-mobile",
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
    apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"
  }
}

export default config
