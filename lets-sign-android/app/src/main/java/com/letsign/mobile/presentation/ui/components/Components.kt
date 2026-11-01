package com.letsign.mobile.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.presentation.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetsSignTopAppBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            titleContentColor = MaterialTheme.colorScheme.onSecondary,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondary
        )
    )
}

@Composable
fun StatusBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // Estilo da web: fundo pastel (cor transparente) + texto na cor do status.
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

fun DocumentStatus.label(): String = when (this) {
    DocumentStatus.DRAFT -> "Rascunho"
    DocumentStatus.PENDING -> "Enviado"
    DocumentStatus.PARTIALLY_SIGNED -> "Parcial"
    DocumentStatus.COMPLETED -> "Concluído"
    DocumentStatus.CANCELLED -> "Cancelado"
}

@Composable
fun DocumentStatusChip(status: DocumentStatus) {
    val color = when (status) {
        DocumentStatus.DRAFT -> StatusColors.Gray
        DocumentStatus.PENDING -> StatusColors.Amber
        DocumentStatus.PARTIALLY_SIGNED -> StatusColors.Orange
        DocumentStatus.COMPLETED -> StatusColors.Green
        DocumentStatus.CANCELLED -> StatusColors.Red
    }
    StatusBadge(text = status.label(), color = color)
}

fun SignerStatus.label(): String = when (this) {
    SignerStatus.PENDING -> "Pendente"
    SignerStatus.VIEWED -> "Visualizado"
    SignerStatus.SIGNED -> "Assinado"
    SignerStatus.REJECTED -> "Recusado"
}

@Composable
fun SignerStatusChip(status: SignerStatus) {
    val color = when (status) {
        SignerStatus.PENDING -> StatusColors.Blue
        SignerStatus.VIEWED -> StatusColors.Orange
        SignerStatus.SIGNED -> StatusColors.Green
        SignerStatus.REJECTED -> StatusColors.Red
    }
    StatusBadge(text = status.label(), color = color)
}

/** Cor fixa por signatário (mesma cor no editor de campos, na revisão e na assinatura). */
private val SignerPalette = listOf(
    Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFFF4511E), Color(0xFF8E24AA), Color(0xFF6D4C41)
)

fun signerColor(index: Int): Color = SignerPalette[(index.coerceAtLeast(0)) % SignerPalette.size]

@Composable
fun Avatar(initials: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer, size: Int = 40) {
    Surface(shape = CircleShape, color = color, contentColor = contentColor, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = initials.ifEmpty { "?" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    WebCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Card branco com borda fina, como os cards da versão web. */
@Composable
fun WebCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, border = border) { content() }
    else Card(modifier = modifier, border = border) { content() }
}

@Composable
fun ErrorBanner(message: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionText, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Mostra [message] uma vez no snackbar e avisa [onShown] para limpar o estado. */
@Composable
fun SnackbarMessage(host: SnackbarHostState, message: String?, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            onShown()
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) { Text(text = actionText) }
            }
        }
    }
}

@Composable
fun LoadingOverlay(isLoading: Boolean, message: String = "Carregando...") {
    if (!isLoading) return
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun BottomActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    loading: Boolean = false
) {
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secondaryText != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) { Text(secondaryText) }
            }
            Button(onClick = onPrimary, enabled = primaryEnabled && !loading, modifier = Modifier.weight(1f)) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text(primaryText)
            }
        }
    }
}
