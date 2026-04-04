buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.7.0-alpha07")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("com.google.code.gson:gson:2.10.1")
                // SSHJ & SMBJ are Pure Java/JVM, so they can live in commonMain ONLY IF we strictly target JVM platforms.
                // Since this app targets Android + Desktop (both JVM), this is valid.
                implementation("com.hierynomus:sshj:0.38.0")
                implementation("org.slf4j:slf4j-simple:2.0.9")
                implementation("com.hierynomus:smbj:0.11.5") {
                    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
                }
                implementation("org.bouncycastle:bcprov-jdk18on:1.75")
                // OkHttp is also JVM
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.compose.runtime:runtime-livedata:1.6.0")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.work:work-runtime-ktx:2.9.0")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
                implementation("androidx.datastore:datastore-preferences:1.0.0")
                implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0-2.LTS")

            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
                implementation("io.github.kevinnzou:compose-webview-multiplatform:1.9.20")
            }
        }
    }
}

android {
    namespace = "com.example.megumidownload"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.megumidownload"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        
        // Optimize APK size: Only include native libraries for ARM devices (99% of phones) & x86_64 (Emulators/Chromebooks)
        // Dropping x86 (32-bit) saves space.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }

            val keyFileName = keystoreProperties["key.store"] as? String ?: "release.jks"
            val keyFile = file(keyFileName)

            if (keyFile.exists() && keystoreProperties.containsKey("store.pass")) {
                storeFile = keyFile
                storePassword = keystoreProperties["store.pass"] as String
                keyAlias = keystoreProperties["key.alias"] as String
                keyPassword = keystoreProperties["key.pass"] as String
            } else {
                println("Note: Release signing skipped. keys missing in local.properties or keystore file not found.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // Only sign if the config was successfully set up above
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
            assets.srcDirs("src/androidMain/assets")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.megumidownload.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "MegumiDownload"
            packageVersion = "1.1.0"
        }
        buildTypes.release.proguard {
            version.set("7.5.0")
            configurationFiles.from(project.file("compose-desktop.pro"))
            obfuscate.set(false)
            optimize.set(false)
        }
    }
}
