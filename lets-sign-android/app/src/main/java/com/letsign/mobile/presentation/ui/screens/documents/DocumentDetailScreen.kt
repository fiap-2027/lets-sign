@file:OptIn(ExperimentalMaterial3Api::class)

package com.letsign.mobile.presentation.ui.screens.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import com.letsign.mobile.BuildConfig
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.Participant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.presentation.theme.EasySignTheme
import com.letsign.mobile.presentation.theme.StatusColors
import com.letsign.mobile.presentation.ui.components.Avatar
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.DocumentStatusChip
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.EasySignTopAppBar
import com.letsign.mobile.presentation.ui.components.LoadingOverlay
import com.letsign.mobile.presentation.ui.components.SectionCard
import com.letsign.mobile.presentation.ui.components.SignerStatusChip
import com.letsign.mobile.presentation.ui.components.StatusBadge
import com.letsign.mobile.presentation.ui.components.WebCard
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
    LaunchedEffect(state.newVersionId) {
        state.newVersionId?.let { newId ->
            viewModel.consumeNewVersion()
            // No modo local a nova versão é outro documento; no modo API o id é o mesmo.
            onContinueDraft(newId)
        }
    }
    DocumentDetailContent(
        state = state,
        onPickNewVersion = { picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")) },
        onBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onContinueDraft = { onContinueDraft(viewModel.documentId) },
        onSign = { onSign(viewModel.documentId) },
        onReject = viewModel::reject,
        onViewPdf = { kind -> onViewPdf(viewModel.documentId, kind) },
        onMessagesShown = viewModel::consumeMessages,
        extras = DetailExtras(
            onReminder = viewModel::sendReminder,
            onAddObserver = viewModel::addObserver,
            onRemoveObserver = viewModel::removeObserver,
            onSaveTemplate = viewModel::saveAsTemplate,
            onSaveOrganization = viewModel::saveOrganization
        )
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
    onPickNewVersion: () -> Unit = {},
    extras: DetailExtras = DetailExtras(),
    initialTab: Int = 0
) {
    val snackbar = remember { SnackbarHostState() }
    val document = state.document
    var showReject by rememberSaveable { mutableStateOf(false) }
    var showNewVersion by rememberSaveable { mutableStateOf(false) }

    // Com o documento na tela, erros viram snackbar; sem documento, viram a tela de erro abaixo.
    SnackbarMessage(snackbar, state.message ?: state.error?.takeIf { document != null }, onMessagesShown)

    Scaffold(
        topBar = {
            EasySignTopAppBar(
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
                document != null -> DetailBody(document, state, onContinueDraft, onSign, { showReject = true }, onViewPdf, { showNewVersion = true }, extras, initialTab)
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

/** Ações das abas novas (Lembretes, Permissões, modelo e organização). Os previews usam os padrões. */
data class DetailExtras(
    val onReminder: () -> Unit = {},
    val onAddObserver: (String) -> Unit = {},
    val onRemoveObserver: (String) -> Unit = {},
    val onSaveTemplate: (String) -> Unit = {},
    val onSaveOrganization: (pasta: String, categorias: String) -> Unit = { _, _ -> }
)

/** Abas como na versão web: Envelope, Assinaturas, Lembretes, Permissões e Auditoria (as do dono só aparecem para ele). */
@Composable
private fun DetailBody(
    document: Document,
    state: DocumentDetailUiState,
    onContinueDraft: () -> Unit,
    onSign: () -> Unit,
    onReject: () -> Unit,
    onViewPdf: (PdfKind) -> Unit,
    onNewVersion: () -> Unit,
    extras: DetailExtras,
    initialTab: Int
) {
    val tabs = if (document.isOwnerView) listOf("Envelope", "Assinaturas", "Lembretes", "Permissões", "Auditoria")
    else listOf("Envelope", "Assinaturas", "Auditoria")
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tabs[tab]) {
                "Envelope" -> {
                    HeaderCard(document)
                    ActionsCard(document, onContinueDraft, onSign, onReject, onViewPdf, onNewVersion)
                    OrganizationCard(document, extras)
                    if (state.localPdfs.isNotEmpty()) LocalFilesCard(state.localPdfs)
                }
                "Assinaturas" -> {
                    ParticipantsCard(document, state.currentUserId)
                    if (document.isOwnerView && document.fields.isNotEmpty()) FieldsCard(document)
                }
                "Lembretes" -> RemindersCard(document, extras.onReminder)
                "Permissões" -> PermissionsCard(document, state.contacts, extras)
                else -> {
                    if (document.events.isNotEmpty()) TimelineCard(document)
                    HashCard(document)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OrganizationCard(document: Document, extras: DetailExtras) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    SectionCard(
        title = "Informações",
        trailing = { if (document.isOwnerView) TextButton(onClick = { editing = true }) { Text("Editar") } }
    ) {
        InfoRow(Icons.Default.Folder, "Pasta", document.pasta)
        InfoRow(Icons.Default.Sell, "Categorias", document.categorias.joinToString().ifEmpty { "Nenhuma" })
        document.mensagem?.let { InfoRow(Icons.AutoMirrored.Filled.Send, "Mensagem do convite", it) }
        if (document.isOwnerView) {
            OutlinedButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentPasteGo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salvar como modelo")
            }
        }
    }
    if (editing) {
        var pasta by rememberSaveable { mutableStateOf(document.pasta) }
        var cats by rememberSaveable { mutableStateOf(document.categorias.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Informações do documento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pasta, { pasta = it.take(60) }, label = { Text("Pasta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cats, { cats = it.take(200) }, label = { Text("Categorias (separe por vírgula)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    extras.onSaveOrganization(pasta, cats)
                }) { Text("Salvar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } }
        )
    }
    if (naming) {
        var nome by rememberSaveable { mutableStateOf(document.titulo) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Salvar como modelo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O modelo guarda quem assina e onde fica cada campo. Ao subir um PDF com o mesmo layout (ex.: outro NDA), as caixas entram posicionadas sozinhas. \"Usar um modelo\" na tela inicial também cria um rascunho com este mesmo PDF.")
                    OutlinedTextField(nome, { nome = it.take(200) }, label = { Text("Nome do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    extras.onSaveTemplate(nome)
                }, enabled = nome.isNotBlank()) { Text("Salvar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RemindersCard(document: Document, onReminder: () -> Unit) {
    val pending = document.signers.filter { it.isPending || it.isViewed }
    val reminders = document.events.filter { it.tipo == AuditType.REMINDER }
    SectionCard(title = "Lembretes", trailing = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
        Text(
            if (document.isPending) "Aguardando ${pending.size} signatário(s): ${pending.joinToString { it.signer?.nome.orEmpty() }}."
            else "Lembretes só valem para documentos aguardando assinatura.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onReminder, enabled = document.isPending && pending.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enviar lembrete agora")
        }
        Text(
            if (BuildConfig.USE_API) "O lembrete fica registrado na auditoria."
            else "No app, o lembrete fica registrado na auditoria e o documento continua em \"Pendentes\" para quem ainda não assinou.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reminders.isNotEmpty()) {
            HorizontalDivider()
            Text("Enviados (${reminders.size})", style = MaterialTheme.typography.labelLarge)
            reminders.asReversed().forEach { e -> TimelineRow(Icons.Default.NotificationsActive, StatusColors.Blue, e.descricao, formatDateTime(e.timestamp)) }
        }
    }
}

@Composable
private fun PermissionsCard(document: Document, contacts: List<User>, extras: DetailExtras) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val signerIds = document.signers.map { it.signerId }.toSet()
    val observerIds = document.observadores.map { it.id }.toSet()
    val candidates = contacts.filter { it.id !in signerIds && it.id !in observerIds }
    SectionCard(title = "Quem tem acesso", trailing = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
        PermissionRow(document.owner?.nome ?: "Você", "Dono: configura, envia e acompanha", null)
        document.signers.forEach { p -> PermissionRow(p.signer?.nome.orEmpty(), "Signatário (${p.roleLabel.label.lowercase()}): visualiza e assina", null) }
        HorizontalDivider()
        Text("Observadores", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Acompanham o documento e baixam o PDF final, sem assinar. Valem a partir do envio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (document.observadores.isEmpty()) Text("Nenhum observador.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        document.observadores.forEach { o -> PermissionRow(o.nome, o.email) { extras.onRemoveObserver(o.id) } }
        OutlinedButton(onClick = { adding = true }, enabled = candidates.isNotEmpty() && !document.isCancelled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adicionar observador")
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Adicionar observador") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    candidates.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable {
                            adding = false
                            extras.onAddObserver(c.id)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.initials, size = 32)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.nome)
                                Text(c.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PermissionRow(name: String, detail: String, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(User(id = "", nome = name, email = "").initials, size = 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remover $name") }
    }
}

@Composable
private fun HashCard(document: Document) {
    SectionCard(title = "Integridade") {
        Text("Versão ${document.versao}", style = MaterialTheme.typography.bodyMedium)
        Text("SHA-256 do original:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(document.hashSha256, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        document.hashFinal?.let {
            Text("SHA-256 do PDF final assinado:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun HeaderCard(document: Document) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    WebCard(modifier = Modifier.fillMaxWidth()) {
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
                val done = document.requiredSigned >= document.requiredSigners
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusColors.Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${document.requiredSigned} / ${document.requiredSigners}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("Assinaturas no documento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { document.requiredSigned / total.toFloat() },
                    color = if (done) StatusColors.Green else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
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
        Box {
            Avatar(p.signer?.initials.orEmpty(), color = signerColor(index), contentColor = Color.White)
            if (p.isSigned) Icon(
                Icons.Default.CheckCircle, contentDescription = "Assinou", tint = StatusColors.Green,
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
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
            if (p.isSigned) StatusBadge("Assinou como ${p.roleLabel.label.lowercase()}", color = StatusColors.Green, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        if (!p.isSigned) SignerStatusChip(p.status)
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
                AuditType.REMINDER -> Icons.Default.NotificationsActive to StatusColors.Blue
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
fun DocumentDetailAdminPreview() = EasySignTheme {
    DocumentDetailContent(DocumentDetailUiState(document = PreviewData.document, localPdfs = PreviewData.localPdfs, currentUserId = PreviewData.admin.id), {}, {}, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun DocumentDetailSignerPreview() = EasySignTheme {
    DocumentDetailContent(DocumentDetailUiState(document = PreviewData.signerDocument, currentUserId = PreviewData.ana.id), {}, {}, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun DocumentDetailSignaturesPreview() = EasySignTheme {
    DocumentDetailContent(DocumentDetailUiState(document = PreviewData.document, currentUserId = PreviewData.admin.id), {}, {}, {}, {}, {}, {}, {}, initialTab = 1)
}

@ScreenPreviews
@Composable
fun DocumentDetailPermissionsPreview() = EasySignTheme {
    DocumentDetailContent(
        DocumentDetailUiState(
            document = PreviewData.document.copy(observadores = listOf(PreviewData.signers.last())),
            currentUserId = PreviewData.admin.id, contacts = PreviewData.signers
        ),
        {}, {}, {}, {}, {}, {}, {}, initialTab = 3
    )
}
