package com.letsign.mobile.presentation.ui.screens.esg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.rules.EsgBadge
import com.letsign.mobile.domain.rules.EsgImpact
import com.letsign.mobile.presentation.theme.EasySignTheme
import com.letsign.mobile.presentation.theme.StatusColors
import com.letsign.mobile.presentation.ui.components.EasySignTopAppBar
import com.letsign.mobile.presentation.ui.components.SectionCard
import com.letsign.mobile.presentation.ui.components.WebCard
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.DocumentListViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun EsgScreen(onBack: () -> Unit, viewModel: DocumentListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EsgContent(state.documents, onBack)
}

/** Painel ESG sem ViewModel: é o que os previews renderizam. */
@Composable
fun EsgContent(documents: List<Document>, onBack: () -> Unit) {
    val impact = EsgImpact.of(documents)
    // Os números "sobem" de 0 até o valor (efeito de placar) sempre que o painel abre ou o total muda.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(impact.folhas) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
    }
    val k = grow.value.toDouble()

    Scaffold(
        topBar = { EasySignTopAppBar(title = "Impacto ESG", subtitle = "O que o papel digital já poupou", showBackButton = true, onBackClick = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LevelCard(impact, k)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("💧", "Água", liters(impact.litrosAgua * k), "≈ ${num(impact.garrafoes20L * k)} garrafões de 20 L", StatusColors.Blue, Modifier.weight(1f))
                MetricTile("🪵", "Madeira", mass(impact.kgMadeira * k), "≈ ${num(impact.arvores * k, 3)} árvore(s)", StatusColors.Amber, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("☁️", "CO₂e evitado", mass(impact.kgCo2 * k), "≈ ${num(impact.kmCarro * k)} km de carro", StatusColors.Gray, Modifier.weight(1f))
                MetricTile("📄", "Folhas", num(impact.folhas * k, 0), "em ${impact.documentos} documento(s)", StatusColors.Green, Modifier.weight(1f))
            }
            BadgesCard(impact, documents)
            MethodCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LevelCard(impact: EsgImpact, k: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(impact.level.emoji, fontSize = 56.sp)
            Text("Nível ${impact.level.label}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "${num(impact.folhas * k, 0)} folhas deixaram de ser impressas",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center
            )
            LinearProgressIndicator(
                progress = { impact.levelProgress * k.toFloat() },
                color = StatusColors.Green,
                trackColor = Color.White,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp)
            )
            val next = impact.nextLevel
            Text(
                if (next == null) "Nível máximo alcançado. Obrigado por cuidar do planeta!"
                else "Faltam ${num((next.minFolhas - impact.folhas).toDouble(), 0)} folhas para ${next.label} ${next.emoji}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MetricTile(emoji: String, label: String, value: String, equivalence: String, accent: Color, modifier: Modifier) {
    WebCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 28.sp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(equivalence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesCard(impact: EsgImpact, documents: List<Document>) {
    val unlocked = EsgBadge.entries.count { it.reached(impact, documents) }
    SectionCard(title = "Conquistas ($unlocked/${EsgBadge.entries.size})") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            EsgBadge.entries.forEach { b ->
                val ok = b.reached(impact, documents)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ok) StatusColors.Green.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f).alpha(if (ok) 1f else 0.55f)
                ) {
                    Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (ok) b.emoji else "🔒", fontSize = 28.sp)
                        Text(b.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(b.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodCard() {
    SectionCard(title = "Como calculamos") {
        Text(
            "Folhas = páginas × vias (1 por signatário + 1 do remetente), só de documentos enviados. " +
                "Usamos valores conservadores por folha A4 de 80 g/m² (5 g), para não superestimar:",
            style = MaterialTheme.typography.bodySmall
        )
        listOf(
            "💧 10 L de água: média global da Water Footprint Network (estudos vão de 2 a 20 L).",
            "🪵 2,5 kg de madeira por kg de papel virgem, ou seja, 12,5 g por folha.",
            "☁️ 4,7 g de CO₂e: ponta baixa da faixa publicada (4 a 13 g); só o papel, sem impressão nem transporte.",
            "🌳 24 árvores por tonelada de papel virgem (≈ 1 árvore a cada 8.333 folhas).",
            "🚗 Carro popular a gasolina: cerca de 120 g de CO₂ por km."
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            "São estimativas para conscientização, não um inventário de emissões auditado.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- formatação pt-BR

private val ptBR = Locale.forLanguageTag("pt-BR")

private fun num(v: Double, decimals: Int = if (v >= 100) 0 else if (v >= 10) 1 else 2): String =
    NumberFormat.getNumberInstance(ptBR).apply {
        maximumFractionDigits = decimals
        minimumFractionDigits = 0
    }.format(v)

private fun liters(v: Double) = if (v >= 1000) "${num(v / 1000)} mil L" else "${num(v, 0)} L"

/** Kg com troca para g (pouco) ou t (muito), para o número caber no card. */
private fun mass(kg: Double) = when {
    kg >= 1000 -> "${num(kg / 1000)} t"
    kg >= 1 -> "${num(kg)} kg"
    else -> "${num(kg * 1000, 0)} g"
}

// ---------------------------------------------------------------- Previews

@ScreenPreviews
@Composable
fun EsgPreview() = EasySignTheme { EsgContent(PreviewData.documents, {}) }
