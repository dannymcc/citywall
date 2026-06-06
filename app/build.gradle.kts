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
        // versionCode auto-increments from the CI run number so each published APK is
        // a higher version and installs as an update. Falls back to 1 for local builds.
        versionCode = (System.getenv("CITYWALL_VERSION_CODE") ?: "1").toIntOrNull() ?: 1
        versionName = System.getenv("CITYWALL_VERSION_NAME") ?: "1.0"
    }

    // Committed, non-secret debug-grade key. The point is a STABLE signature across
    // builds — without it, each CI run mints a fresh debug key and APKs refuse to
    // install over one another. A proper release key / Play App Signing comes later.
    signingConfigs {
        getByName("debug") {
            storeFile = file("citywall.keystore")
            storePassword = "citywall"
            keyAlias = "citywall"
            keyPassword = "citywall"
            storeType = "PKCS12"
        }
        create("release") {
            storeFile = file("citywall.keystore")
            storePassword = "citywall"
            keyAlias = "citywall"
            keyPassword = "citywall"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Same key as debug, so a release APK installs as an update over the
            // earlier debug builds (v0.1.1+) with no uninstall.
            signingConfig = signingConfigs.getByName("release")
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
