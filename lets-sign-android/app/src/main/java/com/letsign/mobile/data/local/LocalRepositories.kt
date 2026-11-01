package com.letsign.mobile.data.local

import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.local.db.AuditEventEntity
import com.letsign.mobile.data.local.db.DocumentEntity
import com.letsign.mobile.data.local.db.FieldEntity
import com.letsign.mobile.data.local.db.GroupEntity
import com.letsign.mobile.data.local.db.TemplateEntity
import com.letsign.mobile.data.local.db.categoriesToText
import com.letsign.mobile.data.local.db.observersFromJson
import com.letsign.mobile.data.local.db.toObserversJson
import com.letsign.mobile.data.local.db.ParticipantEntity
import com.letsign.mobile.data.local.db.PdfFileEntity
import com.letsign.mobile.data.local.db.UserDao
import com.letsign.mobile.data.local.db.UserEntity
import com.letsign.mobile.data.local.db.toDomain
import com.letsign.mobile.data.pdf.PdfDocumentRenderer
import com.letsign.mobile.data.pdf.PdfInvalidException
import com.letsign.mobile.data.pdf.PdfStamper
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.data.pdf.Stamp
import com.letsign.mobile.data.repository.NotLoggedInException
import com.letsign.mobile.data.repository.perUser
import com.letsign.mobile.domain.model.ActionResult
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.DocumentTemplate
import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.domain.model.SignerGroup
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.TemplateMatch
import com.letsign.mobile.domain.rules.LayoutField
import com.letsign.mobile.domain.rules.LayoutSlot
import com.letsign.mobile.domain.rules.TemplateLayout
import com.letsign.mobile.domain.rules.TemplateMatcher
import com.letsign.mobile.presentation.util.formatDateTime
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.Role
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.SendResult
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import com.letsign.mobile.domain.rules.DocumentRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.cancellation.CancellationException

// Modo local (padrão): o Room é a fonte da verdade e o próprio aparelho aplica as regras de negócio
// que o backend aplicaria. Admin e signatários usam o mesmo aparelho: "enviar" deixa o documento
// visível para os convidados quando eles entram no app. As interfaces são as mesmas do modo API
// (RepositoryImpl.kt), então os ViewModels e as telas não mudam.

/** Valor da coluna userId no modo local: todo registro pertence ao aparelho; o acesso é filtrado por usuário. */
const val LOCAL = "local"

/** Erro de regra de negócio: a mensagem vai direto para a tela. */
class RuleException(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw RuleException(message)

/** Mesmo papel do apiCall: roda no IO e converte em Result, com mensagem amigável para erro inesperado. */
private suspend fun <T> local(defaultError: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuleException) {
        Result.failure(e)
    } catch (e: PdfInvalidException) {
        Result.failure(e)
    } catch (e: NotLoggedInException) {
        Result.failure(e)
    } catch (e: Exception) {
        Log.e("LetsSign", defaultError, e)
        Result.failure(Exception(defaultError, e))
    }
}

/** Mesmo formato das datas da API ("2026-10-25T14:03:00.000Z"), lido por parseIso. */
private fun isoNow(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

private fun newId() = UUID.randomUUID().toString()

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().hex()
}

/** PBKDF2 com sal aleatório: a senha nunca fica salva. Formato "iterações:sal:hash" (base64). */
// ponytail: 20 mil iterações (no Android o PBKDF2 roda em Java; 120 mil levavam ~3 s por login no emulador).
// Hashes antigos guardam as próprias iterações, então dá para aumentar sem invalidar contas.
internal object PasswordHasher {
    private const val ITERATIONS = 20_000

    fun hash(senha: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "$ITERATIONS:${b64(salt)}:${b64(derive(senha, salt, ITERATIONS))}"
    }

    fun verify(senha: String, stored: String): Boolean {
        val parts = stored.split(':')
        val iterations = parts.getOrNull(0)?.toIntOrNull() ?: return false
        if (parts.size != 3) return false
        return MessageDigest.isEqual(derive(senha, unb64(parts[1]), iterations), unb64(parts[2]))
    }

    // PBKDF2WithHmacSHA1 existe desde o minSdk 24 (a variante SHA256 só a partir do Android 8).
    private fun derive(senha: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(senha.toCharArray(), salt, iterations, 256)).encoded

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)
}

private const val EMAIL_IN_USE = "Já existe um usuário cadastrado com este e-mail."

private suspend fun UserDao.requireUser(tokenManager: TokenManager): User =
    tokenManager.currentUserId()?.let { get(it) }?.toDomain() ?: throw NotLoggedInException()

private suspend fun UserDao.requireAdmin(tokenManager: TokenManager): User =
    requireUser(tokenManager).takeIf { it.isAdmin } ?: fail("Apenas o Admin pode realizar esta ação.")

class LocalAuthRepository(private val users: UserDao, private val tokenManager: TokenManager) : AuthRepository {

    override val currentUser: StateFlow<User?> = tokenManager.currentUser

    /** Sem servidor, a sessão não expira sozinha. */
    override val sessionExpired: Flow<Unit> = emptyFlow()

    override suspend fun login(email: String, senha: String): Result<User> = local("Erro no login") {
        val user = users.findByEmail(email.trim().lowercase())
        if (user == null || !PasswordHasher.verify(senha, user.senhaHash)) fail("E-mail ou senha incorretos.")
        user.toDomain().also { tokenManager.saveSession(LOCAL, it) }
    }

    /** Cadastro aberto cria sempre um Admin; signatários são cadastrados pelo Admin. */
    override suspend fun register(nome: String, email: String, telefone: String?, senha: String): Result<User> = local("Erro no cadastro") {
        DocumentRules.validateAccount(nome, email, senha)?.let(::fail)
        val mail = email.trim().lowercase()
        if (users.findByEmail(mail) != null) fail(EMAIL_IN_USE)
        val user = UserEntity(newId(), nome.trim(), mail, telefone?.trim()?.ifEmpty { null }, Role.ADMIN.name, null, PasswordHasher.hash(senha), isoNow())
        users.upsert(user)
        user.toDomain().also { tokenManager.saveSession(LOCAL, it) }
    }

    /** Só encerra a sessão: aqui o banco é o próprio sistema, e os outros usuários do aparelho mantêm seus dados. */
    override suspend fun logout() = tokenManager.clearTokens()
}

class LocalSignerRepository(private val database: AppDatabase, private val tokenManager: TokenManager) : SignerRepository {

    private val users = database.userDao()

    override fun observeSigners(): Flow<List<User>> =
        tokenManager.currentUser.perUser(emptyList()) { adminId ->
            users.observeSignersOf(adminId).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun refreshSigners(): Result<Unit> = Result.success(Unit)

    override suspend fun createSigner(nome: String, email: String, telefone: String, senha: String, cpf: String, dataNascimento: String): Result<User> =
        local("Erro ao cadastrar signatário") {
            val admin = users.requireAdmin(tokenManager)
            DocumentRules.validateSigner(nome, email, telefone, senha, isNew = true, cpf, dataNascimento)?.let(::fail)
            val mail = email.trim().lowercase()
            if (users.findByEmail(mail) != null) fail(EMAIL_IN_USE)
            val signer = UserEntity(
                newId(), nome.trim(), mail, telefone.trim(), Role.SIGNER.name, admin.id, PasswordHasher.hash(senha), isoNow(),
                cpf.filter(Char::isDigit).ifEmpty { null }, dataNascimento.trim().ifEmpty { null }
            )
            users.upsert(signer)
            signer.toDomain()
        }

    override suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?, cpf: String, dataNascimento: String): Result<User> =
        local("Erro ao atualizar signatário") {
            val signer = ownedSigner(id)
            DocumentRules.validateSigner(nome, email, telefone, senha.orEmpty(), isNew = false, cpf, dataNascimento)?.let(::fail)
            val mail = email.trim().lowercase()
            users.findByEmail(mail)?.let { if (it.id != id) fail(EMAIL_IN_USE) }
            val updated = signer.copy(
                nome = nome.trim(),
                email = mail,
                telefone = telefone.trim(),
                cpf = cpf.filter(Char::isDigit).ifEmpty { null },
                dataNascimento = dataNascimento.trim().ifEmpty { null },
                senhaHash = senha?.ifEmpty { null }?.let(PasswordHasher::hash) ?: signer.senhaHash
            )
            users.upsert(updated)
            updated.toDomain()
        }

    override suspend fun deleteSigner(id: String): Result<Unit> = local("Erro ao excluir signatário") {
        ownedSigner(id)
        if (database.documentDao().countParticipations(LOCAL, id) > 0) {
            fail("Este signatário já está vinculado a documentos e não pode ser removido.")
        }
        users.delete(id)
        // Sai também dos grupos em que estava.
        val admin = users.requireAdmin(tokenManager)
        orgs.observeGroups(admin.id).first().filter { id in it.memberIds.split(',') }.forEach { g ->
            orgs.upsertGroup(g.copy(memberIds = g.memberIds.split(',').filter { it != id }.joinToString(",")))
        }
    }

    // ---- Grupos de contatos ----

    private val orgs = database.orgDao()

    override fun observeGroups(): Flow<List<SignerGroup>> =
        tokenManager.currentUser.perUser(emptyList()) { adminId -> orgs.observeGroups(adminId).map { list -> list.map { it.toDomain() } } }

    override suspend fun saveGroup(id: String?, nome: String, memberIds: List<String>): Result<SignerGroup> = local("Erro ao salvar grupo") {
        val admin = users.requireAdmin(tokenManager)
        if (nome.isBlank()) fail("Informe o nome do grupo.")
        if (memberIds.isEmpty()) fail("Escolha pelo menos um contato para o grupo.")
        if (memberIds.any { users.get(it)?.adminId != admin.id }) fail("Um ou mais contatos não existem ou não pertencem a você.")
        val existing = id?.let { orgs.getGroup(it)?.takeIf { g -> g.adminId == admin.id } ?: fail("Grupo não encontrado.") }
        val group = GroupEntity(existing?.id ?: newId(), admin.id, nome.trim(), memberIds.distinct().joinToString(","))
        orgs.upsertGroup(group)
        group.toDomain()
    }

    override suspend fun deleteGroup(id: String): Result<Unit> = local("Erro ao excluir grupo") {
        val admin = users.requireAdmin(tokenManager)
        orgs.getGroup(id)?.takeIf { it.adminId == admin.id } ?: fail("Grupo não encontrado.")
        orgs.deleteGroup(id)
    }

    private suspend fun ownedSigner(id: String): UserEntity {
        val admin = users.requireAdmin(tokenManager)
        return users.get(id)?.takeIf { it.adminId == admin.id } ?: fail("Signatário não encontrado.")
    }
}

class LocalDocumentRepository(
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val pdfStorage: PdfStorage,
    private val stamper: PdfStamper,
    private val textIndex: com.letsign.mobile.data.pdf.PdfTextIndex
) : DocumentRepository {

    private val docs = database.documentDao()
    private val files = database.pdfFileDao()
    private val users = database.userDao()

    override fun observeDocuments(): Flow<List<Document>> =
        tokenManager.currentUser.perUser(emptyList()) { me ->
            combine(docs.observeVisible(LOCAL, me), docs.observeParticipations(LOCAL, me)) { list, mine ->
                val myByDoc = mine.associateBy { it.documentId }
                list.map { it.toDomain().forViewer(me, myByDoc[it.id]) }
            }
        }

    /** Emite null quando o documento não existe ou o usuário logado não pode vê-lo. */
    override fun observeDocument(id: String): Flow<Document?> =
        tokenManager.currentUser.perUser(null) { me ->
            combine(
                docs.observe(LOCAL, id),
                docs.observeParticipants(LOCAL, id),
                docs.observeFields(LOCAL, id),
                docs.observeEvents(LOCAL, id)
            ) { doc, participants, fields, events ->
                doc?.takeIf { canAccess(it, participants, me) }
                    ?.toDomain(participants, fields, events)
                    ?.forViewer(me, participants.firstOrNull { it.signerId == me })
            }
        }

    override fun observeLocalPdfs(id: String): Flow<List<LocalPdf>> =
        tokenManager.currentUser.perUser(emptyList()) { me ->
            combine(files.observe(LOCAL, id), docs.observe(LOCAL, id), docs.observeParticipants(LOCAL, id)) { list, doc, participants ->
                if (doc == null || !canAccess(doc, participants, me)) emptyList()
                else list.filter { File(it.caminho).exists() }.map { it.toDomain() }
            }
        }

    override suspend fun refreshDocuments(): Result<Unit> = Result.success(Unit)

    /** Abrir o documento como signatário registra a visualização (igual ao backend). */
    override suspend fun refreshDocument(id: String): Result<Document> = local("Documento não encontrado") {
        val me = users.requireUser(tokenManager)
        val doc = load(id)
        val mine = docs.getParticipants(LOCAL, id).firstOrNull { it.signerId == me.id }
        if (mine != null && mine.status == SignerStatus.PENDING.name && !doc.isCancelled) {
            val entity = docs.get(LOCAL, id)!!
            database.withTransaction {
                docs.upsertParticipants(listOf(mine.copy(status = SignerStatus.VIEWED.name, viewedAt = isoNow())))
                event(entity, AuditType.VIEWED, me, "${me.nome} visualizou o documento.")
            }
            load(id)
        } else doc
    }

    override suspend fun createDocument(titulo: String, pdf: File, nomeArquivo: String): Result<Document> =
        local("Erro ao criar documento") {
            val me = users.requireAdmin(tokenManager)
            DocumentRules.validateTitle(titulo)?.let(::fail)
            val pages = PdfDocumentRenderer.inspect(pdf).pageCount
            val hash = sha256(pdf)
            val size = pdf.length()
            val id = newId()
            val now = isoNow()
            val doc = DocumentEntity(
                userId = LOCAL, id = id, ownerId = me.id, ownerNome = me.nome, titulo = titulo.trim(),
                status = DocumentStatus.DRAFT.name, versao = 1, hashSha256 = hash, tamanhoBytes = size,
                nomeArquivo = nomeArquivo, totalPaginas = pages, mensagem = null, criadoEm = now, enviadoEm = null,
                concluidoEm = null, atualizadoEm = now, hashFinal = null, totalSignatarios = 0, assinados = 0,
                papel = null, minhaSituacao = null, meuPapel = null, assinadoEm = null, possuiDocumentoFinal = false,
                sincronizadoEm = System.currentTimeMillis()
            )
            val file = pdfStorage.adoptImported(LOCAL, id, 1, pdf)
            database.withTransaction {
                docs.upsertDocuments(listOf(doc))
                registerFile(doc, PdfKind.ORIGINAL, file, nomeArquivo)
                event(doc, AuditType.CREATED, me, "Documento \"${doc.titulo}\" criado por ${me.nome}.")
            }
            load(id)
        }

    /**
     * Regra do desafio: o documento enviado e suas configurações ficam preservados (assinaturas inclusive).
     * A nova versão é um documento novo, em rascunho, que começa com os mesmos signatários e campos;
     * a versão anterior, se ainda estava em andamento, é cancelada para ninguém assinar o PDF antigo.
     */
    // ponytail: nada impede criar outra versão a partir de uma já substituída; guardar o vínculo se isso incomodar.
    override suspend fun newVersion(id: String, pdf: File, nomeArquivo: String): Result<Document> =
        local("Erro ao enviar a nova versão") {
            val me = users.requireAdmin(tokenManager)
            val old = owned(id, me)
            when (old.status) {
                DocumentStatus.COMPLETED.name -> fail("Documento concluído não pode receber nova versão. Crie um novo documento.")
                DocumentStatus.DRAFT.name -> fail("Este documento ainda é um rascunho: continue a configuração em vez de criar uma versão.")
            }
            val pages = PdfDocumentRenderer.inspect(pdf).pageCount
            val newId = newId()
            val versao = old.versao + 1
            val now = isoNow()
            val doc = old.copy(
                id = newId, status = DocumentStatus.DRAFT.name, versao = versao, hashSha256 = sha256(pdf),
                tamanhoBytes = pdf.length(), nomeArquivo = nomeArquivo, totalPaginas = pages, criadoEm = now,
                enviadoEm = null, concluidoEm = null, atualizadoEm = now, hashFinal = null, assinados = 0,
                possuiDocumentoFinal = false
            )
            val participants = docs.getParticipants(LOCAL, id).map {
                it.copy(
                    id = newId(), documentId = newId, status = SignerStatus.PENDING.name, invitedAt = null, viewedAt = null,
                    signedAt = null, rejectedAt = null, motivoRecusa = null, assinaturaPng = null, gestureMetadata = null, rubricaPng = null
                )
            }
            val fields = docs.getFields(LOCAL, id).filter { it.page <= pages }.map { it.copy(id = newId(), documentId = newId) }
            val file = pdfStorage.adoptImported(LOCAL, newId, versao, pdf)
            database.withTransaction {
                docs.upsertDocuments(listOf(doc))
                docs.upsertParticipants(participants)
                docs.upsertFields(fields)
                registerFile(doc, PdfKind.ORIGINAL, file, nomeArquivo)
                event(doc, AuditType.VERSION_CREATED, me, "Versão $versao criada a partir da versão ${old.versao}, que foi preservada. Novo fluxo iniciado.")
                val emAndamento = old.status == DocumentStatus.PENDING.name || old.status == DocumentStatus.PARTIALLY_SIGNED.name
                if (emAndamento) docs.upsertDocuments(listOf(old.copy(status = DocumentStatus.CANCELLED.name, atualizadoEm = now)))
                event(old, AuditType.VERSION_CREATED, me, "Substituído pela versão $versao. Esta versão e suas assinaturas ficam preservadas.")
            }
            load(newId)
        }

    override suspend fun sendDocument(id: String, draft: SendDraft): Result<SendResult> = local("Erro ao enviar documento") {
        val me = users.requireAdmin(tokenManager)
        val doc = owned(id, me)
        if (doc.status != DocumentStatus.DRAFT.name) {
            if (doc.status == DocumentStatus.CANCELLED.name) fail("Documento cancelado não pode ser enviado. Crie uma nova versão.")
            // Idempotência: nova tentativa não duplica as solicitações.
            return@local SendResult(true, "Este documento já havia sido enviado; nenhuma solicitação foi duplicada.", load(id))
        }
        val mensagem = draft.mensagem?.trim()?.ifEmpty { null }
        (DocumentRules.validateTitle(doc.titulo)
            ?: DocumentRules.validateParticipants(draft.participants)
            ?: DocumentRules.validateFields(draft.participants, draft.fields, doc.totalPaginas)
            ?: DocumentRules.validateMessage(mensagem.orEmpty()))?.let(::fail)
        val signers = draft.participants.map { p ->
            users.get(p.signer.id)?.takeIf { it.adminId == me.id } ?: fail("Um ou mais signatários não existem ou não pertencem a você.")
        }
        // Revalida com o cadastro atual (ex.: CPF apagado depois de montar o rascunho).
        DocumentRules.validateFields(draft.participants.mapIndexed { i, p -> p.copy(signer = signers[i].toDomain()) }, draft.fields, doc.totalPaginas)?.let(::fail)
        val roleOf = draft.participants.associate { it.signer.id to it.roleLabel.name }
        val now = isoNow()
        database.withTransaction {
            docs.deleteParticipants(LOCAL, id)
            docs.deleteFields(LOCAL, id)
            docs.upsertParticipants(draft.participants.mapIndexed { i, p ->
                ParticipantEntity(
                    userId = LOCAL, id = newId(), documentId = id, signerId = signers[i].id, signerNome = signers[i].nome,
                    signerEmail = signers[i].email, status = SignerStatus.PENDING.name, roleLabel = p.roleLabel.name,
                    required = p.required, ordem = i, invitedAt = now, viewedAt = null, signedAt = null, rejectedAt = null,
                    motivoRecusa = null, signerCpf = signers[i].cpf, signerNascimento = signers[i].dataNascimento
                )
            })
            docs.upsertFields(draft.fields.map { f ->
                FieldEntity(LOCAL, newId(), id, f.signerId, f.page, f.x, f.y, f.width, f.height, true, roleOf.getValue(f.signerId), f.tipo.name)
            })
            docs.upsertDocuments(listOf(doc.copy(
                status = DocumentStatus.PENDING.name, enviadoEm = now, atualizadoEm = now, mensagem = mensagem,
                totalSignatarios = signers.size, assinados = 0
            )))
            event(doc, AuditType.SENT, me, "Documento enviado para ${signers.size} signatário(s): ${signers.joinToString { it.nome }}.")
        }
        SendResult(false, "Documento enviado. Os signatários encontram o convite ao entrar no app.", load(id))
    }

    override suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?, rubricaPngBase64: String?): Result<ActionResult> =
        local("Erro ao assinar documento") {
            val me = users.requireUser(tokenManager)
            val png = validPng(signaturePngBase64, "assinatura")
            val rubrica = rubricaPngBase64?.let { validPng(it, "rubrica") }

            val concluido = database.withTransaction {
                val (doc, mine) = invitation(id, me)
                when (mine.status) {
                    SignerStatus.SIGNED.name -> fail("Você já assinou este documento.")
                    SignerStatus.REJECTED.name -> fail("Você recusou este documento e não pode mais assiná-lo.")
                }
                val now = isoNow()
                val signed = mine.copy(
                    status = SignerStatus.SIGNED.name, signedAt = now, viewedAt = mine.viewedAt ?: now,
                    assinaturaPng = png, gestureMetadata = gestureMetadata?.toString(), rubricaPng = rubrica
                )
                val all = docs.getParticipants(LOCAL, id).map { if (it.id == signed.id) signed else it }
                // Regra: só conclui quando todos os signatários obrigatórios assinaram.
                val done = all.none { it.required && it.status != SignerStatus.SIGNED.name }
                docs.upsertParticipants(listOf(signed))
                docs.upsertDocuments(listOf(doc.copy(
                    status = if (done) DocumentStatus.COMPLETED.name else DocumentStatus.PARTIALLY_SIGNED.name,
                    concluidoEm = if (done) now else null, atualizadoEm = now,
                    assinados = all.count { it.status == SignerStatus.SIGNED.name }
                )))
                event(doc, AuditType.SIGNED, me, "${me.nome} assinou o documento.")
                done
            }
            if (concluido) finalize(id)
            ActionResult(if (concluido) "Assinatura registrada. Documento concluído!" else "Assinatura registrada com sucesso.", load(id))
        }

    override suspend fun rejectDocument(id: String, motivo: String): Result<ActionResult> = local("Erro ao recusar documento") {
        val me = users.requireUser(tokenManager)
        if (motivo.isBlank()) fail("Informe o motivo da recusa.")
        database.withTransaction {
            val (doc, mine) = invitation(id, me)
            if (mine.status == SignerStatus.SIGNED.name || mine.status == SignerStatus.REJECTED.name) fail("Você já respondeu a este documento.")
            val now = isoNow()
            docs.upsertParticipants(listOf(mine.copy(status = SignerStatus.REJECTED.name, rejectedAt = now, motivoRecusa = motivo.trim())))
            // Recusa de obrigatório cancela o documento; de opcional, só fica registrada.
            if (mine.required) docs.upsertDocuments(listOf(doc.copy(status = DocumentStatus.CANCELLED.name, atualizadoEm = now)))
            event(doc, AuditType.REJECTED, me, "${me.nome} recusou o documento. Motivo: ${motivo.trim()}")
        }
        ActionResult("Recusa registrada.", load(id))
    }

    override suspend fun getPdf(id: String, kind: PdfKind): Result<File> = local("Erro ao abrir o PDF") {
        val doc = load(id)
        if (kind == PdfKind.FINAL && doc.hashFinal == null) fail("O documento final só fica disponível após a conclusão de todas as assinaturas.")
        files.get(LOCAL, id, kind.apiValue, doc.versao)?.let { File(it.caminho) }?.takeIf { it.exists() }
            ?: fail("O arquivo deste documento não foi encontrado no aparelho.")
    }

    override suspend fun updateOrganization(id: String, pasta: String, categorias: List<String>): Result<Document> = local("Erro ao salvar") {
        val me = users.requireAdmin(tokenManager)
        val doc = owned(id, me)
        val folder = pasta.trim().ifEmpty { Document.DEFAULT_FOLDER }
        if (folder.length > 60) fail("O nome da pasta deve ter no máximo 60 caracteres.")
        docs.upsertDocuments(listOf(doc.copy(pasta = folder, categorias = categoriesToText(categorias))))
        load(id)
    }

    override suspend fun sendReminder(id: String): Result<ActionResult> = local("Erro ao enviar lembrete") {
        val me = users.requireAdmin(tokenManager)
        val doc = owned(id, me)
        if (doc.status != DocumentStatus.PENDING.name && doc.status != DocumentStatus.PARTIALLY_SIGNED.name) {
            fail("Só há lembrete para documentos aguardando assinatura.")
        }
        val pending = docs.getParticipants(LOCAL, id).filter { it.status == SignerStatus.PENDING.name || it.status == SignerStatus.VIEWED.name }
        if (pending.isEmpty()) fail("Todos os signatários já responderam.")
        event(doc, AuditType.REMINDER, me, "Lembrete enviado para ${pending.joinToString { it.signerNome.orEmpty() }}.")
        ActionResult("Lembrete registrado para ${pending.size} signatário(s). Eles veem o documento pendente ao entrar no app.", load(id))
    }

    override suspend fun setObservers(id: String, observerIds: List<String>): Result<Document> = local("Erro ao salvar permissões") {
        val me = users.requireAdmin(tokenManager)
        val doc = owned(id, me)
        val signerIds = docs.getParticipants(LOCAL, id).map { it.signerId }.toSet()
        val observers = observerIds.distinct().map { oid ->
            users.get(oid)?.takeIf { it.adminId == me.id }?.toDomain() ?: fail("Contato não encontrado.")
        }
        observers.firstOrNull { it.id in signerIds }?.let { fail("${it.nome} já é signatário deste documento.") }
        val before = observersFromJson(doc.observadores).map { it.id }.toSet()
        database.withTransaction {
            docs.upsertDocuments(listOf(doc.copy(observadores = observers.toObserversJson())))
            observers.filter { it.id !in before }.forEach { event(doc, AuditType.OTHER, me, "${it.nome} pode acompanhar o documento.") }
            before.filter { b -> observers.none { it.id == b } }.forEach { b ->
                event(doc, AuditType.OTHER, me, "${observersFromJson(doc.observadores).first { it.id == b }.nome} não acompanha mais o documento.")
            }
        }
        load(id)
    }

    override fun observeTemplates(): Flow<List<DocumentTemplate>> =
        tokenManager.currentUser.perUser(emptyList()) { me -> orgs.observeTemplates(me).map { list -> list.map { it.toDomain() } } }

    override suspend fun saveAsTemplate(id: String, nome: String): Result<DocumentTemplate> = local("Erro ao salvar modelo") {
        val me = users.requireAdmin(tokenManager)
        owned(id, me)
        if (nome.isBlank()) fail("Informe o nome do modelo.")
        val layout = buildLayout(id) ?: fail("Posicione os campos e envie o documento antes de salvar como modelo.")
        val template = TemplateEntity(newId(), me.id, nome.trim().take(DocumentRules.MAX_TITLE), id, isoNow(), layoutJ.encodeToString(layout))
        orgs.upsertTemplate(template)
        template.toDomain()
    }

    override suspend fun deleteTemplate(templateId: String): Result<Unit> = local("Erro ao excluir modelo") {
        val me = users.requireAdmin(tokenManager)
        orgs.getTemplate(templateId)?.takeIf { it.adminId == me.id } ?: fail("Modelo não encontrado.")
        orgs.deleteTemplate(templateId)
    }

    /** Copia o PDF original, os signatários e os campos do documento de origem para um rascunho novo. */
    override suspend fun createFromTemplate(templateId: String): Result<Document> = local("Erro ao usar o modelo") {
        val me = users.requireAdmin(tokenManager)
        val template = orgs.getTemplate(templateId)?.takeIf { it.adminId == me.id } ?: fail("Modelo não encontrado.")
        val source = owned(template.sourceDocumentId, me)
        val pdf = files.get(LOCAL, source.id, PdfKind.ORIGINAL.apiValue, source.versao)?.let { File(it.caminho) }?.takeIf { it.exists() }
            ?: fail("O PDF deste modelo não está mais no aparelho.")
        val newId = newId()
        val now = isoNow()
        val doc = source.copy(
            id = newId, titulo = template.nome, status = DocumentStatus.DRAFT.name, versao = 1, criadoEm = now, enviadoEm = null,
            concluidoEm = null, atualizadoEm = now, hashFinal = null, assinados = 0, possuiDocumentoFinal = false, mensagem = null
        )
        // Contatos excluídos depois de salvar o modelo ficam de fora.
        val participants = docs.getParticipants(LOCAL, source.id).filter { users.get(it.signerId) != null }.map {
            it.copy(
                id = newId(), documentId = newId, status = SignerStatus.PENDING.name, invitedAt = null, viewedAt = null, signedAt = null,
                rejectedAt = null, motivoRecusa = null, assinaturaPng = null, gestureMetadata = null, rubricaPng = null
            )
        }
        val keep = participants.map { it.signerId }.toSet()
        val fields = docs.getFields(LOCAL, source.id).filter { it.signerId in keep }.map { it.copy(id = newId(), documentId = newId) }
        val file = pdfStorage.save(LOCAL, newId, PdfKind.ORIGINAL.apiValue, 1, pdf.readBytes())
        database.withTransaction {
            docs.upsertDocuments(listOf(doc))
            docs.upsertParticipants(participants)
            docs.upsertFields(fields)
            registerFile(doc, PdfKind.ORIGINAL, file, source.nomeArquivo)
            event(doc, AuditType.CREATED, me, "Documento \"${doc.titulo}\" criado a partir do modelo \"${template.nome}\".")
        }
        load(newId)
    }

    override suspend fun matchTemplate(pdf: File, templateId: String?): Result<TemplateMatch?> = local("Erro ao aplicar o modelo") {
        val me = users.requireAdmin(tokenManager)
        val templates = if (templateId != null) listOf(orgs.getTemplate(templateId)?.takeIf { it.adminId == me.id } ?: fail("Modelo não encontrado."))
        else orgs.observeTemplates(me.id).first()
        if (templates.isEmpty()) return@local null
        val pageCount = PdfDocumentRenderer.inspect(pdf).pageCount
        val lines = textIndex.lines(pdf)
        val scored = templates.mapNotNull { t -> layoutOf(t)?.let { l -> Triple(t, l, TemplateMatcher.score(l, lines, pageCount)) } }
        val (template, layout, score) = (
            if (templateId != null) scored.firstOrNull()
            else scored.filter { it.third >= TemplateMatcher.MIN_SCORE }.maxByOrNull { it.third }
        ) ?: return@local null
        // Contatos excluídos depois de salvar o modelo ficam de fora (com os campos deles).
        val signers = layout.slots.map { s -> users.get(s.signerId)?.takeIf { it.adminId == me.id }?.toDomain() }
        val placed = TemplateMatcher.place(layout, lines, pageCount).filter { signers.getOrNull(it.slot) != null }
        TemplateMatch(
            template = template.toDomain(),
            score = score,
            participants = layout.slots.mapIndexedNotNull { i, s -> signers[i]?.let { DraftParticipant(it, s.roleLabel, s.required) } },
            fields = placed.map { p -> DraftField(newId(), signers[p.slot]!!.id, p.page, p.x, p.y, p.w, p.h, p.tipo) },
            anchored = placed.count { it.anchored },
            missingSigners = signers.count { it == null }
        )
    }

    // ---- internos ----

    private val layoutJ = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Layout salvo do modelo; modelos antigos (sem layout) são calculados agora a partir do documento de origem e guardados. */
    private suspend fun layoutOf(t: TemplateEntity): TemplateLayout? =
        t.layoutJson?.let { runCatching { layoutJ.decodeFromString<TemplateLayout>(it) }.getOrNull() }
            ?: buildLayout(t.sourceDocumentId)?.also { orgs.upsertTemplate(t.copy(layoutJson = layoutJ.encodeToString(it))) }

    /** Campos e signatários do documento + âncoras de texto do PDF original. Null se não há campos. */
    private suspend fun buildLayout(documentId: String): TemplateLayout? {
        val doc = docs.get(LOCAL, documentId) ?: return null
        val participants = docs.getParticipants(LOCAL, documentId)
        val fields = docs.getFields(LOCAL, documentId)
        if (fields.isEmpty() || participants.isEmpty()) return null
        val original = files.get(LOCAL, documentId, PdfKind.ORIGINAL.apiValue, doc.versao)?.let { File(it.caminho) }?.takeIf { it.exists() }
        val lines = original?.let(textIndex::lines).orEmpty()
        val slots = participants.map { p ->
            LayoutSlot(p.signerId, RoleLabel.entries.firstOrNull { it.name == p.roleLabel } ?: RoleLabel.CONTRATADO, p.required)
        }
        val layoutFields = fields.mapNotNull { f ->
            val slot = participants.indexOfFirst { it.signerId == f.signerId }.takeIf { it >= 0 } ?: return@mapNotNull null
            LayoutField(slot, FieldType.entries.firstOrNull { it.name == f.tipo } ?: FieldType.ASSINATURA, f.page, f.x, f.y, f.width, f.height)
        }
        return TemplateMatcher.build(slots, layoutFields, lines, doc.totalPaginas)
    }

    private val orgs = database.orgDao()

    private fun validPng(base64: String, what: String): String {
        val png = base64.replace(Regex("^data:image/[a-z]+;base64,", RegexOption.IGNORE_CASE), "").replace(Regex("\\s"), "")
        val bytes = runCatching { Base64.decode(png, Base64.DEFAULT) }.getOrNull()
        if (bytes == null || bytes.size < 8 || bytes.copyOf(8).hex() != "89504e470d0a1a0a") fail("A imagem da $what deve ser um PNG válido.")
        if (bytes.size > 2 * 1024 * 1024) fail("A imagem da $what deve ter no máximo 2 MB.")
        return png
    }

    /** Documento como o usuário logado o vê; falha se não existe ou se ele não tem acesso. */
    private suspend fun load(id: String): Document = observeDocument(id).first() ?: fail("Documento não encontrado.")

    private suspend fun owned(id: String, me: User): DocumentEntity =
        docs.get(LOCAL, id)?.takeIf { it.ownerId == me.id } ?: fail("Documento não encontrado.")

    /** Documento em andamento e o convite do usuário logado nele. */
    private suspend fun invitation(id: String, me: User): Pair<DocumentEntity, ParticipantEntity> {
        val doc = docs.get(LOCAL, id) ?: fail("Documento não encontrado.")
        val mine = docs.getParticipants(LOCAL, id).firstOrNull { it.signerId == me.id }
        if (mine == null || doc.status == DocumentStatus.DRAFT.name) fail("Apenas signatários convidados podem responder a este documento.")
        if (doc.status != DocumentStatus.PENDING.name && doc.status != DocumentStatus.PARTIALLY_SIGNED.name) {
            fail("Este documento não está mais disponível para assinatura.")
        }
        return doc to mine
    }

    /** Gera e registra o PDF final com as assinaturas (igual ao finalize do backend). */
    private suspend fun finalize(id: String) {
        val doc = docs.get(LOCAL, id) ?: return
        val participants = docs.getParticipants(LOCAL, id).filter { it.status == SignerStatus.SIGNED.name && it.assinaturaPng != null }
        val pngs = participants.associate { it.signerId to Base64.decode(it.assinaturaPng.orEmpty(), Base64.DEFAULT) }
        val rubricas = participants.filter { it.rubricaPng != null }.associate { it.signerId to Base64.decode(it.rubricaPng, Base64.DEFAULT) }
        val bySigner = participants.associateBy { it.signerId }
        val stamps = docs.getFields(LOCAL, id).mapNotNull { f ->
            val p = bySigner[f.signerId] ?: return@mapNotNull null
            val nome = p.signerNome.orEmpty()
            val quando = formatDateTime(p.signedAt)
            fun text(vararg lines: String, boxed: Boolean = false) = Stamp(f.page, f.x, f.y, f.width, f.height, null, "", lines.toList(), boxed)
            when (FieldType.entries.firstOrNull { it.name == f.tipo } ?: FieldType.ASSINATURA) {
                FieldType.ASSINATURA -> Stamp(f.page, f.x, f.y, f.width, f.height, pngs.getValue(f.signerId), "$nome - ${p.roleLabel} - ${p.signedAt}")
                FieldType.RUBRICA -> Stamp(f.page, f.x, f.y, f.width, f.height, rubricas[f.signerId] ?: pngs.getValue(f.signerId), "")
                FieldType.CARIMBO -> text("ASSINADO ELETRONICAMENTE", nome, quando, "LetsSign", boxed = true)
                FieldType.NOME -> text(nome)
                FieldType.EMAIL -> text(p.signerEmail.orEmpty())
                FieldType.CPF -> text(DocumentRules.formatCpf(p.signerCpf))
                FieldType.DATA -> text(quando)
            }
        }
        val original = File(files.get(LOCAL, id, PdfKind.ORIGINAL.apiValue, doc.versao)?.caminho ?: return)
        val footer = "LetsSign - documento ${doc.id} v${doc.versao} - hash original SHA-256 ${doc.hashSha256}"
        val bytes = try {
            stamper.stamp(original, stamps, footer)
        } catch (e: Exception) {
            // Mesmo comportamento do backend: sem conseguir carimbar, o final é o original (as assinaturas ficam no histórico).
            Log.e("LetsSign", "Falha ao gerar o PDF final de $id", e)
            original.readBytes()
        }
        val file = pdfStorage.save(LOCAL, id, PdfKind.FINAL.apiValue, doc.versao, bytes)
        val hashFinal = sha256(file)
        val completed = doc.copy(hashFinal = hashFinal, possuiDocumentoFinal = true)
        database.withTransaction {
            docs.upsertDocuments(listOf(completed))
            registerFile(completed, PdfKind.FINAL, file, doc.nomeArquivo.removeSuffix(".pdf") + "-assinado.pdf")
            event(completed, AuditType.COMPLETED, null, "Todos os signatários obrigatórios assinaram. Documento concluído.", hashFinal)
        }
    }

    private suspend fun registerFile(doc: DocumentEntity, kind: PdfKind, file: File, name: String) = files.upsert(
        PdfFileEntity(LOCAL, doc.id, kind.apiValue, doc.versao, name, file.absolutePath, file.length(), System.currentTimeMillis(), doc.status)
    )

    private suspend fun event(doc: DocumentEntity, tipo: AuditType, actor: User?, descricao: String, hash: String? = null) =
        docs.upsertEvents(listOf(
            AuditEventEntity(LOCAL, newId(), doc.id, tipo.name, actor?.id, actor?.role?.name, descricao, doc.versao, isoNow(), hash ?: doc.hashSha256)
        ))

    private fun canAccess(doc: DocumentEntity, participants: List<ParticipantEntity>, me: String) =
        doc.ownerId == me || (doc.status != DocumentStatus.DRAFT.name &&
            (participants.any { it.signerId == me } || observersFromJson(doc.observadores).any { it.id == me }))

    /** Campos que dependem de quem está vendo (o backend calcula o mesmo no detalhe e na lista). */
    private fun Document.forViewer(me: String, mine: ParticipantEntity?): Document {
        val isOwner = ownerId == me
        val my = mine?.toDomain()
        return copy(
            papel = when {
                isOwner -> "OWNER"
                my != null -> "SIGNER"
                else -> "OBSERVER"
            },
            minhaSituacao = my?.status,
            meuPapel = my?.roleLabel,
            assinadoEm = my?.signedAt,
            // O signatário só vê os próprios campos.
            fields = if (isOwner) fields else fields.filter { it.signerId == me },
            possuiDocumentoFinal = hashFinal != null
        )
    }
}
