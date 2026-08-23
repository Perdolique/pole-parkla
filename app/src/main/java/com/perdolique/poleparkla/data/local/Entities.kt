package com.perdolique.poleparkla.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val occurredAtEpochMillis: Long,
    val status: String,
    val plate: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val violationType: String?,
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
    val suggestedViolationType: String?,
    val mailOpenedAtEpochMillis: Long?,
    val localRecognitionFingerprint: String,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reportId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val filePath: String,
    val source: String,
    val capturedAtEpochMillis: Long,
    val isPrimary: Boolean,
)

@Entity(
    tableName = "plate_observations",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reportId"), Index("photoId")],
)
data class PlateObservationEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val photoId: String,
    val source: String,
    val value: String,
    val boundsLeft: Float?,
    val boundsTop: Float?,
    val boundsRight: Float?,
    val boundsBottom: Float?,
    val detectionConfidence: Float?,
    val characterConfidence: Float?,
    val relativeArea: Float?,
)

@Entity(tableName = "custom_violation_templates")
data class CustomViolationTemplateEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val estonianDescription: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ReportWithPhotos(
    @Embedded val report: ReportEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reportId",
    )
    val photos: List<PhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "reportId",
    )
    val plateObservations: List<PlateObservationEntity>,
)
