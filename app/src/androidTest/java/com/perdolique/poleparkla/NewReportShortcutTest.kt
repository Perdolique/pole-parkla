package com.perdolique.poleparkla

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.ui.CameraCaptureTarget
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewReportShortcutTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as PoleParklaApplication).container
    private lateinit var savedPhoto: ReportPhoto

    @Before
    fun setUp() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
        container.settingsRepository.completeOnboarding("en", PROFILE, DEFAULT_RECIPIENT)
        val file = container.photoStore.createCameraTarget(SAVED_REPORT_ID)
        copyTestPhoto(file)
        savedPhoto = ReportPhoto(
            id = file.nameWithoutExtension,
            reportId = SAVED_REPORT_ID,
            filePath = file.absolutePath,
            source = PhotoSource.CAMERA,
            capturedAtEpochMillis = 1L,
            isPrimary = true,
        )
        container.reportRepository.createDraft(
            id = SAVED_REPORT_ID,
            photo = savedPhoto,
            settings = AppSettings(profile = PROFILE),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = false,
        )
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
    }

    @Test
    fun shortcutStartsFreshCameraAndKeepsNewPhotoAndNavigationAfterRecreation() {
        ActivityScenario.launch<MainActivity>(shortcutIntent()).use { scenario ->
            assertFreshCamera(scenario)
            lateinit var target: CameraCaptureTarget
            scenario.onActivity { activity -> target = activity.cameraViewModel().createCameraTarget() }
            copyTestPhoto(target.file)
            scenario.onActivity { activity -> activity.cameraViewModel().onCameraPhotoCaptured(target) }
            runBlocking {
                withTimeout(10_000L) {
                    container.reportRepository.observeReport(target.reportId).first { it?.photos?.size == 1 }
                }
            }
            waitForTag("camera_review")

            scenario.recreate()

            waitForTag("camera_settings")
            var expectedCounter = ""
            lateinit var viewModel: PoleParklaViewModel
            scenario.onActivity { activity ->
                viewModel = activity.cameraViewModel()
                assertEquals(target.reportId, viewModel.cameraReportId.value)
                expectedCounter = activity.getString(R.string.photo_counter, 1)
            }
            runBlocking { withTimeout(15_000L) { viewModel.busy.first { !it } } }
            composeRule.onNodeWithTag("camera_photo_counter").assertTextEquals(expectedCounter)
            composeRule.onNodeWithTag("camera_review").assertIsEnabled().performClick()
            waitForTag("wizard_scene_vehicle")

            scenario.recreate()

            waitForTag("wizard_scene_vehicle")
            composeRule.onNodeWithTag("camera_settings").assertDoesNotExist()
            assertSavedPhotoStillExists()
        }
    }

    @Test
    fun shortcutReplacesRunningReportWithFreshCamera() {
        val launcherIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<MainActivity>(launcherIntent).use { original ->
            waitForTag("wizard_scene_vehicle")
            ActivityScenario.launch<MainActivity>(shortcutIntent()).use { shortcut ->
                assertFreshCamera(shortcut)
                assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, original.state)
                assertSavedPhotoStillExists()
            }
        }
    }

    @Test
    fun shortcutRequiresOnboardingBeforeOpeningFreshCamera() {
        runBlocking { container.settingsRepository.clearAll() }
        ActivityScenario.launch<MainActivity>(shortcutIntent()).use { scenario ->
            waitForTag("onboarding_submit")
            composeRule.onNodeWithTag("camera_settings").assertDoesNotExist()
            composeRule.onNodeWithTag("onboarding_submit").performClick()
            composeRule.onNodeWithTag("onboarding_name")
                .performScrollTo().performTextReplacement(PROFILE.name)
            composeRule.onNodeWithTag("onboarding_phone")
                .performScrollTo().performTextReplacement(PROFILE.phone)
            composeRule.onNodeWithTag("onboarding_submit").performClick()

            assertFreshCamera(scenario)
            assertSavedPhotoStillExists()
        }
    }

    private fun shortcutIntent(): Intent {
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        assertEquals(1, shortcuts.size)
        val shortcut = shortcuts.single()
        assertEquals("new_report", shortcut.id)
        assertTrue(shortcut.isEnabled)
        assertTrue(shortcut.isDeclaredInManifest)
        assertEquals(context.getString(R.string.shortcut_new_report), shortcut.shortLabel.toString())
        val intent = Intent(requireNotNull(shortcut.intents).single())
        assertEquals("com.perdolique.poleparkla.action.NEW_REPORT", intent.action)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val launchFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(launchFlags, intent.flags and launchFlags)
        return intent
    }

    private fun assertFreshCamera(scenario: ActivityScenario<MainActivity>) {
        waitForTag("camera_settings")
        var expectedCounter = ""
        scenario.onActivity { activity ->
            assertNotEquals(SAVED_REPORT_ID, activity.cameraViewModel().cameraReportId.value)
            expectedCounter = activity.getString(R.string.photo_counter, 0)
        }
        composeRule.onNodeWithTag("camera_photo_counter").assertTextEquals(expectedCounter)
        composeRule.onNodeWithTag("camera_review").assertDoesNotExist()
        runBlocking {
            assertEquals(listOf(SAVED_REPORT_ID), container.reportRepository.observeReports().first().map { it.id })
        }
    }

    private fun assertSavedPhotoStillExists() = runBlocking {
        val saved = requireNotNull(container.reportRepository.getReport(SAVED_REPORT_ID))
        assertEquals(listOf(savedPhoto), saved.photos)
        assertTrue(File(savedPhoto.filePath).isFile)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun copyTestPhoto(destination: File) {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("street_plate_test_image.png")
            .use { input -> destination.outputStream().use(input::copyTo) }
    }

    private fun MainActivity.cameraViewModel(): PoleParklaViewModel =
        ViewModelProvider(this)[PoleParklaViewModel::class.java]

    private companion object {
        const val SAVED_REPORT_ID = "shortcut-saved-report"
        val PROFILE = ReporterProfile(name = "Pier Dolique", phone = "+37256789012")
    }
}
