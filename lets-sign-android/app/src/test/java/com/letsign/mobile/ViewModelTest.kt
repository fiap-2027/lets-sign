package com.letsign.mobile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.Participant
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SendResult
import com.letsign.mobile.domain.model.SignatureField
import com.letsign.mobile.domain.model.User
import com.letsign.mobile.data.pdf.PdfInvalidException
import com.letsign.mobile.presentation.viewmodel.DocumentDetailViewModel
import com.letsign.mobile.presentation.viewmodel.NewDocumentViewModel
import com.letsign.mobile.presentation.viewmodel.SendOutcome
import com.letsign.mobile.presentation.viewmodel.SessionViewModel
import com.letsign.mobile.presentation.viewmodel.WizardStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    private val ana = User("u-ana", "Ana Costa", "ana@x.com")
    private val joao = User("u-joao", "João Pereira", "joao@x.com")
    private val bia = User("u-bia", "Beatriz Souza", "bia@x.com")
    private val docs = FakeDocumentRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newWizard() = NewDocumentViewModel(SavedStateHandle(), docs, FakeSignerRepository(listOf(ana, joao, bia)), FakePdfImporter(pages = 3))

    /** Preenche as 3 primeiras etapas até chegar na revisão. */
    private fun NewDocumentViewModel.fillUntilReview() {
        setTitle("Contrato de serviços")
        onPdfPicked(Mockito.mock(Uri::class.java))
        next()
        toggleParticipant(ana)
        toggleParticipant(joao)
        setRole(joao.id, RoleLabel.TESTEMUNHA)
        setRequired(joao.id, false)
        next()
        setActiveSigner(ana.id)
        addField()
        setActiveSigner(joao.id)
        setPage(2)
        addField()
        next()
    }

    private fun sent(jaEnviado: Boolean = false) =
        Result.success(SendResult(jaEnviado, "Documento enviado com sucesso.", Document(id = "srv-1", ownerId = "admin", titulo = "Contrato", status = DocumentStatus.PENDING)))

    @Test
    fun `nao avanca sem titulo e PDF, nem sem signatario, nem sem campo`() {
        val vm = newWizard()
        vm.next()
        assertEquals(WizardStep.DADOS, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.stepError)

        vm.setTitle("Contrato")
        vm.onPdfPicked(Mockito.mock(Uri::class.java))
        vm.next()
        assertEquals(WizardStep.SIGNATARIOS, vm.uiState.value.step)

        vm.next()
        assertEquals("Selecione pelo menos um signatário.", vm.uiState.value.stepError)

        vm.toggleParticipant(ana)
        vm.next()
        assertEquals(WizardStep.CAMPOS, vm.uiState.value.step)

        vm.next()
        assertEquals("Posicione pelo menos um campo para Ana Costa.", vm.uiState.value.stepError)
        assertEquals(WizardStep.CAMPOS, vm.uiState.value.step)
    }

    @Test
    fun `envio monta participantes, papeis, campos e mensagem`() {
        docs.sendResults = mutableListOf(sent())
        val vm = newWizard()
        vm.fillUntilReview()
        vm.setMessage("Olá, segue para assinatura.")
        vm.send()

        val (id, draft) = docs.sentDrafts.single()
        assertEquals("srv-1", id)
        assertEquals(listOf(ana.id, joao.id), draft.participants.map { it.signer.id })
        assertEquals(RoleLabel.TESTEMUNHA, draft.participants[1].roleLabel)
        assertEquals(false, draft.participants[1].required)
        assertEquals(listOf(1, 2), draft.fields.map { it.page })
        assertEquals("Olá, segue para assinatura.", draft.mensagem)
        assertTrue(vm.uiState.value.outcome is SendOutcome.Success)
    }

    @Test
    fun `nova tentativa apos falha nao cria o documento de novo`() {
        docs.sendResults = mutableListOf(Result.failure(Exception("Erro de conexão. Verifique sua internet.")), sent(jaEnviado = true))
        val vm = newWizard()
        vm.fillUntilReview()

        vm.send()
        val failure = vm.uiState.value.outcome as SendOutcome.Failure
        assertEquals("Erro de conexão. Verifique sua internet.", failure.mensagem)
        assertEquals("srv-1", vm.uiState.value.serverDocumentId)
        assertEquals(WizardStep.REVISAO, vm.uiState.value.step) // dados preservados para tentar de novo

        vm.send()
        assertEquals(1, docs.createCalls) // o documento foi criado uma única vez
        assertEquals(2, docs.sendCalls)
        assertEquals(listOf("srv-1", "srv-1"), docs.sentDrafts.map { it.first })
        assertEquals(true, (vm.uiState.value.outcome as SendOutcome.Success).jaEnviado)
    }

    @Test
    fun `falha ao criar mantem tudo e permite tentar de novo`() {
        docs.createResult = Result.failure(Exception("O PDF está corrompido ou não pôde ser processado."))
        val vm = newWizard()
        vm.fillUntilReview()
        vm.send()
        assertEquals("O PDF está corrompido ou não pôde ser processado.", (vm.uiState.value.outcome as SendOutcome.Failure).mensagem)
        assertNull(vm.uiState.value.serverDocumentId)
        assertEquals(0, docs.sendCalls)
    }

    @Test
    fun `remover signatario remove os campos dele e substituir transfere os campos`() {
        val vm = newWizard()
        vm.fillUntilReview()
        vm.goTo(WizardStep.SIGNATARIOS)

        vm.replaceParticipant(joao.id, bia)
        val s1 = vm.uiState.value
        assertEquals(listOf(ana.id, bia.id), s1.participants.map { it.signer.id })
        assertEquals(RoleLabel.TESTEMUNHA, s1.participants[1].roleLabel) // papel mantido
        assertTrue(s1.fields.any { it.signerId == bia.id })
        assertTrue(s1.fields.none { it.signerId == joao.id })

        vm.removeParticipant(bia.id)
        assertTrue(vm.uiState.value.fields.none { it.signerId == bia.id })
        assertEquals(1, vm.uiState.value.fields.size)
    }

    @Test
    fun `rascunho existente carrega com signatarios e campos e nao recria no envio`() {
        docs.detail = Document(
            id = "d-draft", ownerId = "admin", titulo = "Aditivo", status = DocumentStatus.DRAFT, nomeArquivo = "aditivo.pdf",
            signers = listOf(Participant(id = "p1", signerId = ana.id, roleLabel = RoleLabel.CONTRATANTE, signer = ana)),
            fields = listOf(SignatureField(id = "f1", signerId = ana.id, page = 1, x = 0.1f, y = 0.8f, width = 0.3f, height = 0.08f))
        )
        docs.sendResults = mutableListOf(sent())
        val vm = NewDocumentViewModel(SavedStateHandle(mapOf("documentId" to "d-draft")), docs, FakeSignerRepository(listOf(ana)), FakePdfImporter())

        val s = vm.uiState.value
        assertTrue(s.editMode)
        assertEquals("Aditivo", s.titulo)
        assertEquals(listOf(ana.id), s.participants.map { it.signer.id })
        assertEquals(1, s.fields.size)

        vm.send()
        assertEquals(0, docs.createCalls)
        assertEquals("d-draft", docs.sentDrafts.single().first)
    }

    @Test
    fun `rascunho ja enviado nao pode ser editado`() {
        docs.detail = Document(id = "d1", ownerId = "admin", titulo = "X", status = DocumentStatus.PENDING)
        val vm = NewDocumentViewModel(SavedStateHandle(mapOf("documentId" to "d1")), docs, FakeSignerRepository(emptyList()), FakePdfImporter())
        assertEquals("Este documento já foi enviado. Alterações exigem uma nova versão.", vm.uiState.value.loadError)
    }

    /** O uiState do detalhe só é calculado enquanto alguém observa (como a tela faz): o teste observa também. */
    private fun TestScope.detailVm(importer: FakePdfImporter = FakePdfImporter()) =
        DocumentDetailViewModel(SavedStateHandle(mapOf("documentId" to "d1")), docs, FakeAuthRepository(), importer).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    @Test
    fun `nova versao valida o PDF, envia e abre o assistente`() = runTest {
        val vm = detailVm()
        // Abre o assistente no id devolvido (no modo local a nova versão é outro documento).
        docs.newVersionResult = Result.success(Document(id = "d2", ownerId = "admin", titulo = "Contrato", versao = 2))
        vm.newVersion(Mockito.mock(Uri::class.java))
        assertEquals(1, docs.newVersionCalls)
        assertEquals("d2", vm.uiState.value.newVersionId)
        vm.consumeNewVersion()
        assertNull(vm.uiState.value.newVersionId)
    }

    @Test
    fun `nova versao com PDF invalido nao chama a API`() = runTest {
        val vm = detailVm(FakePdfImporter(importError = PdfInvalidException("O PDF está protegido por senha e não pode ser processado.")))
        vm.newVersion(Mockito.mock(Uri::class.java))
        assertEquals(0, docs.newVersionCalls)
        assertEquals("O PDF está protegido por senha e não pode ser processado.", vm.uiState.value.error)
        assertNull(vm.uiState.value.newVersionId)
    }

    @Test
    fun `nova versao recusada pelo servidor mostra o motivo`() = runTest {
        docs.newVersionResult = Result.failure(Exception("Documento concluído não pode receber nova versão. Crie um novo documento."))
        val vm = detailVm()
        vm.newVersion(Mockito.mock(Uri::class.java))
        assertEquals("Documento concluído não pode receber nova versão. Crie um novo documento.", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isWorking)
    }

    @Test
    fun `sessao expirada faz logout e avisa o usuario`() = runTest {
        val auth = FakeAuthRepository()
        val vm = SessionViewModel(auth)
        auth.expired.emit(Unit)
        assertEquals(1, auth.logoutCalls)
        assertEquals("Sua sessão expirou. Entre novamente.", vm.notice.value)
    }
}
