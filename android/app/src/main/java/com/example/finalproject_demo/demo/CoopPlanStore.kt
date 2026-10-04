package com.example.finalproject_demo.demo

import android.content.Context
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
}

class LocalCoopPlanStore(context: Context) : CoopPlanStore {
    private val prefs = context.applicationContext.getSharedPreferences("coop_plan", Context.MODE_PRIVATE)

    override fun load(): Pair<CoopPick?, List<String>>? =
        prefs.getString("plan", null)?.let { runCatching { coopPlanFromJson(it) }.getOrNull() }

    override fun save(pick: CoopPick?, questions: List<String>) {
        val e = prefs.edit()
        if (pick == null && questions.none { it.isNotBlank() }) e.remove("plan") else e.putString("plan", coopPlanToJson(pick, questions))
        e.apply()
    }
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

    /** 켤 때 — 저장해 둔 이야기 · 질문을 읽어 들인다. 지금 이미 무언가 있으면(시연 서랍 등) 덮지 않는다 */
    fun attach(s: DemoState, store: CoopPlanStore) {
        stores[s] = store
        if (s.coopPick != null || s.parentQuestions.isNotEmpty()) return
        val (pick, qs) = store.load() ?: return
        s.parentQuestions.addAll(qs)
        s.parentQIndex = 0
        s.coopPick = pick
    }

    fun attach(context: Context, s: DemoState) = attach(s, LocalCoopPlanStore(context))

    /** 지금 상태를 남긴다 — 비었으면 지운다 */
    fun saved(s: DemoState) {
        val store = stores[s] ?: return
        runCatching { store.save(s.coopPick, s.parentQuestions.toList()) }
    }
}
