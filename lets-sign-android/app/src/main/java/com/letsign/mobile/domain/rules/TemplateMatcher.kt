package com.letsign.mobile.domain.rules

import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.domain.model.RoleLabel
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.abs

// Modelos inteligentes: o layout dos campos é salvo junto com "âncoras" de texto (a linha mais próxima de
// cada campo). Num PDF novo do mesmo tipo (NDA, proposta padrão), o app reconhece o modelo pelas palavras
// e recoloca cada campo relativo à sua âncora: se o bloco de assinatura desceu ou mudou de página, a caixa vai junto.
// Tudo aqui é Kotlin puro (posições em fração da página, origem no topo), coberto por TemplateMatcherTest.

/** Trecho de texto do PDF com posição (fração da página, origem no topo). Página começa em 1. */
@Serializable
data class TextLine(val page: Int, val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

/** Quem assina no modelo: o contato salvo e o papel. */
@Serializable
data class LayoutSlot(val signerId: String, val roleLabel: RoleLabel, val required: Boolean = true)

/** Campo do modelo: posição absoluta + âncora de texto opcional ([dx], [dy] = deslocamento até a âncora). */
@Serializable
data class LayoutField(
    val slot: Int,
    val tipo: FieldType,
    val page: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val anchor: String? = null,
    val anchorY: Float = 0f,
    val dx: Float = 0f,
    val dy: Float = 0f
)

@Serializable
data class TemplateLayout(
    val pageCount: Int,
    /** Palavras do documento de origem (impressão digital para reconhecer PDFs parecidos). */
    val words: List<String>,
    val slots: List<LayoutSlot>,
    val fields: List<LayoutField>
)

/** Campo recolocado num PDF novo. [anchored] = achou o texto âncora; senão, ficou na posição original. */
data class PlacedField(val slot: Int, val tipo: FieldType, val page: Int, val x: Float, val y: Float, val w: Float, val h: Float, val anchored: Boolean)

object TemplateMatcher {

    /** Semelhança mínima para aplicar um modelo sozinho, sem o usuário pedir. */
    const val MIN_SCORE = 0.45

    private const val MAX_WORDS = 1500

    /** Minúsculas, sem acentos e com espaços simples: "  CONTRATANTE:  Ana " -> "contratante: ana". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace(Regex("\\s+"), " ").trim()

    fun words(lines: List<TextLine>): Set<String> =
        lines.asSequence()
            .flatMap { normalize(it.text).split(' ', ',', '.', ':', ';', '(', ')', '"').asSequence() }
            .filter { w -> w.length >= 4 && w.any(Char::isLetter) }
            .toSet()

    /** Junta palavras soltas (como o extrator entrega) em linhas: mesma página e mesma altura, da esquerda para a direita. */
    fun groupLines(words: List<TextLine>): List<TextLine> =
        words.groupBy { it.page }.toSortedMap().flatMap { (page, onPage) ->
            val lines = mutableListOf<MutableList<TextLine>>()
            onPage.sortedBy { it.y }.forEach { w ->
                val line = lines.lastOrNull()?.takeIf { abs(it.first().y - w.y) <= maxOf(it.first().h, w.h) * 0.5f }
                if (line != null) line += w else lines += mutableListOf(w)
            }
            lines.map { ws ->
                val sorted = ws.sortedBy { it.x }
                val left = sorted.first().x
                val top = sorted.minOf { it.y }
                TextLine(page, sorted.joinToString(" ") { it.text.trim() }, left, top, sorted.maxOf { it.x + it.w } - left, sorted.maxOf { it.y + it.h } - top)
            }
        }

    /** Monta o layout a partir dos campos posicionados no documento de origem (âncora = linha logo acima, ou logo abaixo). */
    fun build(slots: List<LayoutSlot>, fields: List<LayoutField>, lines: List<TextLine>, pageCount: Int): TemplateLayout {
        val withAnchors = fields.map { f ->
            val onPage = lines.filter { it.page == f.page && normalize(it.text).length >= 3 }
            // Mais perto na vertical e na horizontal: com "CONTRATANTE" e "CONTRATADA" lado a lado, cada campo pega a sua.
            val above = onPage.filter { it.y + it.h <= f.y + 0.02f && f.y - it.y <= 0.3f }.minByOrNull { (f.y - it.y) + abs(it.x - f.x) }
            val below = onPage.filter { it.y >= f.y + f.h - 0.02f && it.y - f.y <= 0.3f }.minByOrNull { (it.y - f.y) + abs(it.x - f.x) }
            val a = above ?: below
            if (a == null) f else f.copy(anchor = normalize(a.text), anchorY = a.y, dx = f.x - a.x, dy = f.y - a.y)
        }
        return TemplateLayout(pageCount, words(lines).sorted().take(MAX_WORDS), slots, withAnchors)
    }

    /** Parecença 0..1 entre o modelo e um PDF novo: palavras em comum (Jaccard), com leve desconto se o nº de páginas muda. */
    fun score(layout: TemplateLayout, lines: List<TextLine>, pageCount: Int): Double {
        val a = layout.words.toSet()
        val b = words(lines)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val jaccard = a.intersect(b).size.toDouble() / a.union(b).size
        return jaccard * if (pageCount == layout.pageCount) 1.0 else 0.9
    }

    /** Recoloca os campos do modelo num PDF novo. */
    fun place(layout: TemplateLayout, lines: List<TextLine>, pageCount: Int): List<PlacedField> = layout.fields.map { f ->
        val match = f.anchor?.let { anchor -> findAnchor(anchor, f, lines) }
        if (match != null) {
            PlacedField(f.slot, f.tipo, match.page, (match.x + f.dx).coerceIn(0f, 1f - f.w), (match.y + f.dy).coerceIn(0f, 1f - f.h), f.w, f.h, anchored = true)
        } else {
            // Sem âncora: mesma posição; quem estava na última página continua na última (bloco de assinatura).
            val page = if (f.page == layout.pageCount) pageCount else f.page.coerceAtMost(pageCount)
            PlacedField(f.slot, f.tipo, page.coerceAtLeast(1), f.x, f.y, f.w, f.h, anchored = false)
        }
    }

    /** Linha com o mesmo texto (ou que começa com as mesmas 3 palavras), a mais perto da página e altura originais. */
    private fun findAnchor(anchor: String, f: LayoutField, lines: List<TextLine>): TextLine? {
        val exact = lines.filter { normalize(it.text) == anchor }
        val prefix = anchor.split(' ').take(3).takeIf { it.size >= 2 }?.joinToString(" ")
        val candidates = exact.ifEmpty { if (prefix == null) emptyList() else lines.filter { normalize(it.text).startsWith(prefix) } }
        return candidates.minByOrNull { abs(it.page - f.page) * 10f + abs(it.y - f.anchorY) }
    }
}
