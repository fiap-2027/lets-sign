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
        UserEntity::class,
        TemplateEntity::class,
        GroupEntity::class
    ],
    version = 4,
    exportSchema = true,
    // v2: tabela users e colunas de assinatura em participants (modo local).
    // v3: tipos de campo, CPF/nascimento, rubrica, pasta/categorias/observadores, modelos e grupos.
    // v4: layout dos modelos inteligentes (templates.layoutJson).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun signerDao(): SignerDao
    abstract fun pdfFileDao(): PdfFileDao
    abstract fun userDao(): UserDao
    abstract fun orgDao(): OrgDao

    companion object {
        const val NAME = "letssign.db"
    }
}
