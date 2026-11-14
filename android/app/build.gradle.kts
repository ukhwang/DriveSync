import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Supabase connection comes from local.properties or env (never committed). The anon key is
// public by design (RLS protects data); the LLM key lives only in Edge Function secrets.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun config(name: String): String = localProps.getProperty(name) ?: System.getenv(name) ?: ""

// Release keystore lives outside the repository (git-ignored, e.g. /tmp or CI runner temp).
val releaseKeystore = config("DRIVESYNC_KEYSTORE_PATH")
    .takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.drivesync.app"
    compileSdk = 35

    // AAOS car APIs (CarUxRestrictionsManager, CarPropertyManager) ship as an SDK optional library.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.drivesync.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "DEMO_EMAIL", "\"${config("DRIVESYNC_DEMO_EMAIL")}\"")
        buildConfigField("String", "DEMO_PASSWORD", "\"${config("DRIVESYNC_DEMO_PASSWORD")}\"")
        // Max length of one voice memo in seconds (default 2 minutes). Ends earlier on silence.
        buildConfigField("int", "MEMO_MAX_SECONDS", config("DRIVESYNC_MEMO_MAX_SECONDS").ifEmpty { "120" })
        // true = real CarUxRestrictions/CarPropertyManager; false = scripted simulator (badge shown).
        buildConfigField("boolean", "USE_CAR_API", config("DRIVESYNC_USE_CAR_API").ifEmpty { "false" })
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release signing is never committed. Point DRIVESYNC_KEYSTORE_PATH (local.properties or env)
    // at a keystore outside the repository; CI generates an ephemeral one per build.
    // When it is unset the release build simply stays unsigned, so normal CI/debug work is unaffected.
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = config("DRIVESYNC_KEYSTORE_PASSWORD")
                keyAlias = config("DRIVESYNC_KEY_ALIAS").ifEmpty { "drivesync" }
                keyPassword = config("DRIVESYNC_KEY_PASSWORD").ifEmpty { config("DRIVESYNC_KEYSTORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:vehicle"))
    implementation(project(":core:safety"))
    implementation(project(":core:session"))
    implementation(project(":core:action"))
    implementation(project(":core:ai"))
    implementation(project(":core:data"))
    implementation(project(":core:voice"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
