package com.perdolique.poleparkla.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.ui.PoleParklaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interactiveComponentsExposeRolesStateAndMinimumTouchTargets() {
        composeRule.setContent {
            PoleParklaTheme {
                Column {
                    PpButton("Continue", {}, Modifier.testTag("tj_button"))
                    PpIconButton(
                        icon = PpIcons.Settings,
                        contentDescription = "Settings",
                        onClick = {},
                        modifier = Modifier.testTag("tj_icon_button"),
                    )
                    PpChoiceRow(
                        title = "Selected choice",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.testTag("tj_choice"),
                    )
                    PpCard(
                        modifier = Modifier.testTag("tj_card"),
                        onClick = {},
                    ) {
                        androidx.compose.material3.Text("Open")
                    }
                    PpDisclosureRow(
                        title = "Coordinates",
                        expanded = false,
                        stateDescription = "Collapsed",
                        onClick = {},
                        modifier = Modifier.testTag("tj_disclosure"),
                    )
                }
            }
        }

        val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onNodeWithTag("tj_button")
            .assert(buttonRole)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("tj_icon_button")
            .assert(buttonRole)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag("tj_choice")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("tj_card")
            .assert(buttonRole)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("tj_disclosure")
            .assert(buttonRole)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Collapsed",
                ),
            )
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun repeatedSheetCloseWaitsForHideAnimationAndDismissesOnce() {
        var dismissals = 0
        composeRule.setContent {
            PoleParklaTheme {
                PpSheet(onDismissRequest = { dismissals++ }) { dismiss ->
                    PpSheetHeader("Recognition", dismiss)
                }
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription("Close").assertExists()
        composeRule.onNodeWithTag("sheet_close").performClick()
        composeRule.onNodeWithTag("sheet_close").performClick()

        composeRule.runOnIdle { assertEquals(0, dismissals) }
        composeRule.onNodeWithTag("sheet_close").assertExists()

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun languageSelectorExposesRadioButtonSelection() {
        composeRule.setContent {
            PoleParklaTheme {
                LanguageSelector(selected = "et", onSelected = {})
            }
        }

        composeRule.onNodeWithTag("language_et")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
    }
}
