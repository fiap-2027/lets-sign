package com.letsign.mobile.data.local

import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.local.db.AuditEventEntity
import com.letsign.mobile.data.local.db.DocumentEntity
import com.letsign.mobile.data.local.db.FieldEntity
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

    override suspend fun createSigner(nome: String, email: String, telefone: String, senha: String): Result<User> =
        local("Erro ao cadastrar signatário") {
            val admin = users.requireAdmin(tokenManager)
            DocumentRules.validateSigner(nome, email, telefone, senha, isNew = true)?.let(::fail)
            val mail = email.trim().lowercase()
            if (users.findByEmail(mail) != null) fail(EMAIL_IN_USE)
            val signer = UserEntity(newId(), nome.trim(), mail, telefone.trim(), Role.SIGNER.name, admin.id, PasswordHasher.hash(senha), isoNow())
            users.upsert(signer)
            signer.toDomain()
        }

    override suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?): Result<User> =
        local("Erro ao atualizar signatário") {
            val signer = ownedSigner(id)
            DocumentRules.validateSigner(nome, email, telefone, senha.orEmpty(), isNew = false)?.let(::fail)
            val mail = email.trim().lowercase()
            users.findByEmail(mail)?.let { if (it.id != id) fail(EMAIL_IN_USE) }
            val updated = signer.copy(
                nome = nome.trim(),
                email = mail,
                telefone = telefone.trim(),
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
    private val stamper: PdfStamper
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
                    signedAt = null, rejectedAt = null, motivoRecusa = null, assinaturaPng = null, gestureMetadata = null
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
                    motivoRecusa = null
                )
            })
            docs.upsertFields(draft.fields.map { f ->
                FieldEntity(LOCAL, newId(), id, f.signerId, f.page, f.x, f.y, f.width, f.height, true, roleOf.getValue(f.signerId))
            })
            docs.upsertDocuments(listOf(doc.copy(
                status = DocumentStatus.PENDING.name, enviadoEm = now, atualizadoEm = now, mensagem = mensagem,
                totalSignatarios = signers.size, assinados = 0
            )))
            event(doc, AuditType.SENT, me, "Documento enviado para ${signers.size} signatário(s): ${signers.joinToString { it.nome }}.")
        }
        SendResult(false, "Documento enviado. Os signatários encontram o convite ao entrar no app.", load(id))
    }

    override suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?): Result<ActionResult> =
        local("Erro ao assinar documento") {
            val me = users.requireUser(tokenManager)
            val png = signaturePngBase64.replace(Regex("^data:image/[a-z]+;base64,", RegexOption.IGNORE_CASE), "").replace(Regex("\\s"), "")
            val bytes = runCatching { Base64.decode(png, Base64.DEFAULT) }.getOrNull()
            if (bytes == null || bytes.size < 8 || bytes.copyOf(8).hex() != "89504e470d0a1a0a") fail("A imagem da assinatura deve ser um PNG válido.")
            if (bytes.size > 2 * 1024 * 1024) fail("A imagem da assinatura deve ter no máximo 2 MB.")

            val concluido = database.withTransaction {
                val (doc, mine) = invitation(id, me)
                when (mine.status) {
                    SignerStatus.SIGNED.name -> fail("Você já assinou este documento.")
                    SignerStatus.REJECTED.name -> fail("Você recusou este documento e não pode mais assiná-lo.")
                }
                val now = isoNow()
                val signed = mine.copy(
                    status = SignerStatus.SIGNED.name, signedAt = now, viewedAt = mine.viewedAt ?: now,
                    assinaturaPng = png, gestureMetadata = gestureMetadata?.toString()
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

    // ---- internos ----

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
        val bySigner = participants.associateBy { it.signerId }
        val stamps = docs.getFields(LOCAL, id).mapNotNull { f ->
            val p = bySigner[f.signerId] ?: return@mapNotNull null
            Stamp(f.page, f.x, f.y, f.width, f.height, pngs.getValue(f.signerId), "${p.signerNome} - ${p.roleLabel} - ${p.signedAt}")
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
        doc.ownerId == me || (doc.status != DocumentStatus.DRAFT.name && participants.any { it.signerId == me })

    /** Campos que dependem de quem está vendo (o backend calcula o mesmo no detalhe e na lista). */
    private fun Document.forViewer(me: String, mine: ParticipantEntity?): Document {
        val isOwner = ownerId == me
        val my = mine?.toDomain()
        return copy(
            papel = if (isOwner) "OWNER" else "SIGNER",
            minhaSituacao = my?.status,
            meuPapel = my?.roleLabel,
            assinadoEm = my?.signedAt,
            // O signatário só vê os próprios campos.
            fields = if (isOwner) fields else fields.filter { it.signerId == me },
            possuiDocumentoFinal = hashFinal != null
        )
    }
}
