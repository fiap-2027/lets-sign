# Próximos passos — LetsSign

Roteiro para as próximas sessões com o Claude. Cada passo tem um **texto pronto para copiar e colar**.

Atualizado em 25/10/2026.

---

## Onde paramos

- O app Android (`lets-sign-android`) **atende os 41 itens do desafio** FIAP × LetsSign.
- 28 testes automáticos passando.
- Testado no emulador com o backend de verdade.
- Detalhes em [`lets-sign-android/MELHORIAS.md`](lets-sign-android/MELHORIAS.md).
- **Ainda não está no GitHub:** a pasta não é um repositório Git.
- Backup do código original: `C:\Users\MKosky\code\lets-sign-android-backup-2026-10-25`.

---

## Como começar cada sessão

Cole isto primeiro, para o Claude retomar o contexto:

```
Estou continuando o projeto LetsSign em C:\Users\MKosky\code\lets-sign-main.
Leia PROXIMOS-PASSOS.md e lets-sign-android/MELHORIAS.md antes de começar.
Hoje quero fazer o passo [NÚMERO] do PROXIMOS-PASSOS.md.
```

No fim de cada sessão, peça: *"atualize o PROXIMOS-PASSOS.md com o que foi feito"*.

---

## Ordem sugerida

| # | Passo | Para quê | Tempo do Claude | Precisa de você? |
|---|---|---|---|---|
| 1 | Testar no seu celular | Ver o app funcionando de verdade | 30 min | Sim: celular com cabo USB |
| 2 | Publicar no GitHub | Guardar o código e mostrar para a FIAP | 30 min | Sim: conta no GitHub |
| 3 | Corrigir o backend e o painel web | 2 problemas encontrados fora do app | 1 hora | Não |
| 4 | Assinar com digital ou rosto | Mais segurança na assinatura | 0,5 dia | Não |
| 5 | Funcionar sem internet | Criar, enviar e assinar offline | 2 a 3 dias | Não |
| 6 | Não perder o rascunho | Se o celular fechar o app no meio | 0,5 dia | Não |
| 7 | Proteger o login no celular | Senha de acesso guardada criptografada | 0,5 dia | Não |
| 8 | Mais testes automáticos | Testar as telas e o banco do celular | 1 dia | Não |
| 9 | Acabamento | Ícone, textos, modo escuro | 0,5 dia | Não |

Os passos 1 e 2 são os mais importantes antes de apresentar o projeto. Os demais podem ser feitos em qualquer ordem.

---

## 1. Testar no seu celular Android

**O que é:** instalar o app no seu celular, ligado ao backend rodando no computador.

> **APK já gerado em 25/10/2026:** `Área de Trabalho\LetsSign-teste-192.168.3.126.apk`, apontando para o IP `192.168.3.126`.
> Se o IP do computador mudar (ele é distribuído pelo roteador e pode trocar), peça um APK novo.
>
> **Para ligar o servidor:** dê dois cliques em `letssign_backend\nodejs_space\iniciar-servidor.bat` e deixe a janela aberta enquanto testa. Ela mostra o "Endereço para o celular". Se for diferente de `192.168.3.126`, o APK precisa ser gerado de novo.

**Antes da sessão:**
1. No celular, ative o **Modo desenvolvedor**: Configurações → Sobre o telefone → toque 7 vezes em "Número da versão".
2. Em Opções do desenvolvedor, ative a **Depuração USB**.
3. Ligue o celular no computador pelo cabo e aceite o aviso que aparecer na tela.
4. Celular e computador precisam estar **no mesmo Wi-Fi**.

**Cole na sessão:**
```
Quero testar o app LetsSign no meu celular Android, que está ligado no computador pelo cabo USB.
Suba o backend, descubra o IP do computador na rede, configure o app para usar esse IP,
instale no celular e me diga o passo a passo para testar o fluxo completo (admin envia, signatário assina).
```

---

## 2. Publicar no GitHub

**O que é:** transformar a pasta em repositório Git e enviar para o GitHub.

> **Pelo navegador (25/10/2026):** a pasta `Área de Trabalho\LetsSign-para-GitHub` tem só os 226 arquivos certos, divididos em 4 envios de até 100, com o passo a passo no `LEIA-ME.txt`.
> A página de upload do GitHub não respeita o `.gitignore`. Arrastar as pastas originais envia `node_modules`, `build` e o `.env` com senhas, e esbarra no limite de 100 arquivos e 25 MB.

**Antes da sessão:**
1. Tenha uma conta no [github.com](https://github.com).
2. Decida se o repositório será **público** (qualquer um vê) ou **privado** (só quem você convidar).

**Cole na sessão:**
```
Quero publicar o projeto LetsSign (C:\Users\MKosky\code\lets-sign-main) no GitHub.
Crie o repositório Git, confira que nenhum segredo vai junto (.env, senhas, banco local),
faça o primeiro commit e me ajude a enviar para um repositório [PÚBLICO ou PRIVADO] chamado lets-sign.
Me explique cada passo que eu precisar fazer no navegador.
```

> O `.gitignore` da raiz já bloqueia o `.env` do backend, que tem a chave do login (`JWT_SECRET`) e a senha do e-mail. Mesmo assim, o Claude deve conferir antes de enviar.

---

## 3. Corrigir o backend e o painel web

**O que é:** 2 problemas encontrados fora do app Android (o terceiro, a lista de dependências desatualizada, já foi resolvido em 25/10).
- O backend aceita signatário sem telefone, mas o desafio exige o telefone.
- O painel web chama um endereço de login (`/auth/refresh`) que não existe no backend.

**Cole na sessão:**
```
Corrija os problemas pendentes da seção "Encontrados fora do app Android" do lets-sign-android/MELHORIAS.md:
telefone obrigatório no cadastro de signatário e a chamada /auth/refresh do painel web. Teste cada correção.
```

---

## 4. Assinar com digital ou reconhecimento facial

**O que é:** antes de assinar, o celular pede a digital ou o rosto. O backend registra que a identidade foi confirmada.

**Cole na sessão:**
```
Faça o item "Biometria" das pendências do MELHORIAS.md: pedir digital/rosto (BiometricPrompt)
antes de assinar e enviar biometriaVerificada no gestureMetadata. Se o celular não tiver biometria,
use o PIN/padrão de bloqueio. Teste no emulador.
```

---

## 5. Funcionar sem internet

**O que é:** hoje o app **mostra** os documentos sem internet, mas para criar, enviar ou assinar precisa estar conectado. Com este passo, as ações ficam numa fila e são enviadas quando a internet voltar.

**Cole na sessão:**
```
Faça o item "Fila offline" das pendências do MELHORIAS.md: permitir criar, enviar e assinar
sem internet, guardando as ações no banco local e enviando com WorkManager quando a conexão voltar.
Mostre na tela o que está aguardando envio. Teste desligando a internet do emulador.
```

---

## 6. Não perder o rascunho

**O que é:** se o Android fechar o app no meio do cadastro de um documento (por falta de memória, por exemplo), hoje o que foi preenchido se perde. Com este passo, o rascunho fica salvo.

**Cole na sessão:**
```
Faça o item "Salvar o rascunho do assistente no Room" das pendências do MELHORIAS.md.
Ao reabrir o app, ofereça continuar o rascunho. Teste matando o processo do app no meio do assistente.
```

---

## 7. Proteger o login guardado no celular

**O que é:** o "passe" de login (token) fica guardado no celular sem criptografia. Este passo o criptografa com o cofre de chaves do Android.

**Cole na sessão:**
```
Faça o item "Token criptografado" das pendências do MELHORIAS.md, usando o Android Keystore.
Quem já estiver logado não pode perder a sessão na atualização.
```

---

## 8. Mais testes automáticos

**O que é:** hoje os 28 testes rodam no computador. Este passo cria testes que rodam no próprio Android: o banco de dados e as telas do assistente.

**Cole na sessão:**
```
Faça o item "Testes instrumentados" das pendências do MELHORIAS.md: testes do banco Room
(banco em memória) e um teste de tela do assistente de novo documento. Rode no emulador.
```

---

## 9. Acabamento

**O que é:**
- Ícone definitivo do app.
- Textos em arquivo próprio, o que permite traduzir para outros idiomas.
- Ajuste da barra de status no modo escuro.
- Organização das versões das bibliotecas.

**Cole na sessão:**
```
Faça os itens de acabamento das pendências do MELHORIAS.md: ícone adaptativo do app,
textos em strings.xml, contraste da barra de status no modo escuro e version catalog.
Depois regere as imagens do README.
```

---

## Ideias para depois (fora do desafio)

- **Notificação no celular** quando chegar um documento para assinar.
- **Versão para iPhone:** o desafio permite Android ou iOS; o backend já serve os dois.
- **Painel web** mais completo, usando a mesma API.

Para qualquer uma, cole: *"Quero fazer [IDEIA]. Me diga primeiro o que precisa e quanto tempo leva, antes de começar."*
