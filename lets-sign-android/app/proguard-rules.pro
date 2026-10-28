# Retrofit, OkHttp e kotlinx.serialization já trazem suas regras R8 (consumer rules).
# Adicione aqui apenas regras específicas do app, se o build release precisar.

# PDFBox-Android (PDF final assinado): o decodificador JPEG 2000 é opcional e não está no app.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
# Carrega fontes e tabelas por nome em tempo de execução: manter as classes evita erro só no release.
-keep class com.tom_roush.pdfbox.** { *; }
