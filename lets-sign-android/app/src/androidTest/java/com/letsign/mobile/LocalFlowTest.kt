package com.letsign.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letsign.mobile.data.local.LocalAuthRepository
import com.letsign.mobile.data.local.LocalDocumentRepository
import com.letsign.mobile.data.local.LocalSignerRepository
import com.letsign.mobile.data.local.TokenManager
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.pdf.PdfDocumentRenderer
import com.letsign.mobile.data.pdf.PdfStamper
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Fluxo completo do modo local (sem servidor): Room em memória, PDF real e PDF final gerado pelo PDFBox. */
@RunWith(AndroidJUnit4::class)
class LocalFlowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var tokens: TokenManager
    private lateinit var auth: LocalAuthRepository
    private lateinit var signers: LocalSignerRepository
    private lateinit var docs: LocalDocumentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tokens = TokenManager(context).also { it.clearTokens() }
        auth = LocalAuthRepository(db.userDao(), tokens)
        signers = LocalSignerRepository(db, tokens)
        docs = LocalDocumentRepository(db, tokens, PdfStorage(context), PdfStamper(context))
    }

    @After
    fun tearDown() {
        tokens.clearTokens()
        db.close()
    }

    @Test
    fun fluxoCompletoAteDocumentoFinal() = runBlocking {
        val admin = auth.register("Ana Admin", "ana@teste.com", null, "123456").getOrThrow()
        assertTrue(admin.isAdmin)
        assertTrue("e-mail repetido", auth.register("Outra", "ANA@teste.com", null, "123456").isFailure)
        val joao = signers.createSigner("João Silva", "joao@teste.com", "11999998888", "senha1").getOrThrow()
        val maria = signers.createSigner("Maria Souza", "maria@teste.com", "11999997777", "senha2").getOrThrow()
        val doc = docs.createDocument("Contrato", pdf(2), "contrato.pdf").getOrThrow()
        assertEquals(DocumentStatus.DRAFT, doc.status)
        assertEquals(2, doc.totalPaginas)

        // Regra: dados de um usuário não são acessados por outro antes do envio.
        loginAs("joao@teste.com", "senha1")
        assertTrue(docs.observeDocuments().first().isEmpty())
        assertTrue(docs.getPdf(doc.id, PdfKind.ORIGINAL).isFailure)

        auth.logout()
        assertTrue("senha errada", auth.login("ana@teste.com", "errada").isFailure)
        loginAs("ana@teste.com", "123456")
        val draft = draftFor(joao, maria)
        assertFalse(docs.sendDocument(doc.id, draft).getOrThrow().jaEnviado)
        assertTrue("reenvio não duplica", docs.sendDocument(doc.id, draft).getOrThrow().jaEnviado)
        assertEquals(2, docs.refreshDocument(doc.id).getOrThrow().signers.size)
        assertTrue("signatário vinculado não é removido", signers.deleteSigner(joao.id).isFailure)

        loginAs("joao@teste.com", "senha1")
        assertEquals(1, docs.observeDocuments().first().size)
        val visto = docs.refreshDocument(doc.id).getOrThrow()
        assertEquals(SignerStatus.VIEWED, visto.minhaSituacao)
        assertEquals("só os próprios campos", 1, visto.fields.size)
        assertEquals(DocumentStatus.PARTIALLY_SIGNED, docs.signDocument(doc.id, png(), null).getOrThrow().document.status)
        assertTrue("não assina duas vezes", docs.signDocument(doc.id, png(), null).isFailure)
        assertTrue("final só depois de concluir", docs.getPdf(doc.id, PdfKind.FINAL).isFailure)

        loginAs("maria@teste.com", "senha2")
        val concluido = docs.signDocument(doc.id, png(), null).getOrThrow().document
        assertEquals(DocumentStatus.COMPLETED, concluido.status)
        val final = docs.getPdf(doc.id, PdfKind.FINAL).getOrThrow()
        val original = docs.getPdf(doc.id, PdfKind.ORIGINAL).getOrThrow()
        assertEquals(2, PdfDocumentRenderer.inspect(final).pageCount)
        assertTrue("assinaturas embutidas no PDF final", final.length() > original.length())

        loginAs("ana@teste.com", "123456")
        val visaoAdmin = docs.refreshDocument(doc.id).getOrThrow()
        assertTrue(visaoAdmin.events.any { it.tipo == AuditType.COMPLETED })
        assertTrue(visaoAdmin.signers.all { it.status == SignerStatus.SIGNED && it.signedAt != null })
        assertTrue("concluído não ganha nova versão", docs.newVersion(doc.id, pdf(1), "v2.pdf").isFailure)
    }

    @Test
    fun novaVersaoPreservaAnteriorEIniciaNovoFluxo() = runBlocking {
        auth.register("Ana Admin", "ana2@teste.com", null, "123456").getOrThrow()
        val joao = signers.createSigner("João Silva", "joao2@teste.com", "11999998888", "senha1").getOrThrow()
        val maria = signers.createSigner("Maria Souza", "maria2@teste.com", "11999997777", "senha2").getOrThrow()
        val v1 = docs.createDocument("Contrato", pdf(2), "contrato.pdf").getOrThrow()
        docs.sendDocument(v1.id, draftFor(joao, maria)).getOrThrow()
        loginAs("joao2@teste.com", "senha1")
        docs.signDocument(v1.id, png(), null).getOrThrow()

        loginAs("ana2@teste.com", "123456")
        val v2 = docs.newVersion(v1.id, pdf(1), "contrato-v2.pdf").getOrThrow()
        assertTrue(v2.id != v1.id)
        assertEquals(2, v2.versao)
        assertEquals(DocumentStatus.DRAFT, v2.status)
        assertEquals("signatários copiados e zerados", listOf(SignerStatus.PENDING, SignerStatus.PENDING), v2.signers.map { it.status })
        assertEquals("campo da página 2 não existe no PDF novo", 1, v2.fields.size)

        val antigo = docs.refreshDocument(v1.id).getOrThrow()
        assertEquals(DocumentStatus.CANCELLED, antigo.status)
        assertEquals("assinatura da versão 1 preservada", SignerStatus.SIGNED, antigo.signers.first { it.signerId == joao.id }.status)

        loginAs("maria2@teste.com", "senha2")
        assertTrue("versão antiga não pode mais ser assinada", docs.signDocument(v1.id, png(), null).isFailure)
        assertTrue("rascunho da v2 ainda não aparece", docs.getPdf(v2.id, PdfKind.ORIGINAL).isFailure)
    }

    private suspend fun loginAs(email: String, senha: String) {
        auth.logout()
        auth.login(email, senha).getOrThrow()
    }

    private fun draftFor(a: User, b: User) = SendDraft(
        participants = listOf(DraftParticipant(a, RoleLabel.CONTRATANTE), DraftParticipant(b, RoleLabel.CONTRATADO)),
        fields = listOf(
            DraftField("1", a.id, 1, 0.1f, 0.7f, 0.3f, 0.08f),
            DraftField("2", b.id, 2, 0.5f, 0.7f, 0.3f, 0.08f)
        ),
        mensagem = "Assine, por favor."
    )

    private fun pdf(pages: Int): File {
        val doc = PdfDocument()
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            page.canvas.drawText("Página ${i + 1}", 50f, 50f, Paint())
            doc.finishPage(page)
        }
        val file = File.createTempFile("teste", ".pdf", context.cacheDir)
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    private fun png(): String {
        val bitmap = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawLine(10f, 60f, 190f, 20f, Paint().apply { strokeWidth = 4f })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
