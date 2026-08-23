package com.perdolique.poleparkla.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoleParklaAppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addingAndUpdatingDraftDoesNotReplaceCurrentStartRoute() {
        var reports by mutableStateOf(emptyList<Report>())
        composeRule.setContent {
            Text(
                text = rememberInitialRoute(onboardingComplete = true, reports = reports),
                modifier = Modifier.testTag("initial_route"),
            )
        }

        composeRule.onNodeWithTag("initial_route").assertTextEquals("camera")

        composeRule.runOnIdle { reports = listOf(draftReport()) }
        composeRule.onNodeWithTag("initial_route").assertTextEquals("camera")

        composeRule.runOnIdle {
            reports = listOf(draftReport().copy(status = ReportStatus.READY))
        }
        composeRule.onNodeWithTag("initial_route").assertTextEquals("camera")
    }

    @Test
    fun readyAndHandedOffReportsRestoreDirectlyToTheirSummary() {
        var readyRoute = ""
        var handedOffRoute = ""
        composeRule.setContent {
            readyRoute = rememberInitialRoute(
                onboardingComplete = true,
                reports = listOf(draftReport().copy(status = ReportStatus.READY)),
            )
            handedOffRoute = rememberInitialRoute(
                onboardingComplete = true,
                reports = listOf(draftReport().copy(status = ReportStatus.HANDED_OFF_TO_MAIL)),
            )
        }

        composeRule.runOnIdle {
            assertEquals("review/draft", readyRoute)
            assertEquals("review/draft", handedOffRoute)
        }
    }

    @Test
    fun reviewBackUsesCameraFallbackWhenRestoredDraftIsTheStartRoute() {
        var fallbackCalls = 0

        navigateBackFromReview(
            popBackStack = { false },
            onEmptyBackStack = { fallbackCalls++ },
        )

        assertEquals(1, fallbackCalls)
    }

    @Test
    fun reviewBackDoesNotUseFallbackWhenNavigationCanPop() {
        var fallbackCalls = 0

        navigateBackFromReview(
            popBackStack = { true },
            onEmptyBackStack = { fallbackCalls++ },
        )

        assertEquals(0, fallbackCalls)
    }

    private fun draftReport() = Report(
        id = "draft",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        occurredAtEpochMillis = 1,
        status = ReportStatus.DRAFT,
        plate = "003 PUK",
        vehicleMake = "",
        vehicleModel = "",
        violationType = null,
        customTemplateId = null,
        recipient = "mupo@example.com",
        address = "Lastekodu tn 42, Tallinn",
        latitude = null,
        longitude = null,
        accuracyMeters = null,
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
        photos = listOf(
            ReportPhoto(
                id = "photo",
                reportId = "draft",
                filePath = "/missing.jpg",
                source = PhotoSource.CAMERA,
                capturedAtEpochMillis = 1,
                isPrimary = true,
            ),
        ),
    )
}
