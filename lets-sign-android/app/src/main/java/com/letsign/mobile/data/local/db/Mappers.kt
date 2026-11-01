package com.letsign.mobile.data.local.db

import com.letsign.mobile.domain.model.AuditEvent
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.DocumentTemplate
import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.domain.model.SignerGroup
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.Participant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.Role
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SignatureField
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User

// Conversão API/domínio <-> Room. Enums viram texto; valor desconhecido cai num padrão seguro.

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun List<User>.toObserversJson(): String = json.encodeToString(map { User(id = it.id, nome = it.nome, email = it.email) })

fun observersFromJson(text: String): List<User> = runCatching { json.decodeFromString<List<User>>(text) }.getOrDefault(emptyList())

fun categoriesToText(list: List<String>): String = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

fun Document.toEntity(userId: String, now: Long = System.currentTimeMillis()) = DocumentEntity(
    userId = userId,
    id = id,
    ownerId = ownerId,
    ownerNome = owner?.nome,
    titulo = titulo,
    status = status.name,
    versao = versao,
    hashSha256 = hashSha256,
    tamanhoBytes = tamanhoBytes,
    nomeArquivo = nomeArquivo,
    totalPaginas = totalPaginas,
    mensagem = mensagem,
    criadoEm = criadoEm,
    enviadoEm = enviadoEm,
    concluidoEm = concluidoEm,
    atualizadoEm = atualizadoEm,
    hashFinal = hashFinal,
    totalSignatarios = totalSignatarios ?: signers.takeIf { it.isNotEmpty() }?.size,
    assinados = assinados ?: signers.takeIf { it.isNotEmpty() }?.count { it.isSigned },
    papel = papel,
    minhaSituacao = minhaSituacao?.name,
    meuPapel = meuPapel?.name,
    assinadoEm = assinadoEm,
    possuiDocumentoFinal = possuiDocumentoFinal || hashFinal != null,
    sincronizadoEm = now,
    pasta = pasta,
    categorias = categoriesToText(categorias),
    observadores = observadores.toObserversJson()
)

/** Ao gravar o resumo da lista, preserva o que só o detalhe traz (ex.: nome do dono). */
fun DocumentEntity.mergeLocalOnly(existing: DocumentEntity?) =
    if (existing == null) this else copy(ownerNome = ownerNome ?: existing.ownerNome)

fun Participant.toEntity(userId: String, documentId: String) = ParticipantEntity(
    userId = userId,
    id = id,
    documentId = documentId,
    signerId = signerId,
    signerNome = signer?.nome,
    signerEmail = signer?.email,
    status = status.name,
    roleLabel = roleLabel.name,
    required = required,
    ordem = ordem,
    invitedAt = invitedAt,
    viewedAt = viewedAt,
    signedAt = signedAt,
    rejectedAt = rejectedAt,
    motivoRecusa = motivoRecusa
)

fun SignatureField.toEntity(userId: String, documentId: String) = FieldEntity(
    userId = userId,
    id = id,
    documentId = documentId,
    signerId = signerId,
    page = page,
    x = x,
    y = y,
    width = width,
    height = height,
    required = required,
    roleLabel = roleLabel.name,
    tipo = tipo.name
)

fun AuditEvent.toEntity(userId: String, documentId: String) = AuditEventEntity(
    userId = userId,
    id = id,
    documentId = documentId,
    tipo = tipo.name,
    actorId = actorId,
    actorRole = actorRole?.name,
    descricao = descricao,
    versao = versao,
    timestamp = timestamp,
    hashDocumentoNoMomento = hashDocumentoNoMomento
)

fun User.toSignerEntity(userId: String) = SignerEntity(
    userId = userId,
    id = id,
    nome = nome,
    email = email,
    telefone = telefone,
    criadoEm = criadoEm
)

fun DocumentEntity.toDomain(
    participants: List<ParticipantEntity> = emptyList(),
    fields: List<FieldEntity> = emptyList(),
    events: List<AuditEventEntity> = emptyList()
) = Document(
    id = id,
    ownerId = ownerId,
    titulo = titulo,
    status = enumOr(status, DocumentStatus.DRAFT),
    versao = versao,
    hashSha256 = hashSha256,
    tamanhoBytes = tamanhoBytes,
    nomeArquivo = nomeArquivo,
    totalPaginas = totalPaginas,
    mensagem = mensagem,
    criadoEm = criadoEm,
    enviadoEm = enviadoEm,
    concluidoEm = concluidoEm,
    atualizadoEm = atualizadoEm,
    hashFinal = hashFinal,
    owner = ownerNome?.let { User(id = ownerId, nome = it, email = "") },
    signers = participants.map { it.toDomain() },
    fields = fields.map { it.toDomain() },
    events = events.map { it.toDomain() },
    totalSignatarios = totalSignatarios,
    assinados = assinados,
    papel = papel,
    minhaSituacao = minhaSituacao?.let { enumOr(it, SignerStatus.PENDING) },
    meuPapel = meuPapel?.let { enumOr(it, RoleLabel.CONTRATADO) },
    assinadoEm = assinadoEm,
    possuiDocumentoFinal = possuiDocumentoFinal,
    pasta = pasta,
    categorias = categorias.split('\n').filter { it.isNotBlank() },
    observadores = observersFromJson(observadores)
)

fun ParticipantEntity.toDomain() = Participant(
    id = id,
    documentId = documentId,
    signerId = signerId,
    status = enumOr(status, SignerStatus.PENDING),
    roleLabel = enumOr(roleLabel, RoleLabel.CONTRATADO),
    required = required,
    ordem = ordem,
    invitedAt = invitedAt,
    viewedAt = viewedAt,
    signedAt = signedAt,
    rejectedAt = rejectedAt,
    motivoRecusa = motivoRecusa,
    signer = User(id = signerId, nome = signerNome ?: "", email = signerEmail ?: "", cpf = signerCpf, dataNascimento = signerNascimento)
)

fun FieldEntity.toDomain() = SignatureField(
    id = id,
    documentId = documentId,
    signerId = signerId,
    page = page,
    x = x,
    y = y,
    width = width,
    height = height,
    required = required,
    roleLabel = enumOr(roleLabel, RoleLabel.CONTRATADO),
    tipo = enumOr(tipo, FieldType.ASSINATURA)
)

fun AuditEventEntity.toDomain() = AuditEvent(
    id = id,
    documentId = documentId,
    tipo = enumOr(tipo, AuditType.OTHER),
    actorId = actorId,
    actorRole = actorRole?.let { enumOr(it, Role.SIGNER) },
    descricao = descricao,
    versao = versao,
    timestamp = timestamp,
    hashDocumentoNoMomento = hashDocumentoNoMomento
)

fun UserEntity.toDomain() =
    User(
        id = id, nome = nome, email = email, telefone = telefone, role = enumOr(role, Role.SIGNER), adminId = adminId, criadoEm = criadoEm,
        cpf = cpf, dataNascimento = dataNascimento
    )

fun TemplateEntity.toDomain() = DocumentTemplate(id = id, nome = nome, sourceDocumentId = sourceDocumentId, criadoEm = criadoEm)

fun GroupEntity.toDomain() = SignerGroup(id = id, nome = nome, memberIds = memberIds.split(',').filter { it.isNotBlank() })

fun SignerEntity.toDomain() = User(id = id, nome = nome, email = email, telefone = telefone, role = Role.SIGNER, criadoEm = criadoEm)

fun PdfFileEntity.toDomain() = LocalPdf(
    documentId = documentId,
    tipo = if (tipo == PdfKind.FINAL.apiValue) PdfKind.FINAL else PdfKind.ORIGINAL,
    versao = versao,
    nomeArquivo = nomeArquivo,
    caminho = caminho,
    tamanhoBytes = tamanhoBytes,
    salvoEm = salvoEm
)
