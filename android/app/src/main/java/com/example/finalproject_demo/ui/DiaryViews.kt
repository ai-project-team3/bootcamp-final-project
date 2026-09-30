package com.example.finalproject_demo.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.BRUSH_PAUSE_MS
import com.example.finalproject_demo.demo.BoardBox
import com.example.finalproject_demo.demo.Card
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryPage
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryStage
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.FEEL_LEAD
import com.example.finalproject_demo.demo.PEN_W
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.PieceMove
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.boxOf
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.cropFor
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.ottoEmoji
import kotlinx.coroutines.delay
import java.time.LocalDate
import com.example.finalproject_demo.demo.Stroke as DrawStroke

/**
 * 그림일기 화면 (docs/일기모드_흐름.html) — 조각 화이트보드(D1) · 그림일기 한 쪽(D5).
 *
 * `StageView` 가 [DiaryStage] 를 여기로 넘긴다(#28). 이 파일과 [DiaryStage] 는 일기 모드(박진웅) 것이라
 * 그림일기 화면을 더해도 `Screen.kt` 를 다시 고치지 않는다.
 */
@Composable
fun DiaryStageView(d: Director, stage: DiaryStage) {
    when (stage) {
        is DiaryBoard -> DiaryBoardView(d, stage)
        is DiaryPaper -> DiaryPaperView(d, stage)
    }
}

// ── D1 화이트보드 ───────────────────────────────────────────────

/** 크레용 12색 — 그림판(`Screen.kt` DrawPadView)과 같은 색. 그쪽이 private 이라 여기 다시 적었다 — 바꾸면 둘 다 바꾼다 */
private val DIARY_CRAYONS = listOf(
    Color(0xFFE8604C), Color(0xFFF08A3C), Color(0xFFF3C33C), Color(0xFF7FBF4D), Color(0xFF3F9E6E), Color(0xFF3F7BD9),
    Color(0xFF6C63C9), Color(0xFFD96BA8), Color(0xFFFFC2A0), Color(0xFF8A5A3C), Color(0xFF3A2A1E), Color(0xFFA9B4BD),
)

/**
 * 그리는 판. 기존 그림판과 다른 점 셋:
 * 1. 획을 **그을 때마다** `s.drawing` 에 넣는다 — 오또 그림을 고르는 사이 화면이 바뀌어도 그리던 것이 남는다
 * 2. 붓이 [BRUSH_PAUSE_MS] 멈추면 `pause` 를 보낸다 — 오또가 묻는 때. 오또가 묻는 중(마이크가 열림)에는 보내지 않는다
 * 3. 이름 붙은 조각 위에 이름표, 오또 그림을 고른 조각은 오또 그림으로 보인다
 */
@Composable
private fun DiaryBoardView(d: Director, stage: DiaryBoard) {
    val s = d.s
    val day = s.diaryDay
    var color by remember { mutableStateOf(DIARY_CRAYONS.first()) }
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    val live = remember { mutableStateListOf<Offset>() }
    var strokes by remember { mutableIntStateOf(0) }

    // 붓 멈춤 — 마지막 획 뒤로 조용하면 알린다
    LaunchedEffect(strokes) {
        if (strokes == 0) return@LaunchedEffect
        delay(BRUSH_PAUSE_MS)
        if (!s.micEnabled && stage.pick == null) d.send(Reply.Tapped("pause", "붓 멈춤"))
    }

    Row(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = TopChrome - 16.dp, bottom = BottomChrome - 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DIARY_CRAYONS.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c ->
                        Box(
                            Modifier.size(30.dp).background(c, CircleShape)
                                .border(if (c == color) 4.dp else 1.dp, if (c == color) InkBrown else InkBrown.copy(alpha = 0.2f), CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxHeight()
                .felt(FeltWhite, RoundedCornerShape(R), lift = 6.dp, texture = false)
                .onSizeChanged { box = it; s.drawingAspect = it.width.toFloat() / maxOf(it.height, 1) }
                .testTag("diary-board")
                .pointerInput(color) {
                    detectDragGestures(
                        onDragStart = { p -> live.clear(); live += p },
                        onDrag = { change, _ -> live += change.position; change.consume() },
                        onDragEnd = {
                            if (live.size >= 2) {
                                s.drawing += DrawStroke(color, live.map { Offset(it.x / box.width, it.y / box.height) }, PEN_W)
                                strokes++
                            }
                            live.clear()
                        },
                    )
                }
        ) {
            val otto = day.pieces.filter { it.look == PieceLook.OTTO }
            val hidden = otto.flatMap { it.strokes }.toSet()
            Canvas(Modifier.fillMaxSize()) {
                s.drawing.filter { it !in hidden }.forEach { drawBoardStroke(it, BoardBox(0f, 0f, 1f, 1f)) }
                if (live.size >= 2) {
                    val p = Path().apply { live.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                    drawPath(p, color, style = Stroke(size.width * PEN_W, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            otto.forEach { p -> boxOf(p.strokes)?.let { b -> OttoLook(p, b, BoardBox(0f, 0f, 1f, 1f), maxWidth.value, maxHeight.value) } }
            day.pieces.filter { it.name != null }.forEach { p ->
                val b = boxOf(p.strokes) ?: return@forEach
                Text(
                    p.name!!, fontSize = 15.sp, color = InkBrown,
                    modifier = Modifier
                        // 조각 바로 위 — 판 맨 위에 그린 조각이면 판 안으로 내려 잘리지 않게
                        .offset(x = (maxWidth.value * b.left).dp, y = ((maxHeight.value * b.top) - 26f).coerceAtLeast(4f).dp)
                        .felt(Sun2, RoundedCornerShape(10.dp), lift = 1.dp, stitch = false)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (s.drawing.isEmpty() && live.isEmpty()) {
                Text("여기에 손가락으로 그려 보세요", fontSize = 20.sp, color = InkSoft, modifier = Modifier.align(Alignment.Center))
            }
            stage.pick?.let { id -> day.pieces.firstOrNull { it.id == id }?.let { OttoPick(d, it, Modifier.align(Alignment.BottomCenter)) } }
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton("✅ 다 그렸어", Coral, Color.White, 17) { d.send(Reply.Tapped("done", "완료")) }
            PillButton("그리기 싫어", WoolCream, Ink, 15) { d.send(Reply.Tapped("skip", "안 그릴래")) }
        }
    }
}

/** 「어떤 게 좋아?」 — 내 그림과 오또 그림을 나란히. 원본이 왼쪽(기본값) */
@Composable
private fun OttoPick(d: Director, piece: DiaryPiece, modifier: Modifier) {
    Row(modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BigCard(Card("내 그림", Art.ChildDrawing(piece.strokes, 0, d.s.drawingAspect), "orig"), picked = false, width = 150) {
            d.send(Reply.Tapped("orig", "내 그림"))
        }
        Column(
            Modifier.width(150.dp).felt(WoolCream, RoundedCornerShape(R)).clickable { d.send(Reply.Tapped("otto", "오또 그림")) }.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(90.dp)) { OttoArt(piece, Modifier.fillMaxSize()) }
            Spacer(Modifier.height(6.dp))
            Text("오또 그림 ✨", fontSize = TextSize.KidCard, color = InkBrown)
        }
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
    val x = (b.left - crop.left) / crop.width * wDp
    val y = (b.top - crop.top) / crop.height * hDp
    val w = b.width / crop.width * wDp
    val h = b.height / crop.height * hDp
    Box(
        Modifier.offset(x = x.dp, y = y.dp).size(maxOf(w, 24f).dp, maxOf(h, 24f).dp).alpha(alpha)
            .graphicsLayer { scaleX = 2f - pop.value; scaleY = pop.value }
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

// ── D5 그림일기 한 쪽 ───────────────────────────────────────────

/**
 * 아이들이 쓰는 한 장짜리 그림일기 양식 — 날짜 · 날씨 · 제목 / 그림 칸(3:1) / 원고지 · 오늘 기분.
 * 가로 화면이라 그림 칸을 왼쪽, 원고지를 오른쪽에 둔다. 문장은 아래 나레이션 칸에서도 읽어 준다.
 */
@Composable
private fun DiaryPaperView(d: Director, stage: DiaryPaper) {
    val s = d.s
    val day = s.diaryDay
    val pages = buildDiaryBook(s.diaryBookInput())
    val page = pages.getOrNull(stage.index) ?: return
    val last = stage.index == pages.lastIndex

    Row(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = TopChrome - 12.dp, bottom = BottomChrome - 10.dp)
            .felt(FeltWhite, RoundedCornerShape(R), lift = 4.dp, texture = false)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(0.56f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetHead(d, day.weather)
            // 실제 그림일기처럼 날짜 · 날씨 · 제목이 위, 그림이 그 아래. 그림 칸은 남은 높이에 맞춰 3:1 로 줄어든다
            Text("제목: ${s.title ?: "오늘의 그림일기"}", fontSize = 17.sp, color = InkBrown, maxLines = 1)
            PicturePanel(d, page, Modifier.weight(1f).aspectRatio(3f, matchHeightConstraintsFirst = true))
        }
        Column(Modifier.weight(0.44f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Manuscript(page, Modifier.fillMaxWidth().weight(1f))
            if (last && page.asksFeel) FeelRow(d)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (stage.index > 0) {
                    PillButton("◀", WoolCream, Ink, 15) { d.send(Reply.Tapped("prev", "앞")) }
                    Spacer(Modifier.width(8.dp))
                }
                PillButton(if (last) "📔 다 읽었어" else "▶ 다음 쪽", Coral, Color.White, 15) { d.send(Reply.Tapped("next", "다음")) }
            }
        }
    }
}

private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/** 날짜 · 날씨 — 날씨는 그림에서 알아봤거나 아이가 누른 것만 켜진다(지어내지 않는다) */
@Composable
private fun SheetHead(d: Director, weather: DiaryWeather?) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${today.year}년 ${today.monthValue}월 ${today.dayOfMonth}일 ${DAYS[today.dayOfWeek.value - 1]}요일", fontSize = 15.sp, color = InkBrown)
        Spacer(Modifier.width(6.dp))
        Text("날씨", fontSize = 15.sp, color = InkSoft)
        DiaryWeather.entries.forEach { w ->
            val on = w == weather
            Text(
                w.emoji, fontSize = 20.sp,
                modifier = Modifier
                    .background(if (on) Sun2 else Color.Transparent, CircleShape)
                    .alpha(if (weather == null || on) 1f else 0.35f)
                    .clickable { d.send(Reply.Tapped("wx:${w.name}", w.label)) }
                    .padding(4.dp)
                    .testTag("wx-${w.name}"),
            )
        }
    }
}

/**
 * 그림 칸 — 아이 그림 조각이 그린 자리 그대로. 문장에 나온 조각은 앞에서 문장대로 움직이고
 * 나머지는 흐리게 뒤에 남는다. 그린 부분만 3:1 로 잘라 맞춘다(찌그러짐 없음).
 */
@Composable
private fun PicturePanel(d: Director, page: DiaryPage, modifier: Modifier) {
    val s = d.s
    val pieces = s.diaryDay.pieces.toList().ifEmpty {
        if (s.sceneDrawing.isEmpty()) emptyList() else listOf(DiaryPiece(0, s.sceneDrawing.toList()))
    }
    BoxWithConstraints(modifier.border(2.dp, InkSoft.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).background(Color.White, RoundedCornerShape(8.dp))) {
        if (pieces.isEmpty()) {
            Text("✏️", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center).alpha(0.4f))
            return@BoxWithConstraints
        }
        val crop = cropFor(pieces.flatMap { it.strokes }, s.drawingAspect.takeIf { it > 0f } ?: 1f)
        val everyone = page.kind == DiaryPageKind.DRAWING || page.kind == DiaryPageKind.PLACE
        pieces.forEach { p ->
            val front = everyone || (p.name != null && p.name in page.cast)
            val moves = front && !everyone && p.name !in page.still
            val a = if (front) 1f else 0.25f
            PieceLayer(p, crop, if (moves) page.move else null, a, maxWidth.value, maxHeight.value)
        }
    }
}

/** 조각 한 겹 — 움직임은 조각 가운데를 축으로 */
@Composable
private fun PieceLayer(p: DiaryPiece, crop: BoardBox, move: PieceMove?, alpha: Float, wDp: Float, hDp: Float) {
    val b = boxOf(p.strokes) ?: return
    val t = rememberInfiniteTransition(label = "piece${p.id}")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (move == PieceMove.TOPPLE) 1400 else 700), RepeatMode.Reverse), label = "k${p.id}")
    val cx = ((b.left + b.right) / 2f - crop.left) / crop.width
    val cy = ((b.top + b.bottom) / 2f - crop.top) / crop.height
    val layer = Modifier.fillMaxSize().alpha(alpha).graphicsLayer {
        transformOrigin = TransformOrigin(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f))
        when (move) {
            PieceMove.HOP -> translationY = -k * size.height * 0.08f
            PieceMove.TOPPLE -> rotationZ = k * 22f
            PieceMove.DROOP -> { translationY = k * size.height * 0.03f; scaleY = 1f - k * 0.06f }
            PieceMove.BUILD -> { scaleX = 1f + k * 0.06f; scaleY = 1f + k * 0.06f }
            PieceMove.WALK -> translationX = (k - 0.5f) * size.width * 0.04f
            PieceMove.BOB -> translationY = -k * size.height * 0.02f
            null -> {}
        }
    }
    if (p.look == PieceLook.OTTO) Box(layer) { OttoLook(p, b, crop, wDp, hDp) }
    else Canvas(layer) { p.strokes.forEach { drawBoardStroke(it, crop) } }
}

/** 원고지 — 문장이 한 글자씩 써진다. 비었다고 쓰는 꼬리와 「오늘은 …」은 연하게 */
@Composable
private fun Manuscript(page: DiaryPage, modifier: Modifier) {
    val main = page.text
    val soft = listOfNotNull(page.tail, page.closing ?: if (page.asksFeel) "$FEEL_LEAD …" else null).joinToString(" ")
    val chars = (main + if (soft.isEmpty()) "" else " $soft").toList()
    var shown by remember(page) { mutableIntStateOf(0) }
    LaunchedEffect(page) { while (shown < chars.size) { delay(60); shown++ } }
    BoxWithConstraints(modifier) {
        val cols = when { chars.size <= 30 -> 10; chars.size <= 60 -> 12; else -> 14 }
        val cell = (maxWidth.value / cols).coerceAtMost(maxHeight.value / ((chars.size + cols - 1) / cols).coerceAtLeast(1))
        Column {
            chars.chunked(cols).forEachIndexed { r, row ->
                Row {
                    row.forEachIndexed { c, ch ->
                        val i = r * cols + c
                        Box(Modifier.size(cell.dp).border(0.5.dp, Coral.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                            if (i < shown && ch != ' ') Text(ch.toString(), fontSize = (cell * 0.55f).sp, color = if (i >= main.length) InkSoft else InkBrown)
                        }
                    }
                }
            }
        }
    }
}

/** 오늘 기분 — 말하지 않았을 때만. 누르면 `by: card` */
@Composable
private fun FeelRow(d: Director) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("오늘 기분은?", fontSize = 16.sp, color = InkBrown)
        DiaryFeel.entries.forEach { f ->
            Text(
                f.emoji, fontSize = 30.sp,
                modifier = Modifier.clickable { d.send(Reply.Tapped("feel:${f.name}", f.line)) }.testTag("feel-${f.name}"),
            )
        }
    }
}
