package com.letsign.mobile.data.pdf

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** PDF escolhido e já validado: caminho no aparelho + número e proporção das páginas. */
data class PickedPdf(val path: String, val displayName: String, val sizeBytes: Long, val info: PdfInfo)

/** Importa e valida PDFs. Interface para o ViewModel do assistente poder ser testado sem Android. */
interface PdfImporter {
    /** Copia o arquivo escolhido para o app e valida. Lança [PdfInvalidException] com mensagem para o usuário. */
    suspend fun import(uri: Uri): PickedPdf

    /** Valida um PDF que já está no aparelho (ex.: rascunho baixado do servidor). */
    suspend fun inspect(file: File, displayName: String): PickedPdf
}

@Singleton
class DefaultPdfImporter @Inject constructor(private val storage: PdfStorage) : PdfImporter {

    override suspend fun import(uri: Uri): PickedPdf = withContext(Dispatchers.IO) {
        val imported = storage.importFromUri(uri)
        try {
            PickedPdf(imported.file.absolutePath, imported.displayName, imported.sizeBytes, PdfDocumentRenderer.inspect(imported.file))
        } catch (e: Exception) {
            imported.file.delete()
            throw e
        }
    }

    override suspend fun inspect(file: File, displayName: String): PickedPdf = withContext(Dispatchers.IO) {
        PickedPdf(file.absolutePath, displayName, file.length(), PdfDocumentRenderer.inspect(file))
    }
}
