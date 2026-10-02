package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.questionHint

/*
 * 같이 만들기 — **아이에게 나가기 직전 갈무리** (10-02 · 협업 질문 업그레이드 §9).
 *
 * 템플릿 · 사다리 · 서버 LLM · 부모 질문, 네 출처가 모두 이 함수를 지나 오또 목소리로 나간다(`CoopScenes.kt` coopAskInFlow).
 * 걸리면 규칙으로 고칠 수 있는 것은 고치고, 못 고치면 null → 부르는 쪽이 그 자리의 템플릿 사다리 질문으로 돌아간다.
 *
 * 검사 순서
 *   1. 거친 · 무서운 말          → 못 고침
 *   2. 한 문장 · 물음표 하나     → 질문 앞의 말(선택지가 아닌 것)은 떼고, 물음표가 둘 이상이면 첫 질문만
 *   3. 의문사 하나 · 「언제」 없음 → 못 고침 ([questionHint] 그대로 — 부모 화면 귀띔과 같은 셈)
 *   4. 어려운 말                  → 쉬운 말로 바꿈 ([COOP_EASY_WORDS])
 *   5. 오또 말투(다정한 반말)      → 「-요?」는 「?」로, 「-습니까/-세요」는 못 고침
 *   6. 고른 이유의 시제            → 못 고침 (다녀왔어요 = 지난 일, 곧 해요 = 앞으로 할 일, 좋아해요 = 둘 다)
 *   7. 단정 없음 (다녀왔어요 · 곧 해요) → 「-구나 · -잖아 · -지?」가 질문에 있으면 못 고침
 *   6 · 7 은 **새로 만들어지는 문장**(서버 LLM · 이어 받기)에만 본다. 우리가 이유별로 써 둔 템플릿 · 사다리는 시제가 정해져 있고,
 *   「소방관은 왜 그 일을 할까?」(늘 하는 일) · 「다음엔 뭐 할까?」(다녀온 뒤 다음 계획)처럼 규칙으로는 가를 수 없는 말이 있다
 *   8. 길이                        → 못 고침
 *
 * **부모 질문은 몰래 바꾸지 않는다.** 부모 화면에서 같은 검사로 안내하고, 말하기 직전에는 2번(질문 하나)만 손본다.
 */

/**
 * 질문 하나의 길이 상한 — **공백을 뺀 글자 수**. 3세가 한 번에 듣는 길이, TTS 로 약 3초 [설계 · 실기기에서 조정].
 * 어절 수로 세지 않는다 — 「해 보고 싶은 게」처럼 띄어 쓰는 말이 많아 어절 수가 길이를 못 나타낸다
 */
const val COOP_Q_MAX_CHARS = 22

/** 선택지를 앞에 붙인 질문 전체 · 선택지 하나의 상한 (공백 뺀 글자) */
const val COOP_CHOICE_Q_MAX_CHARS = 40
const val COOP_CHOICES_MAX = 3
const val COOP_CHOICE_MAX_CHARS = 9

private fun String.len() = count { !it.isWhitespace() }

/** 질문이 어디서 왔나 — 로그와 부모 질문 처리에 쓴다 */
enum class CoopSource(val label: String) { TEMPLATE("템플릿"), LADDER("사다리"), LLM("서버 LLM"), PARENT("부모 질문"), HEARD("이어 받기") }

/**
 * 갈무리 결과. [text] 가 null 이면 내보내지 않는다(사다리로).
 * [issues] 는 걸린 항목, [changed] 는 규칙으로 고쳤는지
 */
data class CoopGuarded(val text: String?, val issues: List<String>, val changed: Boolean) {
    val ok: Boolean get() = text != null
}

/** 질문을 선택지 부분과 질문 부분으로 — 「사자 우리, 기린 마당, 원숭이 산. 어디가 좋았어?」 */
private class Split(val lead: List<String>, val question: String)

private fun split(text: String): Split? {
    val parts = Regex("[^.!?]+[.!?]+").findAll(text.trim()).map { it.value.trim() }.toList()
        .ifEmpty { return null }
    val qIdx = parts.indexOfFirst { it.endsWith("?") }
    if (qIdx < 0) return null
    return Split(parts.take(qIdx), parts[qIdx])
}

/** 선택지 문장인가 — 쉼표로 나눈 짧은 말들 + 마침표 */
private fun choicesOf(s: String): List<String>? {
    if (!s.endsWith(".")) return null
    val items = s.trimEnd('.').split(",").map { it.trim().removePrefix("아니면 ").trim() }.filter(String::isNotEmpty)
    return items.takeIf { it.size >= 2 }
}

/** 받침 ㅆ 으로 끝난 지난 꼴인가 (있 · 없은 지금 꼴이라 뺀다) */
private fun pastSyllable(c: Char): Boolean {
    if (c !in '가'..'힣' || c == '있' || c == '없') return false
    return (c - '가') % 28 == 20       // ㅆ
}

/** ㄹ 받침 (할 · 갈 · 될 · 생길 …) */
private fun rieulSyllable(c: Char): Boolean = c in '가'..'힣' && (c - '가') % 28 == 8

/** 앞으로 할 일을 묻는 꼴 — 「할까? · 될까? · 할 거야? · 할 것 같아?」. 「그랬을까?」(지난 일 짐작)는 아니다 */
private fun asksFuture(q: String): Boolean {
    val t = q.trimEnd('?', ' ')
    if (t.endsWith("까") && t.length >= 2) {
        val before = t[t.length - 2]
        if (before == '을' && t.length >= 3) return !pastSyllable(t[t.length - 3])
        return rieulSyllable(before)
    }
    // 「할 거야 · 될 것 같아」는 앞일, 「그랬을 것 같아」는 지난 일 짐작
    val m = Regex("([가-힣]+) (거야|거니|것 같아)$").find(t) ?: return false
    val w = m.groupValues[1]
    if (w.endsWith("을") && w.length >= 2) return !pastSyllable(w[w.length - 2])
    return rieulSyllable(w.last())
}

/** 「-요?」 존댓말 끝을 오또 반말로 — 「뭐예요?」→「뭐야?」 · 「갔어요?」→「갔어?」 · 「좋나요?」→「좋니?」 */
internal fun banmal(q: String): String = when {
    q.endsWith("이에요?") -> q.dropLast(4) + "이야?"
    q.endsWith("예요?") || q.endsWith("에요?") -> q.dropLast(3) + "야?"
    q.endsWith("나요?") -> q.dropLast(3) + "니?"
    q.endsWith("요?") -> q.dropLast(2) + "?"
    else -> q
}

/** 지난 일을 묻는 꼴 — 「했어? · 갔어? · 있었어?」 */
private fun asksPast(q: String): Boolean {
    val t = q.trimEnd('?', ' ')
    return t.endsWith("어") && t.length >= 2 && pastSyllable(t[t.length - 2])
}

/**
 * 오또가 말하기 직전 갈무리. [reason] 은 고른 이유(없으면 좋아해요로 친다).
 * 부모 질문은 [CoopSource.PARENT] — 질문 하나만 남기고(2번) 그대로 둔다.
 */
fun coopGuard(raw: String, reason: CoopReason?, source: CoopSource): CoopGuarded {
    val issues = mutableListOf<String>()
    var changed = false
    var text = raw.trim()
    val r = reason ?: CoopReason.DREAM

    // 1. 거친 · 무서운 말 — 앞 말을 떼기 전에 본다 (부모 질문은 부모 화면에서 안내한다)
    if (source != CoopSource.PARENT && hasRoughWord(text)) return CoopGuarded(null, issues + "거친 말", false)

    // 2. 한 문장 · 물음표 하나 (부모 질문도 이것만은 손본다)
    val sp = split(text) ?: return CoopGuarded(null, issues + "질문이 아님(물음표 없음)", false)
    val qCount = text.count { it == '?' }
    val choices = sp.lead.lastOrNull()?.let(::choicesOf)
    val keptLead = if (choices != null) listOf(sp.lead.last()) else emptyList()
    if (qCount > 1) { issues += "질문 ${qCount}개 → 첫 질문만"; changed = true }
    if (sp.lead.size > keptLead.size) { issues += "질문 앞 말 ${sp.lead.size - keptLead.size}문장 뗌"; changed = true }
    text = (keptLead + sp.question).joinToString(" ")
    if (source == CoopSource.PARENT) return CoopGuarded(text, issues, changed)

    // 4. 어려운 말 → 쉬운 말
    var q = sp.question
    COOP_EASY_WORDS.forEach { (hard, easy) -> if (hard in q) { q = q.replace(hard, easy); issues += "어려운 말 「$hard」→「$easy」"; changed = true } }

    // 3. 의문사 하나 · 「언제」 없음
    questionHint(q)?.let { h -> return CoopGuarded(null, issues + "질문 규칙: ${h.why}", changed) }

    // 5. 오또 말투 — 다정한 반말
    if (Regex("(습니까|십니까|세요|하시|드려)").containsMatchIn(q)) return CoopGuarded(null, issues + "존댓말", changed)
    if (q.endsWith("요?")) { q = banmal(q); issues += "「-요?」→ 반말"; changed = true }

    val generated = source == CoopSource.LLM || source == CoopSource.HEARD
    // 6. 시제
    if (generated && r == CoopReason.DONE && asksFuture(q)) return CoopGuarded(null, issues + "시제: 다녀왔어요인데 앞일을 물음", changed)
    if (generated && r == CoopReason.SOON && asksPast(q)) return CoopGuarded(null, issues + "시제: 곧 해요인데 지난 일을 물음", changed)

    // 7. 단정 — 아이의 실제 일에 아이가 말하지 않은 사실을 박지 않는다
    if (generated && r != CoopReason.DREAM && Regex("(구나|잖아|지\\?$|겠네)").containsMatchIn(q)) return CoopGuarded(null, issues + "단정하는 말", changed)

    // 8. 길이
    if (q.len() > COOP_Q_MAX_CHARS) return CoopGuarded(null, issues + "길이 ${q.len()}자", changed)
    val out = (keptLead + q).joinToString(" ")
    if (choices != null) {
        if (choices.size > COOP_CHOICES_MAX || choices.any { it.len() > COOP_CHOICE_MAX_CHARS } || out.len() > COOP_CHOICE_Q_MAX_CHARS)
            return CoopGuarded(null, issues + "선택지가 길다", changed)
    }
    return CoopGuarded(out, issues, changed)
}

/**
 * 부모 화면 귀띔 — 부모가 적은 질문을 **막지 않는다.** 아이가 답하기 쉬운 쪽으로 무엇이 걸렸는지와 고쳐 쓴 문장 하나를 보여 주고,
 * 부모가 [바꿀게요] 또는 [그대로 둘게요]를 고른다 (협업 질문 업그레이드 §9 · 규칙 7 「과잉 차단 금지」).
 * 「언제」 · 의문사 둘 · 예/아니오는 [questionHint] 가 이미 보여 주므로 여기서 겹쳐 말하지 않는다.
 */
data class CoopParentAdvice(val notes: List<String>, val suggestion: String?)

fun coopParentAdvice(raw: String): CoopParentAdvice? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val notes = mutableListOf<String>()
    if (hasRoughWord(t)) notes += "아이에게 무섭거나 거칠게 들릴 수 있는 말이 있어요"
    if (t.count { it == '?' } > 1) notes += "질문이 ${t.count { it == '?' }}개예요 — 오또는 첫 질문만 물어요"
    // 오또가 실제로 물을 문장(첫 질문만) — 거기서 쉬운 말 · 반말만 바꿔 본다
    var q = coopGuard(t, null, CoopSource.PARENT).text ?: t
    COOP_EASY_WORDS.forEach { (hard, easy) -> if (hard in q) { q = q.replace(hard, easy); notes += "「$hard」보다 「$easy」가 아이에게 쉬워요" } }
    if (q.endsWith("요?")) { q = banmal(q); notes +="오또는 반말로 물어요 — 「-요」를 빼면 오또 말투와 맞아요" }
    if (q.len() > COOP_Q_MAX_CHARS) notes += "조금 길어요 — 짧을수록 아이가 잘 답해요"
    if (notes.isEmpty()) return null
    return CoopParentAdvice(notes, q.takeIf { it != t })
}
