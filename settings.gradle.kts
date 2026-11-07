pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nova"

include(
    ":app",
    ":core:design",
    ":core:config",
    ":core:modules",
    ":core:modes",
    ":core:telemetry",
    ":core:safemode",
    ":core:device",
    ":core:system",
    ":core:update",
    ":feature:home",
    ":feature:onboarding",
    ":feature:improve",
    ":feature:ocr",
)
