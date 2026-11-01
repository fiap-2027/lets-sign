@file:OptIn(ExperimentalMaterial3Api::class)

package com.letsign.mobile.presentation.ui.screens.signers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.SignerGroup
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.domain.rules.DocumentRules
import com.letsign.mobile.presentation.theme.EasySignTheme
import com.letsign.mobile.presentation.ui.components.Avatar
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.EasySignTopAppBar
import com.letsign.mobile.presentation.ui.components.SnackbarMessage
import com.letsign.mobile.presentation.ui.components.WebCard
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.GroupForm
import com.letsign.mobile.presentation.viewmodel.SignerForm
import com.letsign.mobile.presentation.viewmodel.SignersUiState
import com.letsign.mobile.presentation.viewmodel.SignersViewModel

/** Ações da tela de Contatos (agrupadas para os previews não precisarem de 20 lambdas). */
data class ContactsActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (User) -> Unit = {},
    val onSave: (nome: String, email: String, telefone: String, senha: String, cpf: String, nascimento: String) -> Unit = { _, _, _, _, _, _ -> },
    val onCloseForm: () -> Unit = {},
    val onAskDelete: (User) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onNewGroup: () -> Unit = {},
    val onEditGroup: (SignerGroup) -> Unit = {},
    val onSaveGroup: (nome: String, memberIds: List<String>) -> Unit = { _, _ -> },
    val onCloseGroupForm: () -> Unit = {},
    val onAskDeleteGroup: (SignerGroup) -> Unit = {},
    val onConfirmDeleteGroup: () -> Unit = {},
    val onCancelDeleteGroup: () -> Unit = {},
    val onMessagesShown: () -> Unit = {}
)

@Composable
fun SignersScreen(onBack: () -> Unit, viewModel: SignersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignersContent(
        state,
        ContactsActions(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onQuery = viewModel::setQuery,
            onNew = viewModel::openNew,
            onEdit = viewModel::openEdit,
            onSave = viewModel::save,
            onCloseForm = viewModel::closeForm,
            onAskDelete = viewModel::askDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onNewGroup = viewModel::openNewGroup,
            onEditGroup = viewModel::openEditGroup,
            onSaveGroup = viewModel::saveGroup,
            onCloseGroupForm = viewModel::closeGroupForm,
            onAskDeleteGroup = viewModel::askDeleteGroup,
            onConfirmDeleteGroup = viewModel::confirmDeleteGroup,
            onCancelDeleteGroup = viewModel::cancelDeleteGroup,
            onMessagesShown = viewModel::consumeMessages
        )
    )
}

/** Contatos (quem assina ou acompanha documentos) e grupos de contatos, sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignersContent(state: SignersUiState, actions: ContactsActions, initialTab: Int = 0) {
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    SnackbarMessage(snackbar, state.message ?: state.error, actions.onMessagesShown)

    Scaffold(
        topBar = { EasySignTopAppBar(title = "Contatos", subtitle = "${state.signers.size} contato(s) • ${state.groups.size} grupo(s)", showBackButton = true, onBackClick = actions.onBack) },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = actions.onNew, icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }, text = { Text("Novo contato") })
            else ExtendedFloatingActionButton(onClick = actions.onNewGroup, icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) }, text = { Text("Novo grupo") })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contatos (${state.signers.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Grupos (${state.groups.size})") })
            }
            if (tab == 0) ContactsTab(state, actions) else GroupsTab(state, actions)
        }
    }

    state.form?.let { form -> SignerFormDialog(form, actions.onSave, actions.onCloseForm) }
    state.groupForm?.let { form -> GroupFormDialog(form, state.signers, actions.onSaveGroup, actions.onCloseGroupForm) }
    state.confirmDelete?.let { user ->
        ConfirmDialog(
            title = "Remover contato?",
            text = "${user.nome} será removido. Só é possível remover quem ainda não participa de nenhum documento.",
            confirmText = "Remover",
            destructive = true,
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onCancelDelete
        )
    }
    state.confirmDeleteGroup?.let { group ->
        ConfirmDialog(
            title = "Remover grupo?",
            text = "O grupo \"${group.nome}\" será removido. Os contatos continuam cadastrados.",
            confirmText = "Remover",
            destructive = true,
            onConfirm = actions.onConfirmDeleteGroup,
            onDismiss = actions.onCancelDeleteGroup
        )
    }
}

@Composable
private fun ContactsTab(state: SignersUiState, actions: ContactsActions) {
    Column {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Buscar por nome ou e-mail") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            if (state.visible.isEmpty() && !state.isRefreshing) {
                if (state.signers.isEmpty()) EmptyState(Icons.Default.Group, "Nenhum contato", "Cadastre quem vai assinar ou acompanhar seus documentos.", "Cadastrar", actions.onNew)
                else EmptyState(Icons.Default.Search, "Nada encontrado", "Nenhum contato corresponde a \"${state.query}\".")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.visible, key = { it.id }) { user -> SignerRow(user, onEdit = { actions.onEdit(user) }, onDelete = { actions.onAskDelete(user) }) }
                }
            }
        }
    }
}

@Composable
private fun GroupsTab(state: SignersUiState, actions: ContactsActions) {
    if (state.groups.isEmpty()) {
        EmptyState(
            Icons.Default.Groups, "Nenhum grupo",
            "Junte contatos que costumam assinar juntos (ex.: diretoria) para adicioná-los de uma vez no envio.",
            if (state.signers.isEmpty()) null else "Criar grupo", actions.onNewGroup
        )
        return
    }
    val names = state.signers.associate { it.id to it.nome }
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.groups, key = { it.id }) { group ->
            WebCard(Modifier.fillMaxWidth(), onClick = { actions.onEditGroup(group) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            group.memberIds.mapNotNull(names::get).joinToString().ifEmpty { "Sem contatos" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { actions.onAskDeleteGroup(group) }) { Icon(Icons.Default.Delete, contentDescription = "Remover grupo ${group.nome}") }
                }
            }
        }
    }
}

@Composable
private fun SignerRow(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    WebCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.initials)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val extra = listOfNotNull(
                    user.telefone,
                    user.cpf?.let { "CPF ${DocumentRules.formatCpf(it)}" },
                    user.dataNascimento?.let { "Nasc. $it" }
                ).joinToString(" • ")
                if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remover ${user.nome}") }
        }
    }
}

@Composable
private fun SignerFormDialog(form: SignerForm, onSave: (String, String, String, String, String, String) -> Unit, onClose: () -> Unit) {
    val editing = form.editing
    var nome by rememberSaveable(editing?.id) { mutableStateOf(editing?.nome.orEmpty()) }
    var email by rememberSaveable(editing?.id) { mutableStateOf(editing?.email.orEmpty()) }
    var telefone by rememberSaveable(editing?.id) { mutableStateOf(editing?.telefone.orEmpty()) }
    var cpf by rememberSaveable(editing?.id) { mutableStateOf(DocumentRules.formatCpf(editing?.cpf)) }
    var nascimento by rememberSaveable(editing?.id) { mutableStateOf(editing?.dataNascimento.orEmpty()) }
    var senha by rememberSaveable(editing?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!form.saving) onClose() },
        title = { Text(if (editing == null) "Novo contato" else "Editar contato") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(nome, { nome = it }, "Nome completo", Icons.Default.Person)
                FormField(email, { email = it }, "E-mail (login)", Icons.Default.Email, KeyboardType.Email)
                FormField(telefone, { telefone = it }, "Telefone com DDD", Icons.Default.Phone, KeyboardType.Phone)
                FormField(cpf, { cpf = it.take(14) }, "CPF (opcional)", Icons.Default.Badge, KeyboardType.Number)
                FormField(nascimento, { nascimento = maskDate(it) }, "Nascimento (dd/mm/aaaa)", Icons.Default.Cake, KeyboardType.Number)
                FormField(
                    senha, { senha = it },
                    if (editing == null) "Senha (mín. 6)" else "Nova senha (deixe vazio para manter)",
                    Icons.Default.Lock, KeyboardType.Password, isPassword = true
                )
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nome, email, telefone, senha, cpf, nascimento) }, enabled = !form.saving) {
                if (form.saving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !form.saving) { Text("Cancelar") } }
    )
}

/** Digita só números e as barras entram sozinhas: 13071992 -> 13/07/1992. */
private fun maskDate(input: String): String {
    val d = input.filter(Char::isDigit).take(8)
    return buildString {
        d.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

@Composable
private fun GroupFormDialog(form: GroupForm, contacts: List<User>, onSave: (String, List<String>) -> Unit, onClose: () -> Unit) {
    val editing = form.editing
    var nome by rememberSaveable(editing?.id) { mutableStateOf(editing?.nome.orEmpty()) }
    var selected by rememberSaveable(editing?.id) { mutableStateOf(editing?.memberIds.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!form.saving) onClose() },
        title = { Text(if (editing == null) "Novo grupo" else "Editar grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(nome, { nome = it.take(60) }, "Nome do grupo", Icons.Default.Groups)
                Text("Contatos do grupo (${selected.size})", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    contacts.forEach { c ->
                        val checked = c.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (checked) selected - c.id else selected + c.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { selected = if (checked) selected - c.id else selected + c.id })
                            Column {
                                Text(c.nome, style = MaterialTheme.typography.bodyMedium)
                                Text(c.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nome, selected) }, enabled = !form.saving) {
                if (form.saving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !form.saving) { Text("Cancelar") } }
    )
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------- Previews

private val previewGroups = listOf(SignerGroup("g1", "Diretoria", PreviewData.signers.take(2).map { it.id }))

@ScreenPreviews
@Composable
fun SignersPreview() = EasySignTheme {
    SignersContent(SignersUiState(signers = PreviewData.signers, groups = previewGroups), ContactsActions())
}

@ScreenPreviews
@Composable
fun SignersGroupsPreview() = EasySignTheme {
    SignersContent(SignersUiState(signers = PreviewData.signers, groups = previewGroups), ContactsActions(), initialTab = 1)
}

@ScreenPreviews
@Composable
fun SignerFormPreview() = EasySignTheme {
    SignersContent(SignersUiState(signers = PreviewData.signers, form = SignerForm(error = "Informe um CPF válido.")), ContactsActions())
}
