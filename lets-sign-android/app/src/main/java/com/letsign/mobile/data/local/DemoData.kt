package com.letsign.mobile.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.pdf.PdfStamper
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.data.pdf.PdfTextIndex
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SendDraft
import com.letsign.mobile.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

/**
 * Dados de demonstração (só no modo local): um Admin, 6 contatos e 17 documentos que passam por todos os estágios
 * (rascunho, enviado, visualizado, parcialmente assinado, concluído, recusado e substituído por nova versão).
 * Tudo é criado pelo fluxo real dos repositórios, então PDFs, hashes, PDF final e auditoria são verdadeiros.
 * Depois as datas são espalhadas pelos últimos 40 dias.
 * Soma ~1.250 folhas poupadas no painel ESG (nível Árvore).
 */
// ponytail: o PDF final é carimbado com a data real da geração; as datas do app são recuadas depois. Só afeta a demonstração.
class DemoData @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    pdfStorage: PdfStorage,
    stamper: PdfStamper,
    textIndex: PdfTextIndex
) {
    // Sessão própria: trocar de usuário durante a criação não mexe na tela; o app só entra como Admin no final.
    private val sessao = TokenManager(context)
    private val auth = LocalAuthRepository(database.userDao(), sessao)
    private val contacts = LocalSignerRepository(database, sessao)
    private val docs = LocalDocumentRepository(database, sessao, pdfStorage, stamper, textIndex)

    private enum class Fim { RASCUNHO, ENVIADO, VISUALIZADO, PARCIAL, CONCLUIDO, RECUSADO, NOVA_VERSAO }

    /** [quem] = índices dos contatos; o primeiro é o Contratante. [opcional] = índice (em [quem]) de quem não é obrigatório. */
    private class Spec(
        val titulo: String, val paginas: Int, val quem: List<Int>, val fim: Fim, val diasAtras: Int,
        val pasta: String = "Documentos", val categorias: List<String> = emptyList(),
        val opcional: Int? = null, val camposExtras: Boolean = false
    )

    private val pessoas = listOf(
        Triple("João Pereira", "joao@demo.com", "52601815906"),
        Triple("Mariana Lopes", "mariana@demo.com", "08301661305"),
        Triple("Rafael Costa", "rafael@demo.com", "18609139034"),
        Triple("Beatriz Nunes", "beatriz@demo.com", "99603082430"),
        Triple("Lucas Almeida", "lucas@demo.com", "62819482112"),
        Triple("Fernanda Ribeiro", "fernanda@demo.com", "99351819019")
    )

    private val specs = listOf(
        Spec("Contrato de prestação de serviços de TI", 12, listOf(0, 1), Fim.CONCLUIDO, 40, "Jurídico", listOf("Contratos"), camposExtras = true),
        Spec("Contrato de locação comercial – Sala 1204", 24, listOf(1, 2, 3), Fim.CONCLUIDO, 35, "Jurídico", listOf("Contratos", "Imóveis")),
        Spec("Acordo de confidencialidade (NDA) – Projeto Aurora", 6, listOf(0, 4), Fim.CONCLUIDO, 28, "Jurídico", listOf("NDA")),
        Spec("Termo aditivo – Contrato 2026/031", 4, listOf(1, 0), Fim.CONCLUIDO, 21, categorias = listOf("Contratos")),
        Spec("Orçamento de reforma do escritório", 8, listOf(3, 5), Fim.RECUSADO, 18, "Financeiro", listOf("Compras")),
        Spec("Proposta comercial – Grupo Horizonte", 18, listOf(4, 1), Fim.CONCLUIDO, 15, "Comercial", listOf("Propostas")),
        Spec("Contrato de trabalho – Rafael Costa", 10, listOf(1, 2, 5), Fim.CONCLUIDO, 12, "RH", listOf("Admissão"), opcional = 2),
        Spec("Contrato de parceria – Instituto Verde", 14, listOf(0, 3), Fim.NOVA_VERSAO, 10, "Comercial", listOf("Parcerias")),
        Spec("Política de privacidade – ciência dos colaboradores", 40, listOf(1, 0, 2, 3, 4), Fim.CONCLUIDO, 9, "RH", listOf("LGPD")),
        Spec("Manual de integração 2026", 60, listOf(1, 2, 3, 4), Fim.PARCIAL, 6, "RH", listOf("Treinamento")),
        Spec("Contrato de fornecimento – Papelaria Central", 20, listOf(5, 0, 4), Fim.PARCIAL, 4, "Financeiro", listOf("Compras", "Contratos")),
        Spec("Distrato – Contrato de manutenção predial", 5, listOf(2, 3), Fim.VISUALIZADO, 3, "Jurídico", listOf("Contratos")),
        Spec("Relatório anual ESG 2025 – aprovação", 48, listOf(1, 3, 4, 5), Fim.ENVIADO, 2, "Diretoria", listOf("ESG")),
        Spec("Procuração – Representação em assembleia", 3, listOf(0), Fim.ENVIADO, 1, "Jurídico"),
        Spec("Contrato de estágio – Turma 2027", 3, listOf(2), Fim.RASCUNHO, 1, "RH", listOf("Admissão")),
        Spec("Termo de uso de imagem", 2, emptyList(), Fim.RASCUNHO, 0, "Comercial")
    )

    /** Cria tudo (ou só entra, se já foi criado) e deixa o Admin logado. */
    // NonCancellable: sair da tela no meio não deixa a demonstração pela metade.
    suspend fun load(): Result<Unit> = withContext(Dispatchers.IO + NonCancellable) {
        runCatching {
            if (database.userDao().findByEmail(ADMIN_EMAIL) != null) {
                LocalAuthRepository(database.userDao(), tokenManager).login(ADMIN_EMAIL, SENHA).getOrThrow()
                return@runCatching
            }
            val admin = auth.register("Carla Mendes", ADMIN_EMAIL, "11987654321", SENHA).getOrThrow()
            val gente = pessoas.mapIndexed { i, (nome, email, cpf) ->
                contacts.createSigner(nome, email, "1199${(1000000 + i * 137531).toString().take(7)}", SENHA, cpf, "1${i}/0${i + 1}/19${80 + i}").getOrThrow()
            }
            contacts.saveGroup(null, "Diretoria", listOf(gente[1].id, gente[3].id, gente[4].id)).getOrThrow()
            contacts.saveGroup(null, "Jurídico", listOf(gente[0].id, gente[2].id)).getOrThrow()

            for (s in specs) {
                val doc = docs.createDocument(s.titulo, pdf(s.titulo, s.paginas), arquivo(s.titulo)).getOrThrow()
                docs.updateOrganization(doc.id, s.pasta, s.categorias).getOrThrow()
                if (s.fim == Fim.RASCUNHO) { espalhar(doc.id, s.diasAtras); continue }
                val draft = draft(s, gente, s.paginas)
                docs.sendDocument(doc.id, draft).getOrThrow()
                val signers = s.quem.map { gente[it] }
                when (s.fim) {
                    Fim.VISUALIZADO -> como(signers[0]) { docs.refreshDocument(doc.id).getOrThrow() }
                    Fim.PARCIAL -> signers.take(signers.size / 2).forEach { assinar(it, doc.id) }
                    Fim.RECUSADO -> como(signers[1]) {
                        docs.refreshDocument(doc.id).getOrThrow()
                        docs.rejectDocument(doc.id, "Valor acima do orçamento aprovado para o trimestre.").getOrThrow()
                    }
                    Fim.CONCLUIDO -> {
                        // A testemunha opcional recusa antes: depois que os obrigatórios assinam, o documento já está concluído.
                        s.opcional?.let { o -> como(signers[o]) { docs.rejectDocument(doc.id, "Não estive presente na assinatura; prefiro não testemunhar.").getOrThrow() } }
                        signers.filterIndexed { i, _ -> i != s.opcional }.forEach { assinar(it, doc.id) }
                    }
                    Fim.NOVA_VERSAO -> {
                        assinar(signers[0], doc.id)
                        como(admin) {
                            val v2 = docs.newVersion(doc.id, pdf("${s.titulo} (revisado)", s.paginas + 1), arquivo(s.titulo).replace(".pdf", "-v2.pdf")).getOrThrow()
                            docs.sendDocument(v2.id, draft(s, gente, s.paginas + 1)).getOrThrow()
                            como(signers[1]) { docs.refreshDocument(v2.id).getOrThrow() }
                            espalhar(v2.id, s.diasAtras / 2)
                        }
                    }
                    else -> Unit
                }
                if (s.titulo.startsWith("Relatório anual")) docs.sendReminder(doc.id).getOrThrow()
                if (s.titulo.startsWith("Manual")) docs.setObservers(doc.id, listOf(gente[5].id)).getOrThrow()
                if (s.titulo.startsWith("Acordo de confidencialidade")) docs.saveAsTemplate(doc.id, "NDA padrão").getOrThrow()
                espalhar(doc.id, s.diasAtras)
            }
            tokenManager.saveSession(LOCAL, admin)
        }
    }

    private fun draft(s: Spec, gente: List<User>, paginas: Int): SendDraft {
        val participants = s.quem.mapIndexed { i, idx ->
            val role = when {
                i == 0 -> RoleLabel.CONTRATANTE
                i == s.opcional -> RoleLabel.TESTEMUNHA
                else -> RoleLabel.CONTRATADO
            }
            DraftParticipant(gente[idx], role, required = i != s.opcional)
        }
        // Assinaturas na última página, uma abaixo da outra (duas colunas quando há muitos signatários).
        val fields = participants.flatMapIndexed { i, p ->
            val x = if (i % 2 == 0) 0.08f else 0.54f
            val y = 0.30f + (i / 2) * 0.17f
            val id = p.signer.id
            buildList {
                add(DraftField(UUID.randomUUID().toString(), id, paginas, x, y, 0.38f, 0.08f, FieldType.ASSINATURA))
                if (s.camposExtras) {
                    add(DraftField(UUID.randomUUID().toString(), id, paginas, x, y + 0.085f, 0.38f, 0.025f, FieldType.NOME))
                    add(DraftField(UUID.randomUUID().toString(), id, paginas, x, y + 0.11f, 0.38f, 0.025f, FieldType.CPF))
                    add(DraftField(UUID.randomUUID().toString(), id, paginas, x, y + 0.135f, 0.38f, 0.025f, FieldType.DATA))
                    (1 until paginas).forEach { pg -> add(DraftField(UUID.randomUUID().toString(), id, pg, 0.80f - i * 0.1f, 0.92f, 0.08f, 0.04f, FieldType.RUBRICA)) }
                }
            }
        }
        val msg = "Olá! Segue \"${s.titulo}\" para assinatura. Qualquer dúvida, estou à disposição."
        return SendDraft(participants, fields, msg)
    }

    private suspend fun assinar(p: User, docId: String) = como(p) {
        docs.refreshDocument(docId).getOrThrow()
        docs.signDocument(docId, assinaturaPng(p.nome, 600, 180, 64f), null, assinaturaPng(p.initials, 200, 120, 60f)).getOrThrow()
    }

    /** Troca a sessão sem passar pelo login (que calcula o hash da senha a cada vez) e volta para quem estava. */
    private suspend fun <T> como(user: User, block: suspend () -> T): T {
        val antes = sessao.currentUser.value
        sessao.saveSession(LOCAL, user)
        return try { block() } finally { antes?.let { sessao.saveSession(LOCAL, it) } }
    }

    /** Recua as datas do documento para [dias] atrás, mantendo a ordem dos acontecimentos (passos de até 5 h). */
    private suspend fun espalhar(id: String, dias: Int) {
        val dao = database.documentDao()
        val doc = dao.get(LOCAL, id) ?: return
        val parts = dao.getParticipants(LOCAL, id)
        val events = dao.observeEvents(LOCAL, id).first()
        val todas = (listOf(doc.criadoEm, doc.enviadoEm, doc.concluidoEm, doc.atualizadoEm) +
            parts.flatMap { listOf(it.invitedAt, it.viewedAt, it.signedAt, it.rejectedAt) } + events.map { it.timestamp })
            .filterNotNull().distinct().sorted()
        val agora = System.currentTimeMillis()
        val inicio = agora - dias * DIA - 2 * HORA
        val passo = minOf(5 * HORA, (agora - inicio) / (todas.size + 1))
        val novo = todas.withIndex().associate { (i, t) -> t to iso(inicio + i * passo) }
        fun m(t: String?) = t?.let { novo[it] ?: it }
        dao.upsertDocuments(listOf(doc.copy(criadoEm = m(doc.criadoEm), enviadoEm = m(doc.enviadoEm), concluidoEm = m(doc.concluidoEm), atualizadoEm = m(doc.atualizadoEm))))
        dao.upsertParticipants(parts.map { it.copy(invitedAt = m(it.invitedAt), viewedAt = m(it.viewedAt), signedAt = m(it.signedAt), rejectedAt = m(it.rejectedAt)) })
        dao.upsertEvents(events.map { it.copy(timestamp = m(it.timestamp)!!) })
    }

    private fun iso(ms: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private fun arquivo(titulo: String) =
        java.text.Normalizer.normalize(titulo.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50) + ".pdf"

    /** PDF A4 com título, cláusulas de exemplo e, na última página, o espaço das assinaturas. */
    private fun pdf(titulo: String, paginas: Int): File {
        val out = File(context.cacheDir, "demo-${UUID.randomUUID()}.pdf")
        val pdf = PdfDocument()
        val titulo1 = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0x3D, 0x3E, 0x80) }
        val texto = Paint().apply { textSize = 11f; color = Color.DKGRAY }
        val cinza = Paint().apply { textSize = 9f; color = Color.GRAY }
        for (n in 1..paginas) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            val c = page.canvas
            c.drawText(titulo, 50f, 70f, titulo1)
            var y = 110f
            if (n < paginas) {
                for (k in 1..8) {
                    c.drawText("Cláusula ${(n - 1) * 8 + k}ª", 50f, y, Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD }); y += 18f
                    CLAUSULA.forEach { c.drawText(it, 50f, y, texto); y += 15f }
                    y += 12f
                }
            } else {
                c.drawText("E, por estarem de acordo, as partes assinam eletronicamente este documento.", 50f, y, texto)
                c.drawText("Assinaturas", 50f, 230f, Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 13f })
            }
            c.drawText("Documento de demonstração gerado pelo EasySign · Página $n de $paginas", 50f, 815f, cinza)
            pdf.finishPage(page)
        }
        out.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /** Assinatura "à mão": o texto em fonte cursiva com um traço por baixo, em PNG base64. */
    private fun assinaturaPng(texto: String, w: Int, h: Int, size: Float): String {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val tinta = Color.rgb(0x1F, 0x2A, 0x6B)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; textSize = size; typeface = Typeface.create("cursive", Typeface.ITALIC) }
        c.drawText(texto, 12f, h * 0.62f, p)
        val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
        c.drawPath(Path().apply { moveTo(8f, h * 0.8f); cubicTo(w * 0.3f, h * 0.7f, w * 0.6f, h * 0.9f, w * 0.92f, h * 0.74f) }, traco)
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bmp.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        const val ADMIN_EMAIL = "demo@easysign.com"
        const val SENHA = "demo123"
        private const val HORA = 3_600_000L
        private const val DIA = 24 * HORA
        private val CLAUSULA = listOf(
            "As partes acima qualificadas ajustam entre si as condições descritas neste instrumento,",
            "que se obrigam a cumprir por si e por seus sucessores, nos termos da legislação vigente.",
            "Eventuais alterações deverão ser formalizadas por escrito e assinadas pelas partes."
        )
    }
}
