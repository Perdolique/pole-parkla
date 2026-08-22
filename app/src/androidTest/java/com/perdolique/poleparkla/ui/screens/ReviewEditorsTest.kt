package com.perdolique.poleparkla.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.domain.ReportLocationInput
import com.perdolique.poleparkla.domain.ReportLocationInputResult
import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.AddressMapSelection
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LetterDraft
import com.perdolique.poleparkla.model.PlateSuggestion
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.PoleParklaTheme
import com.perdolique.poleparkla.ui.components.PpIcons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewEditorsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closingVehicleEditorDiscardsBufferedChanges() {
        var saved: VehicleDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                VehicleEditorSheet(
                    report = report(),
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onClose = {},
                    onSave = { plate, make, model -> saved = VehicleDetails(plate, make, model) },
                    onRecognize = {},
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_plate").performTextReplacement("999 XYZ")
        composeRule.onNodeWithTag("sheet_close").performClick()

        assertNull(saved)
    }

    @Test
    fun saveVehicleEditorSubmitsAllFieldsTogether() {
        var saved: VehicleDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                VehicleEditorSheet(
                    report = report(),
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onClose = {},
                    onSave = { plate, make, model -> saved = VehicleDetails(plate, make, model) },
                    onRecognize = {},
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_plate").performTextReplacement("999 XYZ")
        composeRule.onNodeWithTag("vehicle_make").performTextReplacement("Volvo")
        composeRule.onNodeWithTag("vehicle_model").performTextReplacement("XC40")
        composeRule.onNodeWithTag("vehicle_save").performClick()

        assertEquals(VehicleDetails("999 XYZ", "Volvo", "XC40"), saved)
    }

    @Test
    fun everyPhotoHasDirectRetryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photos = listOf(
            photo(id = "primary", isPrimary = true),
            photo(id = "secondary", isPrimary = false),
        )
        var retriedPhotoId: String? = null
        composeRule.setContent {
            PoleParklaTheme {
                PhotoEditorSheet(
                    report = report().copy(photos = photos),
                    photoStore = PhotoStore(context, File(context.cacheDir, "photo-editor-test")),
                    busy = false,
                    onClose = {},
                    onAddPhotos = {},
                    onRetry = { retriedPhotoId = it },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("photo_retry_primary").assertExists()
        composeRule.onNodeWithTag("photo_retry_secondary")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals("secondary", retriedPhotoId)
    }

    @Test
    fun photoActionsAreDisabledWhileRecognitionRuns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = photo(id = "busy", isPrimary = true)
        composeRule.setContent {
            PoleParklaTheme {
                PhotoEditorSheet(
                    report = report().copy(photos = listOf(photo)),
                    photoStore = PhotoStore(context, File(context.cacheDir, "photo-editor-busy-test")),
                    busy = true,
                    onClose = {},
                    onAddPhotos = {},
                    onRetry = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("photo_retry_busy").assertIsNotEnabled()
        composeRule.onNodeWithTag("photo_delete_busy").assertIsNotEnabled()
    }

    @Test
    fun ocrSuggestionIsOnlySavedAfterConfirmation() {
        var saved: VehicleDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                VehicleEditorSheet(
                    report = report().copy(
                        plateSuggestions = listOf(
                            PlateSuggestion(RecognitionSource.ML_KIT_OCR, "999 XYZ"),
                        ),
                    ),
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onClose = {},
                    onSave = { plate, make, model -> saved = VehicleDetails(plate, make, model) },
                    onRecognize = {},
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_suggestion_999 XYZ").performClick()
        assertNull(saved)
        composeRule.onNodeWithTag("vehicle_save").performScrollTo().performClick()

        assertEquals(VehicleDetails("999 XYZ", "", ""), saved)
    }

    @Test
    fun localPlateSuggestionUsesUserFacingSourceLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PoleParklaTheme {
                VehicleEditorSheet(
                    report = report().copy(
                        plateSuggestions = listOf(
                            PlateSuggestion(RecognitionSource.LOCAL_PLATE_MODEL, "003 OOO"),
                        ),
                    ),
                    busy = false,
                    cloudConfigured = false,
                    defaultProvider = CloudProvider.WORKERS_AI,
                    onClose = {},
                    onSave = { _, _, _ -> },
                    onRecognize = {},
                )
            }
        }

        composeRule.onNodeWithTag("vehicle_suggestion_003 OOO")
            .assertTextContains(
                context.getString(R.string.recognition_source_on_device),
                substring = true,
            )
    }

    @Test
    fun customViolationIsSavedImmediately() {
        var saved: ViolationDetails? = null
        val template = CustomViolationTemplate(
            id = "crossing",
            displayName = "Crossing",
            estonianDescription = "Sõiduk blokeerib ülekäigurada.",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        composeRule.setContent {
            PoleParklaTheme {
                ViolationEditorSheet(
                    report = report(),
                    templates = listOf(template),
                    onClose = {},
                    onSelect = { type, templateId -> saved = ViolationDetails(type, templateId) },
                )
            }
        }

        composeRule.onNodeWithTag("violation_template_${template.id}").performClick()

        assertEquals(ViolationDetails(ViolationType.CUSTOM, template.id), saved)
        composeRule.onNodeWithTag("violation_save").assertDoesNotExist()
    }

    @Test
    fun closingViolationEditorWithoutSelectionDoesNotSave() {
        var saved: ViolationDetails? = null
        var closed = false
        composeRule.setContent {
            PoleParklaTheme {
                ViolationEditorSheet(
                    report = report(),
                    templates = emptyList(),
                    onClose = { closed = true },
                    onSelect = { type, templateId -> saved = ViolationDetails(type, templateId) },
                )
            }
        }

        composeRule.onNodeWithTag("sheet_close").performClick()
        composeRule.waitUntil(5_000) { closed }

        assertNull(saved)
    }

    @Test
    fun cyclePathUsesBicycleIcon() {
        assertEquals(Icons.AutoMirrored.Outlined.DirectionsBike, PpIcons.CyclePath)
    }

    @Test
    fun coordinatesAreSavedThroughConfirmation() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(address = "", latitude = null, longitude = null),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "21.08.2026 11:55",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_coordinates_toggle").performClick()
        composeRule.onNodeWithTag("location_latitude").performTextReplacement("59.437")
        composeRule.onNodeWithTag("location_longitude").performTextReplacement("24.7536")
        assertNull(saved)
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        assertEquals(LocationDetails("", "59.437", "24.7536", "21.08.2026 12:00", null), saved)
    }

    @Test
    fun closingLocationEditorDiscardsBufferedChanges() {
        var saved: LocationDetails? = null
        var closed = false
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = { closed = true },
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_address").performTextReplacement("Unsaved address")
        composeRule.onNodeWithTag("sheet_close").performClick()
        composeRule.waitUntil(5_000) { closed }

        assertNull(saved)
    }

    @Test
    fun emptyTimeShowsOnlyTheTimeErrorWhenCoordinatesAreBlank() {
        var closed = false
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(latitude = null, longitude = null),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "21.08.2026 11:55",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = { closed = true },
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, _ ->
                        when (val result = ReportLocationInput.validate(address, latitude, longitude, time)) {
                            is ReportLocationInputResult.Valid -> null
                            is ReportLocationInputResult.Invalid -> result.error
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_time").performTextClearance()
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        composeRule.onNodeWithTag("location_time_error").assertExists()
        composeRule.onNodeWithTag("location_time").assertIsFocused()
        composeRule.onNodeWithTag("location_coordinates_error").assertDoesNotExist()
        assertEquals(false, closed)
    }

    @Test
    fun invalidCoordinatesExpandAndFocusTheCoordinateEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(latitude = null, longitude = null),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, _ ->
                        when (val result = ReportLocationInput.validate(address, latitude, longitude, time)) {
                            is ReportLocationInputResult.Valid -> null
                            is ReportLocationInputResult.Invalid -> result.error
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_coordinates_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.collapsed),
                ),
            )
        composeRule.onNodeWithTag("location_coordinates_toggle").performClick()
        composeRule.onNodeWithTag("location_latitude").performTextReplacement("not-a-coordinate")
        composeRule.onNodeWithTag("location_longitude").performTextReplacement("24.7536")
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        composeRule.onNodeWithTag("location_coordinates_error").assertExists()
        composeRule.onNodeWithTag("location_latitude").assertIsFocused()
        composeRule.onNodeWithTag("location_coordinates_toggle")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.expanded),
                ),
            )
    }

    @Test
    fun currentTimeButtonFillsTheCurrentTimeWithoutSaving() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "21.08.2026 11:55",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:34" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_time_now").performClick()

        composeRule.onNodeWithTag("location_time").assertTextContains("21.08.2026 12:34")
        assertNull(saved)
    }

    @Test
    fun photoTimeButtonFillsThePrimaryPhotoTimeWithoutSaving() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "20.08.2026 09:15",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:34" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_time_from_photo").performClick()

        composeRule.onNodeWithTag("location_time").assertTextContains("20.08.2026 09:15")
        assertNull(saved)
    }

    @Test
    fun photoSourceButtonsAreHiddenWithoutRealExifMetadata() {
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { _, _, _, _, _ -> null },
                )
            }
        }

        composeRule.onNodeWithTag("location_from_photo").assertDoesNotExist()
        composeRule.onNodeWithTag("location_time_from_photo").assertDoesNotExist()
        composeRule.onNodeWithTag("location_use_current").assertExists()
        composeRule.onNodeWithTag("location_time_now").assertExists()
    }

    @Test
    fun photoLocationButtonRequestsExifAddressCandidatesBeforeFillingWithoutSaving() {
        val suggestion = mutableStateOf<LocationEditorSuggestion?>(null)
        var photoLocationRequests = 0
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(address = "Old address", latitude = null, longitude = null),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = LocationEditorSuggestion(
                        address = "Tartu mnt 24",
                        latitude = "59.437",
                        longitude = "24.7536",
                        accuracyMeters = null,
                    ),
                    locationLoading = false,
                    suggestedLocation = suggestion.value,
                    onUsePhotoLocation = {
                        photoLocationRequests++
                        suggestion.value = LocationEditorSuggestion(
                            address = "Tartu mnt 24",
                            latitude = "59.437",
                            longitude = "24.7536",
                            accuracyMeters = null,
                        )
                    },
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_from_photo").performClick()
        assertEquals(1, photoLocationRequests)
        composeRule.onNodeWithTag("location_address").assertTextContains("Tartu mnt 24")
        composeRule.onNodeWithTag("location_coordinates_toggle").performClick()
        composeRule.onNodeWithTag("location_latitude").assertTextContains("59.437")
        composeRule.onNodeWithTag("location_longitude").assertTextContains("24.7536")
        assertNull(saved)
    }

    @Test
    fun locationAndTimeAreGroupedIntoOrderedSections() {
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { _, _, _, _, _ -> null },
                )
            }
        }

        val locationSection = composeRule.onNodeWithTag("location_section").fetchSemanticsNode().boundsInRoot
        val timeSection = composeRule.onNodeWithTag("time_section").fetchSemanticsNode().boundsInRoot
        assertTrue(locationSection.top < timeSection.top)
    }

    @Test
    fun currentLocationButtonRequestsTheCurrentLocationWithoutSaving() {
        var locationRequests = 0
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "21.08.2026 11:55",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = { locationRequests++ },
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_use_current").performClick()

        assertEquals(1, locationRequests)
        assertNull(saved)
    }

    @Test
    fun suggestedCurrentLocationIsSavedOnlyAfterConfirmation() {
        val suggestion = mutableStateOf<LocationEditorSuggestion?>(null)
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(address = "", latitude = null, longitude = null),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = "21.08.2026 11:55",
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = suggestion.value,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.runOnIdle {
            suggestion.value = LocationEditorSuggestion(
                address = "Tartu mnt 24",
                latitude = "59.437",
                longitude = "24.7536",
                accuracyMeters = 8.5f,
            )
        }
        composeRule.waitForIdle()
        assertNull(saved)
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        assertEquals(
            LocationDetails("Tartu mnt 24", "59.437", "24.7536", "21.08.2026 12:00", 8.5f),
            saved,
        )
    }

    @Test
    fun nearbyCandidatesAreRadioChoicesAndChangeOnlyTheAddressBuffer() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(latitude = 59.437, longitude = 24.7536, accuracyMeters = 8.5f),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = LocationEditorSuggestion(
                        address = null,
                        latitude = "59.437",
                        longitude = "24.7536",
                        accuracyMeters = 8.5f,
                        candidates = addressCandidates(),
                        applyLocation = false,
                    ),
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("location_street_candidate_0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("location_address").assertTextContains("Tartu mnt, Tallinn")
        assertNull(saved)
        composeRule.onNodeWithTag("location_coordinates_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("location_latitude").assertTextContains("59.437")
        composeRule.onNodeWithTag("location_longitude").assertTextContains("24.7536")
    }

    @Test
    fun savingConfirmsAnAmbiguousAddressChoice() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(locationNeedsReview = true),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = LocationEditorSuggestion(
                        address = null,
                        latitude = "",
                        longitude = "",
                        accuracyMeters = null,
                        candidates = addressCandidates(),
                        applyLocation = false,
                    ),
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_building_candidate_0").performScrollTo().performClick()
        assertNull(saved)
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        assertEquals("Tartu mnt 24, Tallinn", saved?.address)
    }

    @Test
    fun failedAddressLookupKeepsManualInputAndOffersRetry() {
        var retries = 0
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(
                        latitude = 59.437,
                        longitude = 24.7536,
                        accuracyMeters = 8.5f,
                    ),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = LocationEditorSuggestion(
                        address = null,
                        latitude = "",
                        longitude = "",
                        accuracyMeters = null,
                        addressLookupFailed = true,
                        applyLocation = false,
                    ),
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onRetryAddressLookup = { retries++ },
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { _, _, _, _, _ -> null },
                )
            }
        }

        composeRule.onNodeWithTag("location_lookup_error").assertExists()
        composeRule.onNodeWithTag("location_address").performTextReplacement("Manual address")
        composeRule.onNodeWithTag("location_lookup_retry").performScrollTo().assertIsEnabled().performClick()

        composeRule.onNodeWithTag("location_address").assertTextContains("Manual address")
        assertEquals(1, retries)
    }

    @Test
    fun failedCurrentLocationLookupAppliesCoordinatesWithoutErasingManualAddress() {
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(address = "Manual address"),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = LocationEditorSuggestion(
                        address = null,
                        latitude = "59.437",
                        longitude = "24.7536",
                        accuracyMeters = 8.5f,
                        addressLookupFailed = true,
                        applyLocation = true,
                    ),
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("location_address").assertTextContains("Manual address")
        composeRule.onNodeWithTag("location_coordinates_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("location_latitude").assertTextContains("59.437")
        composeRule.onNodeWithTag("location_longitude").assertTextContains("24.7536")
        composeRule.onNodeWithTag("location_save").performScrollTo().performClick()

        assertEquals("Manual address", saved?.address)
        assertEquals("59.437", saved?.latitude)
        assertEquals("24.7536", saved?.longitude)
    }

    @Test
    fun editingCoordinatesClearsCandidatesForThePreviousPoint() {
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report().copy(latitude = 59.437, longitude = 24.7536),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = LocationEditorSuggestion(
                        address = null,
                        latitude = "59.437",
                        longitude = "24.7536",
                        accuracyMeters = null,
                        candidates = addressCandidates(),
                        applyLocation = false,
                    ),
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { _, _, _, _, _ -> null },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("location_street_candidate_0").assertExists()
        composeRule.onNodeWithTag("location_coordinates_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("location_latitude").performTextReplacement("58.0")
        composeRule.onNodeWithTag("location_street_candidate_0").assertDoesNotExist()
    }

    @Test
    fun loadingAddressCandidatesDisablesLocationSourcesButKeepsManualInput() {
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = true,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { _, _, _, _, _ -> null },
                )
            }
        }

        composeRule.onNodeWithTag("location_use_current").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("location_address").performTextReplacement("Manual while loading")
        composeRule.onNodeWithTag("location_address").assertTextContains("Manual while loading")
    }

    @Test
    fun closingMapKeepsTheEditorBufferAndDoesNotSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var saved: LocationDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                LocationEditorSheet(
                    report = report(),
                    formattedTime = "21.08.2026 12:00",
                    photoTime = null,
                    photoLocation = null,
                    locationLoading = false,
                    suggestedLocation = null,
                    onUsePhotoLocation = {},
                    onClose = {},
                    onUseCurrentLocation = {},
                    onUseCurrentTime = { "21.08.2026 12:01" },
                    onSave = { address, latitude, longitude, time, accuracy ->
                        saved = LocationDetails(address, latitude, longitude, time, accuracy)
                        null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("location_address").performTextReplacement("Unsaved address")
        composeRule.onNodeWithTag("location_refine_map").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        composeRule.onNodeWithTag("location_address").assertTextContains("Unsaved address")
        assertNull(saved)
    }

    @Test
    fun pendingMapSelectionReturnsOnlyThroughUseButton() {
        val selection = AddressMapSelection(
            address = "Tartu mnt 24, Tallinn",
            latitude = 59.437,
            longitude = 24.7536,
            movedPoint = true,
            candidates = addressCandidates(),
        )
        var used: AddressMapSelection? = null
        composeRule.setContent {
            PoleParklaTheme {
                AddressMapPickerActions(
                    lookupState = AddressMapLookupState.Ready(selection),
                    onRetry = {},
                    onUseSelection = { used = it },
                )
            }
        }

        assertNull(used)
        composeRule.onNodeWithTag("address_map_use").performClick()
        assertEquals(selection, used)
    }

    @Test
    fun mapActionsStayDisabledBeforeTapAndWhileLookup() {
        val lookupState = mutableStateOf<AddressMapLookupState>(AddressMapLookupState.Idle)
        composeRule.setContent {
            PoleParklaTheme {
                AddressMapPickerActions(
                    lookupState = lookupState.value,
                    onRetry = {},
                    onUseSelection = {},
                )
            }
        }

        composeRule.onNodeWithTag("address_map_use").assertIsNotEnabled()
        composeRule.runOnIdle { lookupState.value = AddressMapLookupState.Loading }
        composeRule.onNodeWithTag("address_map_lookup_loading").assertExists()
        composeRule.onNodeWithTag("address_map_use").assertIsNotEnabled()
    }

    @Test
    fun unavailableMapPointOffersRetryAndReturnsOnlyAfterUse() {
        val selection = AddressMapSelection(
            address = "Manual address",
            latitude = 52.52,
            longitude = 13.405,
            movedPoint = true,
            addressLookupFailed = true,
        )
        var retries = 0
        var used: AddressMapSelection? = null
        composeRule.setContent {
            PoleParklaTheme {
                AddressMapPickerActions(
                    lookupState = AddressMapLookupState.Unavailable(selection),
                    onRetry = { retries++ },
                    onUseSelection = { used = it },
                )
            }
        }

        composeRule.onNodeWithTag("address_map_lookup_retry").performClick()
        assertEquals(1, retries)
        assertNull(used)
        composeRule.onNodeWithTag("address_map_use").performClick()
        assertEquals(selection, used)
    }

    @Test
    fun letterAndRecipientAreSavedThroughConfirmation() {
        var recipient: String? = null
        var letter: LetterDetails? = null
        composeRule.setContent {
            PoleParklaTheme {
                DeliveryEditorSheet(
                    report = report().copy(subject = "Old subject", body = "Old body"),
                    onClose = {},
                    onSaveRecipient = { recipient = it },
                    onSaveLetter = { subject, body -> letter = LetterDetails(subject, body) },
                    onSaveRegeneratedLetter = {},
                    onRegenerate = { null },
                )
            }
        }

        composeRule.onNodeWithTag("delivery_recipient")
            .performTextReplacement("qa@example.invalid")
        composeRule.onNodeWithTag("delivery_subject").performTextReplacement("New subject")
        composeRule.onNodeWithTag("delivery_body").performTextReplacement("New body")
        assertNull(recipient)
        assertNull(letter)
        composeRule.onNodeWithTag("delivery_save").performScrollTo().performClick()

        assertEquals("qa@example.invalid", recipient)
        assertEquals(LetterDetails("New subject", "New body"), letter)
    }

    @Test
    fun closingDeliveryEditorDiscardsBufferedChanges() {
        var recipient: String? = null
        var letter: LetterDetails? = null
        var regeneratedSaved = false
        var closed = false
        composeRule.setContent {
            PoleParklaTheme {
                DeliveryEditorSheet(
                    report = report().copy(subject = "Old subject", body = "Old body"),
                    onClose = { closed = true },
                    onSaveRecipient = { recipient = it },
                    onSaveLetter = { subject, body -> letter = LetterDetails(subject, body) },
                    onSaveRegeneratedLetter = { regeneratedSaved = true },
                    onRegenerate = { null },
                )
            }
        }

        composeRule.onNodeWithTag("delivery_recipient")
            .performTextReplacement("unsaved@example.invalid")
        composeRule.onNodeWithTag("delivery_subject").performTextReplacement("Unsaved subject")
        composeRule.onNodeWithTag("delivery_body").performTextReplacement("Unsaved body")
        composeRule.onNodeWithTag("sheet_close").performClick()
        composeRule.waitUntil(5_000) { closed }

        assertNull(recipient)
        assertNull(letter)
        assertEquals(false, regeneratedSaved)
    }

    @Test
    fun closingAfterRegenerateDiscardsGeneratedLetter() {
        var regeneratedSaved = false
        composeRule.setContent {
            PoleParklaTheme {
                DeliveryEditorSheet(
                    report = report().copy(subject = "Manual subject", body = "Manual body"),
                    onClose = {},
                    onSaveRecipient = {},
                    onSaveLetter = { _, _ -> },
                    onSaveRegeneratedLetter = { regeneratedSaved = true },
                    onRegenerate = { LetterDraft("Generated subject", "Generated body") },
                )
            }
        }

        composeRule.onNodeWithTag("delivery_regenerate").performScrollTo().performClick()
        composeRule.onNodeWithTag("sheet_close").performClick()

        assertEquals(false, regeneratedSaved)
    }

    @Test
    fun regeneratedLetterIsSavedOnlyAfterConfirmation() {
        var regeneratedSaved = false
        composeRule.setContent {
            PoleParklaTheme {
                DeliveryEditorSheet(
                    report = report().copy(subject = "Manual subject", body = "Manual body"),
                    onClose = {},
                    onSaveRecipient = {},
                    onSaveLetter = { _, _ -> },
                    onSaveRegeneratedLetter = { regeneratedSaved = true },
                    onRegenerate = { LetterDraft("Generated subject", "Generated body") },
                )
            }
        }

        composeRule.onNodeWithTag("delivery_regenerate").performScrollTo().performClick()
        assertEquals(false, regeneratedSaved)
        composeRule.onNodeWithTag("delivery_save").performScrollTo().performClick()

        assertEquals(true, regeneratedSaved)
    }

    private fun report() = Report(
        id = "report",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        occurredAtEpochMillis = 1,
        status = ReportStatus.DRAFT,
        plate = "123 ABC",
        vehicleMake = "",
        vehicleModel = "",
        violationType = null,
        customTemplateId = null,
        recipient = "mupo@example.com",
        address = "Tartu mnt 24",
        latitude = null,
        longitude = null,
        accuracyMeters = null,
        locationNeedsReview = false,
        subject = "",
        body = "",
        letterManuallyEdited = false,
        plateManuallyEdited = false,
        vehicleManuallyEdited = false,
        plateSuggestions = emptyList(),
        suggestedViolationType = null,
        mailOpenedAtEpochMillis = null,
        photos = emptyList(),
    )

    private fun photo(id: String, isPrimary: Boolean) = ReportPhoto(
        id = id,
        reportId = "report",
        filePath = "/missing-$id.jpg",
        source = PhotoSource.CAMERA,
        capturedAtEpochMillis = 1,
        isPrimary = isPrimary,
    )

    private fun addressCandidates() = listOf(
        AddressCandidate(
            address = "Tartu mnt, Tallinn",
            distanceMeters = 4,
            type = AddressCandidateType.STREET,
        ),
        AddressCandidate(
            address = "Tartu mnt 24, Tallinn",
            distanceMeters = 8,
            type = AddressCandidateType.BUILDING,
        ),
    )

    private data class VehicleDetails(val plate: String, val make: String, val model: String)
    private data class ViolationDetails(val type: ViolationType, val templateId: String?)
    private data class LocationDetails(
        val address: String,
        val latitude: String,
        val longitude: String,
        val time: String,
        val accuracyMeters: Float?,
    )
    private data class LetterDetails(val subject: String, val body: String)
}
