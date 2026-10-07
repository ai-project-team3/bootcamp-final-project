package com.example.finalproject_demo.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ── 업적 보상 — 받으면 그림판에서 정말 쓴다 (10-06 종훈 · #223) ─────────────────────────────
 *
 * 전에는 업적이 이야기 한 권 동안만 있었고(`resetStory` 가 지웠다) 부모 업적 탭에 보이기만 했다.
 * 이제 받은 보상은 폰에 남고(`rewards`), 다음 책의 그림판에 도구로 나온다.
 *
 * 받는 조건은 **아이가 한 일**뿐이다 — 많이 말한 것 · 빨리 한 것 · 잘한 것에는 주지 않는다(조사3 §1-3 · 규칙 9).
 * 도구로 그린 것도 보통 선(`Stroke`)이라 책 · 책장 · 저장 형식은 그대로다: 무지개는 색이 바뀌는 짧은 선들,
 * 도장은 별 · 하트 모양 선 하나.
 */
enum class Reward(val key: String, val emoji: String, val title: String, val how: String) {
    RAINBOW("rainbow", "🌈", "무지개 크레용", "내가 그린 그림을 처음 책에 넣었어요"),
    GOLD("gold", "✨", "반짝이 크레용", "그림일기를 처음 끝까지 만들었어요"),
    HEART("heart", "💗", "하트 도장", "어른과 같이 만든 책을 처음 꽂았어요"),
    BIG("big", "🖌", "굵은 붓", "내 목소리로 소리를 처음 만들었어요"),
    STAR("star", "⭐", "별 도장", "책을 세 권 만들었어요"),
}

/** 별 도장을 주는 권수 */
const val STAR_STAMP_BOOKS = 3

object Rewards {
    private var prefs: SharedPreferences? = null
    /** 받은 보상 — 화면이 바로 다시 그리도록 상태 목록 */
    val owned = mutableStateListOf<Reward>()
    /** 받고 아직 그림판에서 안 써 본 것 — 그림판이 「새로」 표시를 단다 */
    val fresh = mutableStateListOf<Reward>()
    private val days = mutableMapOf<Reward, String>()
    var booksMade = 0
        private set

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("rewards", Context.MODE_PRIVATE)
        reload()
    }

    @Synchronized fun reload() {
        owned.clear(); fresh.clear(); days.clear(); booksMade = 0
        val p = prefs ?: return
        Reward.entries.forEach { r ->
            p.getString("day_${r.key}", null)?.let { owned += r; days[r] = it }
            if (p.getBoolean("fresh_${r.key}", false)) fresh += r
        }
        booksMade = p.getInt("books", 0)
    }

    fun has(r: Reward) = r in owned
    fun dayOf(r: Reward): String? = days[r]

    /** 받는다. 이미 있으면 아무 일도 없다 — 처음 받았을 때만 true */
    @Synchronized fun grant(r: Reward): Boolean {
        if (r in owned) return false
        val today = LocalDate.now().toString()
        owned += r; fresh += r; days[r] = today
        prefs?.edit()?.putString("day_${r.key}", today)?.putBoolean("fresh_${r.key}", true)?.apply()
        return true
    }

    /** 그림판에서 한 번 골랐다 — 「새로」 표시를 뗀다 */
    @Synchronized fun tried(r: Reward) {
        if (fresh.remove(r)) prefs?.edit()?.putBoolean("fresh_${r.key}", false)?.apply()
    }

    /** 책을 꽂았다 — 모드마다 그 모드의 첫 책 보상, 세 권째에 별 도장. 새로 받은 것을 돌려준다 */
    @Synchronized fun bookShelved(mode: StoryMode, coop: Boolean): List<Reward> {
        booksMade++
        prefs?.edit()?.putInt("books", booksMade)?.apply()
        return listOfNotNull(
            Reward.GOLD.takeIf { mode == StoryMode.DIARY && !coop && grant(it) },
            Reward.HEART.takeIf { coop && grant(it) },
            Reward.STAR.takeIf { booksMade >= STAR_STAMP_BOOKS && grant(it) },
        )
    }

    /** 테스트 · 시연 서랍 「처음부터」 */
    @Synchronized fun clear() { prefs?.edit()?.clear()?.apply(); reload() }
}

// ── 그림판 도구 ───────────────────────────────────────────────────

/** 반짝이 크레용의 색 */
val GOLD_CRAYON = Color(0xFFE0A526)

/** 무지개 크레용이 도는 색 — 크레용 12색에서 고른 일곱 */
val RAINBOW_COLORS = listOf(
    Color(0xFFE8604C), Color(0xFFF08A3C), Color(0xFFF3C33C), Color(0xFF7FBF4D),
    Color(0xFF3F7BD9), Color(0xFF6C63C9), Color(0xFFD96BA8),
)

/** 무지개 선은 이만큼(그림판 폭에 대한 비율) 지날 때마다 다음 색으로 */
const val RAINBOW_STEP = 0.035f

/** 굵은 붓 — 보통 붓의 몇 배 */
const val BIG_BRUSH = 2.6f

/** 도장 크기 — 그림판 폭에 대한 지름 */
const val STAMP_SIZE = 0.09f

/** 별 도장 한 번 — 다섯 꼭짓점 별 테두리. [c] 는 픽셀 중심, [r] 은 픽셀 반지름 */
fun starStamp(c: Offset, r: Float): List<Offset> = (0..10).map { i ->
    val rr = if (i % 2 == 0) r else r * 0.45f
    val a = -PI / 2 + i * PI / 5
    Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
}

/** 하트 도장 한 번 — 하트 곡선 테두리 */
fun heartStamp(c: Offset, r: Float): List<Offset> = (0..32).map { i ->
    val t = 2 * PI * i / 32
    val x = 16 * sin(t) * sin(t) * sin(t)
    val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
    Offset(c.x + (x / 17 * r).toFloat(), c.y - (y / 17 * r).toFloat())
}
