package com.example.finalproject_demo.demo

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

/*
 * 같이 만들기 — 부모가 [저장하기]로 확정한 이야기 · 질문을 **폰에 남긴다** (#98 🔴 10-03 실기기).
 *
 * 전에는 `coopPick` · `parentQuestions` 가 메모리에만 있어서 앱을 껐다 켜면 소파가 「아직 준비된 이야기가 없어!」였다.
 * 부모는 아침에 골라 두고 저녁에 아이가 소파를 누르는데, 그 사이 앱이 꺼지는 것이 보통이다.
 *
 * - 남기는 때: 부모 화면 [저장하기] · [삭제하기], 그리고 한 권이 끝나 비울 때(`clearParentQuestions`) — 오늘 고른 이야기가 내일 또 나오지 않게
 * - 켤 때 한 번 읽어 들인다([attach]). 쓴 질문 순서(`parentQIndex`)는 남기지 않는다 — 이야기를 시작하면 처음부터다
 * - 폰 안에만 둔다(`shared_prefs/coop_plan`). 서버에는 보내지 않는다
 */
interface CoopPlanStore {
    fun load(): Pair<CoopPick?, List<String>>?
    fun save(pick: CoopPick?, questions: List<String>)
    /** 계획 옆에 두는 것 — 가기 전 책 · 「다녀온 뒤」 상자. 붙이지 않은 저장소(옛 테스트 대역)는 아무것도 안 남긴다 */
    fun loadExtras(): CoopPlanExtras = CoopPlanExtras()
    fun saveExtras(extras: CoopPlanExtras) {}
}

/**
 * 「다녀온 뒤」 상자 한 칸 — 「곧 해요」로 지어 책장에 꽂힌 책 한 권 (협업모드_확장_설계 §2-3 ②).
 * 다녀온 뒤 부모가 [있었던 일로 준비하기]를 누르면 같은 종류 · 이름 · 「다녀왔어요」로 계획을 채운다
 */
data class CoopAfter(val kind: String, val name: String, val beforeBookId: String, val beforeTitle: String, val madeAt: String)

/** 상자에 두는 수 — 넷째가 오면 가장 오래된 것을 버린다 */
const val COOP_AFTER_MAX = 3

/**
 * [before] 지금 저장된 계획이 짝을 지을 「가기 전」 책 id — 상자에서 꺼내 저장했을 때만, 계획이 비면 같이 빈다.
 * [after] 「다녀온 뒤」 상자 — 새 것이 앞
 */
data class CoopPlanExtras(val before: String? = null, val after: List<CoopAfter> = emptyList())

class LocalCoopPlanStore(context: Context) : CoopPlanStore {
    private val prefs = context.applicationContext.getSharedPreferences("coop_plan", Context.MODE_PRIVATE)

    override fun load(): Pair<CoopPick?, List<String>>? =
        prefs.getString("plan", null)?.let { runCatching { coopPlanFromJson(it) }.getOrNull() }

    override fun save(pick: CoopPick?, questions: List<String>) {
        val e = prefs.edit()
        if (pick == null && questions.none { it.isNotBlank() }) e.remove("plan") else e.putString("plan", coopPlanToJson(pick, questions))
        e.apply()
    }

    override fun loadExtras(): CoopPlanExtras =
        prefs.getString("extras", null)?.let { runCatching { coopExtrasFromJson(it) }.getOrNull() } ?: CoopPlanExtras()

    override fun saveExtras(extras: CoopPlanExtras) {
        val e = prefs.edit()
        if (extras.before == null && extras.after.isEmpty()) e.remove("extras") else e.putString("extras", coopExtrasToJson(extras))
        e.apply()
    }
}

internal fun coopExtrasToJson(x: CoopPlanExtras): String = JSONObject()
    .put("before", x.before ?: JSONObject.NULL)
    .put("after", JSONArray().also { a ->
        x.after.forEach {
            a.put(JSONObject().put("kind", it.kind).put("name", it.name).put("beforeBookId", it.beforeBookId)
                .put("beforeTitle", it.beforeTitle).put("madeAt", it.madeAt))
        }
    })
    .toString()

/** 망가진 칸은 건너뛴다 — 상자는 부모가 다시 고르면 되는 편의라 앱을 세우지 않는다 */
internal fun coopExtrasFromJson(raw: String): CoopPlanExtras {
    val o = JSONObject(raw)
    val after = o.optJSONArray("after")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            runCatching {
                val x = a.getJSONObject(i)
                CoopAfter(x.getString("kind"), x.getString("name"), x.getString("beforeBookId"), x.getString("beforeTitle"), x.optString("madeAt", ""))
            }.getOrNull()
        }
    }.orEmpty()
    return CoopPlanExtras(if (o.isNull("before")) null else o.optString("before").ifBlank { null }, after)
}

internal fun coopPlanToJson(pick: CoopPick?, questions: List<String>): String = JSONObject()
    .put("pick", pick?.let { JSONObject().put("kind", it.kind).put("name", it.name).put("reason", it.reason ?: JSONObject.NULL) } ?: JSONObject.NULL)
    .put("questions", JSONArray().also { a -> questions.forEach { a.put(it) } })
    .toString()

internal fun coopPlanFromJson(raw: String): Pair<CoopPick?, List<String>> {
    val o = JSONObject(raw)
    val pick = o.optJSONObject("pick")?.let { p ->
        CoopPick(p.getString("kind"), p.getString("name"), p.optString("reason").takeUnless { p.isNull("reason") || it.isBlank() })
    }
    val qs = o.optJSONArray("questions")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    return pick to qs
}

object CoopPlan {
    private val stores = WeakHashMap<DemoState, CoopPlanStore>()
    /** 가기 전 책 · 「다녀온 뒤」 상자 — 부모 화면이 다시 그리도록 Compose 상태로 든다. `DemoState` 에 칸을 더하지 않는다 */
    private val extras = WeakHashMap<DemoState, MutableState<CoopPlanExtras>>()
    private fun cell(s: DemoState) = extras.getOrPut(s) { mutableStateOf(CoopPlanExtras()) }

    /** 켤 때 — 저장해 둔 이야기 · 질문을 읽어 들인다. 지금 이미 무언가 있으면(시연 서랍 등) 덮지 않는다 */
    fun attach(s: DemoState, store: CoopPlanStore) {
        stores[s] = store
        cell(s).value = runCatching { store.loadExtras() }.getOrDefault(CoopPlanExtras())
        if (s.coopPick != null || s.parentQuestions.isNotEmpty()) return
        val (pick, qs) = store.load() ?: return
        s.parentQuestions.addAll(qs)
        s.parentQIndex = 0
        s.coopPick = pick
    }

    fun attach(context: Context, s: DemoState) = attach(s, LocalCoopPlanStore(context))

    /** 지금 상태를 남긴다 — 비었으면 지운다. 계획이 비면 짝을 지을 가기 전 책도 잊는다(한 권이 끝났거나 부모가 지웠다) */
    fun saved(s: DemoState) {
        if (s.coopPick == null && s.parentQuestions.none { it.isNotBlank() } && cell(s).value.before != null) {
            set(s, cell(s).value.copy(before = null))
        }
        val store = stores[s] ?: return
        runCatching { store.save(s.coopPick, s.parentQuestions.toList()) }
    }

    // ── 「다녀온 뒤」 상자 (협업모드_확장_설계 §2) ──────────────────────────────

    /** 상자 — 새 것이 앞 */
    fun after(s: DemoState): List<CoopAfter> = cell(s).value.after

    /** 지금 저장된 계획이 짝을 지을 「가기 전」 책 id — 상자에서 꺼내 저장한 계획일 때만 */
    fun beforeBookId(s: DemoState): String? = cell(s).value.before

    /** 곧 해요 책이 꽂혔다 — 상자 맨 앞에. 같은 종류 · 이름이 있으면 새 것으로 바꾸고, [COOP_AFTER_MAX] 를 넘으면 오래된 것을 버린다 */
    fun rememberAfter(s: DemoState, a: CoopAfter) {
        val rest = after(s).filterNot { it.kind == a.kind && it.name == a.name }
        set(s, cell(s).value.copy(after = (listOf(a) + rest).take(COOP_AFTER_MAX)))
    }

    /**
     * A co-op book was removed from the shelf (#80 · #223) — its 「다녀온 뒤」 box entry and a plan waiting to pair
     * with it go too. Otherwise the parent is offered 「있었던 일로 준비하기」 for a book that no longer exists,
     * and the new book tries to pair with it. The other book's `pairId` stays as designed (read as no pair).
     */
    fun forgetBook(s: DemoState, bookId: String) {
        val x = cell(s).value
        val next = x.copy(before = x.before.takeUnless { it == bookId }, after = x.after.filterNot { it.beforeBookId == bookId })
        if (next != x) set(s, next)
    }

    /** [안 하게 됐어요] — 상자에서 뺀다 */
    fun dismissAfter(s: DemoState, a: CoopAfter) = set(s, cell(s).value.copy(after = after(s) - a))

    /** 부모가 상자의 한 권으로 「다녀왔어요」 계획을 [저장하기] 했다 — 가기 전 책을 기억하고 상자에서 뺀다 */
    fun startAfter(s: DemoState, a: CoopAfter) = set(s, CoopPlanExtras(before = a.beforeBookId, after = after(s) - a))

    private fun set(s: DemoState, x: CoopPlanExtras) {
        cell(s).value = x
        stores[s]?.let { st -> runCatching { st.saveExtras(x) } }
    }
}
