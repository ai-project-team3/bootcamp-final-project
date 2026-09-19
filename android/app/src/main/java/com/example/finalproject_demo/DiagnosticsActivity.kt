package com.example.finalproject_demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Device check, not product code. Run it, read the screen, delete it later.
 *
 *   adb shell am start -n com.example.finalproject_demo/.DiagnosticsActivity
 *
 * Answers two things documentation cannot:
 *   1. Does this phone speak Korean offline, and does it sound bearable?
 *   2. Does it recognise Korean offline? That decides server-STT fallback.
 *
 * Two buttons on purpose. ERROR_LANGUAGE_UNAVAILABLE on its own is ambiguous
 * — it can mean "no offline pack" or "no Korean at all". Running the same
 * request with and without EXTRA_PREFER_OFFLINE separates the two.
 *
 * No new dependency: VAD comes in a second pass so a bad artifact cannot
 * take the whole build down with it.
 */
class DiagnosticsActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        setContent { MaterialTheme { Diagnostics() } }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun Diagnostics() {
        val lines = remember { mutableStateListOf<String>() }
        val log: (String) -> Unit = { lines.add(it) }

        LaunchedEffect(Unit) {
            log("기기  ${Build.MANUFACTURER} ${Build.MODEL}")
            log("안드로이드  ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            log("")
            log("── ① 음성 합성 (TextToSpeech) ──")
            tts = TextToSpeech(this@DiagnosticsActivity) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    log("엔진 초기화 실패 — 이 기기에는 TTS 엔진이 없다")
                    return@TextToSpeech
                }
                val t = tts ?: return@TextToSpeech
                val avail = t.isLanguageAvailable(Locale.KOREAN)
                log("한국어 지원  ${languageLabel(avail)}")

                if (avail >= TextToSpeech.LANG_AVAILABLE) {
                    t.language = Locale.KOREAN
                    val v = t.voice
                    log("목소리  ${v?.name ?: "(이름 없음)"}")
                    log("오프라인 동작  ${if (v?.isNetworkConnectionRequired == false) "예" else "아니오 — 네트워크 필요"}")
                    log("품질  ${v?.quality ?: "?"}  (400=아주좋음 · 100=아주나쁨)")
                    log("")
                    log("▶ 지금 들립니다. 아이가 들을 만한지 귀로 판단하세요.")
                    t.speak(
                        "공룡 나라구나! 거기서 누굴 만났어?",
                        TextToSpeech.QUEUE_FLUSH, null, "diag"
                    )
                }

                log("")
                log("── ② 음성 인식 (SpeechRecognizer) ──")
                log("기기 내 인식 가능  ${if (Build.VERSION.SDK_INT >= 31) onDeviceAvailable() else "API 31 미만 — 해당 없음"}")
                log("일반 인식 가능  ${SpeechRecognizer.isRecognitionAvailable(this@DiagnosticsActivity)}")
                log("")
                log("아래 ①과 ②를 차례로 눌러 \"공룡나라 갈래\" 라고 말하세요.")
            }
        }

        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("기기 점검", fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            lines.forEach { Text(it, fontSize = 14.sp) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { listen(log, offline = true) }) { Text("① 오프라인 우선") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { listen(log, offline = false) }) { Text("② 온라인 허용") }
            Spacer(Modifier.height(10.dp))
            Text("① 12 + ② 성공  →  한국어는 되는데 오프라인 팩만 없다", fontSize = 12.sp)
            Text("① 12 + ② 12     →  이 기기는 한국어 인식 자체가 안 된다", fontSize = 12.sp)
            Text("② 가 7 또는 6   →  마이크가 없는 것(에뮬레이터). 실패가 아니다", fontSize = 12.sp)
        }
    }

    private fun onDeviceAvailable(): String = try {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(this).let {
            it.destroy(); "예 — createOnDeviceSpeechRecognizer 생성됨"
        }
    } catch (e: Throwable) {
        "아니오 — ${e.javaClass.simpleName}"
    }

    /** Korean. ERROR_LANGUAGE_UNAVAILABLE with offline=true but not false means the pack is missing. */
    private fun listen(log: (String) -> Unit, offline: Boolean) {
        val tag = if (offline) "[오프라인]" else "[온라인]"
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            if (offline && Build.VERSION.SDK_INT >= 23) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        val started = System.currentTimeMillis()
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                log("$tag … 듣는 중")
            }

            override fun onResults(results: Bundle?) {
                val hit = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                log("$tag 결과  \"$hit\"   (${System.currentTimeMillis() - started}ms)")
                rec.destroy()
            }

            override fun onError(code: Int) {
                log("$tag 오류  ${errorLabel(code)}")
                rec.destroy()
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec.startListening(intent)
    }

    private fun languageLabel(code: Int) = when (code) {
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "2 — 국가·변형까지 지원"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "1 — 국가까지 지원"
        TextToSpeech.LANG_AVAILABLE -> "0 — 언어 지원"
        TextToSpeech.LANG_MISSING_DATA -> "-1 — 데이터 없음 (설정에서 한국어 팩을 받아야 한다)"
        TextToSpeech.LANG_NOT_SUPPORTED -> "-2 — 지원 안 함"
        else -> code.toString()
    }

    private fun errorLabel(code: Int) = when (code) {
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "12 — 한국어를 지원하지만 지금은 못 쓴다 (오프라인 팩 미설치)"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "13 — 한국어 미지원"
        SpeechRecognizer.ERROR_NO_MATCH -> "7 — 못 알아들었다 (연결은 된 것)"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "6 — 말이 없었다"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "9 — 마이크 권한 없음"
        SpeechRecognizer.ERROR_NETWORK -> "2 — 네트워크 오류 (= 오프라인이 아니라는 뜻)"
        SpeechRecognizer.ERROR_CLIENT -> "5 — 클라이언트 오류"
        SpeechRecognizer.ERROR_SERVER -> "4 — 서버 오류"
        else -> code.toString()
    }
}
