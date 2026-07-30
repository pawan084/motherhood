import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aira.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aira.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Aira backend base URL. Defaults to 10.0.2.2 (the host machine's
        // loopback as seen from the Android emulator). Override per build with
        // `-PairaApiBase=https://api.aira.app` or a line in gradle.properties —
        // the Android equivalent of an .env file.
        val airaApiBase = (project.findProperty("airaApiBase") as String?)
            ?: "http://10.0.2.2:8000"
        buildConfigField("String", "AIRA_API_BASE", "\"$airaApiBase\"")

        // The backend's coarse edge gate (security.APP_SHARED_SECRET). When the
        // server has one configured — which app.py REQUIRES in production —
        // every request must carry it as `X-App-Token` or it 401s before auth.
        // Blank by default so a zero-config dev backend still works; set it with
        // `-PairaAppToken=...` or in gradle.properties (which is gitignored for
        // release use — never commit a production token).
        val airaAppToken = (project.findProperty("airaAppToken") as String?) ?: ""
        buildConfigField("String", "AIRA_APP_TOKEN", "\"$airaAppToken\"")

        // Optional application-id suffix so a dev build can install ALONGSIDE an
        // existing install (e.g. `-PappIdSuffix=.dev` -> com.aira.companion.dev).
        // Default is empty, so normal builds are unchanged.
        (project.findProperty("appIdSuffix") as String?)?.takeIf { it.isNotBlank() }
            ?.let { applicationIdSuffix = it }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Kotlin 2.2 removed assigning `jvmTarget` as a String inside `kotlinOptions`
// (it is a hard error on the 2.3.21 pinned in the root build file, so the module
// did not configure at all). The compilerOptions DSL is the replacement.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    // Backports the Android 12 splash screen to minSdk 26, so first launch shows
    // the brand rather than a blank window while the cached session resolves.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Reminder delivery. A reminder that never fires is a note, and this app
    // called them reminders on four screens while delivering nothing.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is stubbed in unit tests, so JSONObject would throw
    // "not mocked". This puts a real implementation on the unit-test classpath.
    // Note it is the reference implementation, which is NOT byte-identical to
    // Android's on edge cases (optString over a JSON null differs) — the
    // definitive check for that one is on-device.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
