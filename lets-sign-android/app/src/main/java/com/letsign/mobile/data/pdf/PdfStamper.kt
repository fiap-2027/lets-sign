package com.letsign.mobile.data.pdf

import android.content.Context
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * O que desenhar num campo: posição em fração da página (origem no topo, igual ao editor) e
 * a imagem PNG (assinatura/rubrica) ou linhas de texto (nome, CPF, data, carimbo).
 */
class Stamp(
    val page: Int, val x: Float, val y: Float, val width: Float, val height: Float,
    val png: ByteArray?, val label: String,
    val lines: List<String> = emptyList(),
    /** Desenha uma moldura em volta (carimbo/selo). */
    val boxed: Boolean = false
)

/**
 * Gera o PDF final com as assinaturas desenhadas nos campos, sem rasterizar o original (texto continua texto).
 * PDFBox-Android é a opção sugerida no desafio; a lógica é a mesma do stampPdf do backend.
 */
// ponytail: ignora /Rotate da página (o backend também); trate a rotação se aparecer PDF girado.
@Singleton
class PdfStamper @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun stamp(original: File, stamps: List<Stamp>, footer: String): ByteArray {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(original).use { doc ->
            // PDF com senha só de permissões abre, mas precisa sair sem a criptografia para poder ser salvo.
            doc.isAllSecurityToBeRemoved = true
            val font = PDType1Font.HELVETICA
            // A mesma assinatura em vários campos entra no PDF uma vez só.
            val images = IdentityHashMap<ByteArray, PDImageXObject?>()
            for (s in stamps) {
                if (s.page !in 1..doc.numberOfPages) continue
                val page = doc.getPage(s.page - 1)
                val box = page.cropBox
                val w = s.width * box.width
                val h = s.height * box.height
                val left = box.lowerLeftX + s.x * box.width
                val top = box.upperRightY - s.y * box.height
                if (s.png == null) {
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs -> cs.textBlock(font, s, left, top, w, h) }
                    continue
                }
                val png = s.png
                val img = images.getOrPut(png) {
                    BitmapFactory.decodeByteArray(png, 0, png.size)?.let { LosslessFactory.createFromImage(doc, it) }
                } ?: continue
                val scale = min(w / img.width, h / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(img, left + (w - dw) / 2, top - h + (h - dh) / 2, dw, dh)
                    if (s.label.isNotEmpty()) cs.text(font, left, max(box.lowerLeftY + 2, top - h - 8), s.label)
                }
            }
            val last = doc.getPage(doc.numberOfPages - 1)
            PDPageContentStream(doc, last, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.text(font, last.cropBox.lowerLeftX + 20, last.cropBox.lowerLeftY + 10, footer)
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    /** Linhas de texto centralizadas na vertical, no maior tamanho (até 11 pt) que cabe no campo. */
    private fun PDPageContentStream.textBlock(font: PDType1Font, s: Stamp, left: Float, top: Float, w: Float, h: Float) {
        if (s.boxed) {
            setStrokingColor(0.24f, 0.24f, 0.5f)
            setLineWidth(1f)
            addRect(left, top - h, w, h)
            stroke()
        }
        val lines = s.lines.map(::latin1)
        if (lines.isEmpty()) return
        val pad = if (s.boxed) 3f else 1f
        val widest = lines.maxOf { font.getStringWidth(it) / 1000f }.coerceAtLeast(1f)
        val size = minOf(11f, (h - 2 * pad) / (lines.size * 1.2f), (w - 2 * pad) / widest).coerceAtLeast(3f)
        var y = top - (h - lines.size * size * 1.2f) / 2 - size
        for (line in lines) {
            text(font, left + pad, y, line, size)
            y -= size * 1.2f
        }
    }

    private fun PDPageContentStream.text(font: PDType1Font, x: Float, y: Float, text: String, size: Float = 6f) {
        beginText()
        setFont(font, size)
        newLineAtOffset(x, y)
        showText(latin1(text))
        endText()
    }

    // A Helvetica padrão só tem caracteres Latin-1: o resto vira "?" em vez de quebrar a geração.
    private fun latin1(text: String) = text.map { if (it.code in 32..126 || it.code in 160..255) it else '?' }.joinToString("")
}
