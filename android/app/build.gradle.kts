plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
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

        // ONNX Runtime (via android-vad) ships 72MB of native libs across four ABIs.
        // Keep the two we actually run on — arm64 phones and the x86_64 emulator.
        // No 32-bit target exists: Play has required 64-bit since 2019.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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

    // 화면을 **에뮬레이터 없이** PNG로 그려 검사한다 (9/21).
    // 이 기기에서는 에뮬레이터를 띄우면 몇 분 안에 기계가 얼어 강제 재부팅하게 된다 (트러블슈팅 6-12).
    // Robolectric이 안드로이드 화면을 JVM 안에서 만들고, Roborazzi가 그것을 그림으로 떨군다.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // 기준 그림을 `app/screens` 에 두고 **검사할 때마다 대조**한다.
                //   ./gradlew recordRoborazziDebug  → 기준 그림을 새로 찍는다 (화면을 일부러 바꿨을 때)
                //   ./gradlew test                  → 기준과 달라지면 실패하고 build/screens 에 차이를 남긴다
                it.systemProperty("roborazzi.test.verify", "true")
            }
        }
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
    implementation(libs.vad.silero)   // 기기 점검 화면 전용
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
