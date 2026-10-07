package com.example.finalproject_demo.ui

import com.example.finalproject_demo.demo.hasDiaryCover
import com.example.finalproject_demo.demo.coverKey
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.ShelfBook
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.openShelfMode
import com.example.finalproject_demo.demo.shelfMode
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.restoreCoopBook
import com.example.finalproject_demo.demo.playStorySound
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/*
 * bg_shelf (1344 × 768) 속 책장 칸 — 그림의 픽셀로 잰 값이다(10-07). 화면 비율로 박아 두었을 때는 화면이 바뀌면
 * 책이 칸에서 어긋났다: 폰에서 가운데보다 왼쪽으로 쏠리고 맨 위 칸이 비어 좁아 보였다 · 태블릿에서는 더 작고 더 왼쪽(10-07 종훈).
 * 이제 그림을 Crop 한 그대로 화면에 옮겨, 책이 그림의 칸 안에 선다.
 */
private const val SHELF_ART_W = 1344f
private const val SHELF_ART_H = 768f
/** 칸 안쪽 벽의 왼쪽 · 오른쪽 */
private const val SHELF_IN_L = 318f
private const val SHELF_IN_R = 1028f
/** 칸마다 (천장, 책이 서는 선반 윗면) — 위 · 가운데 · 아래 */
private val SHELF_ROWS = listOf(55f to 240f, 282f to 476f, 515f to 700f)
/** 한 칸에 몇 권 — 세 칸 × 4 = 12, 모드마다 책장 상한(SHELF_CAPACITY)과 같아 한 화면에 다 선다 */
private const val PER_ROW = 4
private const val PER_PAGE = PER_ROW * 3

/**
 * 책장 — 만든 책이 표지를 보이며 선반에 선다. 방금 만든 책은 위에서 내려와 꽂히고 "새 책!"이 붙는다.
 * 저장된 동화는 탭해서 완성된 책을 다시 읽는다.
 */
@Composable
fun ShelfView(d: Director, stage: Stage.Shelf) {
    val s = d.s
    // 책장은 모드마다 따로다(#154 · guidelines/3 §3-5) — 왼쪽 그림 탭으로 고른다. 글을 못 읽어도 방 물건과 같은 그림 · 색이다
    var mode by remember { mutableStateOf(openShelfMode(s.shelf, s.mode)) }
    val shown = s.shelf.filter { it.shelfMode(s.mode) == mode }
    var shelfPage by remember(mode) { mutableIntStateOf(0) }
    val lastShelfPage = ((shown.size - 1).coerceAtLeast(0)) / PER_PAGE
    if (shelfPage > lastShelfPage) shelfPage = lastShelfPage
    Box(Modifier.fillMaxSize().background(Color(0xFF6B4A33))) {
        AssetImage("bg_shelf", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8A6246), Color(0xFF5E4030)))))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // the picture as ContentScale.Crop lays it out: scaled to cover, centred
            val scale = maxOf(maxWidth.value / SHELF_ART_W, maxHeight.value / SHELF_ART_H)
            val ox = (maxWidth.value - SHELF_ART_W * scale) / 2
            val oy = (maxHeight.value - SHELF_ART_H * scale) / 2
            fun ax(px: Float) = (ox + px * scale).dp
            fun ay(px: Float) = (oy + px * scale).dp
            // one size for every row: 80 % of the lowest compartment, a book's 72:98 shape
            val bookH = (SHELF_ROWS.minOf { it.second - it.first } * 0.8f * scale).dp
            val bookW = bookH * (72f / 98f)
            // spread evenly inside the compartment walls — the same gap at both ends and between books
            val inner = ax(SHELF_IN_R) - ax(SHELF_IN_L)
            val gap = (inner - bookW * PER_ROW) / (PER_ROW + 1)
            val books = shown.drop(shelfPage * PER_PAGE).take(PER_PAGE)
            books.forEachIndexed { i, b ->
                val row = i / PER_ROW
                val col = i % PER_ROW
                val x = ax(SHELF_IN_L) + gap + (bookW + gap) * col
                val y = ay(SHELF_ROWS[row].second) - bookH
                // 탭을 바꾸면 다른 책이 같은 자리에 온다 — 내려오는 움직임 · 「새 책!」이 그 책을 따라가게
                key(b.savedStoryId ?: b.title) {
                    Box(Modifier.offset(x, y).size(bookW, bookH)) {
                        ShelfBookView(d, b, fresh = b.fresh && stage.fromEnd)
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 78.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SHELF_TABS.forEach { tab ->
                ShelfModeTab(tab, on = tab.mode == mode, count = s.shelf.count { it.shelfMode(s.mode) == tab.mode }) { mode = tab.mode }
            }
        }
        if (shown.isEmpty()) {
            Text("아직 만든 책이 없어요", color = Color.White, fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center))
        }
        if (lastShelfPage > 0) {
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfButton("◀", Sun, Ink) { shelfPage = (shelfPage - 1).coerceAtLeast(0) }
                Text("${shelfPage + 1}/${lastShelfPage + 1}", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                ShelfButton("▶", Sun, Ink) { shelfPage = (shelfPage + 1).coerceAtMost(lastShelfPage) }
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeltButton(FeltMustard, onClick = { d.send(Reply.Tapped("home", "처음으로")) }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(Radius.Round)) {
                Text("🏠 방으로", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (stage.fromEnd) FeltButton(WoolCream, onClick = { d.send(Reply.Tapped("parent", "부모 모드")) }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(Radius.Round)) {
                Text("👪 부모", fontSize = 16.sp, color = Ink, modifier = Modifier.padding(horizontal = 14.dp))
            }
        Box(
            Modifier
                .felt(FeltWhite.copy(alpha = 0.94f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("📚 ${shown.size}권", fontSize = 16.sp, color = Ink) }
        }
    }
}

/** 책장 탭 하나 = 모드 하나. 그림 · 색은 방의 물건 이름표와 같다(`ui/shell/Room.kt` Thing) */
private class ShelfTab(val mode: StoryMode, val name: String, val badge: String, val icon: String, val color: Color)

private val SHELF_TABS = listOf(
    ShelfTab(StoryMode.STORY, "동화", "icon_story", "🎭", FeltCoral),
    ShelfTab(StoryMode.DIARY, "그림일기", "icon_diary", "☀️", FeltSky),
    ShelfTab(StoryMode.COOP, "같이 만들기", "icon_coop", "🛋", FeltTeal),
)

/** 동그란 펠트 탭 — 고른 탭은 크고 흰 테두리. 오른쪽 위 숫자는 그 책장의 권수 */
@Composable
private fun ShelfModeTab(tab: ShelfTab, on: Boolean, count: Int, onClick: () -> Unit) {
    val size = if (on) 64.dp else 54.dp
    Box(
        Modifier.size(64.dp).semantics { contentDescription = "${tab.name} 책장 ${count}권"; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        FeltButton(
            tab.color, onClick = onClick,
            modifier = Modifier.size(size).alpha(if (on) 1f else 0.8f)
                .then(if (on) Modifier.border(4.dp, FeltWhite, CircleShape) else Modifier),
            shape = CircleShape,
        ) {
            AssetImage(tab.badge, Modifier.align(Alignment.Center).size(size * 0.66f)) {
                Text(tab.icon, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).size(22.dp)
                .felt(FeltWhite, CircleShape, lift = 2.dp, stitch = false),
            contentAlignment = Alignment.Center,
        ) { Text("$count", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ShelfButton(label: String, color: Color, textColor: Color, onClick: () -> Unit) {
    FeltButton(color, onClick = onClick, modifier = Modifier.height(48.dp)) {
        Text(label, color = textColor, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}

@Composable
private fun ShelfBookView(d: Director, b: ShelfBook, fresh: Boolean) {
    // 방금 만든 책은 위에서 내려와 통 튀며 꽂힌다
    val drop = remember { Animatable(if (fresh) -260f else 0f) }
    val tilt = remember { Animatable(if (fresh) -14f else 0f) }
    LaunchedEffect(fresh) {
        if (fresh) {
            kotlinx.coroutines.delay(500)
            drop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(fresh) {
        if (fresh) {
            kotlinx.coroutines.delay(500)
            tilt.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 120f))
        }
    }
    val inf = rememberInfiniteTransition(label = "shelf")
    val tw by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "tw")

    Box(Modifier.fillMaxSize().offset { IntOffset(0, drop.value.roundToInt()) }.rotate(tilt.value)) {
        Tappable(text = { if (b.savedStoryId != null) "『${b.title}』 다시 읽기" else "『${b.title}』" }, modifier = Modifier.fillMaxSize(), onTap = {
            b.savedStoryId?.let { d.send(Reply.Tapped("book", it)) }
        }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .background(Color(0xFFFFFBF2))
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    // 그림일기는 아이 그림이 표지다 (ui/DiaryViews.kt · #46 과 같은 한 줄 요청)
                    if (d.s.hasDiaryCover(b.coverKey())) DiaryShelfCover(d.s, b.coverKey(), Modifier.fillMaxSize())
                    else AssetImage(b.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Sun2)) }
                    // 책등 그림자
                    Box(Modifier.width(7.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
                }
                Text(
                    b.title,
                    fontSize = 9.sp, lineHeight = 11.sp, color = Ink, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 3.dp, vertical = 2.dp),
                )
            }
        }
        // 「가기 전 · 다녀온 뒤」 짝책 — 표시만, 말은 없다(아이에게 두 책을 권하지 않는다 · 협업모드_확장_설계 §2-6)
        if (CoopShelf.hasPair(d.s, b.savedStoryId)) Text("🧳", fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp))
        if (fresh) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-14).dp)
                    .scale(0.9f + 0.1f * tw)
                    .felt(FeltCoral, RoundedCornerShape(Radius.Round), lift = 2.dp, stitch = false)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text("새 책!", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            // ✏️ 이름 바꾸기는 기능이 생길 때 다시 둔다 — 「곧 생겨요」만 뜨는 버튼은 뺐다(#154)
            Text("✨", fontSize = 22.sp, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-12).dp).alpha(tw))
        }
    }
}

/** 저장 당시의 자막을 그대로 보여 주는 읽기 전용 책. 미션 결과도 이미 자막에 포함되어 있다. */
@Composable
fun SavedStoryView(d: Director, stage: Stage.SavedStory) {
    val book = stage.book
    val page = stage.index
    if (book.visuals != null) {
        val scope = rememberCoroutineScope()
        // 같이 만들기 책은 같이 만들기로 되살린다 — 쪽 구성 · 그림 재료가 동화와 다르다 (#83 · CoopBookStore.kt)
        val reader = remember(book.id) { Director(scope).apply { if (!s.restoreCoopBook(book)) s.restoreStoryBook(book) } }
        LaunchedEffect(reader, book.id, if (reader.s.mode == StoryMode.STORY) page else -1) {
            while (isActive) {
                when ((reader.awaitReply() as? Reply.Tapped)?.value) {
                    "dino", "sound" -> if (reader.s.mode == StoryMode.STORY) d.playStorySound(book)
                        else reader.playStorySound(book)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            BookPageView(reader, Stage.BookPage(page, m1Done = true, m2Done = true), savedBook = book, onReply = { reply ->
                val action = reply as? Reply.Tapped
                when (action?.value) {
                    "speak" -> if (reader.s.mode == StoryMode.STORY) d.send(reply)
                        else reader.say(if (page == 0) book.title else book.pages[page - 1].caption)
                    "next" -> d.send(if (page == book.pages.size) Reply.Tapped("close", "책장") else reply)
                    "prev" -> d.send(reply)
                    "dino", "sound" -> reader.send(reply)
                }
            })
            Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp)) {
                ShelfButton("📚 책장", Sun, Ink) { d.send(Reply.Tapped("close", "책장")) }
            }
            if (reader.s.mode == StoryMode.STORY) key(book.id, page) { StorySoundReplay(d, book, page) }
            else StorySoundReplay(reader, book, page)
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF2E2A26))) {
        AssetImage(book.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Sun2))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Text(
            if (page == 0) "『${book.title}』" else book.pages[page - 1].caption,
            color = Ink, fontSize = if (page == 0) 30.sp else 23.sp,
            lineHeight = if (page == 0) 38.sp else 34.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFFBF2).copy(alpha = 0.95f))
                .padding(24.dp),
        )
        Text("$page / ${book.pages.size}", color = Color.White, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShelfButton("📚 책장", Sun, Ink) { d.send(Reply.Tapped("close", "책장")) }
            if (page > 0) ShelfButton("◀ 앞 쪽", Sun, Ink) { d.send(Reply.Tapped("prev", "앞")) }
            if (page < book.pages.size) ShelfButton("다음 쪽 ▶", Sun, Ink) { d.send(Reply.Tapped("next", "다음")) }
        }
        if (!book.id.startsWith(com.example.finalproject_demo.demo.COOP_SHELF_ID))
            key(book.id, page) { StorySoundReplay(d, book, page) }
        else StorySoundReplay(d, book, page)
    }
}

