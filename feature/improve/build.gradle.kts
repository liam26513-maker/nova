plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Pulsanti Migliora e Ripara (SPEC §4.2-4.3, Fase 0 punto 9).
android {
    namespace = "it.manlio.nova.feature.improve"
    compileSdk = 36
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
