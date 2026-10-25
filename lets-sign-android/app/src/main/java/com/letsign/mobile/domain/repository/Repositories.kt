package com.letsign.mobile.domain.repository

import com.letsign.mobile.domain.model.ActionResult
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.SendResult
import com.letsign.mobile.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import java.io.File

// Os ViewModels só conhecem estas interfaces. A implementação atual é offline-first:
// as telas leem do banco local (Room) e os métodos refresh*/ações sincronizam com a API REST.

interface AuthRepository {
    /** Usuário logado; null quando não há sessão. */
    val currentUser: StateFlow<User?>

    /** Emite quando o servidor recusa o token (sessão expirada). */
    val sessionExpired: Flow<Unit>

    suspend fun login(email: String, senha: String): Result<User>
    suspend fun register(nome: String, email: String, telefone: String?, senha: String): Result<User>

    /** Encerra a sessão e apaga todos os dados locais (banco e PDFs) deste aparelho. */
    suspend fun logout()
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    /** Documento com participantes, campos e eventos já sincronizados; null se ainda não está no aparelho. */
    fun observeDocument(id: String): Flow<Document?>

    /** PDFs deste documento salvos no aparelho. */
    fun observeLocalPdfs(id: String): Flow<List<LocalPdf>>

    suspend fun refreshDocuments(): Result<Unit>
    suspend fun refreshDocument(id: String): Result<Document>

    /** Cria o documento em rascunho no servidor e guarda o PDF no aparelho. */
    suspend fun createDocument(titulo: String, pdf: File, nomeArquivo: String): Result<Document>

    /** Nova versão do PDF de um documento já enviado: volta a rascunho e o PDF novo fica salvo no aparelho. */
    suspend fun newVersion(id: String, pdf: File, nomeArquivo: String): Result<Document>

    /** Envia para assinatura com participantes, campos e mensagem. Idempotente. */
    suspend fun sendDocument(id: String, draft: SendDraft): Result<SendResult>

    suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?): Result<ActionResult>
    suspend fun rejectDocument(id: String, motivo: String): Result<ActionResult>

    /** Devolve o PDF salvo no aparelho; se ainda não existe, baixa, salva e registra no banco. */
    suspend fun getPdf(id: String, kind: PdfKind): Result<File>
}

interface SignerRepository {
    fun observeSigners(): Flow<List<User>>
    suspend fun refreshSigners(): Result<Unit>
    suspend fun createSigner(nome: String, email: String, telefone: String, senha: String): Result<User>

    /** [senha] nula = manter a senha atual. */
    suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?): Result<User>
    suspend fun deleteSigner(id: String): Result<Unit>
}
