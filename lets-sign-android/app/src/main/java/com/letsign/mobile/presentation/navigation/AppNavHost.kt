package com.letsign.mobile.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.presentation.ui.screens.auth.LoginScreen
import com.letsign.mobile.presentation.ui.screens.auth.RegisterScreen
import com.letsign.mobile.presentation.ui.screens.documents.AdminHomeScreen
import com.letsign.mobile.presentation.ui.screens.documents.DocumentDetailScreen
import com.letsign.mobile.presentation.ui.screens.documents.SignerHomeScreen
import com.letsign.mobile.presentation.ui.screens.esg.EsgScreen
import com.letsign.mobile.presentation.ui.screens.pdf.PdfViewerScreen
import com.letsign.mobile.presentation.ui.screens.sign.SignDocumentScreen
import com.letsign.mobile.presentation.ui.screens.signers.SignersScreen
import com.letsign.mobile.presentation.ui.screens.wizard.NewDocumentScreen
import com.letsign.mobile.presentation.viewmodel.SessionViewModel

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ADMIN_HOME = "documents"
    const val SIGNER_HOME = "inbox"
    const val DETAIL = "document/{documentId}"
    const val NEW = "new_document"
    const val EDIT = "edit_document/{documentId}"
    const val SIGNERS = "signers"
    const val SIGN = "sign/{documentId}"
    const val PDF = "pdf/{documentId}/{tipo}"
    const val ESG = "esg"

    fun detail(id: String) = "document/$id"
    fun edit(id: String) = "edit_document/$id"
    fun sign(id: String) = "sign/$id"
    fun pdf(id: String, tipo: String) = "pdf/$id/$tipo"

    /** Requisito: Admin e Signatário têm telas iniciais diferentes. */
    fun homeOf(user: User) = if (user.isAdmin) ADMIN_HOME else SIGNER_HOME
}

private val documentIdArg = listOf(navArgument("documentId") { type = NavType.StringType })

@Composable
fun AppNavHost(session: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val user by session.currentUser.collectAsStateWithLifecycle()
    val notice by session.notice.collectAsStateWithLifecycle()
    // Decidido uma vez só: mudar o startDestination depois de montado recria o grafo.
    val startDestination = remember { user?.let(Routes::homeOf) ?: Routes.LOGIN }

    // Logout ou sessão expirada (401): volta para o login limpando a pilha.
    var hadUser by remember { mutableStateOf(user != null) }
    LaunchedEffect(user) {
        if (user == null && hadUser) navController.resetTo(Routes.LOGIN)
        hadUser = user != null
    }

    NavHost(navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                notice = notice,
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onLoginSuccess = { u ->
                    session.consumeNotice()
                    navController.resetTo(Routes.homeOf(u))
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = { u -> navController.resetTo(Routes.homeOf(u)) }
            )
        }
        composable(Routes.ADMIN_HOME) {
            AdminHomeScreen(
                onNewDocument = { navController.navigate(Routes.NEW) },
                onOpenDocument = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSigners = { navController.navigate(Routes.SIGNERS) },
                onLogout = session::logout,
                onEditDraft = { id -> navController.navigate(Routes.edit(id)) },
                onOpenEsg = { navController.navigate(Routes.ESG) }
            )
        }
        composable(Routes.SIGNER_HOME) {
            SignerHomeScreen(
                onOpenDocument = { id -> navController.navigate(Routes.detail(id)) },
                onLogout = session::logout,
                onOpenEsg = { navController.navigate(Routes.ESG) }
            )
        }
        composable(Routes.DETAIL, arguments = documentIdArg) {
            DocumentDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinueDraft = { id -> navController.navigate(Routes.edit(id)) },
                onSign = { id -> navController.navigate(Routes.sign(id)) },
                onViewPdf = { id, kind -> navController.navigate(Routes.pdf(id, kind.apiValue)) }
            )
        }
        composable(Routes.NEW) { WizardDestination(navController) }
        composable(Routes.EDIT, arguments = documentIdArg) { WizardDestination(navController) }
        composable(Routes.ESG) { EsgScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SIGNERS) {
            SignersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SIGN, arguments = documentIdArg) {
            SignDocumentScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.PDF,
            arguments = documentIdArg + navArgument("tipo") { type = NavType.StringType }
        ) {
            PdfViewerScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun WizardDestination(navController: NavHostController) {
    NewDocumentScreen(
        onClose = { navController.popBackStack() },
        onOpenSigners = { navController.navigate(Routes.SIGNERS) },
        // Depois de enviar, o "voltar" do detalhe leva para a lista (não para o assistente).
        onOpenDocument = { id ->
            navController.navigate(Routes.detail(id)) {
                popUpTo(Routes.ADMIN_HOME)
            }
        }
    )
}

/** Navega para [route] e limpa toda a pilha (o "voltar" não retorna para a tela anterior). */
private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }
}
