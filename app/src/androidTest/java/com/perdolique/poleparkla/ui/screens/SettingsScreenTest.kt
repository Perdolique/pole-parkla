package com.perdolique.poleparkla.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.ui.PoleParklaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createsEditsAndDeletesCustomTemplate() {
        val saved = mutableListOf<TemplateSubmission>()
        val deleted = mutableListOf<String>()
        val existing = CustomViolationTemplate(
            id = "loading-zone",
            displayName = "Loading zone",
            estonianDescription = "Sõiduk blokeerib laadimisala.",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        composeRule.setContent {
            PoleParklaTheme {
                SettingsScreen(
                    settings = AppSettings(
                        loaded = true,
                        onboardingComplete = true,
                        profile = ReporterProfile("Mari", "+372 5555"),
                    ),
                    cloudConfigured = false,
                    templates = listOf(existing),
                    appVersion = "9.8.7",
                    onBack = {},
                    onRateApp = {},
                    onLanguageSelected = {},
                    onSave = { _, _, _, _, _, _, onSaved -> onSaved() },
                    onClearToken = {},
                    onUpsertTemplate = { id, name, description ->
                        saved += TemplateSubmission(id, name, description)
                    },
                    onDeleteTemplate = { deleted += it },
                    onDeleteAll = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_category_templates").performClick()
        composeRule.onNodeWithTag("settings_add_template").performScrollTo().performClick()
        composeRule.onNodeWithTag("template_name").performTextReplacement("Blocked crossing")
        composeRule.onNodeWithTag("template_description")
            .performTextReplacement("Sõiduk blokeerib ülekäigurada.")
        composeRule.onNodeWithTag("template_save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("template_name").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("template_edit_loading-zone").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("template_name").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("template_name").performTextReplacement("Loading area")
        composeRule.onNodeWithTag("template_description")
            .performTextReplacement("Sõiduk takistab laadimisala kasutamist.")
        composeRule.onNodeWithTag("template_save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("template_name").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("template_delete_loading-zone").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("template_confirm_delete").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList<String>(), deleted)
        composeRule.onNodeWithTag("template_confirm_delete").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                TemplateSubmission(null, "Blocked crossing", "Sõiduk blokeerib ülekäigurada."),
                TemplateSubmission(
                    "loading-zone",
                    "Loading area",
                    "Sõiduk takistab laadimisala kasutamist.",
                ),
            ),
            saved,
        )
        assertEquals(listOf("loading-zone"), deleted)
    }

    @Test
    fun savesIndependentCategoriesAndConfirmsLocalDataDeletion() {
        val languages = mutableListOf<String>()
        val saved = mutableListOf<SettingsSubmission>()
        var deleteAllCalls = 0
        composeRule.setContent {
            PoleParklaTheme {
                SettingsScreen(
                    settings = AppSettings(
                        loaded = true,
                        onboardingComplete = true,
                        languageTag = "ru",
                        profile = ReporterProfile("Mari", "+372 5555"),
                        defaultRecipient = "recipient@example.invalid",
                    ),
                    cloudConfigured = false,
                    templates = emptyList(),
                    appVersion = "9.8.7",
                    onBack = {},
                    onRateApp = {},
                    onLanguageSelected = { languages += it },
                    onSave = { language, profile, recipient, workerUrl, provider, token, onSaved ->
                        saved += SettingsSubmission(language, profile, recipient, workerUrl, provider, token)
                        onSaved()
                    },
                    onClearToken = {},
                    onUpsertTemplate = { _, _, _ -> },
                    onDeleteTemplate = {},
                    onDeleteAll = { deleteAllCalls++ },
                )
            }
        }

        composeRule.onNodeWithTag("settings_category_language").performClick()
        composeRule.onNodeWithTag("settings_language_save").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("ru"), languages)

        composeRule.onNodeWithTag("settings_category_profile").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_profile_email").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_profile_name").performTextReplacement("Mari Updated")
        composeRule.onNodeWithTag("settings_profile_save").performClick()
        composeRule.waitForIdle()
        assertEquals(1, saved.size)
        assertEquals("Mari Updated", saved.single().profile.name)

        composeRule.onNodeWithTag("settings_category_recipient").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_recipient")
            .performTextReplacement("qa@example.invalid")
        composeRule.onNodeWithTag("settings_recipient_save").performClick()
        composeRule.waitForIdle()
        assertEquals(2, saved.size)
        assertEquals("qa@example.invalid", saved.last().recipient)

        composeRule.onNodeWithTag("settings_category_recognition").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_provider_openai").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_recognition_save").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(3, saved.size)
        assertEquals(CloudProvider.OPENAI, saved.last().provider)

        composeRule.onNodeWithTag("settings_category_privacy").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_privacy_policy").performClick()
        composeRule.onNodeWithTag("privacy_policy_dialog").assertExists()
        composeRule.onNodeWithTag("privacy_policy_close").performClick()
        composeRule.onNodeWithTag("settings_delete_all").performClick()
        assertEquals(0, deleteAllCalls)
        composeRule.onNodeWithTag("settings_confirm_delete_all").performClick()
        composeRule.waitForIdle()

        assertEquals(1, deleteAllCalls)
        composeRule.onNodeWithTag("settings_confirm_delete_all").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_delete_all").assertDoesNotExist()
    }

    @Test
    fun ratesAppAndShowsInstalledVersionInFooter() {
        var rateAppCalls = 0
        composeRule.setContent {
            PoleParklaTheme {
                SettingsScreen(
                    settings = AppSettings(loaded = true, onboardingComplete = true),
                    cloudConfigured = false,
                    templates = emptyList(),
                    appVersion = "9.8.7",
                    onBack = {},
                    onRateApp = { rateAppCalls++ },
                    onLanguageSelected = {},
                    onSave = { _, _, _, _, _, _, onSaved -> onSaved() },
                    onClearToken = {},
                    onUpsertTemplate = { _, _, _ -> },
                    onDeleteTemplate = {},
                    onDeleteAll = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_rate_app").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_app_version")
            .performScrollTo()
            .assertTextContains("9.8.7", substring = true)

        assertEquals(1, rateAppCalls)
    }

    @Test
    fun showsBundledPlateModelAsReady() {
        composeRule.setContent {
            PoleParklaTheme {
                SettingsScreen(
                    settings = AppSettings(loaded = true, onboardingComplete = true),
                    cloudConfigured = false,
                    templates = emptyList(),
                    appVersion = "9.8.7",
                    onBack = {},
                    onRateApp = {},
                    onLanguageSelected = {},
                    onSave = { _, _, _, _, _, _, onSaved -> onSaved() },
                    onClearToken = {},
                    onUpsertTemplate = { _, _, _ -> },
                    onDeleteTemplate = {},
                    onDeleteAll = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_category_recognition").performClick()
        composeRule.onNodeWithTag("settings_plate_model_ready").assertExists()
    }

    private data class TemplateSubmission(
        val id: String?,
        val name: String,
        val description: String,
    )

    private data class SettingsSubmission(
        val language: String,
        val profile: ReporterProfile,
        val recipient: String,
        val workerUrl: String,
        val provider: CloudProvider,
        val token: String,
    )
}
