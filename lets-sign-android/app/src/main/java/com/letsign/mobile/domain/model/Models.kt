package com.letsign.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Datas chegam da API como texto ISO-8601 (ex.: "2026-10-25T14:03:00.000Z").
// Os mesmos modelos servem de DTO da API e de modelo das telas; o Room tem entidades próprias (data/local).

@Serializable
data class User(
    val id: String,
    val nome: String,
    val email: String,
    val telefone: String? = null,
    val role: Role = Role.SIGNER,
    val adminId: String? = null,
    val criadoEm: String? = null
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
    val isSigner: Boolean get() = role == Role.SIGNER
    val initials: String
        get() = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

@Serializable
enum class Role {
    ADMIN,
    SIGNER
}

@Serializable
data class Document(
    val id: String,
    val ownerId: String,
    val titulo: String,
    val status: DocumentStatus = DocumentStatus.DRAFT,
    val versao: Int = 1,
    val hashSha256: String = "",
    val tamanhoBytes: Long = 0,
    val nomeArquivo: String = "",
    val totalPaginas: Int = 0,
    val mensagem: String? = null,
    val criadoEm: String? = null,
    val enviadoEm: String? = null,
    val concluidoEm: String? = null,
    val atualizadoEm: String? = null,
    val hashFinal: String? = null,
    val owner: User? = null,
    val signers: List<Participant> = emptyList(),
    val fields: List<SignatureField> = emptyList(),
    val events: List<AuditEvent> = emptyList(),
    val totalSignatarios: Int? = null,
    val assinados: Int? = null,
    /** "OWNER" (criador) ou "SIGNER" (convidado), do ponto de vista do usuário logado. */
    val papel: String? = null,
    /** Situação do usuário logado como signatário (só vem quando papel = SIGNER). */
    val minhaSituacao: SignerStatus? = null,
    val meuPapel: RoleLabel? = null,
    val assinadoEm: String? = null,
    val possuiDocumentoFinal: Boolean = false
) {
    val isDraft: Boolean get() = status == DocumentStatus.DRAFT
    val isPending: Boolean get() = status == DocumentStatus.PENDING || status == DocumentStatus.PARTIALLY_SIGNED
    val isCompleted: Boolean get() = status == DocumentStatus.COMPLETED
    val isCancelled: Boolean get() = status == DocumentStatus.CANCELLED
    val isOwnerView: Boolean get() = papel != "SIGNER"
    val canEdit: Boolean get() = isDraft && isOwnerView

    /** Regra: documento enviado não muda; alteração = nova versão (só o dono, e não depois de concluído). */
    val canCreateNewVersion: Boolean get() = isOwnerView && !isDraft && !isCompleted

    /** O usuário logado (signatário) ainda pode assinar ou recusar. */
    val canSign: Boolean
        get() = isPending && papel == "SIGNER" &&
            (minhaSituacao == SignerStatus.PENDING || minhaSituacao == SignerStatus.VIEWED)

    val requiredSigners: Int get() = signers.count { it.required }
    val requiredSigned: Int get() = signers.count { it.required && it.isSigned }
}

@Serializable
enum class DocumentStatus {
    DRAFT,
    PENDING,
    PARTIALLY_SIGNED,
    COMPLETED,
    CANCELLED
}

@Serializable
data class Participant(
    val id: String,
    val documentId: String = "",
    val signerId: String,
    val status: SignerStatus = SignerStatus.PENDING,
    val roleLabel: RoleLabel = RoleLabel.CONTRATADO,
    val required: Boolean = true,
    val ordem: Int = 0,
    val invitedAt: String? = null,
    val viewedAt: String? = null,
    val signedAt: String? = null,
    val rejectedAt: String? = null,
    val motivoRecusa: String? = null,
    val gestureMetadata: JsonElement? = null,
    val signer: User? = null
) {
    val isPending: Boolean get() = status == SignerStatus.PENDING
    val isViewed: Boolean get() = status == SignerStatus.VIEWED
    val isSigned: Boolean get() = status == SignerStatus.SIGNED
    val isRejected: Boolean get() = status == SignerStatus.REJECTED
}

@Serializable
enum class SignerStatus {
    PENDING,
    VIEWED,
    SIGNED,
    REJECTED
}

@Serializable
enum class RoleLabel(val label: String) {
    CONTRATANTE("Contratante"),
    CONTRATADO("Contratado"),
    TESTEMUNHA("Testemunha")
}

/** Página começa em 1; x, y, width e height são frações (0..1) do tamanho da página. */
@Serializable
data class SignatureField(
    val id: String,
    val documentId: String = "",
    val signerId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val required: Boolean = true,
    val roleLabel: RoleLabel = RoleLabel.CONTRATADO
)

@Serializable
data class AuditEvent(
    val id: String,
    val documentId: String = "",
    val tipo: AuditType = AuditType.OTHER,
    val actorId: String? = null,
    val actorRole: Role? = null,
    val descricao: String,
    val versao: Int = 1,
    val timestamp: String,
    val ip: String? = null,
    val hashDocumentoNoMomento: String? = null
)

@Serializable
enum class AuditType {
    CREATED,
    FIELDS_UPDATED,
    SENT,
    VIEWED,
    SIGNED,
    REJECTED,
    VERSION_CREATED,
    COMPLETED,
    /** Tipo novo no backend que este app ainda não conhece. */
    OTHER
}

@Serializable
data class AuthTokens(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 7 * 24 * 3600,
    val usuario: User
)

/** PDF guardado no aparelho e registrado no banco local (nome, caminho, data, status). */
data class LocalPdf(
    val documentId: String,
    val tipo: PdfKind,
    val versao: Int,
    val nomeArquivo: String,
    val caminho: String,
    val tamanhoBytes: Long,
    val salvoEm: Long
)

enum class PdfKind(val apiValue: String) {
    ORIGINAL("original"),
    FINAL("final")
}

/** Configuração montada no assistente e enviada de uma vez em POST /documents/{id}/send. */
data class SendDraft(
    val participants: List<DraftParticipant>,
    val fields: List<DraftField>,
    val mensagem: String?
)

data class DraftParticipant(
    val signer: User,
    val roleLabel: RoleLabel = RoleLabel.CONTRATADO,
    val required: Boolean = true
)

/** Campo em edição no assistente. [localId] só existe no app (o backend gera o id definitivo). */
data class DraftField(
    val localId: String,
    val signerId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class SendResult(
    val jaEnviado: Boolean,
    val mensagem: String,
    val document: Document
)

data class ActionResult(
    val mensagem: String,
    val document: Document
)
