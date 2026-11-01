package com.letsign.mobile.presentation.ui.components

import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsign.mobile.presentation.theme.LetsSignTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream

private const val STROKE_PX = 6f
private val Ink = Color(0xFF1F2133)

/** Traços desenhados com o dedo. Fica fora do Composable para sobreviver à troca de aba Digitar/Desenhar. */
class DrawnSignature(initial: List<List<Offset>> = emptyList()) {
    val strokes = mutableStateListOf<List<Offset>>().apply { addAll(initial) }
    var size by mutableStateOf(IntSize.Zero)
    private var startedAt by mutableLongStateOf(0L)
    private var endedAt by mutableLongStateOf(0L)

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun begin(point: Offset) {
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        strokes.add(listOf(point))
    }

    fun extend(point: Offset) {
        strokes[strokes.lastIndex] = strokes.last() + point
    }

    fun end() {
        endedAt = System.currentTimeMillis()
    }

    fun clear() {
        strokes.clear()
        startedAt = 0L
    }

    /** Metadados do gesto no formato esperado pelo backend (`gestureMetadata`). */
    fun metadata() = JsonObject(
        mapOf(
            "tipo" to JsonPrimitive("desenhada"),
            "totalTracos" to JsonPrimitive(strokes.size),
            "totalPontos" to JsonPrimitive(strokes.sumOf { it.size }),
            "duracaoMs" to JsonPrimitive((endedAt - startedAt).coerceAtLeast(0)),
            "dispositivo" to JsonPrimitive("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        )
    )

    /** PNG com fundo transparente, para carimbar sobre o PDF. */
    fun toPngBase64(): String {
        val bitmap = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = inkPaint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = STROKE_PX
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        strokes.forEach { points ->
            val path = android.graphics.Path()
            points.firstOrNull()?.let { path.moveTo(it.x, it.y) }
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
        return bitmap.toBase64Png()
    }
}

/** Aba "Desenhar" (print 14 da web): quadro branco com borda e botão "Apagar" logo abaixo. */
@Composable
fun DrawSignatureArea(signature: DrawnSignature, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, Ink.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .onSizeChanged { signature.size = it }
                .pointerInput(signature) {
                    // Registra desde o primeiro toque (detectDragGestures só começa após a distância mínima
                    // e cortava o início de cada traço) e consome o movimento para a tela não rolar.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        signature.begin(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            signature.extend(change.position)
                        }
                        signature.end()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (signature.isEmpty) Text("Assine aqui", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.headlineSmall)
            Canvas(Modifier.fillMaxSize()) {
                signature.strokes.forEach { points ->
                    drawPath(points.toPath(), Ink, style = Stroke(STROKE_PX, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        OutlinedButton(onClick = signature::clear, enabled = !signature.isEmpty) { Text("Apagar") }
    }
}

/** Estilos da aba "Digitar" (Estilo 1, 2 e 3 na web). Fontes do sistema: nenhum arquivo extra no APK. */
enum class TypedStyle(val label: String, val typeface: Typeface) {
    STYLE_1("Estilo 1", Typeface.create("cursive", Typeface.NORMAL)),
    STYLE_2("Estilo 2", Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)),
    STYLE_3("Estilo 3", Typeface.create("casual", Typeface.NORMAL));

    val fontFamily: FontFamily get() = FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
}

/** Prévia da assinatura digitada: o nome na fonte do estilo, num quadro branco. */
@Composable
fun TypedSignaturePreview(name: String, style: TypedStyle, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().height(96.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = name.ifBlank { "Seu nome" },
                fontFamily = style.fontFamily,
                fontSize = 34.sp,
                color = if (name.isBlank()) Color(0xFFBDBDBD) else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Gera o PNG (fundo transparente) da assinatura digitada, no mesmo formato da desenhada. */
fun typedSignaturePng(name: String, style: TypedStyle): String {
    val paint = inkPaint().apply {
        typeface = style.typeface
        textSize = 96f
    }
    val padding = 24f
    val width = (paint.measureText(name) + padding * 2).toInt().coerceAtLeast(1)
    val metrics = paint.fontMetrics
    val height = (metrics.descent - metrics.ascent + padding * 2).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).drawText(name, padding, padding - metrics.ascent, paint)
    return bitmap.toBase64Png()
}

fun typedSignatureMetadata(style: TypedStyle) = JsonObject(
    mapOf(
        "tipo" to JsonPrimitive("digitada"),
        "estilo" to JsonPrimitive(style.label),
        "dispositivo" to JsonPrimitive("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    )
)

private fun inkPaint() = android.graphics.Paint().apply {
    color = android.graphics.Color.argb((Ink.alpha * 255).toInt(), (Ink.red * 255).toInt(), (Ink.green * 255).toInt(), (Ink.blue * 255).toInt())
    isAntiAlias = true
}

private fun Bitmap.toBase64Png(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun List<Offset>.toPath() = Path().apply {
    firstOrNull()?.let { moveTo(it.x, it.y) }
    drop(1).forEach { lineTo(it.x, it.y) }
}

@Preview(name = "Desenhar", widthDp = 360, showBackground = true)
@Composable
fun DrawSignatureAreaPreview() = LetsSignTheme {
    // Traço fictício em formato de rubrica.
    val stroke = (0..60).map { i ->
        val t = i / 60f
        Offset(60f + t * 780f, 260f - kotlin.math.sin(t * 9.4f) * 90f * (1 - t * 0.5f))
    }
    Surface { DrawSignatureArea(remember { DrawnSignature(listOf(stroke)) }, Modifier.padding(16.dp)) }
}

@Preview(name = "Digitar", widthDp = 360, showBackground = true)
@Composable
fun TypedSignaturePreviewPreview() = LetsSignTheme {
    Surface { TypedSignaturePreview("Dionisio Leonardo", TypedStyle.STYLE_1, Modifier.padding(16.dp)) }
}
