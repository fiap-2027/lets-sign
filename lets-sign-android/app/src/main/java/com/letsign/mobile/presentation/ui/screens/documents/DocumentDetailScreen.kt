@file:OptIn(ExperimentalMaterial3Api::class)

package com.letsign.mobile.presentation.ui.screens.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.Participant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.theme.StatusColors
import com.letsign.mobile.presentation.ui.components.Avatar
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.DocumentStatusChip
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.LetsSignTopAppBar
import com.letsign.mobile.presentation.ui.components.LoadingOverlay
import com.letsign.mobile.presentation.ui.components.SectionCard
import com.letsign.mobile.presentation.ui.components.SignerStatusChip
import com.letsign.mobile.presentation.ui.components.SnackbarMessage
import com.letsign.mobile.presentation.ui.components.signerColor
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.util.formatBytes
import com.letsign.mobile.presentation.util.formatDateTime
import com.letsign.mobile.presentation.util.formatEpochDateTime
import com.letsign.mobile.presentation.viewmodel.DocumentDetailUiState
import com.letsign.mobile.presentation.viewmodel.DocumentDetailViewModel

@Composable
fun DocumentDetailScreen(
    onNavigateBack: () -> Unit,
    onContinueDraft: (String) -> Unit,
    onSign: (String) -> Unit,
    onViewPdf: (String, PdfKind) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::newVersion)
    }
    // Nova versão enviada: abre o assistente com signatários e campos mantidos para revisar e reenviar.
    LaunchedEffect(state.newVersionReady) {
        if (state.newVersionReady) {
            viewModel.consumeNewVersion()
            onContinueDraft(viewModel.documentId)
        }
    }
    DocumentDetailContent(
        state = state,
        onPickNewVersion = { picker.launch(arrayOf("application/pdf")) },
        onBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onContinueDraft = { onContinueDraft(viewModel.documentId) },
        onSign = { onSign(viewModel.documentId) },
        onReject = viewModel::reject,
        onViewPdf = { kind -> onViewPdf(viewModel.documentId, kind) },
        onMessagesShown = viewModel::consumeMessages
    )
}

/** Detalhe/acompanhamento sem ViewModel: é o que os previews renderizam. */
@Composable
fun DocumentDetailContent(
    state: DocumentDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onContinueDraft: () -> Unit,
    onSign: () -> Unit,
    onReject: (String) -> Unit,
    onViewPdf: (PdfKind) -> Unit,
    onMessagesShown: () -> Unit,
    onPickNewVersion: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val document = state.document
    var showReject by rememberSaveable { mutableStateOf(false) }
    var showNewVersion by rememberSaveable { mutableStateOf(false) }

    // Com o documento na tela, erros viram snackbar; sem documento, viram a tela de erro abaixo.
    SnackbarMessage(snackbar, state.message ?: state.error?.takeIf { document != null }, onMessagesShown)

    Scaffold(
        topBar = {
            LetsSignTopAppBar(
                title = document?.titulo ?: "Documento",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (document?.canEdit == true) IconButton(onClick = onContinueDraft) { Icon(Icons.Default.Edit, contentDescription = "Continuar configuração") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isRefreshing && document != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                document != null -> DetailBody(document, state.localPdfs, state.currentUserId, onContinueDraft, onSign, { showReject = true }, onViewPdf, { showNewVersion = true })
                state.error != null -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir", state.error, "Tentar novamente", onRefresh)
                else -> CenteredLoading()
            }
        }
        LoadingOverlay(state.isWorking, state.workingMessage)
    }

    if (showNewVersion && document != null) {
        val signed = document.signers.count { it.isSigned }
        ConfirmDialog(
            title = "Enviar nova versão?",
            text = buildString {
                append("Escolha o novo PDF. A versão ${document.versao} fica preservada no histórico e o documento volta para rascunho, ")
                append("com os mesmos signatários e campos, para você revisar e reenviar.")
                when {
                    signed == 1 -> append("\n\nA assinatura já feita será descartada e todos assinam de novo.")
                    signed > 1 -> append("\n\nAs $signed assinaturas já feitas serão descartadas e todos assinam de novo.")
                }
            },
            confirmText = "Escolher PDF",
            onConfirm = {
                showNewVersion = false
                onPickNewVersion()
            },
            onDismiss = { showNewVersion = false }
        )
    }

    if (showReject) {
        RejectDialog(
            onConfirm = { motivo ->
                showReject = false
                onReject(motivo)
            },
            onDismiss = { showReject = false }
        )
    }
}

@Composable
private fun DetailBody(
    document: Document,
    localPdfs: List<LocalPdf>,
    currentUserId: String?,
    onContinueDraft: () -> Unit,
    onSign: () -> Unit,
    onReject: () -> Unit,
    onViewPdf: (PdfKind) -> Unit,
    onNewVersion: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderCard(document)
        ActionsCard(document, onContinueDraft, onSign, onReject, onViewPdf, onNewVersion)
        ParticipantsCard(document, currentUserId)
        if (document.isOwnerView && document.fields.isNotEmpty()) FieldsCard(document)
        if (document.events.isNotEmpty()) TimelineCard(document)
        if (localPdfs.isNotEmpty()) LocalFilesCard(localPdfs)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderCard(document: Document) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.titulo, style = MaterialTheme.typography.titleLarge)
                    Text("${document.nomeArquivo} • ${document.totalPaginas} páginas • ${formatBytes(document.tamanhoBytes)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!document.isOwnerView && document.owner != null) {
                        Text("Enviado por ${document.owner.nome}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                DocumentStatusChip(document.status)
            }

            if (document.signers.isNotEmpty() && !document.isDraft) {
                val total = document.requiredSigners.coerceAtLeast(1)
                LinearProgressIndicator(progress = { document.requiredSigned / total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Text("${document.requiredSigned} de ${document.requiredSigners} assinatura(s) obrigatória(s)", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Versão ${document.versao}", style = MaterialTheme.typography.bodyMedium)
                    Text("SHA-256: ${document.hashSha256.take(20)}…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SHA-256", document.hashFinal ?: document.hashSha256))) } }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar hash para verificação")
                }
            }
        }
    }
}

@Composable
private fun ActionsCard(
    document: Document,
    onContinueDraft: () -> Unit,
    onSign: () -> Unit,
    onReject: () -> Unit,
    onViewPdf: (PdfKind) -> Unit,
    onNewVersion: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (document.canSign) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recusar")
                }
                Button(onClick = onSign, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Assinar")
                }
            }
        }
        if (document.canEdit) {
            Button(onClick = onContinueDraft, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continuar configuração e enviar")
            }
        }
        if (document.possuiDocumentoFinal || document.isCompleted) {
            FilledTonalButton(onClick = { onViewPdf(PdfKind.FINAL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver documento final assinado")
            }
        }
        OutlinedButton(onClick = { onViewPdf(PdfKind.ORIGINAL) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ver PDF original")
        }
        if (document.canCreateNewVersion) {
            TextButton(onClick = onNewVersion, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enviar nova versão do PDF")
            }
        }
    }
}

@Composable
private fun ParticipantsCard(document: Document, currentUserId: String?) {
    SectionCard(title = "Signatários (${document.signers.size})") {
        if (document.signers.isEmpty()) {
            Text("Nenhum signatário definido ainda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        document.signers.forEachIndexed { index, p -> ParticipantRow(p, index, isMe = p.signerId == currentUserId) }
    }
}

@Composable
private fun ParticipantRow(p: Participant, index: Int, isMe: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(p.signer?.initials.orEmpty(), color = signerColor(index), contentColor = MaterialTheme.colorScheme.surface)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (p.signer?.nome?.ifEmpty { null } ?: "Signatário") + if (isMe) " (você)" else "",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
            )
            Text(
                text = p.roleLabel.label + if (p.required) " • obrigatório" else " • opcional",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val whenText = when (p.status) {
                SignerStatus.SIGNED -> "Assinou em ${formatDateTime(p.signedAt)}"
                SignerStatus.REJECTED -> "Recusou em ${formatDateTime(p.rejectedAt)}" + (p.motivoRecusa?.let { "\nMotivo: $it" } ?: "")
                SignerStatus.VIEWED -> "Visualizou em ${formatDateTime(p.viewedAt)}"
                SignerStatus.PENDING -> p.invitedAt?.let { "Convidado em ${formatDateTime(it)}" } ?: "Ainda não convidado"
            }
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        SignerStatusChip(p.status)
    }
}

@Composable
private fun FieldsCard(document: Document) {
    SectionCard(title = "Campos de assinatura (${document.fields.size})") {
        document.fields.groupBy { it.page }.toSortedMap().forEach { (page, fields) ->
            val names = fields.joinToString { f ->
                document.signers.firstOrNull { it.signerId == f.signerId }?.signer?.nome?.ifEmpty { null } ?: f.roleLabel.label
            }
            Text("Página $page: $names", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TimelineCard(document: Document) {
    SectionCard(title = "Histórico", trailing = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
        document.events.forEach { e ->
            val (icon, tint) = when (e.tipo) {
                AuditType.CREATED, AuditType.VERSION_CREATED -> Icons.AutoMirrored.Filled.NoteAdd to MaterialTheme.colorScheme.secondary
                AuditType.FIELDS_UPDATED -> Icons.Default.Edit to MaterialTheme.colorScheme.secondary
                AuditType.SENT -> Icons.AutoMirrored.Filled.Send to StatusColors.Amber
                AuditType.VIEWED -> Icons.Default.Visibility to StatusColors.Blue
                AuditType.SIGNED -> Icons.Default.Draw to StatusColors.Green
                AuditType.REJECTED -> Icons.Default.Block to StatusColors.Red
                AuditType.COMPLETED -> Icons.Default.CheckCircle to StatusColors.Green
                AuditType.OTHER -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
            }
            TimelineRow(icon, tint, e.descricao, formatDateTime(e.timestamp))
        }
    }
}

@Composable
private fun TimelineRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String, time: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalFilesCard(files: List<LocalPdf>) {
    SectionCard(title = "Salvo neste aparelho", trailing = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
        files.forEach { f ->
            Column {
                Text(f.nomeArquivo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${if (f.tipo == PdfKind.FINAL) "Final assinado" else "Original"} • v${f.versao} • ${formatBytes(f.tamanhoBytes)} • salvo em ${formatEpochDateTime(f.salvoEm)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RejectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var motivo by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recusar documento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conte ao remetente por que você não vai assinar. Se você for obrigatório, o documento será cancelado.")
                OutlinedTextField(value = motivo, onValueChange = { motivo = it.take(500) }, label = { Text("Motivo da recusa") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(motivo) }, enabled = motivo.isNotBlank()) {
                Text("Recusar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------------------------------------------------------------- Previews

@ScreenPreviews
@Composable
fun DocumentDetailAdminPreview() = LetsSignTheme {
    DocumentDetailContent(DocumentDetailUiState(document = PreviewData.document, localPdfs = PreviewData.localPdfs, currentUserId = PreviewData.admin.id), {}, {}, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun DocumentDetailSignerPreview() = LetsSignTheme {
    DocumentDetailContent(DocumentDetailUiState(document = PreviewData.signerDocument, currentUserId = PreviewData.ana.id), {}, {}, {}, {}, {}, {}, {})
}
