package com.example.finalproject_demo.demo

/**
 * A place answered as a sentence — 「숲속에 갔어」 (10-09 device · Laya v5). The judge filled `place` with the whole answer and
 * page 1 read 「‘숲속에 갔어’에서 친구는 숲길을 걸어요.」. The place is taken from the sentence: the word before 에 · 에서 ·
 * (으)로 and the verb at the end, with the whole place phrase before it — 「숲속에 갔어」 → 숲속 · 「바다 속 궁전으로 갔어」 →
 * 바다 속 궁전 · 「엄마랑 동물원에 갔어」 → 동물원 (a whole phrase, so SceneKits still matches 「바다 속」 · #390 review).
 * A value that is not that shape is kept as it is (「우리 집」 · 「놀이공원」 · 「바다 속 궁전」).
 */
private val PLACE_PARTICLE = Regex("(?:에서|에|으로|로)$")
/** The last word reads as a verb — 갔어 · 갈래 · 놀았어 · 있었어요 · 간다 · 가자 · and the bare 가 · 와 (「동물원으로 가」) */
private val VERB_END = Regex("(?:어|아|요|래|다|자|지|네|가|와)$")
/** A word that belongs to someone else in the sentence, not to the place — 「엄마랑」 · 「나는」 · 「엄마가」 · 「오늘도」 */
private val NOT_PLACE_WORD = Regex("(?:랑|하고|와|과|는|가|을|를|도|에서|에|으로|로)$")
/** 은 ends these place adjectives — 「작은 숲」 · 「높은 산」; elsewhere it is a topic (「동생은」) */
private val PLACE_ADJECTIVES = setOf("작은", "높은", "넓은", "깊은", "좋은", "맑은", "밝은", "검은", "붉은", "낮은", "좁은", "얕은", "같은")

/**
 * Someone who can be the subject with a bare 이 — 「곰이 숲에 갔어」. Only these stop the phrase: an unknown word ending in 이
 * may be the place's own noun (「고양이 마을로 갔어」 → 고양이 마을), so it is kept (#390 review — keep what is uncertain).
 */
private val SUBJECT_BEFORE_I = listOf(
    "곰", "괴물", "공룡", "드래곤", "외계인", "유령", "도둑", "해적", "로봇", "사람", "동생", "형", "선생님",
)

/** Does [w] still belong to the place phrase — it stops only at someone (「엄마랑」 · 「곰이」) or a time (「오늘」) */
private fun belongsToPlace(w: String): Boolean {
    if (w in TIME_WORDS || NOT_PLACE_WORD.containsMatchIn(w)) return false
    if (w.endsWith('은')) return w in PLACE_ADJECTIVES
    if (w.endsWith('이') && w.length >= 2) {
        val stem = w.dropLast(1)
        if (SUBJECT_BEFORE_I.any(stem::endsWith)) return false
    }
    return true
}
/** Times and words about the speaker — 「우리」 is not here: 「우리 집」 is a place (#390 review) */
private val TIME_WORDS = setOf("오늘", "어제", "내일", "아까", "주말", "그때", "지금", "같이", "다", "또", "나", "내가", "제가", "저", "얼른", "빨리")

internal fun storyPlaceFromSentence(value: String): String {
    val words = value.trim().trimEnd('.', '!', '?', '~', '…').split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.size < 2 || !VERB_END.containsMatchIn(words.last())) return value.trim()
    val marked = words[words.size - 2]
    val particle = PLACE_PARTICLE.find(marked)?.value ?: return value.trim()
    // 「놀이터로」 is 놀이터 + 로; 「바다로」 too — but a word that is only the particle is nothing
    val place = marked.dropLast(particle.length).takeIf { it.isNotEmpty() } ?: return value.trim()
    // every word before it that still belongs to the place — stops at someone (「엄마랑」) or a time (「오늘」)
    val phrase = words.dropLast(2).takeLastWhile(::belongsToPlace)
    return (phrase + place).joinToString(" ")
}
