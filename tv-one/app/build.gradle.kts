plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.natanael.ntplay.tv"
    compileSdk = 34
    defaultConfig {
        // applicationId próprio: NTPlay-one instala AO LADO do NTPlay normal, sem conflito.
        applicationId = "com.natanael.ntplayone.tv"
        minSdk = 21
        targetSdk = 34
        // versionCode sobe sozinho a cada compilação no GitHub (NT_VERSION_CODE = 1000 + nº da execução); local usa 1
        versionCode = (System.getenv("NT_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        // chave do TMDB vem do GitHub Secret TMDB_KEY (fica fora do repositório); sem ela o app usa só as outras fontes
        buildConfigField("String", "TMDB_KEY", "\"" + (System.getenv("TMDB_KEY") ?: "") + "\"")
        // libVLC traz .so por arquitetura; só arm cobre quase todo stick/box de TV e deixa o APK bem menor.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    signingConfigs {
        create("ntplay") {
            // chave FIXA e SECRETA (vem dos GitHub Secrets na compilação). Sem ela, cada build teria assinatura diferente
            // e o Android recusaria a atualização por cima.
            val ks = System.getenv("NT_KS_FILE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("NT_KS_PASS")
                keyAlias = "ntplay"
                keyPassword = System.getenv("NT_KS_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // com chave própria (NT_KS_FILE nos Secrets) assina fixo; sem ela cai na chave debug e
            // compila na mesma — bom para gerar o APK logo. Para atualizar por cima depois, use chave fixa.
            signingConfig = if (System.getenv("NT_KS_FILE") != null) signingConfigs.getByName("ntplay") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // Motor de vídeo: libVLC. Roda IPTV (TS/HLS, canais em pedaços, AC3/EAC3/DTS, HEVC/4K) sem os
    // problemas de codec do ExoPlayer, e traz os decodificadores nativos dentro do próprio app.
    implementation("org.videolan.android:libvlc-all:3.6.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
