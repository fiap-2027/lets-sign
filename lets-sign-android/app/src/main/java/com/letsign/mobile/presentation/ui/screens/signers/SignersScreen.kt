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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.ui.components.Avatar
import com.letsign.mobile.presentation.ui.components.ConfirmDialog
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.LetsSignTopAppBar
import com.letsign.mobile.presentation.ui.components.SnackbarMessage
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.SignerForm
import com.letsign.mobile.presentation.viewmodel.SignersUiState
import com.letsign.mobile.presentation.viewmodel.SignersViewModel

@Composable
fun SignersScreen(onBack: () -> Unit, viewModel: SignersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignersContent(
        state = state,
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
        onMessagesShown = viewModel::consumeMessages
    )
}

/** Cadastro de signatários sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignersContent(
    state: SignersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (User) -> Unit,
    onSave: (nome: String, email: String, telefone: String, senha: String) -> Unit,
    onCloseForm: () -> Unit,
    onAskDelete: (User) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onMessagesShown: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessage(snackbar, state.message ?: state.error, onMessagesShown)

    Scaffold(
        topBar = { LetsSignTopAppBar(title = "Signatários", subtitle = "${state.signers.size} cadastrado(s)", showBackButton = true, onBackClick = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }, text = { Text("Novo signatário") })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Buscar por nome ou e-mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                if (state.visible.isEmpty() && !state.isRefreshing) {
                    if (state.signers.isEmpty()) EmptyState(Icons.Default.Group, "Nenhum signatário", "Cadastre quem vai assinar seus documentos.", "Cadastrar", onNew)
                    else EmptyState(Icons.Default.Search, "Nada encontrado", "Nenhum signatário corresponde a \"${state.query}\".")
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.visible, key = { it.id }) { user -> SignerRow(user, onEdit = { onEdit(user) }, onDelete = { onAskDelete(user) }) }
                    }
                }
            }
        }
    }

    state.form?.let { form -> SignerFormDialog(form, onSave, onCloseForm) }
    state.confirmDelete?.let { user ->
        ConfirmDialog(
            title = "Remover signatário?",
            text = "${user.nome} será removido. Só é possível remover quem ainda não participa de nenhum documento.",
            confirmText = "Remover",
            destructive = true,
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete
        )
    }
}

@Composable
private fun SignerRow(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.initials)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                user.telefone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remover ${user.nome}") }
        }
    }
}

@Composable
private fun SignerFormDialog(form: SignerForm, onSave: (String, String, String, String) -> Unit, onClose: () -> Unit) {
    val editing = form.editing
    var nome by rememberSaveable(editing?.id) { mutableStateOf(editing?.nome.orEmpty()) }
    var email by rememberSaveable(editing?.id) { mutableStateOf(editing?.email.orEmpty()) }
    var telefone by rememberSaveable(editing?.id) { mutableStateOf(editing?.telefone.orEmpty()) }
    var senha by rememberSaveable(editing?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!form.saving) onClose() },
        title = { Text(if (editing == null) "Novo signatário" else "Editar signatário") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(nome, { nome = it }, "Nome completo", Icons.Default.Person)
                FormField(email, { email = it }, "E-mail (login)", Icons.Default.Email, KeyboardType.Email)
                FormField(telefone, { telefone = it }, "Telefone com DDD", Icons.Default.Phone, KeyboardType.Phone)
                FormField(
                    senha, { senha = it },
                    if (editing == null) "Senha (mín. 6)" else "Nova senha (deixe vazio para manter)",
                    Icons.Default.Lock, KeyboardType.Password, isPassword = true
                )
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nome, email, telefone, senha) }, enabled = !form.saving) {
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

@ScreenPreviews
@Composable
fun SignersPreview() = LetsSignTheme {
    SignersContent(SignersUiState(signers = PreviewData.signers), {}, {}, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {}, {})
}

@ScreenPreviews
@Composable
fun SignerFormPreview() = LetsSignTheme {
    SignersContent(
        SignersUiState(signers = PreviewData.signers, form = SignerForm(error = "Informe o telefone com DDD.")),
        {}, {}, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {}, {}
    )
}
