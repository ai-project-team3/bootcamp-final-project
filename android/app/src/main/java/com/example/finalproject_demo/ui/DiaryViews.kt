package com.example.finalproject_demo.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.BRUSH_PAUSE_MS
import com.example.finalproject_demo.demo.COLOR_PAUSE_MS
import com.example.finalproject_demo.demo.CRAYON_PAUSE
import com.example.finalproject_demo.demo.DiaryTrace
import com.example.finalproject_demo.demo.UndoneStroke
import com.example.finalproject_demo.demo.redoStroke
import com.example.finalproject_demo.demo.undoStroke
import com.example.finalproject_demo.demo.BoardBox
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPage
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryStage
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.DiaryStitch
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.FEEL_LEAD
import com.example.finalproject_demo.demo.PEN_W
import com.example.finalproject_demo.demo.PICTURE_REQUIRED
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.ottoSpots
import com.example.finalproject_demo.demo.readingDiary
import com.example.finalproject_demo.demo.PieceMove
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.boxOf
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.cropFor
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryPlaceBg
import com.example.finalproject_demo.demo.diaryCovers
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.ottoEmoji
import com.example.finalproject_demo.ui.shell.Otto
import com.example.finalproject_demo.ui.shell.Pose
import kotlinx.coroutines.delay
import java.time.LocalDate
import com.example.finalproject_demo.demo.Stroke as DrawStroke

/*
 * 그림일기 화면 — docs/일기모드_UI.html(오늘 이야기 그림 중심 프로토타입)의 D0 · D1 · D3 · D4 · D5 · D6.
 *
 * `StageView` 가 [DiaryStage] 를 여기로 넘긴다(#28). 이 파일과 [DiaryStage] 는 일기 모드(박진웅) 것이라
 * 그림일기 화면을 더해도 `Screen.kt` 를 다시 고치지 않는다.
 *
 * 치수는 프로토타입의 틀(800×360dp)을 따른다. 거기서 1cqw = 틀 폭의 1% = 8dp 다.
 * 여기서는 [cq] = min(폭/100, 높이/45) — 폰 비율이 달라도 넘치지 않게.
 *
 * 대사 칸도 여기서 그린다 — D1 은 그림판을 가리지 않는 작은 말풍선, D5 는 없음, 나머지는 늘 쓰는 칸.
 * (앱 틀의 공용 대사 칸 · 별 막대는 일기 화면에서 비킨다 — `MainActivity`)
 */

private val Paper = Color(0xFFFFFEF8)        // 그림일기 종이
private val PaperLine = Color(0xFFE6D9C2)    // 종이 위 칸 테두리
private val GridLine = Color(0xFFEBD9BD)     // 원고지 줄
private val PenInk = Color(0xFF2F3B63)       // 원고지 글씨
private val Picked = Color(0xFFFFF3D1)       // 고른 날씨 바탕

/** 늘 쓰는 대사 칸(`Narration`)의 윗변 — 칸 120dp 에서 위 여백을 뺀 자리. 그 위에 세우는 것들의 바닥 */
private val BandTop = 112.dp

/** 앱 틀의 🏠 · 🔒 (`KidTopBar`)가 차지하는 폭 — 그 오른쪽부터 글을 둔다 */
private val TopBarEnd = 160.dp

/** 🏠 · 🔒 의 오른쪽 끝(12 + 56 + 4 + 56dp)에 틈을 더한 자리 — 그림판 · 일기 종이는 여기서 시작한다. 전에는 🔒 가 판 위에 얹혔다 (#98) */
private val TopBarRight = 136.dp

/**
 * 🎤 — 일기 화면 어디서나 **오른쪽 아래 같은 자리 · 같은 크기**(다른 일기 화면의 대사 칸 마이크와 같은 96dp · 끝 12dp).
 * 전에는 그림판 말풍선 안에 있어 말이 길고 짧음에 따라 움직였다 (#46 조장 요청 — 그리다가 손을 뻗을 때 늘 같은 자리)
 */
private val DiaryMicSize = 96.dp
private val DiaryMicGap = 12.dp

/** 그림판이 비켜 두는 오른쪽 띠 — 🎤 가 판을 가리지 않게 */
private val DiaryMicRail = DiaryMicSize + DiaryMicGap * 2

/** 그림판 왼쪽 끝 — 크레용 두 줄 자리와 🏠 · 🔒 자리 중 넓은 쪽 */
private fun boardStart(cq: Dp) = maxOf(cq * 12, TopBarRight)

@Composable
fun DiaryStageView(d: Director, stage: DiaryStage) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val cq = minOf(maxWidth / 100f, maxHeight / 45f)
        when (stage) {
            DiaryStart -> DiaryStartView(d, cq)
            is DiaryBoard -> DiaryBoardView(d, stage, cq)
            DiaryAsk -> DiaryAskView(d, cq)
            DiaryStitch -> DiaryStitchView(d, cq)
            is DiaryPaper -> DiaryPaperView(d, stage, cq)
            DiaryGift -> DiaryGiftView(d, cq)
        }
        when (stage) {
            // 말풍선은 그림판 왼쪽 아래 — 크레용 · ↶ ↷ 를 가리지 않게 판이 시작하는 자리부터 (#98)
            is DiaryBoard -> {
                DiaryBubble(d, cq, Modifier.align(Alignment.BottomStart).padding(start = boardStart(cq) - cq * 1.2f))
                DiaryMic(d, Modifier.align(Alignment.BottomEnd))
            }
            // 그림일기 한 장에는 대사 칸이 없다 — 오또가 묻는 동안(제목)만 작은 말풍선과 마이크
            is DiaryPaper -> if (d.s.micEnabled) {
                DiaryBubble(d, cq, Modifier.align(Alignment.BottomCenter))
                DiaryMic(d, Modifier.align(Alignment.BottomEnd))
            }
            else -> MascotBubble(d, Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, bottom = 6.dp))
        }
    }
}

/** 배경 사진 — [soft] 면 흐리게 하고 크림 막을 덮는다(그 위의 것이 주인공인 화면) */
@Composable
private fun Backdrop(name: String, soft: Boolean) {
    AssetImage(
        name,
        Modifier.fillMaxSize().then(if (soft) Modifier.blur(10.dp).graphicsLayer { scaleX = 1.06f; scaleY = 1.06f } else Modifier),
        contentScale = ContentScale.Crop,
    )
    if (soft) Box(Modifier.fillMaxSize().background(Wool.copy(alpha = 0.55f)))
}

// ── D0 시작 ─────────────────────────────────────────────────────

/** 방에서 손 흔드는 오또 · 오른쪽에 [그릴래!](크고 반짝) · [그림 없이 말할래] */
@Composable
private fun DiaryStartView(d: Director, cq: Dp) {
    Box(Modifier.fillMaxSize()) {
        Backdrop("bg_home", soft = false)
        // 오또는 아래 대사 칸의 얼굴 하나만 — 방에 서 있는 오또까지 두면 한 화면에 둘이었다 (#98)
        Column(
            Modifier.align(Alignment.TopEnd).padding(end = cq * 8, top = cq * 4.5f),
            verticalArrangement = Arrangement.spacedBy(cq * 2.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoButton(cq) { d.send(Reply.Tapped("draw", "그릴래")) }
            Box(
                Modifier.size(cq * 28, cq * 11)
                    .felt(WoolCream, RoundedCornerShape(cq * 5.5f), lift = 4.dp)
                    .clickable { d.send(Reply.Tapped("skip", "그림 없이")) }
                    .testTag("diary-talk"),
                contentAlignment = Alignment.Center,
            ) { Text("그림 없이 말할래", fontSize = (cq.value * 3.4f).sp, color = InkBrown) }
        }
    }
}

/** [그릴래!] — 코랄 그라데이션 · 겨자 빛 고리가 퍼지고 · 살짝 흔들리고 · 크레용이 끄적인다 */
@Composable
private fun GoButton(cq: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "go")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "halo")
    val scribble by t.animateFloat(-14f, 6f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "scribble")
    val shape = RoundedCornerShape(cq * 5.5f)
    Box(Modifier.size(cq * 28, cq * 11).graphicsLayer { translationY = -bob * cq.toPx() * 0.3f; rotationZ = bob }) {
        // 빛 고리 — 버튼 밖으로 퍼지며 사라진다
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { val k = 0.94f + 0.2f * halo; scaleX = k; scaleY = k * 1.1f; alpha = 0.95f * (1f - halo) }
                .border(cq * 0.6f, FeltMustard, RoundedCornerShape(cq * 6.7f))
        )
        Row(
            Modifier.fillMaxSize()
                .shadow(cq * 1.6f, shape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF9A76), FeltCoral, Color(0xFFD9483A))), shape)
                .border(cq * 0.5f, Color.White.copy(alpha = 0.22f), shape)
                .clickable(onClick = onClick)
                .testTag("diary-draw"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🖍️", fontSize = (cq.value * 4.4f).sp, modifier = Modifier.graphicsLayer { rotationZ = scribble })
            Spacer(Modifier.width(cq))
            Text("그릴래!", fontSize = (cq.value * 3.4f).sp, color = Color.White)
        }
    }
}

// ── D1 화이트보드 ───────────────────────────────────────────────

/** 크레용 10색 — 두 줄로 세워 그림판 왼쪽에 (프로토타입 D1) */
private val DIARY_CRAYONS = listOf(
    Color(0xFFE8604C), Color(0xFFF08A3C), Color(0xFFF3C33C), Color(0xFF7FBF4D), Color(0xFF3F9E6E),
    Color(0xFF3F7BD9), Color(0xFF6C63C9), Color(0xFFD96BA8), Color(0xFF8A5A3C), Color(0xFF3A2A1E),
)

/**
 * 그리는 판 — 화면 거의 전부. 옆 버튼이 없다: 끝내기 · 오또에게 부탁은 말로 한다(오또가 멈춘 틈에 묻는다).
 * 1. 획을 **그을 때마다** `s.drawing` 에 넣는다 — 오또 그림을 고르는 사이 화면이 바뀌어도 그리던 것이 남는다
 * 2. 붓이 [BRUSH_PAUSE_MS] 멈추면 `pause` 를 보낸다 — 오또가 묻는 때. 오또가 묻는 중(마이크가 열림)에는 보내지 않는다
 * 3. 묻는 조각에는 청록 점선 고리, 이름 붙은 조각에는 겨자 고리와 이름표. 오또 그림을 고른 조각은 오또 그림으로
 */
@Composable
private fun DiaryBoardView(d: Director, stage: DiaryBoard, cq: Dp) {
    val s = d.s
    val day = s.diaryDay
    var color by remember { mutableStateOf(DIARY_CRAYONS.first()) }
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    val live = remember { mutableStateListOf<Offset>() }
    var strokes by remember { mutableIntStateOf(0) }
    // 판을 만질 때마다(획 시작 · 획 끝 · 크레용) 붓 멈춤 시계를 다시 건다. 크레용을 바꿨으면 더 오래 기다린다
    var touched by remember { mutableIntStateOf(0) }
    var quietFor by remember { mutableLongStateOf(BRUSH_PAUSE_MS) }
    var downAt by remember { mutableLongStateOf(0L) }
    // ↶ 로 지운 획 — ↷ 로 되살린다. 새로 그으면 비운다
    val redo = remember { mutableStateListOf<UndoneStroke>() }
    // 획 기록(디버그 빌드 · 폰 안에만) — 조각 묶기 기준값을 실제 아이 그림으로 정한다 (DiaryTrace)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(day) { DiaryTrace.open(context, day) }

    // 붓 멈춤 — 마지막으로 만진 뒤로 조용하면 알린다
    LaunchedEffect(touched) {
        if (strokes == 0) return@LaunchedEffect
        delay(quietFor)
        if (live.isNotEmpty()) return@LaunchedEffect          // 아직 긋는 중 — 천천히 긋는 선을 잘라 묻지 않는다
        // 오또가 지켜보는 중에만 — 묻는 중 · 고르는 중 · 아이가 말하는 중(녹음)에는 보내지 않는다
        val label = if (quietFor == COLOR_PAUSE_MS) CRAYON_PAUSE else "붓 멈춤"
        // 지켜보는 중이면 바로 알리고, 아니면(말하는 중 · 묻는 중 · 고르는 중) 남겨 둔다 — 흐름이 돌아오면 받는다
        val now = day.watching && !s.micOn && stage.pick == null
        DiaryTrace.pause(label, delivered = now)
        if (now) { day.pendingPause = null; d.send(Reply.Tapped("pause", label)) } else day.pendingPause = label
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop("bg_home", soft = true)
        // 크레용 — 두 줄 · 왼쪽 위 방 버튼 아래
        Column(Modifier.padding(start = cq * 2.4f, top = cq * 8.4f), verticalArrangement = Arrangement.spacedBy(cq * 0.8f)) {
            DIARY_CRAYONS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(cq * 0.8f)) {
                    row.forEach { c ->
                        val on = c == color
                        Box(
                            Modifier.size(cq * 4.1f)
                                .then(if (on) Modifier.border(cq * 0.45f, FeltMustard, CircleShape).padding(cq * 0.65f) else Modifier)
                                .shadow(cq * 0.4f, CircleShape)
                                .background(c, CircleShape)
                                .border(cq * 0.35f, Color.White.copy(alpha = 0.9f), CircleShape)
                                .clickable { color = c; quietFor = COLOR_PAUSE_MS; touched++; DiaryTrace.crayon(c) }
                                .testTag("crayon-${DIARY_CRAYONS.indexOf(c)}")
                        )
                    }
                }
            }
            // 획 지우기 ↶ · 되살리기 ↷ — 할 것이 없으면 흐리게(누를 수 없음). 그림 고르는 중에는 둘 다 쉰다
            Row(Modifier.padding(top = cq * 0.6f), horizontalArrangement = Arrangement.spacedBy(cq * 0.8f)) {
                ArrowButton(left = true, enabled = s.drawing.isNotEmpty() && stage.pick == null, cq = cq, tag = "stroke-undo") {
                    day.undoStroke(s.drawing)?.let { redo += it; touched++ }
                }
                ArrowButton(left = false, enabled = redo.isNotEmpty() && stage.pick == null, cq = cq, tag = "stroke-redo") {
                    redo.removeLastOrNull()?.let { day.redoStroke(s.drawing, it); touched++ }
                }
            }
        }
        BoxWithConstraints(
            Modifier.padding(start = boardStart(cq), end = DiaryMicRail, top = cq * 1.5f, bottom = cq * 1.5f).fillMaxSize()
                .shadow(cq * 2, RoundedCornerShape(cq * 2.5f))
                .background(Color.White, RoundedCornerShape(cq * 2.5f))
                .clip(RoundedCornerShape(cq * 2.5f))
                .onSizeChanged { box = it; s.drawingAspect = it.width.toFloat() / maxOf(it.height, 1) }
                .testTag("diary-board")
                .pointerInput(color) {
                    detectDragGestures(
                        onDragStart = { p -> live.clear(); live += p; day.penDown = true; downAt = DiaryTrace.now(); touched++ },
                        onDrag = { change, _ -> live += change.position; change.consume() },
                        onDragEnd = {
                            if (live.size >= 2) {
                                val stroke = DrawStroke(color, live.map { Offset(it.x / box.width, it.y / box.height) }, PEN_W)
                                s.drawing += stroke
                                strokes++
                                redo.clear()
                                DiaryTrace.stroke(s.drawing.size - 1, downAt, stroke, s.drawingAspect)
                            }
                            live.clear()
                            day.penDown = false                       // 획을 넣은 뒤에 — 질문이 새 획을 먼저 본다
                            quietFor = BRUSH_PAUSE_MS
                            touched++
                        },
                        onDragCancel = { live.clear(); day.penDown = false; quietFor = BRUSH_PAUSE_MS; touched++ },
                    )
                }
        ) {
            val whole = BoardBox(0f, 0f, 1f, 1f)
            val otto = day.pieces.filter { it.look == PieceLook.OTTO }
            val hidden = otto.flatMap { it.strokes }.toSet()
            Canvas(Modifier.fillMaxSize()) {
                // 배경 획을 먼저 — 나중에 그은 땅 · 하늘이 물체를 덮지 않는다
                val behind = day.pieces.filter { it.role == PieceRole.BACKGROUND }.flatMap { it.strokes }.toSet()
                s.drawing.filter { it !in hidden }.sortedBy { if (it in behind) 0 else 1 }.forEach { drawBoardStroke(it, whole) }
                if (live.size >= 2) {
                    val p = Path().apply { live.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                    drawPath(p, color, style = Stroke(size.width * PEN_W, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            otto.forEach { p -> p.ottoSpots().forEach { b -> OttoLook(p, b, whole, maxWidth.value, maxHeight.value) } }
            PieceRings(day.pieces.toList(), day.askingPiece, cq)
            day.pieces.filter { it.name != null }.forEach { p ->
                val b = boxOf(p.strokes) ?: return@forEach
                // ✨ — 오또 그림이 와 있다. 톡 하면 다시 고른다
                val ready = p.ottoPng != null || p.look == PieceLook.OTTO
                Text(
                    if (ready) "✨ ${p.name}" else p.name!!,
                    fontSize = (cq.value * 1.7f).sp,
                    color = if (ready) Color.White else InkBrown,
                    modifier = Modifier
                        // 고리 바로 위 — 판 맨 위에 그린 조각이면 판 안으로 내려 잘리지 않게
                        .offset(x = (maxWidth.value * b.left).dp, y = ((maxHeight.value * b.top) - cq.value * 4.4f).coerceAtLeast(4f).dp)
                        .background(if (ready) FeltCoral else FeltMustard, RoundedCornerShape(cq * 2))
                        .then(
                            when {
                                // 톡 = ✨ 오또 그림 다시 고르기 · 그냥 이름표는 이름 부르기. 길게 = 이름 고치기(지켜볼 때만 · 10-02 진웅).
                                // 묻는 중에는 답으로 섞이지 않게 지켜볼 때만 받는다 — ✨ 는 전처럼 언제든
                                ready || day.watching -> Modifier.pointerInput(p.id, ready, day.watching) {
                                    detectTapGestures(
                                        onTap = {
                                            if (ready) d.send(Reply.Tapped("look:${p.id}", "오또 그림 보기"))
                                            else d.send(Reply.Tapped("name:${p.id}", "이름 부르기"))
                                        },
                                        onLongPress = { if (day.watching) d.send(Reply.Tapped("rename:${p.id}", "이름 고치기")) },
                                    )
                                }.testTag("tag-${p.id}")
                                else -> Modifier
                            }
                        )
                        .padding(horizontal = cq * 1.2f, vertical = cq * 0.2f),
                )
            }
            if (s.drawing.isEmpty() && live.isEmpty()) {
                Text("오늘 있었던 일을 그려 봐", fontSize = (cq.value * 2.4f).sp, color = InkSoft, modifier = Modifier.align(Alignment.Center))
            }
        }
        stage.pick?.let { id -> day.pieces.firstOrNull { it.id == id }?.let { OttoPick(d, it, cq) } }
    }
}

/** 조각 둘레 고리 — 묻는 조각은 청록 점선이 숨 쉬듯, 이름 붙은 조각은 가는 겨자 선 */
@Composable
private fun PieceRings(pieces: List<DiaryPiece>, asking: Int?, cq: Dp) {
    val t = rememberInfiniteTransition(label = "ring")
    val k by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "k")
    Canvas(Modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val named = p.name != null
            if (!named && p.id != asking) return@forEach
            val b = boxOf(p.strokes) ?: return@forEach
            val w = b.width * size.width
            val h = b.height * size.height
            val pad = maxOf(w, h) * 0.12f + cq.toPx()
            val scale = if (p.id == asking) k else 1f
            val rw = (w + pad * 2) * scale
            val rh = (h + pad * 2) * scale
            val cx = (b.left + b.right) / 2f * size.width
            val cy = (b.top + b.bottom) / 2f * size.height
            val style = if (p.id == asking) Stroke(cq.toPx() * 0.45f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cq.toPx(), cq.toPx() * 0.7f)))
            else Stroke(cq.toPx() * 0.3f)
            drawOval(if (p.id == asking) FeltTeal else FeltMustard.copy(alpha = 0.8f), Offset(cx - rw / 2, cy - rh / 2), Size(rw, rh), style = style)
        }
    }
}

/**
 * 작은 말풍선 (D1) — 그림판 왼쪽 아래에 얹는다. 오또가 말을 마치고 조금 지나면 얼굴만 남기고 접힌다(묻는 말은 안 접는다).
 * 얼굴을 누르면 다시 펼친다. 마이크가 열리면(오또가 물었다) 펼쳐진 채 마이크를 보인다
 */
@Composable
private fun DiaryBubble(d: Director, cq: Dp, modifier: Modifier) {
    val s = d.s
    val text = s.line
    var shown by remember { mutableIntStateOf(text.length) }
    var tucked by remember { mutableStateOf(false) }
    LaunchedEffect(s.lineId) {
        tucked = false
        shown = 0
        while (shown < text.length) { delay(28); shown++ }
        delay(4_000)
        // 묻는 말은 답이 올 때까지 펼쳐 둔다 — 접히면 아이는 오또가 무엇을 기다리는지 모른다(10-01 실기기)
        if (!s.micOn && !text.trimEnd().endsWith("?")) tucked = true
    }
    // 접혀도 마이크는 남는다 — 아이는 아무 때나 먼저 말해도 된다. 말하는 중에는 펼친다
    val open = !tucked || s.micOn
    val state = when {
        s.micOn -> OttoState.LISTEN
        shown < text.length -> OttoState.TALK
        else -> OttoState.IDLE
    }
    Row(
        modifier.padding(start = cq * 1.2f, bottom = cq * 1.2f)
            .widthIn(max = cq * 62)
            .felt(Wool, RoundedCornerShape(cq * 4), lift = 4.dp)
            .border(cq * 0.4f, FeltSky, RoundedCornerShape(cq * 4))
            .clickable { tucked = !tucked }
            .padding(start = cq * 0.6f, top = cq * 0.6f, bottom = cq * 0.6f, end = if (open) cq * 2 else cq * 0.6f)
            .testTag("diary-bubble"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OttoFace(state, Modifier.size(cq * 5.4f), pulse = s.micOn)
        if (open && text.isNotBlank()) {
            Spacer(Modifier.width(cq * 1.2f))
            Text(text.take(shown), fontSize = (cq.value * 2.2f).sp, color = InkBrown, lineHeight = (cq.value * 2.9f).sp, modifier = Modifier.widthIn(max = cq * 52))
        }
    }
}

/** 🎤 — 오른쪽 아래 고정([DiaryMicSize]). 들을 차례에만 보인다 */
@Composable
private fun DiaryMic(d: Director, modifier: Modifier) {
    if (d.s.micEnabled) Box(modifier.padding(end = DiaryMicGap, bottom = DiaryMicGap).testTag("diary-mic")) { MicButton(d, size = DiaryMicSize) }
}

/** 「어떤 게 좋아?」 — 그림판 위에 고르기 칸. 내 그림이 먼저(기본값), 오또 그림이 옆 */
@Composable
private fun OttoPick(d: Director, piece: DiaryPiece, cq: Dp) {
    Column(
        Modifier.fillMaxSize().padding(start = cq * 3, end = cq * 3, top = cq * 5.5f, bottom = cq * 12.5f)
            .shadow(cq * 2.4f, RoundedCornerShape(cq * 3))
            .background(Wool.copy(alpha = 0.98f), RoundedCornerShape(cq * 3))
            .padding(horizontal = cq * 2, vertical = cq * 1.6f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cq * 1.4f),
    ) {
        Text("어떤 게 좋아?", fontSize = (cq.value * 2.8f).sp, color = InkBrown)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(cq * 2.4f)) {
            LookCard("내 그림", cq, { d.send(Reply.Tapped("orig", "내 그림")) }) { ChildPiece(piece, d.s.drawingAspect) }
            LookCard("오또 그림 ✨", cq, { d.send(Reply.Tapped("otto", "오또 그림")) }) { OttoArt(piece, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun LookCard(label: String, cq: Dp, onClick: () -> Unit, art: @Composable () -> Unit) {
    Column(
        Modifier.width(cq * 24).fillMaxHeight()
            .shadow(cq, RoundedCornerShape(cq * 2.4f))
            .background(Color.White, RoundedCornerShape(cq * 2.4f))
            .clickable(onClick = onClick)
            .padding(cq),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) { art() }
        Spacer(Modifier.height(cq * 0.8f))
        Text(label, fontSize = (cq.value * 2.3f).sp, color = InkBrown)
    }
}

/** 아이가 그린 조각 하나 — 그 조각만 칸에 맞게 잘라서 */
@Composable
private fun ChildPiece(piece: DiaryPiece, aspect: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val crop = cropFor(piece.strokes, aspect.takeIf { it > 0f } ?: 1f, ratio = maxWidth / maxHeight)
        Canvas(Modifier.fillMaxSize()) { piece.strokes.forEach { drawBoardStroke(it, crop) } }
    }
}

/** 오또 그림 — 서버가 준 PNG(`/image` redraw), 없으면(대본) 이름에 맞는 그림 글자 */
@Composable
private fun OttoArt(piece: DiaryPiece, modifier: Modifier) {
    val png = piece.ottoPng
    val bmp = remember(png) { png?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    if (bmp != null) Image(bmp, contentDescription = piece.name, modifier = modifier)
    else EmojiView(ottoEmoji(piece.name.orEmpty()), modifier)
}

/**
 * 오또 그림을 조각 자리에 얹는다. 처음 나타날 때 찹쌀떡처럼 출렁인다(「펑」 · 리뷰 48~51).
 * [crop] 은 보이는 창 — 화이트보드면 전체, 그림일기면 잘라 낸 3:1.
 */
@Composable
private fun OttoLook(piece: DiaryPiece, b: BoardBox, crop: BoardBox, wDp: Float, hDp: Float, alpha: Float = 1f) {
    val pop = remember(piece.id, piece.look) { Animatable(0.4f) }
    LaunchedEffect(piece.id, piece.look) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)) }
    // 오또 그림은 정사각형(640²)이다 — 조각이 납작한 선이어도 쪼그라들지 않게 조각의 긴 변만 한 정사각형을 조각 가운데에
    val w = b.width / crop.width * wDp
    val h = b.height / crop.height * hDp
    val side = maxOf(w, h, 24f)
    val x = ((b.left + b.right) / 2f - crop.left) / crop.width * wDp - side / 2f
    val y = ((b.top + b.bottom) / 2f - crop.top) / crop.height * hDp - side / 2f
    Box(
        Modifier.offset(x = x.dp, y = y.dp).size(side.dp).alpha(alpha)
            .graphicsLayer { scaleX = 2f - pop.value; scaleY = pop.value }
            .testTag("otto-look-${piece.id}")
    ) { OttoArt(piece, Modifier.fillMaxSize()) }
}

/** 획 하나를 [crop] 창에 맞춰 그린다 — 흰 테두리 없이 그대로(그림일기 종이 위) */
private fun DrawScope.drawBoardStroke(s: DrawStroke, crop: BoardBox) {
    if (s.pts.size < 2) return
    val p = Path()
    s.pts.forEachIndexed { i, o ->
        val x = (o.x - crop.left) / crop.width * size.width
        val y = (o.y - crop.top) / crop.height * size.height
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    val w = (s.w / crop.width * size.width).coerceIn(2f, size.minDimension * 0.05f)
    drawPath(p, s.color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ── D3 다 그린 뒤 묻기 ──────────────────────────────────────────


/**
 * 다 그린 뒤 빈 칸 묻기 — 위 가운데 별 둘(필수 두 칸), 가운데 아이 그림을 꽂은 카드. 묻는 말은 아래 대사 칸(오또 얼굴)이 맡는다.
 * 오또는 대사 칸 얼굴 하나만 — 엎드린 오또까지 두면 한 화면에 둘이었다 (10-05 진웅 · D0 와 같이).
 * 그림 없는 날의 카드는 아이가 말한 곳의 펠트 그림(아직 모르면 어디라고 말하지 않는 저녁 들판) — 그림일기 한 장과 같다.
 */
@Composable
private fun DiaryAskView(d: Director, cq: Dp) {
    val s = d.s
    // 「이건 뭐 그린 거야?」를 묻는 동안은 그 조각만 꽂는다
    val focus = s.diaryDay.focusPiece
    val pieces = bookPieces(s).let { all -> all.filter { it.id == focus }.ifEmpty { all } }
    Box(Modifier.fillMaxSize()) {
        TwoStars(PICTURE_REQUIRED.count { !s.slots[it].isNullOrBlank() }, cq, Modifier.align(Alignment.TopCenter).padding(top = cq * 2))
        Row(
            Modifier.fillMaxWidth().padding(start = TopBarRight, end = TopBarRight, top = cq * 8.5f).height(cq * 24),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().padding(top = cq)
                        .graphicsLayer { rotationZ = 0.8f }
                        .shadow(cq * 2, RoundedCornerShape(cq * 1.6f))
                        .background(Color.White, RoundedCornerShape(cq * 1.6f))
                        .padding(cq * 1.4f)
                        .testTag("d3-card")
                ) {
                    if (pieces.isEmpty()) {
                        AssetImage(diaryPlaceBg(s.slots["place"]), Modifier.fillMaxSize().testTag("d3-place"), contentScale = ContentScale.Crop)
                    } else {
                        // 그린 부분만 카드 비율로 잘라 꽉 채운다 — 화이트보드의 빈 곳은 버린다
                        val crop = cropFor(pieces.flatMap { it.strokes }, s.drawingAspect.takeIf { it > 0f } ?: 1f, ratio = maxWidth / maxHeight)
                        pieces.forEach { p -> PieceLayer(p, crop, null, 1f, maxWidth.value, maxHeight.value) }
                    }
                }
                Box(Modifier.align(Alignment.TopCenter).size(cq * 2.4f).shadow(2.dp, CircleShape).background(FeltCoral, CircleShape))
            }
        }
    }
}

/** 별 둘 — 필수 두 칸(place · problem)이 찰 때마다 하나씩 켜진다. 털실 막대가 그만큼 찬다 */
@Composable
private fun TwoStars(filled: Int, cq: Dp, modifier: Modifier) {
    Row(
        modifier.shadow(cq * 1.2f, RoundedCornerShape(cq * 4)).background(Color.White, RoundedCornerShape(cq * 4))
            .padding(horizontal = cq * 2.2f, vertical = cq)
            .testTag("d3-stars"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(cq * 1.2f),
    ) {
        Box(Modifier.size(cq * 20, cq * 1.2f).background(WoolCream, RoundedCornerShape(cq))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(filled / 2f).background(FeltMustard, RoundedCornerShape(cq)))
        }
        repeat(2) { i ->
            Text("⭐", fontSize = (cq.value * 2.6f).sp, modifier = Modifier.alpha(if (i < filled) 1f else 0.35f))
        }
    }
}

/** 책에 들어갈 조각 — 묶인 조각이 없으면 화이트보드 그림 한 덩어리 */
private fun bookPieces(s: DemoState): List<DiaryPiece> = s.diaryDay.pieces.toList().ifEmpty {
    if (s.sceneDrawing.isEmpty()) emptyList() else listOf(DiaryPiece(0, s.sceneDrawing.toList()))
}.sortedBy { if (it.role == PieceRole.BACKGROUND) 0 else 1 }       // 배경이 맨 뒤 겹

// ── D4 만드는 중 ────────────────────────────────────────────────

/** 「그림일기를 꿰매는 중…」 — 흐린 아이 그림 위로 코랄 실이 바느질하듯 흐른다 */
@Composable
private fun DiaryStitchView(d: Director, cq: Dp) {
    val pieces = bookPieces(d.s)
    val t = rememberInfiniteTransition(label = "sew")
    val phase by t.animateFloat(0f, 36f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    Box(Modifier.fillMaxSize()) {
        if (pieces.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxSize().blur(8.dp).alpha(0.35f).padding(cq * 4)) {
            val crop = cropFor(pieces.flatMap { it.strokes }, d.s.drawingAspect.takeIf { it > 0f } ?: 1f, ratio = maxWidth / maxHeight)
            pieces.forEach { p -> PieceLayer(p, crop, null, 1f, maxWidth.value, maxHeight.value) }
        }
        Box(Modifier.fillMaxSize().background(Wool.copy(alpha = 0.55f)))
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = cq * 11).testTag("d4-stitch"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(cq * 2),
        ) {
            Canvas(Modifier.size(cq * 34, cq * 6)) {
                val w = size.width; val h = size.height
                val p = Path().apply {
                    moveTo(w * 0.03f, h * 0.5f)
                    cubicTo(w * 0.18f, h * 0.08f, w * 0.32f, h * 0.92f, w * 0.5f, h * 0.5f)
                    cubicTo(w * 0.68f, h * 0.08f, w * 0.82f, h * 0.5f, w * 0.97f, h * 0.5f)
                }
                val u = w / 340f
                drawPath(p, FeltCoral, style = Stroke(3f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 8f * u), -phase * u)))
            }
            Text("그림일기를 꿰매는 중…", fontSize = (cq.value * 3f).sp, color = InkBrown)
        }
    }
}

// ── D5 그림일기 한 쪽 ───────────────────────────────────────────

/** 그림 칸 조각을 톡 누를 때의 반응 — 도구마다 다르게 */
private enum class Tool(val icon: String) { HAND("✋"), HAMMER("🔨"), FEATHER("🪶"), LENS("🔍") }

/**
 * 아이들이 쓰는 한 장짜리 그림일기 양식 — 위 날짜 · 날씨 · 제목, 가운데 그림 칸(3:1), 아래 원고지.
 * 대사 칸이 없다 — 문장은 원고지에 한 글자씩 써진다. 왼쪽 도구 넷, 오른쪽 쪽 점, 아래 모서리 ◀ ▶.
 */
@Composable
private fun DiaryPaperView(d: Director, stage: DiaryPaper, cq: Dp) {
    val s = d.s
    val day = s.diaryDay
    val pages = buildDiaryBook(s.diaryBookInput())
    val page = pages.getOrNull(stage.index) ?: return
    val last = stage.index == pages.lastIndex
    var tool by remember { mutableStateOf(Tool.HAND) }
    var replay by remember(stage.index) { mutableIntStateOf(0) }                   // 글 칸을 누른 수 — 다시 쓰고 다시 움직인다
    var glow by remember(stage.index) { mutableStateOf<Pair<Int, DiaryWeather>?>(null) }   // 날씨를 눌러 반짝일 조각

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 종이 — 가운데. 오른쪽에 쪽 점과 ▶ 자리를 남긴다
        val left = maxOf(cq * 9.5f, TopBarRight)          // 🏠 · 🔒 가 종이 위에 얹히지 않게 (#98)
        val right = cq * 11.5f
        Box(
            Modifier.padding(start = left, end = right, top = cq * 1.6f, bottom = cq * 1.4f).fillMaxSize()
                .shadow(cq * 2, RoundedCornerShape(cq * 1.6f))
                .background(Paper, RoundedCornerShape(cq * 1.6f))
                .testTag("d5-sheet")
        ) {
            // 왼쪽 위는 앱 틀의 🏠 · 🔒 자리 — 머리글을 그만큼 비킨다
            SheetHead(
                d, day.weather, s.title, (s.readingDiary?.by ?: s.slotBy)["title"] == "child", cq,
                Modifier.padding(start = maxOf(cq * 2, TopBarEnd - left), end = cq * 2, top = cq * 0.9f),
                onWeather = { w -> bookPieces(s).lastOrNull { p -> p.name?.let(w::drew) == true }?.let { glow = it.id to w } },
            )
            if (page.kind == DiaryPageKind.PUZZLE) {
                PuzzlePanel(d, page, cq, stage.index)
                return@Box
            }
            PicturePanel(
                d, page, tool, cq,
                Modifier.align(Alignment.TopCenter).padding(top = cq * 6).size(cq * 66, cq * 22),
                replay = replay, glow = glow,
            )
            // 오늘 기분을 고를 쪽이면 원고지가 기분 칸 자리를 비킨다 — 긴 문장이 얼굴 밑에 숨지 않게
            val feel = page.asksFeel
            Manuscript(
                page, cq, replay,
                Modifier.padding(start = cq * 3, end = if (feel) cq * 38 else cq * 3, top = cq * 28.8f, bottom = cq)
                    .clickable { replay++ }.testTag("d5-lines"),
            )
            if (feel) FeelRow(d, cq, Modifier.align(Alignment.TopEnd).padding(end = cq * 3, top = cq * 29.6f))
        }
        // 도구 — 왼쪽 세로. 앱 틀의 방 버튼 아래
        Column(Modifier.padding(start = cq * 1.4f, top = cq * 9), verticalArrangement = Arrangement.spacedBy(cq)) {
            Tool.entries.forEach { t ->
                val on = t == tool
                Box(
                    Modifier.size(cq * 5.4f).graphicsLayer { translationY = if (on) -cq.toPx() * 0.3f else 0f }
                        .shadow(cq, CircleShape)
                        .background(if (on) FeltMustard else Color.White.copy(alpha = 0.95f), CircleShape)
                        .clickable { tool = t }
                        .testTag("tool-${t.name}"),
                    contentAlignment = Alignment.Center,
                ) { Text(t.icon, fontSize = (cq.value * 2.6f).sp) }
            }
        }
        PageDots(stage.index, pages.size, cq, Modifier.align(Alignment.TopEnd).padding(end = cq, top = cq * 3))
        NavDot("◀", Color.White, enabled = stage.index > 0, cq = cq, modifier = Modifier.align(Alignment.BottomStart).padding(start = cq * 1.4f, bottom = cq * 3)) {
            d.send(Reply.Tapped("prev", "앞"))
        }
        NavDot(if (last) "📔" else "▶", FeltMustard, enabled = true, cq = cq, modifier = Modifier.align(Alignment.BottomEnd).padding(end = cq * 1.4f, bottom = if (s.micEnabled) DiaryMicSize + DiaryMicGap * 2 else cq * 3).testTag("d5-next")) {
            d.send(Reply.Tapped("next", "다음"))
        }
    }
}

@Composable
private fun NavDot(label: String, bg: Color, enabled: Boolean, cq: Dp, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(cq * 6.4f).alpha(if (enabled) 1f else 0.35f)
            .shadow(cq, CircleShape).background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = (cq.value * 2.6f).sp, color = InkBrown) }
}

/** 쪽 점 — 오른쪽 위 세로. 지금 쪽이 겨자색, 아래에 「1/4」 */
@Composable
private fun PageDots(index: Int, total: Int, cq: Dp, modifier: Modifier) {
    Column(
        modifier.background(InkBrown.copy(alpha = 0.55f), RoundedCornerShape(cq * 3)).padding(horizontal = cq * 0.7f, vertical = cq),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cq * 0.6f),
    ) {
        repeat(total) { i -> Box(Modifier.size(cq * 1.1f).background(if (i == index) FeltMustard else Color.White.copy(alpha = 0.55f), CircleShape)) }
        Text("${index + 1}/$total", fontSize = (cq.value * 1.5f).sp, color = Color.White)
    }
}

private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/** 날짜 · 날씨 · 제목 — 날씨는 그림에서 알아봤거나 아이가 누른 것만 켜진다(지어내지 않는다) */
@Composable
private fun SheetHead(
    d: Director, weather: DiaryWeather?, title: String?, titleSaid: Boolean, cq: Dp, modifier: Modifier,
    onWeather: (DiaryWeather) -> Unit,
) {
    val today = d.s.diaryDay.madeOn ?: LocalDate.now()        // 책장에서 다시 연 일기는 만든 날
    val fs = (cq.value * 1.8f).sp
    Column(modifier) {
        Row(Modifier.height(cq * 4.4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq * 2)) {
            Text("${today.year}년 ${today.monthValue}월 ${today.dayOfMonth}일 ${DAYS[today.dayOfWeek.value - 1]}요일", fontSize = fs, color = InkBrown, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq * 0.5f)) {
                Text("날씨", fontSize = fs, color = InkBrown)
                DiaryWeather.entries.forEach { w ->
                    val on = w == weather
                    Box(
                        Modifier.size(cq * 3.6f).alpha(if (on) 1f else 0.35f)
                            .background(if (on) Picked else Color.Transparent, CircleShape)
                            .border(cq * 0.25f, if (on) FeltMustard else Color.Transparent, CircleShape)
                            .clickable { d.send(Reply.Tapped("wx:${w.name}", w.label)); onWeather(w) }
                            .testTag("wx-${w.name}"),
                        contentAlignment = Alignment.Center,
                    ) { Text(w.emoji, fontSize = (cq.value * 2f).sp) }
                }
            }
            // 제목을 누르면 오또가 「이 일기 제목은 뭐로 할까?」 — 아이가 말한 제목이 아니면 밑줄로 누를 수 있다고 알린다
            Row(Modifier.clickable { d.send(Reply.Tapped("title", "제목")) }.testTag("d5-title"), verticalAlignment = Alignment.CenterVertically) {
                Text("제목: ", fontSize = fs, color = InkBrown)
                Text(
                    title ?: "눌러서 말해 줘", fontSize = fs, maxLines = 1,
                    color = if (titleSaid) InkBrown else InkSoft,
                    textDecoration = if (titleSaid) null else TextDecoration.Underline,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(cq * 0.2f).background(PaperLine))
    }
}

/**
 * 그림 칸 — 아이 그림 조각이 그린 자리 그대로. 문장에 나온 조각은 앞에서 문장대로 움직이고
 * 나머지는 흐리게 뒤에 남는다. 그린 부분만 3:1 로 잘라 맞춘다(찌그러짐 없음).
 * 조각을 짧게 톡 하면 고른 도구대로 반응하고(✋ 통통 · 🔨 납작 · 🪶 간질 · 🔍 크게) 한마디 한다. 끌면 옮겨진다(이 쪽 안에서만).
 * 글 칸을 누르면([replay]) 앞에 나온 조각이 다시 통통, 날씨를 누르면([glow]) 그 날씨를 그린 조각이 반짝인다.
 */
@Composable
private fun PicturePanel(
    d: Director, page: DiaryPage, tool: Tool, cq: Dp, modifier: Modifier,
    replay: Int = 0, glow: Pair<Int, DiaryWeather>? = null,
) {
    val s = d.s
    // 1쪽(「○○을 그렸어요」)은 늘 아이가 그린 그대로 — 오또 그림을 골랐어도 (흐름 §D5 · 10-01 진웅)
    val pieces = bookPieces(s).let { all -> if (page.kind == DiaryPageKind.DRAWING) all.map { it.copy(look = PieceLook.ORIGINAL) } else all }
    val density = LocalDensity.current
    var poke by remember(page) { mutableStateOf<Pair<Int, Int>?>(null) }      // 조각 id · 누른 차례
    var said by remember(page) { mutableStateOf<Pair<Int, String>?>(null) }   // 조각 id · 한마디
    val moved = remember(page) { mutableStateMapOf<Int, Offset>() }           // 조각 id → 끌어 옮긴 거리(px)
    var dragging by remember(page) { mutableStateOf<Int?>(null) }
    LaunchedEffect(said) { if (said != null) { delay(1_500); said = null } }
    LaunchedEffect(glow) { glow?.let { (id, w) -> said = id to "${w.emoji} ${pieces.firstOrNull { it.id == id }?.name.orEmpty()}!" } }
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(cq)).background(Color.White, RoundedCornerShape(cq))
            .border(cq * 0.25f, PaperLine, RoundedCornerShape(cq))
            .testTag("d5-picture")
    ) {
        if (pieces.isEmpty()) {
            // 그림 없이 만든 일기 — 아이가 말한 곳의 펠트 그림(모르는 곳은 어디라고 말하지 않는 저녁 들판). 전에는 ✏️ 하나였다 (#98)
            AssetImage(diaryPlaceBg(s.diaryBookInput().lines["place"]), Modifier.fillMaxSize().testTag("d5-place"), contentScale = ContentScale.Crop)
            return@BoxWithConstraints
        }
        val crop = cropFor(pieces.flatMap { it.strokes }, s.drawingAspect.takeIf { it > 0f } ?: 1f)
        val everyone = page.kind == DiaryPageKind.DRAWING || page.kind == DiaryPageKind.PLACE || page.kind == DiaryPageKind.PUZZLE
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        // 화면 좌표 → 그 자리의 조각 (옮긴 조각은 옮긴 자리로)
        fun hit(at: Offset, w: Float, h: Float): DiaryPiece? = pieces.lastOrNull { p ->
            val o = moved[p.id] ?: Offset.Zero
            val x = crop.left + (at.x - o.x) / w * crop.width
            val y = crop.top + (at.y - o.y) / h * crop.height
            boxOf(p.strokes)?.grow(0.03f)?.let { x in it.left..it.right && y in it.top..it.bottom } == true
        }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(pieces, crop, tool) {
                    detectTapGestures { at ->
                        val p = hit(at, size.width.toFloat(), size.height.toFloat()) ?: return@detectTapGestures
                        poke = p.id to ((poke?.second ?: 0) + 1)
                        said = p.id to reactionWord(tool, p)
                    }
                }
                .pointerInput(pieces, crop) {
                    detectDragGestures(
                        onDragStart = { at -> dragging = hit(at, size.width.toFloat(), size.height.toFloat())?.id },
                        onDrag = { change, amount ->
                            val id = dragging ?: return@detectDragGestures
                            moved[id] = (moved[id] ?: Offset.Zero) + amount
                            change.consume()
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    )
                }
        ) {
            pieces.forEach { p ->
                val backdrop = p.role == PieceRole.BACKGROUND             // 배경은 흐리지도 움직이지도 않고 뒤에 있다
                val front = backdrop || everyone || (p.name != null && p.name in page.cast) || p.id in moved || p.id == glow?.first
                val moves = front && !backdrop && !everyone && p.name !in page.still
                val a = if (front) 1f else 0.25f
                val mine = poke?.takeIf { it.first == p.id }?.second ?: 0
                // 글 칸을 다시 누르면 앞에 나온 조각이 모두 통통 — 누른 조각의 반응이 먼저다
                val (rxTool, nonce) = when {
                    mine > 0 -> tool to mine
                    replay > 0 && front -> Tool.HAND to 10_000 + replay
                    else -> null to 0
                }
                PieceLayer(
                    p, crop, if (!moves) null else if (p.name in page.with) PieceMove.HOP else page.move, a, wDp, hDp, rxTool, nonce,
                    shift = moved[p.id] ?: Offset.Zero, glowing = p.id == glow?.first,
                )
            }
            said?.let { (id, word) ->
                val p = pieces.firstOrNull { it.id == id } ?: return@let
                val b = boxOf(p.strokes) ?: return@let
                val o = moved[id] ?: Offset.Zero
                val x = ((b.left + b.right) / 2f - crop.left) / crop.width * wDp + with(density) { o.x.toDp().value }
                val y = (b.top - crop.top) / crop.height * hDp + with(density) { o.y.toDp().value }
                Text(
                    word, fontSize = (cq.value * 2f).sp, color = InkBrown, maxLines = 1,
                    modifier = Modifier.offset(x = (x - cq.value * 6).dp, y = (y - cq.value * 4).coerceAtLeast(0f).dp)
                        .shadow(cq * 0.4f, RoundedCornerShape(cq * 2)).background(Color.White, RoundedCornerShape(cq * 2))
                        .padding(horizontal = cq * 1.4f, vertical = cq * 0.4f)
                        .testTag("d5-said"),
                )
            }
        }
    }
}

/** 도구로 조각을 누를 때 조각이 하는 한마디 (프로토타입 react) */
private fun reactionWord(tool: Tool, p: DiaryPiece): String = when (tool) {
    Tool.HAND -> p.name?.let { "$it 톡!" } ?: "톡!"
    Tool.HAMMER -> "간지러워!"
    Tool.FEATHER -> "깔깔깔!"
    Tool.LENS -> "${if (p.look == PieceLook.OTTO) "오또랑 같이 그린" else "내가 그린"} ${p.name ?: "그림"}"
}

/** 조각 한 겹 — 움직임은 조각 가운데를 축으로. [poked] 가 바뀔 때마다 [tool] 반응을 한 번 */
@Composable
private fun PieceLayer(
    p: DiaryPiece, crop: BoardBox, move: PieceMove?, alpha: Float, wDp: Float, hDp: Float,
    tool: Tool? = null, poked: Int = 0, shift: Offset = Offset.Zero, glowing: Boolean = false,
) {
    val b = boxOf(p.strokes) ?: return
    val t = rememberInfiniteTransition(label = "piece${p.id}")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (move == PieceMove.TOPPLE) 1400 else 700), RepeatMode.Reverse), label = "k${p.id}")
    val rx = remember(p.id) { Animatable(0f) }
    LaunchedEffect(poked) {
        if (poked == 0) return@LaunchedEffect
        rx.snapTo(0f)
        rx.animateTo(1f, tween(if (tool == Tool.LENS) 900 else 600))
        rx.snapTo(0f)
    }
    val cx = ((b.left + b.right) / 2f - crop.left) / crop.width
    val cy = ((b.top + b.bottom) / 2f - crop.top) / crop.height
    val layer = Modifier.fillMaxSize().alpha(alpha).graphicsLayer {
        transformOrigin = TransformOrigin(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f))
        translationX = shift.x
        translationY = shift.y
        when (move) {
            PieceMove.HOP -> translationY += -k * size.height * 0.08f
            PieceMove.TOPPLE -> rotationZ = k * 22f
            PieceMove.DROOP -> { translationY += k * size.height * 0.03f; scaleY = 1f - k * 0.06f }
            PieceMove.BUILD -> { scaleX = 1f + k * 0.06f; scaleY = 1f + k * 0.06f }
            PieceMove.WALK -> translationX += (k - 0.5f) * size.width * 0.04f
            PieceMove.BOB -> translationY += -k * size.height * 0.02f
            null -> {}
        }
        // 누른 반응 — 0 → 1 동안 한 번 (sin 곡선으로 제자리로 돌아온다)
        val w = kotlin.math.sin(rx.value * Math.PI).toFloat()
        when (tool) {
            Tool.HAND -> translationY -= w * size.height * 0.12f
            Tool.HAMMER -> { scaleX *= 1f + w * 0.15f; scaleY *= 1f - w * 0.4f }
            Tool.FEATHER -> rotationZ += kotlin.math.sin(rx.value * Math.PI * 4).toFloat() * 9f * (1f - rx.value)
            Tool.LENS -> { scaleX *= 1f + w * 0.35f; scaleY *= 1f + w * 0.35f }
            null -> {}
        }
    }
    if (p.look == PieceLook.OTTO) Box(layer) { p.ottoSpots().forEach { spot -> OttoLook(p, spot, crop, wDp, hDp) } }
    else Canvas(layer) {
        // 날씨를 눌러 반짝 — 선 뒤로 겨자빛을 두껍게 한 번 더
        if (glowing) p.strokes.forEach { drawBoardStroke(it.copy(color = FeltMustard.copy(alpha = 0.45f), w = it.w * 3.5f), crop) }
        p.strokes.forEach { drawBoardStroke(it, crop) }
    }
}

/** 원고지 — 문장이 한 글자씩 써진다. 비었다고 쓰는 꼬리와 「오늘은 …」은 연하게. 넘치면 칸을 줄인다 */
@Composable
private fun Manuscript(page: DiaryPage, cq: Dp, replay: Int, modifier: Modifier) {
    val main = page.text
    val soft = listOfNotNull(page.tail, page.closing ?: if (page.asksFeel) "$FEEL_LEAD …" else null).joinToString(" ")
    val chars = (main + if (soft.isEmpty()) "" else " $soft").toList()
    var shown by remember(page, replay) { mutableIntStateOf(0) }
    LaunchedEffect(page, replay) { while (shown < chars.size) { delay(60); shown++ } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 프로토타입 칸 3.3 × 5.2cqw — 글이 넘치면 같은 비율로 줄인다
        var cellW = cq.value * 3.3f
        var cellH = cq.value * 5.2f
        fun fits(): Boolean {
            val cols = (maxWidth.value / cellW).toInt().coerceAtLeast(1)
            val rows = (maxHeight.value / cellH).toInt().coerceAtLeast(1)
            return cols * rows >= chars.size
        }
        while (!fits() && cellW > cq.value * 1.6f) { cellW *= 0.9f; cellH *= 0.9f }
        val cols = (maxWidth.value / cellW).toInt().coerceAtLeast(1)
        val rows = (maxHeight.value / cellH).toInt().coerceAtLeast(1)
        Canvas(Modifier.size((cols * cellW).dp, (rows * cellH).dp)) {
            val lw = cq.toPx() * 0.15f
            for (c in 0..cols) drawLine(GridLine, Offset(c * cellW.dp.toPx(), 0f), Offset(c * cellW.dp.toPx(), size.height), lw)
            for (r in 0..rows) drawLine(GridLine, Offset(0f, r * cellH.dp.toPx()), Offset(size.width, r * cellH.dp.toPx()), lw)
        }
        // 줄 첫 칸에 오는 띄어쓰기는 건너뛴다 — 원고지처럼. 칸마다 (글자, 원래 차례)
        val lines = buildList {
            var row = mutableListOf<Pair<Char, Int>>()
            chars.forEachIndexed { i, ch ->
                if (row.isEmpty() && ch == ' ' && isNotEmpty()) return@forEachIndexed
                row += ch to i
                if (row.size == cols) { add(row); row = mutableListOf() }
            }
            if (row.isNotEmpty()) add(row)
        }
        Column {
            lines.take(rows).forEach { row ->
                Row {
                    row.forEach { (ch, i) ->
                        Box(Modifier.size(cellW.dp, cellH.dp), contentAlignment = Alignment.Center) {
                            if (i < shown && ch != ' ') Text(ch.toString(), fontSize = (cellW * 0.8f).sp, color = if (i >= main.length) InkSoft else PenInk)
                        }
                    }
                }
            }
        }
    }
}

/** 오늘 기분 — 말하지 않았을 때만. 누르면 `by: card` */
@Composable
private fun FeelRow(d: Director, cq: Dp, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "feel")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(cq)) {
        DiaryFeel.entries.forEach { f ->
            Column(
                Modifier.size(cq * 7.4f)
                    .border(cq * (0.3f + 0.3f * glow), FeltCoral.copy(alpha = 0.25f + 0.35f * glow), RoundedCornerShape(cq * 1.6f))
                    .background(Color.White, RoundedCornerShape(cq * 1.6f))
                    .border(cq * 0.3f, PaperLine, RoundedCornerShape(cq * 1.6f))
                    .clickable { d.send(Reply.Tapped("feel:${f.name}", f.line)) }
                    .padding(vertical = cq * 0.4f)
                    .testTag("feel-${f.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(f.emoji, fontSize = (cq.value * 3.2f).sp)
                Text(f.word, fontSize = (cq.value * 1.4f).sp, color = InkBrown, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

// ── D6 끝 ───────────────────────────────────────────────────────

/** 책장 앞에서 — 아이 그림이 표지인 오늘 그림일기 한 권이 톡 나타나고, 아래 [책장에 꽂기] */
@Composable
private fun DiaryGiftView(d: Director, cq: Dp) {
    val s = d.s
    val pieces = bookPieces(s)
    val pop = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    val today = s.diaryDay.madeOn ?: LocalDate.now()        // 그림일기 머리와 같은 날 — 다시 연 일기는 만든 날
    Box(Modifier.fillMaxSize()) {
        Backdrop("bg_shelf", soft = true)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = cq * 3.5f).width(cq * 23)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.9f) * 10f).coerceIn(0f, 1f) }
                .shadow(cq * 2, RoundedCornerShape(cq * 1.2f))
                .background(FeltSky, RoundedCornerShape(cq * 1.2f))
                .padding(start = cq * 1.6f, end = cq * 1.2f, top = cq * 1.2f, bottom = cq * 1.4f)
                .testTag("d6-book"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(cq * 9).background(Color.White)) {
                if (pieces.isNotEmpty()) {
                    val crop = cropFor(pieces.flatMap { it.strokes }, s.drawingAspect.takeIf { it > 0f } ?: 1f, ratio = maxWidth / maxHeight)
                    pieces.forEach { p -> PieceLayer(p, crop, null, 1f, maxWidth.value, maxHeight.value) }
                // 그림 없이 만든 일기 — 책장 표지와 같은 그림(아이가 말한 곳). 전에는 📔 하나였다 (#98)
                } else AssetImage(diaryPlaceBg(s.diaryBookInput().lines["place"]), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(cq * 1.6f))
            Text(s.title ?: "나의 그림일기", fontSize = (cq.value * 1.8f).sp, color = Color.White, maxLines = 1)
            Text("${today.monthValue}월 ${today.dayOfMonth}일", fontSize = (cq.value * 1.2f).sp, color = Color.White.copy(alpha = 0.85f))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = BandTop + cq).size(cq * 26, cq * 9)
                .felt(FeltCoral, RoundedCornerShape(cq * 4.5f), lift = 5.dp)
                .clickable { d.send(Reply.Tapped("shelf", "책장")) }
                .testTag("d6-shelf"),
            contentAlignment = Alignment.Center,
        ) { Text("📚 책장에 꽂기", fontSize = (cq.value * 3f).sp, color = Color.White) }
    }
}

// ── 책장 표지 ────────────────────────────────────────────────────

/**
 * 책장에 꽂힌 그림일기의 표지 — 그날 아이가 그린 조각(오또 그림을 고른 조각은 그 모습)을 흰 종이에.
 * `Shelf.kt` 가 [hasDiaryCover] 로 물어 그림일기 책이면 장소 그림 대신 이것을 그린다
 */
@Composable
fun DiaryShelfCover(s: DemoState, title: String, modifier: Modifier = Modifier) {
    val cover = s.diaryCovers[title] ?: return
    BoxWithConstraints(modifier.background(Color.White).padding(4.dp).testTag("diary-shelf-cover")) {
        val crop = cropFor(cover.pieces.flatMap { it.strokes }, cover.aspect.takeIf { it > 0f } ?: 1f, ratio = maxWidth / maxHeight)
        cover.pieces.forEach { p -> PieceLayer(p, crop, null, 1f, maxWidth.value, maxHeight.value) }
    }
}

// ── D5 🧩 내 그림 맞추기 ─────────────────────────────────────────

/** 퍼즐 조각 수 · 섞인 차례(처음부터 맞게 놓이지 않게) · 원고지 자리에서의 크기 */
private const val PUZZLE_N = com.example.finalproject_demo.demo.PUZZLE_STRIPS
private val PUZZLE_SHUFFLE = listOf(2, 0, 1)
private const val PUZZLE_SMALL = 0.4f

/**
 * 🧩 내 그림 맞추기 (프로토타입 A3) — 그림 칸에 내 그림을 세로로 셋 나눈 흐린 자리, 원고지 자리에 섞인 조각 셋.
 * 끌어다 놓거나 조각을 톡 · 자리를 톡. 틀린 자리면 제자리로 돌아가 「다른 자리에 맞춰 볼까?」(틀렸다고 하지 않는다).
 * 8초 동안 진전이 없으면 다음 조각과 그 자리를 알려 준다. 다 맞추면 그림이 통통 — 점수 · 시간은 남기지 않는다.
 */
@Composable
private fun PuzzlePanel(d: Director, page: DiaryPage, cq: Dp, pageIndex: Int) {
    val s = d.s
    val pieces = bookPieces(s)
    if (pieces.isEmpty()) return
    val density = LocalDensity.current
    val placed = remember(pageIndex) { mutableStateListOf<Int>() }
    var selected by remember(pageIndex) { mutableStateOf<Int?>(null) }
    var hint by remember(pageIndex) { mutableStateOf("🧩 내 그림을 맞춰 볼까?") }
    var helping by remember(pageIndex) { mutableStateOf<Int?>(null) }     // 8초 힌트 — 이 조각과 그 자리
    var progress by remember(pageIndex) { mutableIntStateOf(0) }
    var nudge by remember(pageIndex) { mutableStateOf<Pair<Int, Int>?>(null) }  // 조각 · 차례 — 흔들기
    var done by remember(pageIndex) { mutableStateOf(false) }
    val moved = remember(pageIndex) { mutableStateMapOf<Int, Offset>() }   // 끄는 중인 조각의 거리(px)

    LaunchedEffect(progress, done) {
        if (done) return@LaunchedEffect
        delay(8_000)
        val next = (0 until PUZZLE_N).firstOrNull { it !in placed } ?: return@LaunchedEffect
        helping = next
        nudge = next to ((nudge?.second ?: 0) + 1)
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("d5-puzzle")) {
        val cqPx = with(density) { cq.toPx() }
        val picLeft = (maxWidth - cq * 66) / 2
        val slotW = cq * 22
        val crop = cropFor(pieces.flatMap { it.strokes }, s.drawingAspect.takeIf { it > 0f } ?: 1f)
        fun strip(i: Int) = BoardBox(crop.left + i * crop.width / PUZZLE_N, crop.top, crop.left + (i + 1) * crop.width / PUZZLE_N, crop.bottom)
        fun slotRect(i: Int): Pair<Offset, Float> = Offset(with(density) { (picLeft + slotW * i).toPx() }, 6 * cqPx) to with(density) { slotW.toPx() }
        fun homeOf(i: Int): Offset { val j = PUZZLE_SHUFFLE.indexOf(i); return Offset((10 + j * (22 * PUZZLE_SMALL + 6)) * cqPx, 30 * cqPx) }

        fun place(i: Int) {
            if (i in placed) return
            placed += i; selected = null; helping = null; moved.remove(i); progress++
            if (placed.size < PUZZLE_N) { hint = "🧩 잘했어! 또 맞춰 볼까?"; return }
            hint = "✨ 다 맞췄다!"
            d.event("mission", "kind" to "A3", "result" to "done")      // 점수 · 시간은 남기지 않는다
        }
        fun sendHome(i: Int) {
            moved.remove(i); selected = null
            nudge = i to ((nudge?.second ?: 0) + 1)
            hint = "🧩 다른 자리에 맞춰 볼까?"
        }
        LaunchedEffect(placed.size) { if (placed.size == PUZZLE_N) { delay(600); done = true } }

        if (done) {
            PicturePanel(d, page, Tool.HAND, cq, Modifier.align(Alignment.TopCenter).padding(top = cq * 6).size(cq * 66, cq * 22), replay = 1)
        } else {
            // 자리 셋 — 흐린 내 그림 조각. 조각을 고른 뒤 자리를 톡 하면 맞춘다
            for (i in 0 until PUZZLE_N) {
                Box(
                    Modifier.offset(x = picLeft + slotW * i, y = cq * 6).size(slotW)
                        .border(cq * 0.3f, if (helping == i) FeltMustard else PaperLine, RoundedCornerShape(cq * 0.8f))
                        .clip(RoundedCornerShape(cq * 0.8f))
                        .clickable(enabled = selected != null) { selected?.let { if (it == i) place(it) else sendHome(it) } }
                        .testTag("puzzle-slot-$i")
                ) {
                    if (i in placed) StripArt(pieces, strip(i), 1f)
                    else StripArt(pieces, strip(i), 0.15f)
                }
            }
            // 섞인 조각 — 끌거나 톡
            PUZZLE_SHUFFLE.filter { it !in placed }.forEach { i ->
                val home = homeOf(i)
                val at = home + (moved[i] ?: Offset.Zero)
                val wobble = remember(i) { Animatable(0f) }
                LaunchedEffect(nudge) {
                    if (nudge?.first != i) return@LaunchedEffect
                    repeat(2) { wobble.animateTo(9f, tween(90)); wobble.animateTo(-8f, tween(120)); wobble.animateTo(0f, tween(90)) }
                }
                val tilt = (PUZZLE_SHUFFLE.indexOf(i) - 1) * 6f
                Box(
                    Modifier.offset { androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()) }
                        .size(slotW * PUZZLE_SMALL)
                        .graphicsLayer { rotationZ = if (moved[i] != null) 0f else tilt + wobble.value }
                        .shadow(cq * 0.6f, RoundedCornerShape(cq * 0.6f))
                        .background(Color.White, RoundedCornerShape(cq * 0.6f))
                        .border(if (selected == i || helping == i) cq * 0.45f else cq * 0.25f, if (selected == i || helping == i) FeltMustard else Color.White, RoundedCornerShape(cq * 0.6f))
                        .clip(RoundedCornerShape(cq * 0.6f))
                        .pointerInput(i) { detectTapGestures { selected = i } }
                        .pointerInput(i) {
                            detectDragGestures(
                                onDrag = { change, amount -> moved[i] = (moved[i] ?: Offset.Zero) + amount; change.consume() },
                                onDragEnd = {
                                    val size = slotW.toPx() * PUZZLE_SMALL
                                    val center = homeOf(i) + (moved[i] ?: Offset.Zero) + Offset(size / 2, size / 2)
                                    val under = (0 until PUZZLE_N).firstOrNull { k ->
                                        val (o, w) = slotRect(k)
                                        center.x in o.x..(o.x + w) && center.y in o.y..(o.y + w)
                                    }
                                    when (under) {
                                        i -> place(i)
                                        null -> progress++
                                        else -> sendHome(i)
                                    }
                                },
                            )
                        }
                        .testTag("puzzle-piece-$i")
                ) { StripArt(pieces, strip(i), 1f) }
            }
        }
        // 안내 — 그림 칸 위
        Text(
            hint, fontSize = (cq.value * 1.8f).sp, color = if (placed.size == PUZZLE_N) Color.White else InkBrown,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = cq * 4.4f)
                .shadow(cq * 0.4f, RoundedCornerShape(cq * 3))
                .background(if (placed.size == PUZZLE_N) FeltTeal else Color.White, RoundedCornerShape(cq * 3))
                .padding(horizontal = cq * 1.6f, vertical = cq * 0.4f)
                .testTag("puzzle-hint"),
        )
    }
}

/** 내 그림의 한 세로 줄 — [crop] 창만 보이게 잘라서. [alpha] 가 낮으면 흐린 자리 */
@Composable
private fun StripArt(pieces: List<DiaryPiece>, crop: BoardBox, alpha: Float) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)).alpha(alpha)) {
        pieces.forEach { p -> PieceLayer(p, crop, null, 1f, maxWidth.value, maxHeight.value) }
    }
}

/** 그림판 ↶ · ↷ — 크레용과 같은 크기의 동그란 단추에 화살표. [enabled] 가 아니면 흐린 회색이고 눌리지 않는다 */
@Composable
private fun ArrowButton(left: Boolean, enabled: Boolean, cq: Dp, tag: String, onClick: () -> Unit) {
    val ink = if (enabled) InkBrown else InkSoft.copy(alpha = 0.45f)
    Box(
        Modifier.size(cq * 4.1f)
            .shadow(if (enabled) cq * 0.4f else 0.dp, CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.55f), CircleShape)
            .border(cq * 0.3f, if (enabled) PaperLine else PaperLine.copy(alpha = 0.4f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (left) "획 지우기" else "획 되살리기" }
            .testTag(tag),
    ) {
        Canvas(Modifier.fillMaxSize().padding(cq * 1.05f)) {
            val w = size.width; val h = size.height
            val dir = if (left) -1f else 1f
            val cx = w / 2f; val cy = h / 2f
            val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tip = Offset(cx + dir * w * 0.42f, cy)
            drawLine(ink, Offset(cx - dir * w * 0.42f, cy), tip, strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(tip.x - dir * w * 0.32f, cy - h * 0.32f); lineTo(tip.x, tip.y); lineTo(tip.x - dir * w * 0.32f, cy + h * 0.32f)
            }, ink, style = stroke)
        }
    }
}
