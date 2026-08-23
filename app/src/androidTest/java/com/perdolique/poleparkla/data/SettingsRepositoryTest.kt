package com.perdolique.poleparkla.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.ReporterProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        repository.clearAll()
    }

    @After
    fun tearDown() = runBlocking {
        repository.clearAll()
    }

    @Test
    fun reviewAttemptCanOnlyBeReservedOnce() = runBlocking {
        assertTrue(repository.reserveInAppReviewAttempt())
        assertFalse(repository.reserveInAppReviewAttempt())
        assertFalse(SettingsRepository(ApplicationProvider.getApplicationContext()).reserveInAppReviewAttempt())
    }

    @Test
    fun deletingAllLocalDataRestoresReviewEligibility() = runBlocking {
        assertTrue(repository.reserveInAppReviewAttempt())

        repository.clearAll()

        assertTrue(repository.reserveInAppReviewAttempt())
    }

    @Test
    fun usesDutyOfficerAsDefaultRecipient() = runBlocking {
        assertEquals("korrapidaja@tallinnlv.ee", DEFAULT_RECIPIENT)
        assertEquals(DEFAULT_RECIPIENT, repository.settings.first().defaultRecipient)
    }

    @Test
    fun savingSettingsWritesAllValuesAndCanClearCloudConsents() = runBlocking {
        repository.setCloudConsent(CloudProvider.WORKERS_AI, true)
        repository.setCloudConsent(CloudProvider.OPENAI, true)

        repository.saveSettings(
            languageTag = "et",
            profile = ReporterProfile(" Pier Dolique ", " +37256789012 "),
            defaultRecipient = " reports@example.invalid ",
            workerUrl = " https://worker.example.invalid ",
            cloudProvider = CloudProvider.OPENAI,
            clearCloudConsents = false,
        )

        val settings = repository.settings.first()
        assertEquals("et", settings.languageTag)
        assertEquals(ReporterProfile("Pier Dolique", "+37256789012"), settings.profile)
        assertEquals("reports@example.invalid", settings.defaultRecipient)
        assertEquals("https://worker.example.invalid", settings.workerUrl)
        assertEquals(CloudProvider.OPENAI, settings.cloudProvider)
        assertTrue(settings.workersAiConsent)
        assertTrue(settings.openAiConsent)

        repository.saveSettings(
            languageTag = settings.languageTag,
            profile = settings.profile,
            defaultRecipient = settings.defaultRecipient,
            workerUrl = settings.workerUrl,
            cloudProvider = settings.cloudProvider,
            clearCloudConsents = true,
        )

        val settingsWithoutConsents = repository.settings.first()
        assertFalse(settingsWithoutConsents.workersAiConsent)
        assertFalse(settingsWithoutConsents.openAiConsent)
    }
}
