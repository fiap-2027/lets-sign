package com.letsign.mobile

import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.rules.LayoutField
import com.letsign.mobile.domain.rules.LayoutSlot
import com.letsign.mobile.domain.rules.TemplateMatcher
import com.letsign.mobile.domain.rules.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateMatcherTest {

    private val nda = listOf(
        TextLine(1, "ACORDO DE CONFIDENCIALIDADE", 0.1f, 0.05f, 0.6f, 0.02f),
        TextLine(1, "As partes concordam em manter sigilo sobre informações confidenciais trocadas", 0.1f, 0.10f, 0.8f, 0.02f),
        TextLine(1, "durante a vigência deste acordo, sob pena de multa contratual.", 0.1f, 0.13f, 0.8f, 0.02f),
        TextLine(2, "CONTRATANTE", 0.1f, 0.60f, 0.2f, 0.02f),
        TextLine(2, "CONTRATADA", 0.55f, 0.60f, 0.2f, 0.02f)
    )
    private val slots = listOf(LayoutSlot("ana", RoleLabel.CONTRATANTE), LayoutSlot("joao", RoleLabel.CONTRATADO))
    private val fields = listOf(
        LayoutField(0, FieldType.ASSINATURA, 2, 0.1f, 0.65f, 0.3f, 0.08f),
        LayoutField(1, FieldType.ASSINATURA, 2, 0.55f, 0.65f, 0.3f, 0.08f),
        LayoutField(0, FieldType.DATA, 2, 0.1f, 0.95f, 0.2f, 0.03f)
    )

    @Test
    fun `cada campo ganha como ancora a linha logo acima`() {
        val layout = TemplateMatcher.build(slots, fields, nda, 2)
        assertEquals("contratante", layout.fields[0].anchor)
        assertEquals("contratada", layout.fields[1].anchor)
        assertEquals(0.05f, layout.fields[0].dy, 1e-5f)
        // Data longe (> 30% da página) de qualquer texto: fica sem âncora.
        assertEquals(null, layout.fields[2].anchor)
    }

    @Test
    fun `campo acompanha a ancora quando o bloco de assinatura muda de pagina e de altura`() {
        val layout = TemplateMatcher.build(slots, fields, nda, 2)
        // Proposta maior: o bloco de assinatura foi para a página 3, mais abaixo.
        val novo = nda.map { if (it.page == 2) it.copy(page = 3, y = 0.80f) else it } + TextLine(2, "Cláusula extra de vigência", 0.1f, 0.1f, 0.5f, 0.02f)
        val placed = TemplateMatcher.place(layout, novo, 3)
        assertTrue(placed[0].anchored)
        assertEquals(3, placed[0].page)
        assertEquals(0.85f, placed[0].y, 1e-5f)
        assertEquals(0.55f, placed[1].x, 1e-5f)
        // Sem âncora e estava na última página: continua na última.
        assertFalse(placed[2].anchored)
        assertEquals(3, placed[2].page)
    }

    @Test
    fun `campo nunca sai da pagina`() {
        val layout = TemplateMatcher.build(slots, fields, nda, 2)
        val novo = nda.map { if (it.text == "CONTRATANTE") it.copy(y = 0.97f) else it }
        val p = TemplateMatcher.place(layout, novo, 2).first()
        assertTrue(p.y + p.h <= 1f + 1e-5f)
    }

    @Test
    fun `reconhece o mesmo tipo de documento e recusa um diferente`() {
        val layout = TemplateMatcher.build(slots, fields, nda, 2)
        val outroNda = nda.map { if (it.text.startsWith("durante")) it.copy(text = "durante a vigência deste acordo com Maria Souza, sob pena de multa.") else it }
        assertTrue(TemplateMatcher.score(layout, outroNda, 2) >= TemplateMatcher.MIN_SCORE)
        val recibo = listOf(TextLine(1, "RECIBO DE PAGAMENTO referente ao aluguel do imóvel", 0.1f, 0.1f, 0.8f, 0.02f))
        assertTrue(TemplateMatcher.score(layout, recibo, 1) < TemplateMatcher.MIN_SCORE)
    }

    @Test
    fun `junta palavras soltas em linhas e ignora acentos e caixa`() {
        val palavras = listOf(
            TextLine(1, "CONTRATANTE:", 0.1f, 0.500f, 0.15f, 0.02f),
            TextLine(1, "Ana", 0.3f, 0.505f, 0.05f, 0.02f),
            TextLine(1, "Testemunha", 0.1f, 0.60f, 0.15f, 0.02f)
        )
        val linhas = TemplateMatcher.groupLines(palavras)
        assertEquals(listOf("CONTRATANTE: Ana", "Testemunha"), linhas.map { it.text })
        assertEquals("informacoes confidenciais", TemplateMatcher.normalize("  Informações   CONFIDENCIAIS "))
    }
}
