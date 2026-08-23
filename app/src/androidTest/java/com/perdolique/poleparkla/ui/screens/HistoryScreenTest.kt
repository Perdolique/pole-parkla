package com.perdolique.poleparkla.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.PoleParklaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportCardOpensAndOverflowRequiresDeleteConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val report = report()
        composeRule.setContent {
            PoleParklaTheme {
                HistoryScreen(
                    reports = listOf(report),
                    photoStore = PhotoStore(context, File(context.cacheDir, "history-screen-test")),
                    onBack = {},
                    onNewReport = {},
                    onOpen = { opened += it },
                    onDelete = { deleted += it },
                )
            }
        }

        composeRule.onNodeWithTag("history_report_${report.id}").performClick()
        assertEquals(listOf(report.id), opened)

        composeRule.onNodeWithTag("history_more_${report.id}").performClick()
        composeRule.onNodeWithTag("history_delete_${report.id}").performClick()
        assertEquals(emptyList<String>(), deleted)

        composeRule.onNodeWithTag("history_confirm_delete").performClick()
        assertEquals(listOf(report.id), deleted)
    }

    private fun report() = Report(
        id = "history-report",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        occurredAtEpochMillis = 1,
        status = ReportStatus.READY,
        plate = "003 PUK",
        vehicleMake = "Volvo",
        vehicleModel = "XC40",
        violationType = ViolationType.CYCLE_PATH,
        customTemplateId = null,
        recipient = "recipient@example.invalid",
        address = "Lastekodu tn 42, Tallinn",
        latitude = null,
        longitude = null,
        accuracyMeters = null,
        locationNeedsReview = false,
        subject = "Subject",
        body = "Body",
        plateManuallyEdited = false,
        vehicleManuallyEdited = false,
        vehicleConfirmed = true,
        locationConfirmed = true,
        plateObservations = emptyList(),
        suggestedViolationType = null,
        mailOpenedAtEpochMillis = null,
        photos = emptyList(),
    )
}
