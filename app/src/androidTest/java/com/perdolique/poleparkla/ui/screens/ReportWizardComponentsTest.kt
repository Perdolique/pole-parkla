package com.perdolique.poleparkla.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.NormalizedPhotoRect
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.PlateObservation
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.PoleParklaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportWizardComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val photoStore by lazy { PhotoStore(context, File(context.cacheDir, "wizard-components")) }

    @Test
    fun detectedPlateStillRequiresExplicitVehicleConfirmation() {
        var saved: String? = null
        composeRule.setContent {
            PoleParklaTheme {
                VehicleWizardStep(
                    report = report().copy(
                        plate = "003 PUK",
                        vehicleConfirmed = false,
                        plateObservations = listOf(observation("003 PUK", "photo")),
                    ),
                    photoStore = photoStore,
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onOpenPhoto = {},
                    onRecognize = {},
                    onConfirm = { plate, _, _ -> saved = plate },
                )
            }
        }

        assertNull(saved)
        composeRule.onNodeWithTag("vehicle_save").performClick()
        assertEquals("003 PUK", saved)
    }

    @Test
    fun selectingAlternativeChangesTheConfirmedPlate() {
        var saved: String? = null
        composeRule.setContent {
            PoleParklaTheme {
                VehicleWizardStep(
                    report = report().copy(
                        plate = "003 PUK",
                        vehicleConfirmed = false,
                        plateObservations = listOf(
                            observation("003 PUK", "photo"),
                            observation("999 XYZ", "photo-2"),
                        ),
                    ),
                    photoStore = photoStore,
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onOpenPhoto = {},
                    onRecognize = {},
                    onConfirm = { plate, _, _ -> saved = plate },
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_suggestion_999 XYZ").performClick()
        composeRule.onNodeWithTag("vehicle_save").performClick()

        assertEquals("999 XYZ", saved)
    }

    @Test
    fun recognitionInProgressDisablesVehicleConfirmation() {
        composeRule.setContent {
            PoleParklaTheme {
                VehicleWizardStep(
                    report = report().copy(plate = "003 PUK", vehicleConfirmed = false),
                    photoStore = photoStore,
                    busy = true,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onOpenPhoto = {},
                    onRecognize = {},
                    onConfirm = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_save").assertIsNotEnabled()
    }

    @Test
    fun evidenceWithoutBoundsUsesTheFullPhotoFallbackWithoutStartingACrop() {
        composeRule.setContent {
            PoleParklaTheme {
                VehicleWizardStep(
                    report = report().copy(
                        plate = "003 PUK",
                        plateObservations = listOf(observation("003 PUK", "photo").copy(bounds = null)),
                    ),
                    photoStore = photoStore,
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onOpenPhoto = {},
                    onRecognize = {},
                    onConfirm = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("plate_evidence_fallback", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("plate_evidence_loading", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("plate_evidence_crop", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun boundedCropDecodeFailureEnablesTheFullPhotoFallback() {
        composeRule.setContent {
            PoleParklaTheme {
                VehicleWizardStep(
                    report = report().copy(
                        plate = "003 PUK",
                        plateObservations = listOf(observation("003 PUK", "photo")),
                    ),
                    photoStore = photoStore,
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onOpenPhoto = {},
                    onRecognize = {},
                    onConfirm = { _, _, _ -> },
                )
            }
        }

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("plate_evidence_fallback", useUnmergedTree = true).assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("plate_evidence_loading", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("plate_evidence_crop", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tappingProblemCommitsItImmediately() {
        var chosen: ViolationType? = null
        composeRule.setContent {
            PoleParklaTheme {
                ProblemWizardStep(
                    report = report(),
                    templates = emptyList(),
                    onSelect = { type, _ -> chosen = type },
                )
            }
        }

        composeRule.onNodeWithTag("violation_cycle_path").performClick()

        assertEquals(ViolationType.CYCLE_PATH, chosen)
    }

    @Test
    fun summaryShowsSubjectButNeverFullLetterBody() {
        val fullBody = "SECRET FULL LETTER BODY"
        composeRule.setContent {
            PoleParklaTheme {
                ReportSummaryStep(
                    report = report().copy(
                        plate = "003 PUK",
                        vehicleConfirmed = true,
                        locationConfirmed = true,
                        violationType = ViolationType.CYCLE_PATH,
                        subject = "Illegal parking report",
                        body = fullBody,
                    ),
                    profile = ReporterProfile("Pier Dolique", "+37256789012"),
                    templates = emptyList(),
                    photoStore = photoStore,
                    busy = false,
                    editorsEnabled = true,
                    onPhotos = {},
                    onVehicle = {},
                    onLocation = {},
                    onProblem = {},
                    onRecipient = {},
                    onSender = {},
                    onOpenMail = {},
                )
            }
        }

        composeRule.onNodeWithText("Illegal parking report").assertExists()
        composeRule.onNodeWithText(fullBody).assertDoesNotExist()
        composeRule.onNodeWithTag("delivery_body").assertDoesNotExist()
        listOf(
            "summary_vehicle",
            "summary_location",
            "summary_problem",
            "summary_recipient",
            "summary_sender",
            "summary_subject",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.state_complete),
                ),
            )
        }
    }

    @Test
    fun wizardLabelsDoNotUseRequiredAsterisks() {
        listOf(R.string.plate, R.string.violation, R.string.recipient).forEach { label ->
            assertFalse(context.getString(label).contains('*'))
        }
    }

    @Test
    fun recipientEditorContainsNoSubjectOrBodyEditors() {
        composeRule.setContent {
            PoleParklaTheme {
                RecipientEditorSheet("recipient@example.invalid", onClose = {}, onSave = {})
            }
        }

        composeRule.onNodeWithTag("delivery_recipient").assertExists()
        composeRule.onNodeWithTag("delivery_subject").assertDoesNotExist()
        composeRule.onNodeWithTag("delivery_body").assertDoesNotExist()
    }

    private fun report() = Report(
        id = "report",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        occurredAtEpochMillis = 1_786_629_480_000,
        status = ReportStatus.DRAFT,
        plate = "",
        vehicleMake = "",
        vehicleModel = "",
        violationType = null,
        customTemplateId = null,
        recipient = "recipient@example.invalid",
        address = "Lastekodu tn 42, Tallinn",
        latitude = 59.43,
        longitude = 24.75,
        accuracyMeters = 8f,
        locationNeedsReview = false,
        subject = "",
        body = "",
        plateManuallyEdited = false,
        vehicleManuallyEdited = false,
        vehicleConfirmed = false,
        locationConfirmed = false,
        plateObservations = emptyList(),
        suggestedViolationType = null,
        mailOpenedAtEpochMillis = null,
        photos = listOf(photo("photo", true)),
    )

    private fun photo(id: String, primary: Boolean) = ReportPhoto(
        id = id,
        reportId = "report",
        filePath = "/missing-$id.jpg",
        source = PhotoSource.CAMERA,
        capturedAtEpochMillis = 1,
        isPrimary = primary,
    )

    private fun observation(value: String, photoId: String) = PlateObservation(
        id = "$photoId-$value",
        reportId = "report",
        photoId = photoId,
        source = RecognitionSource.LOCAL_PLATE_MODEL,
        value = value,
        bounds = NormalizedPhotoRect(0.1f, 0.2f, 0.5f, 0.35f),
        detectionConfidence = 0.9f,
        characterConfidence = 0.9f,
        relativeArea = 0.03f,
    )
}
