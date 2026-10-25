package com.letsign.mobile.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Mesmo limite do backend (FileInterceptor de 15 MB em documents.module.ts). */
const val MAX_PDF_BYTES = 15L * 1024 * 1024

data class ImportedPdf(val file: File, val displayName: String, val sizeBytes: Long)

/**
 * PDFs no sistema de arquivos privado do app:
 * - `cacheDir/importados/` guarda o PDF escolhido no assistente até ele virar documento;
 * - `filesDir/documentos/<usuário>/` guarda os PDFs baixados/enviados (registrados na tabela pdf_files).
 */
@Singleton
class PdfStorage @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val root get() = File(context.filesDir, "documentos")
    private val imports get() = File(context.cacheDir, "importados")

    /** Copia o PDF escolhido pelo usuário (Storage Access Framework) para o app. */
    fun importFromUri(uri: Uri): ImportedPdf {
        val resolver = context.contentResolver
        var name = "documento.pdf"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) declaredSize = c.getLong(1)
            }
        }
        if (declaredSize > MAX_PDF_BYTES) throw PdfInvalidException("O arquivo tem mais de 15 MB, o limite permitido.")
        if (!name.endsWith(".pdf", ignoreCase = true)) name += ".pdf"

        imports.mkdirs()
        val target = File(imports, "${UUID.randomUUID()}.pdf")
        val input = resolver.openInputStream(uri) ?: throw PdfInvalidException("Não foi possível abrir o arquivo selecionado.")
        input.use { inp ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = inp.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PDF_BYTES) {
                        target.delete()
                        throw PdfInvalidException("O arquivo tem mais de 15 MB, o limite permitido.")
                    }
                    out.write(buffer, 0, read)
                }
            }
        }
        return ImportedPdf(target, name, target.length())
    }

    fun fileFor(userId: String, documentId: String, tipo: String, versao: Int): File =
        File(File(root, safe(userId)), "${safe(documentId)}-$tipo-v$versao.pdf")

    fun save(userId: String, documentId: String, tipo: String, versao: Int, bytes: ByteArray): File {
        val file = fileFor(userId, documentId, tipo, versao)
        file.parentFile?.mkdirs()
        // Grava num temporário e renomeia: um download interrompido nunca deixa PDF pela metade.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        return file
    }

    fun adoptImported(userId: String, documentId: String, versao: Int, imported: File): File {
        val file = fileFor(userId, documentId, "original", versao)
        file.parentFile?.mkdirs()
        imported.copyTo(file, overwrite = true)
        imported.delete()
        return file
    }

    fun deleteAll() {
        root.deleteRecursively()
        imports.deleteRecursively()
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

class PdfInvalidException(message: String, cause: Throwable? = null) : IOException(message, cause)
