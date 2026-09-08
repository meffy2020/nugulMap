package com.nugulmap.nativeapp.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nugulmap.nativeapp.data.dto.RecommendationIntent
import com.nugulmap.nativeapp.ui.theme.NugulMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapChromeAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFieldAndEmbeddedButtonExposeIndependentActions() {
        var query by mutableStateOf("")
        var searched = false

        composeRule.setContent {
            NugulMapTheme {
                TopMapChrome(
                    searchQuery = query,
                    isLoading = false,
                    onSearchQueryChange = { query = it },
                    onSearch = { searched = true },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(SEARCH_FIELD_ACCESSIBILITY_LABEL)
            .assert(hasSetTextAction())
            .performTextInput("성수 팝업")
        composeRule
            .onNodeWithContentDescription(SEARCH_ACTION_ACCESSIBILITY_LABEL)
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertTrue(searched) }
    }

    @Test
    fun popupTabAnnouncesSelectedStateAndClickAction() {
        composeRule.setContent {
            NugulMapTheme {
                MapCategorySelector(
                    activeMode = Season2LayerMode.Events,
                    intent = RecommendationIntent.CAFE,
                    isSignedIn = false,
                    personalizationEnabled = false,
                    preferenceAvailable = false,
                    isSavingPreference = false,
                    onModeSelected = {},
                    onIntentSelected = {},
                    onPersonalizationChanged = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("팝업 지도 보기")
            .assertIsSelected()
            .assertHasClickAction()
    }
}
