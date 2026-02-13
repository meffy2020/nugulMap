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
    backgroundColor: "#ffffff",
    marginHorizontal: 12,
    marginBottom: 10,
    borderRadius: 18,
    padding: 4,
    shadowColor: "#0f172a",
    shadowOpacity: 0.1,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 11,
    borderRadius: 14,
  },
  activeTab: {
    backgroundColor: "#dbeafe",
  },
  label: {
    color: "#334155",
    fontSize: 13,
    fontWeight: "700",
  },
  activeLabel: {
    color: "#1d4ed8",
    fontWeight: "800",
  },
})
