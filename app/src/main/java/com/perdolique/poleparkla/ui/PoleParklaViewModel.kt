package com.perdolique.poleparkla.ui

import android.content.ComponentName
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.perdolique.poleparkla.AppContainer
import com.perdolique.poleparkla.domain.AddressResolutionFreshness
import com.perdolique.poleparkla.domain.InAppReviewEligibility
import com.perdolique.poleparkla.domain.LocationFreshness
import com.perdolique.poleparkla.domain.ReportLocationInput
import com.perdolique.poleparkla.domain.ReportLocationInputError
import com.perdolique.poleparkla.domain.ReportLocationInputResult
import com.perdolique.poleparkla.domain.ReportTransitions
import com.perdolique.poleparkla.domain.ViolationTemplates
import com.perdolique.poleparkla.domain.hasCurrentLocalRecognition
import com.perdolique.poleparkla.domain.localRecognitionFingerprint
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LetterDraft
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.CloudRecognitionException
import com.perdolique.poleparkla.service.MailApp
import com.perdolique.poleparkla.service.StoredGalleryPhoto
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NoticeKind {
    PHOTO_FAILED,
    OCR_FAILED,
    PLATE_RECOGNITION_FAILED,
    CLOUD_FAILED,
    INVALID_REPORT,
    MAIL_FAILED,
    LOCATION_UNAVAILABLE,
    SAVED,
    DATA_DELETED,
    DATA_DELETE_FAILED,
}

data class UiNotice(val kind: NoticeKind, val detail: String? = null)

sealed interface UiEffect {
    data object RequestInAppReview : UiEffect
}

data class CameraCaptureTarget(
    val reportId: String,
    val file: File,
    internal val dataGeneration: Long,
)

data class PhotoEditorMetadata(
    val capturedAtEpochMillis: Long?,
    val location: LocationSnapshot?,
)

data class AddressLookupResult(
    val location: LocationSnapshot,
    val resolution: AddressResolution?,
)

private data class PendingAddressResolution(
    val location: LocationSnapshot?,
    val needsReview: Boolean,
)

sealed interface MailPreparation {
    data object Idle : MailPreparation
    data class Ready(
        val report: Report,
        val files: List<File>,
        val apps: List<MailApp>,
        val savedComponent: ComponentName?,
    ) : MailPreparation
}

class PoleParklaViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    val reports = container.reportRepository.observeReports()
        .map<List<Report>, List<Report>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val templates = container.reportRepository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val cloudTokenRevision = MutableStateFlow(0)
    val cloudConfigured = combine(settings, cloudTokenRevision) { currentSettings, _ ->
        currentSettings.workerUrl.isNotBlank() &&
            container.secureTokenStore.read(currentSettings.workerUrl).isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _cameraReportId = MutableStateFlow(UUID.randomUUID().toString())
    val cameraReportId = _cameraReportId.asStateFlow()
    private val _latestLocation = MutableStateFlow<com.perdolique.poleparkla.model.LocationSnapshot?>(null)
    val latestLocation = _latestLocation.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _mailPreparation = MutableStateFlow<MailPreparation>(MailPreparation.Idle)
    val mailPreparation = _mailPreparation.asStateFlow()
    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()
    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private val photoMutex = Mutex()
    private val recognitionMutex = Mutex()
    private val addressResolutionMutex = Mutex()
    private val addressResolutions = mutableMapOf<AddressLookupKey, AddressResolution>()
    private val activeRecognitionReports = mutableSetOf<String>()
    private var locationJob: Job? = null
    private var dataSessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var dataMutationScope = CoroutineScope(viewModelScope.coroutineContext + dataSessionJob)
    private var addressLookupSessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var addressLookupScope = CoroutineScope(viewModelScope.coroutineContext + addressLookupSessionJob)
    private var acceptingDataMutations = true
    private var photoInputEnabled = true
    private var dataGeneration = 0L

    fun report(reportId: String): Flow<Report?> = container.reportRepository.observeReport(reportId)

    fun startNewCameraSession() {
        photoInputEnabled = true
        _latestLocation.value = null
        _cameraReportId.value = UUID.randomUUID().toString()
    }

    fun continueCameraSession(reportId: String) {
        photoInputEnabled = true
        _latestLocation.value = null
        _cameraReportId.value = reportId
    }

    fun createCameraTarget(): CameraCaptureTarget {
        check(photoInputEnabled) { "Photo input is unavailable" }
        val reportId = _cameraReportId.value
        return CameraCaptureTarget(
            reportId = reportId,
            file = container.photoStore.createCameraTarget(reportId),
            dataGeneration = dataGeneration,
        )
    }

    fun startLocationUpdates() {
        _latestLocation.value = null
        if (!container.locationService.hasPermission()) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            container.locationService.updates().collect { _latestLocation.value = it }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _latestLocation.value = null
    }

    fun onCameraPhotoCaptured(target: CameraCaptureTarget) {
        if (!target.isCurrent()) {
            target.file.delete()
            return
        }
        launchDataMutation {
            photoMutex.withLock {
                if (!target.isCurrent()) {
                    target.file.delete()
                    return@withLock
                }
                _busy.value = true
                var persisted = false
                try {
                    val addressResolution = persistCameraPhoto(target.reportId, target.file)
                    persisted = true
                    completeCameraPhoto(target.reportId, addressResolution)
                } catch (error: CancellationException) {
                    if (!persisted) target.file.delete()
                    throw error
                } catch (_: Exception) {
                    if (!persisted) target.file.delete()
                    _notices.emit(UiNotice(NoticeKind.PHOTO_FAILED))
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun onCameraCaptureFailed(target: CameraCaptureTarget) {
        target.file.delete()
        if (!target.isCurrent()) return
        _notices.tryEmit(UiNotice(NoticeKind.PHOTO_FAILED))
    }

    fun addGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || !photoInputEnabled) return
        val generation = dataGeneration
        launchDataMutation {
            photoMutex.withLock {
                if (!photoInputEnabled || generation != dataGeneration) return@withLock
                _busy.value = true
                val imported = mutableListOf<StoredGalleryPhoto>()
                var persisted = false
                try {
                    val reportId = _cameraReportId.value
                    val report = container.reportRepository.getReport(reportId)
                    val remaining = (3 - report.orEmptyPhotoCount()).coerceAtLeast(0)
                    val fallback = _latestLocation.value ?: container.locationService.currentLocation()
                    uris.take(remaining).forEachIndexed { index, uri ->
                        imported += container.photoStore.importGalleryPhoto(
                            reportId = reportId,
                            uri = uri,
                            isPrimary = report.orEmptyPhotoCount() == 0 && index == 0,
                            fallbackLocation = fallback,
                        )
                    }
                    if (imported.isEmpty()) return@withLock
                    if (report == null) {
                        val first = imported.first()
                        val loadedSettings = settings.first { it.loaded }
                        container.reportRepository.createDraft(
                            id = reportId,
                            photos = imported.map { it.photo },
                            settings = loadedSettings,
                            location = first.exifLocation,
                            occurredAtEpochMillis = first.photo.capturedAtEpochMillis,
                            locationNeedsReview = first.metadataNeedsReview,
                        )
                        persisted = true
                        updateResolvedAddress(reportId, first.exifLocation, first.metadataNeedsReview)
                    } else {
                        container.reportRepository.addPhotos(reportId, imported.map { it.photo })
                        persisted = true
                    }
                    container.reportRepository.clearAutomaticRecognition(reportId)
                    recognizeLocally(reportId)
                } catch (error: CancellationException) {
                    if (!persisted) imported.forEach { File(it.photo.filePath).delete() }
                    throw error
                } catch (_: Exception) {
                    if (!persisted) imported.forEach { File(it.photo.filePath).delete() }
                    _notices.emit(UiNotice(NoticeKind.PHOTO_FAILED))
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun removePhoto(reportId: String, photoId: String) {
        if (_busy.value) return
        launchDataMutation {
            _busy.value = true
            try {
                container.photoStore.clearTemporaryCopies(reportId)
                container.reportRepository.removePhoto(photoId)
                val report = container.reportRepository.getReport(reportId)
                if (report != null) {
                    container.reportRepository.clearAutomaticRecognition(reportId)
                    if (report.photos.isEmpty()) {
                        refreshLetter(reportId)
                    } else {
                        recognizeLocally(reportId)
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun retryPhotoRecognition(reportId: String, photoId: String) {
        if (
            !acceptingDataMutations ||
            _busy.value ||
            !activeRecognitionReports.add(reportId)
        ) return
        launchDataMutation {
            _busy.value = true
            try {
                recognitionMutex.withLock {
                    val report = container.reportRepository.getReport(reportId) ?: return@withLock
                    if (report.photos.none { it.id == photoId }) return@withLock
                    container.textRecognitionService.invalidate(photoId)
                    container.plateRecognitionService.invalidate(photoId)
                    recognizeLocallyLocked(reportId, report)
                }
            } finally {
                activeRecognitionReports.remove(reportId)
                _busy.value = false
            }
        }
    }

    fun ensureLocalRecognition(reportId: String) {
        if (
            !acceptingDataMutations ||
            !activeRecognitionReports.add(reportId)
        ) return
        launchDataMutation {
            try {
                recognitionMutex.withLock {
                    val report = container.reportRepository.getReport(reportId) ?: return@withLock
                    if (
                        report.photos.isEmpty() ||
                        report.status == ReportStatus.HANDED_OFF_TO_MAIL ||
                        report.hasCurrentLocalRecognition()
                    ) return@withLock
                    _busy.value = true
                    try {
                        recognizeLocallyLocked(reportId, report)
                    } finally {
                        _busy.value = false
                    }
                }
            } finally {
                activeRecognitionReports.remove(reportId)
            }
        }
    }

    fun updateVehicleDetails(reportId: String, plate: String, make: String, model: String) =
        updateReport(reportId) {
            it.copy(
                plate = plate.uppercase(),
                vehicleMake = make,
                vehicleModel = model,
                plateManuallyEdited = true,
                vehicleManuallyEdited = true,
            )
        }

    fun chooseViolation(reportId: String, type: ViolationType, customTemplateId: String? = null) =
        updateReport(reportId) {
            it.copy(
                violationType = type,
                customTemplateId = if (type == ViolationType.CUSTOM) customTemplateId else null,
            )
        }

    fun updateRecipient(reportId: String, value: String) = updateReport(reportId) {
        it.copy(recipient = value)
    }

    fun updateLocation(
        reportId: String,
        address: String,
        latitude: String,
        longitude: String,
        occurredAt: String,
        accuracyMeters: Float?,
    ): ReportLocationInputError? {
        val parsed = when (
            val result = ReportLocationInput.validate(address, latitude, longitude, occurredAt)
        ) {
            is ReportLocationInputResult.Valid -> result.location
            is ReportLocationInputResult.Invalid -> return result.error
        }
        updateReport(reportId) { report ->
            report.copy(
                address = parsed.address,
                latitude = parsed.latitude,
                longitude = parsed.longitude,
                accuracyMeters = if (parsed.latitude != null && parsed.longitude != null) {
                    accuracyMeters
                } else {
                    null
                },
                occurredAtEpochMillis = parsed.occurredAtEpochMillis,
                locationNeedsReview = false,
            )
        }
        return null
    }

    fun loadCurrentLocation(onLoaded: (AddressLookupResult?) -> Unit) {
        addressLookupScope.launch {
            val location = container.locationService.currentLocation()
            if (location == null) {
                _notices.emit(UiNotice(NoticeKind.LOCATION_UNAVAILABLE))
                onLoaded(null)
                return@launch
            }
            onLoaded(AddressLookupResult(location, resolveAddressOrNull(location)))
        }
    }

    suspend fun readPhotoEditorMetadata(photo: ReportPhoto): PhotoEditorMetadata {
        val metadata = container.photoStore.readExifMetadata(photo)
        return PhotoEditorMetadata(
            capturedAtEpochMillis = metadata.capturedAtEpochMillis,
            location = metadata.location,
        )
    }

    fun loadPhotoLocation(
        location: LocationSnapshot,
        onLoaded: (AddressLookupResult) -> Unit,
    ) {
        addressLookupScope.launch {
            onLoaded(AddressLookupResult(location, resolveAddressOrNull(location)))
        }
    }

    fun loadAddressCandidates(
        location: LocationSnapshot,
        forceRefresh: Boolean,
        onLoaded: (AddressLookupResult) -> Unit,
    ) {
        addressLookupScope.launch {
            onLoaded(AddressLookupResult(location, resolveAddressOrNull(location, forceRefresh)))
        }
    }

    internal suspend fun resolveAddress(
        location: LocationSnapshot,
        forceRefresh: Boolean,
    ): AddressResolution? = resolveAddressOrNull(location, forceRefresh)

    fun updateLetter(reportId: String, subject: String, body: String) = updateReport(
        reportId,
        regenerate = false,
    ) { it.copy(subject = subject, body = body, letterManuallyEdited = true) }

    fun regenerateLetter(reportId: String) = updateReport(reportId, regenerate = true) {
        it.copy(letterManuallyEdited = false)
    }

    fun previewRegeneratedLetter(report: Report): LetterDraft? {
        val description = violationDescription(report, templates.value) ?: return null
        return container.emailTemplateRenderer.render(report, settings.value.profile, description)
    }

    fun recognizeWithCloud(reportId: String, provider: CloudProvider) {
        launchDataMutation {
            _busy.value = true
            var image: File? = null
            try {
                val report = requireNotNull(container.reportRepository.getReport(reportId))
                image = container.photoStore.prepareCloudImage(requireNotNull(report.primaryPhoto))
                val result = container.cloudRecognitionService.recognize(
                    workerUrl = settings.value.workerUrl,
                    bearerToken = container.secureTokenStore.read(settings.value.workerUrl),
                    provider = provider,
                    image = requireNotNull(image),
                )
                container.reportRepository.applyRecognition(reportId, result)
                refreshLetter(reportId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val detail = (error as? CloudRecognitionException)?.code
                _notices.emit(UiNotice(NoticeKind.CLOUD_FAILED, detail))
            } finally {
                image?.delete()
                _busy.value = false
            }
        }
    }

    fun setCloudConsent(provider: CloudProvider, consented: Boolean) {
        launchDataMutation { container.settingsRepository.setCloudConsent(provider, consented) }
    }

    fun completeOnboarding(
        languageTag: String,
        profile: ReporterProfile,
        recipient: String,
        onComplete: () -> Unit,
    ) {
        launchDataMutation {
            container.settingsRepository.completeOnboarding(languageTag, profile, recipient)
            startNewCameraSession()
            onComplete()
        }
    }

    fun setLanguage(languageTag: String) {
        launchDataMutation { container.settingsRepository.setLanguage(languageTag) }
    }

    fun saveSettings(
        languageTag: String,
        profile: ReporterProfile,
        recipient: String,
        workerUrl: String,
        provider: CloudProvider,
        newToken: String,
        onSaved: () -> Unit,
    ) {
        launchDataMutation {
            val currentSettings = settings.first { it.loaded }
            val normalizedWorkerUrl = workerUrl.trim()
            val workerUrlChanged = currentSettings.workerUrl.normalizedWorkerUrl() !=
                normalizedWorkerUrl.normalizedWorkerUrl()
            if (workerUrlChanged) {
                container.secureTokenStore.clear()
            }
            if (newToken.isNotBlank()) {
                container.secureTokenStore.save(newToken, normalizedWorkerUrl)
            }
            container.settingsRepository.saveSettings(
                languageTag = languageTag,
                profile = profile,
                defaultRecipient = recipient,
                workerUrl = normalizedWorkerUrl,
                cloudProvider = provider,
                clearCloudConsents = workerUrlChanged,
            )
            cloudTokenRevision.value++
            reports.value.orEmpty().forEach { refreshLetter(it.id) }
            _notices.emit(UiNotice(NoticeKind.SAVED))
            onSaved()
        }
    }

    fun clearToken() {
        launchDataMutation {
            container.secureTokenStore.clear()
            cloudTokenRevision.value++
        }
    }

    fun upsertTemplate(id: String?, displayName: String, description: String) {
        if (displayName.isBlank() || description.isBlank()) return
        launchDataMutation {
            val now = System.currentTimeMillis()
            val existing = templates.value.firstOrNull { it.id == id }
            val updatedTemplate = CustomViolationTemplate(
                id = existing?.id ?: UUID.randomUUID().toString(),
                displayName = displayName.trim(),
                estonianDescription = description.trim(),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
            container.reportRepository.upsertTemplate(updatedTemplate)
            if (existing != null) {
                val templateSnapshot = templates.value
                    .filterNot { it.id == updatedTemplate.id } + updatedTemplate
                reports.value.orEmpty()
                    .filter { it.customTemplateId == updatedTemplate.id }
                    .forEach { report ->
                        updateReportNow(
                            reportId = report.id,
                            regenerate = false,
                            templateSnapshot = templateSnapshot,
                        ) { it }
                    }
            }
        }
    }

    fun deleteTemplate(id: String) {
        launchDataMutation {
            container.reportRepository.deleteTemplate(id)
            reports.value.orEmpty()
                .filter { it.customTemplateId == id }
                .forEach { report ->
                    updateReportNow(report.id, regenerate = false) {
                        it.copy(
                            violationType = null,
                            customTemplateId = null,
                            subject = if (it.letterManuallyEdited) it.subject else "",
                            body = if (it.letterManuallyEdited) it.body else "",
                        )
                    }
                }
        }
    }

    fun deleteReport(id: String) {
        launchDataMutation {
            try {
                container.photoStore.clearTemporaryCopies(id)
                container.reportRepository.deleteReport(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _notices.emit(UiNotice(NoticeKind.DATA_DELETE_FAILED))
            }
        }
    }

    fun deleteAllLocalData(onDeleted: () -> Unit) {
        if (!acceptingDataMutations) return
        acceptingDataMutations = false
        photoInputEnabled = false
        dataGeneration++
        stopLocationUpdates()
        val previousSession = dataSessionJob
        val previousAddressLookupSession = addressLookupSessionJob
        previousSession.cancel()
        previousAddressLookupSession.cancel()
        viewModelScope.launch {
            _busy.value = true
            try {
                previousSession.cancelAndJoin()
                previousAddressLookupSession.cancelAndJoin()
                photoMutex.withLock {
                    container.reportRepository.deleteAll()
                    container.settingsRepository.clearAll()
                    container.secureTokenStore.clear()
                    cloudTokenRevision.value++
                    container.photoStore.clearTemporaryCopies()
                }
                addressResolutionMutex.withLock { addressResolutions.clear() }
                container.clearMapCache()
                activeRecognitionReports.clear()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                _latestLocation.value = null
                _cameraReportId.value = UUID.randomUUID().toString()
                _mailPreparation.value = MailPreparation.Idle
                startDataMutationSession()
                startAddressLookupSession()
                _notices.emit(UiNotice(NoticeKind.DATA_DELETED))
                onDeleted()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                photoInputEnabled = true
                startDataMutationSession()
                startAddressLookupSession()
                _notices.emit(UiNotice(NoticeKind.DATA_DELETE_FAILED))
            } finally {
                _busy.value = false
            }
        }
    }

    fun prepareMail(reportId: String) {
        launchDataMutation {
            val report = container.reportRepository.getReport(reportId)
            val description = report?.let { violationDescription(it, templates.value) }
            if (report == null ||
                !report.isReady(settings.value.profile, description) ||
                report.recipient.isBlank()
            ) {
                _notices.emit(UiNotice(NoticeKind.INVALID_REPORT))
                return@launchDataMutation
            }
            _busy.value = true
            try {
                val files = container.photoStore.prepareEmailCopies(report.photos)
                val apps = container.emailLauncher.compatibleApps()
                _mailPreparation.value = MailPreparation.Ready(
                    report = report,
                    files = files,
                    apps = apps,
                    savedComponent = container.emailLauncher.findSavedComponent(settings.value.mailComponent, apps),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _mailPreparation.value = MailPreparation.Idle
                _notices.emit(UiNotice(NoticeKind.MAIL_FAILED))
            } finally {
                _busy.value = false
            }
        }
    }

    fun buildMailIntent(preparation: MailPreparation.Ready, component: ComponentName) =
        container.emailLauncher.buildIntent(preparation.report, preparation.files, component)

    fun rememberMailComponent(component: ComponentName) {
        launchDataMutation { container.settingsRepository.setMailComponent(component.flattenToString()) }
    }

    fun markMailOpened(reportId: String) {
        launchDataMutation {
            markMailOpenedNow(reportId)
            _mailPreparation.value = MailPreparation.Idle
        }
    }

    fun onMailClientReturned(reportId: String) {
        launchDataMutation { handleMailClientReturned(reportId) }
    }

    internal suspend fun handleMailClientReturned(reportId: String) {
        markMailOpenedNow(reportId)
        val report = container.reportRepository.getReport(reportId)
        if (InAppReviewEligibility.afterMailReturn(report) &&
            container.settingsRepository.reserveInAppReviewAttempt()
        ) {
            _effects.emit(UiEffect.RequestInAppReview)
        }
    }

    fun mailLaunchFailed() {
        _mailPreparation.value = MailPreparation.Idle
        _notices.tryEmit(UiNotice(NoticeKind.MAIL_FAILED))
    }

    fun dismissMailPreparation() {
        _mailPreparation.value = MailPreparation.Idle
    }

    fun formatOccurredAt(epochMillis: Long): String =
        ReportLocationInput.format(epochMillis)

    private suspend fun persistCameraPhoto(
        reportId: String,
        file: File,
    ): PendingAddressResolution? {
        val existing = container.reportRepository.getReport(reportId)
        val photo = container.photoStore.cameraPhoto(
            reportId,
            file,
            isPrimary = existing.orEmptyPhotoCount() == 0,
        )
        return if (existing == null) {
            val location = _latestLocation.value
                ?.takeIf { LocationFreshness.isFresh(it) }
                ?: container.locationService.currentLocation()
            val settings = settings.first { it.loaded }
            container.reportRepository.createDraft(
                id = reportId,
                photo = photo,
                settings = settings,
                location = location,
                occurredAtEpochMillis = photo.capturedAtEpochMillis,
                locationNeedsReview = location == null || !LocationFreshness.isFresh(location),
            )
            PendingAddressResolution(
                location = location,
                needsReview = location == null || !LocationFreshness.isFresh(location),
            )
        } else {
            require(existing.photos.size < 3) { "A report can contain at most three photos" }
            container.reportRepository.addPhotos(reportId, listOf(photo))
            null
        }
    }

    private suspend fun completeCameraPhoto(
        reportId: String,
        addressResolution: PendingAddressResolution?,
    ) {
        addressResolution?.let { resolution ->
            updateResolvedAddress(reportId, resolution.location, resolution.needsReview)
        }
        container.reportRepository.clearAutomaticRecognition(reportId)
        recognizeLocally(reportId)
    }

    private suspend fun updateResolvedAddress(
        reportId: String,
        location: LocationSnapshot?,
        metadataNeedsReview: Boolean,
    ) {
        if (location == null) {
            container.reportRepository.mutateReport(reportId) {
                it.copy(locationNeedsReview = true)
            }
            return
        }
        val reportBeforeLookup = container.reportRepository.getReport(reportId) ?: return
        val addressBeforeLookup = reportBeforeLookup.address
        val resolution = resolveAddressOrNull(location)
        container.reportRepository.mutateReport(reportId) { current ->
            if (!AddressResolutionFreshness.canApply(current, location, addressBeforeLookup)) {
                current
            } else {
                current.copy(
                    address = resolution?.suggested?.address.orEmpty(),
                    locationNeedsReview = metadataNeedsReview ||
                        resolution == null ||
                        resolution.needsReview,
                )
            }
        }
    }

    private suspend fun resolveAddressOrNull(
        location: LocationSnapshot,
        forceRefresh: Boolean = false,
    ): AddressResolution? {
        val generation = dataGeneration
        val key = AddressLookupKey(location.latitude, location.longitude, location.accuracyMeters)
        if (!forceRefresh) {
            addressResolutionMutex.withLock { addressResolutions[key] }?.let { return it }
        }
        val resolution = try {
            container.addressResolver.resolve(location)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        if (resolution.suggested != null && acceptingDataMutations && generation == dataGeneration) {
            addressResolutionMutex.withLock {
                if (acceptingDataMutations && generation == dataGeneration) {
                    addressResolutions[key] = resolution
                }
            }
        }
        return resolution
    }

    private suspend fun recognizeLocally(reportId: String) {
        recognitionMutex.withLock {
            val report = container.reportRepository.getReport(reportId) ?: return@withLock
            recognizeLocallyLocked(reportId, report)
        }
    }

    private suspend fun recognizeLocallyLocked(reportId: String, report: Report) {
        val fingerprint = report.photos.localRecognitionFingerprint()
        var completed = true
        try {
            val result = container.textRecognitionService.recognize(report.photos)
            container.reportRepository.applyRecognition(reportId, result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            completed = false
            _notices.emit(UiNotice(NoticeKind.OCR_FAILED))
        }
        try {
            val result = container.plateRecognitionService.recognize(report.photos)
            container.reportRepository.applyRecognition(reportId, result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            completed = false
            _notices.emit(UiNotice(NoticeKind.PLATE_RECOGNITION_FAILED))
        }
        if (completed) {
            container.reportRepository.markLocalRecognitionComplete(reportId, fingerprint)
        }
        refreshLetter(reportId)
    }

    private fun updateReport(
        reportId: String,
        regenerate: Boolean = false,
        transform: (Report) -> Report,
    ) {
        launchDataMutation { updateReportNow(reportId, regenerate, transform = transform) }
    }

    private suspend fun updateReportNow(
        reportId: String,
        regenerate: Boolean,
        templateSnapshot: List<CustomViolationTemplate> = templates.value,
        transform: (Report) -> Report,
    ) {
        val profile = settings.first { it.loaded }.profile
        container.reportRepository.mutateReport(reportId) { current ->
            var updated = transform(current)
            val description = violationDescription(updated, templateSnapshot)
            if (description != null && (regenerate || !updated.letterManuallyEdited)) {
                val letter = container.emailTemplateRenderer.render(updated, profile, description)
                updated = updated.copy(
                    subject = letter.subject,
                    body = letter.body,
                    letterManuallyEdited = false,
                )
            }
            ReportTransitions.refreshReadiness(updated, profile, description)
        }
    }

    private suspend fun refreshLetter(reportId: String) {
        updateReportNow(reportId, regenerate = false) { it }
    }

    private suspend fun markMailOpenedNow(reportId: String) {
        updateReportNow(reportId, regenerate = false) { report ->
            if (report.status == ReportStatus.READY ||
                report.status == ReportStatus.HANDED_OFF_TO_MAIL
            ) {
                ReportTransitions.markHandedOff(report)
            } else {
                report
            }
        }
    }

    private fun violationDescription(
        report: Report,
        templates: List<CustomViolationTemplate>,
    ): String? = when (report.violationType) {
        ViolationType.CUSTOM -> templates.firstOrNull { it.id == report.customTemplateId }?.estonianDescription
        else -> ViolationTemplates.description(report.violationType)
    }

    private fun Report?.orEmptyPhotoCount(): Int = this?.photos?.size ?: 0

    private fun CameraCaptureTarget.isCurrent(): Boolean =
        photoInputEnabled && dataGeneration == this.dataGeneration

    private fun String.normalizedWorkerUrl(): String = trim().trimEnd('/')

    private fun launchDataMutation(block: suspend CoroutineScope.() -> Unit) {
        if (!acceptingDataMutations) return
        dataMutationScope.launch(block = block)
    }

    private fun startDataMutationSession() {
        if (acceptingDataMutations) return
        dataSessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        dataMutationScope = CoroutineScope(viewModelScope.coroutineContext + dataSessionJob)
        acceptingDataMutations = true
    }

    private fun startAddressLookupSession() {
        addressLookupSessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        addressLookupScope = CoroutineScope(viewModelScope.coroutineContext + addressLookupSessionJob)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PoleParklaViewModel(container) as T
    }
}

private data class AddressLookupKey(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)
