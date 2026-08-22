package com.perdolique.poleparkla.data

import com.perdolique.poleparkla.data.local.CustomViolationTemplateEntity
import com.perdolique.poleparkla.data.local.PhotoEntity
import com.perdolique.poleparkla.data.local.ReportEntity
import com.perdolique.poleparkla.data.local.ReportWithPhotos
import com.perdolique.poleparkla.data.local.PoleParklaDao
import com.perdolique.poleparkla.domain.RecognitionMerger
import com.perdolique.poleparkla.domain.localRecognitionFingerprint
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.PlateSuggestion
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ViolationType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReportRepository(
    private val dao: PoleParklaDao,
    private val reportsDirectory: File,
) {
    private val mutationMutex = Mutex()

    fun observeReports(): Flow<List<Report>> = dao.observeReports().map { reports ->
        reports.map(ReportWithPhotos::toDomain)
    }

    fun observeReport(id: String): Flow<Report?> = dao.observeReport(id).map { it?.toDomain() }

    suspend fun getReport(id: String): Report? = dao.getReport(id)?.toDomain()

    fun observeTemplates(): Flow<List<CustomViolationTemplate>> = dao.observeTemplates().map { templates ->
        templates.map(CustomViolationTemplateEntity::toDomain)
    }

    suspend fun createDraft(
        id: String,
        photo: ReportPhoto,
        settings: AppSettings,
        location: LocationSnapshot?,
        occurredAtEpochMillis: Long,
        locationNeedsReview: Boolean,
    ) = createDraft(
        id = id,
        photos = listOf(photo),
        settings = settings,
        location = location,
        occurredAtEpochMillis = occurredAtEpochMillis,
        locationNeedsReview = locationNeedsReview,
    )

    suspend fun createDraft(
        id: String,
        photos: List<ReportPhoto>,
        settings: AppSettings,
        location: LocationSnapshot?,
        occurredAtEpochMillis: Long,
        locationNeedsReview: Boolean,
    ) = mutationMutex.withLock {
        require(photos.isNotEmpty()) { "A draft requires at least one photo" }
        require(photos.size <= 3) { "A report can contain at most three photos" }
        require(photos.all { it.reportId == id }) { "Every photo must belong to the draft" }
        val primaryPhotoId = photos.firstOrNull(ReportPhoto::isPrimary)?.id ?: photos.first().id
        val normalizedPhotos = photos.map { it.copy(isPrimary = it.id == primaryPhotoId) }
        val now = System.currentTimeMillis()
        val report = ReportEntity(
            id = id,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            occurredAtEpochMillis = occurredAtEpochMillis,
            status = ReportStatus.DRAFT.name,
            plate = "",
            vehicleMake = "",
            vehicleModel = "",
            violationType = null,
            customTemplateId = null,
            recipient = settings.defaultRecipient,
            address = "",
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracyMeters,
            locationNeedsReview = locationNeedsReview,
            subject = "",
            body = "",
            letterManuallyEdited = false,
            plateManuallyEdited = false,
            vehicleManuallyEdited = false,
            plateSuggestions = "",
            suggestedViolationType = null,
            mailOpenedAtEpochMillis = null,
            localRecognitionFingerprint = "",
        )
        dao.insertDraft(report, normalizedPhotos.map(ReportPhoto::toEntity))
    }

    suspend fun addPhotos(reportId: String, photos: List<ReportPhoto>) = mutationMutex.withLock {
        if (photos.isEmpty()) return@withLock
        val report = dao.getReport(reportId) ?: return@withLock
        require(report.photos.size + photos.size <= 3) { "A report can contain at most three photos" }
        require(photos.all { it.reportId == reportId }) { "Every photo must belong to the report" }
        val primaryPhotoId = if (report.photos.any(PhotoEntity::isPrimary)) {
            null
        } else {
            photos.firstOrNull(ReportPhoto::isPrimary)?.id ?: photos.first().id
        }
        dao.upsertPhotos(
            photos.map { photo ->
                photo.copy(isPrimary = photo.id == primaryPhotoId).toEntity()
            },
        )
        touchAfterPhotoChange(reportId)
    }

    suspend fun removePhoto(photoId: String) = mutationMutex.withLock {
        val photo = dao.getPhoto(photoId) ?: return@withLock
        withContext(Dispatchers.IO) {
            val file = File(photo.filePath)
            check(!file.exists() || file.delete()) { "Unable to delete report photo" }
        }
        dao.deletePhoto(photo)
        val remaining = dao.getReport(photo.reportId)?.photos.orEmpty()
        if (photo.isPrimary && remaining.isNotEmpty()) {
            dao.upsertPhoto(remaining.first().copy(isPrimary = true))
        }
        touchAfterPhotoChange(photo.reportId)
    }

    suspend fun mutateReport(id: String, transform: (Report) -> Report) = mutationMutex.withLock {
        val current = dao.getReport(id)?.toDomain() ?: return@withLock
        val transformed = transform(current)
        if (transformed == current) return@withLock
        val updated = transformed.copy(updatedAtEpochMillis = System.currentTimeMillis())
        dao.upsertReport(updated.toEntity())
    }

    suspend fun applyRecognition(id: String, result: RecognitionResult) =
        mutateReport(id) { report -> RecognitionMerger.merge(report, result) }

    suspend fun clearAutomaticRecognition(id: String) =
        mutateReport(id) { report ->
            RecognitionMerger.clearAutomatic(report).copy(localRecognitionFingerprint = "")
        }

    suspend fun markLocalRecognitionComplete(id: String, expectedFingerprint: String) =
        mutateReport(id) { report ->
            if (report.photos.localRecognitionFingerprint() == expectedFingerprint) {
                report.copy(localRecognitionFingerprint = expectedFingerprint)
            } else {
                report
            }
        }

    suspend fun upsertTemplate(template: CustomViolationTemplate) = mutationMutex.withLock {
        dao.upsertTemplate(template.toEntity())
    }

    suspend fun deleteTemplate(id: String) = mutationMutex.withLock {
        dao.deleteTemplate(id)
    }

    suspend fun deleteReport(id: String) = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            dao.getReport(id)?.photos?.forEach { photo ->
                val file = File(photo.filePath)
                check(!file.exists() || file.delete()) { "Unable to delete report photo" }
            }
            val directory = File(reportsDirectory, id)
            check(!directory.exists() || directory.deleteRecursively()) { "Unable to delete report directory" }
        }
        dao.deleteReport(id)
    }

    suspend fun deleteAll() = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            reportsDirectory.listFiles()?.forEach { directory ->
                check(directory.deleteRecursively()) { "Unable to delete report directory" }
            }
        }
        dao.deleteAllReports()
        dao.deleteAllTemplates()
    }

    private suspend fun touchAfterPhotoChange(reportId: String) {
        val current = dao.getReport(reportId)?.report ?: return
        dao.upsertReport(
            current.copy(
                updatedAtEpochMillis = System.currentTimeMillis(),
                localRecognitionFingerprint = "",
            ),
        )
    }
}

private fun ReportWithPhotos.toDomain(): Report = report.toDomain(photos)

private fun ReportEntity.toDomain(photos: List<PhotoEntity>): Report = Report(
    id = id,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    occurredAtEpochMillis = occurredAtEpochMillis,
    status = enumValueOrDefault(status, ReportStatus.DRAFT),
    plate = plate,
    vehicleMake = vehicleMake,
    vehicleModel = vehicleModel,
    violationType = violationType?.let { enumValueOrNull<ViolationType>(it) },
    customTemplateId = customTemplateId,
    recipient = recipient,
    address = address,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    locationNeedsReview = locationNeedsReview,
    subject = subject,
    body = body,
    letterManuallyEdited = letterManuallyEdited,
    plateManuallyEdited = plateManuallyEdited,
    vehicleManuallyEdited = vehicleManuallyEdited,
    plateSuggestions = decodeSuggestions(plateSuggestions),
    suggestedViolationType = suggestedViolationType?.let { enumValueOrNull<ViolationType>(it) },
    mailOpenedAtEpochMillis = mailOpenedAtEpochMillis,
    photos = photos.map(PhotoEntity::toDomain).sortedBy(ReportPhoto::capturedAtEpochMillis),
    localRecognitionFingerprint = localRecognitionFingerprint,
)

private fun Report.toEntity(): ReportEntity = ReportEntity(
    id = id,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    occurredAtEpochMillis = occurredAtEpochMillis,
    status = status.name,
    plate = plate,
    vehicleMake = vehicleMake,
    vehicleModel = vehicleModel,
    violationType = violationType?.name,
    customTemplateId = customTemplateId,
    recipient = recipient,
    address = address,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    locationNeedsReview = locationNeedsReview,
    subject = subject,
    body = body,
    letterManuallyEdited = letterManuallyEdited,
    plateManuallyEdited = plateManuallyEdited,
    vehicleManuallyEdited = vehicleManuallyEdited,
    plateSuggestions = encodeSuggestions(plateSuggestions),
    suggestedViolationType = suggestedViolationType?.name,
    mailOpenedAtEpochMillis = mailOpenedAtEpochMillis,
    localRecognitionFingerprint = localRecognitionFingerprint,
)

private fun PhotoEntity.toDomain(): ReportPhoto = ReportPhoto(
    id = id,
    reportId = reportId,
    filePath = filePath,
    source = enumValueOrDefault(source, PhotoSource.GALLERY),
    capturedAtEpochMillis = capturedAtEpochMillis,
    isPrimary = isPrimary,
)

private fun ReportPhoto.toEntity(): PhotoEntity = PhotoEntity(
    id = id,
    reportId = reportId,
    filePath = filePath,
    source = source.name,
    capturedAtEpochMillis = capturedAtEpochMillis,
    isPrimary = isPrimary,
)

private fun CustomViolationTemplateEntity.toDomain(): CustomViolationTemplate =
    CustomViolationTemplate(id, displayName, estonianDescription, createdAtEpochMillis, updatedAtEpochMillis)

private fun CustomViolationTemplate.toEntity(): CustomViolationTemplateEntity =
    CustomViolationTemplateEntity(id, displayName, estonianDescription, createdAtEpochMillis, updatedAtEpochMillis)

private fun encodeSuggestions(suggestions: List<PlateSuggestion>): String = suggestions.joinToString("\n") {
    "${it.source.name}\t${it.value.replace("\t", " ").replace("\n", " ")}"
}

private fun decodeSuggestions(encoded: String): List<PlateSuggestion> = encoded.lineSequence()
    .mapNotNull { line ->
        val parts = line.split('\t', limit = 2)
        val source = parts.firstOrNull()?.let { enumValueOrNull<RecognitionSource>(it) }
        val value = parts.getOrNull(1)?.takeIf(String::isNotBlank)
        if (source != null && value != null) PlateSuggestion(source, value) else null
    }
    .toList()

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValueOrNull<T>(value) ?: default
