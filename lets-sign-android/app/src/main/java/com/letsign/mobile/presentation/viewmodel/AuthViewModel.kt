package com.letsign.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsign.mobile.data.local.DemoData
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.rules.DocumentRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sessão no nível do app: quem está logado, logout e sessão expirada (401). Usado pelo AppNavHost. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser: StateFlow<User?> = authRepository.currentUser

    private val _notice = MutableStateFlow<String?>(null)

    /** Aviso para mostrar na tela de login (ex.: "Sua sessão expirou"). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionExpired.collect {
                authRepository.logout()
                _notice.value = "Sua sessão expirou. Entre novamente."
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun consumeNotice() {
        _notice.value = null
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val demoData: DemoData
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUser.value)

    data class AuthUiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )

    fun login(email: String, senha: String) {
        if (email.isBlank() || senha.isBlank()) {
            setError("Informe e-mail e senha.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.login(email, senha)
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Erro ao fazer login") } }
        }
    }

    fun register(nome: String, email: String, telefone: String?, senha: String) {
        DocumentRules.validateAccount(nome, email, senha)?.let {
            setError(it)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.register(nome, email, telefone, senha)
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Erro ao cadastrar") } }
        }
    }

    /** Cria (uma vez) os dados de demonstração e entra como o Admin da demonstração. */
    fun loadDemo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            demoData.load()
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = "Não foi possível criar a demonstração: ${e.message}") } }
        }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}
