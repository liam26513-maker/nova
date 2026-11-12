plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "it.manlio.nova"
    compileSdk = 35

    defaultConfig {
        // Non cambiare mai: romperebbe gli aggiornamenti (CLAUDE.md).
        applicationId = "it.manlio.nova"
        minSdk = 31
        targetSdk = 35
        // In CI release.yml li imposta (versionCode sempre crescente); in locale valgono i default.
        versionCode = System.getenv("NOVA_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("NOVA_VERSION_NAME") ?: "0.1.0"
        // Repository da cui arrivano gli aggiornamenti (SPEC §4.5).
        buildConfigField("String", "GITHUB_REPO", "\"liam26513-maker/nova\"")
        // Solo il tablet ARM a 64 bit: la lettura del testo porta librerie native, e le altre architetture peserebbero per niente.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Librerie native compresse nell'APK (ONNX Runtime sono 34 MB non compressi): il download dell'aggiornamento resta piccolo.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    sourceSets["main"].assets.srcDir(rootProject.file("config"))
    // Il CHANGELOG viaggia nell'app per la schermata Novità (SPEC §4.8).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/novita"))

    // Chiave di firma definitiva: solo in CI, dai segreti del repository (mai nel codice).
    val keystore = System.getenv("NOVA_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("NOVA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NOVA_KEY_ALIAS")
                keyPassword = System.getenv("NOVA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        }
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:config"))
    implementation(project(":core:modes"))
    implementation(project(":core:modules"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:safemode"))
    implementation(project(":core:device"))
    implementation(project(":core:system"))
    implementation(project(":core:update"))
    implementation(project(":feature:home"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:improve"))
    implementation(project(":feature:ocr"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

// I test unitari (Robolectric/Compose) girano sulla variante debug: ui-test-manifest esiste solo lì.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

val copyChangelog =
    tasks.register<Copy>("copyChangelog") {
        from(rootProject.file("CHANGELOG.md"))
        into(layout.buildDirectory.dir("generated/novita"))
    }
tasks.named("preBuild") { dependsOn(copyChangelog) }
