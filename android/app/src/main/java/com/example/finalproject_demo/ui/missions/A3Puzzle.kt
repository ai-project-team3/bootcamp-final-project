package com.example.finalproject_demo.ui.missions

import androidx.compose.animation.core.Animatable
import com.example.finalproject_demo.demo.heroImageName
import com.example.finalproject_demo.demo.storyHeroArt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.demo.mission2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.*

/** A3 그림 퍼즐 — mission slot 2 for frames A · G (moved from ui/Book.kt as is, 10-03 · 맞춤미션 설계 §7-1 1-a) */
/**
 * 미션 2 — **4등분 퍼즐** (미션 구상 A3).
 *
 * 아이가 맞추는 그림은 **아이 이야기로 만든 그 배경**이다. 새 그림을 가져오지 않는다 —
 * *"자기 이야기를 손으로 완성한다"* 가 이 미션의 뜻이다. 다 맞추면 그림이 살아 움직인다.
 *
 * 실패가 없다 — 틀린 자리에 놓으면 **말없이 제자리로 돌아간다.** 틀렸다는 표시도 소리도 없다.
 * 미션 1을 도움받아 끝낸 아이(`m1Result == "helped"`)에게는 **두 조각**만 준다 (§2 · 3~4세 완화).
 */
@Composable
internal fun PuzzleMission(d: Director, done: Boolean) {
    val view = LocalView.current   // 효과음과 같이 진동 (Sfx · 09-25)
    val s = d.s
    val density = LocalDensity.current.density
    val pic = assetBitmap(s.bgName)
    // 그림이 없으면 퍼즐을 낼 수 없다 — 건네주기로 물러난다 (없는 것을 만들어 내지 않는다)
    if (pic == null) {
        GiveMission(d, done, Art.HeroArt(s.heroAttr ?: HeroAttr()), "hand")
        return
    }
    val cols = if (s.m1Result == "helped") 2 else 2
    val rows = if (s.m1Result == "helped") 1 else 2
    val count = cols * rows

    val placed = remember { mutableStateListOf(*Array(count) { done }) }
    val drag = remember { List(count) { Animatable(Offset.Zero, Offset.VectorConverter) } }
    val allIn = done || placed.all { it }
    val puffs = rememberParticleField()
    val scope = rememberCoroutineScope()
    // 다른 미션과 같은 신호 — 반짝 한 번 · 완료 장면을 보인 뒤 보낸다 (#260)
    MissionDoneSignal(d, allIn, done, "미션2")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat()
        val hpx = constraints.maxHeight.toFloat()
        // 판 — 화면 오른쪽에 놓는다. 왼쪽 아래는 조각을 늘어놓는 자리다
        val boardH = hpx * 0.44f
        val boardW = boardH * (cols.toFloat() / rows)
        val boardX = wpx * 0.60f
        val boardY = hpx * 0.20f
        val pw = boardW / cols
        val ph = boardH / rows

        fun slot(i: Int) = Offset(boardX + (i % cols) * pw, boardY + (i / cols) * ph)
        // 흩어 놓는 자리 — 왼쪽 아래에 **겹치지 않게** 나란히. 겹쳐 두면 아이가 집을 수가 없다
        // 왼쪽 끝은 ◀ 버튼(64dp) 자리 — 조각이 그 밑에 깔리지 않게 비워 둔다 (09-29)
        val trayX = maxOf(wpx * 0.05f, 96f * density)
        fun home(i: Int) = Offset(trayX + i * (pw * 1.06f), hpx * 0.56f)

        // 맞출 자리 — **완성된 그림을 흐리게** 깔아 어디에 뭘 놓는지 보여 준다.
        // 실패 없는 설계의 절반은 안내다 — 어디에 놓을지 모르면 그건 어려운 게 아니라 막막한 것이다
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                pic,
                dstOffset = IntOffset(boardX.roundToInt(), boardY.roundToInt()),
                dstSize = IntSize(boardW.roundToInt(), boardH.roundToInt()),
                alpha = 0.28f,
            )
            for (i in 0 until count) {
                val p = slot(i)
                drawRect(Color.White.copy(alpha = 0.75f), topLeft = p, size = Size(pw, ph), style = Stroke(4f))
            }
        }

        for (i in 0 until count) {
            val target = slot(i)
            val start = home(i)
            val at = if (placed[i]) target else start + drag[i].value
            Box(
                Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size((pw / density).dp, (ph / density).dp)
                    // 만질 수 있는 것에는 두꺼운 테두리 (09-27 · Interactive.kt) — 맞춘 조각은 이제 못 만지니 끈다
                    .touchOutline(!placed[i] && !done)
                    .pointerInput(placed[i], done) {
                        if (placed[i] || done) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                val now = start + drag[i].value
                                val near = kotlin.math.hypot(now.x - target.x, now.y - target.y) < pw * 0.45f
                                if (near) {
                                    placed[i] = true
                                    Sfx.play(Sound.THUD, 0L, view = view)
                                    puffs.burst(target.x + pw / 2, target.y + ph / 2, wpx * 0.018f, 10)
                                } else {
                                    // 틀렸다고 말하지 않는다 — 조용히 제자리로
                                    scope.launch {
                                        drag[i].animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = 260f))
                                    }
                                }
                            },
                        ) { change, d2 ->
                            scope.launch { drag[i].snapTo(drag[i].value + d2) }
                            change.consume()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // 배경 그림에서 이 조각에 해당하는 네모만 오려 그린다
                    val sx = (pic.width.toFloat() / cols * (i % cols)).roundToInt()
                    val sy = (pic.height.toFloat() / rows * (i / cols)).roundToInt()
                    drawImage(
                        pic,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(pic.width / cols, pic.height / rows),
                        dstOffset = IntOffset(0, 0),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                    // 오려낸 흰 테두리 — 아이 그림과 같은 결 (⭐27)
                    drawRect(Color.White.copy(alpha = 0.9f), style = Stroke(6f))
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            puffs.tick
            puffs.draw(this)
        }
    }
}
