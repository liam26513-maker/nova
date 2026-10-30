plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Modalità provvisoria: dipende SOLO dal nucleo, mai da moduli feature/ (CLAUDE.md, SPEC §14.2).
android {
    namespace = "it.manlio.nova.core.safemode"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:system"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
}
