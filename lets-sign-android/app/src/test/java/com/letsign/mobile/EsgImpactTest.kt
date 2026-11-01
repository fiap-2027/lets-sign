package com.letsign.mobile

import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.rules.EsgBadge
import com.letsign.mobile.domain.rules.EsgImpact
import com.letsign.mobile.domain.rules.EsgLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsgImpactTest {

    private fun doc(status: DocumentStatus, paginas: Int, signatarios: Int) =
        Document(id = "$status$paginas", ownerId = "o", titulo = "t", status = status, totalPaginas = paginas, totalSignatarios = signatarios)

    @Test
    fun `folhas sao paginas vezes vias e rascunho nao conta`() {
        // 4 páginas × (2 signatários + remetente) = 12; 3 × (1 + 1) = 6; rascunho fora.
        val docs = listOf(doc(DocumentStatus.COMPLETED, 4, 2), doc(DocumentStatus.PENDING, 3, 1), doc(DocumentStatus.DRAFT, 50, 5))
        val i = EsgImpact.of(docs)
        assertEquals(2, i.documentos)
        assertEquals(18L, i.folhas)
        assertEquals(180.0, i.litrosAgua, 1e-9)
        assertEquals(0.225, i.kgMadeira, 1e-9) // 18 × 5 g = 90 g de papel × 2,5
        assertEquals(0.0846, i.kgCo2, 1e-9) // 18 × 4,7 g
        assertEquals(0.00216, i.arvores, 1e-9) // 0,09 kg = 0,00009 t × 24
    }

    @Test
    fun `nivel e progresso acompanham as folhas`() {
        val zero = EsgImpact.of(emptyList())
        assertEquals(EsgLevel.SEMENTE, zero.level)
        assertEquals(0f, zero.levelProgress)
        val meio = EsgImpact.of(listOf(doc(DocumentStatus.PENDING, 150, 0))) // 150 folhas: Broto (50) → Muda (250)
        assertEquals(EsgLevel.BROTO, meio.level)
        assertEquals(0.5f, meio.levelProgress, 1e-6f)
        val topo = EsgImpact.of(listOf(doc(DocumentStatus.COMPLETED, 25_000, 0)))
        assertEquals(EsgLevel.FLORESTA, topo.level)
        assertNull(topo.nextLevel)
        assertEquals(1f, topo.levelProgress)
    }

    @Test
    fun `conquistas desbloqueiam pelos marcos`() {
        val docs = listOf(doc(DocumentStatus.COMPLETED, 2, 1))
        val i = EsgImpact.of(docs)
        assertTrue(EsgBadge.PRIMEIRO_ENVIO.reached(i, docs))
        assertTrue(EsgBadge.CONCLUIDO.reached(i, docs))
        assertFalse(EsgBadge.CEM_FOLHAS.reached(i, docs))
        assertFalse(EsgBadge.UMA_ARVORE.reached(i, docs))
    }
}
