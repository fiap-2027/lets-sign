package com.letsign.mobile.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Toda tabela tem `userId` na chave primária.
// Modo API: é o dono do cache; um usuário nunca enxerga registros de outro e o logout apaga tudo (LocalDataCleaner).
// Modo local: vale sempre LOCAL e o acesso é filtrado por usuário em LocalRepositories.kt.

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
    val sincronizadoEm: Long,
    @ColumnInfo(defaultValue = "Documentos") val pasta: String = "Documentos",
    /** Categorias separadas por quebra de linha. */
    @ColumnInfo(defaultValue = "") val categorias: String = "",
    /** Observadores em JSON: [{"id","nome","email"}]. */
    @ColumnInfo(defaultValue = "[]") val observadores: String = "[]"
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
    val motivoRecusa: String?,
    /** Modo local: imagem da assinatura (PNG em base64), usada para gerar o PDF final. */
    val assinaturaPng: String? = null,
    /** Modo local: dados do gesto da assinatura (JSON), guardados como evidência. */
    val gestureMetadata: String? = null,
    /** Modo local: CPF e nascimento do contato no momento do envio (vão para os campos do PDF). */
    val signerCpf: String? = null,
    val signerNascimento: String? = null,
    /** Modo local: imagem da rubrica (PNG em base64). */
    val rubricaPng: String? = null
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
    val roleLabel: String,
    @ColumnInfo(defaultValue = "ASSINATURA") val tipo: String = "ASSINATURA"
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

/** Modo local: contas de Admin e Signatário. Senha guardada só como hash (PasswordHasher). */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val email: String,
    val telefone: String?,
    val role: String,
    /** Admin que cadastrou este signatário (null para Admin). */
    val adminId: String?,
    val senhaHash: String,
    val criadoEm: String,
    val cpf: String? = null,
    val dataNascimento: String? = null
)

/** Modo local: modelos de documento do Admin. */
@Entity(tableName = "templates", indices = [Index(value = ["adminId"])])
data class TemplateEntity(
    @PrimaryKey val id: String,
    val adminId: String,
    val nome: String,
    val sourceDocumentId: String,
    val criadoEm: String,
    /** Layout com âncoras de texto (TemplateLayout em JSON), para posicionar campos em PDFs novos. */
    val layoutJson: String? = null
)

/** Modo local: grupos de contatos do Admin. */
@Entity(tableName = "signer_groups", indices = [Index(value = ["adminId"])])
data class GroupEntity(
    @PrimaryKey val id: String,
    val adminId: String,
    val nome: String,
    /** Ids dos contatos separados por vírgula. */
    val memberIds: String
)
