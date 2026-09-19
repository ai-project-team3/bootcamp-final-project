plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.finalproject_demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.finalproject_demo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // ONNX Runtime (via android-vad) ships 72MB of native libs across four ABIs.
    // Keep the two we actually run on — arm64 phones and the x86_64 emulator.
    // No 32-bit target exists: Play has required 64-bit since 2019.
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        // android-vad:silero is compiled with Kotlin 2.2; we are on 2.0.21. We only
        // touch its builder and three enums, so read the newer metadata anyway.
        // Drop this together with the stdlib force when the project moves to 2.2.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
    }
}

// android-vad:silero asks for kotlin-stdlib 2.2.0. Our compiler is 2.0.21 and
// crashes reading the newer metadata, on files that never touch VAD. Pin the
// stdlib to ours; drop this the day the project moves to Kotlin 2.2.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.vad.silero)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
