package com.example.finalproject_demo.demo

/**
 * Who-slots the judge must not fill with a whole sentence (10-09 device · Laya v5). Asked 「그때 누구를 만났어?」, the child said
 * 「눈보라가 불어서 길을 잃었어」 and the judge filled `newcomer` with it — the app then asked 「눈보라가 불어서 길을 잃었어는
 * 어떻게 생겼을까?」 and 「…의 소리를 직접 만들어 볼까?」. The model tends to fill the asked slot, so the app guards it.
 */
internal val STORY_WHO_SLOTS = setOf("newcomer", "name")

/** What the newcomer is called when the child twice answered with a sentence and no name could be taken from it */
internal const val STORY_NEW_FRIEND = "새 친구"

private fun jong(c: Char): Int = if (c in '가'..'힣') (c - '가') % 28 else -1
private const val JONG_N = 4
private const val JONG_SS = 20
private const val JONG_BS = 18

private val POLITE_END = Regex("(?:어|아|해|에|예|와|워|봐|줘)요$|니다$")
private val LINKING_END = Regex("(?:서|고|는데|니까|면서)$")
private val MEETING = Regex("^(?:만났|만나|봤|보았|나타났|나왔|왔|찾았|발견했)")
private val PARTICLE = Regex("(?:이랑|하고|을|를|이|가|랑|와|과)$")
private val NOT_ADJECTIVE = setOf('은', '는', '인', '면', '던', '만')

/**
 * Does [value] read as a sentence rather than a name — a finite predicate on the last word (「잃었어」 · 「있어요」 · 「갔다」),
 * or three words or more with a linking ending before a plain 어/아 ending (「눈보라가 불어서 길을 잃어」).
 * Names with a final 어 stay names (「보라색 문어」 · 「고등어」), and so do long names (「고슴도치처럼 생긴 바늘괴물」).
 */
internal fun storyLooksLikeSentence(value: String): Boolean {
    val words = value.trim().trimEnd('.', '!', '?', '~', '…').split(Regex("\\s+")).filter(String::isNotEmpty)
    val last = words.lastOrNull() ?: return false
    if (POLITE_END.containsMatchIn(last)) return true
    if (last.length >= 2 && last.last() in "어아다지네") {
        val before = jong(last[last.length - 2])
        if (before == JONG_SS || before == JONG_BS) return true
    }
    return words.size >= 3 && last.last() in "어아" && words.dropLast(1).any { LINKING_END.containsMatchIn(it) }
}

/**
 * The one met, from a meeting sentence — 「하얀 북극곰을 만났어」 → 「하얀 북극곰」 · 「숲에서 곰이 나타났어」 → 「곰」.
 * Null when the sentence is not about meeting someone (「눈보라가 불어서 길을 잃었어」).
 */
internal fun storyNameInSentence(value: String): String? {
    val words = value.trim().trimEnd('.', '!', '?', '~', '…').split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.size < 2 || !MEETING.containsMatchIn(words.last())) return null
    val marked = words[words.size - 2]
    val noun = PARTICLE.replace(marked, "").takeIf { it.isNotEmpty() && it != marked } ?: return null
    // 「하얀」 · 「큰」 — an adjective right before keeps the look; 「엄마는」 · 「숲에서」 do not
    val adjective = words.getOrNull(words.size - 3)?.takeIf { w ->
        jong(w.last()) == JONG_N && w.last() !in NOT_ADJECTIVE && w.length <= 3
    }
    return listOfNotNull(adjective, noun).joinToString(" ")
}
