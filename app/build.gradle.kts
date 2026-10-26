import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Public mobile API origin. The ingestion service does not serve mobile routes.
// Override in local.properties with
// leaktrace.apiBaseUrl=https://... . It is a public URL, not a secret; never put secrets here.
val apiBaseUrl: String = Properties().run {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    getProperty("leaktrace.apiBaseUrl", "https://leaktrace-mobile.onrender.com").trim().trimEnd('/')
}
val apiOrigin = URI(apiBaseUrl)
require(apiOrigin.scheme == "https" && !apiOrigin.host.isNullOrBlank() &&
    apiOrigin.rawUserInfo == null && apiOrigin.rawQuery == null && apiOrigin.rawFragment == null &&
    apiOrigin.rawPath.isNullOrEmpty()) { "leaktrace.apiBaseUrl must be an HTTPS origin without credentials, path, query or fragment" }

android {
    namespace = "com.codecrafters.leaktrace"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.codecrafters.leaktrace"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug { buildConfigField("boolean", "LOCAL_SMS_INBOX", "true") }
        release {
            buildConfigField("boolean", "LOCAL_SMS_INBOX", "false")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // Offline, bundled-model transcription. Audio never leaves the device.
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
