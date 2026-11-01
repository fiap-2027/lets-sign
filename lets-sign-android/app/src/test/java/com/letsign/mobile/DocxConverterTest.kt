package com.letsign.mobile

import com.letsign.mobile.data.pdf.DocxConverter
import com.letsign.mobile.data.pdf.PdfInvalidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Extração do texto de um .docx (a parte da conversão que roda na JVM). */
class DocxConverterTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun `le paragrafos, tabulacoes e quebras do word document xml`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>Contrato de</w:t></w:r><w:r><w:t xml:space="preserve"> prestação</w:t></w:r></w:p>
            <w:p/>
            <w:p><w:r><w:t>Nome:</w:t><w:tab/><w:t>Ana</w:t><w:br/><w:t>CPF</w:t></w:r></w:p>
            <w:p/>
            </w:body></w:document>"""
        val paragraphs = DocxConverter.paragraphs(zip("[Content_Types].xml" to "<Types/>", "word/document.xml" to xml))
        assertEquals(listOf("Contrato de prestação", "", "Nome:\tAna\nCPF"), paragraphs)
    }

    @Test
    fun `zip que nao e documento word e recusado`() {
        assertThrows(PdfInvalidException::class.java) { DocxConverter.paragraphs(zip("outro.txt" to "oi")) }
    }
}
