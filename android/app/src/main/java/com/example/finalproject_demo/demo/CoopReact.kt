package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason

/*
 * 같이 만들기 — **받아주기 한마디 · 엉뚱한 답 되돌리기** (10-02 · 협업 질문 업그레이드 §5 · §7 · §8 · §10).
 *
 * 한 턴 = 받아주기 한마디 + 질문 하나 (두 문장을 넘지 않는다).
 * - 받아주기는 아이 말에서 뗀 **이름 하나**를 되짚는다 — 「큰 소방서!」 · 「아빠구나!」.
 *   그 이름이 문장의 주어면(「소방차가 왔어」) 「그랬구나!」 — 이름만 되짚으면 일어난 일을 놓친다.
 *   아이 문장을 활용형으로 바꾸지 않는다(「불이 크니까구나!」 같은 말이 나오지 않게). 이름이 없으면 「그랬구나!」 한마디
 *   · **좁은 예외 (10-07 치영 · #304 2)** — 이름이 없는 답이 짧은 지난 일 말이면 끝만 바꿔 되비춘다
 *     (「츄러스 사 먹었어」 → 「츄러스 사 먹었구나!」). 조건 다섯이 모두일 때만: 5어절 이하 · 끝이 었어 · 았어 · 했어 · 였어(줄임꼴 줬어 · 갔어 포함) ·
 *     이음 끝(서 · 고 · 니까 · 는데 · 면) 없음 · 1인칭 없음 · 거친 말 없음 — 위 실패(이음 끝 + 구나)가 나지 않게 짰다. 밖이면 맞장구([pastEcho])
 * - 「몰라」 · 「응」 같은 짧은 답은 되비추지 않는다
 * - 엉뚱한 답(상상 낱말)은 고치지 않는다
 *   · 좋아해요(상상): 이야기에 섞는다 — 이름을 되짚고 다음 질문이 그 이름을 이어 받는다
 *   · 다녀왔어요 · 곧 해요(아이의 실제 일): 「공룡이면 깜짝 놀라겠다!」 하고 「진짜로는 …?」으로 한 번만 되돌린다. 또 그러면 그대로 받는다
 */

/** 이름이 없을 때의 받아주기 — 질문도 재촉도 넣지 않는다 */
private val PLAIN_ACKS = listOf("그랬구나!", "우와!", "응응!")

/** 이름이 문장의 주어일 때의 받아주기 */
internal const val SUBJECT_ACK = "그랬구나!"

/** [name] 이 [text] 에서 주어 자리인가 — 이름 뒤에 「이 · 가」가 붙고 뒤에 말이 더 있다(「불이 났어」 · 「공이 하늘로 날아갔어」) */
private fun isSubjectIn(name: String, text: String): Boolean =
    Regex("(^|\\s)${Regex.escape(name)}(이|가)\\s+\\S").containsMatchIn(text)

/** 아이 말의 상상 낱말 — 「공룡이 불을 뿜었어」 → 공룡 */
internal fun fantasyWordIn(text: String): String? =
    text.split(Regex("[\\s,.!?~]+")).firstNotNullOfOrNull { w ->
        COOP_FANTASY_WORDS.sortedByDescending { it.length }.firstOrNull { f ->
            if (f.length == 1) Regex("^$f(이|가|은|는|을|를|이랑|랑|도|아|야)?$").matches(w) else w.startsWith(f)
        }
    }

/**
 * 다녀왔어요 · 곧 해요에 상상 낱말이 나왔나 — 「진짜로는 …?」으로 한 번 되돌릴 자리.
 * 좋아해요(상상)는 언제나 false — 상상 이야기에는 그대로 섞는다
 */
internal fun isWildForReality(text: String, reason: CoopReason): Boolean =
    reason != CoopReason.DREAM && fantasyWordIn(text) != null

/**
 * 받아주기 한마디. [role] 은 이 자리에서 이름이 놓이는 모양, [last] 는 바로 앞 받아주기. 되비추지 않을 답이면 null.
 * 다녀왔어요 · 곧 해요의 엉뚱한 답이면 「공룡이면 깜짝 놀라겠다!」
 */
internal fun coopAck(text: String, role: CoopRole?, reason: CoopReason, wild: Boolean = false, last: String? = null): String? =
    ackFor(text, role, reason, wild)?.let { a ->
        // 바로 앞 받아주기와 같은 말이면 다른 맞장구로 — 「그랬구나!」가 이어 나오지 않게 (10-02 사용자 결정)
        if (a == last) PLAIN_ACKS.first { it != last } else a
    }

private fun ackFor(text: String, role: CoopRole?, reason: CoopReason, wild: Boolean): String? {
    val t = text.trim()
    if (t.isEmpty() || isNonAnswer(t) || dontKnow(t)) return null
    if (wild) fantasyWordIn(t)?.let { f -> return "$f${if (bat(f)) "이면" else "면"} 깜짝 놀라겠다!" }
    val name = (role?.let { coopNameFrom(t, it) } ?: coopNameFrom(t, CoopRole.THING) ?: coopNameFrom(t, CoopRole.ANY))
        ?.takeIf { !hasRoughWord(it) && it.length <= 8 }
    return when {
        name == null -> pastEcho(t) ?: PLAIN_ACKS.random()
        // 이름이 문장의 주어면(「사자가 문을 열고 나왔어」) 이름만 되짚으면 일어난 일을 놓친다 — 맞장구로 받는다 (10-02 사용자 결정)
        isSubjectIn(name, t) -> SUBJECT_ACK
        // 「엄마랑 너」처럼 이어진 말은 되짚기만 — 「구나」를 붙이면 어색하다
        " " in name -> "$name!"
        else -> "$name${if (bat(name)) "이구나" else "구나"}!"
    }
}

/**
 * 끝 어절이 지난 일 말인가 — 었어 · 았어 · 했어 · 였어와 그 줄임꼴(줬어 · 갔어 · 봤어 · 왔어)은 모두 「어」 앞 글자에 ㅆ 받침이 있다.
 * 「있어」(지금 있는 것) · 「겠어」(짐작)는 지난 일이 아니라 뺀다
 */
private fun endsInPast(word: String): Boolean {
    if (word.length < 2 || !word.endsWith("어")) return false
    val c = word[word.length - 2]
    if (c !in '가'..'힣' || c == '있' || c == '겠') return false
    return (c - '가') % 28 == 20                                   // 받침 ㅆ
}

/** 묻는 말 — 이것이 든 말은 되비추지 않는다 */
private val ECHO_ASK_WORDS = listOf("뭐", "뭘", "무엇", "무슨", "왜", "어떻게", "어디", "누구", "누가", "누굴", "언제", "몇", "어느")

/** 이음 끝 — 이것으로 끝나는 어절이 있으면 이어진 문장이라 되비추지 않는다(「크니까 무서웠어」) */
private val LINK_ENDS = listOf("서", "고", "니까", "는데", "면")

/** 1인칭 — 「나 · 내가 · 저 · 제가 · 우리」로 시작하는 어절 */
private val FIRST_PERSON = Regex("^(나|내가|내|나는|나도|나랑|저|저는|제가|우리.*)$")

/**
 * 이름이 없는 짧은 지난 일 답을 끝만 바꿔 되비춘다 — 「츄러스 사 먹었어」 → 「츄러스 사 먹었구나!」(위 머리말의 좁은 예외).
 * 조건 다섯 중 하나라도 어긋나면 null
 */
internal fun pastEcho(text: String): String? {
    val t = text.trim().trimEnd('.', '!', '~', ' ')
    if (t.isEmpty() || t.any { it in ".!?,~" }) return null           // 한 문장만
    val words = t.split(Regex("\\s+"))
    if (words.size > 5) return null
    // 묻는 말이 든 말은 되비추지 않는다 — 받아쓰기가 「?」를 빼면 「뭐 먹었어」가 「뭐 먹었구나!」가 됐다 (#327)
    if (words.any { w -> ECHO_ASK_WORDS.any { w.startsWith(it) } }) return null
    if (!endsInPast(words.last())) return null
    if (words.dropLast(1).any { w -> LINK_ENDS.any { w.endsWith(it) } }) return null
    if (words.any { FIRST_PERSON.matches(it) }) return null
    if (hasRoughWord(t)) return null
    return t.dropLast(1) + "구나!"
}

/** 엉뚱한 답 다음 — 같은 자리를 「진짜로는」으로 한 번 다시 묻는다. 시제는 고른 이유를 따른다 */
internal fun coopRedirect(stepKey: String, reason: CoopReason): String {
    fun t(done: String, soon: String) = if (reason == CoopReason.SOON) soon else done
    return when (stepKey) {
        "place" -> t("진짜로는 어디 갔었어?", "진짜로는 어디 갈 거야?")
        "companion" -> t("진짜로는 누구랑 갔어?", "진짜로는 누구랑 갈 거야?")
        "cause" -> t("진짜로는 왜 그랬을까?", "진짜로는 왜 그럴까?")
        "solution" -> t("진짜로는 그다음에 뭐 했어?", "진짜로는 그다음에 뭐 할까?")
        else -> t("진짜로는 무슨 일이 있었어?", "진짜로는 무슨 일이 생길까?")
    }
}
