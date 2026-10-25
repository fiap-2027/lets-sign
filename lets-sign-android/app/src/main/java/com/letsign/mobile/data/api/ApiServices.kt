package com.letsign.mobile.data.api

import com.letsign.mobile.domain.model.AuthTokens
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// Contrato espelhado de letssign_backend/nodejs_space (NestJS). Swagger: http://localhost:3000/api

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthTokens>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthTokens>
}

@Serializable
data class LoginRequest(
    val email: String,
    val senha: String
)

@Serializable
data class RegisterRequest(
    val nome: String,
    val email: String,
    val telefone: String?,
    val senha: String
)

interface DocumentsApi {
    /** Admin: cria o documento em rascunho a partir do PDF (multipart `pdf` + `titulo`). */
    @Multipart
    @POST("documents")
    suspend fun createDocument(
        @Part pdf: MultipartBody.Part,
        @Part("titulo") titulo: RequestBody
    ): Response<Document>

    /**
     * Admin: substitui o PDF de um documento não concluído. O backend preserva a versão anterior,
     * mantém os participantes (com assinaturas zeradas), mantém os campos das páginas que ainda existem
     * e volta o documento para rascunho (novo fluxo).
     */
    @Multipart
    @POST("documents/{id}/version")
    suspend fun newVersion(
        @Path("id") id: String,
        @Part pdf: MultipartBody.Part
    ): Response<Document>

    /** Admin: documentos que criou. Signatário: documentos para os quais foi convidado. */
    @GET("documents")
    suspend fun listDocuments(): Response<List<Document>>

    /** Detalhe com participantes, campos e eventos de auditoria. Para o signatário, marca como visualizado. */
    @GET("documents/{id}")
    suspend fun getDocument(@Path("id") id: String): Response<Document>

    @GET("documents/{id}/file")
    suspend fun downloadPdf(
        @Path("id") id: String,
        @Query("tipo") tipo: String
    ): Response<ResponseBody>

    /** Define participantes e campos e envia. Idempotente: repetir não duplica as solicitações. */
    @POST("documents/{id}/send")
    suspend fun sendDocument(
        @Path("id") id: String,
        @Body body: SendRequest
    ): Response<SendResponse>

    @POST("documents/{id}/sign")
    suspend fun signDocument(
        @Path("id") id: String,
        @Body body: SignRequest
    ): Response<ActionResponse>

    @POST("documents/{id}/reject")
    suspend fun rejectDocument(
        @Path("id") id: String,
        @Body body: RejectRequest
    ): Response<ActionResponse>
}

@Serializable
data class ParticipantRequest(
    val signerId: String,
    val roleLabel: String,
    val required: Boolean = true
)

/** Coordenadas normalizadas de 0 a 1 (x/y a partir do canto superior esquerdo); página começa em 1. */
@Serializable
data class FieldRequest(
    val signerId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val required: Boolean = true,
    val roleLabel: String? = null
)

@Serializable
data class SendRequest(
    val mensagem: String? = null,
    val participantes: List<ParticipantRequest>? = null,
    val fields: List<FieldRequest>? = null
)

@Serializable
data class SendResponse(
    val jaEnviado: Boolean,
    val mensagem: String,
    val documento: Document
)

@Serializable
data class SignRequest(
    val assinaturaImagemBase64: String,
    val gestureMetadata: JsonObject? = null
)

@Serializable
data class RejectRequest(
    val motivo: String
)

/** Resposta de /sign e /reject. */
@Serializable
data class ActionResponse(
    val mensagem: String,
    val documento: Document
)

/** Corpo de erro padrão do backend (GlobalExceptionFilter). */
@Serializable
data class ApiError(
    val mensagem: String? = null,
    val erros: List<String> = emptyList()
)

interface SignersApi {
    @GET("signers")
    suspend fun listSigners(): Response<List<User>>

    @POST("signers")
    suspend fun createSigner(@Body body: SignerRequest): Response<User>

    @PATCH("signers/{id}")
    suspend fun updateSigner(@Path("id") id: String, @Body body: SignerRequest): Response<User>

    @DELETE("signers/{id}")
    suspend fun deleteSigner(@Path("id") id: String): Response<Unit>
}

/** [senha] nula no PATCH = manter a senha atual (o campo é omitido do JSON). */
@Serializable
data class SignerRequest(
    val nome: String,
    val email: String,
    val telefone: String?,
    val senha: String? = null
)
