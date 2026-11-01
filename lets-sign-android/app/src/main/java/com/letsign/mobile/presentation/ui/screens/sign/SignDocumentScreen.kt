package com.letsign.mobile.presentation.ui.screens.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsign.mobile.domain.model.FieldType
import com.letsign.mobile.presentation.theme.EasySignTheme
import com.letsign.mobile.presentation.theme.StatusColors
import com.letsign.mobile.presentation.ui.components.CenteredLoading
import com.letsign.mobile.presentation.ui.components.DrawSignatureArea
import com.letsign.mobile.presentation.ui.components.DrawnSignature
import com.letsign.mobile.presentation.ui.components.EmptyState
import com.letsign.mobile.presentation.ui.components.FieldBox
import com.letsign.mobile.presentation.ui.components.EasySignTopAppBar
import com.letsign.mobile.presentation.ui.components.LoadingOverlay
import com.letsign.mobile.presentation.ui.components.PdfLoad
import com.letsign.mobile.presentation.ui.components.PdfPageView
import com.letsign.mobile.presentation.ui.components.SnackbarMessage
import com.letsign.mobile.presentation.ui.components.TypedSignaturePreview
import com.letsign.mobile.presentation.ui.components.TypedStyle
import com.letsign.mobile.presentation.ui.components.rememberPdfRenderer
import com.letsign.mobile.presentation.ui.components.typedSignatureMetadata
import com.letsign.mobile.presentation.ui.components.typedSignaturePng
import com.letsign.mobile.presentation.ui.preview.PreviewData
import com.letsign.mobile.presentation.ui.preview.ScreenPreviews
import com.letsign.mobile.presentation.viewmodel.SignDocumentUiState
import com.letsign.mobile.presentation.viewmodel.SignDocumentViewModel
import kotlinx.serialization.json.JsonObject

/** Iniciais para a rubrica: primeira letra do primeiro e do último nome ("Dionisio Leonardo" -> "DL"). */
private fun initialsOf(name: String): String {
    val words = name.trim().split(' ', '\t', '\n').filter { it.isNotEmpty() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).joinToString("") { it.first().uppercase() }
}

/** Etapas do fluxo de assinatura, como na versão web ("Etapa 2 de 3 · Assinatura manuscrita"). */
private val StepTitles = listOf("Revisar documento", "Assinatura manuscrita", "Código de verificação")
private const val CODE_LENGTH = 6

@Composable
fun SignDocumentScreen(onBack: () -> Unit, viewModel: SignDocumentViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignDocumentContent(state, onBack, viewModel::sign, viewModel::sendCode, viewModel::load, viewModel::consumeError)
}

/** Assinatura sem ViewModel: é o que os previews renderizam. */
@Composable
fun SignDocumentContent(
    state: SignDocumentUiState,
    onBack: () -> Unit,
    onSign: (signature: String, metadata: JsonObject, rubrica: String?, code: String) -> Unit,
    onSendCode: () -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    initialStep: Int = 0
) {
    val snackbar = remember { SnackbarHostState() }
    val document = state.document
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var drawMode by rememberSaveable { mutableStateOf(false) }
    var typedName by rememberSaveable(state.userName) { mutableStateOf(state.userName) }
    var style by rememberSaveable { mutableStateOf(TypedStyle.STYLE_1) }
    var code by rememberSaveable { mutableStateOf("") }
    val drawn = remember { DrawnSignature() }
    val signing = state.signedMessage == null && document?.canSign == true && state.pdfPath != null

    SnackbarMessage(snackbar, state.error?.takeIf { state.pdfPath != null }, onErrorShown)
    // Ao chegar na etapa 3, "envia" o token (uma vez; "Reenviar" gera outro).
    LaunchedEffect(step) { if (step == 2 && state.sentCode == null) onSendCode() }

    val back: () -> Unit = { if (step == 0) onBack() else { step -= 1 } }
    val nextEnabled = when (step) {
        1 -> if (drawMode) !drawn.isEmpty else typedName.isNotBlank()
        2 -> code.length == CODE_LENGTH
        else -> true
    }
    val hasRubrica = document?.fields.orEmpty().any { it.tipo == FieldType.RUBRICA }
    // Rubrica = iniciais no estilo escolhido (Estilo 1 quando a assinatura foi desenhada).
    val rubricaStyle = if (drawMode) TypedStyle.STYLE_1 else style
    val initials = initialsOf(typedName.ifBlank { state.userName })
    val next: () -> Unit = {
        val rubrica = if (hasRubrica && initials.isNotEmpty()) typedSignaturePng(initials, rubricaStyle) else null
        if (step < 2) { step += 1 }
        else if (drawMode) onSign(drawn.toPngBase64(), drawn.metadata(), rubrica, code)
        else onSign(typedSignaturePng(typedName.trim(), style), typedSignatureMetadata(style), rubrica, code)
    }

    Scaffold(
        topBar = { EasySignTopAppBar(title = "Assinar documento", showBackButton = true, onBackClick = onBack) },
        bottomBar = {
            if (signing) SignFooter(
                primaryText = if (step == 2) "Assinar como ${document?.meuPapel?.label?.lowercase() ?: "parte"}" else "Próximo passo",
                primaryEnabled = nextEnabled,
                onBack = back,
                onNext = next
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.signedMessage != null -> SignedView(onBack)
                state.error != null && state.pdfPath == null -> EmptyState(Icons.Default.ErrorOutline, "Não foi possível abrir", state.error, "Tentar novamente", onRetry)
                state.isLoading && state.pdfPath == null -> CenteredLoading()
                document != null && !document.canSign -> EmptyState(Icons.Default.CheckCircle, "Nada a assinar", "Você já assinou ou recusou este documento, ou ele não está mais disponível.", "Voltar", onBack)
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !(step == 1 && drawMode)).padding(16.dp)) {
                    StepHeader(document?.titulo.orEmpty(), step)
                    Spacer(Modifier.height(20.dp))
                    when (step) {
                        0 -> ReviewStep(state)
                        1 -> {
                            HandwrittenStep(drawMode, { drawMode = it }, typedName, { typedName = it.take(60) }, style, { style = it }, drawn)
                            if (hasRubrica && initials.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text("Sua rubrica (iniciais), usada nos campos de rubrica:", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                TypedSignaturePreview(initials, rubricaStyle, Modifier.size(width = 160.dp, height = 72.dp))
                            }
                        }
                        else -> CodeStep(state, code, { code = it }, onResend = {
                            code = ""
                            onSendCode()
                        })
                    }
                }
            }
            LoadingOverlay(state.isSigning, "Registrando assinatura...")
        }
    }
}

@Composable
private fun StepHeader(title: String, step: Int) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Etapa ${step + 1} de ${StepTitles.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(StepTitles[step], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepTitles.indices.forEach { i ->
            Box(
                Modifier.weight(1f).height(4.dp).background(
                    if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp)
                )
            )
        }
    }
}

@Composable
private fun ReviewStep(state: SignDocumentUiState) {
    val load = rememberPdfRenderer(state.pdfPath)
    val renderer = (load as? PdfLoad.Ready)?.renderer
    val document = state.document
    val myFields = document?.fields.orEmpty().filter { state.currentUserId == null || it.signerId == state.currentUserId }
    val pages = myFields.map { it.page }.distinct().sorted().ifEmpty { listOf(1) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Você assina como ${document?.meuPapel?.label?.lowercase() ?: "signatário"}. Sua assinatura será aplicada em ${myFields.size} campo(s), na(s) página(s) ${pages.joinToString()}. Revise o documento antes de continuar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        pages.forEach { page ->
            Column {
                Text("Página $page de ${document?.totalPaginas ?: pages.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                val aspect = renderer?.info?.pageAspectRatios?.getOrNull(page - 1) ?: 0.707f
                PdfPageView(renderer, page - 1, aspect) { w, h ->
                    myFields.filter { it.page == page }.forEach { f ->
                        val label = when (f.tipo) {
                            FieldType.ASSINATURA -> "Assine aqui"
                            FieldType.RUBRICA -> "Rubrica"
                            else -> "${f.tipo.label} (automático)"
                        }
                        FieldBox(f.x, f.y, f.width, f.height, w, h, MaterialTheme.colorScheme.primary, label, highlighted = f.tipo == FieldType.ASSINATURA)
                    }
                }
            }
        }
    }
}

@Composable
private fun HandwrittenStep(
    drawMode: Boolean,
    onDrawMode: (Boolean) -> Unit,
    typedName: String,
    onTypedName: (String) -> Unit,
    style: TypedStyle,
    onStyle: (TypedStyle) -> Unit,
    drawn: DrawnSignature
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Assine no campo abaixo:", style = MaterialTheme.typography.bodyMedium)
        SecondaryTabRow(selectedTabIndex = if (drawMode) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = !drawMode, onClick = { onDrawMode(false) }, text = { Text("Digitar") }, icon = { Icon(Icons.Default.TextFields, null, Modifier.size(18.dp)) })
            Tab(selected = drawMode, onClick = { onDrawMode(true) }, text = { Text("Desenhar") }, icon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) })
        }
        if (drawMode) {
            DrawSignatureArea(drawn)
        } else {
            OutlinedTextField(value = typedName, onValueChange = onTypedName, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TypedSignaturePreview(typedName, style)
            SecondaryTabRow(selectedTabIndex = style.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                TypedStyle.entries.forEach { s -> Tab(selected = style == s, onClick = { onStyle(s) }, text = { Text(s.label) }) }
            }
        }
    }
}

@Composable
private fun CodeStep(state: SignDocumentUiState, code: String, onCode: (String) -> Unit, onResend: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(buildAnnotatedString {
            append("Enviamos um token para: ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) { append(state.userEmail) }
        }, style = MaterialTheme.typography.bodyLarge)
        Text(buildAnnotatedString {
            append("Insira o Token recebido no campo abaixo ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
        }, style = MaterialTheme.typography.bodyMedium)
        CodeInput(code, onCode)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Não recebeu ou precisa de um novo token?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onResend) { Text("Reenviar") }
        }
        // Sem servidor de e-mail no modo local: o "e-mail" é este aviso.
        state.sentCode?.let {
            Card {
                Text(
                    "Modo demonstração: o código enviado para ${state.userEmail} é $it",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/** Seis caixas de um dígito, como na web. Um único campo de texto por baixo (colar e apagar funcionam). */
@Composable
private fun CodeInput(code: String, onCode: (String) -> Unit) {
    BasicTextField(
        value = code,
        onValueChange = { v -> onCode(v.filter(Char::isDigit).take(CODE_LENGTH)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { i ->
                    val active = i == code.length
                    Box(
                        Modifier.size(width = 44.dp, height = 52.dp)
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) { Text(code.getOrNull(i)?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    )
}

@Composable
private fun SignFooter(primaryText: String, primaryEnabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("Voltar") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext, enabled = primaryEnabled) { Text(primaryText) }
            }
            Text(
                buildAnnotatedString {
                    append("Ao assinar o documento, você está de acordo com os ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Termos de Uso do EasySign") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun SignedView(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusColors.Green, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Você terminou de assinar!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pronto! Aguarde até que todos assinem. Após a conclusão de todas as assinaturas, o documento final assinado fica disponível no app.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao documento") }
    }
}

// ---------------------------------------------------------------- Previews

private val previewState = SignDocumentUiState(
    document = PreviewData.signerDocument, pdfPath = "/preview.pdf", currentUserId = PreviewData.ana.id,
    userName = PreviewData.ana.nome, userEmail = PreviewData.ana.email, isLoading = false
)

@ScreenPreviews
@Composable
fun SignDocumentPreview() = EasySignTheme {
    SignDocumentContent(previewState, {}, { _, _, _, _ -> }, {}, {}, {})
}

@ScreenPreviews
@Composable
fun SignDocumentHandwrittenPreview() = EasySignTheme {
    SignDocumentContent(previewState, {}, { _, _, _, _ -> }, {}, {}, {}, initialStep = 1)
}

@ScreenPreviews
@Composable
fun SignDocumentCodePreview() = EasySignTheme {
    SignDocumentContent(previewState.copy(sentCode = "482913"), {}, { _, _, _, _ -> }, {}, {}, {}, initialStep = 2)
}

@ScreenPreviews
@Composable
fun SignDocumentDonePreview() = EasySignTheme {
    SignDocumentContent(SignDocumentUiState(signedMessage = "Assinatura registrada com sucesso.", isLoading = false), {}, { _, _, _, _ -> }, {}, {}, {})
}
