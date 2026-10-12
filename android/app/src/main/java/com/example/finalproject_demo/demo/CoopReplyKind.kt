package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason

/*
 * ── Co-op — is the child's reply an answer, a question or a negation (#327 design · docs/같이만들기_질문답_부정답_설계.md §3) ──────────
 *
 * Until now 「아니」 · 「싫어」 were only non-answers like 「몰라」, and a child's question (「그게 뭐야?」) went to the judge as a normal
 * answer. When the judge found no slot, that question became the **book slot value** at the ladder's end (「그게 뭐야?」 in problem).
 * Here a reply is sorted into seven kinds against Otto's question — app rules only (a judge field waits for device logs · ⚖️1).
 *
 * Speech-to-text may drop the 「?」, so words are read. A wrong sort costs one question — a reply sorted as a question never fills a slot.
 * Pure functions · no Android dependency — the picture diary can use them later.
 */
internal sealed interface CoopReply {
    /** A normal answer — the current path (judge · ack) */
    data object Answer : CoopReply
    /** An answer after recalling — 「엄마 우리 누구랑 갔지? 아 할머니!」 → only 「할머니」 is the answer (and all the judge sees) (#341) */
    data class AnswerAfterRecall(val answer: String) : CoopReply
    /** 「몰라」 · 「응」 · a bare 「아니」 — the ladder, as now */
    data object NonAnswer : CoopReply
    /** Asks what Otto's question means — 「뭐를 넣어?」 · 「그게 뭐야?」 · 「까닭이 뭐야」 */
    data class AboutQuestion(val text: String) : CoopReply
    /**
     * Recalling something shared — 「엄마, 우리 뭐 먹었지?」 · 「누구랑 갔더라」 (#341 · was ToPartner).
     * Even when shaped as a question to the adult nearby, Otto does not call the adult and gives it back to the child (user decision 10-08 — no more parent involvement).
     * [who] the person called — logged only, never said
     */
    data class Recall(val text: String, val who: String?) : CoopReply
    /** Any other question — 「기린은 뭐 먹어?」 · 「오또는 어디 살아?」 */
    data class World(val text: String) : CoopReply
    /** Denies the premise of Otto's question — 「안 줬어」 → stem 「줬」 · 「기린 없었어」 → stem 「없」 noun 「기린」 (handled in #327 ②) */
    data class PremiseDenied(val stem: String, val noun: String?) : CoopReply
    /** A correction — 「아니, 사과 줬어」 · 「놀이터 말고 수영장 갔어」. [denied] the name denied before it (handled in #327 ②) */
    data class Corrected(val instead: String, val denied: String?) : CoopReply
}

/** Did the child ask Otto, or is the child recalling — answer, then ask again (§4). Never into a slot */
internal val CoopReply.isQuestion: Boolean get() = this is CoopReply.AboutQuestion || this is CoopReply.Recall || this is CoopReply.World

/** Ask words — at the start of an eojeol (「뭐 · 뭘 · 뭐를 · 무엇 · 무슨 · 왜 · 어떻게 · 어디 · 누구 · 누가 · 언제 · 몇 · 어느」) */
private val ASK_WORDS = listOf("뭐", "뭘", "무엇", "무슨", "왜", "어떻게", "어떡", "어디", "누구", "누가", "누굴", "언제", "몇", "어느")

/**
 * Starts with an ask word but is an answer — giving a reason with 「왜냐(하)면 · ~냐면」, 「언제나 · 어느 날」 (#332 lead review: the cause
 * step's most common answer, 「왜냐면 배고파서 그랬어」, was sorted as a question)
 */
private val ANSWER_FORMS = Regex("왜냐|냐면|언제나|어느\\s*날|어느날")

/** 「뭐」 inside an answer — 「뭐 그냥 놀았어」 · 「뭐더라… 아 츄러스」 */
private val ANSWER_MWO = Regex("^뭐(\\s*그냥|더라|였지)")

/** Calling words — as the first eojeol, the child called the adult nearby (logged only) */
private val CALLS = listOf("엄마", "아빠", "할머니", "할아버지", "이모", "삼촌", "선생님", "고모", "언니", "누나", "형", "오빠")

/** Words pointing at Otto's question — 「그게 뭐야?」 · 「뭐를 넣어?」 · 「무슨 말이야?」 */
private val ABOUT_WORDS = listOf("그게", "그거", "뭐를", "뭘 말", "무슨 말", "어떻게 해", "뭐라고", "뭐 하라고", "뭐라는")

/** 「뭐야 · 뭔데 · 뭐지 · 뭐예요 · 뭔지」 — endings that ask what a word means */
private val WHAT_IS = Regex("^(뭐야|뭔데|뭐지|뭐예요|뭐에요|뭔지|뭐니|뭐냐)$")

/** A reply saying the child does not know is not a question — 「어디 갔는지 기억 안 나」 */
private val DONT_KNOW_IN = Regex("몰라|모르|기억 안|기억이 안|생각 안|생각이 안")

private val HESITATION_HEAD = Regex("^(?:(?:으*음+|어+|흠+|아+|그+)[.…,~!\\s]+)+")

private fun eojeolsOf(t: String): List<String> = t.split(Regex("[\\s,.!?~·…\"“”「」]+")).filter(String::isNotEmpty)

/** The first two letters of each word — the same yardstick as #310 `causeAsksOtherEvent` */
private fun stems(t: String): Set<String> = Regex("[가-힣A-Za-z0-9]{2,}").findAll(t).map { it.value.take(2) }.toSet()

private fun Char.hasSsangSiot() = this in '가'..'힣' && (this - '가') % 28 == 20

/** Ends in the past — ㅆ under the letter before the last of the last eojeol (「났어 · 먹었어 · 갔어」). A past-tense statement is an answer */
private fun endsInPast(word: String): Boolean = word.length >= 2 && word[word.length - 2].hasSsangSiot()

/** Recalling endings — 「~더라」 · past + 「지」 (「먹었지 · 갔지」) */
private fun recallEnd(word: String): Boolean = word.endsWith("더라") || (word.endsWith("지") && endsInPast(word))

private fun hasAskWord(words: List<String>) = words.any { w -> ASK_WORDS.any { w.startsWith(it) } }

/** A question about whether anyone was there — 「누구랑 갔어?」 · 「누굴 만났어?」 · 「누가 있었어?」. 「없어」 · 「아무도」 answer it (§3-4) */
private fun asksWhetherAnyone(question: String): Boolean =
    listOf("누구", "누굴", "누가").any { it in question } && listOf("갔", "만났", "있었", "같이", "랑").any { it in question }

/**
 * Words after a recalling phrase, if any — 「엄마 우리 누구랑 갔지? 아 할머니!」 → 「할머니」 ·
 * 「뭐 먹었더라 츄러스」 → 「츄러스」. Null otherwise
 */
private fun answerAfterRecall(t: String): String? {
    val words = eojeolsOf(t)
    val i = words.indexOfFirst(::recallEnd)
    if (i < 0 || i == words.size - 1 || !hasAskWord(words.subList(0, i + 1))) return null
    val rest = words.subList(i + 1, words.size).joinToString(" ").replace(HESITATION_HEAD, "").trim()
        .let { r -> if (r.startsWith("아 ")) r.removePrefix("아 ") else r }
    return rest.takeIf { it.isNotEmpty() && !isNonAnswer(it) }
}

/**
 * Sorts the child's reply [said] against Otto's [question] (§3-2 order — top first).
 * Speech-to-text often drops the 「?」, so words are read — but narrowly, **so that casual statements are not taken as questions** (#332 lead review):
 * without 「?」 it is a question only if an ask word comes first · three eojeols or fewer · not ending in the past (or 「○○가 뭐야」)
 */
internal fun classifyCoopReply(said: String, question: String): CoopReply {
    val raw = said.trim()
    val t = raw.replace(HESITATION_HEAD, "").trim()
    val core = t.trimEnd('.', '!', '?', '~', ' ', '…')
    if (core.isEmpty()) return CoopReply.NonAnswer
    // ① Non-answer — except 「없어」 · 「아무도」 to a question about whether anyone was there (§3-4)
    if (core in setOf("없어", "없었어", "아무도", "아무도 없어", "아무도 없었어") && asksWhetherAnyone(question)) return CoopReply.Answer
    // Found the answer while recalling — only that answer (#341)
    answerAfterRecall(t)?.let { return CoopReply.AnswerAfterRecall(it) }
    if (isNonAnswer(core)) return CoopReply.NonAnswer

    // ② Question · recalling
    val words = eojeolsOf(core)
    val notAsking = ANSWER_MWO.containsMatchIn(core) || DONT_KNOW_IN.containsMatchIn(core) || ANSWER_FORMS.containsMatchIn(core)
    if (!notAsking && hasAskWord(words) && recallEnd(words.last())) {
        val first = words.first().trimEnd('야', '아')
        return CoopReply.Recall(t, CALLS.firstOrNull { first == it })
    }
    val marked = raw.trimEnd().endsWith("?")
    val askFirst = ASK_WORDS.any { words.first().startsWith(it) }
    val whatIsLast = WHAT_IS.matches(words.last())
    val asking = !notAsking && (marked || (words.size <= 3 && (askFirst || whatIsLast) && !endsInPast(words.last())))
    if (asking) {
        // 「○○가 뭐야」 asking about a word of Otto's question — 「까닭이 뭐야」. The same verb (「먹었지」) is not asking what it means
        val qStems = stems(question) - stems(ASK_WORDS.joinToString(" "))
        val whatIs = words.size >= 2 && whatIsLast && words[words.size - 2].let { w -> w.length >= 2 && w.take(2) in qStems }
        val about = ABOUT_WORDS.any { core.startsWith(it) || " $it" in " $core" } || whatIs
        return if (about) CoopReply.AboutQuestion(t) else CoopReply.World(t)
    }

    // ③ Correction — 「아니, ○○」 · 「○○ 말고 ○○」 · 「○○ 아니고 ○○」 · 「그게 아니라 ○○」 (if what follows is a negation, ④)
    corrected(core)?.let { c -> if (negatedWord(c.instead) == null) return c }

    // ④ Premise denied — only when the negated word is in Otto's question. Negating something not asked (「안 무서웠어」) is a normal answer
    premiseDenied(core, question)?.let { return it }
    return CoopReply.Answer
}

private val NO_HEAD = Regex("^아니(?:야|요|에요)?[,\\s]+(.+)$")
private val NOT_THIS = Regex("^(.+?)\\s*(?:말고|아니고|아니라)\\s+(.+)$")
private val THAT_NOT = Regex("^그게\\s*아니(?:라|고)[,\\s]+(.+)$")

private fun corrected(core: String): CoopReply.Corrected? {
    THAT_NOT.find(core)?.let { return CoopReply.Corrected(it.groupValues[1].trim(), null) }
    NOT_THIS.find(core)?.let { m ->
        val denied = m.groupValues[1].trim().split(Regex("\\s+")).last()
        return CoopReply.Corrected(m.groupValues[2].trim(), denied.takeIf { it.isNotEmpty() })
    }
    NO_HEAD.find(core)?.let { m -> m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { return CoopReply.Corrected(it, null) } }
    return null
}

/** The negated word — 「안 줬어」 → 줬어 · 「못 갔어」 → 갔어 · 「가지 않았어」 → 가지 · 「기린 없었어」 → 없었어 (name before it: 기린) */
private fun negatedWord(core: String): Pair<String, String?>? {
    val w = eojeolsOf(core)
    w.forEachIndexed { i, x ->
        if ((x == "안" || x == "못") && i + 1 < w.size) return w[i + 1] to null
        if (x.startsWith("없었") || x == "없어" || x.startsWith("없어요")) return x to w.getOrNull(i - 1)
        if (x.startsWith("않았") || x.startsWith("않아")) return (w.getOrNull(i - 1) ?: x) to null
        if (x.startsWith("아니야") || x.startsWith("아닌데") || x.startsWith("아니었")) return x to w.getOrNull(i - 1)
    }
    return null
}

/** 「줬어」 → 줬 · 「만났어」 → 만났 · 「없었어」 → 없 — the ending dropped, up to the first two letters */
private fun verbStem(word: String): String {
    if (word.startsWith("없")) return "없"
    val bare = listOf("었어요", "았어요", "어요", "아요", "었어", "았어", "어", "아", "요", "다", "지").fold(word) { acc, e ->
        if (acc.length > e.length && acc.endsWith(e) && acc == word) acc.dropLast(e.length) else acc
    }
    return bare.take(2)
}

private fun premiseDenied(core: String, question: String): CoopReply.PremiseDenied? {
    val (word, before) = negatedWord(core) ?: return null
    val qStems = stems(question)
    // Is that verb in the question (「줬어」 ↔ 「줬어?」), or the name denied before it (「기린 없었어」 ↔ 「기린한테」)
    val noun = before?.takeIf { b -> b.length >= 2 && b.take(2) in qStems }?.replace(Regex("(은|는|이|가|도|을|를|한테|에게)$"), "")
    // Verbs are compared by stem without the ending — 「안 줬어」 ↔ 「줬어?」 · 「안 갔었어」 ↔ 「갔어?」
    val stem = verbStem(word)
    val verbInQuestion = !stem.startsWith("없") && eojeolsOf(question).any { verbStem(it) == stem }
    if (noun == null && !verbInQuestion) return null
    return CoopReply.PremiseDenied(verbStem(word), noun)
}

// ── Negation (#327 ② · design §5) ─────────────────────────────────────────────

private val NO_HEAD_ONLY = Regex("^아니(?:야|요|에요)?[,\\s]+")
private val LINKING = listOf("서", "고", "니까", "는데", "면")

/**
 * The ack for an answer that denies the premise of Otto's question — 「구나」 on the negated last eojeol (「아니, 안 줬어」 → 「안 줬구나!」 ·
 * 「기린 없었어」 → 「기린 없었구나!」). The same safety rules as `pastEcho` (five eojeols or fewer · no linking ending · no rough word).
 * Null when it cannot be made — the caller says 「그랬구나!」. Never 「우와!」 · 「응응!」 (§5-1)
 */
internal fun negationAck(said: String): String? {
    val t = said.trim().replace(NO_HEAD_ONLY, "").trimEnd('.', '!', '~', ' ', '?')
    val words = t.split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty() || words.size > 5 || hasRoughWord(t)) return null
    if (words.dropLast(1).any { w -> LINKING.any { w.endsWith(it) } }) return null
    val last = words.last()
    if (!last.endsWith("어") || last.startsWith("아니")) return null
    return t.dropLast(1) + "구나!"
}

/**
 * A premise-free question — open, with no name slot and no choices (§5-2). Asked once after the child denied the premise of Otto's question.
 * Null for the cause of 곧 해요 (the template question stays) and for steps other than the four skeleton steps
 */
internal fun coopPremiseFree(key: String, reason: CoopReason): String? {
    val soon = reason == CoopReason.SOON
    val dream = reason == CoopReason.DREAM
    return when (key) {
        "place" -> if (soon) "그럼 어디 갈 거야?" else if (dream) "그럼 어디로 가 볼까?" else "그럼 어디 갔었어?"
        "problem" -> if (soon) "그럼 거기서 뭘 할 거야?" else if (dream) "그럼 무슨 일이 생겼을까?" else "그럼 거기서 무슨 일이 있었어?"
        "cause" -> if (soon) null else if (dream) "그럼 무엇 때문에 그랬을까?" else "그럼 무엇 때문에 그런 일이 생겼을까?"
        "solution" -> if (soon) "그럼 그다음엔 어떻게 할 거야?" else if (dream) "그럼 그다음엔 어떻게 됐을까?" else "그럼 그다음엔 어떻게 됐어?"
        else -> null
    }
}
