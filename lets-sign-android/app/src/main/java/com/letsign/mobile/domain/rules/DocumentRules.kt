package com.letsign.mobile.domain.rules

import com.letsign.mobile.domain.model.DraftField
import com.letsign.mobile.domain.model.DraftParticipant
import java.util.UUID

// Regras de negócio do desafio validadas no app ANTES de chamar a API (o backend valida de novo).
// Funções puras: cobertas por DocumentRulesTest.

object DocumentRules {
    const val MAX_TITLE = 200
    const val MAX_MESSAGE = 2000
    const val MIN_PASSWORD = 6

    /** Tamanho padrão de um campo novo, em fração da página (≈ 30% x 8%). */
    const val DEFAULT_FIELD_WIDTH = 0.30f
    const val DEFAULT_FIELD_HEIGHT = 0.08f
    const val MIN_FIELD_WIDTH = 0.10f
    const val MIN_FIELD_HEIGHT = 0.03f

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validateTitle(titulo: String): String? = when {
        titulo.isBlank() -> "Informe o título do documento."
        titulo.trim().length > MAX_TITLE -> "O título deve ter no máximo $MAX_TITLE caracteres."
        else -> null
    }

    fun validateMessage(mensagem: String): String? =
        if (mensagem.length > MAX_MESSAGE) "A mensagem deve ter no máximo $MAX_MESSAGE caracteres." else null

    /** Regra: pelo menos 1 signatário, ao menos 1 obrigatório, sem repetição. */
    fun validateParticipants(participants: List<DraftParticipant>): String? = when {
        participants.isEmpty() -> "Selecione pelo menos um signatário."
        participants.none { it.required } -> "Pelo menos um signatário precisa ser obrigatório."
        participants.map { it.signer.id }.toSet().size != participants.size -> "O mesmo signatário foi selecionado mais de uma vez."
        else -> null
    }

    /**
     * Regras: todo campo pertence a um participante, fica dentro da página e numa página existente;
     * todo participante tem pelo menos um campo (o backend recusa o envio sem isso).
     */
    fun validateFields(participants: List<DraftParticipant>, fields: List<DraftField>, pageCount: Int): String? {
        val ids = participants.map { it.signer.id }.toSet()
        if (fields.any { it.signerId !in ids }) return "Existe campo de assinatura sem signatário associado."
        if (fields.any { it.page !in 1..pageCount }) return "Existe campo em uma página que não existe no PDF."
        if (fields.any { !it.isInsidePage() }) return "Existe campo de assinatura fora dos limites da página."
        val semCampo = participants.firstOrNull { p -> fields.none { it.signerId == p.signer.id } }
        if (semCampo != null) return "Posicione pelo menos um campo para ${semCampo.signer.nome}."
        return null
    }

    /** Cadastro da própria conta (Admin): telefone é opcional. */
    fun validateAccount(nome: String, email: String, senha: String): String? = when {
        nome.isBlank() -> "Informe o nome completo."
        !EMAIL.matches(email.trim()) -> "Informe um e-mail válido."
        senha.length < MIN_PASSWORD -> "A senha deve ter pelo menos $MIN_PASSWORD caracteres."
        else -> null
    }

    /** Signatário (requisito: nome, e-mail, telefone e senha). Na edição, senha vazia = manter a atual. */
    fun validateSigner(nome: String, email: String, telefone: String, senha: String, isNew: Boolean): String? = when {
        nome.isBlank() -> "Informe o nome completo."
        !EMAIL.matches(email.trim()) -> "Informe um e-mail válido."
        telefone.count { it.isDigit() } < 10 -> "Informe o telefone com DDD."
        isNew && senha.length < MIN_PASSWORD -> "A senha deve ter pelo menos $MIN_PASSWORD caracteres."
        !isNew && senha.isNotEmpty() && senha.length < MIN_PASSWORD -> "A nova senha deve ter pelo menos $MIN_PASSWORD caracteres."
        else -> null
    }

    /** Campo novo centralizado; cada campo extra na mesma página desce um pouco para não sobrepor. */
    fun newField(signerId: String, page: Int, existingOnPage: Int): DraftField {
        val x = (1f - DEFAULT_FIELD_WIDTH) / 2f
        val y = ((1f - DEFAULT_FIELD_HEIGHT) / 2f + existingOnPage * 0.1f) % (1f - DEFAULT_FIELD_HEIGHT)
        return DraftField(UUID.randomUUID().toString(), signerId, page, x, y, DEFAULT_FIELD_WIDTH, DEFAULT_FIELD_HEIGHT)
    }
}

private const val EPS = 1e-4f

fun DraftField.isInsidePage(): Boolean =
    x >= -EPS && y >= -EPS && x + width <= 1f + EPS && y + height <= 1f + EPS

/** Arrasta o campo (deslocamento em fração da página), sem deixar sair da página. */
fun DraftField.moveBy(dx: Float, dy: Float): DraftField =
    copy(x = (x + dx).coerceIn(0f, 1f - width), y = (y + dy).coerceIn(0f, 1f - height))

/** Redimensiona pelo canto inferior direito, respeitando tamanho mínimo e a borda da página. */
fun DraftField.resizeBy(dw: Float, dh: Float): DraftField = copy(
    width = (width + dw).coerceIn(DocumentRules.MIN_FIELD_WIDTH, 1f - x),
    height = (height + dh).coerceIn(DocumentRules.MIN_FIELD_HEIGHT, 1f - y)
)
