package com.perdolique.poleparkla.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.data.local.PoleParklaDatabase
import com.perdolique.poleparkla.data.local.PhotoEntity
import com.perdolique.poleparkla.domain.localRecognitionFingerprint
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.NormalizedPhotoRect
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.RecognitionPlateObservation
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        repository = ReportRepository(database, reportsDirectory)
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

        repository.mutateReport(REPORT_ID) { it.copy(plate = "003 PUK") }

        val updated = requireNotNull(repository.getReport(REPORT_ID))
        assertEquals("003 PUK", updated.plate)
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
    fun freshVersionOneSchemaContainsReportsPhotosTemplatesAndObservations() {
        val tables = mutableSetOf<String>()
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }

        assertTrue("reports" in tables)
        assertTrue("photos" in tables)
        assertTrue("custom_violation_templates" in tables)
        assertTrue("plate_observations" in tables)
        assertEquals(1, database.openHelper.readableDatabase.version)
    }

    @Test
    fun replacingOneRecognitionSourcePreservesOtherSourcesAndUpdatesPlateAtomically() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.ML_KIT_OCR, photo.id, "003 PUK"),
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.OPENAI, photo.id, "999 XYZ"),
        )
        val firstChange = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeReport(REPORT_ID).drop(1).first()
        }

        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.ML_KIT_OCR, photo.id, "555 ABC"),
        )

        val emitted = requireNotNull(withTimeout(5_000) { firstChange.await() })
        assertEquals(setOf("555 ABC", "999 XYZ"), emitted.plateObservations.map { it.value }.toSet())
        assertEquals(
            setOf(RecognitionSource.ML_KIT_OCR, RecognitionSource.OPENAI),
            emitted.plateObservations.map { it.source }.toSet(),
        )
        assertEquals("555 ABC", emitted.plate)
    }

    @Test
    fun failedPhotoFileDeletionRollsBackTheDatabaseChange() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.LOCAL_PLATE_MODEL, photo.id, "003 PUK"),
        )
        val undeletable = File(photo.filePath)
        assertTrue(undeletable.delete())
        assertTrue(undeletable.mkdir())
        File(undeletable, "child").writeText("keep")

        val failure = runCatching { repository.removePhoto(photo.id) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        val preserved = requireNotNull(repository.getReport(REPORT_ID))
        assertEquals(listOf(photo.id), preserved.photos.map(ReportPhoto::id))
        assertEquals(listOf("003 PUK"), preserved.plateObservations.map { it.value })
    }

    @Test
    fun cloudCandidatesWithoutBoundsAreAttributedOnlyToThePrimaryPhoto() = runBlocking {
        val primary = createPhoto(id = "primary")
        val secondary = createPhoto(id = "secondary", isPrimary = false)
        repository.createDraft(
            id = REPORT_ID,
            photos = listOf(primary, secondary),
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )

        repository.applyRecognition(
            REPORT_ID,
            RecognitionResult(
                source = RecognitionSource.OPENAI,
                plateCandidates = listOf("003 PUK"),
            ),
        )

        val observation = requireNotNull(repository.getReport(REPORT_ID)).plateObservations.single()
        assertEquals(primary.id, observation.photoId)
        assertEquals(null, observation.bounds)
    }

    @Test
    fun photoChangePreservesChosenPlateAndLocationButRequiresVehicleConfirmationAgain() = runBlocking {
        val original = createPhoto(id = "original")
        repository.createDraft(
            id = REPORT_ID,
            photo = original,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = false,
        )
        repository.mutateReport(REPORT_ID) {
            it.copy(
                plate = "USER 1",
                plateManuallyEdited = true,
                vehicleConfirmed = true,
                address = "Lastekodu tn 42, Tallinn",
                locationConfirmed = true,
            )
        }
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.LOCAL_PLATE_MODEL, original.id, "003 PUK"),
        )

        repository.addPhotos(REPORT_ID, listOf(createPhoto(id = "added", isPrimary = false)))

        val report = requireNotNull(repository.getReport(REPORT_ID))
        assertEquals("USER 1", report.plate)
        assertFalse(report.vehicleConfirmed)
        assertTrue(report.locationConfirmed)
        assertEquals("Lastekodu tn 42, Tallinn", report.address)
        assertTrue(report.plateObservations.isEmpty())
    }

    @Test
    fun addingPhotoEmitsOnlyTheNewPhotosWithResetReportState() = runBlocking {
        val original = createPhoto(id = "original")
        repository.createDraft(
            id = REPORT_ID,
            photo = original,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = false,
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.LOCAL_PLATE_MODEL, original.id, "003 PUK"),
        )
        repository.mutateReport(REPORT_ID) {
            it.copy(
                status = ReportStatus.HANDED_OFF_TO_MAIL,
                vehicleConfirmed = true,
                mailOpenedAtEpochMillis = 42L,
            )
        }
        val firstChange = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeReport(REPORT_ID).drop(1).first()
        }

        repository.addPhotos(REPORT_ID, listOf(createPhoto(id = "added", isPrimary = false)))

        val emitted = requireNotNull(withTimeout(5_000) { firstChange.await() })
        assertEquals(setOf("original", "added"), emitted.photos.mapTo(mutableSetOf()) { it.id })
        assertEquals(ReportStatus.DRAFT, emitted.status)
        assertFalse(emitted.vehicleConfirmed)
        assertEquals(null, emitted.mailOpenedAtEpochMillis)
        assertTrue(emitted.plateObservations.isEmpty())
        assertEquals("", emitted.localRecognitionFingerprint)
    }

    @Test
    fun removingPrimaryEmitsPromotedPhotoWithResetReportState() = runBlocking {
        val primary = createPhoto(id = "primary")
        val secondary = createPhoto(id = "secondary", isPrimary = false)
        repository.createDraft(
            id = REPORT_ID,
            photos = listOf(primary, secondary),
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = false,
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.LOCAL_PLATE_MODEL, primary.id, "003 PUK"),
        )
        repository.mutateReport(REPORT_ID) {
            it.copy(
                status = ReportStatus.HANDED_OFF_TO_MAIL,
                vehicleConfirmed = true,
                mailOpenedAtEpochMillis = 42L,
            )
        }
        val firstChange = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeReport(REPORT_ID).drop(1).first()
        }

        repository.removePhoto(primary.id)

        val emitted = requireNotNull(withTimeout(5_000) { firstChange.await() })
        val remaining = emitted.photos.single()
        assertEquals(secondary.id, remaining.id)
        assertTrue(remaining.isPrimary)
        assertEquals(ReportStatus.DRAFT, emitted.status)
        assertFalse(emitted.vehicleConfirmed)
        assertEquals(null, emitted.mailOpenedAtEpochMillis)
        assertTrue(emitted.plateObservations.isEmpty())
        assertEquals("", emitted.localRecognitionFingerprint)
    }

    @Test
    fun recognitionObservationMetadataSurvivesRepositoryRoundTrip() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        val bounds = NormalizedPhotoRect(0.11f, 0.22f, 0.77f, 0.44f)

        repository.applyRecognition(
            REPORT_ID,
            RecognitionResult(
                source = RecognitionSource.LOCAL_PLATE_MODEL,
                plateCandidates = listOf("003 PUK"),
                plateObservations = listOf(
                    RecognitionPlateObservation(
                        photoId = photo.id,
                        value = "003 PUK",
                        bounds = bounds,
                        detectionConfidence = 0.81f,
                        characterConfidence = 0.72f,
                        relativeArea = 0.031f,
                    ),
                ),
            ),
        )

        val observation = requireNotNull(repository.getReport(REPORT_ID)).plateObservations.single()
        assertEquals(photo.id, observation.photoId)
        assertEquals(RecognitionSource.LOCAL_PLATE_MODEL, observation.source)
        assertEquals(bounds, observation.bounds)
        assertEquals(0.81f, observation.detectionConfidence)
        assertEquals(0.72f, observation.characterConfidence)
        assertEquals(0.031f, observation.relativeArea)
    }

    @Test
    fun deletingPhotoOrReportCascadesItsRecognitionObservations() = runBlocking {
        val photo = createPhoto()
        repository.createDraft(
            id = REPORT_ID,
            photo = photo,
            settings = settings(),
            location = null,
            occurredAtEpochMillis = 1L,
            locationNeedsReview = true,
        )
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.LOCAL_PLATE_MODEL, photo.id, "003 PUK"),
        )

        database.dao().deletePhoto(
            PhotoEntity(
                id = photo.id,
                reportId = photo.reportId,
                filePath = photo.filePath,
                source = photo.source.name,
                capturedAtEpochMillis = photo.capturedAtEpochMillis,
                isPrimary = photo.isPrimary,
            ),
        )
        assertTrue(database.dao().getPlateObservations(REPORT_ID).isEmpty())

        val replacement = createPhoto(id = "replacement")
        repository.addPhotos(REPORT_ID, listOf(replacement))
        repository.applyRecognition(
            REPORT_ID,
            recognition(RecognitionSource.ML_KIT_OCR, replacement.id, "999 XYZ"),
        )
        database.dao().deleteReport(REPORT_ID)

        assertTrue(database.dao().getPlateObservations(REPORT_ID).isEmpty())
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
        profile = ReporterProfile("Pier Dolique", "+37256789012"),
    )

    private fun recognition(
        source: RecognitionSource,
        photoId: String,
        value: String,
    ) = RecognitionResult(
        source = source,
        plateCandidates = listOf(value),
        plateObservations = listOf(RecognitionPlateObservation(photoId, value)),
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
