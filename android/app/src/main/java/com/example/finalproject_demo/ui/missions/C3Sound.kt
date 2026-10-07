package com.example.finalproject_demo.ui.missions

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.Coral
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.Ink
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.rememberBlowLevel
import com.example.finalproject_demo.ui.touchOutline
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 목소리 세기가 이만큼 넘으면 「소리 낸다」 표시 — 실기기(10-05)에서 평소 말소리 꼭대기가 0.12~0.15 였다(0.18 은 외쳐야 넘었다) */
internal const val VOICE_ON = 0.12f

/** 소리 덩어리(음절)를 이만큼 내면 다 찬다 — 「삐-뽀-삐-뽀」 한 번 · 「멍-멍」 두 번 · 「슛」 네 번 */
internal const val SOUND_BEATS = 4

/** 소리 덩어리 사이가 이보다 벌어지면 처음부터 센다 — 「삐뽀삐뽀」 한 번은 1초 안팎(10-05 실기기) */
internal const val BEAT_GAP_MS = 1200L

/** 새 소리 덩어리가 [now] 에 왔을 때 이어진 덩어리 수 — 앞 덩어리와 [BEAT_GAP_MS] 넘게 벌어지면 1 부터 */
internal fun beatStreak(streak: Int, lastAt: Long, now: Long): Int = if (now - lastAt > BEAT_GAP_MS) 1 else streak + 1

/** 한 번 누를 때 차는 양 — 세 번이면 다 찬다(탭 길) */
internal const val SOUND_TAP = 0.34f

/**
 * C3 소리 흉내 — 미션 자리 1 (`docs/맞춤미션_설계.md` §4 ★C3 · #101 둘째 순서).
 *
 * 아이가 이야기에서 말한 소리(삐뽀삐뽀 · 부릉부릉 · 어흥 · 슛 …)를 **크게 따라 하면** 그것이 움직인다. 발달 목표는 발성 · 의성어.
 * - 마이크로 **끊어 말한 소리 덩어리(음절) 수**를 센다(`VoiceOnsets` · 무슨 말인지는 안 본다) — 「삐-뽀-삐-뽀」 한 번이면 차고,
 *   길게 이어지는 「아아아아」는 한 칸뿐이다(10-05 실기기 · 사용자 결정). 발음이 서툴러도 리듬이 맞으면 된다
 * - **탭 길(원칙 6)** — 소품을 세 번 톡톡 누르면 소리 말이 뜨며 똑같이 찬다. 마이크가 없어도 끝난다
 * - 8초 진전이 없으면 손이 소품을 눌러 보인다 · 듣는 중 표시는 화면 아래(#98 겹침)
 * - 소품 그림 `prop_firetruck` · `prop_car` · `prop_lion` · `prop_puppy`(10-04 · `gen_room.py` 파이프라인) · 기차는 있던 `train`
 */
@Composable
internal fun SoundMission(d: Director, done: Boolean, heroArt: Art, prop: SoundProp) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var fill by remember { mutableFloatStateOf(if (done) 1f else 0f) }
    var pops by remember { mutableIntStateOf(0) }
    val finished = done || fill >= 1f
    // 크기만 보면 「아아아아」도 찼다(10-05 실기기) — 끊어 말한 소리 덩어리 수로 채운다(사용자 결정)
    val beats = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // 스피커가 울리는 동안(오또 낭독 · 효과음)은 크기 0 · 덩어리도 세지 않는다 — 낭독 음절을 박자로 셌다(#258)
    val voice = rememberBlowLevel(!finished, beats)
    val speaking by com.example.finalproject_demo.net.Voice.playing.collectAsState()
    val micOn = remember {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val idle = rememberIdleHint(fill, finished)
    // 15초 그대로면 오또가 반을 채워 준다(같이 「삐-뽀」) — 나머지 반은 아이가 (#260)
    MissionHelp(d, fill, finished) { if (fill < 0.5f) { fill = 0.5f; pops++ } }
    MissionDoneSignal(d, finished, done, "미션1")
    val bounce = remember { Animatable(1f) }
    fun nudge() { scope.launch { bounce.snapTo(1.12f); bounce.animateTo(1f, spring(dampingRatio = 0.35f)) } }

    // 소리 덩어리가 **이어서** 나와야 찬다 — 덩어리 사이가 [BEAT_GAP_MS] 넘게 벌어지면 처음부터.
    // 「삐-뽀-삐-뽀」는 1초 안에 넷이 몰리고, 방 안 말소리 · 소음은 띄엄띄엄 들어온다 — 10초 동안 띄엄띄엄 넷이
    // 잡혀 말하기도 전에 끝났다(10-05 실기기). 누르기(탭 길)로 찬 만큼은 그대로 둔다
    var streak by remember { mutableIntStateOf(0) }
    var lastBeat by remember { mutableLongStateOf(0L) }
    LaunchedEffect(beats.intValue) {
        if (beats.intValue == 0 || done || fill >= 1f) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val before = streak
        streak = beatStreak(streak, lastBeat, now)
        lastBeat = now
        fill = minOf(1f, maxOf(fill - before.toFloat() / SOUND_BEATS, 0f) + streak.toFloat() / SOUND_BEATS)
        pops++; nudge()
    }
    // 끝 반짝임은 MissionDoneSignal 하나만 — 두 번 울렸다 (#105 리뷰)
    // 다 차면 소품이 제 할 일을 한다 — 옆모습인 소방차는 오른쪽으로 달려 나가고, 정면 그림(자동차 · 기차)과
    // 동물 · 공은 제자리에서 통통 뛴다(정면 그림이 옆으로 미끄러지면 어색하다)
    val go by animateFloatAsState(if (finished) 1f else 0f, tween(1200, easing = FastOutSlowInEasing), label = "go")
    val vehicle = prop == SoundProp.SIREN

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        // 소품은 가운데 조금 위 — 아래에는 채움 막대 · 마이크 표시 · 책 문장 띠가 온다
        val size = 0.17f * wpx
        val cx = 0.56f * wpx; val cy = 0.44f * hpx
        Stand(0.18f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        val dx = if (vehicle) go * wpx * 0.5f else 0f
        val dy = if (vehicle) 0f else -go * hpx * 0.08f
        Box(
            Modifier
                .offset { IntOffset((cx - size / 2 + dx).roundToInt(), (cy - size / 2 + dy).roundToInt()) }
                .size((size / density).dp)
                .scale(bounce.value)
                .alpha(if (vehicle) 1f - go * 0.6f else 1f)
                .pointerInput(finished) {
                    if (finished) return@pointerInput
                    detectTapGestures {
                        fill = minOf(1f, fill + SOUND_TAP); pops++; nudge()
                        // 누를 때마다 톡 — 완료 반짝은 MissionDoneSignal 한 번만 (#260 효과음 규칙)
                        Sfx.play(Sound.POP, minGapMs = 120L, view = view)
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().touchOutline(!finished)) { ArtView(Art.Img(prop.art, Art.Emoji(prop.emoji)), Modifier.fillMaxSize()) }
        }

        // 따라 할 소리 — 크게 띄운다. 소리를 낼 때마다 한 번씩 톡 커진다
        val beat by animateFloatAsState(if (pops % 2 == 0) 1f else 1.15f, spring(dampingRatio = 0.4f), label = "beat")
        Text(
            "「${prop.sound}!」",
            fontSize = 34.sp, color = Coral, fontWeight = FontWeight.Bold,
            modifier = Modifier
                // 소품 오른쪽 옆 — 위에 두면 책 위쪽의 도구 줄 · 안내와 겹쳐 잘렸다(10-05 실기기)
                .offset { IntOffset((cx + size * 0.6f).roundToInt(), (cy - size * 0.35f).roundToInt()) }
                .scale(beat)
                .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        )

        // 얼마나 찼나 — 펠트 막대(점수가 아니라 「조금만 더」를 보이는 것)
        Box(
            Modifier
                .offset { IntOffset((cx - 110.dp.toPx()).roundToInt(), (cy + size / 2 + 4.dp.toPx()).roundToInt()) }
                .width(220.dp).height(18.dp)
                .clip(RoundedCornerShape(Radius.Round))
                .background(FeltWhite.copy(alpha = 0.85f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).clip(RoundedCornerShape(Radius.Round)).background(Coral))
        }

        if (idle && !finished) {
            val press by rememberInfiniteTransition(label = "c3hint").animateFloat(
                0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "press",
            )
            Box(
                Modifier
                    .offset { IntOffset((cx - wpx * 0.02f).roundToInt(), (cy + press * hpx * 0.03f).roundToInt()) }
                    .size((wpx * 0.05f / density).dp)
                    .alpha(0.8f),
            ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
        }
        MicListeningTag(micOn && !finished && !speaking, voice > VOICE_ON, "🎤 「${prop.sound}!」 크게 말해 봐! (눌러도 돼)", "${prop.sound}~! 잘한다!")
        if (!micOn && !finished) Text(
            "눌러서 「${prop.sound}!」 해 볼까?", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
                .felt(FeltWhite.copy(alpha = 0.95f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
