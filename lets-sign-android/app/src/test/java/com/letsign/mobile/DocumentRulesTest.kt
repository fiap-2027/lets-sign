package com.letsign.mobile

import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.rules.DocumentRules
import com.letsign.mobile.domain.rules.isInsidePage
import com.letsign.mobile.domain.rules.moveBy
import com.letsign.mobile.domain.rules.resizeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras de negócio do desafio validadas no app antes do envio. */
class DocumentRulesTest {

    private val ana = User("u-ana", "Ana Costa", "ana@x.com")
    private val joao = User("u-joao", "João Pereira", "joao@x.com")
    private fun field(signer: User, page: Int = 1, x: Float = 0.1f, y: Float = 0.1f, w: Float = 0.3f, h: Float = 0.08f) =
        DraftField("f-${signer.id}-$page-$x", signer.id, page, x, y, w, h)

    @Test
    fun `titulo obrigatorio e com limite de 200 caracteres`() {
        assertNotNull(DocumentRules.validateTitle("   "))
        assertNotNull(DocumentRules.validateTitle("a".repeat(201)))
        assertNull(DocumentRules.validateTitle("Contrato"))
    }

    @Test
    fun `documento precisa de pelo menos um signatario obrigatorio`() {
        assertEquals("Selecione pelo menos um signatário.", DocumentRules.validateParticipants(emptyList()))
        assertNotNull(DocumentRules.validateParticipants(listOf(DraftParticipant(ana, required = false))))
        assertNotNull(DocumentRules.validateParticipants(listOf(DraftParticipant(ana), DraftParticipant(ana))))
        assertNull(DocumentRules.validateParticipants(listOf(DraftParticipant(ana), DraftParticipant(joao, required = false))))
    }

    @Test
    fun `todo campo pertence a um participante e todo participante tem campo`() {
        val participants = listOf(DraftParticipant(ana), DraftParticipant(joao))
        // João sem campo
        assertEquals("Posicione pelo menos um campo para João Pereira.", DocumentRules.validateFields(participants, listOf(field(ana)), 2))
        // campo de alguém que não é participante
        val intruso = User("u-x", "Intruso", "x@x.com")
        assertNotNull(DocumentRules.validateFields(participants, listOf(field(ana), field(joao), field(intruso)), 2))
        // página inexistente
        assertNotNull(DocumentRules.validateFields(participants, listOf(field(ana), field(joao, page = 3)), 2))
        // mais de um campo para o mesmo signatário é permitido
        assertNull(DocumentRules.validateFields(participants, listOf(field(ana), field(ana, page = 2), field(joao)), 2))
    }

    @Test
    fun `campo fora da pagina e recusado`() {
        val participants = listOf(DraftParticipant(ana))
        assertEquals(
            "Existe campo de assinatura fora dos limites da página.",
            DocumentRules.validateFields(participants, listOf(field(ana, x = 0.8f, w = 0.3f)), 1)
        )
    }

    @Test
    fun `arrastar e redimensionar nunca deixam o campo sair da pagina`() {
        val f = field(ana, x = 0.6f, y = 0.85f, w = 0.3f, h = 0.08f)
        val moved = f.moveBy(1f, 1f)
        assertEquals(0.7f, moved.x, 1e-4f)
        assertEquals(0.92f, moved.y, 1e-4f)
        assertTrue(moved.isInsidePage())
        assertEquals(0f, f.moveBy(-5f, -5f).x, 0f)

        val grown = f.resizeBy(1f, 1f)
        assertEquals(0.4f, grown.width, 1e-4f) // limitado pela borda direita (1 - 0.6)
        assertTrue(grown.isInsidePage())
        val shrunk = f.resizeBy(-1f, -1f)
        assertEquals(DocumentRules.MIN_FIELD_WIDTH, shrunk.width, 0f)
        assertEquals(DocumentRules.MIN_FIELD_HEIGHT, shrunk.height, 0f)
    }

    @Test
    fun `campo novo nasce dentro da pagina mesmo com muitos campos`() {
        repeat(20) { n -> assertTrue(DocumentRules.newField("s", 1, n).isInsidePage()) }
    }

    @Test
    fun `signatario exige nome, email, telefone e senha`() {
        assertNotNull(DocumentRules.validateSigner("", "a@b.com", "11999998888", "123456", isNew = true))
        assertNotNull(DocumentRules.validateSigner("Ana", "email-invalido", "11999998888", "123456", isNew = true))
        assertNotNull(DocumentRules.validateSigner("Ana", "a@b.com", "9999", "123456", isNew = true))
        assertNotNull(DocumentRules.validateSigner("Ana", "a@b.com", "(11) 99999-8888", "123", isNew = true))
        assertNull(DocumentRules.validateSigner("Ana", "a@b.com", "(11) 99999-8888", "123456", isNew = true))
        // na edição, senha vazia = manter a atual
        assertNull(DocumentRules.validateSigner("Ana", "a@b.com", "(11) 99999-8888", "", isNew = false))
    }

    @Test
    fun `so o dono cria nova versao, e so de documento enviado e nao concluido`() {
        val doc = Document(id = "d", ownerId = "o", titulo = "t", papel = "OWNER")
        assertTrue(doc.copy(status = DocumentStatus.PENDING).canCreateNewVersion)
        assertTrue(doc.copy(status = DocumentStatus.PARTIALLY_SIGNED).canCreateNewVersion)
        assertTrue(doc.copy(status = DocumentStatus.CANCELLED).canCreateNewVersion)
        assertFalse(doc.copy(status = DocumentStatus.DRAFT).canCreateNewVersion) // rascunho ainda pode ser editado
        assertFalse(doc.copy(status = DocumentStatus.COMPLETED).canCreateNewVersion) // concluído é imutável
        assertFalse(doc.copy(status = DocumentStatus.PENDING, papel = "SIGNER").canCreateNewVersion)
    }

    @Test
    fun `mensagem do convite tem limite de 2000 caracteres`() {
        assertNull(DocumentRules.validateMessage("a".repeat(2000)))
        assertNotNull(DocumentRules.validateMessage("a".repeat(2001)))
    }
}
