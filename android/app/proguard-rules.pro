# 난독화(R8) 규칙 — 2026-09-23
#
# 릴리스에서 `isMinifyEnabled = true` 를 켜면서 넣었다. 켜는 이유는 둘이다:
#   - 번들 크기를 줄인다 (그림이 많아 켜기 전 72MB 였다)
#   - 쓰지 않는 코드가 떨어져 나간다 — 릴리스 매니페스트에서 뺀 「기기 점검」 화면도 여기서 같이 사라진다
#
# ⚠️ **출시 직전에 켜면 안 된다.** R8 은 「안 쓰는 것처럼 보이는 것」을 지우는데,
#    리플렉션이나 네이티브(JNI)에서 이름으로 찾는 것은 그렇게 보인다.
#    그래서 미리 켜 두고 한 번 돌려 본다 — 출시 전날 켰다가 앱이 죽는 것을 막는다.

# ── ONNX Runtime (android-vad:silero) ─────────────────────────
# 네이티브에서 자바 클래스를 **이름으로** 찾는다. 지우면 「기기 점검」의 음성 감지가 죽는다.
# 지금은 디버그 빌드에서만 쓰지만, 제품에 붙으면 이 규칙이 그대로 필요하다.
-keep class ai.onnxruntime.** { *; }
-keep class com.konovalov.vad.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.konovalov.vad.**

# ── 코루틴 ────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── 줄 정보는 남긴다 ──────────────────────────────────────────
# 오류 보고에서 어느 줄인지 알 수 있어야 한다. 파일 이름은 가린다
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── 보호자 로그인 SDK (10-05 · net/SocialLogin.kt) ──────────────────────
# Kakao: models are read by Gson through reflection, API interfaces by Retrofit (Kakao's own guide).
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep class * extends com.google.gson.TypeAdapter
-keep interface com.kakao.sdk.**.*Api
# Credential Manager finds the Play Services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
