package com.letsign.mobile.presentation.ui.screens.pdf

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.data.pdf.PdfDocumentRenderer
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.LetsSignTopAppBar
import com.letsign.mobile.presentation.ui.components.PdfLoad
import com.letsign.mobile.presentation.ui.components.PdfPageView
import com.letsign.mobile.presentation.ui.components.rememberPdfRenderer
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.PdfViewerUiState
import com.letsign.mobile.presentation.viewmodel.PdfViewerViewModel
import java.io.File

@Composable
fun PdfViewerScreen(onBack: () -> Unit, viewModel: PdfViewerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val load = rememberPdfRenderer(state.file?.absolutePath)

    fun launch(file: File, share: Boolean) {
        // Cópia temporária com o nome real (ex.: contrato-assinado.pdf) para o outro app mostrar um nome legível.
        val named = File(File(context.cacheDir, "compartilhar").apply { mkdirs() }, state.fileName ?: file.name)
        file.copyTo(named, overwrite = true)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", named)
        val intent = if (share) Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        try {
            context.startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app para abrir PDF encontrado.", Toast.LENGTH_SHORT).show()
        }
    }

    PdfViewerContent(
        state = state,
        load = load,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenWith = { state.file?.let { launch(it, share = false) } },
        onShare = { state.file?.let { launch(it, share = true) } }
    )
}

/** Visualizador de PDF sem ViewModel: é o que os previews renderizam. */
@Composable
fun PdfViewerContent(
    state: PdfViewerUiState,
    load: PdfLoad,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    previewPages: Int = 0
) {
    val renderer: PdfDocumentRenderer? = (load as? PdfLoad.Ready)?.renderer
    val pageCount = renderer?.info?.pageCount ?: previewPages
    Scaffold(
        topBar = {
            LetsSignTopAppBar(
                title = state.title,
                subtitle = state.fileName ?: if (pageCount > 0) "$pageCount página(s)" else null,
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (state.file != null) {
                        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Compartilhar") }
                        IconButton(onClick = onOpenWith) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir com outro app") }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant)) {
            when {
                state.error != null -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir o PDF", state.error, "Tentar novamente", onRetry)
                load is PdfLoad.Failed -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir o PDF", load.message)
                pageCount == 0 -> CenteredLoading()
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(pageCount) { index ->
                        Column {
                            PdfPageView(renderer, index, renderer?.info?.pageAspectRatios?.getOrNull(index) ?: 0.707f)
                            Text("${index + 1} / $pageCount", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
fun PdfViewerPreview() = LetsSignTheme {
    PdfViewerContent(PdfViewerUiState(title = "Documento final", isLoading = false), PdfLoad.Loading, {}, {}, {}, {}, previewPages = 3)
}
