package com.example.finalproject_demo.demo

/**
 * A place answered as a sentence — 「숲속에 갔어」 (10-09 device · Laya v5). The judge filled `place` with the whole answer and
 * page 1 read 「‘숲속에 갔어’에서 친구는 숲길을 걸어요.」. The place is taken from the sentence: the word before 에 · 에서 ·
 * (으)로 and the verb at the end — 「숲속에 갔어」 → 숲속 · 「얼음 산으로 갈래」 → 얼음 산 · 「엄마랑 동물원에 갔어」 → 동물원.
 * A value that is not that shape is kept as it is (「우리 집」 · 「놀이공원」 · 「바다 속 궁전」).
 */
private val PLACE_PARTICLE = Regex("(?:에서|에|으로|로)$")
/** The last word reads as a verb — 갔어 · 갈래 · 놀았어 · 있었어요 · 간다 · 가자 */
private val VERB_END = Regex("(?:어|아|요|래|다|자|지|네)$")
/** A word that belongs to someone else in the sentence, not to the place — 「엄마랑」 · 「나는」 · 「오늘도」 */
private val NOT_PLACE_WORD = Regex("(?:랑|이랑|하고|와|과|은|는|이|가|을|를|도|에서|에|으로|로)$")
private val TIME_WORDS = setOf("오늘", "어제", "내일", "아까", "주말", "그때", "지금", "같이", "우리", "다", "또")

internal fun storyPlaceFromSentence(value: String): String {
    val words = value.trim().trimEnd('.', '!', '?', '~', '…').split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.size < 2 || !VERB_END.containsMatchIn(words.last())) return value.trim()
    val marked = words[words.size - 2]
    val particle = PLACE_PARTICLE.find(marked)?.value ?: return value.trim()
    // 「놀이터로」 is 놀이터 + 로; 「바다로」 too — but a word that is only the particle is nothing
    val place = marked.dropLast(particle.length).takeIf { it.isNotEmpty() } ?: return value.trim()
    val before = words.getOrNull(words.size - 3)?.takeIf { !NOT_PLACE_WORD.containsMatchIn(it) && it !in TIME_WORDS }
    return listOfNotNull(before, place).joinToString(" ")
}
