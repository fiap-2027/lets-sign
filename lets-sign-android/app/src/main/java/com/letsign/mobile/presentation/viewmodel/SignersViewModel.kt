package com.letsign.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.SignerRepository
import com.letsign.mobile.domain.rules.DocumentRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Formulário aberto: [editing] null = novo signatário. */
data class SignerForm(val editing: User? = null, val error: String? = null, val saving: Boolean = false)

data class SignersUiState(
    val signers: List<User> = emptyList(),
    val query: String = "",
    val isRefreshing: Boolean = false,
    val form: SignerForm? = null,
    val confirmDelete: User? = null,
    val error: String? = null,
    val message: String? = null
) {
    val visible: List<User>
        get() = if (query.isBlank()) signers else signers.filter {
            it.nome.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
        }
}

@HiltViewModel
class SignersViewModel @Inject constructor(
    private val signerRepository: SignerRepository
) : ViewModel() {

    private val local = MutableStateFlow(SignersUiState())

    val uiState: StateFlow<SignersUiState> =
        combine(local, signerRepository.observeSigners()) { state, signers -> state.copy(signers = signers) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignersUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true, error = null) }
            val result = signerRepository.refreshSigners()
            local.update { it.copy(isRefreshing = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun setQuery(query: String) = local.update { it.copy(query = query) }
    fun openNew() = local.update { it.copy(form = SignerForm()) }
    fun openEdit(user: User) = local.update { it.copy(form = SignerForm(editing = user)) }
    fun closeForm() = local.update { it.copy(form = null) }

    fun save(nome: String, email: String, telefone: String, senha: String) {
        val form = local.value.form ?: return
        val isNew = form.editing == null
        DocumentRules.validateSigner(nome, email, telefone, senha, isNew)?.let { msg ->
            local.update { it.copy(form = form.copy(error = msg)) }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(form = form.copy(saving = true, error = null)) }
            val result = if (isNew) signerRepository.createSigner(nome, email, telefone, senha)
            else signerRepository.updateSigner(form.editing.id, nome, email, telefone, senha.ifEmpty { null })
            result
                .onSuccess { local.update { it.copy(form = null, message = if (isNew) "Signatário cadastrado." else "Signatário atualizado.") } }
                .onFailure { e -> local.update { it.copy(form = form.copy(saving = false, error = e.message)) } }
        }
    }

    fun askDelete(user: User) = local.update { it.copy(confirmDelete = user) }
    fun cancelDelete() = local.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val user = local.value.confirmDelete ?: return
        viewModelScope.launch {
            local.update { it.copy(confirmDelete = null) }
            signerRepository.deleteSigner(user.id)
                .onSuccess { local.update { it.copy(message = "Signatário removido.") } }
                .onFailure { e -> local.update { it.copy(error = e.message) } }
        }
    }

    fun consumeMessages() = local.update { it.copy(error = null, message = null) }
}
