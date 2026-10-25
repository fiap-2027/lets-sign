package com.letsign.mobile.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.letsign.mobile.data.pdf.PdfDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val renderer: PdfDocumentRenderer) : PdfLoad
    data class Failed(val message: String) : PdfLoad
}

/** Abre o PDF de [path] para renderizar páginas e fecha quando a tela sai de composição. */
@Composable
fun rememberPdfRenderer(path: String?): PdfLoad {
    val load by produceState<PdfLoad>(PdfLoad.Loading, path) {
        if (path == null) return@produceState
        val result = try {
            PdfLoad.Ready(withContext(Dispatchers.IO) { PdfDocumentRenderer.open(java.io.File(path)) })
        } catch (e: Exception) {
            PdfLoad.Failed(e.message ?: "Não foi possível abrir o PDF.")
        }
        value = result
        awaitDispose { (result as? PdfLoad.Ready)?.renderer?.close() }
    }
    return load
}

/**
 * Uma página do PDF na largura disponível, com [overlay] por cima (campos de assinatura).
 * O overlay recebe o tamanho da página em dp para posicionar os campos por fração (0..1).
 * Sem [renderer] (previews, carregando) desenha uma página fictícia.
 */
@Composable
fun PdfPageView(
    renderer: PdfDocumentRenderer?,
    pageIndex: Int,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(pageWidth: Dp, pageHeight: Dp) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.2f, 5f))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val bitmap by produceState<Bitmap?>(null, renderer, pageIndex, widthPx) {
            value = renderer?.let { runCatching { it.render(pageIndex, widthPx) }.getOrNull() }
        }
        val image = bitmap
        if (image != null) {
            Image(bitmap = image.asImageBitmap(), contentDescription = "Página ${pageIndex + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            FakePageContent()
        }
        overlay(maxWidth, maxHeight)
    }
}

/** Linhas cinzas imitando texto: usado em previews e enquanto a página renderiza. */
@Composable
private fun FakePageContent() {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        val lineColor = Color(0xFFE0E0E0)
        val step = size.height / 22f
        drawRect(Color(0xFFBDBDBD), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.55f, step * 0.6f))
        for (i in 2 until 20) {
            val w = if (i % 6 == 5) 0.6f else 1f
            drawRect(lineColor, topLeft = Offset(0f, i * step), size = androidx.compose.ui.geometry.Size(size.width * w, step * 0.35f))
        }
    }
}

/** Retângulo de campo de assinatura posicionado por fração da página. */
@Composable
fun BoxScope.FieldBox(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    pageWidth: Dp,
    pageHeight: Dp,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    // offset/size primeiro: gestos passados em [modifier] precisam enxergar a posição real do campo.
    Box(
        modifier = Modifier
            .offset(x = pageWidth * x, y = pageHeight * y)
            .size(width = pageWidth * width, height = pageHeight * height)
            .then(modifier)
            .background(color.copy(alpha = if (highlighted) 0.30f else 0.18f), RoundedCornerShape(4.dp))
            .border(if (highlighted) 2.dp else 1.dp, color, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        content()
    }
}
