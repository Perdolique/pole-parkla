package com.perdolique.poleparkla.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.PoleParklaApplication
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.PhotoSource
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoleParklaViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PoleParklaApplication).container
    private lateinit var viewModel: PoleParklaViewModel

    @Before
    fun setUp() = runBlocking {
        container.reportRepository.deleteAll()
        container.settingsRepository.clearAll()
        container.settingsRepository.completeOnboarding(
            languageTag = "en",
            profile = PROFILE,
            defaultRecipient = DEFAULT_RECIPIENT,
        )
        container.reportRepository.createDraft(
            id = REPORT_ID,
            photo = ReportPhoto(
                id = "photo",
                reportId = REPORT_ID,
                filePath = File(context.cacheDir, "mail-return-review.jpg").absolutePath,
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
                plate = "123 ABC",
                violationType = ViolationType.CYCLE_PATH,
                address = "Tallinn",
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

    private companion object {
        const val REPORT_ID = "mail-return-review"
        val PROFILE = ReporterProfile(name = "Mari", phone = "+372 5555")
    }
}
