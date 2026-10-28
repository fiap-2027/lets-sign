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

/** Assinatura a desenhar: posição em fração da página (origem no topo, igual ao editor) e a imagem PNG. */
class Stamp(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float, val png: ByteArray, val label: String)

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
                val img = images.getOrPut(s.png) {
                    BitmapFactory.decodeByteArray(s.png, 0, s.png.size)?.let { LosslessFactory.createFromImage(doc, it) }
                } ?: continue
                val page = doc.getPage(s.page - 1)
                val box = page.cropBox
                val w = s.width * box.width
                val h = s.height * box.height
                val left = box.lowerLeftX + s.x * box.width
                val top = box.upperRightY - s.y * box.height
                val scale = min(w / img.width, h / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(img, left + (w - dw) / 2, top - h + (h - dh) / 2, dw, dh)
                    cs.text(font, left, max(box.lowerLeftY + 2, top - h - 8), s.label)
                }
            }
            val last = doc.getPage(doc.numberOfPages - 1)
            PDPageContentStream(doc, last, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.text(font, last.cropBox.lowerLeftX + 20, last.cropBox.lowerLeftY + 10, footer)
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    private fun PDPageContentStream.text(font: PDType1Font, x: Float, y: Float, text: String) {
        beginText()
        setFont(font, 6f)
        newLineAtOffset(x, y)
        // A Helvetica padrão só tem caracteres Latin-1: o resto vira "?" em vez de quebrar a geração.
        showText(text.map { if (it.code in 32..126 || it.code in 160..255) it else '?' }.joinToString(""))
        endText()
    }
}
