plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

// The release key is not in the repository. CI restores it from repository secrets and passes it
// through these variables; for a signed local build, set the same ones. Without them the release
// APK is left unsigned.
val releaseKeystore: String? = System.getenv("KEYSTORE_FILE")
val releaseKeyPassword: String? = System.getenv("KEYSTORE_PASSWORD")
val hasReleaseKey = releaseKeyPassword != null && releaseKeystore?.let { file(it).exists() } == true

android {
    namespace = "com.mangapill.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mangapill.app"
        minSdk = 26
        targetSdk = 34
        // CI passes -PversionCode / -PversionName (derived from the release tag) so every release upgrades the last.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Sign-in and sync (the Supabase project shared with AniStream and CineStream). Both values
        // are public by design; without them the account section stays hidden.
        buildConfigField("String", "SUPABASE_URL", "\"${System.getenv("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${System.getenv("SUPABASE_ANON_KEY") ?: ""}\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Every release is signed with the same key, so each new APK installs over the previous one.
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeyPassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "mangapill"
                keyPassword = System.getenv("KEY_PASSWORD") ?: releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    testOptions {
        // android.* calls that are not mocked return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking & HTML Scraping
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.jsoup)

    // Image Loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM unit tests (parsers run on plain Jsoup/JSON, no device needed).
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in unit tests; the real library stands in for it.
    testImplementation("org.json:json:20240303")
}
