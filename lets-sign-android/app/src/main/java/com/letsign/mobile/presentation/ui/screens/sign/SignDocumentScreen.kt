package com.letsign.mobile.presentation.ui.screens.sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.theme.StatusColors
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.FieldBox
import com.letsign.mobile.presentation.ui.components.LetsSignTopAppBar
import com.letsign.mobile.presentation.ui.components.LoadingOverlay
import com.letsign.mobile.presentation.ui.components.PdfLoad
import com.letsign.mobile.presentation.ui.components.PdfPageView
import com.letsign.mobile.presentation.ui.components.SignaturePad
import com.letsign.mobile.presentation.ui.components.SnackbarMessage
import com.letsign.mobile.presentation.ui.components.rememberPdfRenderer
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.SignDocumentUiState
import com.letsign.mobile.presentation.viewmodel.SignDocumentViewModel
import kotlinx.serialization.json.JsonObject

@Composable
fun SignDocumentScreen(onBack: () -> Unit, viewModel: SignDocumentViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignDocumentContent(state, onBack, viewModel::sign, viewModel::load, viewModel::consumeError)
}

/** Assinatura sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignDocumentContent(
    state: SignDocumentUiState,
    onBack: () -> Unit,
    onSign: (String, JsonObject) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    val document = state.document
    SnackbarMessage(snackbar, state.error?.takeIf { state.pdfPath != null }, onErrorShown)

    Scaffold(
        topBar = { LetsSignTopAppBar(title = "Assinar documento", subtitle = document?.titulo, showBackButton = true, onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.signedMessage != null -> SignedView(state.signedMessage, onBack)
                state.error != null && state.pdfPath == null -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir", state.error, "Tentar novamente", onRetry)
                state.isLoading && state.pdfPath == null -> CenteredLoading()
                document != null && !document.canSign -> EmptyState(Icons.Default.CheckCircle, "Nada a assinar", "Você já assinou ou recusou este documento, ou ele não está mais disponível.", "Voltar", onBack)
                else -> SignBody(state, onSign = { png, meta -> pending = png to meta })
            }
            LoadingOverlay(state.isSigning, "Registrando assinatura...")
        }
    }

    pending?.let { (png, meta) ->
        ConfirmDialog(
            title = "Confirmar assinatura?",
            text = "Ao confirmar, sua assinatura é registrada com data, hora e dados do aparelho, e não pode ser desfeita.",
            confirmText = "Assinar",
            onConfirm = {
                pending = null
                onSign(png, meta)
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun SignBody(state: SignDocumentUiState, onSign: (String, JsonObject) -> Unit) {
    val load = rememberPdfRenderer(state.pdfPath)
    val renderer = (load as? PdfLoad.Ready)?.renderer
    val document = state.document
    val myFields = document?.fields.orEmpty().filter { state.currentUserId == null || it.signerId == state.currentUserId }
    val pages = myFields.map { it.page }.distinct().sorted().ifEmpty { listOf(1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Você assina como ${document?.meuPapel?.label ?: "signatário"}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sua assinatura será aplicada em ${myFields.size} campo(s), na(s) página(s) ${pages.joinToString()}. Revise o documento antes de assinar.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        pages.forEach { page ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Página $page de ${document?.totalPaginas ?: pages.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                val aspect = renderer?.info?.pageAspectRatios?.getOrNull(page - 1) ?: 0.707f
                PdfPageView(renderer, page - 1, aspect) { w, h ->
                    myFields.filter { it.page == page }.forEach { f ->
                        FieldBox(f.x, f.y, f.width, f.height, w, h, StatusColors.Blue, "Assine aqui", highlighted = true)
                    }
                }
            }
        }

        SignaturePad(onSign = onSign)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SignedView(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusColors.Green, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Assinatura registrada", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao documento") }
    }
}

// ---------------------------------------------------------------- Previews

@ScreenPreviews
@Composable
fun SignDocumentPreview() = LetsSignTheme {
    SignDocumentContent(
        SignDocumentUiState(document = PreviewData.signerDocument, pdfPath = "/preview.pdf", currentUserId = PreviewData.ana.id, isLoading = false),
        {}, { _, _ -> }, {}, {}
    )
}

@ScreenPreviews
@Composable
fun SignDocumentDonePreview() = LetsSignTheme {
    SignDocumentContent(SignDocumentUiState(signedMessage = "Assinatura registrada com sucesso.", isLoading = false), {}, { _, _ -> }, {}, {})
}
