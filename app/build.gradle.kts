plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mvscrapper.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mvscrapper.downloader"
        minSdk = 26
        targetSdk = 35
        // Release builds get these from the release workflow: the git tag (v1.2.3 -> 1.2.3)
        // and the workflow run number, which only goes up, so Android and Obtainium accept updates.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    // One key for every build, here and on GitHub, so each release installs over the last
    // (and Obtainium can update it). Same approach as Exo Player: the key is committed and its
    // password is public, which is fine for an app you install yourself, but it means anyone
    // could sign a look-alike APK. Only install builds from this repo's releases.
    signingConfigs {
        create("shared") {
            storeFile = file("signing.keystore")
            storePassword = "android"
            keyAlias = "mvdownloader"
            keyPassword = "android"
        }
    }

    // One APK per CPU type keeps the download small; the universal APK works on any phone.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // Off for now: the library maps yt-dlp's JSON with Jackson, which needs
            // keep rules before code shrinking is safe.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
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

    packaging {
        // youtubedl-android runs Python, ffmpeg and QuickJS from the native library folder,
        // so the .so files must be extracted on install.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    // Magnet links. MIT licensed. One native library per CPU type, matching the APK splits.
    val lt = "2.1.0-39"
    implementation("org.libtorrent4j:libtorrent4j:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:$lt")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
