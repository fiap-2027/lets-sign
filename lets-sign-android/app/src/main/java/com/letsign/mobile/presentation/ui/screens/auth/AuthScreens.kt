package com.letsign.mobile.presentation.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.presentation.theme.LetsSignTheme
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (User) -> Unit,
    notice: String? = null,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    LaunchedEffect(user) { user?.let(onLoginSuccess) }
    LoginContent(
        isLoading = uiState.isLoading,
        error = uiState.error ?: notice,
        onLogin = viewModel::login,
        onNavigateToRegister = onNavigateToRegister
    )
}

/** Tela de login sem ViewModel: é o que os previews renderizam. */
@Composable
fun LoginContent(
    isLoading: Boolean,
    error: String?,
    onLogin: (email: String, senha: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    initialEmail: String = ""
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(icon = Icons.Default.Description, title = "LetsSign", subtitle = "Entre na sua conta") {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        PasswordField(value = password, onValueChange = { password = it }, label = "Senha")

        ErrorText(error)

        PrimaryButton(text = "Entrar", isLoading = isLoading, onClick = { onLogin(email, password) })

        FooterLink(question = "Não tem conta?", action = "Cadastre-se", onClick = onNavigateToRegister)

        // Mostra qual versão está instalada (útil para conferir se o APK novo entrou).
        Text(
            "Versão ${com.letsign.mobile.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: (User) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    LaunchedEffect(user) { user?.let(onRegisterSuccess) }
    RegisterContent(
        isLoading = uiState.isLoading,
        error = uiState.error,
        onRegister = viewModel::register,
        onValidationError = viewModel::setError,
        onNavigateToLogin = onNavigateToLogin
    )
}

/** Tela de cadastro sem ViewModel: é o que os previews renderizam. */
@Composable
fun RegisterContent(
    isLoading: Boolean,
    error: String?,
    onRegister: (nome: String, email: String, telefone: String?, senha: String) -> Unit,
    onValidationError: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var telefone by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var confirmSenha by rememberSaveable { mutableStateOf("") }

    AuthScaffold(icon = Icons.Default.PersonAdd, title = "Criar Conta", subtitle = "Preencha seus dados para começar") {
        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome completo") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = telefone,
            onValueChange = { telefone = it },
            label = { Text("Telefone (opcional)") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        PasswordField(value = senha, onValueChange = { senha = it }, label = "Senha")
        PasswordField(value = confirmSenha, onValueChange = { confirmSenha = it }, label = "Confirmar senha")

        ErrorText(error)

        PrimaryButton(
            text = "Criar Conta",
            isLoading = isLoading,
            onClick = {
                if (senha != confirmSenha) onValidationError("As senhas não coincidem.")
                else onRegister(nome, email, telefone.ifBlank { null }, senha)
            }
        )

        FooterLink(question = "Já tem conta?", action = "Entrar", onClick = onNavigateToLogin)
    }
}

@Composable
private fun AuthScaffold(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(18.dp))
                }
                Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar senha" else "Mostrar senha"
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PrimaryButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FooterLink(question: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = question, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@ScreenPreviews
@Composable
fun LoginScreenPreview() = LetsSignTheme {
    LoginContent(isLoading = false, error = null, onLogin = { _, _ -> }, onNavigateToRegister = {}, initialEmail = "marina@letssign.com")
}

@ScreenPreviews
@Composable
fun LoginScreenErrorPreview() = LetsSignTheme {
    LoginContent(isLoading = false, error = "E-mail ou senha incorretos.", onLogin = { _, _ -> }, onNavigateToRegister = {}, initialEmail = "marina@letssign.com")
}

@ScreenPreviews
@Composable
fun RegisterScreenPreview() = LetsSignTheme {
    RegisterContent(isLoading = false, error = null, onRegister = { _, _, _, _ -> }, onValidationError = {}, onNavigateToLogin = {})
}
