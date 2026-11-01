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
import com.letsign.mobile.domain.model.FieldType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
        docs = LocalDocumentRepository(db, tokens, PdfStorage(context), PdfStamper(context), com.letsign.mobile.data.pdf.PdfTextIndex(context))
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

    @Test
    fun camposExtrasObservadorModeloGrupoELembrete() = runBlocking {
        auth.register("Ana Admin", "ana3@teste.com", null, "123456").getOrThrow()
        assertTrue("CPF inválido é recusado", signers.createSigner("X", "x3@teste.com", "11999990000", "senha1", "123.456.789-00").isFailure)
        val joao = signers.createSigner("João Silva", "joao3@teste.com", "11999998888", "senha1", "529.982.247-25", "13/07/1992").getOrThrow()
        assertEquals("52998224725", joao.cpf)
        assertEquals("13/07/1992", joao.dataNascimento)
        val bia = signers.createSigner("Bia Lima", "bia3@teste.com", "11999996666", "senha3").getOrThrow()
        assertEquals(2, signers.saveGroup(null, "Diretoria", listOf(joao.id, bia.id)).getOrThrow().memberIds.size)

        val doc = docs.createDocument("Contrato", pdf(1), "contrato.pdf").getOrThrow()
        val organizado = docs.updateOrganization(doc.id, "Jurídico", listOf("Contratos", "RH")).getOrThrow()
        assertEquals("Jurídico", organizado.pasta)
        assertEquals(listOf("Contratos", "RH"), organizado.categorias)

        val fields = FieldType.entries.mapIndexed { i, t -> DraftField("$i", joao.id, 1, 0.05f, 0.05f + i * 0.12f, 0.4f, 0.08f, t) }
        docs.sendDocument(doc.id, SendDraft(listOf(DraftParticipant(joao)), fields, null)).getOrThrow()
        assertEquals(listOf(bia.id), docs.setObservers(doc.id, listOf(bia.id)).getOrThrow().observadores.map { it.id })
        assertTrue("signatário não vira observador", docs.setObservers(doc.id, listOf(joao.id)).isFailure)
        assertTrue(docs.sendReminder(doc.id).isSuccess)
        val modelo = docs.saveAsTemplate(doc.id, "Contrato padrão").getOrThrow()

        loginAs("bia3@teste.com", "senha3")
        val visaoBia = docs.refreshDocument(doc.id).getOrThrow()
        assertTrue(visaoBia.isObserver)
        assertFalse("observador não assina", visaoBia.canSign)
        assertTrue("observador não vê campos", visaoBia.fields.isEmpty())
        assertEquals(1, docs.observeDocuments().first().size)
        assertTrue("observador não assina", docs.signDocument(doc.id, png(), null).isFailure)

        loginAs("joao3@teste.com", "senha1")
        assertEquals(DocumentStatus.COMPLETED, docs.signDocument(doc.id, png(), null, png()).getOrThrow().document.status)
        val final = docs.getPdf(doc.id, PdfKind.FINAL).getOrThrow()
        PDFBoxResourceLoader.init(context)
        val texto = PDDocument.load(final).use { PDFTextStripper().getText(it) }
        listOf("529.982.247-25", "João Silva", "joao3@teste.com", "ASSINADO ELETRONICAMENTE").forEach {
            assertTrue("PDF final contém \"$it\"", texto.contains(it))
        }

        loginAs("bia3@teste.com", "senha3")
        assertTrue("observador baixa o final", docs.getPdf(doc.id, PdfKind.FINAL).isSuccess)

        loginAs("ana3@teste.com", "123456")
        assertTrue(docs.refreshDocument(doc.id).getOrThrow().events.any { it.tipo == AuditType.REMINDER })
        val copia = docs.createFromTemplate(modelo.id).getOrThrow()
        assertEquals(DocumentStatus.DRAFT, copia.status)
        assertEquals("Contrato padrão", copia.titulo)
        assertEquals("Jurídico", copia.pasta)
        assertEquals(FieldType.entries.toSet(), copia.fields.map { it.tipo }.toSet())
        assertEquals(SignerStatus.PENDING, copia.signers.single().status)
    }

    /** Modelo inteligente: um NDA novo, maior, recebe os campos pelo texto âncora; um recibo não é reconhecido. */
    @Test
    fun modeloInteligentePosicionaCamposEmPdfNovo() = runBlocking {
        auth.register("Ana Admin", "ana4@teste.com", null, "123456").getOrThrow()
        val joao = signers.createSigner("João Silva", "joao4@teste.com", "11999998888", "senha1").getOrThrow()
        val corpo = listOf(
            "ACORDO DE CONFIDENCIALIDADE",
            "As partes concordam em manter sigilo sobre as informações",
            "confidenciais trocadas durante a vigência deste acordo,",
            "sob pena de multa contratual e demais sanções legais."
        )
        // v1: 2 páginas, bloco de assinatura na página 2 em y = 500 pt.
        val original = textPdf(listOf(corpo.mapIndexed { i, t -> 80f + i * 30f to t }, listOf(500f to "CONTRATANTE")))
        val doc = docs.createDocument("NDA Ana", original, "nda.pdf").getOrThrow()
        val campoY = 520f / 842f
        docs.sendDocument(doc.id, SendDraft(listOf(DraftParticipant(joao)), listOf(DraftField("1", joao.id, 2, 0.1f, campoY, 0.3f, 0.08f)), null)).getOrThrow()
        docs.saveAsTemplate(doc.id, "NDA padrão").getOrThrow()

        // NDA de outro cliente: uma página a mais e o bloco de assinatura na página 3, 100 pt mais abaixo.
        val novo = textPdf(listOf(
            corpo.mapIndexed { i, t -> 80f + i * 30f to t },
            listOf(80f to "Cláusula adicional sobre a vigência do acordo"),
            listOf(600f to "CONTRATANTE")
        ))
        val match = docs.matchTemplate(novo).getOrThrow()
        assertTrue("NDA reconhecido", match != null && match.score >= com.letsign.mobile.domain.rules.TemplateMatcher.MIN_SCORE)
        val campo = match!!.fields.single()
        assertEquals("NDA padrão", match.template.nome)
        assertEquals(joao.id, campo.signerId)
        assertEquals(1, match.anchored)
        assertEquals("seguiu o texto para a página 3", 3, campo.page)
        assertEquals("desceu junto com o texto", campoY + 100f / 842f, campo.y, 0.02f)

        val recibo = textPdf(listOf(listOf(80f to "RECIBO DE PAGAMENTO do aluguel referente ao mês de outubro")))
        assertEquals("recibo não é reconhecido", null, docs.matchTemplate(recibo).getOrThrow())
        assertTrue("aplicar à força funciona", docs.matchTemplate(recibo, match.template.id).getOrThrow() != null)
    }

    /** PDF com linhas de texto de verdade (y em pontos a partir do topo, página A4). */
    private fun textPdf(pages: List<List<Pair<Float, String>>>): File {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        pages.forEachIndexed { i, lines ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            lines.forEach { (y, text) -> page.canvas.drawText(text, 60f, y, paint) }
            doc.finishPage(page)
        }
        val file = File.createTempFile("texto", ".pdf", context.cacheDir)
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    /** .docx de verdade (ZIP com word/document.xml) vira PDF de várias páginas no aparelho. */
    @Test
    fun docxViraPdf() {
        val body = (1..120).joinToString("") { "<w:p><w:r><w:t>Cláusula $it: texto do contrato para testar a quebra de página.</w:t></w:r></w:p>" }
        val xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>"
        val docx = File.createTempFile("teste", ".docx", context.cacheDir)
        java.util.zip.ZipOutputStream(docx.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            z.write(xml.toByteArray())
            z.closeEntry()
        }
        val pdf = File.createTempFile("convertido", ".pdf", context.cacheDir)
        com.letsign.mobile.data.pdf.DocxConverter.writePdf(docx.inputStream().use(com.letsign.mobile.data.pdf.DocxConverter::paragraphs), pdf)
        assertTrue("120 parágrafos ocupam mais de uma página", PdfDocumentRenderer.inspect(pdf).pageCount > 1)
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
