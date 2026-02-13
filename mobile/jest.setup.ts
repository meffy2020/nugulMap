import "react-native"

// RN Animated warning suppression for test output readability.
jest.mock("react-native/src/private/animated/NativeAnimatedHelper")

jest.mock("@expo/vector-icons", () => ({
  MaterialCommunityIcons: ({ name }: { name: string }) => {
    const React = require("react")
    const { Text } = require("react-native")
    return React.createElement(Text, null, name)
  },
}))
