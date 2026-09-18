package com.example.finalproject_demo.ui

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
import com.example.finalproject_demo.demo.Stage
import kotlin.math.roundToInt

/** 선반 윗면의 높이(화면 비율) — bg_shelf 그림의 선반 두 칸에 맞춘다 */
private val SHELF_Y = listOf(0.645f, 0.985f)   // bg_shelf를 가로 화면에 Crop했을 때 가운데 · 아래 선반 윗면

/**
 * 책장 — 만든 책이 표지를 보이며 선반에 선다. 방금 만든 책은 위에서 내려와 꽂히고 "새 책!"이 붙는다.
 * 책을 눌러도 다시 읽기는 아직 없다 (살짝 흔들리기만 · v0.8 범위).
 */
@Composable
fun ShelfView(d: Director, stage: Stage.Shelf) {
    val s = d.s
    Box(Modifier.fillMaxSize().background(Color(0xFF6B4A33))) {
        AssetImage("bg_shelf", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8A6246), Color(0xFF5E4030)))))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bookW = 72.dp
            val bookH = 98.dp
            val books = s.shelf.take(8)
            books.forEachIndexed { i, b ->
                val row = if (i < 4) 0 else 1
                val col = if (i < 4) i else i - 4
                val x = maxWidth * 0.262f + (bookW + 22.dp) * col
                val y = maxHeight * SHELF_Y[row] - bookH
                Box(Modifier.offset(x, y).size(bookW, bookH)) {
                    ShelfBookView(d, b, fresh = b.fresh && stage.fromEnd)
                }
            }
        }
        // 오른쪽 아래 버튼
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (stage.fromEnd) {
                ShelfButton("👪 부모 모드", Color(0xFF4A423A), Color.White) { d.send(Reply.Tapped("parent", "부모 모드")) }
                ShelfButton("🏠 처음으로", Sun, Ink) { d.send(Reply.Tapped("home", "처음으로")) }
            } else {
                ShelfButton("◀ 돌아가기", Sun, Ink) { d.send(Reply.Tapped("home", "돌아가기")) }
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) { Text("📚 ${s.shelf.size}권", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ShelfButton(t: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .shadow(6.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(t, fontSize = 17.sp, color = fg, fontWeight = FontWeight.Bold) }
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
        Tappable(text = { if (fresh) "『${b.title}』" else "다시 읽기는 곧 만나요!" }, modifier = Modifier.fillMaxSize(), onTap = { d.send(Reply.Tapped("book", b.title)) }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .background(Color(0xFFFFFBF2))
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AssetImage(b.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Sun2)) }
                    // 책등 그림자
                    Box(Modifier.width(7.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
                }
                Text(
                    b.title,
                    fontSize = 9.sp, lineHeight = 11.sp, color = Ink, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 3.dp, vertical = 2.dp),
                )
            }
        }
        if (fresh) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-14).dp)
                    .scale(0.9f + 0.1f * tw)
                    .clip(RoundedCornerShape(999.dp)).background(Coral)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text("새 책!", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            // 책 이름은 나중에 책장에서 바꿀 수 있다 — 자리만 둔다
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = 16.dp).size(28.dp)
                    .shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
                    .border(2.dp, Sun, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Tappable(text = { "이름 바꾸기는 곧 생겨요" }, modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("✏️", fontSize = 13.sp) } } }
            Text("✨", fontSize = 22.sp, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-12).dp).alpha(tw))
        }
    }
}

