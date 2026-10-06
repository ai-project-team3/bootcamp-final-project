import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// 보호자 로그인 키 읽기 (10-05 · net/SocialLogin.kt) — android/local.properties 또는 같은 이름의 환경 변수
val loginProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun loginKey(name: String) = (loginProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.example.finalproject_demo"
    compileSdk = 36

    defaultConfig {
        // Play 는 com.example.* 를 받지 않는다. 첫 업로드 뒤에는 영영 못 바꾼다 (09-23 조장)
        applicationId = "kr.clap.otto"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.4-closed"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime (via android-vad) ships 72MB of native libs across four ABIs.
        // Keep the two we actually run on — arm64 phones and the x86_64 emulator.
        // No 32-bit target exists: Play has required 64-bit since 2019.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // 보호자 로그인 키 (10-05 · net/SocialLogin.kt) — 레포에 올리지 않는다. android/local.properties 나
        // 환경 변수에서 읽는다. 비어 있으면 그 로그인 버튼은 「준비 중」(디버그 빌드는 개발용 가짜 로그인)이다.
        // 받는 곳 · 등록할 패키지 이름 · 키 해시는 docs/소셜로그인_키_발급.md
        val kakaoKey = loginKey("OTTO_KAKAO_APP_KEY")
        buildConfigField("String", "KAKAO_APP_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "NAVER_CLIENT_ID", "\"${loginKey("OTTO_NAVER_CLIENT_ID")}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"${loginKey("OTTO_NAVER_CLIENT_SECRET")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${loginKey("OTTO_GOOGLE_WEB_CLIENT_ID")}\"")
        // 카카오 로그인이 돌아오는 주소(kakao{앱 키}://oauth) — 키가 없으면 아무 앱도 쓰지 않는 이름으로
        manifestPlaceholders["kakaoScheme"] = if (kakaoKey.isEmpty()) "otto-kakao-unset" else "kakao$kakaoKey"
    }

    // 업로드 키는 레포 밖 ~/otto-release/keystore.properties 에서 읽는다. 없으면 서명 없이 빌드된다(팀원 빌드용)
    val keystoreProps = Properties().apply {
        val f = File(System.getProperty("user.home"), "otto-release/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("upload") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Dev builds sit beside the Play tester app instead of replacing it (10-05): the tester app is
        // signed by Play and must stay installed for closed testing, so a debug build with the same id
        // could not be installed without wiping it. Own id, own data, named 「오또 개발」.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            if (keystoreProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("upload")
            // 카카오는 네이티브 앱 키 하나에 패키지 하나라, 스토어(kr.clap.otto)는 키가 따로다 (10-05 · 「오또 스토어」 키).
            // OTTO_KAKAO_APP_KEY_STORE 가 없으면 개발용 키를 그대로 쓴다(그러면 스토어 빌드의 카카오 로그인은 패키지가 달라 실패한다)
            val storeKakao = loginKey("OTTO_KAKAO_APP_KEY_STORE").ifEmpty { loginKey("OTTO_KAKAO_APP_KEY") }
            buildConfigField("String", "KAKAO_APP_KEY", "\"$storeKakao\"")
            manifestPlaceholders["kakaoScheme"] = if (storeKakao.isEmpty()) "otto-kakao-unset" else "kakao$storeKakao"
            // 난독화·축소를 켠다 (9/23). 크기가 줄고, 안 쓰는 코드가 떨어져 나간다.
            // ⚠️ **출시 직전에 켜지 않는다** — R8 이 리플렉션·JNI 로 부르는 것을 «안 쓴다»고 보고 지운다.
            //    미리 켜서 한 번 돌려 봐야 한다. 지킬 것은 `proguard-rules.pro` 에 적었다
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true      // net/Trace: the conversation trace is on in debug builds only · 로그인 키 (net/SocialLogin.kt)
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
                // 기준 그림은 조장 Windows PC에서 찍었다 — 다른 OS(예: CI의 Linux 러너)는 폰트·안티앨리어싱이
                // 달라 항상 "is changed"로 실패한다. CI는 -ProborazziVerify=false 로 대조만 끈다.
                it.systemProperty("roborazzi.test.verify", (findProperty("roborazziVerify") as String?) ?: "true")
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
    implementation(libs.vad.silero)   // 말 끝 감지 — 진짜 마이크(net/Voice.kt) · 기기 점검 화면
    // 보호자 로그인 (net/SocialLogin.kt · 10-05)
    implementation(libs.kakao.user)
    implementation(libs.naver.oauth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
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
