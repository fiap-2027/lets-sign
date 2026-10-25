package com.letsign.mobile.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Proporção largura/altura de cada página (índice 0 = página 1). */
data class PdfInfo(val pageCount: Int, val pageAspectRatios: List<Float>)

/**
 * Lê PDFs com o [PdfRenderer] nativo do Android (sem biblioteca externa).
 * O PdfRenderer só renderiza uma página por vez, por isso o [Mutex].
 */
class PdfDocumentRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    private val mutex = Mutex()

    val info: PdfInfo = PdfInfo(
        pageCount = renderer.pageCount,
        pageAspectRatios = (0 until renderer.pageCount).map { i ->
            renderer.openPage(i).use { p -> p.width.toFloat() / p.height.toFloat() }
        }
    )

    /** Renderiza a página [pageIndex] (0-based) com [widthPx] de largura e fundo branco. */
    suspend fun render(pageIndex: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                val width = widthPx.coerceIn(1, 2048)
                val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * Abre e valida o PDF. Lança [PdfInvalidException] com mensagem pronta para o usuário quando
         * o arquivo é inválido, está corrompido, protegido por senha ou não tem páginas.
         */
        fun open(file: File): PdfDocumentRenderer {
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: IOException) {
                throw PdfInvalidException("Não foi possível abrir o arquivo.", e)
            }
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: SecurityException) {
                descriptor.close()
                throw PdfInvalidException("O PDF está protegido por senha e não pode ser processado.", e)
            } catch (e: Exception) {
                descriptor.close()
                throw PdfInvalidException("O arquivo não é um PDF válido ou está corrompido.", e)
            }
            if (renderer.pageCount < 1) {
                renderer.close()
                descriptor.close()
                throw PdfInvalidException("O PDF não tem nenhuma página.")
            }
            return try {
                PdfDocumentRenderer(descriptor, renderer)
            } catch (e: Exception) {
                renderer.close()
                descriptor.close()
                throw PdfInvalidException("O PDF está corrompido ou não pôde ser processado.", e)
            }
        }

        /** Só valida e devolve as informações, fechando o arquivo em seguida. */
        fun inspect(file: File): PdfInfo = open(file).use { it.info }
    }
}
