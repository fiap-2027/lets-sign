package com.letsign.mobile.presentation.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.letsign.mobile.data.pdf.PdfInfo
import com.letsign.mobile.data.pdf.PickedPdf
import com.letsign.mobile.domain.model.AuditEvent
import com.letsign.mobile.domain.model.AuditType
import com.letsign.mobile.domain.model.Document
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import com.letsign.mobile.domain.model.LocalPdf
import com.letsign.mobile.domain.model.Participant
import com.letsign.mobile.domain.model.PdfKind
import com.letsign.mobile.domain.model.Role
import com.letsign.mobile.domain.model.RoleLabel
import com.letsign.mobile.domain.model.SignatureField
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.model.User

/** Tela inteira de celular, em tema claro e escuro. Usada em todos os previews de tela. */
@Preview(name = "Claro", widthDp = 360, heightDp = 780, showBackground = true)
@Preview(name = "Escuro", widthDp = 360, heightDp = 780, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ScreenPreviews

/** Dados fictícios só para os previews (nenhuma chamada de rede). */
object PreviewData {
    val admin = User(id = "u-1", nome = "Marina Albuquerque", email = "marina@letssign.com", role = Role.ADMIN)
    val joao = User(id = "u-2", nome = "João Pereira", email = "joao.pereira@email.com", telefone = "(11) 98765-4321")
    val ana = User(id = "u-3", nome = "Ana Costa", email = "ana.costa@email.com", telefone = "(21) 99876-5432")
    val carlos = User(id = "u-4", nome = "Carlos Lima", email = "carlos@email.com", telefone = "(31) 97654-3210")
    val signers = listOf(ana, carlos, joao, User(id = "u-5", nome = "Beatriz Souza", email = "bia.souza@email.com", telefone = "(41) 98888-7777"))

    val participants = listOf(
        Participant(id = "p-1", documentId = "d-1", signerId = joao.id, status = SignerStatus.SIGNED, roleLabel = RoleLabel.CONTRATANTE,
            invitedAt = "2026-10-20T14:00:00.000Z", viewedAt = "2026-10-20T15:10:00.000Z", signedAt = "2026-10-20T15:12:00.000Z", signer = joao),
        Participant(id = "p-2", documentId = "d-1", signerId = ana.id, status = SignerStatus.VIEWED, roleLabel = RoleLabel.CONTRATADO,
            invitedAt = "2026-10-20T14:00:00.000Z", viewedAt = "2026-10-21T09:30:00.000Z", signer = ana),
        Participant(id = "p-3", documentId = "d-1", signerId = carlos.id, status = SignerStatus.PENDING, roleLabel = RoleLabel.TESTEMUNHA,
            required = false, invitedAt = "2026-10-20T14:00:00.000Z", signer = carlos)
    )

    val fields = listOf(
        SignatureField(id = "f-1", documentId = "d-1", signerId = joao.id, page = 3, x = 0.08f, y = 0.78f, width = 0.38f, height = 0.08f),
        SignatureField(id = "f-2", documentId = "d-1", signerId = ana.id, page = 3, x = 0.54f, y = 0.78f, width = 0.38f, height = 0.08f),
        SignatureField(id = "f-3", documentId = "d-1", signerId = carlos.id, page = 4, x = 0.08f, y = 0.2f, width = 0.38f, height = 0.08f, required = false)
    )

    val events = listOf(
        AuditEvent(id = "e-1", tipo = AuditType.CREATED, descricao = "Documento \"Contrato de Prestação de Serviços\" criado por Marina Albuquerque.", timestamp = "2026-10-20T13:45:00.000Z"),
        AuditEvent(id = "e-2", tipo = AuditType.SENT, descricao = "Documento enviado para 3 signatário(s): João Pereira, Ana Costa, Carlos Lima.", timestamp = "2026-10-20T14:00:00.000Z"),
        AuditEvent(id = "e-3", tipo = AuditType.VIEWED, descricao = "João Pereira visualizou o documento.", timestamp = "2026-10-20T15:10:00.000Z"),
        AuditEvent(id = "e-4", tipo = AuditType.SIGNED, descricao = "João Pereira assinou o documento.", timestamp = "2026-10-20T15:12:00.000Z"),
        AuditEvent(id = "e-5", tipo = AuditType.VIEWED, descricao = "Ana Costa visualizou o documento.", timestamp = "2026-10-21T09:30:00.000Z")
    )

    val document = Document(
        id = "d-1",
        ownerId = admin.id,
        titulo = "Contrato de Prestação de Serviços",
        status = DocumentStatus.PARTIALLY_SIGNED,
        versao = 1,
        hashSha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        tamanhoBytes = 248_000,
        nomeArquivo = "contrato-servicos.pdf",
        totalPaginas = 4,
        criadoEm = "2026-10-20T13:45:00.000Z",
        enviadoEm = "2026-10-20T14:00:00.000Z",
        owner = admin,
        signers = participants,
        fields = fields,
        events = events,
        totalSignatarios = 3,
        assinados = 1,
        papel = "OWNER"
    )

    val documents = listOf(
        document,
        document.copy(id = "d-2", titulo = "Termo de Confidencialidade (NDA)", status = DocumentStatus.PENDING, nomeArquivo = "nda.pdf", totalPaginas = 2, criadoEm = "2026-10-22T09:10:00.000Z", assinados = 0, totalSignatarios = 2),
        document.copy(id = "d-3", titulo = "Aditivo contratual nº 3", status = DocumentStatus.DRAFT, nomeArquivo = "aditivo-3.pdf", totalPaginas = 1, criadoEm = "2026-10-24T17:30:00.000Z", assinados = null, totalSignatarios = null),
        document.copy(id = "d-4", titulo = "Contrato de Locação Residencial", status = DocumentStatus.COMPLETED, nomeArquivo = "locacao.pdf", totalPaginas = 12, criadoEm = "2026-09-30T11:00:00.000Z", assinados = 3, totalSignatarios = 3),
        document.copy(id = "d-5", titulo = "Proposta comercial 2026", status = DocumentStatus.CANCELLED, nomeArquivo = "proposta.pdf", totalPaginas = 6, criadoEm = "2026-09-12T08:20:00.000Z", assinados = 0, totalSignatarios = 1)
    )

    /** O mesmo contrato visto pela Ana (signatária): só os campos dela, papel SIGNER. */
    val signerDocument = document.copy(
        papel = "SIGNER",
        minhaSituacao = SignerStatus.VIEWED,
        meuPapel = RoleLabel.CONTRATADO,
        fields = fields.filter { it.signerId == ana.id }
    )

    val signerDocuments = listOf(
        signerDocument,
        documents[1].copy(papel = "SIGNER", minhaSituacao = SignerStatus.PENDING, meuPapel = RoleLabel.TESTEMUNHA),
        documents[3].copy(papel = "SIGNER", minhaSituacao = SignerStatus.SIGNED, meuPapel = RoleLabel.CONTRATADO, assinadoEm = "2026-10-01T10:00:00.000Z")
    )

    val localPdfs = listOf(
        LocalPdf("d-1", PdfKind.ORIGINAL, 1, "contrato-servicos.pdf", "/data/.../d-1-original-v1.pdf", 248_000, 1_761_400_000_000)
    )

    val pickedPdf = PickedPdf("/preview/contrato.pdf", "contrato-servicos.pdf", 248_000, PdfInfo(4, List(4) { 0.707f }))

    val draftParticipants = listOf(
        DraftParticipant(joao, RoleLabel.CONTRATANTE, true),
        DraftParticipant(ana, RoleLabel.CONTRATADO, true),
        DraftParticipant(carlos, RoleLabel.TESTEMUNHA, false)
    )

    val draftFields = listOf(
        DraftField("l-1", joao.id, 1, 0.08f, 0.16f, 0.38f, 0.08f),
        DraftField("l-2", ana.id, 1, 0.54f, 0.16f, 0.38f, 0.08f),
        DraftField("l-3", carlos.id, 1, 0.31f, 0.30f, 0.38f, 0.07f)
    )
}
