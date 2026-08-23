package com.perdolique.poleparkla.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.PoleParklaApplication
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.RecognitionPlateObservation
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoleParklaViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PoleParklaApplication).container
    private lateinit var viewModel: PoleParklaViewModel
    private lateinit var photoFile: File

    @Before
    fun setUp() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
        container.settingsRepository.completeOnboarding(
            languageTag = "en",
            profile = PROFILE,
            defaultRecipient = DEFAULT_RECIPIENT,
        )
        photoFile = File(context.cacheDir, "mail-return-review.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("street_plate_test_image.png")
            .use { input -> photoFile.outputStream().use(input::copyTo) }
        container.reportRepository.createDraft(
            id = REPORT_ID,
            photo = ReportPhoto(
                id = "photo",
                reportId = REPORT_ID,
                filePath = photoFile.absolutePath,
                source = PhotoSource.CAMERA,
                capturedAtEpochMillis = 1L,
                isPrimary = true,
            ),
            settings = AppSettings(
                loaded = true,
                onboardingComplete = true,
                profile = PROFILE,
                defaultRecipient = DEFAULT_RECIPIENT,
            ),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = false,
        )
        container.reportRepository.mutateReport(REPORT_ID) { report ->
            report.copy(
                status = ReportStatus.READY,
                plate = "003 PUK",
                plateManuallyEdited = true,
                vehicleConfirmed = true,
                violationType = ViolationType.CYCLE_PATH,
                address = "Lastekodu tn 42, Tallinn",
                locationConfirmed = true,
                subject = "Subject",
                body = "Body",
            )
        }
        viewModel = PoleParklaViewModel(container)
    }

    @After
    fun tearDown() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
        photoFile.delete()
        Unit
    }

    @Test
    fun firstMailReturnRequestsReviewWithoutRewritingRepeatedReturn() = runBlocking {
        val firstEffect = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) { viewModel.effects.first() }
        }

        viewModel.handleMailClientReturned(REPORT_ID)

        assertEquals(UiEffect.RequestInAppReview, firstEffect.await())
        val firstReturn = requireNotNull(container.reportRepository.getReport(REPORT_ID))
        assertEquals(ReportStatus.HANDED_OFF_TO_MAIL, firstReturn.status)
        assertNotNull(firstReturn.mailOpenedAtEpochMillis)
        val secondEffect = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(250L) { viewModel.effects.first() }
        }

        viewModel.handleMailClientReturned(REPORT_ID)

        assertNull(secondEffect.await())
        val repeatedReturn = requireNotNull(container.reportRepository.getReport(REPORT_ID))
        assertEquals(firstReturn.updatedAtEpochMillis, repeatedReturn.updatedAtEpochMillis)
        assertEquals(firstReturn.mailOpenedAtEpochMillis, repeatedReturn.mailOpenedAtEpochMillis)
    }

    @Test
    fun confirmedFieldChangesAlwaysRegenerateTheStoredLetter() = runBlocking {
        viewModel.updateVehicleDetails(REPORT_ID, "999 XYZ", "Volvo", "XC40")
        val vehicleUpdated = withTimeout(5_000L) {
            container.reportRepository.observeReport(REPORT_ID).first { report ->
                report?.plate == "999 XYZ" && report.body.contains("999 XYZ")
            }
        }

        assertTrue(requireNotNull(vehicleUpdated).vehicleConfirmed)
        assertTrue(vehicleUpdated.body.contains("999 XYZ"))

        val error = viewModel.updateLocation(
            reportId = REPORT_ID,
            address = "Pärnu mnt 10, Tallinn",
            latitude = "",
            longitude = "",
            occurredAt = viewModel.formatOccurredAt(1_786_629_480_000),
            accuracyMeters = null,
        )
        assertNull(error)
        val locationUpdated = withTimeout(5_000L) {
            container.reportRepository.observeReport(REPORT_ID).first { report ->
                report?.address == "Pärnu mnt 10, Tallinn" &&
                    report.locationConfirmed &&
                    report.body.contains("Pärnu mnt 10, Tallinn")
            }
        }

        assertTrue(requireNotNull(locationUpdated).locationConfirmed)
    }

    @Test
    fun retryingLocalRecognitionPreservesCloudObservations() = runBlocking {
        container.reportRepository.applyRecognition(
            REPORT_ID,
            RecognitionResult(
                source = RecognitionSource.WORKERS_AI,
                plateObservations = listOf(
                    RecognitionPlateObservation(photoId = "photo", value = "999 XYZ"),
                ),
            ),
        )

        viewModel.retryPhotoRecognition(REPORT_ID, "photo")
        withTimeout(5_000L) { viewModel.busy.first { it } }
        withTimeout(30_000L) { viewModel.busy.first { !it } }

        val recognized = requireNotNull(container.reportRepository.getReport(REPORT_ID))
        assertTrue(
            recognized.plateObservations.any { observation ->
                observation.source == RecognitionSource.WORKERS_AI && observation.value == "999 XYZ"
            },
        )
    }

    private companion object {
        const val REPORT_ID = "mail-return-review"
        val PROFILE = ReporterProfile(name = "Pier Dolique", phone = "+37256789012")
    }
}
