package com.example.finalproject_demo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.ChildProfile
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.ruleEstimate
import com.example.finalproject_demo.demo.CHECKLIST
import com.example.finalproject_demo.demo.Card
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.NEVER
import com.example.finalproject_demo.demo.Persona
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.PEN_W
import com.example.finalproject_demo.demo.dinoKind
import com.example.finalproject_demo.demo.Stroke as DrawStroke
import kotlin.math.roundToInt

/** 점선 테두리 — 도감의 빈 칸 (⭐8) */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp) = this.drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = 4.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
    )
}

@Composable
fun ArtView(art: Art, modifier: Modifier = Modifier) {
    when (art) {
        is Art.HeroArt -> HeroImage(art.attr, modifier)   // 프리셋 · 골라서 · 말로 만든 주인공 모두 같은 펠트 그림
        is Art.DinoArt -> AssetImage(dinoKind(art.kind).art, modifier, colorFilter = if (dinoHue(art.color) != 0f) hueRotate(dinoHue(art.color)) else null) {
            FigureView(dino(art.color, art.kind), modifier)
        }
        is Art.Alien -> FigureView(alienPreset(art.k), modifier)
        is Art.Emoji -> EmojiView(art.text, modifier)
        Art.Rocket -> AssetImage("rocket", modifier) { FigureView(Rocket, modifier) }
        Art.Mascot -> AssetImage("mascot", modifier) { FigureView(Mascot, modifier) }
        is Art.ChildDrawing -> ChildDrawingView(art.strokes, art.preset, modifier, art.aspect)
        is Art.Img -> AssetImage(art.name, modifier) { ArtView(art.fallback, modifier) }
    }
}

/** 아이 그림 원본 그대로 + 흰 오려낸 테두리 (27). 안 그렸으면 프리셋. */
@Composable
fun ChildDrawingView(strokes: List<DrawStroke>, preset: Int, modifier: Modifier = Modifier, aspect: Float = 1f) {
    if (strokes.isEmpty()) {
        FigureView(alienPreset(preset), modifier)
        return
    }
    Canvas(modifier) {
        val all = strokes.flatMap { it.pts }
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val bw = maxOf(maxX - minX, 0.05f) * aspect
        val bh = maxOf(maxY - minY, 0.05f)
        val pad = 0.12f
        val sc = minOf(size.width / bw, size.height / bh) * (1f - pad)
        val w = bw * sc; val h = bh * sc
        val ox = (size.width - w) / 2f
        val oy = (size.height - h) / 2f
        val k = minOf(size.width, size.height)
        fun path(s: DrawStroke) = Path().apply {
            s.pts.forEachIndexed { i, p ->
                val x = ox + (p.x - minX) * aspect * sc
                val y = oy + (p.y - minY) * sc
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        // 굵기는 **그릴 때 굵기 그대로** — 그림판 폭 한 칸이 여기서 (aspect * sc) 픽셀이다.
        // 화면 크기(k)로 다시 계산하면 작게 그린 그림일수록 선이 부풀어 뚱뚱해 보였다 (9/21).
        val padUnit = aspect * sc
        strokes.forEach { s ->
            val w = (s.w * padUnit).coerceIn(k * 0.006f, k * 0.03f)
            drawPath(path(s), Color.White, style = Stroke(w * 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        strokes.forEach { s ->
            val w = (s.w * padUnit).coerceIn(k * 0.006f, k * 0.03f)
            drawPath(path(s), s.color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BigCard(
    card: Card,
    picked: Boolean,
    modifier: Modifier = Modifier,
    width: Int = 176,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .width(width.dp)
            .scale(if (picked) 1.06f else 1f)
            .shadow(if (picked) 14.dp else 8.dp, RoundedCornerShape(R), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(R))
            .background(CardWhite)
            .then(if (picked) Modifier.border(5.dp, Sun, RoundedCornerShape(R)) else Modifier)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtView(card.art, Modifier.fillMaxWidth().height(108.dp))
        Spacer(Modifier.height(6.dp))
        Text(card.label, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun PillButton(text: String, bg: Color, fg: Color, fontSize: Int = 19, onClick: () -> Unit) {
    Box(
        Modifier
            .shadow(4.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = (fontSize * 0.9).dp, vertical = (fontSize * 0.45).dp)
    ) { Text(text, fontSize = fontSize.sp, color = fg, fontWeight = FontWeight.Bold) }
}

@Composable
private fun WorldBackground(bg: List<Color>, bgName: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bg))) {
        AssetImage(bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Canvas(Modifier.fillMaxSize()) {
                val rnd = java.util.Random(7)
                repeat(40) {
                    drawCircle(Color.White.copy(alpha = 0.55f), 2.5f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.7f))
                }
            }
        }
        content()
    }
}

/** 무대 가운데 — 위 제목 · 아래 말풍선에 가리지 않게 안쪽 여백을 둔다 */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(top = TopChrome, bottom = BottomChrome), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun StageView(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    val stage = s.stage
    Box(modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        when (stage) {
            Stage.Empty -> {}

            Stage.Adult -> AdultScreen(d)

            is Stage.Bestiary -> Centered {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    stage.heroes.forEachIndexed { i, h ->
                        Box {
                            Column(
                                Modifier
                                    .width(146.dp)
                                    .shadow(8.dp, RoundedCornerShape(R), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
                                    .clip(RoundedCornerShape(R))
                                    .background(CardWhite)
                                    .clickable { d.send(Reply.Tapped("hero:$i", h.name)) }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ArtView(Art.HeroArt(h.attr), Modifier.fillMaxWidth().height(130.dp))
                                Text(h.name, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            // 지우기 — 네 칸이 다 차면 더 못 만들기 때문에 필요하다 (9/21 요청).
                            // 마지막 한 명은 못 지운다 — 도감이 비면 이야기를 시작할 수 없다
                            if (stage.heroes.size > 1) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(30.dp)
                                        .shadow(3.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF7E7E2))
                                        .clickable { d.send(Reply.Tapped("del:$i", h.name)) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("🗑", fontSize = 15.sp) }
                            }
                        }
                    }
                    if (stage.plus) {
                        repeat((4 - stage.heroes.size).coerceAtLeast(0)) {
                            Box(
                                Modifier
                                    .size(146.dp, 172.dp)
                                    .clip(RoundedCornerShape(R))
                                    .dashedBorder(Sun, R)
                                    .clickable { d.send(Reply.Tapped("plus", "＋")) },
                                contentAlignment = Alignment.Center,
                            ) { Text("＋", fontSize = 60.sp, color = Sun) }
                        }
                    }
                }
            }

            is Stage.CardsRow -> Centered {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    stage.cards.forEach { c ->
                        BigCard(c, stage.picked == c.value) { d.send(Reply.Tapped(c.value, c.label)) }
                    }
                    if (stage.drawerHint) {
                        Column(
                            Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(R))
                                .background(CardWhite.copy(alpha = 0.9f))
                                .clickable { d.send(Reply.Tapped("draw", "그려서 알려줄래")) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtView(Art.Img("ic_draw", Art.Emoji("🖍️")), Modifier.size(60.dp))
                            Text("그려서 알려줄래?", fontSize = 14.sp, color = Ink, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            is Stage.PartnerPick -> Centered {
                // 카드 없이 녹음으로 — 말하기 전에는 "누구일까?" 빈 자리, 말하면 그 사람 그림이 톡 나타난다
                val picked = stage.picked?.let { com.example.finalproject_demo.demo.partner(it) }
                val pop by animateFloatAsState(if (picked != null) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "pp")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ArtView(Art.Mascot, Modifier.size(130.dp))
                    Text("＋", fontSize = 40.sp, color = Sun, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        if (picked == null) {
                            val t = rememberInfiniteTransition(label = "who")
                            val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "wa")
                            Box(Modifier.fillMaxSize().dashedBorder(Sun, 75.dp).alpha(a), contentAlignment = Alignment.Center) {
                                Text("?", fontSize = 64.sp, color = Sun, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(pop)) {
                                ArtView(Art.Img(picked.img, Art.Emoji(picked.emoji)), Modifier.size(120.dp))
                                Text(picked.name, fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            is Stage.HeroShow -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (stage.caption.isNotEmpty()) Text(stage.caption, fontSize = 17.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    if (stage.attr == null) {
                        Box(Modifier.size(120.dp, 180.dp).border(4.dp, Sun.copy(alpha = 0.6f), RoundedCornerShape(60.dp)))
                    } else {
                        HeroImage(stage.attr, Modifier.size(130.dp, 190.dp))
                    }
                }
            }

            is Stage.Show -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtView(stage.art, Modifier.size(220.dp, 190.dp))
                    if (stage.caption.isNotEmpty()) Text(stage.caption, fontSize = 17.sp, color = Muted)
                }
            }

            is Stage.HeroBuilder -> HeroBuilderView(d, stage)

            is Stage.Making -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val t = rememberInfiniteTransition(label = "brush")
                    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                    if (stage.progress >= 1f) {
                        ArtView(Art.Img("ic_books", Art.Emoji("📚")), Modifier.size((84 + 10 * a).dp))
                    } else {
                        Text("🖌️", fontSize = (48 + 14 * a).sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stage.label, fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (stage.progress >= 0f) {
                        Spacer(Modifier.height(10.dp))
                        val p by animateFloatAsState(stage.progress, tween(200), label = "mk")
                        Box(Modifier.width(260.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDE2CF))) {
                            Box(Modifier.fillMaxWidth(p).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(Sun2, Coral))))
                        }
                        if (stage.progress >= 1f) {
                            Spacer(Modifier.height(6.dp))
                            Text("책 이름은 책장에서 바꿀 수 있어요", fontSize = 13.sp, color = Muted)
                        }
                    }
                }
            }

            is Stage.Confirm -> {
                val body: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxSize().padding(top = TopChrome, bottom = BottomChrome),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val cw = if (stage.redraws >= 0) 150 else 176
                            ArtView(stage.art, Modifier.size(170.dp, 170.dp))
                            BigCard(Card(stage.ok, Art.Img("ic_good", Art.Emoji("🙂")), "ok"), false, width = cw) { d.send(Reply.Tapped("ok", stage.ok)) }
                            BigCard(Card(stage.no, Art.Img("ic_bad", Art.Emoji("✏️")), "no"), false, width = cw) { d.send(Reply.Tapped("no", stage.no)) }
                            if (stage.redraws >= 0) {
                                BigCard(Card("직접 그리기", Art.Img("ic_draw", Art.Emoji("🖍️")), "draw"), false, width = cw) { d.send(Reply.Tapped("draw", "직접 그리기")) }
                            }
                        }
                        if (stage.redraws >= 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 62.dp, end = 16.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.9f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("다시 그리기 ${stage.redraws}/${stage.redrawMax}", fontSize = 13.sp, color = Muted) }
                        }
                    }
                }
                if (stage.world) WorldBackground(s.worldBg, s.bgName) { body() } else body()
            }

            is Stage.World -> WorldBackground(s.worldBg, s.bgName) {
                val quake = if (stage.quake) {
                    val t = rememberInfiniteTransition(label = "quake")
                    val dy by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "dy")
                    dy
                } else 0f
                HotspotLayer(s.bgName, stage.glow, stage.pulse, quake = stage.quake)
                Box(Modifier.fillMaxSize().offset { IntOffset(0, quake.roundToInt()) }) {
                    stage.items.forEach { item -> WorldItemView(item) }
                }
                if (stage.brush) {
                    val t = rememberInfiniteTransition(label = "brush2")
                    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a2")
                    Text("🖌️", fontSize = 26.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp).alpha(a))
                }
                if (stage.retry >= 0) {
                    Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("🔁 다시 (${stage.retry}번 남음)", Color.White.copy(alpha = 0.95f), Ink, 16) { d.send(Reply.Tapped("retry", "다시")) }
                        PillButton("👍 이 소리로", Sun, Ink, 16) { d.send(Reply.Tapped("ok", "이 소리로")) }
                    }
                }
            }

            is Stage.DrawPad -> DrawPadView(d, stage.forAnswer)

            is Stage.MouthTap -> Centered {
                Box(
                    Modifier
                        .size(210.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                d.s.mouth = Offset(p.x / size.width, p.y / size.height)
                                d.send(Reply.Tapped("mouth", "입"))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ArtView(stage.art, Modifier.fillMaxSize())
                    d.s.mouth?.let { m ->
                        Text(
                            "👄", fontSize = 26.sp,
                            modifier = Modifier.align(Alignment.TopStart).offset {
                                IntOffset((m.x * 210.dp.toPx() - 30).roundToInt(), (m.y * 210.dp.toPx() - 30).roundToInt())
                            }
                        )
                    }
                }
            }

            is Stage.BookPage -> BookPageView(d, stage)

            is Stage.FriendRate -> FriendRateView(d, stage)

            is Stage.Gifts -> GiftsView(d, stage)

            is Stage.Shelf -> ShelfView(d, stage)

            is Stage.Pin -> PinView(d, stage)

            is Stage.Parent -> ParentView(d, stage.tab)
        }
    }
}

/** 무대 위 인물 · 탈것 한 장 — 톡 튀어나온다 (배경 속 것은 HotspotLayer가 맡는다) */
@Composable
private fun WorldItemView(item: com.example.finalproject_demo.demo.WorldItem) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth * item.wf
        val shakeMod = if (item.shake) {
            val t = rememberInfiniteTransition(label = "shake")
            val dx by t.animateFloat(-7f, 7f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "dx")
            Modifier.offset { IntOffset(dx.roundToInt(), 0) }
        } else Modifier
        Box(
            Modifier
                .padding(start = maxWidth * item.xf, top = maxHeight * item.yf)
                .width(w)
                .height(w / 0.75f)
                .then(shakeMod)
        ) { ArtView(item.art, Modifier.fillMaxSize()) }
    }
}

/** 시작 화면의 모드 버튼 — 무엇으로 짓는가를 고르는 자리 */
@Composable
private fun ModeButton(label: String, bg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .shadow(6.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold) }
}

/** 장면 1 — 시작 화면. 아래 마스코트 말풍선은 없다. 위 왼쪽 아이/부모 전환 · 위 오른쪽 별(크롬) */
@Composable
private fun AdultScreen(d: Director) {
    val s = d.s
    Box(Modifier.fillMaxSize()) {
        AssetImage("bg_start", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Sun2, Sun))))
        }
        // 아이 · 부모 모드 전환
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp)
                .shadow(6.dp, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(3.dp)
        ) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Sun).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("🧒 아이", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.clip(RoundedCornerShape(999.dp)).clickable { d.send(Reply.Tapped("parent", "부모 모드")) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("👪 부모", fontSize = 15.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 56.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val t = rememberInfiniteTransition(label = "hi")
                val bob by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "bob")
                ArtView(Art.Mascot, Modifier.size(190.dp).offset { IntOffset(0, bob.roundToInt()) })
                Text("말로 짓는 인형극", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier
                    .weight(1.15f)
                    .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Ink.copy(alpha = 0.3f), spotColor = Ink.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text("${s.childName}${if (com.example.finalproject_demo.demo.bat(s.childName)) "과" else "와"} 함께 이야기를 만들어요", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("약 15분 · 책장에 ${s.shelf.size}권", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .shadow(10.dp, RoundedCornerShape(999.dp), ambientColor = Coral.copy(alpha = 0.5f), spotColor = Coral.copy(alpha = 0.5f))
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(Coral, Coral2)))
                            .clickable { d.send(Reply.Tapped("start", "이야기 만들기")) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (s.pinToStart) "🔒 이야기 만들기" else "이야기 만들기", fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .shadow(6.dp, RoundedCornerShape(999.dp))
                            .clip(RoundedCornerShape(999.dp))
                            .background(Sun2)
                            .clickable { d.send(Reply.Tapped("shelf", "책장")) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text("📚 책장", fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                // 갈래가 갈라지는 유일한 자리 (일기 §1 · 협업 §3).
                // ⚠️ 문구에 "일기"를 쓰지 않는다 — 아이가 옆에서 보고 숙제로 듣는다 (일기 §0)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(
                        "🌙 오늘 있었던 일로", Color(0xFF6E5A8C), Modifier.weight(1f),
                    ) { d.send(Reply.Tapped("diary", "오늘 있었던 일로")) }
                    // 부모에게 소재를 받는 모드가 아니라 **질문하는 사람을 바꾸는 모드**다 (협업 §0)
                    ModeButton(
                        "👪 같이 만들기", Color(0xFF3F6E63), Modifier.weight(1f),
                    ) { d.send(Reply.Tapped("coop", "같이 만들기")) }
                }
                Spacer(Modifier.height(10.dp))
                Text("어른과 함께 하는 놀이예요", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("어른이 옆에서 함께할 때 가장 좋아요", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        if (s.limitOn) "하루 ${s.dailyLimit}권" else "한도 없음",
                        if (s.pinToStart) "시작 비밀번호 켜짐" else "바로 시작",
                        com.example.finalproject_demo.demo.ART_STYLES.first { it.key == s.artStyle }.name,
                    ).forEach {
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Sun2.copy(alpha = 0.45f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                            Text(it, fontSize = 11.sp, color = Ink)
                        }
                    }
                }
            }
        }
        // 하루 별을 다 썼을 때 한 번만 뜨는 안내
        s.notice?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .width(380.dp)
                        .shadow(16.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtView(Art.Mascot, Modifier.size(70.dp))
                    Text(msg, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("📚 책장 보기", Sun, Ink, 16) { d.send(Reply.Tapped("notice:shelf", "책장")) }
                        PillButton("🙂 괜찮아", Color(0xFFF3E7D0), Ink, 16) { d.send(Reply.Tapped("notice:ok", "괜찮아")) }
                    }
                }
            }
        }
    }
}

/**
 * 「골라서 만들기」의 줄 — 이름 · 보내는 key · (보이는 글, 값) 목록.
 *
 * `@Composable` 안에 두면 **화면을 띄워야만 확인할 수 있다.** 이 기기에서는 에뮬레이터를 띄우면
 * 기계가 얼어 버려서(트러블슈팅 6-10 · 6-12) 그 길이 막혔다. 그래서 밖으로 꺼내 검사 대상으로 만들었다 (9/21).
 *
 * 성별(남 · 여) 줄은 **뺐다** — 성별을 먼저 묻고 옷을 정해 주는 순서가 아니라,
 * 아이가 바지든 치마든 그냥 고르면 되는 순서로 둔다.
 */
val HERO_ROWS: List<Triple<String, String, List<Pair<String, String>>>> = listOf(
    Triple("머리", "hair", listOf("짧아" to "short", "길어" to "long", "묶었어" to "tied")),
    Triple("옷", "shirt", listOf("빨강" to "F25C4C", "파랑" to "3F7BD9", "노랑" to "F9B233")),
    Triple("눈", "eyes", listOf("동글" to "round", "반달" to "smile", "별" to "star")),
    Triple("안경", "glasses", listOf("없음" to "none", "동글" to "round", "네모" to "square")),
    Triple("아래옷", "bottom", listOf("바지" to "pants", "치마" to "skirt", "반바지" to "shorts")),
)

@Composable
private fun HeroBuilderView(d: Director, stage: Stage.HeroBuilder) {
    val rows = HERO_ROWS
    // 줄이 여섯으로 늘면서 [이걸로 할래]가 아래 말풍선 자리까지 내려가 **가려졌다** (9/21 에뮬레이터 확인).
    // 버튼을 토글 오른쪽 빈 자리로 옮긴다 — 아래쪽은 말풍선 · 🎤 · ➡️ 가 쓰는 자리다.
    Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 20.dp, top = TopChrome, bottom = BottomChrome), verticalAlignment = Alignment.CenterVertically) {
        HeroImage(stage.attr, Modifier.width(200.dp).fillMaxHeight())
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, key, opts) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 17.sp, color = Ink, modifier = Modifier.width(58.dp))
                    opts.forEach { (t, v) ->
                        val on = when (key) {
                            "hair" -> stage.attr.hair == v
                            "eyes" -> stage.attr.eyes == v
                            "glasses" -> stage.attr.glasses == v
                            "bottom" -> stage.attr.bottom == v
                            else -> stage.attr.shirt == Color(v.toLong(16) or 0xFF000000)
                        }
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) Sun else Color(0xFFF3E7D0))
                                .clickable { d.send(Reply.Tapped("set:$key:$v", t)) }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) { Text(t, fontSize = 16.sp, color = Ink) }
                    }
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        PillButton("🙂 이걸로 할래", Coral, Color.White, 17) { d.send(Reply.Tapped("ok", "좋아")) }
    }
}

/**
 * 그림판 — 크레용 12색 + **지우개** + 모두 지우기.
 *
 * 9/21에 세 가지를 고쳤다.
 *  1. 색이 네 개뿐이라 그릴 수 있는 게 적었다 → 12색.
 *  2. [지우기]가 **전부** 지워서 한 획만 고칠 수가 없었다 → 지우개로 **닿은 데만** 지운다.
 *     획을 통째로 없애지 않고 **지나간 자리에서 끊어** 준다. 긴 낙서 한 획이 통째로 사라지지 않게.
 *  3. 붓이 화면 픽셀 고정(16f)이라 그림판에서보다 책에서 더 두껍게 나왔다 → 굵기를 그림판 폭 기준([PEN_W])으로 두고
 *     책에도 그 값을 그대로 넘긴다.
 */
@Composable
private fun DrawPadView(d: Director, forAnswer: Boolean = false) {
    // 한 획 = 색 + 점들. 지우개가 획을 끊기 때문에 목록을 통째로 갈아 끼운다
    val strokes = remember { mutableStateListOf<Pair<Color, MutableList<Offset>>>() }
    var color by remember { mutableStateOf(Coral) }
    var erasing by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var box by remember { mutableStateOf(IntSize(1, 1)) }

    val penPx = box.width * PEN_W
    val eraseR = box.width * 0.035f

    /** 지우개가 지나간 자리에서 획을 끊는다 — 남은 토막만 다시 담는다 */
    fun erase(at: Offset) {
        var changed = false
        val kept = mutableListOf<Pair<Color, MutableList<Offset>>>()
        strokes.forEach { (c, pts) ->
            var run = mutableListOf<Offset>()
            pts.forEach { p ->
                if ((p - at).getDistance() <= eraseR) {
                    changed = true
                    if (run.size >= 2) kept += c to run
                    run = mutableListOf()
                } else run.add(p)
            }
            if (run.size >= 2) kept += c to run
        }
        if (changed) {
            strokes.clear()
            strokes.addAll(kept)
            tick++
        }
    }

    fun commit() {
        if (forAnswer) return
        d.s.drawing.clear()
        d.s.drawingAspect = box.width.toFloat() / box.height.toFloat()
        strokes.forEach { (c, pts) ->
            if (pts.size >= 2) d.s.drawing += DrawStroke(c, pts.map { Offset(it.x / box.width, it.y / box.height) }, PEN_W)
        }
    }

    // 도화지를 넓힌다 (9/22) — 가장자리 여백과 위아래 자리를 줄여 그릴 자리로 돌린다.
    // 위는 진행 막대·화면 이름이, 아래는 마스코트 말풍선이 있는 자리라 **겹치지 않을 만큼만** 줄였다
    Row(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = TopChrome - 16.dp, bottom = BottomChrome - 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 12색을 2줄로 — 세로 한 줄로 두면 가로 화면에서 넘친다.
        //
        // 크레용은 **30dp 그대로 둔다** (9/22). 한 번 46dp로 키웠다가 되돌렸다 —
        // 팔레트가 커진 만큼 도화지가 좁아져서, 정작 그릴 자리가 줄었다. 그릴 자리가 먼저다.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CRAYONS.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (c == color && !erasing) 4.dp else 1.dp, if (c == color && !erasing) Ink else Color(0x33000000), CircleShape)
                                .clickable { color = c; erasing = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (erasing) Coral else Color(0xFFF3E7D0))
                        .border(if (erasing) 3.dp else 0.dp, Ink, RoundedCornerShape(12.dp))
                        .clickable { erasing = !erasing }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) { Text("🧽 지우개", fontSize = 14.sp, color = if (erasing) Color.White else Ink) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3E7D0))
                        .clickable { strokes.clear(); erasing = false; tick++ }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) { Text("모두 지우기", fontSize = 14.sp, color = Ink) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(6.dp, RoundedCornerShape(R))
                .clip(RoundedCornerShape(R))
                .background(Color.White)
                .onSizeChanged { box = it }
                .pointerInput(erasing) {
                    detectDragGestures(
                        onDragStart = { p -> if (erasing) erase(p) else { strokes += color to mutableListOf(p); tick++ } },
                        onDrag = { change, _ ->
                            if (erasing) erase(change.position)
                            else {
                                strokes.lastOrNull()?.second?.add(change.position)
                                tick++
                            }
                            change.consume()
                        },
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") tick
                strokes.forEach { (c, pts) ->
                    val p = Path()
                    pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                    drawPath(p, c, style = Stroke(penPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (strokes.isEmpty()) {
                Text("여기에 손가락으로 그려 보세요", fontSize = 16.sp, color = Muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton("✅ 다 그렸어", Coral, Color.White, 17) { commit(); d.send(Reply.Tapped("done", "완료")) }
            if (!forAnswer) PillButton("그리기 싫어", Color(0xFFF3E7D0), Ink, 15) { d.send(Reply.Tapped("preset", "프리셋")) }
        }
    }
}

/** 크레용 12색 — 살구 · 갈색까지 넣어 사람도 그릴 수 있게 (9/21) */
private val CRAYONS = listOf(
    Color(0xFFE8604C), Color(0xFFF08A3C), Color(0xFFF3C33C), Color(0xFF7FBF4D), Color(0xFF3F9E6E), Color(0xFF3F7BD9),
    Color(0xFF6C63C9), Color(0xFFD96BA8), Color(0xFFFFC2A0), Color(0xFF8A5A3C), Color(0xFF3A2A1E), Color(0xFFA9B4BD),
)

/** 장면 13 — 친구 평가 (S10). 지우는 선택지는 없다. 고른 것을 평가하지 않는다. */
@Composable
private fun FriendRateView(d: Director, stage: Stage.FriendRate) {
    Centered {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            stage.friends.forEach { f ->
                Column(
                    Modifier
                        .width(220.dp)
                        .shadow(10.dp, RoundedCornerShape(R), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(R))
                        .background(CardWhite)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtView(f.art, Modifier.fillMaxWidth().height(100.dp))
                    Text(f.name, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (f.keep == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PillButton("또 만날래 💛", Coral, Color.White, 14) { d.send(Reply.Tapped("keep:${f.id}", f.name)) }
                            PillButton("안녕 👋", Color(0xFFF3E7D0), Ink, 14) { d.send(Reply.Tapped("bye:${f.id}", f.name)) }
                        }
                    } else {
                        Text(if (f.keep) "또 만나기로 했어 💛" else "안녕! 👋", fontSize = 16.sp, color = if (f.keep) Coral else Muted, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
            PillButton("➡️ 다음", Sun, Ink, 17) { d.send(Reply.Tapped("done", "다음")) }
        }
    }
}

/** 장면 14 — 선물이 차례로, 그다음 [책장에 꽂기] */
@Composable
private fun GiftsView(d: Director, stage: Stage.Gifts) {
    Box(Modifier.fillMaxSize()) {
        Centered {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(Triple("gift_book", "🧩", "해결 방법 도감\n친구와 함께"), Triple("gift_crayon", "🌈", "무지개 크레용")).forEachIndexed { i, (img, e, t) ->
                    val on = i < stage.shown
                    val pop by animateFloatAsState(if (on) 1f else 0.85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "gift$i")
                    Column(
                        Modifier
                            .width(210.dp)
                            .scale(pop)
                            .alpha(if (on) 1f else 0.15f)
                            .shadow(10.dp, RoundedCornerShape(R), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(R))
                            .background(CardWhite)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtView(Art.Img(img, Art.Emoji(e)), Modifier.size(110.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(t, fontSize = 16.sp, color = Ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (d.s.buttons.any { it.label == "📚 책장에 꽂기" }) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)) {
                PillButton("📚 책장에 꽂기", Sun, Ink, 19) { d.send(Reply.Tapped("shelf", "책장")) }
            }
        }
    }
}

/** 시연 서랍 — 화면 오른쪽 위 구석을 길게 누르면 열린다. 아이 화면은 깨끗하게 둔다. */
@Composable
fun DemoDrawer(d: Director, onClose: () -> Unit) {
    val s = d.s
    var tab by remember { mutableStateOf("ctrl") }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.35f)).clickable { onClose() })
        Column(Modifier.width(430.dp).fillMaxHeight().background(Color(0xFF221D18)).padding(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ctrl" to "🎮 조작", "judge" to "🧠 발달 판단", "behind" to "⚙️ 뒤에서", "state" to "📊 상태", "event" to "🧾 기록", "check" to "✅ 체크", "never" to "🚫 금지")
                    .forEach { (k, t) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (tab == k) Sun else Color(0xFF3A332C))
                                .clickable { tab = k }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) { Text(t, fontSize = 12.sp, color = if (tab == k) Ink else Color.White) }
                    }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (tab) {
                    "ctrl" -> DrawerControls(d)
                    "behind" -> Text(s.behind, fontSize = 13.sp, color = Color(0xFFE8DCC8), lineHeight = 20.sp)
                    "state" -> DrawerState(d)
                    "judge" -> DrawerJudge(d)
                    "event" -> DrawerEvents(d)
                    "check" -> CHECKLIST.forEach { (k, t) ->
                        Text(
                            "${if (k in s.done) "✅" else "⬜"} $t",
                            fontSize = 12.sp,
                            color = if (k in s.done) Color(0xFFBFE8B0) else Color(0xFFB8AC9C),
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    else -> NEVER.forEach {
                        Text("· $it", fontSize = 12.sp, color = Color(0xFFF0B8B0), modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A332C)).clickable { onClose() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("닫기", fontSize = 15.sp, color = Color.White) }
        }
    }
}

@Composable
private fun DrawerChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Sun else Color(0xFF3A332C))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) { Text(text, fontSize = 12.sp, color = if (on) Ink else Color.White) }
}

@Composable
private fun DrawerControls(d: Director) {
    val s = d.s
    Text("가짜 아이 (⭐6)", fontSize = 13.sp, color = Muted)
    Row {
        Persona.entries.forEach { p ->
            DrawerChip(p.label, s.persona == p) { s.persona = p; d.go(s.scene) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("더미 아이 답 — 어느 수준처럼 답할까 (🎤 끄기 · 🎲 버튼에 적용 · 70%)", fontSize = 13.sp, color = Muted)
    Row { ChildProfile.entries.forEach { p -> DrawerChip(p.label, s.profile == p) { s.profile = p } } }
    Text("이번 이야기 시작 수준 (지난 세션 종료 단계 흉내)", fontSize = 13.sp, color = Muted)
    Row {
        Level.entries.forEach { lv ->
            DrawerChip(lv.label, s.levelAtStart == lv) {
                s.nextLevel = lv
                if (s.turn == 0) { s.level = lv; s.levelAtStart = lv }
            }
        }
    }
    Text("함께 하는 사람: ${s.partner.emoji} ${s.pn} (시작 화면 다음에 묻는다)", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    Spacer(Modifier.height(8.dp))
    Text("마이크 · 시간", fontSize = 13.sp, color = Muted)
    Row {
        DrawerChip("🎤 온/오프만 (기본)", !s.timerOn) { s.timerOn = false }
        DrawerChip("⏱ 무응답 타이머 켜기 (⭐5 · 5·8·7초)", s.timerOn) { s.timerOn = true }
    }
    Row {
        DrawerChip("실제 초 수", s.speed == 1.0) { s.speed = 1.0 }
        DrawerChip("빠르게 (¼)", s.speed == 0.25) { s.speed = 0.25 }
        DrawerChip("첫 사용자로 시연 (⭐20)", s.firstDay) { s.firstDay = !s.firstDay }
    }
    Spacer(Modifier.height(8.dp))
    Text("그림 · 한도 (구현대본 §7)", fontSize = 13.sp, color = Muted)
    Row { DrawerChip("🐢 그림 생성 느리게 (8초 안내 · 15초 취소)", s.slowImages) { s.slowImages = !s.slowImages } }
    Row {
        DrawerChip("⭐ 하루 별 0으로", s.dayStars == 0) { s.usedToday = if (s.dayStars == 0) 0 else s.dailyLimit }
        DrawerChip("⏩ 8턴 지난 것으로 (남은 칸 자동)", s.turn >= 8) { s.turn = if (s.turn >= 8) 0 else 8 }
    }
    Spacer(Modifier.height(8.dp))
    Text("아이 반응 (대본 버튼 — 화면의 🎤 · 카드 · ➡️와 같음)", fontSize = 13.sp, color = Muted)
    s.buttons.forEach { b ->
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4A423A))
                .clickable { b.onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) { Text(b.label, fontSize = 13.sp, color = Color.White) }
    }
    Spacer(Modifier.height(8.dp))
    Text("장면 이동", fontSize = 13.sp, color = Muted)
    Column {
        Scene.entries.chunked(2).forEach { row ->
            Row { row.forEach { sc -> DrawerChip(sc.title, s.scene == sc) { d.go(sc) } } }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row {
        DrawerChip("🏠 처음으로 (책장 · 설정 유지)", false) { d.goHome() }
        DrawerChip("↺ 처음부터 (전부 초기화)", false) { d.restart() }
    }
    Spacer(Modifier.height(10.dp))
}

/** 남겨야 할 이벤트 (구현대본 §0-5) — 부모 리포트 6축이 이것만 읽는다. 화면 어디에도 점수는 없다. */
@Composable
private fun DrawerEvents(d: Director) {
    val s = d.s
    Text("남는 이벤트 ${s.events.size}건", fontSize = 14.sp, color = Sun, fontWeight = FontWeight.Bold)
    Text("실제 앱에서는 폰 로컬 DB에 쌓이고, 부모 모드가 이것만 읽어 문장을 만든다", fontSize = 11.sp, color = Muted)
    Spacer(Modifier.height(6.dp))
    if (s.events.isEmpty()) {
        Text("아직 없음 — 이야기를 시작하면 쌓인다", fontSize = 12.sp, color = Color(0xFFB8AC9C))
    }
    s.events.forEach {
        Text("· $it", fontSize = 11.sp, color = Color(0xFFBFE8B0), modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun DrawerState(d: Director) {
    val s = d.s
    // 일기 모드는 기승전결 네 자리가 필수 칸이다 (일기 설계 §2-1). 소리 · 동행은 묻지 않는다 (§2-2)
    val slots = if (s.isDiary) listOf(
        Triple("place", "기 · 장소", s.place), Triple("problem", "승 · 문제", s.problem),
        Triple("cause", "전 · 까닭", s.cause), Triple("solution", "결 · 해결", s.solution),
        Triple("reaction", "(선택) 기분", s.reaction), Triple("newcomer", "(선택) 등장인물", s.newcomer),
    ) else listOf(
        Triple("place", "장소", s.place), Triple("problem", "문제", s.problem), Triple("cause", "까닭", s.cause),
        Triple("newcomer", "등장인물", s.newcomer), Triple("sound", "소리", s.sound), Triple("solution", "해결", s.solution),
    )
    Text(
        "이야기 칸 ${s.filled}/${s.reqCount} · 제목: ${s.title ?: "-"}" + if (s.isDiary) "  [일기 모드]" else "",
        fontSize = 14.sp, color = Sun, fontWeight = FontWeight.Bold,
    )
    slots.forEach { (key, label, v) ->
        // 누가 채웠나(by)를 칸마다 보여 준다 — mascot 은 책 자막에만 나오고 리포트 인용에서는 빠진다 (guidelines/2 §1-4 · §5-1)
        val by = s.slotBy[key]
        Text(
            "${if (v != null) "★" else "☆"} $label — ${v ?: "비어 있음"}${if (by != null) "  [by: $by]" else ""}",
            fontSize = 12.sp, color = if (by == "mascot") Color(0xFFD8B4A0) else Color(0xFFE8DCC8),
        )
    }
    if (s.isDiary) {
        Text(
            "mascot_pick 연속 ${s.mascotPicks}회 (2회면 끝) · 끝난 조건 ${s.endReason ?: "-"} · by:mascot 은 주고받기 · 수준 · 리포트 인용에서 빠짐",
            fontSize = 12.sp, color = Color(0xFFD8B4A0),
        )
    }
    Text("곁칸 — 동행 ${s.friend ?: "-"} · 이름 ${s.friendName} · 배경 속 말한 것 ${s.mentioned.joinToString("+").ifEmpty { "-" }}", fontSize = 12.sp, color = Color(0xFFB8AC9C))
    Text("이야기 조각 — ${s.slots.entries.joinToString(" · ") { "${it.key}=${it.value}" }.ifEmpty { "-" }}", fontSize = 12.sp, color = Color(0xFFB8AC9C))
    Text("${s.pn} 참여 — ${s.partnerHelpLine ?: "없음(답하지 않음)"} · 미션 2 물건 ${s.solutionItem}", fontSize = 12.sp, color = Color(0xFFB8AC9C))
    Text("하루 별 ${if (s.limitOn) "${s.dayStars}/${s.dailyLimit}" else "한도 꺼짐"} · 시작 비밀번호 ${if (s.pinToStart) "켜짐" else "꺼짐"} · 책장 ${s.shelf.size}권", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    Spacer(Modifier.height(8.dp))
    Text("수준 · 신호", fontSize = 14.sp, color = Sun, fontWeight = FontWeight.Bold)
    Text("지금 단계: ${s.level.label} (시작 ${s.levelAtStart.label}) · 다음 세션 시작: ${(s.nextLevel ?: s.level).label}", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    Text("템플릿: ${s.template?.let { "${it.code} ${it.name} · ${it.pages.size}쪽 · 속성 ${s.attribute}" } ?: "아직 (3턴째 확정)"}", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    Text("턴 ${s.turn} · 올림 연속 ${s.s1streak} · 내림 연속 ${s.noAnswerStreak} · ${s.pn} 말 ${s.partnerTurns}", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    s.signals.forEach { Text("· $it", fontSize = 11.sp, color = Color(0xFFBFE8B0)) }
    Spacer(Modifier.height(8.dp))
    Text("생성 이미지 ${s.images}장 · 다시 그리기 ${s.redraws}/${s.redrawMax} · 아이 반응 ${s.reactions}회", fontSize = 12.sp, color = Color(0xFFE8DCC8))
    Spacer(Modifier.height(8.dp))
    Text("뒤에서 일어난 일 (최근)", fontSize = 14.sp, color = Sun, fontWeight = FontWeight.Bold)
    s.log.take(16).forEach {
        Text("· $it", fontSize = 11.sp, color = Color(0xFFB8AC9C), modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** 발달 판단 — 질문마다 무엇을 보려 했고, 답에서 어떤 신호가 나왔는지 (역할1 조사2 §4 · 규칙 계산) */
@Composable
private fun DrawerJudge(d: Director) {
    val s = d.s
    val (est, why) = s.ruleEstimate()
    Text("발달 판단 (시연용 · 아이 · 부모 화면에는 수준 이름이 나오지 않는다)", fontSize = 13.sp, color = Sun, fontWeight = FontWeight.Bold)
    Text("세션 누적 추정: ${est.label}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
    Text(why, fontSize = 11.sp, color = Color(0xFFE8DCC8))
    Text("질문 난이도 수준: ${s.level.label} · 시작 ${s.levelAtStart.label}", fontSize = 11.sp, color = Color(0xFFE8DCC8))
    Text(
        s.levelWhy.ifEmpty { "템플릿은 3턴째(까닭 질문 뒤) 확정 — 고르며 짓기=E 여정형(6쪽) · 이어 짓기=C 위험-대응-도움형/D 방문자-직업수행형(7쪽) · 까닭 짓기=A 도전-성취형/G 우화-교훈형(8쪽)" },
        fontSize = 11.sp, color = Color(0xFFBFE8B0),
    )
    Spacer(Modifier.height(6.dp))
    Text("신호: S1 = 왜 · 어떻게에 까닭 · 방법 / S2 = 묻지 않은 배경 · 계기 · 시도 · 결과 / A1 = 잇는 말 (보조) · 마음 말하기는 신호 아님", fontSize = 10.sp, color = Muted)
    Spacer(Modifier.height(6.dp))
    if (s.notes.isEmpty()) Text("아직 답이 없음", fontSize = 12.sp, color = Color(0xFFB8AC9C))
    s.notes.forEachIndexed { i, n ->
        val tags = buildList {
            if (n.s1) add("S1")
            if (n.el.isNotEmpty()) add("S2(${n.el.joinToString("·")})")
            if (n.a1) add("A1")
        }.joinToString(" ").ifEmpty { "신호 없음" }
        val mode = when (n.mode) { "voice" -> "말 ${n.words}어절"; "card" -> "카드"; "mascot" -> "마스코트가 채움"; "draw" -> "그림"; else -> "무응답" }
        Column(Modifier.fillMaxWidth().padding(bottom = 5.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E2822)).padding(7.dp)) {
            Text("${i + 1}. ${n.q}", fontSize = 11.sp, color = Color(0xFFB8AC9C))
            Text("→ ${n.a}", fontSize = 12.sp, color = Color.White)
            Text("$mode · $tags", fontSize = 11.sp, color = if (n.s1 || n.el.isNotEmpty()) Color(0xFFBFE8B0) else Color(0xFFE8DCC8))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("이번 이야기 질문 id: ${s.askedThisStory.joinToString(", ").ifEmpty { "-" }}", fontSize = 10.sp, color = Muted)
    Text("지난 이야기들에서 쓴 질문 ${s.usedVariants.size}개 → 다음 이야기에서는 되도록 피한다", fontSize = 10.sp, color = Muted)
}
