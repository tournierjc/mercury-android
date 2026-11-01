import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.tournierjc.mercury"
    compileSdk = 35
    // Épinglé : le SDK packagé pour arm64 embarque 35.0.0 (AGP viserait 34.0.0 par défaut).
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "io.github.tournierjc.mercury"
        minSdk = 26
        targetSdk = 35
        versionCode = 110
        versionName = "0.61.38"
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                // storeFile is resolved relative to the repo root.
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else if (System.getenv("ANDROID_KEYSTORE_PASSWORD") != null) {
                // CI: the four ANDROID_KEYSTORE_* / ANDROID_KEY_* env vars (see README).
                storeFile = rootProject.file("mercury-release.keystore")
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
            // Otherwise it stays unconfigured: release builds fall back to the debug
            // key below so plain local builds still install over previous versions.
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":core"))

    // The app module had no test source set: the resource contract of a notice (a key spelled in
    // both locales, with the same arguments) is the first thing only this module can check.
    testImplementation(libs.junit)
    // The Configure group now writes: a provider save and a probe are coroutines on a view model,
    // and their two rules — no reload after a refusal, an answer shown only under its own row —
    // can only be checked with a real dispatcher and a fake server.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.nanohttpd)
}
