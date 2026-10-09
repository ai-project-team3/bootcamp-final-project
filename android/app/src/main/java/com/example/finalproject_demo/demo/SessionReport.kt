package com.example.finalproject_demo.demo

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * ── 부모 리포트 「오늘의 기록」 (10-06 종훈 · 시안 report-canvas) ──────────────────────────────
 *
 * 리포트가 이 서비스가 놀이인지 아닌지를 가른다. 그래서 숫자 칸만이 아니라 **아이가 지은 이야기의 뼈대**와
 * **오늘 보인 순간(원문 + 그 말이 무엇을 보여 주는지)**, 그리고 **대화 전체**를 남긴다.
 *
 * 규칙 5 — 출처를 끝까지 나눈다: 아이가 한 말은 따옴표, 카드로 고른 것은 「카드로 고름」, 오또가 채운 것은 흐리게.
 *    오또가 채운 것은 숫자에 하나도 들어가지 않는다.
 * 규칙 9 — 점수 · 등급 · 또래 비교 없음. 횟수와 원문만. 아래 문장은 전부 미리 쓴 것이다(LLM 문장 없음).
 *
 * 세션 중에는 [DemoState.talk] 에 한 줄씩 쌓이고(`Director.event` · `say` · `signal`), 책을 꽂을 때
 * [SessionReports.keep] 이 그 책 id 로 폰에 남긴다 — 책장에서 다시 열 수 있게. 서버로는 보내지 않는다.
 */

/** 대화 한 줄. who: otto · child(말) · card · draw · mascot(오또가 대신 채움) · adult */
enum class ReportQuestionType(val key: String) {
    OPEN("open"), REASON("reason"), CHOICE("choice"), CONFIRMATION("confirmation"), UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String): ReportQuestionType = entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

data class TalkLine(
    val who: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val questionType: ReportQuestionType = ReportQuestionType.UNKNOWN,
)

/** 이야기 뼈대 한 칸. by: child · card · mascot. quoted — text 가 아이가 한 말 그대로일 때만 따옴표를 친다 */
data class ReportBone(val label: String, val text: String, val by: String, val quoted: Boolean = false)

/** 오늘 보인 순간 — kind 는 [MOMENT_MEANING] 의 열쇠 */
data class ReportMoment(val kind: String, val quote: String)

data class SessionReport(
    val bookId: String,
    val mode: String,              // story · diary · coop
    val title: String,
    val bgName: String,
    val day: String,               // ISO 날짜
    val minutes: Int?,             // 잰 것만 — 첫 질문부터 책을 꽂을 때까지
    val exchanges: Int,            // 아이가 답한 횟수(말 · 카드 · 그림) — 오또가 채운 것은 빠진다
    val spoken: Int,               // 말로 한 횟수
    val longestWords: Int,         // 가장 길게 한 말의 낱말 수
    val bones: List<ReportBone>,
    val moments: List<ReportMoment>,
    val talk: List<TalkLine>,
    val homeQuestion: String,
    /** 이번 리포트가 처음 보여 준 것 — 꽂을 때 지난 리포트들과 견줘 정한다(다른 아이가 아니라 지난번의 이 아이와만) */
    val firsts: List<String> = emptyList(),
) {
    val childBones: Int get() = bones.count { it.by == "child" }
    val longest: String? get() = talk.filter { it.who == "child" }.maxByOrNull { words(it.text) }?.text
    val modeName: String get() = when (mode) { "diary" -> "그림일기"; "coop" -> "같이 만들기"; else -> "동화" }
}

const val MOMENT_REASON = "reason"
const val MOMENT_ADD = "add"
const val MOMENT_FEEL = "feel"

/** 순간마다 이름과, 그 말이 무엇을 보여 주는지 — 발달 단계 이름 · 점수가 아니라 그 말이 한 일 (규칙 9) */
val MOMENT_MEANING = mapOf(
    MOMENT_REASON to ("까닭 말하기" to "스스로 까닭을 붙였어요. 원인과 결과를 잇는 말은 이야기를 이해하고 설명하는 힘이 자라고 있다는 신호예요."),
    MOMENT_ADD to ("이야기 보태기" to "묻지 않은 것을 스스로 더했어요. 듣는 사람에게 더 알려 주고 싶은 마음, 이야기를 이어 가는 힘이에요."),
    MOMENT_FEEL to ("마음 말하기" to "마음을 낱말로 나타냈어요. 감정을 말로 부르는 것은 마음을 다루는 첫걸음이에요."),
)

const val HOME_TIP = "대답을 기다려 주고, 아이 말을 한 번 따라 말해 주세요"

internal fun words(t: String) = t.trim().split(Regex("\\s+")).count { it.isNotBlank() }

/** 까닭을 잇는 말 — 서버 신호가 없을 때(대본 · 받아쓰기만 된 말) 아이 말에서 직접 본다 */
private val REASON = Regex("왜냐|때문에|니까|라서")
/** 마음 낱말 — 아이가 한 말에 들어 있을 때만 (지어내지 않는다) */
private val FEEL = Regex("무서|무섭|슬퍼|슬펐|기뻐|기뻤|좋았|행복|화나|화났|속상|괜찮아|신나|신났|외로|심심|놀랐|걱정|부끄|미안|고마|싫어|아파|웃었")

/** 뼈대 칸 — 이야기를 이루는 것. 값이 있는 칸만 보인다(빠진 칸을 빈칸으로 늘어놓지 않는다 · 규칙 2) */
private val BONES = listOf(
    "place" to "어디서", "newcomer" to "누가", "companion" to "누구랑", "problem" to "무슨 일",
    "cause" to "왜", "solution" to "어떻게 끝나", "reaction" to "그래서 마음은",
)

private fun DemoState.boneValue(key: String): String? = (slots[key] ?: when (key) {
    "place" -> place; "newcomer" -> newcomer; "problem" -> problem; "cause" -> cause; "solution" -> solution; "reaction" -> reaction
    else -> null
})?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("{") }

/** 지금 세션으로 리포트를 만든다 — 책을 꽂을 때와, 아직 안 꽂은 오늘 세션을 부모 화면이 볼 때 */
fun DemoState.buildSessionReport(bookId: String = "", now: Long = System.currentTimeMillis()): SessionReport {
    val lines = talk.toList().map { l ->
        if (l.who != "child") l else {
            val extra = buildList {
                if (MOMENT_REASON !in l.tags && REASON.containsMatchIn(l.text)) add(MOMENT_REASON)
                if (MOMENT_FEEL !in l.tags && (FEEL.containsMatchIn(l.text) || feelings.any { it.isNotBlank() && it in l.text })) add(MOMENT_FEEL)
            }
            if (extra.isEmpty()) l else l.copy(tags = l.tags + extra)
        }
    }
    val child = lines.filter { it.who == "child" }
    val usedLines = mutableSetOf<String>()
    val bones = BONES.mapNotNull { (k, label) ->
        val v = boneValue(k) ?: return@mapNotNull null
        val by = slotBy[k] ?: "child"
        // 아이가 말한 칸은 그 칸을 채운 아이 말 그대로 — 서버가 줄인 값보다 원문이 낫다(값이 원문 안에 있을 때만).
        // 한 마디가 두 칸을 채웠으면 두 번째 칸은 그 마디 안의 값만 — 같은 문장을 되풀이하지 않는다
        val said = if (by == "child") child.firstOrNull { v in it.text && it.text !in usedLines }?.text else null
        said?.let { usedLines += it }
        // a value the child said inside a line already shown is still the child's words; a paraphrase is not quoted
        val inLine = by == "child" && said == null && child.any { v in it.text }
        ReportBone(label, said ?: v, by, quoted = said != null || inLine)
    }
    val moments = listOf(MOMENT_REASON, MOMENT_ADD, MOMENT_FEEL).mapNotNull { k ->
        child.firstOrNull { k in it.tags }?.let { ReportMoment(k, it.text) }
    }
    val started = talkStartedAtMs.takeIf { it > 0L } ?: diaryStart.takeIf { it > 0L }
    val minutes = started?.let { ((now - it) / 60_000L).toInt().coerceAtLeast(1) }
    val t = title ?: autoTitleFor()
    return SessionReport(
        bookId = bookId,
        mode = when { isCoop -> "coop"; isDiary -> "diary"; else -> "story" },
        title = t, bgName = bgName, day = LocalDate.now().toString(), minutes = minutes,
        exchanges = lines.count { it.who == "child" || it.who == "card" || it.who == "draw" },
        spoken = child.size,
        longestWords = child.maxOfOrNull { words(it.text) } ?: 0,
        bones = bones, moments = moments, talk = lines,
        homeQuestion = homeQuestion(t, bones),
    )
}

/** 집에서 이어 갈 질문 한 개 — 미리 쓴 틀에 오늘 책의 말만 넣는다 (LLM 없음 · 규칙 9) */
private fun DemoState.homeQuestion(t: String, bones: List<ReportBone>): String {
    val n = childName
    val problem = bones.firstOrNull { it.label == "무슨 일" && it.by == "child" }
    return when {
        isCoop -> "“오늘 만든 『$t』에서 제일 좋았던 장면은 어디야? 한 번 더 들려줄래?”"
        isDiary -> "“오늘 일기에 그린 것 중에 제일 재밌었던 게 뭐였어? ${n}${eun(n)} 내일은 뭐 하고 싶어?”"
        problem != null -> "“『$t』에서 그런 일이 생겼잖아. ${n}${eun(n)} 그럴 때 누가 와 주면 좋겠어?”"
        else -> "“『$t』 다음 이야기는 어떻게 될까? 이번엔 ${n}${ga(n)} 먼저 들려줄래?”"
    }
}

/** 지난 리포트들과 견줘 처음 보인 것만 고른다 — 다른 아이가 아니라 지난번의 이 아이와 (규칙 9) */
fun SessionReport.withFirsts(before: List<SessionReport>): SessionReport {
    if (before.isEmpty()) return this
    val firsts = buildList {
        val kinds = before.flatMap { r -> r.moments.map { it.kind } }.toSet()
        moments.filter { it.kind !in kinds }.forEach { m ->
            add("오또와 만든 책에서 처음으로 「${MOMENT_MEANING[m.kind]!!.first}」가 보였어요")
        }
        val longestBefore = before.maxOf { it.longestWords }
        if (longestWords > longestBefore && longestBefore > 0) add("지금까지 가장 긴 말을 했어요 — ${longestWords}낱말")
    }
    return copy(firsts = firsts)
}

// ── JSON ─────────────────────────────────────────────────────────

private fun List<String>.json() = JSONArray().also { a -> forEach { a.put(it) } }
private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

fun SessionReport.toJson(): JSONObject = JSONObject()
    .put("bookId", bookId).put("mode", mode).put("title", title).put("bgName", bgName).put("day", day)
    .put("minutes", minutes ?: JSONObject.NULL).put("exchanges", exchanges).put("spoken", spoken)
    .put("longestWords", longestWords).put("homeQuestion", homeQuestion).put("firsts", firsts.json())
    .put("bones", JSONArray().also { a -> bones.forEach { a.put(JSONObject().put("label", it.label).put("text", it.text).put("by", it.by).put("quoted", it.quoted)) } })
    .put("moments", JSONArray().also { a -> moments.forEach { a.put(JSONObject().put("kind", it.kind).put("quote", it.quote)) } })
    .put("talk", JSONArray().also { a -> talk.forEach { a.put(JSONObject().put("who", it.who).put("text", it.text).put("tags", it.tags.json()).put("questionType", it.questionType.key)) } })

fun sessionReportOf(j: JSONObject): SessionReport {
    fun objs(name: String) = j.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    return SessionReport(
        bookId = j.getString("bookId"), mode = j.getString("mode"), title = j.getString("title"),
        bgName = j.optString("bgName"), day = j.optString("day"),
        minutes = if (j.isNull("minutes")) null else j.optInt("minutes"),
        exchanges = j.optInt("exchanges"), spoken = j.optInt("spoken"), longestWords = j.optInt("longestWords"),
        bones = objs("bones").map { ReportBone(it.getString("label"), it.getString("text"), it.getString("by"), it.optBoolean("quoted")) },
        moments = objs("moments").map { ReportMoment(it.getString("kind"), it.getString("quote")) },
        talk = objs("talk").map { TalkLine(it.getString("who"), it.getString("text"), it.optJSONArray("tags").strings(), ReportQuestionType.fromKey(it.optString("questionType"))) },
        homeQuestion = j.optString("homeQuestion"),
        firsts = j.optJSONArray("firsts").strings(),
    )
}

/**
 * 책마다 리포트 한 장 — 폰 안에만(`session_reports`). 책을 빼면 같이 지운다. 탈퇴하면 `LocalWipe` 가 지운다.
 * 붙이지 않으면(테스트 · 저장소 없음) 앱을 켜 둔 동안만 남는다.
 */
object SessionReports {
    private const val KEEP = 40
    private var prefs: SharedPreferences? = null
    private val reports = mutableListOf<SessionReport>()   // 새 것이 앞

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("session_reports", Context.MODE_PRIVATE)
        reload()
    }

    @Synchronized fun reload() {
        reports.clear()
        val raw = prefs?.getString("reports", null) ?: return
        runCatching { val a = JSONArray(raw); (0 until a.length()).forEach { reports += sessionReportOf(a.getJSONObject(it)) } }
    }

    @Synchronized fun all(): List<SessionReport> = reports.toList()
    @Synchronized fun of(bookId: String): SessionReport? = reports.firstOrNull { it.bookId == bookId }
    @Synchronized fun latest(): SessionReport? = reports.firstOrNull()

    /** 꽂은 책의 리포트를 남긴다 — 지난 리포트와 견줘 「처음 해낸 것」을 정한 뒤 */
    @Synchronized fun keep(s: DemoState, bookId: String): SessionReport? = try { keepOrThrow(s, bookId) } catch (e: Exception) {
        null   // a report never stops a book from being shelved
    }

    private fun keepOrThrow(s: DemoState, bookId: String): SessionReport {
        val r = s.buildSessionReport(bookId).withFirsts(reports.filter { it.bookId != bookId })
        reports.removeAll { it.bookId == bookId }
        reports.add(0, r)
        while (reports.size > KEEP) reports.removeAt(reports.size - 1)
        save()
        s.lastReport = r
        return r
    }

    /**
     * The book was removed from the shelf — its report goes too. When it is the book just made, the parent
     * screen's copy and this session's transcript go with it, so 「오늘의 기록」 does not keep showing a removed book (#223)
     */
    @Synchronized fun forget(bookId: String, s: DemoState? = null) {
        if (reports.removeAll { it.bookId == bookId }) save()
        if (s != null && s.lastReport?.bookId == bookId) { s.lastReport = null; s.talk.clear() }
    }

    /** 테스트 · 탈퇴 뒤 — 메모리에 든 것도 비운다 */
    @Synchronized fun clear() { reports.clear(); prefs?.edit()?.clear()?.apply() }

    private fun save() { prefs?.edit()?.putString("reports", JSONArray().also { a -> reports.forEach { a.put(it.toJson()) } }.toString())?.apply() }
}
