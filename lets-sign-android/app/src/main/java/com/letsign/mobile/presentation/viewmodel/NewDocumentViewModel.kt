package com.letsign.mobile.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsign.mobile.data.pdf.PdfImporter
import com.letsign.mobile.data.pdf.PickedPdf
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import com.letsign.mobile.domain.rules.DocumentRules
import com.letsign.mobile.domain.rules.moveBy
import com.letsign.mobile.domain.rules.resizeBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Etapas do fluxo de cadastro de documento (requisito 3 do desafio). */
enum class WizardStep(val title: String) {
    DADOS("Documento"),
    SIGNATARIOS("Signatários"),
    CAMPOS("Campos"),
    REVISAO("Revisão")
}

sealed interface SendOutcome {
    data class Success(val documentId: String, val mensagem: String, val jaEnviado: Boolean) : SendOutcome
    data class Failure(val mensagem: String) : SendOutcome
}

data class WizardUiState(
    val step: WizardStep = WizardStep.DADOS,
    /** Continuação de um rascunho que já existe no servidor (título e PDF ficam fixos). */
    val editMode: Boolean = false,
    /** Versão do documento em edição (> 1 = nova versão de um documento já enviado). */
    val versao: Int = 1,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val titulo: String = "",
    val pdf: PickedPdf? = null,
    val pdfError: String? = null,
    val isImporting: Boolean = false,
    val availableSigners: List<User> = emptyList(),
    val participants: List<DraftParticipant> = emptyList(),
    val fields: List<DraftField> = emptyList(),
    val currentPage: Int = 1,
    val activeSignerId: String? = null,
    val selectedFieldId: String? = null,
    val mensagem: String = "",
    /** Id do documento no servidor depois de criado: reenviar reaproveita este id (não duplica). */
    val serverDocumentId: String? = null,
    val isSending: Boolean = false,
    val outcome: SendOutcome? = null,
    val stepError: String? = null
) {
    val pageCount: Int get() = pdf?.info?.pageCount ?: 0
    fun fieldsOnPage(page: Int) = fields.filter { it.page == page }
    fun participantIndex(signerId: String) = participants.indexOfFirst { it.signer.id == signerId }
    val isFinished: Boolean get() = outcome is SendOutcome.Success
}

@HiltViewModel
class NewDocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val signerRepository: SignerRepository,
    private val pdfImporter: PdfImporter
) : ViewModel() {

    // ponytail: rascunho só em memória (sobrevive a rotação, não à morte do processo); salvar no Room se precisar.
    private val local = MutableStateFlow(WizardUiState())

    val uiState: StateFlow<WizardUiState> =
        combine(local, signerRepository.observeSigners()) { state, signers -> state.copy(availableSigners = signers) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WizardUiState())

    private val state get() = local.value

    init {
        viewModelScope.launch { signerRepository.refreshSigners() }
        savedStateHandle.get<String>("documentId")?.let(::loadDraft)
    }

    // ---- Etapa 1: dados ----

    fun setTitle(titulo: String) = local.update { it.copy(titulo = titulo.take(DocumentRules.MAX_TITLE), stepError = null) }

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(isImporting = true, pdfError = null, stepError = null) }
            try {
                val picked = pdfImporter.import(uri)
                local.update { s ->
                    s.copy(
                        isImporting = false,
                        pdf = picked,
                        // Título sugerido a partir do nome do arquivo, se ainda estiver vazio.
                        titulo = s.titulo.ifBlank { picked.displayName.removeSuffix(".pdf").removeSuffix(".PDF") },
                        // Campos de outro PDF podem cair em páginas que não existem: começa do zero.
                        fields = emptyList(),
                        currentPage = 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(isImporting = false, pdfError = e.message ?: "Não foi possível processar o arquivo.") }
            }
        }
    }

    // ---- Etapa 2: signatários ----

    fun toggleParticipant(user: User) = local.update { s ->
        if (s.participants.any { it.signer.id == user.id }) removeFrom(s, user.id)
        else s.copy(participants = s.participants + DraftParticipant(user), stepError = null)
    }

    fun setRole(signerId: String, role: RoleLabel) = updateParticipant(signerId) { it.copy(roleLabel = role) }
    fun setRequired(signerId: String, required: Boolean) = updateParticipant(signerId) { it.copy(required = required) }
    fun removeParticipant(signerId: String) = local.update { removeFrom(it, signerId) }

    /** Troca um assinante por outro mantendo papel, obrigatoriedade e campos já posicionados. */
    fun replaceParticipant(oldSignerId: String, newSigner: User) = local.update { s ->
        if (s.participants.any { it.signer.id == newSigner.id }) return@update s.copy(stepError = "${newSigner.nome} já está na lista.")
        s.copy(
            participants = s.participants.map { if (it.signer.id == oldSignerId) it.copy(signer = newSigner) else it },
            fields = s.fields.map { if (it.signerId == oldSignerId) it.copy(signerId = newSigner.id) else it },
            activeSignerId = if (s.activeSignerId == oldSignerId) newSigner.id else s.activeSignerId,
            stepError = null
        )
    }

    private fun updateParticipant(signerId: String, change: (DraftParticipant) -> DraftParticipant) = local.update { s ->
        s.copy(participants = s.participants.map { if (it.signer.id == signerId) change(it) else it }, stepError = null)
    }

    /** Remover um assinante remove também os campos dele (regra: todo campo pertence a um participante). */
    private fun removeFrom(s: WizardUiState, signerId: String) = s.copy(
        participants = s.participants.filterNot { it.signer.id == signerId },
        fields = s.fields.filterNot { it.signerId == signerId },
        activeSignerId = s.activeSignerId.takeIf { it != signerId },
        stepError = null
    )

    // ---- Etapa 3: campos ----

    fun setPage(page: Int) = local.update { it.copy(currentPage = page.coerceIn(1, it.pageCount.coerceAtLeast(1)), selectedFieldId = null) }
    fun setActiveSigner(signerId: String) = local.update { it.copy(activeSignerId = signerId) }
    fun selectField(localId: String?) = local.update { it.copy(selectedFieldId = localId) }

    fun addField() = local.update { s ->
        val signerId = s.activeSignerId ?: s.participants.firstOrNull()?.signer?.id ?: return@update s
        val field = DocumentRules.newField(signerId, s.currentPage, s.fieldsOnPage(s.currentPage).size)
        s.copy(fields = s.fields + field, selectedFieldId = field.localId, activeSignerId = signerId, stepError = null)
    }

    fun moveField(localId: String, dx: Float, dy: Float) = updateField(localId) { it.moveBy(dx, dy) }
    fun resizeField(localId: String, dw: Float, dh: Float) = updateField(localId) { it.resizeBy(dw, dh) }

    fun deleteField(localId: String) = local.update { s ->
        s.copy(fields = s.fields.filterNot { it.localId == localId }, selectedFieldId = null)
    }

    private fun updateField(localId: String, change: (DraftField) -> DraftField) = local.update { s ->
        s.copy(fields = s.fields.map { if (it.localId == localId) change(it) else it }, selectedFieldId = localId)
    }

    // ---- Etapa 4: revisão e envio ----

    fun setMessage(mensagem: String) = local.update { it.copy(mensagem = mensagem.take(DocumentRules.MAX_MESSAGE)) }

    fun next() {
        val s = state
        validate(s, s.step)?.let { msg ->
            local.update { it.copy(stepError = msg) }
            return
        }
        val nextStep = WizardStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        local.update {
            it.copy(
                step = nextStep,
                stepError = null,
                activeSignerId = it.activeSignerId?.takeIf { id -> it.participants.any { p -> p.signer.id == id } }
                    ?: it.participants.firstOrNull()?.signer?.id
            )
        }
    }

    fun back(): Boolean {
        val previous = WizardStep.entries.getOrNull(state.step.ordinal - 1) ?: return false
        local.update { it.copy(step = previous, stepError = null, outcome = null) }
        return true
    }

    fun goTo(step: WizardStep) {
        // Só volta para etapas anteriores (as seguintes dependem da validação).
        if (step.ordinal < state.step.ordinal) local.update { it.copy(step = step, stepError = null, outcome = null) }
    }

    /**
     * Cria o documento (só na primeira vez) e envia. Em caso de falha, chamar de novo reaproveita
     * o documento já criado e o backend responde "já enviado" se o envio tinha passado:
     * nenhuma solicitação é duplicada.
     */
    fun send() {
        val s = state
        if (s.isSending) return
        val error = WizardStep.entries.firstNotNullOfOrNull { validate(s, it) }
        if (error != null) {
            local.update { it.copy(stepError = error) }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(isSending = true, outcome = null, stepError = null) }
            var documentId = s.serverDocumentId
            if (documentId == null) {
                val created = documentRepository.createDocument(s.titulo.trim(), File(s.pdf!!.path), s.pdf.displayName)
                val doc = created.getOrElse { e ->
                    local.update { it.copy(isSending = false, outcome = SendOutcome.Failure(e.message ?: "Não foi possível criar o documento.")) }
                    return@launch
                }
                documentId = doc.id
                local.update { it.copy(serverDocumentId = doc.id) }
                // O PDF importado foi movido para o armazenamento do documento.
                documentRepository.getPdf(doc.id, PdfKind.ORIGINAL).onSuccess { f ->
                    local.update { it.copy(pdf = it.pdf?.copy(path = f.absolutePath)) }
                }
            }
            val draft = SendDraft(s.participants, s.fields, s.mensagem)
            documentRepository.sendDocument(documentId, draft)
                .onSuccess { r ->
                    local.update { it.copy(isSending = false, outcome = SendOutcome.Success(documentId, r.mensagem, r.jaEnviado)) }
                }
                .onFailure { e ->
                    local.update { it.copy(isSending = false, outcome = SendOutcome.Failure(e.message ?: "Não foi possível enviar.")) }
                }
        }
    }

    private fun validate(s: WizardUiState, step: WizardStep): String? = when (step) {
        WizardStep.DADOS -> DocumentRules.validateTitle(s.titulo) ?: if (s.pdf == null) "Selecione o arquivo PDF." else null
        WizardStep.SIGNATARIOS -> DocumentRules.validateParticipants(s.participants)
        WizardStep.CAMPOS -> DocumentRules.validateFields(s.participants, s.fields, s.pageCount)
        WizardStep.REVISAO -> DocumentRules.validateMessage(s.mensagem)
    }

    // ---- Rascunho existente (rota edit_document/{documentId}) ----

    private fun loadDraft(documentId: String) {
        viewModelScope.launch {
            local.update { it.copy(isLoading = true, editMode = true, serverDocumentId = documentId) }
            val doc = documentRepository.refreshDocument(documentId).getOrElse { e ->
                local.update { it.copy(isLoading = false, loadError = e.message) }
                return@launch
            }
            if (!doc.isDraft) {
                local.update { it.copy(isLoading = false, loadError = "Este documento já foi enviado. Alterações exigem uma nova versão.") }
                return@launch
            }
            val pdf = documentRepository.getPdf(documentId, PdfKind.ORIGINAL)
                .mapCatching { pdfImporter.inspect(it, doc.nomeArquivo) }
                .getOrElse { e ->
                    local.update { it.copy(isLoading = false, loadError = e.message) }
                    return@launch
                }
            local.update {
                it.copy(
                    isLoading = false,
                    titulo = doc.titulo,
                    versao = doc.versao,
                    pdf = pdf,
                    participants = doc.signers.map { p ->
                        DraftParticipant(p.signer ?: User(p.signerId, "Signatário", ""), p.roleLabel, p.required)
                    },
                    fields = doc.fields.map { f -> DraftField(f.id, f.signerId, f.page, f.x, f.y, f.width, f.height) },
                    mensagem = doc.mensagem.orEmpty()
                )
            }
        }
    }
}
