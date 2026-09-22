package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.pageAuthor
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.mission2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면 비율로 자리 잡기 (책 쪽 안에서) */
@Composable
private fun Layer(xf: Float, yf: Float, wf: Float, aspect: Float = 0.75f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth * wf
        Box(
            modifier
                .padding(start = maxWidth * xf, top = maxHeight * yf)
                .width(w)
                .height(w / aspect)
        ) { content() }
    }
}

@Composable
private fun RoundBtn(text: String, bg: Color, size: Int = 46, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(6.dp, CircleShape, ambientColor = Ink.copy(alpha = 0.3f), spotColor = Ink.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(bg)
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = (size * 0.45).sp, color = Ink, fontWeight = FontWeight.Bold) }
}

/** 도구별 반응 글자 — 누른 것 바로 위에 뜬다 (HTML 데모와 같은 방식) */
private fun reactionFor(s: DemoState, tool: String, target: String, objName: String? = null, objTap: String? = null): String {
    val f = s.friendName
    return when (tool) {
        "hammer" -> "간지러워!"
        "feather" -> "깔깔깔!"
        "glass" -> when (target) {
            "hero" -> if (s.isDiary) "${s.childName}의 오늘 이야기!" else "${s.childName}${eun(s.childName)} ${s.th.vehicle} 선장!"
            "dino" -> "${s.dino.label} · ${s.dino.look}"
            "friend" -> if (s.isDiary) "${s.friendCallName} · 오늘 만난 사람" else "$f · ${s.newcomerKind} 친구"
            "vehicle" -> "${s.rideName}예요"
            "obj" -> objName ?: "?"
            else -> "?"
        }
        else -> when (target) {   // 손
            "hero" -> "히히!"
            "friend" -> "헤헤, 반가워!"
            "dino" -> s.soundLine
            "vehicle" -> "부릉부릉!"
            "obj" -> objTap ?: "톡!"
            else -> "톡!"
        }
    }
}

/**
 * 책 한 쪽 — 전체 화면 그림책 (v0.8: 마스코트 · 아래 버튼 없이 책만).
 * 쪽 수(6~8)와 쪽마다의 그림 구성은 템플릿이 정한다 (v0.9) — 출발 · 흔들림 · 대화 · 여정 · 실패 · 미션 2개 · 함께.
 * 세계 배경 위에 확정 그림(주인공 · 아이 그림 · 공룡), 배경 속 것은 그 자리가 반응한다. 누르면 그 자리 위에 반응 글자.
 */
@Composable
fun BookPageView(d: Director, stage: Stage.BookPage) {
    val s = d.s
    val page = stage.index
    var tool by remember { mutableStateOf("hand") }
    val heroArt = Art.HeroArt(s.heroAttr ?: HeroAttr())
    val dinoArt = Art.DinoArt(s.dinoColor, s.dinoKey)

    fun react(target: String) = d.send(Reply.Tapped("tool:$tool:$target", tool))

    val inf = rememberInfiniteTransition(label = "book")
    val wobble by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "wobble")
    val shake by inf.animateFloat(-9f, 9f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "shake")
    val bob by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val twinkle by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "tw")

    @Composable
    fun Char(target: String, art: Art, xf: Float, yf: Float, wf: Float, aspect: Float = 0.75f, mod: Modifier = Modifier, onHand: (() -> Unit)? = null) {
        Layer(xf, yf, wf, aspect, mod) {
            Tappable(
                text = { reactionFor(s, tool, target) },
                modifier = Modifier.fillMaxSize(),
                onTap = { if (tool == "hand" && onHand != null) onHand() else react(target) },
            ) { ArtView(art, Modifier.fillMaxSize()) }
        }
    }

    val kind = s.pageKind(page)
    val last = s.pageCount

    /**
     * **쪽에 적힌 대로 움직인다** (9/21).
     *
     * "로켓에 올라탔어요" 라고 적혀 있는데 주인공이 탈것 **옆에** 서 있거나,
     * "손을 흔들었어요" 라고 적혀 있는데 가만히 서 있으면 아이가 글과 그림을 잇지 못한다.
     * 자막에 나온 낱말을 보고 자리와 몸짓을 정한다.
     *
     * "기차가 흔들렸어요"처럼 사람이 흔든 것이 아닌 문장은 인사로 읽지 않는다 — `손`·`인사`·`안녕`이 같이 있어야 한다.
     */
    val caption = if (page > 0) s.bookCaption(page) else ""
    val riding = ridingFrom(caption)
    val waving = wavingFrom(caption)
    // 인사 — 몸을 좌우로 기울인다. 손 그림이 따로 없으니 몸짓으로 보여 준다
    val waveMod = if (waving) Modifier.graphicsLayer {
        rotationZ = wobble * 1.6f
        transformOrigin = TransformOrigin(0.5f, 1f)
    } else Modifier

    @Composable
    fun Scenery(quake: Boolean = false, glow: Set<String> = if (s.isDiary) s.diaryGlow else s.mentioned.toSet()) {
        // 배경 그림 속 것들 — 새로 얹지 않고 그 자리가 반응한다 (9/17)
        HotspotLayer(
            s.bgName, glow, pulse = page, quake = quake,
            text = { h -> if (tool == "glass") h.name else if (tool == "feather") "간질간질~" else h.tap },
            onTap = { react("bg") },
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2E2A26))) {
        AssetImage(s.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(s.worldBg)))
        }
        if (kind == PageKind.SHAKE) {
            Text("쿵!", fontSize = 44.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).offset { IntOffset(shake.roundToInt(), 0) })
        }

        // 일기 모드에는 탈것(로켓 · 거북이 · 기차)도 동행 공룡도 없다 — 묻지 않는 칸이다 (일기 설계 §2-2).
        // 아이가 아무도 그리지 않은 날에는 친구 자리도 비워 둔다 — 앱이 없는 친구를 만들어 내지 않는다 (§3-2).
        val showRide = !s.isDiary
        val showDino = !s.isDiary
        // 일기 · 협업은 아이가 그린 것 → 아이가 말한 사람 순으로 세우고, 둘 다 없으면 아무도 안 세운다 (일기 §3-2)
        val friendShown: Art? = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        val showFriend = friendShown != null

        when (kind) {
            PageKind.COVER -> Cover(d, heroArt)
            PageKind.DEPART -> {
                Scenery()
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(0, wobble.roundToInt()) })
                if (showRide && riding) {
                    // 탈것 위 — 탈것과 같은 흔들림으로 함께 움직여야 "타고 있다"로 보인다.
                    // 발이 탈것 몸통에 **겹쳐야** 올라탄 것으로 보인다. 띄우면 위에 떠 있는 것처럼 보였다 (9/21)
                    Char("hero", heroArt, 0.445f, 0.120f, 0.075f, mod = Modifier.offset { IntOffset(0, wobble.roundToInt()) }.then(waveMod))
                } else {
                    Char("hero", heroArt, 0.22f, 0.30f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(waveMod))
                }
            }
            PageKind.SHAKE -> {
                Scenery(quake = true)
                if (showRide) Char("vehicle", s.rideArt, 0.34f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(shake.roundToInt(), 0) })
                Char("hero", heroArt, 0.16f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset((shake / 2).roundToInt(), 0) })
                // "창밖에서 손을 흔들고 있었어요" — 적혀 있으면 정말 흔든다
                if (friendShown != null) FadeIn { Char("friend", friendShown, 0.60f, 0.20f, 0.18f, 1f, waveMod) }
            }
            PageKind.MEET, PageKind.TALK -> {
                Scenery()
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.14f, 0.15f, 0.62f)
                Char("hero", heroArt, 0.18f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(waveMod))
                // 인사하는 쪽에서는 친구도 같이 손을 흔든다 — 한쪽만 흔들면 어색하다
                if (friendShown != null) Char("friend", friendShown, 0.58f, 0.24f, 0.18f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }.then(waveMod))
                if (s.partnerHelpLine != null && page == last - 1) {
                    Layer(0.04f, 0.40f, 0.09f) { ArtView(Art.Img(s.partner.img, Art.Emoji(s.partner.emoji)), Modifier.fillMaxSize()) }
                }
            }
            PageKind.JOURNEY -> {
                Scenery(glow = if (s.isDiary) s.diaryGlow else s.hotspots.map { it.key }.toSet())
                if (showRide) Char("vehicle", s.rideArt, 0.36f, 0.14f, 0.17f, 0.62f, Modifier.offset { IntOffset((wobble * 3).roundToInt(), wobble.roundToInt()) })
                // 여정 쪽은 늘 "타고 가는 중" 이다 — 옆에 세워 두면 걸어가는 것처럼 보인다
                if (showRide) Char("hero", heroArt, 0.405f, 0.100f, 0.075f, mod = Modifier.offset { IntOffset((wobble * 3).roundToInt(), wobble.roundToInt()) }.then(waveMod))
                else Char("hero", heroArt, 0.20f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(waveMod))
                if (friendShown != null) Char("friend", friendShown, 0.60f, 0.26f, 0.16f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) })
            }
            PageKind.FAIL -> {
                Scenery()
                Char("hero", heroArt, 0.22f, 0.32f, 0.11f)
                // 풀이 죽은 친구 — 살짝 기울고 아래로
                if (friendShown != null) Char("friend", friendShown, 0.56f, 0.34f, 0.15f, 1f, Modifier.offset { IntOffset(0, 10) }.alpha(0.85f))
                Text("…", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 170.dp).alpha(twinkle))
            }
            PageKind.RUB -> RubPage(d, stage.m1Done, heroArt, dinoArt, tool)
            PageKind.DRAG -> DragPage(d, stage.m2Done, heroArt, tool)
            PageKind.TOGETHER -> {
                Scenery(glow = if (s.isDiary) s.diaryGlow else s.hotspots.map { it.key }.toSet())
                Box(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).size(110.dp, 50.dp).alpha(twinkle)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("⭐✨⭐")), Modifier.fillMaxSize()) }
                Char("hero", heroArt, 0.10f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(waveMod))
                if (friendShown != null) Char("friend", friendShown, 0.28f, 0.26f, 0.17f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) })
                if (showDino) Char("dino", dinoArt, 0.50f, 0.20f, 0.28f, 1.35f, Modifier.offset { IntOffset(0, bob.roundToInt()) }, onHand = { d.send(Reply.Tapped("dino", s.dino.label)) })
                if (s.partnerHelpLine != null) {
                    Layer(0.80f, 0.34f, 0.10f) { ArtView(Art.Img(s.partner.img, Art.Emoji(s.partner.emoji)), Modifier.fillMaxSize()) }
                }
            }
        }

        // 위쪽 안내 한 줄 (마스코트 말풍선 대신)
        if (s.bookNote.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 46.dp)
                    .widthIn(max = 520.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("👆 ${s.bookNote}", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }

        if (page > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 64.dp, end = 64.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFFBF2).copy(alpha = 0.96f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 번갈아 짓기 — 이 쪽을 누가 지었는지 작은 표시 하나 (협업 §6). 채점처럼 보이면 안 되므로 숫자도 순위도 없다
                val author = s.pageAuthor(page)
                if (author != null) {
                    Box(Modifier.size(24.dp)) {
                        ArtView(
                            if (author == "adult") Art.Img("mk_parent", Art.Emoji("🧑")) else Art.Img("mk_child", Art.Emoji("🧒")),
                            Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(s.bookCaption(page), fontSize = 17.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).noRippleClickable { d.send(Reply.Tapped("speak", "낭독")) }) { ArtView(Art.Img("ic_speaker", Art.Emoji("🔊")), Modifier.fillMaxSize()) }
            }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("ic_hand", "✋", "hand"), Triple("ic_hammer", "🔨", "hammer"), Triple("ic_feather", "🪶", "feather"), Triple("ic_magnifier", "🔍", "glass")).forEach { (img, e, k) ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .shadow(if (tool == k) 6.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (tool == k) Sun else Color.White.copy(alpha = 0.9f))
                            .border(if (tool == k) 3.dp else 0.dp, Color.White, CircleShape)
                            .noRippleClickable { tool = k }
                            .padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) { ArtView(Art.Img(img, Art.Emoji(e)), Modifier.fillMaxSize()) }
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 76.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(last) { i -> Box(Modifier.size(if (i + 1 == page) 10.dp else 7.dp).clip(CircleShape).background(if (i + 1 == page) Sun else Color.White.copy(alpha = 0.7f))) }
                Spacer(Modifier.width(6.dp))
                Text("$page/$last", fontSize = 12.sp, color = Color.White)
            }
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            if (page > 0) RoundBtn("◀", Color.White.copy(alpha = 0.9f)) { d.send(Reply.Tapped("prev", "앞")) }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            val canNext = when (kind) { PageKind.RUB -> stage.m1Done; PageKind.DRAG -> stage.m2Done; else -> true }
            if (page == last) {
                Box(
                    Modifier.size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Sun).noRippleClickable { d.send(Reply.Tapped("next", "다음")) }.padding(7.dp)
                ) { ArtView(Art.Img("ic_books", Art.Emoji("📚")), Modifier.fillMaxSize()) }
            } else {
                RoundBtn("▶", if (canNext) Sun else Color.White.copy(alpha = 0.5f)) { if (canNext) d.send(Reply.Tapped("next", "다음")) }
            }
        }
    }
}

@Composable
private fun FadeIn(content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(900), label = "fade")
    Box(Modifier.fillMaxSize().alpha(a)) { content() }
}

@Composable
private fun Cover(d: Director, heroArt: Art) {
    val s = d.s
    Box(Modifier.fillMaxSize().background(Color(0x55000000)))
    Column(
        Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 96.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .shadow(10.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFFFBF2))
                .border(4.dp, Sun2, RoundedCornerShape(22.dp))
                .padding(horizontal = 28.dp, vertical = 10.dp)
        ) { Text("『${s.title}』", fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ArtView(heroArt, Modifier.size(96.dp, 134.dp))
            // 일기 모드 표지에는 아이가 그린 것만 선다 — 안 그렸으면 주인공만 (§2-2 · §3-2)
            val coverFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
            if (coverFriend != null) ArtView(coverFriend, Modifier.size(120.dp))
            if (!s.isDiary) ArtView(Art.DinoArt(s.dinoColor, s.dinoKey), Modifier.size(130.dp, 110.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("글 · 그림 ${s.childName} · 함께 ${s.pn}", fontSize = 13.sp, color = Color.White)
        s.template?.let { t -> Text("${t.name} · ${t.pages.size}쪽", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f)) }
    }
}

/**
 * 4쪽 미션 1 — 문지르기 (쉬움). 장면 4에서 "누가 흔들었나"에 따라 흔적 · 도구가 바뀐다.
 * 탈것 위에 붙은 것 3개를 손가락으로 문지르면 줄어들다 없어진다. 도구가 손가락을 따라온다. 공룡을 문지르면 장난 반응.
 */
@Composable
private fun RubPage(d: Director, done: Boolean, heroArt: Art, dinoArt: Art, tool: String) {
    val s = d.s
    val m = s.mission1()
    val density = LocalDensity.current.density
    val rub = remember { mutableStateListOf(0f, 0f, 0f) }
    val drops = remember { mutableStateListOf<Pair<Offset, Long>>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var fired by remember { mutableStateOf(done) }
    var gag by remember { mutableStateOf<String?>(null) }
    val allOut = done || rub.all { it >= 3f }
    val lift by animateFloatAsState(if (allOut) -30f else 0f, tween(900), label = "lift")
    LaunchedEffect(allOut) { if (allOut && !fired) { fired = true; d.send(Reply.Tapped("mission", "미션1")) } }
    LaunchedEffect(drops.size) { if (drops.isNotEmpty()) { delay(500); val now = System.currentTimeMillis(); drops.removeAll { now - it.second > 450 } } }
    LaunchedEffect(gag) { if (gag != null) { delay(1400); gag = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val vx = 0.33f; val vy = 0.22f; val vw = 0.20f; val va = 0.7f
        val vwPx = vw * wpx; val vhPx = vwPx / va
        // 흔적이 붙는 자리 (탈것 그림 안의 비율): 로켓은 아래 엔진 · 거북이 등 · 기차 지붕
        // 일기 모드는 탈것 대신 하루를 메고 다닌 가방에 흙이 묻는다 — 뼈대는 그대로, 소품만 바꾼다 (§7-1 ②)
        val fy = if (s.isDiary) 0.45f else when (s.themeKey) { "space" -> 0.72f; "sea" -> 0.36f; else -> 0.30f }
        val blobs = listOf(0.22f to fy, 0.50f to fy + 0.08f, 0.78f to fy).map { (bx, by) -> Offset(vx * wpx + bx * vwPx, vy * hpx + by * vhPx) }
        Layer(vx, vy, vw, va, Modifier.offset { IntOffset(0, lift.roundToInt()) }) { ArtView(s.rideArt, Modifier.fillMaxSize()) }
        Layer(0.08f, 0.36f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }
        val rubFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        if (rubFriend != null) Layer(0.58f, 0.30f, 0.11f, 1f) { ArtView(rubFriend, Modifier.fillMaxSize()) }
        if (!s.isDiary) Layer(0.70f, 0.36f, 0.19f, 1.35f) { ArtView(dinoArt, Modifier.fillMaxSize()) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(done) {
                    if (done) return@pointerInput
                    detectDragGestures(
                        onDragStart = { finger = it },
                        onDragEnd = { finger = null },
                        onDragCancel = { finger = null },
                    ) { change, drag ->
                        val p = change.position
                        finger = p
                        val amount = abs(drag.x) + abs(drag.y)
                        var hit = false
                        blobs.forEachIndexed { i, b ->
                            if (abs(p.x - b.x) < wpx * 0.07f && abs(p.y - b.y) < hpx * 0.14f && rub[i] < 3f) {
                                rub[i] = minOf(3f, rub[i] + amount / 200f); hit = true
                            }
                        }
                        if (hit) drops += p to System.currentTimeMillis()
                        // 목표 밖 장난 반응 — 일기 모드에는 공룡이 없으니 그 자리도 없다 (§2-2)
                        else if (!s.isDiary && p.x > wpx * 0.70f && p.y > hpx * 0.36f && gag == null) { gag = "부르르! ${s.soundLine}"; d.send(Reply.Tapped("gag", "장난")) }
                        change.consume()
                    }
                }
        ) {
            blobs.forEachIndexed { i, b ->
                val st = if (done) 3f else rub[i]
                val sz = (0.085f - 0.025f * minOf(st, 2f)) * wpx
                Box(
                    Modifier
                        .offset { IntOffset((b.x - sz / 2).roundToInt(), (b.y - sz / 2).roundToInt()) }
                        .size((sz / density).dp)
                ) {
                    if (st >= 3f) ArtView(Art.Img(m.gone, Art.Emoji(m.goneEmoji)), Modifier.fillMaxSize().alpha(0.8f))
                    else ArtView(Art.Img(m.blob, Art.Emoji(m.blobEmoji)), Modifier.fillMaxSize())
                }
            }
            drops.forEach { (p, _) ->
                Box(Modifier.offset { IntOffset((p.x - 30).roundToInt(), (p.y - 70).roundToInt()) }.size(40.dp)) { ArtView(Art.Img("prop_splash", Art.Emoji("💦")), Modifier.fillMaxSize()) }
            }
            val fp = finger
            val toolMod = if (fp != null) Modifier.offset { IntOffset((fp.x - 40).roundToInt(), (fp.y - 110).roundToInt()) }
            else Modifier.align(Alignment.BottomStart).padding(start = 70.dp, bottom = 70.dp)
            if (!allOut) Box(toolMod.size(76.dp)) { ArtView(Art.Img(m.tool, Art.Emoji(m.toolEmoji)), Modifier.fillMaxSize()) }
            gag?.let { g ->
                Box(
                    Modifier.offset { IntOffset((wpx * 0.68f).roundToInt(), (hpx * 0.30f).roundToInt()) }
                        .shadow(4.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
            }
        }
        if (allOut) {
            Box(Modifier.offset { IntOffset((vx * wpx).roundToInt(), (vy * hpx - 20).roundToInt()) }.size(120.dp, 54.dp)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize()) }
        }
    }
}

/**
 * 5쪽 미션 2 — 끌어다 놓기 (보통 · 목표 영역 넓게). 장면 10에서 아이가 말한 것을 친구에게 건넨다.
 * 친구에게 놓으면 하트가 퐁퐁, 주인공에게 놓으면 장난 반응. 미션 1에서 도와줬으면 톡 누르면 날아간다.
 */
@Composable
private fun DragPage(d: Director, done: Boolean, heroArt: Art, tool: String) {
    val s = d.s
    val m = s.mission2()
    val easy = s.m1Result == "helped"
    val scope = rememberCoroutineScope()
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var given by remember { mutableStateOf(done) }
    var sent by remember { mutableStateOf(done) }
    var gag by remember { mutableStateOf<String?>(null) }
    val inf = rememberInfiniteTransition(label = "give")
    val beat by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "beat")
    val rise by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "rise")
    LaunchedEffect(given) { if (given && !sent) { sent = true; delay(1200); d.send(Reply.Tapped("mission", "미션2")) } }
    LaunchedEffect(gag) { if (gag != null) { delay(1500); gag = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val dens = LocalDensity.current
        val itemSize: Dp = 88.dp
        val itemPx = with(dens) { itemSize.toPx() }
        val startX = wpx * 0.30f; val startY = hpx * 0.22f
        // 친구 = 목표 (넓게 판정)
        val fx = 0.60f; val fy = 0.26f; val fw = 0.19f
        val fL = fx * wpx; val fT = fy * hpx; val fS = fw * wpx
        val fCx = fL + fS / 2; val fCy = fT + fS / 2
        val hL = 0.10f * wpx; val hT = 0.36f * hpx; val hW = 0.11f * wpx

        Layer(0.10f, 0.36f, 0.11f) { Tappable({ reactionFor(s, tool, "hero") }, Modifier.fillMaxSize()) { ArtView(heroArt, Modifier.fillMaxSize()) } }
        Layer(fx, fy, fw, 1f, Modifier.scale(if (given) beat else 1f)) {
            Box(Modifier.fillMaxSize()) {
                // 미션 2는 건넬 상대가 있어야 한다. 아이가 그린 것 → 아이가 말한 사람 →
                // 둘 다 없으면 마스코트가 받는다. **없는 친구를 앱이 만들어 내지 않는다** (일기 §3-2)
                val target = s.friendOrPartnerArt ?: Art.Mascot
                Tappable({ reactionFor(s, tool, "friend") }, Modifier.fillMaxSize()) { ArtView(target, Modifier.fillMaxSize()) }
                if (!given) Box(Modifier.fillMaxSize().border(3.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)))
            }
        }
        if (given) {
            // 하트가 퐁퐁 올라간다
            repeat(3) { i ->
                val t = (rise + i / 3f) % 1f
                Box(
                    Modifier
                        .offset { IntOffset((fCx - 60 + (i - 1) * 60).roundToInt(), (fT - 20 - t * 90).roundToInt()) }
                        .size(44.dp).alpha(1f - t)
                ) { ArtView(Art.Img("prop_heart", Art.Emoji("💗")), Modifier.fillMaxSize()) }
            }
        }
        // 건넬 물건
        val atX = if (given) fCx - itemPx * 0.35f else startX + ox.value
        val atY = if (given) fCy + fS * 0.2f - itemPx * 0.35f else startY + oy.value
        Box(
            Modifier
                .offset { IntOffset(atX.roundToInt(), atY.roundToInt()) }
                .size(if (given) itemSize * 0.7f else itemSize)
                .pointerInput(easy, given) {
                    if (given) return@pointerInput
                    if (easy) {
                        detectTapGestures {
                            scope.launch { ox.animateTo(fCx - itemPx / 2 - startX, tween(700)) }
                            scope.launch { oy.animateTo(fCy - itemPx / 2 - startY, tween(700)); given = true }
                        }
                    } else {
                        detectDragGestures(onDragEnd = {
                            val cx = startX + ox.value + itemPx / 2
                            val cy = startY + oy.value + itemPx / 2
                            val pad = 60f
                            if (cx > fL - pad && cx < fL + fS + pad && cy > fT - pad && cy < fT + fS + pad) {
                                given = true
                            } else {
                                if (cx > hL - pad && cx < hL + hW + pad && cy > hT - pad) {
                                    gag = "헤헤, ${s.friendName}한테 줘야지!"
                                    d.send(Reply.Tapped("gag", "장난"))
                                }
                                scope.launch { ox.animateTo(0f, tween(400)) }
                                scope.launch { oy.animateTo(0f, tween(400)) }
                            }
                        }) { change, drag ->
                            scope.launch { ox.snapTo(ox.value + drag.x) }
                            scope.launch { oy.snapTo(oy.value + drag.y) }
                            change.consume()
                        }
                    }
                }
        ) {
            ArtView(Art.Img(m.item, Art.Emoji(m.itemEmoji)), Modifier.fillMaxSize())
            if (easy && !given) Text("톡!", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
        }
        gag?.let { g ->
            Box(
                Modifier.offset { IntOffset(hL.roundToInt(), (hT - 40).roundToInt()) }
                    .shadow(4.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * 쪽 자막에서 **탈것에 탔는지** 읽는다 (9/21).
 * `BookPageView` 안에 두면 화면 없이 검사할 수 없어서 밖으로 꺼냈다.
 */
fun ridingFrom(caption: String): Boolean =
    listOf("올라탔", "타고", "탔어", "탔습", "태우").any { it in caption }

/**
 * 쪽 자막에서 **손을 흔드는지** 읽는다.
 *
 * ⚠️ "기차가 흔들렸어요" · "거북이를 붙잡고 마구 흔들고 있었어요" 는 인사가 아니다 —
 * `손` · `인사` 가 같이 있어야 한다. 이 오탐이 없는지는 에뮬레이터에서도 확인했다 (9/21).
 */
fun wavingFrom(caption: String): Boolean =
    ("흔들" in caption && ("손" in caption || "인사" in caption)) ||
        "안녕" in caption || "인사했" in caption
