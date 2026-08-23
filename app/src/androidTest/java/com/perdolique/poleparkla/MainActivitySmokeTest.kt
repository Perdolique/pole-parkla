package com.perdolique.poleparkla

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.ui.PoleParklaTheme
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cleanInstallStartsOnboarding() {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("onboarding_submit")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("onboarding_submit").assertExists()
    }

    @Test
    fun deletingAllDataDismissesSettingsOverlaysBeforeOnboarding() {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("onboarding_submit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.onNodeWithTag("onboarding_name")
            .performScrollTo()
            .performTextReplacement("Pier Dolique")
        composeRule.onNodeWithTag("onboarding_phone")
            .performScrollTo()
            .performTextReplacement("+37256789012")
        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("camera_settings").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("camera_settings").performClick()
        composeRule.onNodeWithTag("settings_category_privacy").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_delete_all").performClick()
        composeRule.onNodeWithTag("settings_confirm_delete_all").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("onboarding_submit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings_confirm_delete_all").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_delete_all").assertDoesNotExist()
    }

    @Test
    fun changingAppLanguageUpdatesComposeWithoutRecreatingActivity() {
        val originalLocales = AppCompatDelegate.getApplicationLocales()
        val originalActivity = composeRule.activity

        try {
            composeRule.runOnIdle {
                originalActivity.setContent {
                    PoleParklaTheme {
                        Text(
                            text = stringResource(R.string.settings_title),
                            modifier = Modifier.testTag("localized_title"),
                        )
                    }
                }
            }

            setLocaleAndAssert(originalActivity, "ru", "Настройки")
            setLocaleAndAssert(originalActivity, "en", "Settings")
            setLocaleAndAssert(originalActivity, "et", "Seaded")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setApplicationLocales(originalLocales)
            }
        }
    }

    private fun setLocaleAndAssert(
        originalActivity: MainActivity,
        languageTag: String,
        expectedTitle: String,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("localized_title")
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.contains(SemanticsProperties.Text) &&
                        node.config[SemanticsProperties.Text].any { it.text == expectedTitle }
                }
        }

        composeRule.onNodeWithTag("localized_title").assertTextEquals(expectedTitle)
        assertSame(originalActivity, composeRule.activity)
    }
}
