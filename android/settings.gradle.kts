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
        google() // <--- Ye line sab se pehle hona laazmi hai
        mavenCentral()
    }
}

rootProject.name = "SanadOCR"
include(":app")