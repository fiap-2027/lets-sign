package com.letsign.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY COALESCE(atualizadoEm, criadoEm) DESC")
    fun observeAll(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND id = :id")
    fun observe(userId: String, id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE userId = :userId AND id = :id")
    suspend fun get(userId: String, id: String): DocumentEntity?

    @Query("SELECT * FROM participants WHERE userId = :userId AND documentId = :documentId ORDER BY ordem")
    fun observeParticipants(userId: String, documentId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM signature_fields WHERE userId = :userId AND documentId = :documentId ORDER BY page, y")
    fun observeFields(userId: String, documentId: String): Flow<List<FieldEntity>>

    @Query("SELECT * FROM audit_events WHERE userId = :userId AND documentId = :documentId ORDER BY timestamp")
    fun observeEvents(userId: String, documentId: String): Flow<List<AuditEventEntity>>

    // ---- Modo local ----

    /** Documentos que [viewerId] pode ver: os que criou e os que foram enviados para ele (rascunho nunca). */
    @Query(
        """SELECT * FROM documents WHERE userId = :userId AND (ownerId = :viewerId OR (status != 'DRAFT' AND id IN
           (SELECT documentId FROM participants WHERE userId = :userId AND signerId = :viewerId)))
           ORDER BY COALESCE(atualizadoEm, criadoEm) DESC"""
    )
    fun observeVisible(userId: String, viewerId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM participants WHERE userId = :userId AND signerId = :signerId")
    fun observeParticipations(userId: String, signerId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE userId = :userId AND documentId = :documentId ORDER BY ordem")
    suspend fun getParticipants(userId: String, documentId: String): List<ParticipantEntity>

    @Query("SELECT * FROM signature_fields WHERE userId = :userId AND documentId = :documentId ORDER BY page, y")
    suspend fun getFields(userId: String, documentId: String): List<FieldEntity>

    @Query("SELECT COUNT(*) FROM participants WHERE userId = :userId AND signerId = :signerId")
    suspend fun countParticipations(userId: String, signerId: String): Int

    @Upsert
    suspend fun upsertDocuments(documents: List<DocumentEntity>)

    @Upsert
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    @Upsert
    suspend fun upsertFields(fields: List<FieldEntity>)

    @Upsert
    suspend fun upsertEvents(events: List<AuditEventEntity>)

    @Query("DELETE FROM participants WHERE userId = :userId AND documentId = :documentId")
    suspend fun deleteParticipants(userId: String, documentId: String)

    @Query("DELETE FROM signature_fields WHERE userId = :userId AND documentId = :documentId")
    suspend fun deleteFields(userId: String, documentId: String)

    @Query("DELETE FROM audit_events WHERE userId = :userId AND documentId = :documentId")
    suspend fun deleteEvents(userId: String, documentId: String)

    @Query("DELETE FROM documents WHERE userId = :userId AND id NOT IN (:keepIds)")
    suspend fun deleteDocumentsNotIn(userId: String, keepIds: List<String>)

    @Query("DELETE FROM documents WHERE userId = :userId")
    suspend fun deleteAllDocuments(userId: String)

    @Query("DELETE FROM participants WHERE userId = :userId AND documentId NOT IN (SELECT id FROM documents WHERE userId = :userId)")
    suspend fun deleteOrphanParticipants(userId: String)

    @Query("DELETE FROM signature_fields WHERE userId = :userId AND documentId NOT IN (SELECT id FROM documents WHERE userId = :userId)")
    suspend fun deleteOrphanFields(userId: String)

    @Query("DELETE FROM audit_events WHERE userId = :userId AND documentId NOT IN (SELECT id FROM documents WHERE userId = :userId)")
    suspend fun deleteOrphanEvents(userId: String)

    /**
     * A lista da API é a fonte da verdade sobre *quais* documentos existem: remove os que sumiram
     * (e seus filhos) e atualiza o resumo dos demais, sem apagar participantes/campos já baixados.
     */
    @Transaction
    suspend fun replaceList(userId: String, documents: List<DocumentEntity>) {
        if (documents.isEmpty()) deleteAllDocuments(userId) else deleteDocumentsNotIn(userId, documents.map { it.id })
        deleteOrphanParticipants(userId)
        deleteOrphanFields(userId)
        deleteOrphanEvents(userId)
        upsertDocuments(documents)
    }

    /** O detalhe da API é completo: substitui participantes, campos e eventos do documento. */
    @Transaction
    suspend fun replaceDetail(
        userId: String,
        document: DocumentEntity,
        participants: List<ParticipantEntity>,
        fields: List<FieldEntity>,
        events: List<AuditEventEntity>
    ) {
        upsertDocuments(listOf(document))
        deleteParticipants(userId, document.id)
        deleteFields(userId, document.id)
        deleteEvents(userId, document.id)
        upsertParticipants(participants)
        upsertFields(fields)
        upsertEvents(events)
    }
}

@Dao
interface SignerDao {

    @Query("SELECT * FROM signers WHERE userId = :userId ORDER BY nome COLLATE NOCASE")
    fun observeAll(userId: String): Flow<List<SignerEntity>>

    @Upsert
    suspend fun upsert(signers: List<SignerEntity>)

    @Query("DELETE FROM signers WHERE userId = :userId AND id = :id")
    suspend fun delete(userId: String, id: String)

    @Query("DELETE FROM signers WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Transaction
    suspend fun replaceAll(userId: String, signers: List<SignerEntity>) {
        deleteAll(userId)
        upsert(signers)
    }
}

@Dao
interface PdfFileDao {

    @Query("SELECT * FROM pdf_files WHERE userId = :userId AND documentId = :documentId ORDER BY salvoEm DESC")
    fun observe(userId: String, documentId: String): Flow<List<PdfFileEntity>>

    @Query("SELECT * FROM pdf_files WHERE userId = :userId AND documentId = :documentId AND tipo = :tipo AND versao = :versao")
    suspend fun get(userId: String, documentId: String, tipo: String, versao: Int): PdfFileEntity?

    @Upsert
    suspend fun upsert(file: PdfFileEntity)

    @Query("DELETE FROM pdf_files WHERE userId = :userId AND documentId = :documentId AND tipo = :tipo AND versao = :versao")
    suspend fun delete(userId: String, documentId: String, tipo: String, versao: Int)
}

/** Modo local: contas (Admin e Signatários). */
@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE adminId = :adminId ORDER BY nome COLLATE NOCASE")
    fun observeSignersOf(adminId: String): Flow<List<UserEntity>>

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: String)
}
