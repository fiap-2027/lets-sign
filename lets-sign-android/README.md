<div align="center">

# ✍️ LetsSign Android

**App nativo Android para preparar, enviar, assinar e acompanhar documentos PDF**

Desafio FIAP × LetsSign, Sprint 1 · Kotlin · Jetpack Compose · Room · Hilt

![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-offline--first-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-3DDC84?logo=android&logoColor=white)
![Testes](https://img.shields.io/badge/testes-28%20passando-success)

</div>

---

## 📱 Telas

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-login.png" width="190" alt="Login" /><br/><sub>Login</sub></td>
    <td align="center"><img src="docs/screenshots/02-admin-inicio.png" width="190" alt="Início do Admin" /><br/><sub>Admin: documentos</sub></td>
    <td align="center"><img src="docs/screenshots/03-assistente-campos.png" width="190" alt="Editor de campos" /><br/><sub>Posicionar campos</sub></td>
    <td align="center"><img src="docs/screenshots/04-detalhe.png" width="190" alt="Acompanhamento" /><br/><sub>Acompanhamento</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/05-signatario-inicio.png" width="190" alt="Início do Signatário" /><br/><sub>Signatário: pendentes</sub></td>
    <td align="center"><img src="docs/screenshots/06-assinar.png" width="190" alt="Assinar" /><br/><sub>Assinar</sub></td>
    <td align="center"><img src="docs/screenshots/16-assistente-signatarios.png" width="190" alt="Signatários do documento" /><br/><sub>Escolher signatários</sub></td>
    <td align="center"><img src="docs/screenshots/17-assistente-revisao.png" width="190" alt="Revisão" /><br/><sub>Revisar e enviar</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/02-admin-inicio-escuro.png" width="190" alt="Modo escuro" /><br/><sub>Modo escuro</sub></td>
    <td align="center"><img src="docs/screenshots/04-detalhe-escuro.png" width="190" alt="Detalhe escuro" /><br/><sub>Modo escuro</sub></td>
    <td align="center"><img src="docs/screenshots/20-signatarios.png" width="190" alt="Cadastro de signatários" /><br/><sub>Cadastro de signatários</sub></td>
    <td align="center"><img src="docs/screenshots/18-assistente-falha-envio.png" width="190" alt="Falha no envio" /><br/><sub>Falha: tentar sem duplicar</sub></td>
  </tr>
</table>

### No emulador, com o backend real

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/fluxo-real/1-pdf-invalido.png" width="150" alt="PDF inválido" /><br/><sub>PDF corrompido recusado</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/2-campos-posicionados.png" width="150" alt="Campos no PDF real" /><br/><sub>Campos no PDF real</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/4-onde-assinar.png" width="150" alt="Onde assinar" /><br/><sub>Signatário vê onde assinar</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/6-pdf-final-assinado.png" width="150" alt="PDF final" /><br/><sub>PDF final assinado</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/fluxo-real/7-nova-versao-confirmar.png" width="150" alt="Nova versão" /><br/><sub>Nova versão: confirmação</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/8-nova-versao-campos-mantidos.png" width="150" alt="Campos mantidos na v2" /><br/><sub>v2 com campos mantidos</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/3-acompanhamento.png" width="150" alt="Acompanhamento" /><br/><sub>Acompanhamento</sub></td>
    <td align="center"><img src="docs/screenshots/fluxo-real/5-concluido.png" width="150" alt="Concluído" /><br/><sub>Concluído</sub></td>
  </tr>
</table>

> As telas da primeira parte saem dos `@Preview` do código, com dados fictícios. Para regerar: `bash docs/screenshots/capturar.sh`.

## ✨ Funcionalidades

**Admin**
- Envia um PDF do celular. O app recusa arquivo inválido, corrompido, protegido por senha ou acima de 15 MB.
- Escolhe e classifica signatários (contratante, contratado, testemunha), define quem é obrigatório, remove ou substitui.
- Vê as páginas e **posiciona os campos de assinatura arrastando**. Pode redimensionar, excluir e pôr vários campos por pessoa.
- Revisa tudo, escreve a mensagem do convite e envia. Se a conexão cair, **tenta de novo sem duplicar**.
- Acompanha a situação geral e a de cada signatário, com data e hora, o histórico completo e o PDF final assinado.
- Cadastra, edita, busca e remove signatários.
- Precisa mudar algo depois de enviar? **Envia uma nova versão do PDF.** A anterior fica preservada no histórico, as assinaturas recomeçam e signatários e campos são mantidos para revisar.

**Signatário**
- Vê os documentos pendentes, os assinados e os cancelados.
- Vê exatamente onde vai assinar, assina com o dedo ou recusa informando o motivo.
- O status é atualizado e o PDF final fica salvo no aparelho.

**Para os dois**
- **Banco local (Room)** e PDFs no aparelho: a lista abre na hora e funciona sem internet (leitura).
- Dados isolados por usuário, apagados no logout ou quando a sessão expira.
- Tema claro e escuro.

O que ainda pode evoluir está em **[MELHORIAS.md](MELHORIAS.md)**, com o placar de aderência ao desafio: **41 de 41 itens atendidos**.

## 🚀 Como rodar

### Pré-requisitos

- **Android Studio** recente (AGP 9.4, Gradle 9.8) com o **Android SDK 37**
- **Backend LetsSign** na porta 3000 (`letssign_backend/nodejs_space`, veja o README da raiz do repositório)

### Passo a passo

1. Suba o backend (`npm run start:dev`).
2. No Android Studio: **File → Open** → pasta **`lets-sign-android`** → aguarde o *Gradle sync*.
3. Escolha um emulador e clique em **▶ Run**.
4. Crie uma conta (vira **Admin**), cadastre signatários e envie um PDF. Para assinar, entre com o e-mail e a senha de um signatário.

> **Celular físico:** gere o APK apontando para o IP do computador na rede, sem editar código:
> `./gradlew assembleDebug -PapiUrl=http://192.168.0.10:3000/` (troque pelo seu IP; no Windows, veja com `ipconfig`).
> Celular e computador precisam estar no mesmo Wi-Fi, e o backend precisa estar rodando.

### Linha de comando

```bash
./gradlew assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew installDebug         # instala no aparelho conectado
./gradlew testDebugUnitTest    # 28 testes unitários
```

## 🏗️ Arquitetura

```
Telas (Compose) → ViewModels (StateFlow) → Interfaces de repositório → Room + arquivos  ⇄  API REST
```

- **Offline-first:** as telas leem do Room; a API sincroniza o banco.
- **Repositórios por interface:** dá para trocar a fonte de dados (só local, só REST ou as duas) sem tocar nas telas.
- **Regras de negócio** em funções puras e testadas (`domain/rules/DocumentRules.kt`).
- Cada tela tem `XxxScreen` (com ViewModel) e `XxxContent` (sem estado), o que permite **ver todas as telas no Preview do Android Studio** sem rodar o app nem o backend.

Rotas, tabelas, fluxos, regras e endpoints: **[docs/DOCUMENTACAO.md](docs/DOCUMENTACAO.md)**.

## 🧰 Tecnologias

| Área | Biblioteca |
|---|---|
| Interface | Jetpack Compose, Material 3, Navigation Compose |
| PDF | `PdfRenderer` nativo do Android (sem biblioteca externa) |
| Banco local | Room 2.8 (esquema versionado em `app/schemas`) |
| Injeção de dependência | Hilt (KSP) |
| Rede | Retrofit 3, OkHttp, kotlinx-serialization |
| Testes / CI | JUnit, kotlinx-coroutines-test, GitHub Actions |

## 🔐 Segurança

- JWT enviado em todas as requisições. Um **401 encerra a sessão** e apaga os dados locais.
- O token fica fora dos backups do Android.
- HTTP sem TLS é liberado só no build debug; o release exige HTTPS.
- Os PDFs ficam no armazenamento privado do app e são compartilhados via `FileProvider`.
- Cada assinatura vai com metadados do gesto (traços, pontos, duração, aparelho). O backend grava o hash SHA-256.

## 📄 Licença

Proprietário: LetsSign. Todos os direitos reservados.
