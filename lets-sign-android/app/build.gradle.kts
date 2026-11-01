plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
}

android {
    namespace = "com.letsign.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.letsign.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Padrão: 10.0.2.2 = localhost da máquina vista de dentro do emulador.
        // Celular físico: ./gradlew assembleDebug -PapiUrl=http://IP-DO-COMPUTADOR:3000/
        val apiUrl = providers.gradleProperty("apiUrl").getOrElse("http://10.0.2.2:3000/")
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        // Padrão: tudo fica no aparelho (Room é a fonte da verdade, sem servidor).
        // Com -Pmodo=api o app usa o backend NestJS e o Room vira cache local.
        val usarApi = providers.gradleProperty("modo").orNull == "api"
        buildConfigField("boolean", "USE_API", "$usarApi")
    }

    signingConfigs {
        create("release") {
            System.getenv("KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sem KEYSTORE_PATH o APK release sai sem assinatura (útil para CI sem segredos).
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Esquemas exportados do Room viram assets do teste de migração.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Esquemas do banco local versionados em app/schemas (base para migrações futuras).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Banco local (requisito: registros em Room/SQLite)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // PDF final assinado gerado no aparelho (opção sugerida no desafio: Canvas + PDFBox-Android)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Só para criar um android.net.Uri falso no teste do assistente (Uri não tem construtor público).
    testImplementation("org.mockito:mockito-core:5.24.0")

    // Teste do fluxo completo no modo local (Room em memória + PDF real), roda no emulador.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
}
