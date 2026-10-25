package com.letsign.mobile.presentation.ui.components

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream

private const val STROKE_PX = 6f

/**
 * Área para desenhar a assinatura com o dedo.
 * [onSign] recebe o PNG em base64 e os metadados do gesto no formato esperado pelo backend (`gestureMetadata`).
 */
@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    onSign: (base64: String, gestureMetadata: JsonObject) -> Unit,
    onClear: () -> Unit = {},
    initialStrokes: List<List<Offset>> = emptyList()
) {
    val strokes = remember { mutableStateListOf<List<Offset>>().apply { addAll(initialStrokes) } }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var endedAt by remember { mutableLongStateOf(0L) }
    val inkColor = Color(0xFF1A237E)

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Desenhe sua assinatura abaixo",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    // Registra desde o primeiro toque (detectDragGestures só começa após a distância mínima
                    // e cortava o início de cada traço) e consome o movimento para a tela não rolar.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (startedAt == 0L) startedAt = System.currentTimeMillis()
                        strokes.add(listOf(down.position))
                        drag(down.id) { change ->
                            change.consume()
                            strokes[strokes.lastIndex] = strokes.last() + change.position
                        }
                        endedAt = System.currentTimeMillis()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (strokes.isEmpty()) {
                Text("Assine aqui", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.headlineSmall)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                strokes.forEach { points -> drawPath(points.toPath(), inkColor, style = Stroke(STROKE_PX, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    startedAt = 0L
                    onClear()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Limpar") }

            Button(
                onClick = {
                    val metadata = JsonObject(
                        mapOf(
                            "totalTracos" to JsonPrimitive(strokes.size),
                            "totalPontos" to JsonPrimitive(strokes.sumOf { it.size }),
                            "duracaoMs" to JsonPrimitive((endedAt - startedAt).coerceAtLeast(0)),
                            "dispositivo" to JsonPrimitive("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        )
                    )
                    onSign(strokes.toPngBase64(size, inkColor), metadata)
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Assinar") }
        }
    }
}

private fun List<Offset>.toPath() = Path().apply {
    firstOrNull()?.let { moveTo(it.x, it.y) }
    drop(1).forEach { lineTo(it.x, it.y) }
}

/** PNG com fundo transparente, para o backend carimbar sobre o PDF. */
private fun List<List<Offset>>.toPngBase64(size: IntSize, ink: Color): String {
    val bitmap = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb((ink.alpha * 255).toInt(), (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = STROKE_PX
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    forEach { points ->
        val path = android.graphics.Path()
        points.firstOrNull()?.let { path.moveTo(it.x, it.y) }
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@androidx.compose.ui.tooling.preview.Preview(name = "Vazio", widthDp = 360, showBackground = true)
@Composable
fun SignaturePadEmptyPreview() = com.letsign.mobile.presentation.theme.LetsSignTheme {
    androidx.compose.material3.Surface(Modifier.fillMaxSize()) { SignaturePad(Modifier.statusBarsPadding(), onSign = { _, _ -> }) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Assinado", widthDp = 360, showBackground = true)
@Composable
fun SignaturePadSignedPreview() = com.letsign.mobile.presentation.theme.LetsSignTheme {
    // Traço fictício em formato de rubrica.
    val stroke = (0..60).map { i ->
        val t = i / 60f
        Offset(60f + t * 780f, 300f - kotlin.math.sin(t * 9.4f) * 90f * (1 - t * 0.5f))
    }
    androidx.compose.material3.Surface(Modifier.fillMaxSize()) { SignaturePad(Modifier.statusBarsPadding(), onSign = { _, _ -> }, initialStrokes = listOf(stroke, listOf(Offset(120f, 420f), Offset(760f, 400f)))) }
}
