package com.letsign.mobile.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsign.mobile.data.pdf.PdfImporter
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Filtros da tela do Admin (requisito: ver enviados, parcialmente assinados e concluídos). */
enum class AdminFilter(val label: String, val matches: (Document) -> Boolean) {
    ALL("Todos", { true }),
    DRAFT("Rascunhos", { it.status == DocumentStatus.DRAFT }),
    SENT("Enviados", { it.status == DocumentStatus.PENDING }),
    PARTIAL("Parciais", { it.status == DocumentStatus.PARTIALLY_SIGNED }),
    COMPLETED("Concluídos", { it.status == DocumentStatus.COMPLETED }),
    CANCELLED("Cancelados", { it.status == DocumentStatus.CANCELLED })
}

/** Abas do Signatário (requisito: ver pendentes e os já assinados). */
enum class SignerTab(val label: String, val matches: (Document) -> Boolean) {
    PENDING("Pendentes", { it.canSign }),
    SIGNED("Assinados", { it.minhaSituacao == SignerStatus.SIGNED }),
    CLOSED("Cancelados", { !it.canSign && it.minhaSituacao != SignerStatus.SIGNED })
}

data class DocumentListUiState(
    val user: User? = null,
    val documents: List<Document> = emptyList(),
    val adminFilter: AdminFilter = AdminFilter.ALL,
    val signerTab: SignerTab = SignerTab.PENDING,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val visibleAdminDocuments: List<Document> get() = documents.filter(adminFilter.matches)
    val visibleSignerDocuments: List<Document> get() = documents.filter(signerTab.matches)
    fun count(tab: SignerTab) = documents.count(tab.matches)
    fun count(filter: AdminFilter) = documents.count(filter.matches)
}

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val local = MutableStateFlow(DocumentListUiState())

    val uiState: StateFlow<DocumentListUiState> =
        combine(local, documentRepository.observeDocuments(), authRepository.currentUser) { state, docs, user ->
            state.copy(documents = docs, user = user)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true, error = null) }
            val result = documentRepository.refreshDocuments()
            local.update { it.copy(isRefreshing = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun setAdminFilter(filter: AdminFilter) = local.update { it.copy(adminFilter = filter) }
    fun setSignerTab(tab: SignerTab) = local.update { it.copy(signerTab = tab) }
    fun consumeError() = local.update { it.copy(error = null) }
}

data class DocumentDetailUiState(
    val document: Document? = null,
    val localPdfs: List<LocalPdf> = emptyList(),
    val currentUserId: String? = null,
    val isRefreshing: Boolean = false,
    val isWorking: Boolean = false,
    val workingMessage: String = "Registrando...",
    val error: String? = null,
    val message: String? = null,
    /** Id da nova versão criada: a tela abre o assistente nela para revisar signatários e campos e reenviar. */
    val newVersionId: String? = null
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    authRepository: AuthRepository,
    private val pdfImporter: PdfImporter
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val local = MutableStateFlow(DocumentDetailUiState())

    val uiState: StateFlow<DocumentDetailUiState> = combine(
        local,
        documentRepository.observeDocument(documentId),
        documentRepository.observeLocalPdfs(documentId),
        authRepository.currentUser
    ) { state, doc, pdfs, user -> state.copy(document = doc, localPdfs = pdfs, currentUserId = user?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentDetailUiState())

    init {
        refresh()
    }

    /** Busca o detalhe na API (atualiza o banco local; a tela reage sozinha pelo Flow). */
    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true, error = null) }
            val result = documentRepository.refreshDocument(documentId)
            local.update { it.copy(isRefreshing = false, error = result.exceptionOrNull()?.message) }
        }
    }

    /**
     * Regra do desafio: documento enviado não é alterado; alteração gera nova versão e novo fluxo.
     * Valida o PDF escolhido no aparelho antes de enviar.
     */
    fun newVersion(uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(isWorking = true, workingMessage = "Enviando nova versão...", error = null) }
            val picked = try {
                pdfImporter.import(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(isWorking = false, error = e.message ?: "Não foi possível processar o arquivo.") }
                return@launch
            }
            documentRepository.newVersion(documentId, File(picked.path), picked.displayName)
                .onSuccess { doc -> local.update { it.copy(isWorking = false, newVersionId = doc.id) } }
                .onFailure { e -> local.update { it.copy(isWorking = false, error = e.message) } }
        }
    }

    fun consumeNewVersion() = local.update { it.copy(newVersionId = null) }

    fun reject(motivo: String) {
        if (motivo.isBlank()) {
            local.update { it.copy(error = "Informe o motivo da recusa.") }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(isWorking = true, workingMessage = "Registrando...", error = null) }
            documentRepository.rejectDocument(documentId, motivo)
                .onSuccess { r -> local.update { it.copy(isWorking = false, message = r.mensagem) } }
                .onFailure { e -> local.update { it.copy(isWorking = false, error = e.message) } }
        }
    }

    fun consumeMessages() = local.update { it.copy(error = null, message = null) }
}

data class SignDocumentUiState(
    val document: Document? = null,
    val pdfPath: String? = null,
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val isSigning: Boolean = false,
    val error: String? = null,
    /** Mensagem do repositório após assinar com sucesso. */
    val signedMessage: String? = null
)

@HiltViewModel
class SignDocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val local = MutableStateFlow(SignDocumentUiState())

    val uiState: StateFlow<SignDocumentUiState> = combine(
        local,
        documentRepository.observeDocument(documentId),
        authRepository.currentUser
    ) { state, doc, user -> state.copy(document = doc, currentUserId = user?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignDocumentUiState())

    init {
        load()
    }

    /** Baixa (ou reaproveita do aparelho) o PDF original para mostrar onde assinar. */
    fun load() {
        viewModelScope.launch {
            local.update { it.copy(isLoading = true, error = null) }
            documentRepository.refreshDocument(documentId)
            documentRepository.getPdf(documentId, PdfKind.ORIGINAL)
                .onSuccess { file -> local.update { it.copy(isLoading = false, pdfPath = file.absolutePath) } }
                .onFailure { e -> local.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun sign(pngBase64: String, gestureMetadata: JsonObject) {
        if (local.value.isSigning) return
        viewModelScope.launch {
            local.update { it.copy(isSigning = true, error = null) }
            documentRepository.signDocument(documentId, pngBase64, gestureMetadata)
                .onSuccess { r ->
                    // "Salvar no sistema": se concluiu, já guarda o PDF final assinado no aparelho.
                    if (r.document.isCompleted) documentRepository.getPdf(documentId, PdfKind.FINAL)
                    local.update { it.copy(isSigning = false, signedMessage = r.mensagem) }
                }
                .onFailure { e -> local.update { it.copy(isSigning = false, error = e.message) } }
        }
    }

    fun consumeError() = local.update { it.copy(error = null) }
}

data class PdfViewerUiState(
    val title: String = "Documento",
    val file: File? = null,
    /** Nome do arquivo para o usuário (o arquivo no aparelho tem um nome interno). */
    val fileName: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    val kind: PdfKind = if (savedStateHandle.get<String>("tipo") == PdfKind.FINAL.apiValue) PdfKind.FINAL else PdfKind.ORIGINAL

    private val _uiState = MutableStateFlow(PdfViewerUiState(title = if (kind == PdfKind.FINAL) "Documento final" else "Documento original"))
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            documentRepository.getPdf(documentId, kind)
                .onSuccess { f ->
                    val name = documentRepository.observeLocalPdfs(documentId).first().firstOrNull { it.caminho == f.absolutePath }?.nomeArquivo
                    _uiState.update { it.copy(isLoading = false, file = f, fileName = name ?: f.name) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
