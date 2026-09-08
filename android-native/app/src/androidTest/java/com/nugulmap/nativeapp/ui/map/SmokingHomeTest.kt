package com.nugulmap.nativeapp.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import com.nugulmap.nativeapp.MainActivity
import org.junit.Rule
import org.junit.Test

class SmokingHomeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun smokingHomeKeepsSearchAndLocationWithoutExtraLayers() {
        composeRule.onNodeWithContentDescription(SEARCH_FIELD_ACCESSIBILITY_LABEL)
            .assertIsDisplayed().performTextInput("성수")
        composeRule.onNodeWithContentDescription(SEARCH_ACTION_ACCESSIBILITY_LABEL).assertHasClickAction()
        composeRule.onNodeWithContentDescription(CURRENT_LOCATION_ACCESSIBILITY_LABEL).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("흡연구역 제보하기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("주변 편지 열기").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("팝업 지도 보기").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("실시간 핫플 지도 보기").assertDoesNotExist()
    }
}
