# Melhorias — LetsSign Android

**Legenda:** ✅ feito · 🟡 parcial · ⏳ pendente

| Revisão | Data | O que mudou |
|---|---|---|
| 1 | 25/10/2026 | O app não compilava (dezenas de erros). Foi corrigido para compilar, com previews e bugs críticos resolvidos. Veja o [histórico](#histórico-revisão-1-fazer-o-app-compilar) |
| 2 | 25/10/2026 | **Requisitos do desafio implementados:** banco local (Room), PDFs no aparelho, telas por perfil, cadastro de signatários, assistente completo com editor de campos, envio sem duplicar, assinatura, acompanhamento. 24 testes, CI e teste ponta a ponta no emulador com o backend real |
| 3 | 25/10/2026 | **Nova versão de documento enviado:** fecha o último item do desafio (41/41). 28 testes, também testada ponta a ponta |

Backup do código original: `C:\Users\MKosky\code\lets-sign-android-backup-2026-10-25`.

---

## 0. Aderência aos requisitos do desafio (FIAP × LetsSign, Sprint 1)

Fonte: slides em `C:\Users\MKosky\Desktop\challenge` (1.png a 9.png).

### Placar

| Área | Antes (rev. 1) | Agora |
|---|---|---|
| 1. Acesso e tela inicial | 0 ✅ · 3 🟡 · 3 ❌ | **6 ✅** |
| 2. Cadastro de signatários | 0 ✅ · 1 🟡 · 1 ❌ | **2 ✅** |
| 3. Fluxo de cadastro de documento | 8 ❌ | **8 ✅** |
| 4. Posicionamento das assinaturas | 1 🟡 · 4 ❌ | **5 ✅** |
| 5. Revisão e disparo | 1 🟡 · 2 ❌ | **3 ✅** |
| 6. Acompanhamento | 1 ✅ · 1 🟡 · 3 ❌ | **5 ✅** |
| Requisitos técnicos | 1 ✅ · 2 🟡 · 2 ❌ | **5 ✅** |
| Regras de negócio | 4 ✅ · 3 🟡 | **7 ✅** |
| **Total (41 itens)** | **6 ✅ · 12 🟡 · 23 ❌** | **41 ✅** |

### Como cada requisito foi atendido

| Requisito | Como | Verificado em |
|---|---|---|
| Login com 2 perfis | Admin cai em "Meus documentos"; Signatário em "Pendentes / Assinados / Cancelados" (`Routes.homeOf`) | emulador |
| Admin: upload de PDF | Assistente de 4 etapas com seletor nativo de arquivos | emulador |
| Admin: ver enviados, parciais, concluídos | Filtros com contagem: Todos, Rascunhos, Enviados, Parciais, Concluídos, Cancelados | emulador |
| Cadastrar e editar signatários (nome, e-mail, telefone, senha) | Tela de signatários com busca, criar, editar (senha vazia = manter) e remover; os 4 campos validados | emulador |
| Selecionar e classificar signatários | Contratante, Contratado, Testemunha, obrigatório ou opcional; remover e **substituir** (o substituto herda papel e campos) | emulador + teste |
| Visualizar as páginas do PDF | `PdfRenderer` nativo, página por página | emulador |
| PDF inválido, corrompido ou ilegível | Validado ao escolher: não é PDF, corrompido, protegido por senha, sem páginas, > 15 MB | emulador |
| Campos: adicionar, arrastar, mover, excluir, vários por pessoa | Editor com cor por signatário, arraste, redimensionar pelo canto, ✕ para excluir | emulador + teste |
| Posição relativa à página (abrir em outro aparelho igual) | Frações 0..1 em `x`, `y`, `width`, `height` + página; o signatário viu o campo no mesmo lugar | banco do servidor + emulador |
| Revisão: documento, signatários, campos e páginas, mensagem | Resumo com miniaturas das páginas com campos e mensagem do convite (≤ 2000) | emulador |
| Registrar envio, mostrar resultado, tentar de novo **sem duplicar** | O documento é criado uma vez; nova tentativa reaproveita o id; backend idempotente | **API desligada no meio do envio**: 1 documento, 1 evento de envio |
| Acompanhamento: situação geral e individual, datas e horas, histórico, documento final | Detalhe com progresso, signatários com data e hora, linha do tempo e "Ver documento final assinado" | emulador |
| Signatário assina e **salva no sistema** | Assinatura com o dedo e confirmação; o status local atualiza na hora e, se concluir, o PDF final é salvo no aparelho | emulador + banco |
| Persistência local (Room) | 6 tabelas: `documents`, `participants`, `signature_fields`, `audit_events`, `signers`, `pdf_files` | banco puxado do emulador |
| PDF no sistema de arquivos + registro (nome, caminho, data, status) | `filesDir/documentos/<usuário>/` + tabela `pdf_files` | emulador |
| Repositórios prontos para REST | ViewModels só conhecem as interfaces; implementação offline-first (Room + REST) | código |
| Dados de um usuário inacessíveis a outro | `userId` em todas as chaves + limpeza total no login, logout e 401 | logout zerou banco e PDFs |
| Concluir só com todos os obrigatórios | Testemunha opcional recusou e o documento seguiu; concluiu quando os 2 obrigatórios assinaram | emulador |
| Documento enviado preservado; alteração gera **nova versão e novo fluxo** | Documento enviado não abre para edição. "Enviar nova versão do PDF" (só o dono, nunca depois de concluído) valida o PDF, cria a versão N+1 e abre o assistente com signatários e campos mantidos para revisar e reenviar | servidor: versão 1 preservada, assinaturas zeradas, novo envio |

### Teste ponta a ponta realizado (emulador Android 17 + backend real)

1. Admin cadastrou, validou (telefone faltando), editou e removeu um signatário.
2. Escolheu um PDF corrompido: recusado com mensagem. Escolheu um PDF válido de 3 páginas: aceito.
3. Selecionou 3 signatários, classificou e marcou a testemunha como opcional. Posicionou, arrastou, redimensionou e excluiu campos na página 3.
4. **Enviou com a API desligada:** apareceu o erro e os dados ficaram na tela. Com a API de volta, "Tentar novamente" enviou sem duplicar.
5. Logout zerou o banco local. A Contratada entrou, viu só o campo dela, assinou (status local atualizado na hora). A testemunha recusou com motivo. O Contratante assinou e o documento foi **concluído**.
6. O PDF final abriu no app com as duas assinaturas carimbadas nas posições definidas e foi compartilhado com o nome real.
7. **Sessão expirada:** com o token invalidado, o app voltou ao login com o aviso e apagou os dados locais.
8. **Nova versão** (revisão 3):
   - Num documento já assinado pela Contratada, o Admin tocou em "Enviar nova versão do PDF". A confirmação avisou que a assinatura feita seria descartada.
   - O Admin escolheu o PDF reajustado e o assistente abriu como "Nova versão (v2)", com signatários e campos mantidos sobre o PDF novo.
   - Reenviou. No servidor: v2 enviada, **v1 preservada** com sua configuração, assinaturas zeradas, histórico `CREATED → SENT → SIGNED → VERSION_CREATED → SENT`.
   - Documento concluído não mostra a opção.

Problemas que o teste real encontrou e que foram corrigidos na hora:
- O botão "+ Campo" ficava abaixo da página, fora da tela.
- O erro de envio aparecia no topo da revisão, fora da vista.
- A área de assinatura cortava o início de cada traço.
- O visualizador e o compartilhamento usavam o nome interno do arquivo.

Imagens em `docs/screenshots/fluxo-real/`.

---

## Pendências (ordem sugerida)

| # | O quê | Por quê | Esforço |
|---|---|---|---|
| ✅ | ~~Nova versão de documento enviado~~ | Feito na revisão 3 | — |
| ⏳ 1 | **Fila offline** com WorkManager: criar, enviar e assinar sem internet e sincronizar depois | Hoje as telas funcionam offline (leitura), mas ações exigem conexão | 2–3 dias |
| ⏳ 2 | Biometria (BiometricPrompt) antes de assinar e enviar `biometriaVerificada` no `gestureMetadata` (o backend já documenta o campo) | Mais segurança e valor jurídico | 0,5 dia |
| ⏳ 3 | Salvar o rascunho do assistente no Room | Se o Android encerrar o app no meio do assistente, o que foi preenchido se perde (`ponytail:`, ver `NewDocumentViewModel`) | 0,5 dia |
| ⏳ 4 | Token criptografado (Android Keystore / Tink) | Hoje fica em SharedPreferences, mas fora do backup | 0,5 dia |
| ⏳ 5 | Testes instrumentados: DAO do Room (banco em memória) e UI Compose do assistente | Hoje os 28 testes são unitários (JVM) | 1 dia |
| ⏳ 6 | Textos em `strings.xml` (tradução) | Os textos estão no código | 0,5 dia |
| ⏳ 7 | Version catalog (`gradle/libs.versions.toml`) | Versões centralizadas | 1 hora |
| ⏳ 8 | Barra de status no modo escuro: ícones claros sobre o rosa-claro (pouco contraste) | Ajustar `SystemBarStyle` no `enableEdgeToEdge()` | 30 min |
| ⏳ 9 | Ícone final do app (adaptativo, monocromático) | O atual é provisório | 1 hora |

### Encontrados fora do app Android

| Onde | Problema | Sugestão |
|---|---|---|
| `letssign_backend/nodejs_space` | ✅ `package-lock.json` estava desatualizado (faltava `@types/mjml`) e o `npm ci` falhava | Atualizado pelo `npm install` em 25/10/2026; falta só commitar |
| backend | `telefone` é opcional no cadastro de signatário, mas o desafio exige | `@IsNotEmpty()` em `CreateSignerDto.telefone` (o app já exige) |
| `letssign-frontend/src/lib/api.ts` | Chama `POST /auth/refresh`, que **não existe** no backend | Remover o refresh ou criar o endpoint |
| backend | Sem `.gitignore`: `.env` com `JWT_SECRET` e senha SMTP, `dev.db` e `node_modules` iriam para o Git | Resolvido pelo `.gitignore` da raiz do repositório |
| backend | Node 26 + npm novo bloqueiam scripts de instalação: `prisma generate` não roda sozinho | Documentar `npx prisma generate` ou aprovar com `npm install-scripts approve prisma` |

---

## Histórico (revisão 1: fazer o app compilar)

<details>
<summary>O que estava quebrado no código original e foi corrigido</summary>

**Build:** faltava o `gradle-wrapper.jar`, e o Gradle 8.5 não rodava no JDK 25. AGP, Kotlin e KSP estavam com versões incompatíveis. O `signingConfig` era usado antes de ser declarado. O `proguard-rules.pro` e o ícone não existiam. Havia `package=` no Manifest (proibido no AGP 8+), cerca de 15 dependências sem uso e uma que nem existia (`hilt-lifecycle-viewmodel:1.2.0`). O código usava Gson, mas a dependência declarada era kotlinx-serialization.

**Kotlin:** `import` no fim de arquivos; tipo `ApiServices` inexistente com binding duplicado no Dagger; `@HiltViewModel` do pacote errado; `by hiltViewModel()` na Activity; `StateFlow` sem `.value`; `Instant` sem serializer; APIs de Compose que não existem (`Modifier.onClick`, `Icon(onClick=)`, `Column(crossAxisAlignment=)`); pacote `icons.filed`; rota com variável inexistente; telas importadas que nunca foram criadas.

**Bugs de comportamento:**
- **O token nunca era salvo** (`edit().apply {}` do Kotlin em vez do `Editor.apply()`).
- **O login não saía da tela.**
- **A lista e o detalhe nunca carregavam.**
- **Editar um signatário trocava a senha dele para "unchanged"**, e criar ignorava a senha digitada.
- `@Part("pdf")` com `MultipartBody.Part` impedia o upload, e o `Content-Type` JSON forçado quebrava o multipart.
- O PDF era gravado na raiz do armazenamento externo.
- `/sign` e `/reject` retornam `{mensagem, documento}`, mas o app esperava `Document`.
- `gestureMetadata` ia como texto (o backend exige objeto).
- O app chamava `/auth/refresh`, que não existe.

**Segurança:** o JWT ia no backup. Permissões desnecessárias (`MANAGE_EXTERNAL_STORAGE`, câmera, media projection). HTTP sem TLS liberado em release. O header `Authorization` aparecia no log.

**Interface:** cores fixas quebravam o modo escuro, faltava edge-to-edge e o teclado cobria o botão.

</details>
