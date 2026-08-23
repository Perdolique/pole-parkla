package com.perdolique.poleparkla.model

const val DEFAULT_RECIPIENT = "korrapidaja@tallinnlv.ee"

enum class ViolationType {
    CYCLE_PATH,
    PEDESTRIAN_PATH,
    CUSTOM,
}

enum class ReportStatus {
    DRAFT,
    READY,
    HANDED_OFF_TO_MAIL,
}

enum class PhotoSource {
    CAMERA,
    GALLERY,
}

enum class RecognitionSource {
    ML_KIT_OCR,
    LOCAL_PLATE_MODEL,
    WORKERS_AI,
    OPENAI,
}

enum class CloudProvider {
    WORKERS_AI,
    OPENAI,
}

data class ReporterProfile(
    val name: String = "",
    val phone: String = "",
)

data class AppSettings(
    val loaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val languageTag: String = "",
    val profile: ReporterProfile = ReporterProfile(),
    val defaultRecipient: String = DEFAULT_RECIPIENT,
    val workerUrl: String = "",
    val cloudProvider: CloudProvider = CloudProvider.WORKERS_AI,
    val workersAiConsent: Boolean = false,
    val openAiConsent: Boolean = false,
    val mailComponent: String = "",
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMillis: Long,
)

enum class AddressCandidateType {
    STREET,
    BUILDING,
}

data class AddressCandidate(
    val address: String,
    val distanceMeters: Int,
    val type: AddressCandidateType,
)

data class AddressResolution(
    val location: LocationSnapshot,
    val candidates: List<AddressCandidate>,
    val suggested: AddressCandidate?,
    val needsReview: Boolean,
)

data class AddressMapSelection(
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val movedPoint: Boolean,
    val candidates: List<AddressCandidate> = emptyList(),
    val addressLookupFailed: Boolean = false,
)

data class ReportPhoto(
    val id: String,
    val reportId: String,
    val filePath: String,
    val source: PhotoSource,
    val capturedAtEpochMillis: Long,
    val isPrimary: Boolean,
)

data class NormalizedPhotoRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class RecognitionPlateObservation(
    val photoId: String,
    val value: String,
    val bounds: NormalizedPhotoRect? = null,
    val detectionConfidence: Float? = null,
    val characterConfidence: Float? = null,
    val relativeArea: Float? = null,
)

data class PlateObservation(
    val id: String,
    val reportId: String,
    val photoId: String,
    val source: RecognitionSource,
    val value: String,
    val bounds: NormalizedPhotoRect? = null,
    val detectionConfidence: Float? = null,
    val characterConfidence: Float? = null,
    val relativeArea: Float? = null,
)

data class PlateCandidate(
    val value: String,
    val sources: Set<RecognitionSource>,
    val supportingPhotoCount: Int,
    val observations: List<PlateObservation>,
)

data class RecognitionResult(
    val source: RecognitionSource,
    val plateCandidates: List<String> = emptyList(),
    val plateObservations: List<RecognitionPlateObservation> = emptyList(),
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val suggestedViolationType: ViolationType? = null,
)

data class Report(
    val id: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val occurredAtEpochMillis: Long,
    val status: ReportStatus,
    val plate: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val violationType: ViolationType?,
    val customTemplateId: String?,
    val recipient: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val locationNeedsReview: Boolean,
    val subject: String,
    val body: String,
    val plateManuallyEdited: Boolean,
    val vehicleManuallyEdited: Boolean,
    val vehicleConfirmed: Boolean,
    val locationConfirmed: Boolean,
    val plateObservations: List<PlateObservation>,
    val suggestedViolationType: ViolationType?,
    val mailOpenedAtEpochMillis: Long?,
    val photos: List<ReportPhoto>,
    val localRecognitionFingerprint: String = "",
) {
    val primaryPhoto: ReportPhoto?
        get() = photos.firstOrNull(ReportPhoto::isPrimary) ?: photos.firstOrNull()

    fun hasValidLocation(): Boolean =
        occurredAtEpochMillis > 0L &&
            (
                address.isNotBlank() ||
                    (
                        latitude?.isFinite() == true && latitude in -90.0..90.0 &&
                            longitude?.isFinite() == true && longitude in -180.0..180.0
                        )
                )

    fun isReady(profile: ReporterProfile, violationDescription: String?): Boolean =
        photos.isNotEmpty() &&
            plate.isNotBlank() &&
            vehicleConfirmed &&
            !violationDescription.isNullOrBlank() &&
            recipient.isNotBlank() &&
            subject.isNotBlank() &&
            body.isNotBlank() &&
            profile.name.isNotBlank() &&
            profile.phone.isNotBlank() &&
            locationConfirmed &&
            hasValidLocation()
}

data class CustomViolationTemplate(
    val id: String,
    val displayName: String,
    val estonianDescription: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class LetterDraft(
    val subject: String,
    val body: String,
)
