package com.example.finalproject_demo.demo

/**
 * Who-slots the judge must not fill with a whole sentence (10-09 device · Laya v5). Asked 「그때 누구를 만났어?」, the child said
 * 「눈보라가 불어서 길을 잃었어」 and the judge filled `newcomer` with it — the app then asked 「눈보라가 불어서 길을 잃었어는
 * 어떻게 생겼을까?」 and 「…의 소리를 직접 만들어 볼까?」. The model tends to fill the asked slot, so the app guards it.
 */
internal val STORY_WHO_SLOTS = setOf("newcomer", "name")

/** What the newcomer is called when the child twice answered with a sentence and no name could be taken from it */
internal const val STORY_NEW_FRIEND = "새 친구"

/**
 * A guarded sentence is kept under its own key (`whoSaid:01` …) with its own source, and [storyServerInput] adds it to
 * `extra` like a template answer — mixed child · mascot contributions keep their sources apart (rule 5 · #375 review)
 */
internal const val WHO_SAID_PREFIX = "whoSaid:"

private fun jong(c: Char): Int = if (c in '가'..'힣') (c - '가') % 28 else -1
private const val JONG_N = 4
private const val JONG_SS = 20
private const val JONG_BS = 18

/** Polite endings — 「있어요」 · 「보여요」 · 「내려요」 · 「가요」 · 「놀아요」 · 「했어요」 · 「갑니다」. 「요요」 stays a name */
private val POLITE_END = Regex("(?:어|아|해|에|예|와|워|봐|줘|여|려|가|서|세|래|대|네|내|돼|져|쳐|켜)요$|니다$")
private val LINKING_END = Regex("(?:서|고|는데|니까|면서)$")
private val MEETING = Regex("^(?:만났|만나|봤|보았|나타났|나왔|왔|찾았|발견했)")
/** Particles that cannot be the end of the noun itself — 이 is handled apart ([stripSubjectI]) */
private val PARTICLE = Regex("(?:하고|을|를|와|과|가|랑)$")
private val NOT_ADJECTIVE = setOf('은', '는', '인', '면', '던', '만')
/**
 * A word with no particle is a bare noun only when it does not end like a linked verb or a place — 「열고」 (문을 열고
 * 나왔어) · 「집에서」 · 「공원에」 · 「숲으로」 are not who was met (#375 re-review). Such a sentence is asked again
 */
private val NOT_BARE_NOUN = Regex("(?:고|서|에|에서|으로|로|며|는데|니까)$")

/** Nouns whose own last syllable is 이 — 「고양이 봤어」 is the cat, not 「고양」 (#375 review) */
private val OWN_FINAL_I = setOf(
    "고양이", "호랑이", "원숭이", "거북이", "부엉이", "지렁이", "달팽이", "굼벵이", "반딧불이", "멍멍이", "야옹이",
    "꿀꿀이", "삐약이", "어린이",
)
private val OWN_FINAL_I_TAIL = Regex("(?:양|랑|숭|북|엉|렁|팽|벵|멍|옹)이$")

/**
 * A final 이 is the subject particle only after a consonant (「곰이」 → 곰), and not when it belongs to the noun
 * (「고양이」 · 「호랑이」). A word like that is kept whole.
 */
private fun stripSubjectI(word: String): String {
    if (!word.endsWith('이') || word.length < 2) return word
    if (word in OWN_FINAL_I || OWN_FINAL_I_TAIL.containsMatchIn(word)) return word
    val before = jong(word[word.length - 2])
    return if (before > 0) word.dropLast(1) else word
}

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
 * The one met, from a meeting sentence — 「하얀 북극곰을 만났어」 → 「하얀 북극곰」 · 「숲에서 곰이 나타났어」 → 「곰」 ·
 * 「고양이 봤어」 → 「고양이」. Null when the sentence is not about meeting someone (「눈보라가 불어서 길을 잃었어」).
 */
internal fun storyNameInSentence(value: String): String? {
    val words = value.trim().trimEnd('.', '!', '?', '~', '…').split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.size < 2 || !MEETING.containsMatchIn(words.last())) return null
    val marked = words[words.size - 2]
    val particle = PARTICLE.find(marked)?.value
    if (particle == null && NOT_BARE_NOUN.containsMatchIn(marked)) return null
    val bare = if (particle != null) marked.dropLast(particle.length) else marked
    // 「곰이랑」 → 곰이 → 곰 · 「고양이랑」 → 고양이. After 을 · 를 · 와 · 과 the 이 is the noun's own
    val noun = if (particle == null || particle == "랑") stripSubjectI(bare) else bare
    if (noun.isEmpty()) return null
    // 「하얀」 · 「큰」 — an adjective right before keeps the look; 「엄마는」 · 「숲에서」 do not
    val adjective = words.getOrNull(words.size - 3)?.takeIf { w ->
        jong(w.last()) == JONG_N && w.last() !in NOT_ADJECTIVE && w.length <= 3
    }
    return listOfNotNull(adjective, noun).joinToString(" ")
}
