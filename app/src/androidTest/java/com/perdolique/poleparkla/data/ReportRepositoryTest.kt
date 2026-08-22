package com.perdolique.poleparkla.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.data.local.PoleParklaDatabase
import com.perdolique.poleparkla.domain.localRecognitionFingerprint
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReporterProfile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {
    private lateinit var database: PoleParklaDatabase
    private lateinit var reportsDirectory: File
    private lateinit var repository: ReportRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PoleParklaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reportsDirectory = File(context.cacheDir, "report-repository-${UUID.randomUUID()}").apply { mkdirs() }
        repository = ReportRepository(database.dao(), reportsDirectory)
    }

    @After
    fun tearDown() {
        database.close()
        reportsDirectory.deleteRecursively()
    }

    @Test
    fun updatingReportPreservesItsPhotoRowAndFile() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )

        repository.mutateReport(REPORT_ID) { it.copy(plate = "123 ABC") }

        val updated = requireNotNull(repository.getReport(REPORT_ID))
        assertEquals("123 ABC", updated.plate)
        assertEquals(listOf(photo.id), updated.photos.map { it.id })
        assertTrue(File(photo.filePath).exists())
    }

    @Test
    fun unchangedMutationPreservesUpdatedTimestamp() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        val before = requireNotNull(repository.getReport(REPORT_ID))
        delay(20L)

        repository.mutateReport(REPORT_ID) { it }

        assertEquals(
            before.updatedAtEpochMillis,
            requireNotNull(repository.getReport(REPORT_ID)).updatedAtEpochMillis,
        )
    }

    @Test
    fun addingPhotoToEmptyDraftMakesItPrimary() = runBlocking {
        val original = createPhoto(id = "original")
        repository.createDraft(
            id = REPORT_ID,
            photo = original,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        repository.removePhoto(original.id)

        val replacement = createPhoto(id = "replacement", isPrimary = false)
        repository.addPhotos(REPORT_ID, listOf(replacement))

        val storedPhoto = requireNotNull(repository.getReport(REPORT_ID)).photos.single()
        assertEquals(replacement.id, storedPhoto.id)
        assertTrue(storedPhoto.isPrimary)
    }

    @Test
    fun photoChangesInvalidateOnlyCurrentLocalRecognitionCompletion() = runBlocking {
        val original = createPhoto(id = "original")
        repository.createDraft(
            id = REPORT_ID,
            photo = original,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        val originalFingerprint = listOf(original).localRecognitionFingerprint()
        repository.markLocalRecognitionComplete(REPORT_ID, originalFingerprint)

        assertEquals(
            originalFingerprint,
            requireNotNull(repository.getReport(REPORT_ID)).localRecognitionFingerprint,
        )

        val added = createPhoto(id = "added", isPrimary = false)
        repository.addPhotos(REPORT_ID, listOf(added))
        assertEquals("", requireNotNull(repository.getReport(REPORT_ID)).localRecognitionFingerprint)

        repository.markLocalRecognitionComplete(REPORT_ID, originalFingerprint)
        assertEquals("", requireNotNull(repository.getReport(REPORT_ID)).localRecognitionFingerprint)

        val current = requireNotNull(repository.getReport(REPORT_ID))
        val currentFingerprint = current.photos.localRecognitionFingerprint()
        repository.markLocalRecognitionComplete(REPORT_ID, currentFingerprint)
        assertEquals(
            currentFingerprint,
            requireNotNull(repository.getReport(REPORT_ID)).localRecognitionFingerprint,
        )
    }

    @Test
    fun deletingTemplateKeepsOtherTemplates() = runBlocking {
        val deleted = customTemplate("deleted", "Loading zone")
        val kept = customTemplate("kept", "School entrance")
        repository.upsertTemplate(deleted)
        repository.upsertTemplate(kept)

        repository.deleteTemplate(deleted.id)

        assertEquals(listOf(kept.id), repository.observeTemplates().first().map { it.id })
    }

    @Test
    fun deletingAllRemovesDatabaseRowsTemplatesAndReportFiles() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        repository.upsertTemplate(
            CustomViolationTemplate(
                id = "template",
                displayName = "Loading zone",
                estonianDescription = "Sõiduk blokeerib laadimisala.",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )

        repository.deleteAll()

        assertEquals(emptyList<Any>(), repository.observeReports().first())
        assertEquals(emptyList<Any>(), repository.observeTemplates().first())
        assertFalse(File(photo.filePath).exists())
        assertTrue(reportsDirectory.listFiles().isNullOrEmpty())
    }

    private fun createPhoto(id: String = "photo", isPrimary: Boolean = true): ReportPhoto {
        val file = File(reportsDirectory, "$REPORT_ID/$id.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }
        return ReportPhoto(
            id = id,
            reportId = REPORT_ID,
            filePath = file.absolutePath,
            source = PhotoSource.CAMERA,
            capturedAtEpochMillis = 1L,
            isPrimary = isPrimary,
        )
    }

    private fun settings() = AppSettings(
        loaded = true,
        onboardingComplete = true,
        profile = ReporterProfile("Mari", "+372 5555"),
    )

    private fun customTemplate(id: String, displayName: String) = CustomViolationTemplate(
        id = id,
        displayName = displayName,
        estonianDescription = "Sõiduk blokeerib ala.",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private companion object {
        const val REPORT_ID = "report"
    }
}
