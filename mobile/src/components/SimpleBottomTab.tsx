import { Pressable, StyleSheet, Text, View } from "react-native"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { TABS, type TabKey } from "../navigation/tabs"
import { colors, radius } from "../theme/tokens"

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
            <MaterialCommunityIcons
              name={tab.icon}
              size={18}
              color={isActive ? colors.primary : "#64748b"}
              style={styles.icon}
            />
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
    backgroundColor: colors.surface,
    marginHorizontal: 12,
    marginBottom: 10,
    borderRadius: radius.lg,
    padding: 4,
    shadowColor: colors.dark,
    shadowOpacity: 0.1,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 11,
    borderRadius: radius.md,
  },
  activeTab: {
    backgroundColor: colors.primarySoft,
  },
  icon: {
    marginBottom: 3,
  },
  label: {
    color: "#334155",
    fontSize: 12,
    fontWeight: "700",
  },
  activeLabel: {
    color: colors.primary,
    fontWeight: "800",
  },
})
