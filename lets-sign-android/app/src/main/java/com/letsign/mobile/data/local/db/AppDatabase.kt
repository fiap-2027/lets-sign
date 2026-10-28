package com.letsign.mobile.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        ParticipantEntity::class,
        FieldEntity::class,
        AuditEventEntity::class,
        SignerEntity::class,
        PdfFileEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = true,
    // v2: tabela users e colunas de assinatura em participants (modo local).
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun signerDao(): SignerDao
    abstract fun pdfFileDao(): PdfFileDao
    abstract fun userDao(): UserDao

    companion object {
        const val NAME = "letssign.db"
    }
}
