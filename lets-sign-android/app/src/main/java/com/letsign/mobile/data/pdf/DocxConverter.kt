package com.letsign.mobile.data.pdf

import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Converte .docx (Word) em PDF para poder assinar.
 * ponytail: leva só o texto (parágrafos, tabulações e quebras); fontes, tabelas e imagens se perdem.
 * Para manter a formatação, o usuário exporta o PDF no próprio Word. Trocar por conversão no servidor se isso pesar.
 */
object DocxConverter {

    private const val MAX_XML_BYTES = 20L * 1024 * 1024

    /** Parágrafos de texto de word/document.xml. JVM pura (SAX), coberta por teste unitário. */
    fun paragraphs(docx: InputStream): List<String> {
        val xml = ZipInputStream(docx).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                ?: throw PdfInvalidException("O arquivo não é um documento Word (.docx) válido.")
            readLimited(zip)
        }
        val result = mutableListOf<String>()
        val handler = object : DefaultHandler() {
            private val current = StringBuilder()
            private var inText = false
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                when (qName) {
                    "w:t" -> inText = true
                    "w:tab" -> current.append('\t')
                    "w:br", "w:cr" -> current.append('\n')
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "w:t" -> inText = false
                    "w:p" -> {
                        result += current.toString()
                        current.clear()
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.appendRange(ch, start, start + length)
            }
        }
        SAXParserFactory.newInstance().apply {
            // Sem entidades externas (XXE): o arquivo vem de fora do app. O parser do Android (Expat) já não as
            // resolve e recusa estas opções; na JVM (testes) elas valem.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newSAXParser().parse(ByteArrayInputStream(xml), handler)
        // Tira linhas vazias do fim.
        return result.dropLastWhile { it.isBlank() }
    }

    /** Escreve os parágrafos num PDF A4 (11 pt, margens de 2 cm), quebrando linhas e páginas. */
    fun writePdf(paragraphs: List<String>, out: File) {
        if (paragraphs.none { it.isNotBlank() }) throw PdfInvalidException("O documento Word não tem texto para assinar.")
        val pageW = 595
        val pageH = 842
        val margin = 56f
        val width = (pageW - 2 * margin).toInt()
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
        }
        val lineGap = paint.fontSpacing * 0.4f
        val pdf = PdfDocument()
        try {
            var number = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            fun newPage() {
                page?.let(pdf::finishPage)
                number++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, number).create())
                y = margin
            }
            newPage()
            for (text in paragraphs) {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
                for (line in 0 until layout.lineCount) {
                    val h = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                    if (y + h > pageH - margin) newPage()
                    val baseline = y + (layout.getLineBaseline(line) - layout.getLineTop(line))
                    val end = layout.getLineEnd(line)
                    page!!.canvas.drawText(text.substring(layout.getLineStart(line), end).trimEnd('\n'), margin, baseline, paint)
                    y += h
                }
                y += lineGap
            }
            page?.let(pdf::finishPage)
            out.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_XML_BYTES) throw PdfInvalidException("O documento Word é grande demais para converter.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
