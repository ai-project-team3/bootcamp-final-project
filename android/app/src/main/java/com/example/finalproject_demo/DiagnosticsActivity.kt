package com.example.finalproject_demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import java.util.Locale
import kotlin.concurrent.thread

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
            // Which build am I looking at. Two rounds of results were reported
            // against a stale install before this line existed.
            val installed = runCatching {
                packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            }.getOrDefault(0L)
            log("설치  " + if (installed > 0)
                java.text.SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(java.util.Date(installed))
            else "?")
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
                log("① 12 + ② 성공  →  한국어는 되는데 오프라인 팩만 없다")
                log("① 12 + ② 12     →  이 기기는 한국어 인식 자체가 안 된다")
                log("② 가 7 또는 6   →  마이크가 없는 것(에뮬레이터). 실패가 아니다")
                log("")
                log("버튼을 누르고 한 문장씩 말하세요.")
            }
        }

        // Buttons stay put. The log used to push them off-screen, which made a
        // thirty-utterance run a scrolling exercise.
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }

        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("기기 점검", fontSize = 22.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { listen(log, offline = true) }) { Text("① 오프라인 우선") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { listen(log, offline = false) }) { Text("② 온라인 허용") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { vadOnly(log) }) { Text("③ VAD만 — 끊는 속도") }
            Spacer(Modifier.height(8.dp))
            Text("「인식」만 지연 예산에 센다. 발화·전체는 사람이 만든 시간이다", fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.weight(1f).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                lines.forEach { Text(it, fontSize = 14.sp) }
            }
        }
    }

    private fun onDeviceAvailable(): String = try {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(this).let {
            it.destroy(); "예 — createOnDeviceSpeechRecognizer 생성됨"
        }
    } catch (e: Throwable) {
        "아니오 — ${e.javaClass.simpleName}"
    }

    /**
     * VAD alone — no recogniser, so the two never fight over the microphone.
     *
     * This measures the one thing the built-in endpointer got wrong: on 14% of
     * offline runs it held the mic ~4.8s after a one-syllable answer. Silero
     * decides on its own, so the number here is what we could endpoint at.
     *
     * 발화 includes the 300ms silence hold before VAD calls it over.
     */
    private fun vadOnly(log: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log("[VAD] 마이크 권한이 없다")
            return
        }
        log("[VAD] … 듣는 중")
        val frame = FrameSize.FRAME_SIZE_512.value
        thread(name = "vad-diag") {
            var rec: AudioRecord? = null
            var spokeAt = 0L
            var endedAt = 0L
            val started = System.currentTimeMillis()
            try {
                val vad = Vad.builder()
                    .setContext(applicationContext)
                    .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                    .setFrameSize(FrameSize.FRAME_SIZE_512)
                    .setMode(Mode.NORMAL)
                    .setSpeechDurationMs(50)
                    .setSilenceDurationMs(300)
                    .build()
                val min = AudioRecord.getMinBufferSize(
                    16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, frame * 8)
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    runOnUiThread { log("[VAD] 마이크를 열지 못했다") }
                    return@thread
                }
                val buf = ShortArray(frame)
                rec.startRecording()
                while (System.currentTimeMillis() - started < 12_000) {
                    if (rec.read(buf, 0, frame) != frame) continue
                    val speech = vad.isSpeech(buf)
                    if (speech && spokeAt == 0L) spokeAt = System.currentTimeMillis()
                    if (!speech && spokeAt != 0L) {
                        endedAt = System.currentTimeMillis()
                        break
                    }
                }
                vad.close()
            } catch (e: Throwable) {
                runOnUiThread { log("[VAD] 오류  ${e.javaClass.simpleName}  ${e.message ?: ""}") }
                return@thread
            } finally {
                rec?.let { runCatching { it.stop() }; it.release() }
            }
            runOnUiThread {
                when {
                    spokeAt == 0L -> log("[VAD] 말을 못 잡았다 (12초)")
                    endedAt == 0L -> log("[VAD] 말은 잡았는데 끝을 못 잡았다 (12초)")
                    else -> log(
                        "[VAD]   말 시작 ${spokeAt - started}ms" +
                            " · 발화 ${endedAt - spokeAt}ms" +
                            " · 끊기까지 ${endedAt - started}ms"
                    )
                }
            }
        }
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
        // Split the wall clock. Only endOfSpeech -> results is recognition latency;
        // the rest is the user deciding to talk, and the endpointer waiting them out.
        // Measuring the whole span would fail a model for the tester's reaction time.
        var spokeAt = 0L
        var endedAt = 0L
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                log("$tag … 듣는 중")
            }

            override fun onResults(results: Bundle?) {
                val hit = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val now = System.currentTimeMillis()
                log("$tag 결과  \"$hit\"")
                log(
                    "$tag   인식 ${if (endedAt > 0) "${now - endedAt}ms" else "?"}" +
                        " · 발화 ${if (spokeAt > 0 && endedAt > 0) "${endedAt - spokeAt}ms" else "?"}" +
                        " · 전체 ${now - started}ms"
                )
                rec.destroy()
            }

            override fun onError(code: Int) {
                log("$tag 오류  ${errorLabel(code)}")
                rec.destroy()
            }

            override fun onBeginningOfSpeech() { spokeAt = System.currentTimeMillis() }
            override fun onEndOfSpeech() { endedAt = System.currentTimeMillis() }
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
