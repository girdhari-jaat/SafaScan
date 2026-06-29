pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // <--- YE LINE BADAL DI. YAHI MASLA THA
    repositories {
        google() // 
        mavenCentral()
    }
}

rootProject.name = "SanadOCR"
include(":app")