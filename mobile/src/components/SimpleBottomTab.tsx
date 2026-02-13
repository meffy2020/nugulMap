import { Pressable, StyleSheet, Text, View } from "react-native"
import { TABS, type TabKey } from "../navigation/tabs"

interface TabProps {
  activeTab: TabKey
  onChange: (tab: TabKey) => void
}

export function SimpleBottomTab({ activeTab, onChange }: TabProps) {
  return (
    <View style={styles.container}>
      {TABS.map((tab) => {
        const isActive = tab.key === activeTab
        return (
          <Pressable
            key={tab.key}
            onPress={() => onChange(tab.key)}
            style={[styles.tab, isActive && styles.activeTab]}
          >
            <Text style={[styles.label, isActive && styles.activeLabel]}>{tab.label}</Text>
          </Pressable>
        )
      })}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    backgroundColor: "#ffffff",
  },
  tab: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 12,
  },
  activeTab: {
    borderTopWidth: 2,
    borderTopColor: "#2563eb",
  },
  label: {
    color: "#334155",
    fontSize: 14,
  },
  activeLabel: {
    color: "#1d4ed8",
    fontWeight: "700",
  },
})
