package com.perdolique.poleparkla.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReportEntity::class,
        PhotoEntity::class,
        CustomViolationTemplateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PoleParklaDatabase : RoomDatabase() {
    abstract fun dao(): PoleParklaDao
}
