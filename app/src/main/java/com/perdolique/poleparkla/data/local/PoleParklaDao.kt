package com.perdolique.poleparkla.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PoleParklaDao {
    @Transaction
    @Query("SELECT * FROM reports ORDER BY updatedAtEpochMillis DESC")
    fun observeReports(): Flow<List<ReportWithPhotos>>

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    fun observeReport(id: String): Flow<ReportWithPhotos?>

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReport(id: String): ReportWithPhotos?

    @Upsert
    suspend fun upsertReport(report: ReportEntity)

    @Upsert
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Upsert
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Upsert
    suspend fun upsertTemplate(template: CustomViolationTemplateEntity)

    @Query("SELECT * FROM custom_violation_templates ORDER BY displayName COLLATE NOCASE")
    fun observeTemplates(): Flow<List<CustomViolationTemplateEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhoto(id: String): PhotoEntity?

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("DELETE FROM custom_violation_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("DELETE FROM reports")
    suspend fun deleteAllReports()

    @Query("DELETE FROM custom_violation_templates")
    suspend fun deleteAllTemplates()

    @Transaction
    suspend fun insertDraft(report: ReportEntity, photos: List<PhotoEntity>) {
        upsertReport(report)
        upsertPhotos(photos)
    }
}
