#!/usr/bin/env bash
# Regera as imagens desta pasta a partir dos @Preview do app.
# Abre cada preview no emulador/celular conectado via PreviewActivity (já vem no APK debug)
# e tira um screenshot em tema claro e escuro. Não precisa do backend rodando.
#
# Uso (com um emulador aberto):  bash docs/screenshots/capturar.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
PKG=com.letsign.mobile.debug
OUT=docs/screenshots
UI=com.letsign.mobile.presentation.ui

./gradlew :app:installDebug -q

shot() { # arquivo, função @Preview (pacote.ArquivoKt.funcao), tema (no|yes)
  "$ADB" shell cmd uimode night "$3" > /dev/null
  "$ADB" shell am start -W -S -n "$PKG/androidx.compose.ui.tooling.PreviewActivity" --es composable "$2" > /dev/null
  sleep 3  # tempo para animações (ex.: números do painel ESG) terminarem
  "$ADB" exec-out screencap -p > "$OUT/$1.png"
  echo "ok  $OUT/$1.png"
}

S=$UI.screens
for tema in no yes; do
  sufixo=$([ "$tema" = yes ] && echo "-escuro" || echo "")
  shot "01-login$sufixo"                "$S.auth.AuthScreensKt.LoginScreenPreview" "$tema"
  shot "02-admin-inicio$sufixo"         "$S.documents.DocumentListScreensKt.AdminHomePreview" "$tema"
  shot "03-assistente-campos$sufixo"    "$S.wizard.NewDocumentScreenKt.WizardFieldsPreview" "$tema"
  shot "04-detalhe$sufixo"              "$S.documents.DocumentDetailScreenKt.DocumentDetailAdminPreview" "$tema"
  shot "05-signatario-inicio$sufixo"    "$S.documents.DocumentListScreensKt.SignerHomePreview" "$tema"
  shot "06-assinar$sufixo"              "$S.sign.SignDocumentScreenKt.SignDocumentPreview" "$tema"
done
shot "07-impacto-esg"              "$UI.screens.esg.EsgScreenKt.EsgPreview" no
shot "08-modelo-inteligente"       "$S.wizard.NewDocumentScreenKt.WizardTemplateAppliedPreview" no
shot "09-assinatura-etapas"        "$S.sign.SignDocumentScreenKt.SignDocumentHandwrittenPreview" no
shot "10-cadastro"                 "$S.auth.AuthScreensKt.RegisterScreenPreview" no
shot "11-login-erro"               "$S.auth.AuthScreensKt.LoginScreenErrorPreview" no
shot "12-admin-vazio"              "$S.documents.DocumentListScreensKt.AdminHomeEmptyPreview" no
shot "13-admin-offline"            "$S.documents.DocumentListScreensKt.AdminHomeOfflinePreview" no
shot "14-assistente-documento"     "$S.wizard.NewDocumentScreenKt.WizardDataPreview" no
shot "15-assistente-pdf-invalido"  "$S.wizard.NewDocumentScreenKt.WizardInvalidPdfPreview" no
shot "16-assistente-signatarios"   "$S.wizard.NewDocumentScreenKt.WizardSignersPreview" no
shot "17-assistente-revisao"       "$S.wizard.NewDocumentScreenKt.WizardReviewPreview" no
shot "18-assistente-falha-envio"   "$S.wizard.NewDocumentScreenKt.WizardSendFailedPreview" no
shot "19-assistente-enviado"       "$S.wizard.NewDocumentScreenKt.WizardSuccessPreview" no
shot "20-signatarios"              "$S.signers.SignersScreenKt.SignersPreview" no
shot "21-signatario-formulario"    "$S.signers.SignersScreenKt.SignerFormPreview" no
shot "22-detalhe-signatario"       "$S.documents.DocumentDetailScreenKt.DocumentDetailSignerPreview" no
shot "23-signatario-assinados"     "$S.documents.DocumentListScreensKt.SignerHomeSignedPreview" no
shot "24-assinatura-concluida"     "$S.sign.SignDocumentScreenKt.SignDocumentDonePreview" no
shot "25-visualizador-pdf"         "$S.pdf.PdfViewerScreenKt.PdfViewerPreview" no
shot "27-nova-versao"              "$S.wizard.NewDocumentScreenKt.WizardNewVersionPreview" no
shot "28-documento-final"          "$S.wizard.NewDocumentScreenKt.WizardFullPdfPreview" no
shot "26-area-assinatura"         "$UI.components.SignaturePadKt.SignaturePadSignedPreview" no

"$ADB" shell cmd uimode night no > /dev/null

# Recorta a barra de status (no PreviewActivity ela fica com ícones sem contraste). Só no Windows.
if command -v powershell.exe > /dev/null; then
  topo=$("$ADB" shell dumpsys window | grep -m1 -o 'type=statusBars frame=\[0,0\]\[[0-9]*,[0-9]*\]' | grep -o '[0-9]*\]$' | tr -d ']')
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$OUT/recortar.ps1")" -Pasta "$(cygpath -w "$OUT")" -Topo "${topo:-63}"
  echo "barra de status recortada (${topo:-63}px)"
fi
