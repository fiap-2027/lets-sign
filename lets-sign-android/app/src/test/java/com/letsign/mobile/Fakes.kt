package com.letsign.mobile

import android.net.Uri
import com.letsign.mobile.data.pdf.PdfImporter
import com.letsign.mobile.data.pdf.PdfInfo
import com.letsign.mobile.data.pdf.PickedPdf
import com.letsign.mobile.domain.model.ActionResult
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.SendResult
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import java.io.File

// Implementações em memória das interfaces, para testar ViewModels sem rede nem banco.

class FakeDocumentRepository : DocumentRepository {
    var createCalls = 0
    var sendCalls = 0
    val sentDrafts = mutableListOf<Pair<String, SendDraft>>()
    /** Respostas do envio em ordem; a última se repete. */
    var sendResults: MutableList<Result<SendResult>> = mutableListOf()
    var createResult: Result<Document> = Result.success(Document(id = "srv-1", ownerId = "admin", titulo = "Contrato"))
    var detail: Document? = null

    override fun observeDocuments(): Flow<List<Document>> = flowOf(emptyList())
    override fun observeDocument(id: String): Flow<Document?> = flowOf(detail)
    override fun observeLocalPdfs(id: String): Flow<List<LocalPdf>> = flowOf(emptyList())
    override suspend fun refreshDocuments(): Result<Unit> = Result.success(Unit)
    override suspend fun refreshDocument(id: String): Result<Document> =
        detail?.let { Result.success(it) } ?: Result.failure(Exception("não encontrado"))

    override suspend fun createDocument(titulo: String, pdf: File, nomeArquivo: String): Result<Document> {
        createCalls++
        return createResult
    }

    var newVersionCalls = 0
    var newVersionResult: Result<Document> = Result.success(Document(id = "d1", ownerId = "admin", titulo = "Contrato", versao = 2))
    override suspend fun newVersion(id: String, pdf: File, nomeArquivo: String): Result<Document> {
        newVersionCalls++
        return newVersionResult
    }

    override suspend fun sendDocument(id: String, draft: SendDraft): Result<SendResult> {
        sendCalls++
        sentDrafts += id to draft
        return if (sendResults.size > 1) sendResults.removeAt(0) else sendResults.single()
    }

    override suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?): Result<ActionResult> =
        Result.failure(UnsupportedOperationException())

    override suspend fun rejectDocument(id: String, motivo: String): Result<ActionResult> = Result.failure(UnsupportedOperationException())
    override suspend fun getPdf(id: String, kind: PdfKind): Result<File> = Result.success(File("/tmp/$id.pdf"))
}

class FakeSignerRepository(signers: List<User>) : SignerRepository {
    private val flow = MutableStateFlow(signers)
    override fun observeSigners(): Flow<List<User>> = flow
    override suspend fun refreshSigners(): Result<Unit> = Result.success(Unit)
    override suspend fun createSigner(nome: String, email: String, telefone: String, senha: String) = Result.failure<User>(UnsupportedOperationException())
    override suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?) = Result.failure<User>(UnsupportedOperationException())
    override suspend fun deleteSigner(id: String): Result<Unit> = Result.success(Unit)
}

class FakePdfImporter(private val pages: Int = 3, private val importError: Exception? = null) : PdfImporter {
    override suspend fun import(uri: Uri) = importError?.let { throw it } ?: PickedPdf("/tmp/importado.pdf", "contrato.pdf", 1000, PdfInfo(pages, List(pages) { 0.707f }))
    override suspend fun inspect(file: File, displayName: String) = PickedPdf(file.path, displayName, 1000, PdfInfo(pages, List(pages) { 0.707f }))
}

class FakeAuthRepository : AuthRepository {
    override val currentUser: StateFlow<User?> = MutableStateFlow(User("u", "Admin", "a@x.com"))
    val expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpired: Flow<Unit> = expired
    var logoutCalls = 0
    override suspend fun login(email: String, senha: String) = Result.failure<User>(UnsupportedOperationException())
    override suspend fun register(nome: String, email: String, telefone: String?, senha: String) = Result.failure<User>(UnsupportedOperationException())
    override suspend fun logout() {
        logoutCalls++
    }
}
