// Guards the versioned keystore. It is deliberately not a secret: the key file sits next
// to it in the repository, so the password protects nothing and only has to match.
val BUNDLED_KEYSTORE_PASSWORD = "unoduosign"

// Android refuses to replace an app signed with a different key, and a CI runner that
// generates its own debug key produces a different one on every build — which is exactly
// why updating failed. So the key has to be fixed.
//
// It is versioned under keystore/, in a public repository: anyone can sign an APK that
// would install over this one. Accepted here because the app has no account, no payment
// and no personal data, and because it is the only option that needs no repository
// secret. Setting the UNO_KEYSTORE_* variables takes precedence, so moving the key into
// GitHub secrets later is a configuration change rather than a code change.
val envKeystore = System.getenv("UNO_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let(::file)
    ?.takeIf { it.exists() }
val bundledKeystore = rootProject.file("keystore/uno-duo.jks").takeIf { it.exists() }
val usingEnvKeystore = envKeystore != null

val signingStore = envKeystore ?: bundledKeystore
val signingStorePassword = System.getenv("UNO_KEYSTORE_PASSWORD")
    ?.takeIf { usingEnvKeystore && it.isNotBlank() }
    ?: BUNDLED_KEYSTORE_PASSWORD
val signingAlias = System.getenv("UNO_KEY_ALIAS")
    ?.takeIf { usingEnvKeystore && it.isNotBlank() }
    ?: "unoduo"
val signingKeyPassword = System.getenv("UNO_KEY_PASSWORD")
    ?.takeIf { usingEnvKeystore && it.isNotBlank() }
    ?: signingStorePassword

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

    lint {
        // Gradle only echoes the first failure; the full text report in the job log is
        // what makes the remaining ones fixable without downloading an artifact.
        textReport = true
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
