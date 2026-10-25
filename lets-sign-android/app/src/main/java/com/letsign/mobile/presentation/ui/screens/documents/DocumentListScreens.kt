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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
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
import com.letsign.mobile.presentation.ui.components.label
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.util.formatDate
import com.letsign.mobile.presentation.viewmodel.AdminFilter
import com.letsign.mobile.presentation.viewmodel.DocumentListUiState
import com.letsign.mobile.presentation.viewmodel.DocumentListViewModel
import com.letsign.mobile.presentation.viewmodel.SignerTab

// ---------------------------------------------------------------- Admin

@Composable
fun AdminHomeScreen(
    onNewDocument: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSigners: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DocumentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminHomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onFilter = viewModel::setAdminFilter,
        onNewDocument = onNewDocument,
        onOpenDocument = onOpenDocument,
        onOpenSigners = onOpenSigners,
        onLogout = onLogout
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
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            LetsSignTopAppBar(
                title = "Meus Documentos",
                subtitle = state.user?.nome,
                actions = {
                    IconButton(onClick = onOpenSigners) { Icon(Icons.Default.Group, contentDescription = "Signatários") }
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDocument,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo documento") }
            )
        }
    ) { padding ->
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
                documents = state.visibleAdminDocuments,
                isRefreshing = state.isRefreshing,
                error = state.error,
                hasAnyDocument = state.documents.isNotEmpty(),
                onRefresh = onRefresh,
                onOpen = onOpenDocument,
                forSigner = false,
                empty = {
                    if (state.documents.isEmpty()) EmptyState(
                        icon = Icons.Default.Description,
                        title = "Nenhum documento ainda",
                        subtitle = "Envie um PDF para coletar assinaturas.",
                        actionText = "Novo documento",
                        onAction = onNewDocument
                    ) else EmptyState(
                        icon = Icons.Default.Description,
                        title = "Nada em \"${state.adminFilter.label}\"",
                        subtitle = "Escolha outro filtro acima."
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
    viewModel: DocumentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignerHomeContent(state = state, onRefresh = viewModel::refresh, onTab = viewModel::setSignerTab, onOpenDocument = onOpenDocument, onLogout = onLogout)
}

/** Tela inicial do Signatário sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignerHomeContent(
    state: DocumentListUiState,
    onRefresh: () -> Unit,
    onTab: (SignerTab) -> Unit,
    onOpenDocument: (String) -> Unit,
    onLogout: () -> Unit
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
                documents = state.visibleSignerDocuments,
                isRefreshing = state.isRefreshing,
                error = state.error,
                hasAnyDocument = state.documents.isNotEmpty(),
                onRefresh = onRefresh,
                onOpen = onOpenDocument,
                forSigner = true,
                empty = {
                    EmptyState(
                        icon = Icons.Default.Inbox,
                        title = when (state.signerTab) {
                            SignerTab.PENDING -> "Nada para assinar"
                            SignerTab.SIGNED -> "Nenhum documento assinado"
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
    empty: @Composable () -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            error != null && !hasAnyDocument -> EmptyState(
                icon = Icons.Default.CloudOff,
                title = "Não foi possível carregar",
                subtitle = error,
                actionText = "Tentar novamente",
                onAction = onRefresh
            )
            documents.isEmpty() && !isRefreshing -> empty()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
            ) {
                // Offline-first: com erro de rede a lista continua mostrando o que está salvo no aparelho.
                if (error != null) item { ErrorBanner("$error Mostrando dados salvos no aparelho.", "Tentar", onRefresh) }
                items(documents, key = { it.id }) { document ->
                    DocumentCard(document = document, forSigner = forSigner, onClick = { onOpen(document.id) })
                }
            }
        }
    }
}

@Composable
fun DocumentCard(document: Document, forSigner: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
