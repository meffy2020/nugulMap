package com.nugulmap.nativeapp.ui.map

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
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

/**
 * Reproducible local-emulator capture for visually reviewing the production popup detail UI.
 *
 * Run the debug app against the deterministic UI fixture server and pass the
 * capturePopupStoreAsset=true instrumentation argument before executing this test.
 * The resulting image is reviewer evidence only; it is not a live-data or signed-RC listing asset.
 */
@RunWith(AndroidJUnit4::class)
class PopupStoreAssetCaptureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturePopupDetailCandidate() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("capturePopupStoreAsset") == "true")
        assumeTrue(BuildConfig.NUGUL_API_BASE_URL.startsWith("http://10.0.2.2:"))
        composeRule.onNodeWithContentDescription("팝업 지도 보기").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule
                .onAllNodes(hasContentDescription("팝업 1개", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val popupAction = composeRule
            .onNode(hasContentDescription("팝업 1개", substring = true))
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == "팝업 성수 여름 팝업" }
        composeRule.runOnIdle {
            assertTrue(popupAction.action())
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("성수 여름 팝업").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("2026-07-10", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("2026-07-20", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("서울특별시 성동구 연무장길 1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("행사 원문").fetchSemanticsNodes().isNotEmpty())
        composeRule.waitForIdle()
        Thread.sleep(5_000)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = composeRule.activity.contentResolver
        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "popup-store-reviewer-candidate-${System.currentTimeMillis()}.png",
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/NugulMap",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        )
        resolver.openOutputStream(output, "w").use { stream ->
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(stream)))
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(output, values, null, null)
        screenshot.recycle()
    }
}
