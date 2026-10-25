package com.letsign.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.letsign.mobile.data.api.ApiClient
import com.letsign.mobile.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: token em SharedPreferences sem criptografia (excluído do backup); ver MELHORIAS.md, item de segurança.
@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(readUser())

    /** Usuário logado (null = sem sessão). Os repositórios filtram o banco local por ele. */
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite quando a API responde 401 para uma requisição autenticada (token expirado/inválido). */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun saveSession(accessToken: String, user: User) {
        sharedPrefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_USER_DATA, ApiClient.json.encodeToString(User.serializer(), user))
        }
        _currentUser.value = user
    }

    fun getAccessToken(): String? = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun currentUserId(): String? = _currentUser.value?.id

    fun isAuthenticated(): Boolean = getAccessToken() != null && _currentUser.value != null

    fun clearTokens() {
        sharedPrefs.edit { clear() }
        _currentUser.value = null
    }

    /** Chamado pelo interceptor HTTP; quem escuta [sessionExpired] limpa os dados e volta ao login. */
    fun onUnauthorized() {
        if (getAccessToken() == null) return
        clearTokens()
        _sessionExpired.tryEmit(Unit)
    }

    private fun readUser(): User? =
        if (sharedPrefs.getString(KEY_ACCESS_TOKEN, null) == null) null
        else sharedPrefs.getString(KEY_USER_DATA, null)
            ?.let { runCatching { ApiClient.json.decodeFromString(User.serializer(), it) }.getOrNull() }

    private companion object {
        // Nome do arquivo referenciado em res/xml/backup_rules.xml e data_extraction_rules.xml.
        const val PREF_NAME = "lets_sign_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_DATA = "user_data"
    }
}
