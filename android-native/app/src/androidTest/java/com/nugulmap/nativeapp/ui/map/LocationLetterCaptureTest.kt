package com.nugulmap.nativeapp.ui.map

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationLetterCaptureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureSimpleLocationLetterCallout() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("captureLocationLetter") == "true")
        assumeTrue(BuildConfig.NUGUL_API_BASE_URL.startsWith("http://10.0.2.2:"))

        composeRule.onNodeWithContentDescription("주변 편지 열기").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule
                .onAllNodesWithText("오늘 이 길을 걷는 마음이 조금은 가벼워지길.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("하늘산책").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("12").fetchSemanticsNodes().isNotEmpty())
        composeRule.waitForIdle()
        Thread.sleep(1_500)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = composeRule.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "location-letter-simple-${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NugulMap")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(output, "w").use { stream ->
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(stream)))
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(output, values, null, null)
        screenshot.recycle()
    }
}
