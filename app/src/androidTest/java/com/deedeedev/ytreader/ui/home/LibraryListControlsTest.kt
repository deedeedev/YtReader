package com.deedeedev.ytreader.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deedeedev.ytreader.ui.theme.AppTheme
import com.deedeedev.ytreader.ui.theme.YtReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryListControlsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun filterDropdown_showsAllChannelsAndUpdatesSelection() {
        var selectedChannelFilter by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            YtReaderTheme(appTheme = AppTheme.LIGHT) {
                LibraryListControls(
                    channels = listOf("Channel A", "Channel B"),
                    selectedChannelFilter = selectedChannelFilter,
                    sortOption = SortOption.DOWNLOADED,
                    isAscending = false,
                    onChannelFilterChange = { selectedChannelFilter = it },
                    onSortOptionChange = {},
                    onSortDirectionToggle = {}
                )
            }
        }

        composeTestRule.onNodeWithText("All Channels").performClick()
        composeTestRule.onNodeWithText("All Channels").assertIsDisplayed()
        composeTestRule.onNodeWithText("Channel A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Channel B").assertIsDisplayed()

        composeTestRule.onNodeWithText("Channel B").performClick()
        composeTestRule.runOnIdle {
            assertEquals("Channel B", selectedChannelFilter)
        }
    }

    @Test
    fun sortMenu_showsAllSortOptionsAndUpdatesSortOption() {
        var sortOption by mutableStateOf(SortOption.DOWNLOADED)

        composeTestRule.setContent {
            YtReaderTheme(appTheme = AppTheme.LIGHT) {
                LibraryListControls(
                    channels = emptyList(),
                    selectedChannelFilter = null,
                    sortOption = sortOption,
                    isAscending = false,
                    onChannelFilterChange = {},
                    onSortOptionChange = { sortOption = it },
                    onSortDirectionToggle = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Sort").performClick()
        composeTestRule.onNodeWithText("Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Channel Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date Published").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloaded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Last opened").assertIsDisplayed()

        composeTestRule.onNodeWithText("Title").performClick()
        composeTestRule.runOnIdle {
            assertEquals(SortOption.TITLE, sortOption)
        }
    }

    @Test
    fun sortDirectionToggle_flipsAscendingState() {
        var isAscending by mutableStateOf(false)

        composeTestRule.setContent {
            YtReaderTheme(appTheme = AppTheme.LIGHT) {
                LibraryListControls(
                    channels = emptyList(),
                    selectedChannelFilter = null,
                    sortOption = SortOption.DOWNLOADED,
                    isAscending = isAscending,
                    onChannelFilterChange = {},
                    onSortOptionChange = {},
                    onSortDirectionToggle = { isAscending = !isAscending }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Descending").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertTrue(isAscending)
        }
        composeTestRule.onNodeWithContentDescription("Ascending").assertIsDisplayed()
    }
}
