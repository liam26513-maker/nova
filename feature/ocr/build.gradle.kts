plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Testo dalle immagini (SPEC §12.1): PaddleOCR (PP-OCRv6 tiny) su ONNX Runtime, tutto sul tablet, senza rete né statistiche.
android {
    namespace = "it.manlio.nova.feature.ocr"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // I modelli sono già compressi: comprimerli ancora rallenterebbe solo il caricamento.
    androidResources { noCompress += "onnx" }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
