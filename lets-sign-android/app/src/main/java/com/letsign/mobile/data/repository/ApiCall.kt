package com.letsign.mobile.data.repository

import com.letsign.mobile.data.api.ApiClient
import com.letsign.mobile.data.api.ApiError
import com.letsign.mobile.data.pdf.PdfInvalidException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executa a chamada no [dispatcher] e converte em [Result].
 * Erros HTTP viram a `mensagem` amigável do backend; falhas de rede viram texto em PT-BR.
 * [map] roda no mesmo dispatcher (é onde os repositórios gravam no banco local).
 */
internal suspend fun <T, R> apiCall(
    dispatcher: CoroutineDispatcher,
    defaultError: String,
    call: suspend () -> Response<T>,
    map: suspend (T) -> R
): Result<R> = withContext(dispatcher) {
    try {
        val response = call()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> Result.success(map(body))
            response.code() == 401 -> Result.failure(SessionExpiredException())
            else -> Result.failure(Exception(parseApiError(response.errorBody()?.string()) ?: defaultError))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: PdfInvalidException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(Exception(NETWORK_ERROR, e))
    } catch (e: Exception) {
        Result.failure(Exception(defaultError, e))
    }
}

internal fun parseApiError(body: String?): String? =
    body?.let { runCatching { ApiClient.json.decodeFromString(ApiError.serializer(), it) }.getOrNull()?.mensagem }

const val NETWORK_ERROR = "Erro de conexão. Verifique sua internet."

class SessionExpiredException : Exception("Sua sessão expirou. Entre novamente.")

class NotLoggedInException : Exception("Sessão não iniciada. Entre novamente.")
