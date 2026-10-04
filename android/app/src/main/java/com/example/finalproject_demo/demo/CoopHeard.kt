package com.example.finalproject_demo.demo

/*
 * 같이 만들기 — 아이 답에서 **이름 하나**를 떼어 다음 질문에 끼운다 (10-02 · 협업 질문 업그레이드 §8).
 *
 * 규칙
 * - 아이 문장 자체를 활용형으로 바꾸지 않는다. 「불이 났어」에서는 이름 「불」만 쓴다
 * - 이름 자리는 질문 틀의 `{place:에서}` · `{thing:가}` 꼴이다. 조사는 받침에 맞춰 붙인다(`Model.kt` 조사 함수)
 * - 이름을 못 떼면(서술어 · 너무 긴 말 · 「몰라」 · 「아직 못 들은 …」) null — 부르는 쪽이 지금의 고정 질문으로 돌아간다
 * - 공용 도구(`PictureDiary.kt` [pieceNameFrom] · [you])는 불러 쓰기만 한다. 협업에 맞춰 더 다듬을 것은 이 파일에서 감싼다
 */

/** 이름이 나온 자리 — 자리마다 이름이 놓이는 모양이 다르다 */
enum class CoopRole {
    /** 「큰 소방서에서 일해」 → 큰 소방서 */
    PLACE,
    /** 「엄마랑 나」 → 엄마랑 너 · 「아빠랑 갔어」 → 아빠 */
    WHO,
    /** 「불이 났어」 → 불 (문제의 주인공) */
    THING,
    /** 해결 · 꼬리질문 — 받아주기에만 쓴다 */
    ANY,
}

/** 끼울 이름의 길이 한도 — 13자 이상이거나 4어절 넘으면 이름이 아니라 문장이다 */
private const val NAME_MAX_CHARS = 12
private const val NAME_MAX_WORDS = 3

/** 앞에 붙는 군말 (`PictureDiary.kt` 의 같은 목록은 private — 협업 쪽에 따로 둔다) */
private val COOP_FILLER = Regex("^(음+|어+|아+|저기|그러니까|그니까|그냥|있잖아|이제)[.…,~! ]+")

/** 「-이」로 끝나는 이름 — 「고양이야」의 「이」를 떼면 「고양」이 된다 */
private val NOUNS_ENDING_IN_I = setOf("고양이", "원숭이", "호랑이", "오이", "아이", "거북이", "멍멍이", "야옹이", "토끼인형")

/** 이름이 아닌 짧은 말 — [isNonAnswer] 가 못 거르는 것 */
private val NOT_NAMES = setOf("그거", "이거", "저거", "그냥", "아무거나", "몰라요", "없어요")

private fun clean(raw: String): String {
    var t = raw.trim().trimEnd('.', '!', '?', '~', ' ')
    while (true) { val n = COOP_FILLER.replace(t, "").trim(); if (n == t) break; t = n }
    return t
}

/** 「수영장이요」 · 「유치원이야」 → 받침 뒤 「이」 + 끝말을 함께 뗀다. 「고양이야」는 「고양이」 그대로 */
private fun withoutCopula(t: String): String {
    val m = Regex("^(.+?)(이)?(야|에요|예요|이에요|요)$").find(t) ?: return t
    val stem = m.groupValues[1]
    val hasI = m.groupValues[2].isNotEmpty()
    return when {
        hasI && bat(stem) && (stem + "이") !in NOUNS_ENDING_IN_I -> stem
        hasI -> stem + "이"
        else -> stem
    }
}

/** 자리마다 이름이 놓이는 앞부분 */
private fun front(t: String, role: CoopRole): String = when (role) {
    CoopRole.PLACE -> Regex("^(.+?)(에서|에)(\\s.*)?$").find(t)?.groupValues?.get(1) ?: t
    CoopRole.WHO -> {
        // 「엄마랑 나」처럼 나를 함께 말하면 통째로(→ 「엄마랑 너」), 아니면 「X랑 …」의 X
        val two = Regex("^(\\S+)(랑|이랑|하고|와|과) (나|저|내가|제가)$").find(t)
        if (two != null) t else Regex("^(\\S+?)(이랑|랑|하고|와|과)(\\s.*)?$").find(t)?.groupValues?.get(1) ?: t
    }
    CoopRole.THING -> {
        val ga = Regex("^(.+?)가\\s").find(t)?.groupValues?.get(1)
        val i = Regex("^(.+?)이\\s").find(t)?.groupValues?.get(1)?.let { s -> if ((s + "이") in NOUNS_ENDING_IN_I) s + "이" else s }
        ga ?: i ?: t
    }
    CoopRole.ANY -> t
}

/**
 * 아이 답에서 이름 하나. 못 떼면 null.
 * 「유치원이야」→유치원 · 「수영장이요」→수영장 · 「음 그러니까 공원」→공원 · 「불이 났어」(THING)→불 · 「엄마랑 나」(WHO)→엄마랑 너
 */
fun coopNameFrom(raw: String, role: CoopRole): String? {
    val t = clean(raw)
    if (t.isEmpty() || isNonAnswer(t) || dontKnow(t) || t.startsWith("아직")) return null
    val part = withoutCopula(front(t, role).trim())
    // 남은 다듬기(「이건 …」 · 「…인데」 · 서술어 거르기)는 공용 도구에 맡긴다
    val name = pieceNameFrom(Reply.Spoke(part))?.trim() ?: return null
    if (name.isEmpty() || name in NOT_NAMES || isNonAnswer(name)) return null
    if (name.length > NAME_MAX_CHARS || name.split(" ").size > NAME_MAX_WORDS) return null
    if (looksLikeAPhrase(name)) return null
    return if (role == CoopRole.WHO) you(name) else name
}

/**
 * 이름이 아니라 말 토막인가 — 「사람 도와줄 거」 · 「도구를 챙길」 · 「불 끄는 것」.
 * 이런 것을 끼우면 「사람 도와줄 거가 왜 그럴까?」가 된다(10-02 흐름 검사에서 실제로 나왔다).
 * 「큰 소방서」 · 「엄마랑 나」처럼 꾸미는 말이 붙은 이름은 지나간다
 */
private fun looksLikeAPhrase(name: String): Boolean {
    val words = name.split(" ")
    val last = words.last()
    // 서술어 — 받침 ㅆ(했 · 었 · 왔 · 렸)이 들었거나 말끝이 문장 꼴. 실기기 #98 「기다렸어구나!」
    if (last.any { c -> c in '가'..'힣' && (c - '가') % 28 == 20 }) return true
    if (Regex("(요|줘|봐|려)$").containsMatchIn(last)) return true      // 「바다 · 할머니 · 노래」는 지나간다
    if (last in setOf("거", "것", "게", "데", "때", "줄", "수")) return true
    if (Regex("(을|를)$").containsMatchIn(name.substringBeforeLast(" ", ""))) return true      // 앞 어절에 목적어 → 문장
    if (words.size == 1) return Regex("(을|를|기|고|서|면|지만)$").containsMatchIn(last)
    // 앞 어절이 ㄹ 받침으로 끝나면 「도와줄 · 챙길 · 갈」 같은 꾸밈 — 다음은 이름이 아니라 「거 · 곳」이 오기 쉽다
    return words.dropLast(1).any { w -> w.lastOrNull()?.let { c -> c in '가'..'힣' && (c - '가') % 28 == 8 } == true }
}

/** `{이름:조사}` — 조사 이름은 받침 없는 꼴로 적는다 (가 · 을 · 은 · 랑 · 와 · 로 · 에서 · 아) */
private val SLOT = Regex("\\{([a-z]+)(?::([^}]+))?\\}")   // 자바 \w 는 한글을 받지 않는다

private fun particle(name: String, p: String): String = when (p) {
    "가", "이" -> ga(name)
    "을", "를" -> eul(name)
    "은", "는" -> eun(name)
    "랑", "이랑" -> rang(name)
    "와", "과" -> wa(name)
    "로", "으로" -> ro(name)
    "아", "야" -> ya(name)
    else -> p                     // 에서 · 에 · 도 · 만 — 받침과 상관없다
}

/**
 * 질문 틀에 들은 이름을 끼운다. 자리 하나라도 비면 null — 반쯤 채운 질문은 내보내지 않는다.
 * 「{place:에서} 무슨 일이 생겼어?」 + place=큰 소방서 → 「큰 소방서에서 무슨 일이 생겼어?」
 */
fun coopFill(template: String, heard: Map<String, String>): String? {
    var missing = false
    val out = SLOT.replace(template) { m ->
        val name = heard[m.groupValues[1]]?.takeIf(String::isNotBlank)
        if (name == null) { missing = true; "" }
        else name + (m.groupValues[2].takeIf(String::isNotEmpty)?.let { particle(name, it) } ?: "")
    }
    return if (missing) null else out
}
