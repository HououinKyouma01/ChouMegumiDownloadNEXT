pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/tanersener/maven/master") }
        maven { url = uri("https://artifactory.appodeal.com/appodeal-public") }
    }
}

rootProject.name = "MegumiDownloadAndroid"
include(":app")
