package com.letsign.mobile.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        ParticipantEntity::class,
        FieldEntity::class,
        AuditEventEntity::class,
        SignerEntity::class,
        PdfFileEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun signerDao(): SignerDao
    abstract fun pdfFileDao(): PdfFileDao

    companion object {
        const val NAME = "letssign.db"
    }
}
