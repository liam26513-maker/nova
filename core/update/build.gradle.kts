plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.manlio.nova.core.update"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:system"))
    implementation(project(":core:telemetry"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
}
