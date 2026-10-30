plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ----------------------------------------------------------------- motor vindo do app de TV
// O motor (M3U, Xtream, metadados, nuvem) mora em tv-native/ e NÃO é duplicado aqui: é copiado
// na compilação, com o pacote trocado e três remendos. A lógica está em tools/gen_core.py, que
// tem testes próprios (python3 -m unittest discover -s tools) — é a parte mais arriscada deste
// projeto e, como script, dá para testá-la sem SDK do Android.
// Se a TV mudar e um remendo deixar de bater, o script falha e esta compilação para.
val tvSrc = layout.projectDirectory.dir("../../tv-native/app/src/main/java/com/natanael/ntplay/tv")
val coreGen = layout.buildDirectory.dir("generated/core")

val generateCore by tasks.registering(Exec::class) {
    description = "Copia o motor de tv-native/ trocando o pacote para com.natanael.ntplay.core"
    inputs.dir(tvSrc)
    inputs.file(layout.projectDirectory.file("../tools/gen_core.py"))
    outputs.dir(coreGen)
    commandLine(
        "python3",
        layout.projectDirectory.file("../tools/gen_core.py").asFile.absolutePath,
        tvSrc.asFile.absolutePath,
        coreGen.get().dir("com/natanael/ntplay/core").asFile.absolutePath,
    )
}

tasks.named("preBuild") { dependsOn(generateCore) }

android {
    namespace = "com.natanael.ntplay.mobile"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.natanael.ntplay.mobile"
        minSdk = 21
        targetSdk = 34
        // versionCode sobe sozinho a cada compilação no GitHub (NT_VERSION_CODE = 1 + nº da execução); local usa 1
        versionCode = (System.getenv("NT_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
    }
    signingConfigs {
        create("ntplay") {
            // Mesma chave do app de TV (GitHub Secrets). O applicationId é diferente, então os dois
            // APKs convivem no mesmo aparelho; a chave igual só evita manter dois segredos.
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
    sourceSets["main"].java.srcDir(coreGen)
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
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
    testImplementation("junit:junit:4.13.2")
}
