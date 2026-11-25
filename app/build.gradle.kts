// SPDX-License-Identifier: GPL-3.0-or-later
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from keystore.properties (never committed) or from environment
// variables. Without either, only debug builds are signed.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

android {
    namespace = "io.github.chrisv8.dialerenhancements"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.chrisv8.dialerenhancements"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            // DexKit ships native code; arm64 covers every Android 12+ phone.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val storeFile = signingValue("storeFile", "DE_KEYSTORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = signingValue("storePassword", "DE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // Keep only the Xposed module descriptor files from Java resources.
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Provided by the Xposed framework (Vector/LSPosed) at runtime, so it is not bundled.
    compileOnly("io.github.libxposed:api:102.0.0")
    // App side of the framework: lets the settings screen write settings the hooks can read.
    implementation("io.github.libxposed:service:102.0.0")
    // Searches Google Phone's code for methods by their text markers.
    implementation("org.luckypray:dexkit:2.3.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    testImplementation("junit:junit:4.13.2")
}
