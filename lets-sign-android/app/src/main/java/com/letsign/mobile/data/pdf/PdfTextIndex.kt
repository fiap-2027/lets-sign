package com.letsign.mobile.data.pdf

import android.content.Context
import android.util.Log
import com.letsign.mobile.domain.rules.TemplateMatcher
import com.letsign.mobile.domain.rules.TextLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Texto do PDF com posição de cada linha (para reconhecer modelos e ancorar campos). */
@Singleton
class PdfTextIndex @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Linhas de todas as páginas. PDF escaneado (só imagem) ou ilegível devolve lista vazia: o modelo cai na posição fixa. */
    fun lines(pdf: File): List<TextLine> = try {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(pdf).use { doc ->
            val words = mutableListOf<TextLine>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                    val p = positions?.takeIf { it.isNotEmpty() } ?: return
                    if (text.isNullOrBlank()) return
                    val first = p.first()
                    val pw = first.pageWidth.takeIf { it > 0 } ?: return
                    val ph = first.pageHeight.takeIf { it > 0 } ?: return
                    val h = p.maxOf { it.heightDir }
                    val left = first.xDirAdj
                    val right = p.maxOf { it.xDirAdj + it.widthDirAdj }
                    // yDirAdj é a linha de base medida a partir do topo; o topo da palavra fica uma altura acima.
                    words += TextLine(currentPageNo, text, left / pw, (first.yDirAdj - h) / ph, (right - left) / pw, h / ph)
                }
            }.apply { sortByPosition = true }
            stripper.getText(doc)
            TemplateMatcher.groupLines(words)
        }
    } catch (e: Exception) {
        Log.w("EasySign", "Não foi possível ler o texto do PDF", e)
        emptyList()
    }
}
