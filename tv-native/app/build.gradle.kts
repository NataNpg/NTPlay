plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.natanael.ntplay.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.natanael.ntplay.tv"
        minSdk = 21
        targetSdk = 34
        // versionCode sobe sozinho a cada compilação no GitHub (NT_VERSION_CODE = 1000 + nº da execução); local usa 14
        versionCode = (System.getenv("NT_VERSION_CODE") ?: "14").toInt()
        versionName = "1.10.7"
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
            signingConfig = if (System.getenv("NT_KS_FILE") != null) signingConfigs.getByName("ntplay") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
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
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
