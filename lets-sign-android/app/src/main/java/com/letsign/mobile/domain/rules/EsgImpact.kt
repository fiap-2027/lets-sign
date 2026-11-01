package com.letsign.mobile.domain.rules

import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus

/**
 * Impacto ambiental de assinar no app em vez de imprimir.
 *
 * Premissas (conservadoras, para não superestimar):
 * - 1 página = 1 folha A4 de 80 g/m² (5 g), sem frente e verso;
 * - cada documento enviado seria impresso em 1 via por signatário + 1 via do remetente; rascunhos não contam;
 * - água: 10 L por folha (média global da Water Footprint Network; estudos vão de 2 a 20 L);
 * - madeira: 2,5 kg de madeira por kg de papel virgem (12,5 g por folha);
 * - CO2e: 4,7 g por folha (ponta baixa da faixa publicada de 4 a 13 g; só o papel, sem impressão nem transporte);
 * - árvores: 24 árvores por tonelada de papel virgem (≈ 1 árvore a cada 8.333 folhas).
 */
data class EsgImpact(
    val documentos: Int,
    val folhas: Long,
    val litrosAgua: Double,
    val kgMadeira: Double,
    val kgCo2: Double,
    val arvores: Double
) {
    val level: EsgLevel get() = EsgLevel.entries.last { folhas >= it.minFolhas }
    val nextLevel: EsgLevel? get() = EsgLevel.entries.getOrNull(level.ordinal + 1)

    /** Progresso (0..1) entre o nível atual e o próximo; 1 no último nível. */
    val levelProgress: Float
        get() {
            val next = nextLevel ?: return 1f
            return ((folhas - level.minFolhas).toFloat() / (next.minFolhas - level.minFolhas)).coerceIn(0f, 1f)
        }

    /** Equivalências para o painel (aproximadas). */
    val garrafoes20L: Double get() = litrosAgua / 20.0
    val kmCarro: Double get() = kgCo2 / KG_CO2_POR_KM

    companion object {
        const val GRAMAS_POR_FOLHA = 5.0
        const val LITROS_POR_FOLHA = 10.0
        const val MADEIRA_POR_PAPEL = 2.5
        const val G_CO2_POR_FOLHA = 4.7
        const val ARVORES_POR_TONELADA = 24.0

        /** Carro popular a gasolina: ~120 g de CO2 por km. */
        const val KG_CO2_POR_KM = 0.12

        fun vias(doc: Document): Int = (doc.totalSignatarios ?: doc.signers.size) + 1

        fun of(documents: List<Document>): EsgImpact {
            val enviados = documents.filter { it.status != DocumentStatus.DRAFT }
            val folhas = enviados.sumOf { it.totalPaginas.toLong() * vias(it) }
            val kgPapel = folhas * GRAMAS_POR_FOLHA / 1000.0
            return EsgImpact(
                documentos = enviados.size,
                folhas = folhas,
                litrosAgua = folhas * LITROS_POR_FOLHA,
                kgMadeira = kgPapel * MADEIRA_POR_PAPEL,
                kgCo2 = folhas * G_CO2_POR_FOLHA / 1000.0,
                arvores = kgPapel / 1000.0 * ARVORES_POR_TONELADA
            )
        }
    }
}

/** Níveis do painel, por folhas poupadas. */
enum class EsgLevel(val label: String, val emoji: String, val minFolhas: Long) {
    SEMENTE("Semente", "🌰", 0),
    BROTO("Broto", "🌱", 50),
    MUDA("Muda", "🌿", 250),
    ARVORE("Árvore", "🌳", 1_000),
    BOSQUE("Bosque", "🌲", 5_000),
    FLORESTA("Floresta", "🏞️", 20_000)
}

/** Conquistas: [reached] diz se o usuário já desbloqueou. */
enum class EsgBadge(val label: String, val description: String, val emoji: String, val reached: (EsgImpact, List<Document>) -> Boolean) {
    PRIMEIRO_ENVIO("Primeiro passo", "Primeiro documento assinado sem papel", "✍️", { i, _ -> i.documentos >= 1 }),
    CONCLUIDO("Ciclo fechado", "Um documento concluído 100% digital", "✅", { _, docs -> docs.any { it.status == DocumentStatus.COMPLETED } }),
    CEM_FOLHAS("Resma poupada", "100 folhas que não foram impressas", "📄", { i, _ -> i.folhas >= 100 }),
    MIL_LITROS("Caixa d'água", "1.000 litros de água poupados", "💧", { i, _ -> i.litrosAgua >= 1_000 }),
    DEZ_DOCUMENTOS("Escritório digital", "10 documentos enviados pelo app", "📱", { i, _ -> i.documentos >= 10 }),
    UMA_ARVORE("Uma árvore em pé", "Papel equivalente a uma árvore inteira", "🌳", { i, _ -> i.arvores >= 1 })
}
