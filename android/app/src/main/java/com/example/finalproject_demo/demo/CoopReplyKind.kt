package com.example.finalproject_demo.demo

/*
 * ── 같이 만들기 — 아이 말이 답인가, 질문인가, 부정인가 (#327 설계 · docs/같이만들기_질문답_부정답_설계.md §3) ──────────
 *
 * 지금까지는 「아니」 · 「싫어」를 「몰라」와 같은 「답 아님」으로만 보고, 아이 질문(「그게 뭐야?」)은 보통 답처럼 판정에
 * 보냈다. 판정이 칸을 못 찾으면 그 말이 사다리 끝에서 **책 칸 값**이 됐다(「그게 뭐야?」가 problem 에).
 * 여기서 오또 질문과 견줘 일곱 가지로 가른다 — 앱 규칙만(서버 판정 칸은 실기기 로그로 재 본 뒤 · ⚖️1).
 *
 * 받아쓰기라 「?」가 없을 수 있어 낱말로 본다. 잘못 갈려도 잃는 것은 질문 한 번이다 — 질문으로 갈린 말은 칸에 넣지 않는다.
 * 순수 함수 · Android 의존 없음 — 그림일기도 나중에 쓸 수 있다.
 */
internal sealed interface CoopReply {
    /** 보통 답 — 지금 길 그대로(판정 · 받아주기) */
    data object Answer : CoopReply
    /** 「몰라」 · 「응」 · 그냥 「아니」 — 지금처럼 사다리 */
    data object NonAnswer : CoopReply
    /** 오또 질문의 뜻을 묻는다 — 「뭐를 넣어?」 · 「그게 뭐야?」 · 「까닭이 뭐야」 */
    data class AboutQuestion(val text: String) : CoopReply
    /** 옆 어른에게 묻는다 — 「엄마, 우리 뭐 먹었지?」. [who] 부른 사람(없으면 null) */
    data class ToPartner(val text: String, val who: String?) : CoopReply
    /** 그 밖의 질문 — 「기린은 뭐 먹어?」 · 「오또는 어디 살아?」 */
    data class World(val text: String) : CoopReply
    /** 오또 질문의 전제를 부정 — 「안 줬어」 → stem 「줬」 · 「기린 없었어」 → stem 「없」 noun 「기린」 (대응은 #327 ②) */
    data class PremiseDenied(val stem: String, val noun: String?) : CoopReply
    /** 고쳐 말하기 — 「아니, 사과 줬어」 · 「놀이터 말고 수영장 갔어」. [denied] 부정한 앞 이름 (대응은 #327 ②) */
    data class Corrected(val instead: String, val denied: String?) : CoopReply
}

/** 아이가 오또에게 물었나 — 대답한 뒤 같은 질문을 다시 한다(§4) */
internal val CoopReply.isQuestion: Boolean get() = this is CoopReply.AboutQuestion || this is CoopReply.ToPartner || this is CoopReply.World

/** 묻는 말 — 어절 처음에서 (「뭐 · 뭘 · 뭐를 · 무엇 · 무슨 · 왜 · 어떻게 · 어디 · 누구 · 누가 · 언제 · 몇 · 어느」) */
private val ASK_WORDS = listOf("뭐", "뭘", "무엇", "무슨", "왜", "어떻게", "어떡", "어디", "누구", "누가", "누굴", "언제", "몇", "어느")

/** 물음 끝 — 끝 어절이 이것으로 끝나면 묻는 꼴 (「뭐야 · 뭐예요 · 먹니 · 하냐 · 할까 · 먹지 · 한대 · 하는데 · 먹어 · 가 · 해」) */
private val ASK_ENDS = listOf("야", "예요", "에요", "니", "냐", "나", "까", "지", "래", "는데", "어", "아", "해", "가")

/** 대답 속 「뭐」 — 「뭐 그냥 놀았어」 · 「뭐더라… 아 츄러스」 */
private val ANSWER_MWO = Regex("^뭐(\\s*그냥|더라|였지)")

/** 부르는 말 — 첫 어절이면 옆 어른에게 묻는 것 */
private val CALLS = listOf("엄마", "아빠", "할머니", "할아버지", "이모", "삼촌", "선생님", "고모", "언니", "누나", "형", "오빠")

/** 오또 질문을 가리키는 말 — 「그게 뭐야?」 · 「뭐를 넣어?」 · 「무슨 말이야?」 */
private val ABOUT_WORDS = listOf("그게", "그거", "뭐를", "뭘 말", "무슨 말", "어떻게 해", "뭐라고", "뭐 하라고", "뭐라는")

/** 「뭐야 · 뭔데 · 뭐지 · 뭐예요 · 뭔지」 — 낱말의 뜻을 묻는 끝 */
private val WHAT_IS = Regex("^(뭐야|뭔데|뭐지|뭐예요|뭐에요|뭔지|뭐니|뭐냐)$")

/** 모르겠다는 말이 든 말은 질문이 아니다 — 「어디 갔는지 기억 안 나」 */
private val DONT_KNOW_IN = Regex("몰라|모르|기억 안|기억이 안|생각 안|생각이 안")

private val HESITATION_HEAD = Regex("^(?:(?:으*음+|어+|흠+|아+|그+)[.…,~!\\s]+)+")

private fun eojeolsOf(t: String): List<String> = t.split(Regex("[\\s,.!?~·…\"“”「」]+")).filter(String::isNotEmpty)

/** 낱말 앞 두 글자 — #310 `causeAsksOtherEvent` 와 같은 잣대 */
private fun stems(t: String): Set<String> = Regex("[가-힣A-Za-z0-9]{2,}").findAll(t).map { it.value.take(2) }.toSet()

/** 「있었나」를 묻는 질문 — 「누구랑 갔어?」 · 「누굴 만났어?」 · 「누가 있었어?」. 「없어」 · 「아무도」가 답이 된다(§3-4) */
private fun asksWhetherAnyone(question: String): Boolean =
    listOf("누구", "누굴", "누가").any { it in question } && listOf("갔", "만났", "있었", "같이", "랑").any { it in question }

/**
 * 아이 말 [said] 를 오또가 물은 [question] 과 견줘 가른다(§3-2 차례 — 위가 먼저).
 * [partner] 옆에 어른이 있나(#303) — 없으면 부르는 말이 있어도 옆 어른에게 묻는 것으로 보지 않는다
 */
internal fun classifyCoopReply(said: String, question: String, partner: Boolean = true): CoopReply {
    val raw = said.trim()
    val t = raw.replace(HESITATION_HEAD, "").trim()
    val core = t.trimEnd('.', '!', '?', '~', ' ', '…')
    if (core.isEmpty()) return CoopReply.NonAnswer
    // ① 답 아님 — 단, 있었나를 묻는 질문에 「없어」 · 「아무도」는 답이다(판정이 no_longer_needed 를 낸다 · §3-4)
    if (core in setOf("없어", "없었어", "아무도", "아무도 없어", "아무도 없었어") && asksWhetherAnyone(question)) return CoopReply.Answer
    if (isNonAnswer(core)) return CoopReply.NonAnswer

    // ② 질문
    val words = eojeolsOf(core)
    val hasAsk = words.any { w -> ASK_WORDS.any { w.startsWith(it) } }
    val endsAsk = words.lastOrNull()?.let { last -> ASK_ENDS.any { last.endsWith(it) } } == true
    val answerMwo = ANSWER_MWO.containsMatchIn(core)
    val asking = !answerMwo && !DONT_KNOW_IN.containsMatchIn(core) &&
        (raw.trimEnd().endsWith("?") || (hasAsk && endsAsk && words.size <= 6))
    if (asking) {
        val first = words.first().trimEnd('야', '아')
        val call = CALLS.firstOrNull { first == it }
        // 「우리 뭐 먹었지」 · 「뭐였더라」 — 같이 겪은 일을 떠올리는 꼴(지난 일 + 지 · 더라)
        val last = words.last()
        val recall = last.endsWith("더라") || (last.endsWith("지") && last.length >= 2 && last[last.length - 2].let { c ->
            c in '가'..'힣' && (c - '가') % 28 == 20       // ㅆ 받침 — 먹었지 · 갔지 · 했지
        })
        if (partner && (call != null || recall)) return CoopReply.ToPartner(t, call)
        // 오또 질문의 낱말을 되묻는 「○○가 뭐야」 꼴 — 「까닭이 뭐야」. 같은 동사(「먹었지」)는 뜻을 묻는 것이 아니다
        val qStems = stems(question) - stems(ASK_WORDS.joinToString(" "))
        val whatIs = words.size >= 2 && WHAT_IS.matches(words.last()) && words[words.size - 2].let { w -> w.length >= 2 && w.take(2) in qStems }
        val about = ABOUT_WORDS.any { core.startsWith(it) || " $it" in " $core" } || whatIs
        return if (about) CoopReply.AboutQuestion(t) else CoopReply.World(t)
    }

    // ③ 고쳐 말하기 — 「아니, ○○」 · 「○○ 말고 ○○」 · 「○○ 아니고 ○○」 · 「그게 아니라 ○○」 (뒤가 부정이면 ④로)
    corrected(core)?.let { c -> if (negatedWord(c.instead) == null) return c }

    // ④ 전제 부정 — 부정된 낱말이 오또 질문에 있을 때만. 질문에 없는 말의 부정(「안 무서웠어」)은 보통 답
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

/** 부정된 낱말 — 「안 줬어」 → 줬어 · 「못 갔어」 → 갔어 · 「가지 않았어」 → 가지 · 「기린 없었어」 → 없었어(앞 이름 기린) */
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

/** 「줬어」 → 줬 · 「만났어」 → 만났 · 「없었어」 → 없 — 어미를 떼고 앞 두 글자까지 */
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
    // 질문에 그 동사가 있나(「줬어」 ↔ 「줬어?」), 또는 부정한 앞 이름이 질문에 있나(「기린 없었어」 ↔ 「기린한테」)
    val noun = before?.takeIf { b -> b.length >= 2 && b.take(2) in qStems }?.replace(Regex("(은|는|이|가|도|을|를|한테|에게)$"), "")
    // 동사는 어미를 뗀 줄기끼리 — 「안 줬어」 ↔ 「줬어?」 · 「안 갔었어」 ↔ 「갔어?」
    val stem = verbStem(word)
    val verbInQuestion = !stem.startsWith("없") && eojeolsOf(question).any { verbStem(it) == stem }
    if (noun == null && !verbInQuestion) return null
    return CoopReply.PremiseDenied(verbStem(word), noun)
}
