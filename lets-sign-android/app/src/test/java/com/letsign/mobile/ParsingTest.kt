package com.letsign.mobile

import com.letsign.mobile.data.local.db.toDomain
import com.letsign.mobile.data.local.db.toEntity
import com.letsign.mobile.data.repository.NETWORK_ERROR
import com.letsign.mobile.data.repository.SessionExpiredException
import com.letsign.mobile.data.repository.apiCall
import com.letsign.mobile.data.repository.parseApiError
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.data.api.ApiClient
import com.letsign.mobile.presentation.util.formatBytes
import com.letsign.mobile.presentation.util.formatDate
import com.letsign.mobile.presentation.util.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.TimeZone

class ParsingTest {

    private val saoPaulo = TimeZone.getTimeZone("America/Sao_Paulo")

    @Test
    fun `erro do backend vira a mensagem amigavel`() {
        val body = """{"statusCode":401,"mensagem":"E-mail ou senha incorretos.","erros":["E-mail ou senha incorretos."],"caminho":"/auth/login"}"""
        assertEquals("E-mail ou senha incorretos.", parseApiError(body))
        assertNull(parseApiError("<html>502 Bad Gateway</html>"))
        assertNull(parseApiError(null))
    }

    @Test
    fun `apiCall converte respostas em Result`() = runTest {
        val ok = apiCall(Dispatchers.Unconfined, "padrão", { Response.success("x") }) { it.uppercase() }
        assertEquals("X", ok.getOrNull())

        val json = """{"mensagem":"O PDF está corrompido ou não pôde ser processado."}""".toResponseBody("application/json".toMediaType())
        val bad = apiCall<String, String>(Dispatchers.Unconfined, "padrão", { Response.error(400, json) }) { it }
        assertEquals("O PDF está corrompido ou não pôde ser processado.", bad.exceptionOrNull()?.message)

        val expired = apiCall<String, String>(Dispatchers.Unconfined, "padrão", { Response.error(401, "".toResponseBody()) }) { it }
        assertTrue(expired.exceptionOrNull() is SessionExpiredException)

        val offline = apiCall<String, String>(Dispatchers.Unconfined, "padrão", { throw IOException("timeout") }) { it }
        assertEquals(NETWORK_ERROR, offline.exceptionOrNull()?.message)
    }

    @Test
    fun `data ISO em UTC aparece no fuso do aparelho`() {
        // 01:30 UTC do dia 26 = 22:30 do dia 25 em São Paulo
        assertEquals("25/10/2026", formatDate("2026-10-26T01:30:00.000Z", saoPaulo))
        assertEquals("25/10/2026 às 22:30", formatDateTime("2026-10-26T01:30:00.000Z", saoPaulo))
        assertEquals("25/10/2026 às 22:30", formatDateTime("2026-10-26T01:30:00Z", saoPaulo))
        assertEquals("-", formatDate(null))
        assertEquals("-", formatDateTime("lixo"))
    }

    @Test
    fun `tamanho de arquivo legivel`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1,5 KB", formatBytes(1536))
        assertEquals("1,2 MB", formatBytes(1_258_291))
    }

    @Test
    fun `iniciais ignoram espacos extras`() {
        assertEquals("MA", User(id = "1", nome = " Marina   Albuquerque Souza ", email = "m@x.com").initials)
        assertEquals("", User(id = "1", nome = "   ", email = "m@x.com").initials)
    }

    @Test
    fun `json da API com campos desconhecidos e enum novo nao quebra o app`() {
        val json = """{"id":"d1","ownerId":"u1","titulo":"T","status":"COMPLETED","campoNovo":123,
            "events":[{"id":"e1","tipo":"TIPO_QUE_NAO_EXISTE","descricao":"x","timestamp":"2026-10-25T10:00:00.000Z"}]}"""
        val doc = ApiClient.json.decodeFromString(Document.serializer(), json)
        assertEquals(DocumentStatus.COMPLETED, doc.status)
        assertEquals(AuditType.OTHER, doc.events.single().tipo)
    }

    @Test
    fun `documento sobrevive a ida e volta pelo banco local`() {
        val doc = Document(
            id = "d1", ownerId = "u1", titulo = "Contrato", status = DocumentStatus.PARTIALLY_SIGNED, versao = 2,
            papel = "SIGNER", minhaSituacao = SignerStatus.VIEWED, owner = User("u1", "Marina", "")
        )
        val back = doc.toEntity(userId = "eu").toDomain()
        assertEquals(doc.titulo, back.titulo)
        assertEquals(doc.status, back.status)
        assertEquals(doc.versao, back.versao)
        assertEquals(SignerStatus.VIEWED, back.minhaSituacao)
        assertEquals("Marina", back.owner?.nome)
        assertTrue(back.canSign) // parcial + visualizado + papel SIGNER = ainda pode assinar
    }

    @Test
    fun `status desconhecido no banco cai num padrao seguro`() {
        val entity = Document(id = "d", ownerId = "o", titulo = "t").toEntity("u").copy(status = "STATUS_FUTURO")
        val domain = entity.toDomain()
        assertEquals(DocumentStatus.DRAFT, domain.status)
    }
}
