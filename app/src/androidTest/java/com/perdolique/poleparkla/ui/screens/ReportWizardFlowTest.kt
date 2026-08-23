package com.perdolique.poleparkla.ui.screens

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.PoleParklaApplication
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.testing.MailDraftCaptureActivity
import com.perdolique.poleparkla.ui.MailPreparation
import com.perdolique.poleparkla.ui.PoleParklaTheme
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportWizardFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PoleParklaApplication).container
    private lateinit var viewModel: PoleParklaViewModel
    private lateinit var photoFile: File
    @Volatile
    private var capturedDraftIntent: Intent? = null
    private val mailDraftReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            capturedDraftIntent = intent?.getParcelableExtra(MailDraftCaptureActivity.EXTRA_DRAFT_INTENT)
        }
    }

    @Before
    fun setUp() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
        container.settingsRepository.completeOnboarding(
            languageTag = "en",
            profile = ReporterProfile("Pier Dolique", "+37256789012"),
            defaultRecipient = "recipient@example.invalid",
        )
        photoFile = File(context.cacheDir, "wizard-flow.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("street_plate_test_image.png")
            .use { input -> photoFile.outputStream().use(input::copyTo) }
        val photo = ReportPhoto(
            id = PHOTO_ID,
            reportId = REPORT_ID,
            filePath = photoFile.absolutePath,
            source = PhotoSource.CAMERA,
            capturedAtEpochMillis = 1_786_629_480_000,
            isPrimary = true,
        )
        container.reportRepository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = AppSettings(
                loaded = true,
                onboardingComplete = true,
                profile = ReporterProfile("Pier Dolique", "+37256789012"),
                defaultRecipient = "recipient@example.invalid",
            ),
            location = null,
            occurredAtEpochMillis = photo.capturedAtEpochMillis,
            locationNeedsReview = true,
        )
        container.reportRepository.mutateReport(REPORT_ID) {
            it.copy(
                plate = "003 PUK",
                plateManuallyEdited = true,
                address = "Lastekodu tn 42, Tallinn",
            )
        }
        viewModel = PoleParklaViewModel(container)
        capturedDraftIntent = null
        context.registerReceiver(
            mailDraftReceiver,
            IntentFilter(MailDraftCaptureActivity.ACTION_DRAFT_CAPTURED),
            Context.RECEIVER_EXPORTED,
        )
        Unit
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(mailDraftReceiver)
        runBlocking {
            container.reportRepository.deleteAll()
            container.settingsRepository.clearAll()
            photoFile.delete()
        }
    }

    @Test
    fun happyPathRequiresBothConfirmationsAndProblemTapOpensSummary() {
        composeRule.setContent {
            PoleParklaTheme {
                ReportWizardScreen(
                    reportId = REPORT_ID,
                    viewModel = viewModel,
                    photoStore = PhotoStore(context, File(context.filesDir, "reports")),
                    onBack = {},
                    onAddPhotos = {},
                    onEditSettings = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("vehicle_save").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("violation_cycle_path").assertDoesNotExist()
        composeRule.onNodeWithTag("vehicle_save").performClick()

        composeRule.onNodeWithTag("violation_cycle_path").assertDoesNotExist()
        composeRule.onNodeWithTag("location_date").assertExists()
        composeRule.onNodeWithTag("location_time").assertExists()
        composeRule.onNodeWithTag("location_save").performClick()
        composeRule.onNodeWithTag("violation_cycle_path").performClick()

        composeRule.onNodeWithTag("summary_vehicle").assertExists()
        composeRule.onNodeWithTag("summary_location").assertExists()
        composeRule.onNodeWithTag("summary_problem").assertExists()
        composeRule.onNodeWithTag("delivery_body").assertDoesNotExist()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("review_primary_action").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("review_primary_action").performClick()
        composeRule.waitUntil(10_000) {
            viewModel.mailPreparation.value is MailPreparation.Ready
        }
        composeRule.onNodeWithText(TEST_MAIL_APP_LABEL).performClick()
        composeRule.waitUntil(5_000) { capturedDraftIntent != null }

        val draftIntent = requireNotNull(capturedDraftIntent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, draftIntent.action)
        assertEquals(
            listOf("recipient@example.invalid"),
            draftIntent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList(),
        )
        assertNotNull(draftIntent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(draftIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("003 PUK"))
        @Suppress("DEPRECATION")
        val attachments = draftIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(1, attachments?.size)
        assertTrue(draftIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun invalidPlaceAnnouncesLocalizedErrorAndFocusesAddress() {
        runBlocking {
            container.reportRepository.mutateReport(REPORT_ID) {
                it.copy(address = "", latitude = null, longitude = null)
            }
        }
        setWizardContent()
        openLocationStep()

        composeRule.onNodeWithTag("location_save").performClick()

        val message = context.getString(R.string.invalid_location_value)
        composeRule.onNodeWithTag("location_address")
            .assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))
        composeRule.onNodeWithTag("location_address_error").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive),
        )
    }

    @Test
    fun invalidCoordinatesExpandAdvancedFieldsAndFocusLatitude() {
        runBlocking {
            container.reportRepository.mutateReport(REPORT_ID) {
                it.copy(latitude = 59.43, longitude = null)
            }
        }
        setWizardContent()
        openLocationStep()

        composeRule.onNodeWithTag("location_save").performClick()

        val message = context.getString(R.string.invalid_coordinates_values)
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("location_latitude").assertIsFocused()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("location_latitude").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, message),
        )
        composeRule.onNodeWithTag("location_coordinates_error").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive),
        )
    }

    @Test
    fun leavingLocationWithoutConfirmationDiscardsTheLocalDraft() {
        setWizardContent()
        openLocationStep()

        composeRule.onNodeWithTag("location_address").performTextReplacement("Draft only")
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        composeRule.onNodeWithTag("vehicle_save").assertExists()
        val stored = runBlocking { requireNotNull(container.reportRepository.getReport(REPORT_ID)) }
        assertEquals("Lastekodu tn 42, Tallinn", stored.address)
        assertEquals(false, stored.locationConfirmed)
    }

    @Test
    fun editingVehicleFromSummaryReturnsStraightToSummary() {
        runBlocking {
            container.reportRepository.mutateReport(REPORT_ID) {
                it.copy(
                    vehicleConfirmed = true,
                    locationConfirmed = true,
                    locationNeedsReview = false,
                    violationType = com.perdolique.poleparkla.model.ViolationType.CYCLE_PATH,
                    subject = "Subject",
                    body = "Body",
                )
            }
        }
        composeRule.setContent {
            PoleParklaTheme {
                ReportWizardScreen(
                    reportId = REPORT_ID,
                    viewModel = viewModel,
                    photoStore = PhotoStore(context, File(context.filesDir, "reports")),
                    onBack = {},
                    onAddPhotos = {},
                    onEditSettings = {},
                )
            }
        }

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("summary_vehicle").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("summary_vehicle").performClick()
        composeRule.onNodeWithTag("vehicle_plate").performTextReplacement("999 XYZ")
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("vehicle_save").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("vehicle_save").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("summary_vehicle").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("999 XYZ").assertExists()
        assertEquals(
            "999 XYZ",
            runBlocking { requireNotNull(container.reportRepository.getReport(REPORT_ID)).plate },
        )
    }

    @Test
    fun dateOpensSystemPicker() {
        setWizardContent()
        openLocationStep()

        composeRule.onNodeWithTag("location_date").performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
    }

    @Test
    fun timeOpensSystemPicker() {
        setWizardContent()
        openLocationStep()

        composeRule.onNodeWithTag("location_time").performScrollTo().performClick()
        onView(isAssignableFrom(TimePicker::class.java)).check(matches(isDisplayed()))
    }

    @Test
    fun reconfirmingVehicleSkipsAlreadyConfirmedLocationAndProblem() {
        runBlocking {
            container.reportRepository.mutateReport(REPORT_ID) {
                it.copy(
                    status = ReportStatus.DRAFT,
                    vehicleConfirmed = false,
                    locationConfirmed = true,
                    locationNeedsReview = false,
                    violationType = ViolationType.CYCLE_PATH,
                    subject = "Subject",
                    body = "Body",
                )
            }
        }
        composeRule.setContent {
            PoleParklaTheme {
                ReportWizardScreen(
                    reportId = REPORT_ID,
                    viewModel = viewModel,
                    photoStore = PhotoStore(context, File(context.filesDir, "reports")),
                    onBack = {},
                    onAddPhotos = {},
                    onEditSettings = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("vehicle_save").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("vehicle_save").performClick()

        composeRule.onNodeWithTag("summary_vehicle").assertExists()
        composeRule.onNodeWithTag("location_save").assertDoesNotExist()
    }

    private fun setWizardContent() {
        composeRule.setContent {
            PoleParklaTheme {
                ReportWizardScreen(
                    reportId = REPORT_ID,
                    viewModel = viewModel,
                    photoStore = PhotoStore(context, File(context.filesDir, "reports")),
                    onBack = {},
                    onAddPhotos = {},
                    onEditSettings = {},
                )
            }
        }
    }

    private fun openLocationStep() {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("vehicle_save").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("vehicle_save").performClick()
        composeRule.onNodeWithTag("location_save").assertExists()
    }

    private companion object {
        const val REPORT_ID = "wizard-flow"
        const val PHOTO_ID = "wizard-photo"
        const val TEST_MAIL_APP_LABEL = "Pole parkla test mail"
    }
}
