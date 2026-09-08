package com.nugulmap.nativeapp.ui.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SettingsSheetAccessibilityContractTest {
    @Test
    fun `settings sheet remains scrollable at large font scales`() {
        val source = File(
            "src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt",
        ).readText()
        val settingsSheet = source.substringAfter("private fun SettingsSheet(")
            .substringBefore("private fun AccountSheet(")

        assertTrue(settingsSheet.contains(".verticalScroll(rememberScrollState())"))
    }
}
