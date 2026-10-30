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
        versionCode = 1
        versionName = "0.1.0"
        // Repository da cui arrivano gli aggiornamenti (SPEC §4.5).
        buildConfigField("String", "GITHUB_REPO", "\"liam26513-maker/nova\"")
    }

    sourceSets["main"].assets.srcDir(rootProject.file("config"))

    buildTypes {
        release {
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
    implementation(project(":core:modules"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:safemode"))
    implementation(project(":core:device"))
    implementation(project(":core:system"))
    implementation(project(":core:update"))

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
