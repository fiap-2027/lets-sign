package com.letsign.mobile.domain.repository

import com.letsign.mobile.domain.model.ActionResult
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentTemplate
import com.letsign.mobile.domain.model.SignerGroup
import com.letsign.mobile.domain.model.TemplateMatch
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

    /**
     * Encerra a sessão. No modo local os dados ficam no aparelho (o banco é a fonte da verdade);
     * no modo API o cache local (banco e PDFs) é apagado.
     */
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

    /** [rubricaPngBase64]: imagem usada nos campos "Rubrica" (sem ela, a assinatura é usada). */
    suspend fun signDocument(id: String, signaturePngBase64: String, gestureMetadata: JsonObject?, rubricaPngBase64: String? = null): Result<ActionResult>
    suspend fun rejectDocument(id: String, motivo: String): Result<ActionResult>

    /** Devolve o PDF salvo no aparelho; se ainda não existe, baixa, salva e registra no banco. */
    suspend fun getPdf(id: String, kind: PdfKind): Result<File>

    // ---- Organização (modo local) ----

    /** Pasta e categorias (podem mudar a qualquer momento: não alteram o PDF nem as assinaturas). */
    suspend fun updateOrganization(id: String, pasta: String, categorias: List<String>): Result<Document>

    /** Registra um lembrete para quem ainda não assinou. */
    suspend fun sendReminder(id: String): Result<ActionResult>

    /** Define quem acompanha o documento sem assinar (aba Permissões). */
    suspend fun setObservers(id: String, observerIds: List<String>): Result<Document>

    fun observeTemplates(): Flow<List<DocumentTemplate>>
    suspend fun saveAsTemplate(id: String, nome: String): Result<DocumentTemplate>
    suspend fun deleteTemplate(templateId: String): Result<Unit>

    /** Novo rascunho com o PDF, os signatários e os campos do modelo. */
    suspend fun createFromTemplate(templateId: String): Result<Document>

    /**
     * Posiciona campos num PDF novo a partir de um modelo. [templateId] nulo = escolhe sozinho o modelo
     * mais parecido (null se nenhum passar do mínimo); informado = aplica esse modelo de qualquer forma.
     */
    suspend fun matchTemplate(pdf: File, templateId: String? = null): Result<TemplateMatch?>
}

interface SignerRepository {
    fun observeSigners(): Flow<List<User>>
    suspend fun refreshSigners(): Result<Unit>
    suspend fun createSigner(nome: String, email: String, telefone: String, senha: String, cpf: String = "", dataNascimento: String = ""): Result<User>

    /** [senha] nula = manter a senha atual. */
    suspend fun updateSigner(id: String, nome: String, email: String, telefone: String, senha: String?, cpf: String = "", dataNascimento: String = ""): Result<User>
    suspend fun deleteSigner(id: String): Result<Unit>

    fun observeGroups(): Flow<List<SignerGroup>>

    /** [id] nulo = grupo novo. */
    suspend fun saveGroup(id: String?, nome: String, memberIds: List<String>): Result<SignerGroup>
    suspend fun deleteGroup(id: String): Result<Unit>
}
