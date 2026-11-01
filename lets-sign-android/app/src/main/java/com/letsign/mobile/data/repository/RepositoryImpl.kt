package com.letsign.mobile.data.repository

import com.letsign.mobile.data.api.AuthApi
import com.letsign.mobile.data.api.DocumentsApi
import com.letsign.mobile.data.api.FieldRequest
import com.letsign.mobile.data.api.LoginRequest
import com.letsign.mobile.data.api.ParticipantRequest
import com.letsign.mobile.data.api.RegisterRequest
import com.letsign.mobile.data.api.RejectRequest
import com.letsign.mobile.data.api.SendRequest
import com.letsign.mobile.data.api.SignRequest
import com.letsign.mobile.data.api.SignerRequest
import com.letsign.mobile.data.api.SignersApi
import com.letsign.mobile.data.local.TokenManager
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.local.db.DocumentDao
import com.letsign.mobile.data.local.db.PdfFileDao
import com.letsign.mobile.data.local.db.PdfFileEntity
import com.letsign.mobile.data.local.db.SignerDao
import com.letsign.mobile.data.local.db.mergeLocalOnly
import com.letsign.mobile.data.local.db.toDomain
import com.letsign.mobile.data.local.db.toEntity
import com.letsign.mobile.data.local.db.toSignerEntity
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.domain.model.ActionResult
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentTemplate
import com.letsign.mobile.domain.model.SignerGroup
import kotlinx.coroutines.flow.flowOf
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.SendResult
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Apaga tudo o que é local (banco + PDFs). Usado no logout e quando a sessão expira. */
@Singleton
class LocalDataCleaner @Inject constructor(
    private val database: AppDatabase,
    private val pdfStorage: PdfStorage
) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        pdfStorage.deleteAll()
    }
}

/** Fluxo que segue o usuário logado: troca de sessão = troca de dados, sem misturar usuários. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> StateFlow<User?>.perUser(empty: T, block: (userId: String) -> Flow<T>): Flow<T> =
    flatMapLatest { user -> if (user == null) flowOf(empty) else block(user.id) }

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokenManager: TokenManager,
    private val cleaner: LocalDataCleaner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    override val currentUser: StateFlow<User?> = tokenManager.currentUser

    override val sessionExpired: Flow<Unit> = tokenManager.sessionExpired

    override suspend fun login(email: String, senha: String): Result<User> =
        apiCall(ioDispatcher, "Erro no login", { api.login(LoginRequest(email.trim(), senha)) }) { tokens ->
            startSession(tokens.accessToken, tokens.usuario)
        }

    override suspend fun register(nome: String, email: String, telefone: String?, senha: String): Result<User> =
        apiCall(ioDispatcher, "Erro no cadastro", { api.register(RegisterRequest(nome.trim(), email.trim(), telefone, senha)) }) { tokens ->
            startSession(tokens.accessToken, tokens.usuario)
        }

    /** Outro usuário pode ter usado o aparelho: começa sempre com o banco local vazio. */
    private suspend fun startSession(token: String, user: User): User {
        cleaner.clear()
        tokenManager.saveSession(token, user)
        return user
    }

    override suspend fun logout() {
        tokenManager.clearTokens()
        cleaner.clear()
    }
}

class DocumentRepositoryImpl(
    private val api: DocumentsApi,
    private val documentDao: DocumentDao,
    private val pdfFileDao: PdfFileDao,
    private val tokenManager: TokenManager,
    private val pdfStorage: PdfStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        tokenManager.currentUser.perUser(emptyList()) { userId ->
            documentDao.observeAll(userId).map { list -> list.map { it.toDomain() } }
        }

    override fun observeDocument(id: String): Flow<Document?> =
        tokenManager.currentUser.perUser(null) { userId ->
            combine(
                documentDao.observe(userId, id),
                documentDao.observeParticipants(userId, id),
                documentDao.observeFields(userId, id),
                documentDao.observeEvents(userId, id)
            ) { doc, participants, fields, events -> doc?.toDomain(participants, fields, events) }
        }

    override fun observeLocalPdfs(id: String): Flow<List<LocalPdf>> =
        tokenManager.currentUser.perUser(emptyList()) { userId ->
            pdfFileDao.observe(userId, id).map { files -> files.filter { File(it.caminho).exists() }.map { it.toDomain() } }
        }

    override suspend fun refreshDocuments(): Result<Unit> = withUser { userId ->
        apiCall(ioDispatcher, "Erro ao listar documentos", { api.listDocuments() }) { docs ->
            val entities = docs.map { doc -> doc.toEntity(userId).mergeLocalOnly(documentDao.get(userId, doc.id)) }
            documentDao.replaceList(userId, entities)
        }
    }

    override suspend fun refreshDocument(id: String): Result<Document> = withUser { userId ->
        apiCall(ioDispatcher, "Documento não encontrado", { api.getDocument(id) }) { doc -> saveDetail(userId, doc) }
    }

    override suspend fun createDocument(titulo: String, pdf: File, nomeArquivo: String): Result<Document> = withUser { userId ->
        val pdfPart = MultipartBody.Part.createFormData("pdf", nomeArquivo, pdf.asRequestBody("application/pdf".toMediaType()))
        apiCall(ioDispatcher, "Erro ao criar documento", {
            api.createDocument(pdfPart, titulo.trim().toRequestBody("text/plain".toMediaType()))
        }) { doc ->
            val saved = saveDetail(userId, doc)
            // O PDF escolhido passa a ser o "original" registrado deste documento.
            val file = pdfStorage.adoptImported(userId, doc.id, doc.versao, pdf)
            registerFile(userId, saved, PdfKind.ORIGINAL, file, nomeArquivo)
            saved
        }
    }

    override suspend fun newVersion(id: String, pdf: File, nomeArquivo: String): Result<Document> = withUser { userId ->
        val pdfPart = MultipartBody.Part.createFormData("pdf", nomeArquivo, pdf.asRequestBody("application/pdf".toMediaType()))
        apiCall(ioDispatcher, "Erro ao enviar a nova versão", { api.newVersion(id, pdfPart) }) { doc ->
            val saved = saveDetail(userId, doc)
            // A versão anterior continua registrada no aparelho; o PDF novo vira o "original" da nova versão.
            val file = pdfStorage.adoptImported(userId, doc.id, doc.versao, pdf)
            registerFile(userId, saved, PdfKind.ORIGINAL, file, nomeArquivo)
            saved
        }
    }

    override suspend fun sendDocument(id: String, draft: SendDraft): Result<SendResult> = withUser { userId ->
        val request = SendRequest(
            mensagem = draft.mensagem?.trim()?.ifEmpty { null },
            participantes = draft.participants.map { ParticipantRequest(it.signer.id, it.roleLabel.name, it.required) },
            fields = draft.fields.map { f ->
                val role = draft.participants.firstOrNull { it.signer.id == f.signerId }?.roleLabel
                FieldRequest(f.signerId, f.page, f.x, f.y, f.width, f.height, required = true, roleLabel = role?.name)
            }
        )
        apiCall(ioDispatcher, "Erro ao enviar documento", { api.sendDocument(id, request) }) { resp ->
            SendResult(resp.jaEnviado, resp.mensagem, saveDetail(userId, resp.documento))
        }
    }

    // ponytail: o backend não conhece rubrica; o PDF final dele usa a assinatura em todos os campos.
    override suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?, rubricaPngBase64: String?): Result<ActionResult> =
        withUser { userId ->
            apiCall(ioDispatcher, "Erro ao assinar documento", {
                api.signDocument(id, SignRequest(signaturePngBase64, gestureMetadata))
            }) { resp -> ActionResult(resp.mensagem, saveDetail(userId, resp.documento)) }
        }

    override suspend fun rejectDocument(id: String, motivo: String): Result<ActionResult> = withUser { userId ->
        apiCall(ioDispatcher, "Erro ao recusar documento", { api.rejectDocument(id, RejectRequest(motivo.trim())) }) { resp ->
            ActionResult(resp.mensagem, saveDetail(userId, resp.documento))
        }
    }

    override suspend fun getPdf(id: String, kind: PdfKind): Result<File> = withUser { userId ->
        val doc = withContext(ioDispatcher) { documentDao.get(userId, id) }?.toDomain()
            ?: refreshDocument(id).getOrElse { return@withUser Result.failure(it) }
        val cached = withContext(ioDispatcher) { pdfFileDao.get(userId, id, kind.apiValue, doc.versao) }
        if (cached != null && File(cached.caminho).exists()) return@withUser Result.success(File(cached.caminho))

        apiCall(ioDispatcher, "Erro ao baixar o PDF", { api.downloadPdf(id, kind.apiValue) }) { body ->
            val file = pdfStorage.save(userId, id, kind.apiValue, doc.versao, body.bytes())
            val name = if (kind == PdfKind.FINAL) doc.nomeArquivo.removeSuffix(".pdf") + "-assinado.pdf" else doc.nomeArquivo
            registerFile(userId, doc, kind, file, name)
            file
        }
    }

    // Recursos que o backend REST ainda não tem: disponíveis só no modo local (padrão do app).
    override suspend fun updateOrganization(id: String, pasta: String, categorias: List<String>): Result<Document> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun sendReminder(id: String): Result<ActionResult> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun setObservers(id: String, observerIds: List<String>): Result<Document> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override fun observeTemplates(): Flow<List<DocumentTemplate>> = flowOf(emptyList())
    override suspend fun saveAsTemplate(id: String, nome: String): Result<DocumentTemplate> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun deleteTemplate(templateId: String): Result<Unit> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun createFromTemplate(templateId: String): Result<Document> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun matchTemplate(pdf: File, templateId: String?): Result<com.letsign.mobile.domain.model.TemplateMatch?> = Result.success(null)

    private suspend fun saveDetail(userId: String, doc: Document): Document {
        val existing = documentDao.get(userId, doc.id)
        // O detalhe não traz minhaSituacao/meuPapel: deriva do participante que é o próprio usuário.
        val me = doc.signers.firstOrNull { it.signerId == userId }
        val merged = doc.copy(
            minhaSituacao = doc.minhaSituacao ?: me?.status,
            meuPapel = doc.meuPapel ?: me?.roleLabel,
            assinadoEm = doc.assinadoEm ?: me?.signedAt
        )
        documentDao.replaceDetail(
            userId = userId,
            document = merged.toEntity(userId).mergeLocalOnly(existing),
            participants = merged.signers.map { it.toEntity(userId, doc.id) },
            fields = merged.fields.map { it.toEntity(userId, doc.id) },
            events = merged.events.map { it.toEntity(userId, doc.id) }
        )
        return merged
    }

    private suspend fun registerFile(userId: String, doc: Document, kind: PdfKind, file: File, name: String) {
        pdfFileDao.upsert(
            PdfFileEntity(
                userId = userId,
                documentId = doc.id,
                tipo = kind.apiValue,
                versao = doc.versao,
                nomeArquivo = name,
                caminho = file.absolutePath,
                tamanhoBytes = file.length(),
                salvoEm = System.currentTimeMillis(),
                statusDocumento = doc.status.name
            )
        )
    }

    private inline fun <T> withUser(block: (String) -> Result<T>): Result<T> =
        tokenManager.currentUserId()?.let(block) ?: Result.failure(NotLoggedInException())
}

class SignerRepositoryImpl(
    private val api: SignersApi,
    private val signerDao: SignerDao,
    private val tokenManager: TokenManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SignerRepository {

    override fun observeSigners(): Flow<List<User>> =
        tokenManager.currentUser.perUser(emptyList()) { userId ->
            signerDao.observeAll(userId).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun refreshSigners(): Result<Unit> = withUser { userId ->
        apiCall(ioDispatcher, "Erro ao listar signatários", { api.listSigners() }) { list ->
            signerDao.replaceAll(userId, list.map { it.toSignerEntity(userId) })
        }
    }

    // ponytail: o backend não guarda CPF nem nascimento; ficam só no modo local.
    override suspend fun createSigner(nome: String, email: String, telefone: String, senha: String, cpf: String, dataNascimento: String): Result<User> = withUser { userId ->
        apiCall(ioDispatcher, "Erro ao cadastrar signatário", {
            api.createSigner(SignerRequest(nome.trim(), email.trim(), telefone.trim(), senha))
        }) { created -> created.also { signerDao.upsert(listOf(it.toSignerEntity(userId))) } }
    }

    // senha = null: o PATCH não altera a senha do signatário.
    override suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?, cpf: String, dataNascimento: String): Result<User> =
        withUser { userId ->
            apiCall(ioDispatcher, "Erro ao atualizar signatário", {
                api.updateSigner(id, SignerRequest(nome.trim(), email.trim(), telefone.trim(), senha?.ifEmpty { null }))
            }) { updated -> updated.also { signerDao.upsert(listOf(it.toSignerEntity(userId))) } }
        }

    override suspend fun deleteSigner(id: String): Result<Unit> = withUser { userId ->
        apiCall(ioDispatcher, "Erro ao excluir signatário", { api.deleteSigner(id) }) { signerDao.delete(userId, id) }
    }

    override fun observeGroups(): Flow<List<SignerGroup>> = flowOf(emptyList())
    override suspend fun saveGroup(id: String?, nome: String, memberIds: List<String>): Result<SignerGroup> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))
    override suspend fun deleteGroup(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("Disponível apenas no modo local."))

    private inline fun <T> withUser(block: (String) -> Result<T>): Result<T> =
        tokenManager.currentUserId()?.let(block) ?: Result.failure(NotLoggedInException())
}
