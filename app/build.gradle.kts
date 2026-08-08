// Signing material comes from the environment so the key never lives in the repo.
// Without it Gradle falls back to the auto-generated debug key — fine locally, but a
// fresh key on every CI runner, which is exactly what makes Android refuse an update.
val signingStore = System.getenv("UNO_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let(::file)
    ?.takeIf { it.exists() }
val signingStorePassword = System.getenv("UNO_KEYSTORE_PASSWORD").orEmpty()
val signingAlias = System.getenv("UNO_KEY_ALIAS").orEmpty().ifBlank { "unoduo" }
val signingKeyPassword = System.getenv("UNO_KEY_PASSWORD").orEmpty().ifBlank { signingStorePassword }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zknw.unoduo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zknw.unoduo"
        minSdk = 26
        targetSdk = 35
        // Bumped by CI so a newer build is never seen as a downgrade.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0"

        // Stamped by CI so the in-app updater knows what is installed.
        val builtFrom = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        buildConfigField("String", "GIT_SHA", "\"$builtFrom\"")
    }

    signingConfigs {
        if (signingStore != null) {
            create("shared") {
                storeFile = signingStore
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Same key on every build, so the app can update itself in place.
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
