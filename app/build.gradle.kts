plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.dmcc.citywall"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.dmcc.citywall"
        // 26 (Android 8.0) covers the large majority of active devices and is the
        // floor where adaptive icons work natively. Legacy Geocoder/location/window
        // APIs are version-guarded in code (CityResolver, WallpaperWorker.screenSize).
        minSdk = 26
        targetSdk = 35
        // Our CI sets these from the run number / tag. F-Droid's build server sets no
        // env, so the fallbacks must be the real current version (bump per release).
        versionCode = (System.getenv("CITYWALL_VERSION_CODE") ?: "30200").toIntOrNull() ?: 30200
        versionName = System.getenv("CITYWALL_VERSION_NAME") ?: "0.3.2"
    }

    // Release signing key + password come from CI secrets (decoded to citywall.keystore
    // at build time), never from the repo. If absent (e.g. a local build without the
    // key), release falls back to the default debug signing so the build still works.
    val keystoreFile = file("citywall.keystore")
    val keystorePassword: String? = System.getenv("CITYWALL_KEYSTORE_PASSWORD")
    val hasReleaseKey = keystoreFile.exists() && keystoreFile.length() > 0L && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "citywall"
                keyPassword = keystorePassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose (Material 3). BOM keeps the artifact versions aligned.
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
