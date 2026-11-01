@file:OptIn(ExperimentalMaterial3Api::class)

package com.letsign.mobile.presentation.ui.screens.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.ui.components.DocumentStatusChip
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.ErrorBanner
import com.letsign.mobile.presentation.ui.components.LetsSignTopAppBar
import com.letsign.mobile.presentation.ui.components.SignerStatusChip
import com.letsign.mobile.presentation.ui.components.StatusBadge
import com.letsign.mobile.presentation.ui.components.WebCard
import com.letsign.mobile.presentation.theme.StatusColors
import java.util.Calendar
import com.letsign.mobile.presentation.ui.components.label
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.util.formatDate
import com.letsign.mobile.presentation.viewmodel.AdminFilter
import com.letsign.mobile.presentation.viewmodel.DocumentListUiState
import com.letsign.mobile.presentation.viewmodel.DocumentListViewModel
import com.letsign.mobile.presentation.viewmodel.SignerTab
import com.letsign.mobile.domain.model.DocumentTemplate
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable

// ---------------------------------------------------------------- Admin

@Composable
fun AdminHomeScreen(
    onNewDocument: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSigners: () -> Unit,
    onLogout: () -> Unit,
    onEditDraft: (String) -> Unit = {},
    onOpenEsg: () -> Unit = {},
    viewModel: DocumentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Rascunho criado a partir de um modelo: segue direto para o assistente revisar e enviar.
    LaunchedEffect(state.openDraftId) {
        state.openDraftId?.let { id ->
            viewModel.consumeOpenDraft()
            onEditDraft(id)
        }
    }
    AdminHomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onFilter = viewModel::setAdminFilter,
        onNewDocument = onNewDocument,
        onOpenDocument = onOpenDocument,
        onOpenSigners = onOpenSigners,
        onLogout = onLogout,
        onUseTemplate = viewModel::useTemplate,
        onDeleteTemplate = viewModel::deleteTemplate,
        onOpenEsg = onOpenEsg
    )
}

/** Tela inicial do Admin sem ViewModel: é o que os previews renderizam. */
@Composable
fun AdminHomeContent(
    state: DocumentListUiState,
    onRefresh: () -> Unit,
    onFilter: (AdminFilter) -> Unit,
    onNewDocument: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSigners: () -> Unit,
    onLogout: () -> Unit,
    onUseTemplate: (DocumentTemplate) -> Unit = {},
    onDeleteTemplate: (DocumentTemplate) -> Unit = {},
    onOpenEsg: () -> Unit = {}
) {
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    if (showTemplates) TemplatesDialog(state.templates, { showTemplates = false; onUseTemplate(it) }, onDeleteTemplate) { showTemplates = false }
    Scaffold(
        topBar = {
            LetsSignTopAppBar(
                title = "Meus Documentos",
                subtitle = state.user?.nome,
                actions = {
                    IconButton(onClick = onOpenSigners) { Icon(Icons.Default.Group, contentDescription = "Contatos") }
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDocument,
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text("Enviar documento") }
            )
        }
    ) { padding ->
        var query by rememberSaveable { mutableStateOf("") }
        var folder by rememberSaveable { mutableStateOf<String?>(null) }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AdminFilter.entries) { filter ->
                    FilterChip(
                        selected = state.adminFilter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text("${filter.label} (${state.count(filter)})") }
                    )
                }
            }
            DocumentList(
                documents = state.visibleAdminDocuments.filter { folder == null || it.pasta == folder }.search(query),
                isRefreshing = state.isRefreshing,
                error = state.error,
                hasAnyDocument = state.documents.isNotEmpty(),
                onRefresh = onRefresh,
                onOpen = onOpenDocument,
                forSigner = false,
                header = {
                    HomeHeader(state.user?.nome, query, { query = it }, folders = state.folders, folder = folder, onFolder = { folder = it }) {
                        QuickCard(Icons.Default.CloudUpload, "Enviar um documento para assinatura", "Escolha um PDF ou Word para coletar assinaturas", null, onNewDocument)
                        QuickCard(Icons.Default.ContentPasteGo, "Usar um modelo", "Comece de um documento já configurado", state.templates.size.takeIf { it > 0 }) { showTemplates = true }
                        QuickCard(Icons.Default.HourglassEmpty, "Assinaturas de terceiros", "Documentos aguardando assinatura de terceiros", state.count(AdminFilter.WAITING)) { onFilter(AdminFilter.WAITING) }
                        QuickCard(Icons.Default.TaskAlt, "Concluídos", "Documentos com todas as assinaturas", state.count(AdminFilter.COMPLETED)) { onFilter(AdminFilter.COMPLETED) }
                        EsgQuickCard(state.documents, onOpenEsg)
                    }
                },
                empty = {
                    if (state.documents.isEmpty()) EmptyState(
                        icon = Icons.Default.Description,
                        title = "Nenhum documento ainda",
                        subtitle = "Envie um PDF para coletar assinaturas.",
                        actionText = "Novo documento",
                        onAction = onNewDocument
                    ) else EmptyState(
                        icon = Icons.Default.Description,
                        title = if (query.isNotBlank()) "Nada encontrado" else "Nada em \"${state.adminFilter.label}\"",
                        subtitle = if (query.isNotBlank()) "Nenhum documento com \"$query\"." else "Escolha outro filtro acima."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------- Signatário

@Composable
fun SignerHomeScreen(
    onOpenDocument: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenEsg: () -> Unit = {},
    viewModel: DocumentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignerHomeContent(state = state, onRefresh = viewModel::refresh, onTab = viewModel::setSignerTab, onOpenDocument = onOpenDocument, onLogout = onLogout, onOpenEsg = onOpenEsg)
}

/** Tela inicial do Signatário sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignerHomeContent(
    state: DocumentListUiState,
    onRefresh: () -> Unit,
    onTab: (SignerTab) -> Unit,
    onOpenDocument: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenEsg: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            LetsSignTopAppBar(
                title = "Meus Documentos",
                subtitle = state.user?.nome,
                actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair") } }
            )
        }
    ) { padding ->
        var query by rememberSaveable { mutableStateOf("") }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.signerTab.ordinal) {
                SignerTab.entries.forEach { tab ->
                    Tab(
                        selected = state.signerTab == tab,
                        onClick = { onTab(tab) },
                        text = { Text("${tab.label} (${state.count(tab)})", maxLines = 1) }
                    )
                }
            }
            DocumentList(
                documents = state.visibleSignerDocuments.search(query),
                isRefreshing = state.isRefreshing,
                error = state.error,
                hasAnyDocument = state.documents.isNotEmpty(),
                onRefresh = onRefresh,
                onOpen = onOpenDocument,
                forSigner = true,
                header = {
                    HomeHeader(state.user?.nome, query, { query = it }) {
                        QuickCard(Icons.Default.Draw, "Minhas assinaturas", "Documentos aguardando minha assinatura", state.count(SignerTab.PENDING)) { onTab(SignerTab.PENDING) }
                        QuickCard(Icons.Default.TaskAlt, "Assinados", "Documentos que você já assinou", state.count(SignerTab.SIGNED)) { onTab(SignerTab.SIGNED) }
                        EsgQuickCard(state.documents, onOpenEsg)
                        if (state.count(SignerTab.FOLLOWING) > 0) {
                            QuickCard(Icons.Default.Visibility, "Acompanhando", "Documentos em que você é observador", state.count(SignerTab.FOLLOWING)) { onTab(SignerTab.FOLLOWING) }
                        }
                    }
                },
                empty = {
                    EmptyState(
                        icon = Icons.Default.Inbox,
                        title = when (state.signerTab) {
                            SignerTab.PENDING -> "Nada para assinar"
                            SignerTab.SIGNED -> "Nenhum documento assinado"
                            SignerTab.FOLLOWING -> "Nada para acompanhar"
                            SignerTab.CLOSED -> "Nenhum documento cancelado"
                        },
                        subtitle = "Quando alguém enviar um documento para você, ele aparece aqui."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------- Compartilhado

@Composable
private fun DocumentList(
    documents: List<Document>,
    isRefreshing: Boolean,
    error: String?,
    hasAnyDocument: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    forSigner: Boolean,
    header: @Composable () -> Unit,
    empty: @Composable () -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            item { header() }
            when {
                error != null && !hasAnyDocument -> item {
                    EmptyState(icon = Icons.Default.CloudOff, title = "Não foi possível carregar", subtitle = error, actionText = "Tentar novamente", onAction = onRefresh)
                }
                documents.isEmpty() && !isRefreshing -> item { empty() }
                else -> {
                    // Offline-first: com erro de rede a lista continua mostrando o que está salvo no aparelho.
                    if (error != null) item { ErrorBanner("$error Mostrando dados salvos no aparelho.", "Tentar", onRefresh) }
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(document = document, forSigner = forSigner, onClick = { onOpen(document.id) })
                    }
                }
            }
        }
    }
}

private fun List<Document>.search(query: String): List<Document> {
    val q = query.trim()
    return if (q.isEmpty()) this else filter { d ->
        d.titulo.contains(q, ignoreCase = true) || d.nomeArquivo.contains(q, ignoreCase = true) ||
            d.pasta.contains(q, ignoreCase = true) || d.categorias.any { it.contains(q, ignoreCase = true) }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

/** "Boa noite, Fulano!" + cards de acesso rápido + busca, como o "Acesso rápido" da web. */
@Composable
private fun HomeHeader(
    name: String?,
    query: String,
    onQuery: (String) -> Unit,
    folders: List<String> = emptyList(),
    folder: String? = null,
    onFolder: (String?) -> Unit = {},
    cards: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("${greeting()}, ${name?.substringBefore(' ')?.ifBlank { null } ?: "tudo bem"}!", style = MaterialTheme.typography.titleLarge)
            Text("Selecione uma opção abaixo para começar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards() }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Pesquisar...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        )
        // Pastas: só aparecem quando há mais de uma.
        if (folders.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = folder == null, onClick = { onFolder(null) }, label = { Text("Todas as pastas") })
                folders.forEach { f ->
                    FilterChip(
                        selected = folder == f, onClick = { onFolder(f) }, label = { Text(f) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
        Text("Documentos recentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickCard(icon: ImageVector, title: String, subtitle: String, count: Int?, onClick: () -> Unit) {
    WebCard(modifier = Modifier.width(200.dp).height(150.dp), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.weight(1f))
                if (count != null) StatusBadge("$count", color = StatusColors.Green)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DocumentCard(document: Document, forSigner: Boolean, onClick: () -> Unit) {
    WebCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = document.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = if (forSigner) "De ${document.owner?.nome ?: "remetente"} • ${document.meuPapel?.label ?: ""}"
                    else "${document.nomeArquivo} • ${document.totalPaginas} pág."
                    Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!forSigner && (document.pasta != Document.DEFAULT_FOLDER || document.categorias.isNotEmpty())) {
                        Text(
                            text = (listOf(document.pasta) + document.categorias).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (forSigner && document.minhaSituacao != null && !document.isCancelled) SignerStatusChip(document.minhaSituacao)
                else DocumentStatusChip(document.status)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val left = when {
                    forSigner && document.minhaSituacao == SignerStatus.SIGNED -> "Assinado em ${formatDate(document.assinadoEm)}"
                    forSigner -> "Recebido em ${formatDate(document.enviadoEm ?: document.criadoEm)}"
                    else -> "Criado em ${formatDate(document.criadoEm)}"
                }
                Text(text = left, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val total = document.totalSignatarios
                if (!forSigner && total != null && document.status != DocumentStatus.DRAFT) {
                    Text(text = "${document.assinados ?: 0}/$total assinaturas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (forSigner) {
                    Text(text = "Documento ${document.status.label().lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Card de acesso ao painel ESG, com as folhas poupadas como contador. */
@Composable
private fun EsgQuickCard(documents: List<Document>, onClick: () -> Unit) {
    val impact = com.letsign.mobile.domain.rules.EsgImpact.of(documents)
    QuickCard(Icons.Default.Eco, "Impacto ESG ${impact.level.emoji}", "${impact.folhas} folhas, ${impact.litrosAgua.toLong()} L de água poupados", null, onClick)
}

/** Lista de modelos salvos: tocar cria um rascunho igual ao modelo. */
@Composable
private fun TemplatesDialog(templates: List<DocumentTemplate>, onUse: (DocumentTemplate) -> Unit, onDelete: (DocumentTemplate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usar um modelo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (templates.isEmpty()) {
                    Text("Nenhum modelo ainda. Abra um documento e toque em \"Salvar como modelo\" na aba Envelope.")
                }
                templates.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { onUse(t) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentPasteGo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.nome, style = MaterialTheme.typography.bodyLarge)
                            Text("Criado em ${formatDate(t.criadoEm)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(t) }) { Icon(Icons.Default.Delete, contentDescription = "Excluir modelo ${t.nome}") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

// ---------------------------------------------------------------- Previews

@ScreenPreviews
@Composable
fun AdminHomePreview() = LetsSignTheme {
    AdminHomeContent(DocumentListUiState(user = PreviewData.admin, documents = PreviewData.documents), {}, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun AdminHomeEmptyPreview() = LetsSignTheme {
    AdminHomeContent(DocumentListUiState(user = PreviewData.admin), {}, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun AdminHomeOfflinePreview() = LetsSignTheme {
    AdminHomeContent(
        DocumentListUiState(user = PreviewData.admin, documents = PreviewData.documents.take(2), error = "Erro de conexão. Verifique sua internet."),
        {}, {}, {}, {}, {}, {}
    )
}

@ScreenPreviews
@Composable
fun SignerHomePreview() = LetsSignTheme {
    SignerHomeContent(DocumentListUiState(user = PreviewData.ana, documents = PreviewData.signerDocuments), {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun SignerHomeSignedPreview() = LetsSignTheme {
    SignerHomeContent(DocumentListUiState(user = PreviewData.ana, documents = PreviewData.signerDocuments, signerTab = SignerTab.SIGNED), {}, {}, {}, {})
}
