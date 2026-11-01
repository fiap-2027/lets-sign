package com.letsign.mobile.presentation.ui.screens.wizard

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.BuildConfig
import com.letsign.mobile.data.pdf.PdfDocumentRenderer
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.presentation.viewmodel.AppliedTemplate
import androidx.compose.material.icons.filled.AutoAwesome
import com.letsign.mobile.domain.model.SignerGroup
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Sell
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.rules.DocumentRules
import com.letsign.mobile.presentation.theme.EasySignTheme
import com.letsign.mobile.presentation.ui.components.Avatar
import com.letsign.mobile.presentation.ui.components.BottomActions
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.ErrorBanner
import com.letsign.mobile.presentation.ui.components.FieldBox
import com.letsign.mobile.presentation.ui.components.EasySignTopAppBar
import com.letsign.mobile.presentation.ui.components.PdfLoad
import com.letsign.mobile.presentation.ui.components.PdfPageView
import com.letsign.mobile.presentation.ui.components.SectionCard
import com.letsign.mobile.presentation.ui.components.rememberPdfRenderer
import com.letsign.mobile.presentation.ui.components.signerColor
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.util.formatBytes
import com.letsign.mobile.presentation.viewmodel.NewDocumentViewModel
import com.letsign.mobile.presentation.viewmodel.SendOutcome
import com.letsign.mobile.presentation.viewmodel.WizardStep
import com.letsign.mobile.presentation.viewmodel.WizardUiState

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Ações do assistente agrupadas (os previews usam os padrões que não fazem nada). */
class WizardActions(
    val onClose: () -> Unit = {},
    val onPickPdf: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onFolder: (String) -> Unit = {},
    val onCategories: (String) -> Unit = {},
    val onAddGroup: (SignerGroup) -> Unit = {},
    val onFieldType: (FieldType) -> Unit = {},
    val onApplyTemplate: (String) -> Unit = {},
    val onUndoTemplate: () -> Unit = {},
    val onSaveTemplate: (String) -> Unit = {},
    val onToggleParticipant: (User) -> Unit = {},
    val onRole: (String, RoleLabel) -> Unit = { _, _ -> },
    val onRequired: (String, Boolean) -> Unit = { _, _ -> },
    val onRemoveParticipant: (String) -> Unit = {},
    val onReplaceParticipant: (String, User) -> Unit = { _, _ -> },
    val onOpenSigners: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onActiveSigner: (String) -> Unit = {},
    val onAddField: () -> Unit = {},
    val onSelectField: (String?) -> Unit = {},
    val onMoveField: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onResizeField: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onDeleteField: (String) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onGoTo: (WizardStep) -> Unit = {},
    val onSend: () -> Unit = {},
    val onOpenDocument: (String) -> Unit = {}
)

@Composable
fun NewDocumentScreen(
    onClose: () -> Unit,
    onOpenSigners: () -> Unit,
    onOpenDocument: (String) -> Unit,
    viewModel: NewDocumentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Seletor nativo de arquivos (Storage Access Framework): não precisa de permissão de armazenamento.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::onPdfPicked)
    }
    val hasWork = state.pdf != null || state.titulo.isNotBlank()
    val close = { if (hasWork && !state.isFinished && !state.editMode) confirmDiscard = true else onClose() }

    BackHandler(enabled = !state.isFinished) { if (!viewModel.back()) close() }

    NewDocumentContent(
        state = state,
        actions = WizardActions(
            onClose = close,
            onPickPdf = { picker.launch(arrayOf("application/pdf", DOCX_MIME, "application/msword")) },
            onTitle = viewModel::setTitle,
            onFolder = viewModel::setFolder,
            onCategories = viewModel::setCategories,
            onAddGroup = viewModel::addGroup,
            onFieldType = viewModel::setFieldType,
            onApplyTemplate = viewModel::applyTemplate,
            onUndoTemplate = viewModel::undoTemplate,
            onSaveTemplate = viewModel::saveAsTemplate,
            onToggleParticipant = viewModel::toggleParticipant,
            onRole = viewModel::setRole,
            onRequired = viewModel::setRequired,
            onRemoveParticipant = viewModel::removeParticipant,
            onReplaceParticipant = viewModel::replaceParticipant,
            onOpenSigners = onOpenSigners,
            onPage = viewModel::setPage,
            onActiveSigner = viewModel::setActiveSigner,
            onAddField = viewModel::addField,
            onSelectField = viewModel::selectField,
            onMoveField = viewModel::moveField,
            onResizeField = viewModel::resizeField,
            onDeleteField = viewModel::deleteField,
            onMessage = viewModel::setMessage,
            onNext = viewModel::next,
            onBack = { viewModel.back() },
            onGoTo = viewModel::goTo,
            onSend = viewModel::send,
            onOpenDocument = onOpenDocument
        )
    )

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Descartar documento?",
            text = "O documento ainda não foi enviado. As informações preenchidas serão perdidas.",
            confirmText = "Descartar",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

/** Assistente sem ViewModel: é o que os previews renderizam. */
@Composable
fun NewDocumentContent(state: WizardUiState, actions: WizardActions) {
    val load = rememberPdfRenderer(state.pdf?.path)
    val renderer = (load as? PdfLoad.Ready)?.renderer
    val outcome = state.outcome

    Scaffold(
        topBar = {
            EasySignTopAppBar(
                title = when {
                    !state.editMode -> "Novo documento"
                    state.versao > 1 -> "Nova versão (v${state.versao})"
                    else -> "Continuar rascunho"
                },
                subtitle = if (outcome is SendOutcome.Success) null else "Etapa ${state.step.ordinal + 1} de ${WizardStep.entries.size}: ${state.step.title}",
                showBackButton = true,
                onBackClick = actions.onClose
            )
        },
        bottomBar = {
            if (outcome !is SendOutcome.Success && !state.isLoading && state.loadError == null) {
                Column {
                    state.stepError?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ErrorBanner(it) } }
                    // Fixada junto dos botões: com a revisão rolada até o fim, o erro continua visível.
                    (outcome as? SendOutcome.Failure)?.let {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ErrorBanner("${it.mensagem} Seus dados foram mantidos.", "Tentar novamente", actions.onSend)
                        }
                    }
                    val last = state.step == WizardStep.REVISAO
                    BottomActions(
                        primaryText = if (last) "Enviar para assinatura" else "Avançar",
                        onPrimary = if (last) actions.onSend else actions.onNext,
                        secondaryText = if (state.step != WizardStep.DADOS) "Voltar" else null,
                        onSecondary = actions.onBack,
                        loading = state.isSending || state.isImporting
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CenteredLoading()
                state.loadError != null -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir o rascunho", state.loadError, "Voltar", actions.onClose)
                outcome is SendOutcome.Success -> SuccessStep(outcome, state, actions)
                else -> Column(Modifier.fillMaxSize()) {
                    StepIndicator(state.step, actions.onGoTo)
                    HorizontalDivider()
                    Box(Modifier.weight(1f)) {
                        when (state.step) {
                            WizardStep.DADOS -> DataStep(state, actions)
                            WizardStep.SIGNATARIOS -> SignersStep(state, actions)
                            WizardStep.CAMPOS -> FieldsStep(state, renderer, actions)
                            WizardStep.REVISAO -> ReviewStep(state, renderer, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: WizardStep, onGoTo: (WizardStep) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        WizardStep.entries.forEach { step ->
            val done = step.ordinal < current.ordinal
            val active = step == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable(enabled = done) { onGoTo(step) }
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (done || active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        else Text("${step.ordinal + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    step.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Etapa 1

@Composable
private fun DataStep(state: WizardUiState, actions: WizardActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.versao > 1) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Versão ${state.versao} do documento. A versão ${state.versao - 1} continua preservada no histórico. " +
                        "Os signatários e os campos foram mantidos: revise as próximas etapas antes de reenviar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Text("Escolha o arquivo e dê um título ao documento.", style = MaterialTheme.typography.bodyLarge)
        }

        val pdf = state.pdf
        if (pdf == null) {
            OutlinedCard(onClick = actions.onPickPdf, enabled = !state.isImporting, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isImporting) CircularProgressIndicator() else Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (state.isImporting) "Validando o arquivo..." else "Selecionar arquivo do celular", style = MaterialTheme.typography.titleMedium)
                    Text("Formatos aceitos: .PDF ou .DOCX (Word, convertido em PDF só com o texto). Até 15 MB.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pdf.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${pdf.info.pageCount} página(s) • ${formatBytes(pdf.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!state.editMode) TextButton(onClick = actions.onPickPdf, enabled = !state.isImporting) { Text("Trocar") }
                }
            }
        }
        state.pdfError?.let { ErrorBanner(it, "Escolher outro", actions.onPickPdf) }
        if (state.isMatching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Procurando um modelo compatível...", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.appliedTemplate?.let { TemplateBanner(it, actions.onUndoTemplate) }

        OutlinedTextField(
            value = state.titulo,
            onValueChange = actions.onTitle,
            label = { Text("Título do documento") },
            enabled = !state.editMode,
            singleLine = true,
            supportingText = { Text(if (state.editMode) "O título não muda depois que o documento é criado." else "${state.titulo.length}/${DocumentRules.MAX_TITLE}") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Organização", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.pasta,
            onValueChange = actions.onFolder,
            label = { Text("Pasta de destino") },
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
            singleLine = true,
            supportingText = { Text("Agrupa documentos na tela inicial.") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.categorias,
            onValueChange = actions.onCategories,
            label = { Text("Categorias (opcional)") },
            leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
            placeholder = { Text("Ex.: Contratos, RH") },
            singleLine = true,
            supportingText = { Text("Separe por vírgula. Ajudam na busca.") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------- Etapa 2

@Composable
private fun SignersStep(state: WizardUiState, actions: WizardActions) {
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedIds = state.participants.map { it.signer.id }.toSet()
    val available = state.availableSigners.filter { it.id !in selectedIds }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Quem vai assinar? Classifique cada pessoa e marque quem é obrigatório.", style = MaterialTheme.typography.bodyLarge)

        SectionCard(title = "Selecionados (${state.participants.size})") {
            if (state.participants.isEmpty()) {
                Text("Nenhum signatário ainda. Adicione abaixo (mínimo 1).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.participants.forEachIndexed { index, p ->
                if (index > 0) HorizontalDivider()
                SelectedParticipant(p, index, actions, onReplace = { replacing = p.signer.id })
            }
        }

        if (state.groups.isNotEmpty()) {
            SectionCard(title = "Grupos") {
                Text("Adiciona de uma vez os contatos do grupo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.groups.forEach { g ->
                        AssistChip(
                            onClick = { actions.onAddGroup(g) },
                            label = { Text("${g.nome} (${g.memberIds.size})") },
                            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        SectionCard(title = "Disponíveis (${available.size})", trailing = {
            TextButton(onClick = actions.onOpenSigners) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cadastrar")
            }
        }) {
            if (state.availableSigners.isEmpty()) {
                Text("Você ainda não cadastrou signatários. Toque em \"Cadastrar\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (available.isEmpty()) {
                Text("Todos os signatários cadastrados já foram adicionados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            available.forEach { user ->
                Row(Modifier.fillMaxWidth().clickable { actions.onToggleParticipant(user) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.initials)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.Add, contentDescription = "Adicionar ${user.nome}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    replacing?.let { oldId ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Substituir signatário") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O novo signatário fica com o mesmo papel e com os campos já posicionados.")
                    if (available.isEmpty()) Text("Não há outros signatários cadastrados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    available.forEach { user ->
                        Row(Modifier.fillMaxWidth().clickable {
                            actions.onReplaceParticipant(oldId, user)
                            replacing = null
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(user.initials, size = 32)
                            Spacer(Modifier.width(12.dp))
                            Text(user.nome)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SelectedParticipant(p: DraftParticipant, index: Int, actions: WizardActions, onReplace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.signer.initials, color = signerColor(index), contentColor = Color.White)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.signer.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(p.signer.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onReplace) { Icon(Icons.Default.SwapHoriz, contentDescription = "Substituir ${p.signer.nome}") }
            IconButton(onClick = { actions.onRemoveParticipant(p.signer.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remover ${p.signer.nome}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoleLabel.entries.forEach { role ->
                FilterChip(
                    selected = p.roleLabel == role,
                    onClick = { actions.onRole(p.signer.id, role) },
                    label = { Text(role.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Assinatura obrigatória", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = p.required, onCheckedChange = { actions.onRequired(p.signer.id, it) })
        }
    }
}

// ---------------------------------------------------------------- Etapa 3

@Composable
private fun FieldsStep(state: WizardUiState, renderer: PdfDocumentRenderer?, actions: WizardActions) {
    val pageIndex = state.currentPage - 1
    val aspect = state.pdf?.info?.pageAspectRatios?.getOrNull(pageIndex) ?: 0.707f
    val activeIndex = state.participantIndex(state.activeSignerId.orEmpty())
    val active = state.participants.getOrNull(activeIndex)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.appliedTemplate?.let { TemplateBanner(it, actions.onUndoTemplate) }
        Text("Escolha o signatário, a página e toque em \"+ Campo\". Arraste para posicionar.", style = MaterialTheme.typography.bodyMedium)
        if (state.templates.isNotEmpty()) {
            var choosing by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Aplicar um modelo")
            }
            if (choosing) {
                AlertDialog(
                    onDismissRequest = { choosing = false },
                    title = { Text("Aplicar um modelo") },
                    text = {
                        Column {
                            Text("Os campos e signatários do modelo substituem os atuais (dá para desfazer).", style = MaterialTheme.typography.bodySmall)
                            state.templates.forEach { t ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    choosing = false
                                    actions.onApplyTemplate(t.id)
                                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(t.nome)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancelar") } }
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.participants, key = { it.signer.id }) { p ->
                val index = state.participantIndex(p.signer.id)
                val count = state.fields.count { it.signerId == p.signer.id }
                FilterChip(
                    selected = p.signer.id == state.activeSignerId,
                    onClick = { actions.onActiveSigner(p.signer.id) },
                    label = { Text("${p.signer.nome.substringBefore(' ')} ($count)") },
                    leadingIcon = { Box(Modifier.size(12.dp).background(signerColor(index), CircleShape)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = signerColor(index).copy(alpha = 0.2f)),
                    border = if (count == 0) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.onPage(state.currentPage - 1) }, enabled = state.currentPage > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Página anterior")
            }
            Text(
                "Página ${state.currentPage} de ${state.pageCount}",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { actions.onPage(state.currentPage + 1) }, enabled = state.currentPage < state.pageCount) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próxima página")
            }
        }

        Text("Campos do signatário", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldType.entries.forEach { t ->
                FilterChip(
                    selected = state.newFieldType == t,
                    onClick = { actions.onFieldType(t) },
                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Button(onClick = actions.onAddField, enabled = active != null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("${state.newFieldType.label} para ${active?.signer?.nome?.substringBefore(' ') ?: "..."} (pág. ${state.currentPage})", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        EditablePage(
            renderer = renderer,
            pageIndex = pageIndex,
            aspect = aspect,
            fields = state.fieldsOnPage(state.currentPage),
            participants = state.participants,
            selectedId = state.selectedFieldId,
            actions = actions
        )

        Text(
            "Arraste o campo para mover • puxe a bolinha do canto para redimensionar • toque em ✕ para excluir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val pagesWithFields = state.fields.map { it.page }.distinct().sorted()
        if (pagesWithFields.isNotEmpty()) {
            Text("Páginas com campos: ${pagesWithFields.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Página com campos arrastáveis. Posição e tamanho saem em fração da página (0..1). */
@Composable
private fun EditablePage(
    renderer: PdfDocumentRenderer?,
    pageIndex: Int,
    aspect: Float,
    fields: List<DraftField>,
    participants: List<DraftParticipant>,
    selectedId: String?,
    actions: WizardActions
) {
    PdfPageView(
        renderer = renderer,
        pageIndex = pageIndex,
        aspectRatio = aspect,
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { actions.onSelectField(null) } }
    ) { pageWidth, pageHeight ->
        val density = LocalDensity.current
        val wPx = with(density) { pageWidth.toPx() }
        val hPx = with(density) { pageHeight.toPx() }
        fields.forEach { f ->
            key(f.localId) {
                val index = participants.indexOfFirst { it.signer.id == f.signerId }
                val color = signerColor(index)
                val selected = f.localId == selectedId
                FieldBox(
                    x = f.x, y = f.y, width = f.width, height = f.height,
                    pageWidth = pageWidth, pageHeight = pageHeight,
                    color = color,
                    label = (participants.getOrNull(index)?.signer?.nome?.substringBefore(' ') ?: "?") +
                        if (f.tipo == FieldType.ASSINATURA) "" else " • ${f.tipo.label}",
                    highlighted = selected,
                    modifier = Modifier
                        .pointerInput(f.localId, wPx, hPx) {
                            detectDragGestures(onDragStart = { actions.onSelectField(f.localId) }) { change, drag ->
                                change.consume()
                                actions.onMoveField(f.localId, drag.x / wPx, drag.y / hPx)
                            }
                        }
                        .pointerInput(f.localId) { detectTapGestures { actions.onSelectField(f.localId) } }
                ) {
                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp).size(24.dp)
                                .clickable { actions.onDeleteField(f.localId) }
                        ) { Icon(Icons.Default.Close, contentDescription = "Excluir campo", modifier = Modifier.padding(4.dp)) }
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp).size(18.dp)
                                .background(color, CircleShape)
                                .pointerInput(f.localId, wPx, hPx) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        actions.onResizeField(f.localId, drag.x / wPx, drag.y / hPx)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Etapa 4

@Composable
private fun ReviewStep(state: WizardUiState, renderer: PdfDocumentRenderer?, actions: WizardActions) {
    var showFullPdf by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Documento", trailing = { TextButton(onClick = { actions.onGoTo(WizardStep.DADOS) }) { Text("Editar") } }) {
            Text(state.titulo, style = MaterialTheme.typography.titleMedium)
            state.pdf?.let {
                Text("${it.displayName} • ${it.info.pageCount} página(s) • ${formatBytes(it.sizeBytes)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val cats = DocumentRules.parseCategories(state.categorias)
            Text("Pasta: ${state.pasta.ifBlank { Document.DEFAULT_FOLDER }}" + if (cats.isEmpty()) "" else " • Categorias: ${cats.joinToString()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showFullPdf = true }, enabled = state.pdf != null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ver PDF completo")
            }
        }

        SectionCard(title = "Signatários (${state.participants.size})", trailing = { TextButton(onClick = { actions.onGoTo(WizardStep.SIGNATARIOS) }) { Text("Editar") } }) {
            state.participants.forEachIndexed { index, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.signer.initials, color = signerColor(index), contentColor = Color.White, size = 32)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.signer.nome, style = MaterialTheme.typography.bodyMedium)
                        Text("${p.roleLabel.label} • ${if (p.required) "obrigatório" else "opcional"} • " +
                            state.fields.filter { it.signerId == p.signer.id }.groupingBy { it.tipo }.eachCount().entries.joinToString { (t, n) -> "$n ${t.label.lowercase()}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionCard(title = "Campos e páginas (${state.fields.size})", trailing = { TextButton(onClick = { actions.onGoTo(WizardStep.CAMPOS) }) { Text("Editar") } }) {
            val byPage = state.fields.groupBy { it.page }.toSortedMap()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(byPage.keys.toList()) { page ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PdfPageView(
                            renderer = renderer,
                            pageIndex = page - 1,
                            aspectRatio = state.pdf?.info?.pageAspectRatios?.getOrNull(page - 1) ?: 0.707f,
                            modifier = Modifier.width(130.dp)
                        ) { w, h ->
                            byPage.getValue(page).forEach { f ->
                                FieldBox(f.x, f.y, f.width, f.height, w, h, signerColor(state.participantIndex(f.signerId)), label = "")
                            }
                        }
                        Text("Página $page", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.mensagem,
            onValueChange = actions.onMessage,
            label = { Text("Mensagem do convite (opcional)") },
            placeholder = { Text("Olá! Segue o documento para sua assinatura.") },
            minLines = 3,
            supportingText = { Text("${state.mensagem.length}/${DocumentRules.MAX_MESSAGE}") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            (if (BuildConfig.USE_API) "Ao enviar, cada signatário recebe um convite por e-mail. "
            else "Ao enviar, cada signatário encontra o convite ao entrar no app. ") +
                "Depois do envio, alterações exigem uma nova versão do documento.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showFullPdf) FullPdfDialog(state, renderer, onClose = { showFullPdf = false })
}

@Composable
private fun SuccessStep(outcome: SendOutcome.Success, state: WizardUiState, actions: WizardActions) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        var nome by rememberSaveable { mutableStateOf(state.titulo) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Salvar como modelo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Guarda quem assina e onde fica cada campo. Quando você subir um PDF com o mesmo layout (ex.: outro NDA), as caixas entram posicionadas sozinhas.")
                    OutlinedTextField(nome, { nome = it.take(DocumentRules.MAX_TITLE) }, label = { Text("Nome do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    actions.onSaveTemplate(nome)
                }, enabled = nome.isNotBlank()) { Text("Salvar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancelar") } }
        )
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (outcome.jaEnviado) "Documento já enviado" else "Documento enviado!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(outcome.mensagem, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { actions.onOpenDocument(outcome.documentId) }, modifier = Modifier.fillMaxWidth()) { Text("Acompanhar documento") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.onClose, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao início") }
        Spacer(Modifier.height(8.dp))
        val saved = state.templateSaved
        if (saved == null) {
            TextButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salvar como modelo")
            }
        } else {
            Text(saved, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Aviso de modelo aplicado: qual, quão parecido, quantos campos acharam o texto âncora, e "Desfazer". */
@Composable
private fun TemplateBanner(t: AppliedTemplate, onUndo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (t.auto) "Campos posicionados pelo modelo \"${t.nome}\"" else "Modelo \"${t.nome}\" aplicado",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val detalhes = buildList {
                    if (t.auto) add("${(t.score * 100).toInt()}% parecido")
                    add("${t.anchored} de ${t.total} campo(s) achados pelo texto")
                    if (t.anchored < t.total) add("os demais na posição do modelo")
                    if (t.missingSigners > 0) add("${t.missingSigners} contato(s) do modelo não existem mais")
                }
                Text(detalhes.joinToString(" • ") + ". Confira antes de enviar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            TextButton(onClick = onUndo) { Text("Desfazer") }
        }
    }
}

/** Requisito 5: o documento final antes do envio, com todas as páginas e os campos já posicionados. */
@Composable
private fun FullPdfDialog(state: WizardUiState, renderer: PdfDocumentRenderer?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = { EasySignTopAppBar(title = "Documento final", subtitle = state.titulo, showBackButton = true, onBackClick = onClose) }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.pageCount) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PdfPageView(renderer, index, state.pdf?.info?.pageAspectRatios?.getOrNull(index) ?: 0.707f) { w, h ->
                            state.fieldsOnPage(index + 1).forEach { f ->
                                val i = state.participantIndex(f.signerId)
                                val nome = state.participants.getOrNull(i)?.signer?.nome?.substringBefore(' ') ?: "?"
                                FieldBox(f.x, f.y, f.width, f.height, w, h, signerColor(i), label = nome)
                            }
                        }
                        Text("Página ${index + 1} de ${state.pageCount}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Previews

private val previewBase = WizardUiState(
    titulo = "Contrato de Prestação de Serviços",
    pdf = PreviewData.pickedPdf,
    availableSigners = PreviewData.signers,
    participants = PreviewData.draftParticipants,
    fields = PreviewData.draftFields,
    activeSignerId = PreviewData.joao.id,
    mensagem = "Olá! Segue o contrato para sua assinatura."
)

@ScreenPreviews
@Composable
fun WizardDataEmptyPreview() = EasySignTheme { NewDocumentContent(WizardUiState(), WizardActions()) }

@ScreenPreviews
@Composable
fun WizardDataPreview() = EasySignTheme { NewDocumentContent(previewBase, WizardActions()) }

@ScreenPreviews
@Composable
fun WizardInvalidPdfPreview() = EasySignTheme {
    NewDocumentContent(WizardUiState(pdfError = "O arquivo não é um PDF válido ou está corrompido."), WizardActions())
}

@ScreenPreviews
@Composable
fun WizardNewVersionPreview() = EasySignTheme {
    NewDocumentContent(previewBase.copy(editMode = true, versao = 2, serverDocumentId = "d-1"), WizardActions())
}

@ScreenPreviews
@Composable
fun WizardSignersPreview() = EasySignTheme { NewDocumentContent(previewBase.copy(step = WizardStep.SIGNATARIOS), WizardActions()) }

@ScreenPreviews
@Composable
fun WizardFieldsPreview() = EasySignTheme {
    NewDocumentContent(previewBase.copy(step = WizardStep.CAMPOS, selectedFieldId = "l-1"), WizardActions())
}

@ScreenPreviews
@Composable
fun WizardReviewPreview() = EasySignTheme { NewDocumentContent(previewBase.copy(step = WizardStep.REVISAO), WizardActions()) }

@ScreenPreviews
@Composable
fun WizardSendFailedPreview() = EasySignTheme {
    NewDocumentContent(previewBase.copy(step = WizardStep.REVISAO, outcome = SendOutcome.Failure("Erro de conexão. Verifique sua internet.")), WizardActions())
}

@ScreenPreviews
@Composable
fun WizardSuccessPreview() = EasySignTheme {
    NewDocumentContent(previewBase.copy(outcome = SendOutcome.Success("d-1", "Documento enviado com sucesso.", false)), WizardActions())
}

@ScreenPreviews
@Composable
fun WizardFullPdfPreview() = EasySignTheme { FullPdfDialog(previewBase, renderer = null, onClose = {}) }

@ScreenPreviews
@Composable
fun WizardTemplateAppliedPreview() = EasySignTheme {
    NewDocumentContent(
        previewBase.copy(
            step = WizardStep.CAMPOS,
            appliedTemplate = AppliedTemplate("NDA padrão", 0.87, 2, 3, 0, auto = true, emptyList(), emptyList()),
            templates = listOf(com.letsign.mobile.domain.model.DocumentTemplate("t1", "NDA padrão", "d1", "2026-10-31T12:00:00.000Z"))
        ),
        WizardActions()
    )
}
