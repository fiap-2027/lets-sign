package com.letsign.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index

// Toda tabela tem `userId` (dono do cache) na chave primária: um usuário nunca enxerga
// registros de outro no mesmo aparelho, e o logout apaga tudo (LocalDataCleaner).

@Entity(tableName = "documents", primaryKeys = ["userId", "id"])
data class DocumentEntity(
    val userId: String,
    val id: String,
    val ownerId: String,
    val ownerNome: String?,
    val titulo: String,
    val status: String,
    val versao: Int,
    val hashSha256: String,
    val tamanhoBytes: Long,
    val nomeArquivo: String,
    val totalPaginas: Int,
    val mensagem: String?,
    val criadoEm: String?,
    val enviadoEm: String?,
    val concluidoEm: String?,
    val atualizadoEm: String?,
    val hashFinal: String?,
    val totalSignatarios: Int?,
    val assinados: Int?,
    val papel: String?,
    val minhaSituacao: String?,
    val meuPapel: String?,
    val assinadoEm: String?,
    val possuiDocumentoFinal: Boolean,
    /** Momento (epoch ms) da última sincronização com a API. */
    val sincronizadoEm: Long
)

@Entity(
    tableName = "participants",
    primaryKeys = ["userId", "id"],
    indices = [Index(value = ["userId", "documentId"])]
)
data class ParticipantEntity(
    val userId: String,
    val id: String,
    val documentId: String,
    val signerId: String,
    val signerNome: String?,
    val signerEmail: String?,
    val status: String,
    val roleLabel: String,
    val required: Boolean,
    val ordem: Int,
    val invitedAt: String?,
    val viewedAt: String?,
    val signedAt: String?,
    val rejectedAt: String?,
    val motivoRecusa: String?
)

@Entity(
    tableName = "signature_fields",
    primaryKeys = ["userId", "id"],
    indices = [Index(value = ["userId", "documentId"])]
)
data class FieldEntity(
    val userId: String,
    val id: String,
    val documentId: String,
    val signerId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val required: Boolean,
    val roleLabel: String
)

@Entity(
    tableName = "audit_events",
    primaryKeys = ["userId", "id"],
    indices = [Index(value = ["userId", "documentId"])]
)
data class AuditEventEntity(
    val userId: String,
    val id: String,
    val documentId: String,
    val tipo: String,
    val actorId: String?,
    val actorRole: String?,
    val descricao: String,
    val versao: Int,
    val timestamp: String,
    val hashDocumentoNoMomento: String?
)

/** Signatários cadastrados pelo Admin logado. */
@Entity(tableName = "signers", primaryKeys = ["userId", "id"])
data class SignerEntity(
    val userId: String,
    val id: String,
    val nome: String,
    val email: String,
    val telefone: String?,
    val criadoEm: String?
)

/**
 * Registro de cada PDF salvo no sistema de arquivos do aparelho (requisito: nome, caminho, data e status).
 * O arquivo em si fica em filesDir/documentos (ver PdfStorage); aqui fica só o registro.
 */
@Entity(tableName = "pdf_files", primaryKeys = ["userId", "documentId", "tipo", "versao"])
data class PdfFileEntity(
    val userId: String,
    val documentId: String,
    /** "original" ou "final". */
    val tipo: String,
    val versao: Int,
    val nomeArquivo: String,
    val caminho: String,
    val tamanhoBytes: Long,
    val salvoEm: Long,
    /** Status do documento no momento em que o arquivo foi salvo. */
    val statusDocumento: String
)
