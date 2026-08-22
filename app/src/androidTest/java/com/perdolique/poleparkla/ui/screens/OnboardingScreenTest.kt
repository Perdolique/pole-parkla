package com.perdolique.poleparkla.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.ui.PoleParklaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offersSystemEnglishRussianAndEstonianLanguages() {
        setScreen()

        listOf("system", "en", "ru", "et").forEach { language ->
            composeRule.onNodeWithTag("language_$language").assertExists()
        }
    }

    @Test
    fun showsPoleParklaBrandMark() {
        setScreen()

        composeRule.onNodeWithTag("brand_mark").assertExists()
    }

    @Test
    fun opensPrivacyPolicyBeforeContinuing() {
        setScreen()

        composeRule.onNodeWithTag("onboarding_privacy_policy").assertExists().performClick()
        composeRule.onNodeWithTag("privacy_policy_dialog").assertExists()
        composeRule.onNodeWithTag("privacy_policy_content").assertExists()
    }

    @Test
    fun blocksIncompleteProfile() {
        var submission: Submission? = null
        setScreen { submission = it }

        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.onNodeWithTag("onboarding_submit").performClick()

        composeRule.onNodeWithTag("onboarding_validation").assertExists()
        assertNull(submission)
    }

    @Test
    fun submitsRequiredProfile() {
        var submission: Submission? = null
        setScreen(onSubmit = { submission = it })

        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.onNodeWithTag("onboarding_name")
            .performScrollTo()
            .performTextReplacement("Mari Maasikas")
        composeRule.onNodeWithTag("onboarding_email").assertDoesNotExist()
        composeRule.onNodeWithTag("onboarding_phone")
            .performScrollTo()
            .performTextReplacement("+372 5555 5555")
        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.waitForIdle()

        assertEquals(
            Submission(
                language = "",
                profile = ReporterProfile(
                    name = "Mari Maasikas",
                    phone = "+372 5555 5555",
                ),
                recipient = DEFAULT_RECIPIENT,
            ),
            submission,
        )
    }

    @Test
    fun clearedSettingsRemoveRememberedProfileValues() {
        val settings = mutableStateOf(
            AppSettings(
                loaded = true,
                profile = ReporterProfile("Mari", "+372 5555"),
            ),
        )
        composeRule.setContent {
            PoleParklaTheme {
                OnboardingScreen(
                    settings = settings.value,
                    onComplete = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_submit").performClick()
        composeRule.onNodeWithTag("onboarding_name").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Mari")),
        )

        composeRule.runOnIdle { settings.value = AppSettings(loaded = true) }

        composeRule.onNodeWithTag("onboarding_name").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    private fun setScreen(
        onSubmit: (Submission) -> Unit = {},
    ) {
        composeRule.setContent {
            PoleParklaTheme {
                OnboardingScreen(
                    settings = AppSettings(loaded = true),
                    onComplete = { language, profile, recipient ->
                        onSubmit(Submission(language, profile, recipient))
                    },
                )
            }
        }
    }

    private data class Submission(
        val language: String,
        val profile: ReporterProfile,
        val recipient: String,
    )
}
