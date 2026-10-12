package com.example.finalproject_demo.demo.missions

/*
 * ── Mission words — matched at the start of an eojeol only (#259 · docs/실기기수정_설계_1007.md §4-3 1) ──────────────
 *
 * The tables used to be searched anywhere in the sentence (substring), so the middle of a word matched —
 * 「미끄럼틀」's 「끄」 · 「식초를」's 「초를」 · 「놀다가 넘어지는 바람에」's 「바람」 (a reason, not wind) ·
 * 「연기를 했어」 (acting) as smoke. Now a table word matches only where an eojeol starts (「먼지가」 · 「먼지를」 match,
 * 「흙먼지」 does not). Two words with a space (「불을 끄」) are read across consecutive eojeols.
 * A word with a mark in it (「후~」) is matched as written — without the mark 「후회」 · 「후식」 would match.
 */

/** The child's eojeols — punctuation and quotes dropped, split on spaces */
internal fun eojeols(text: String): List<String> =
    text.split(Regex("[\\s,.!?~·…\"“”「」『』()]+")).filter(String::isNotEmpty)

private val PUNCT = Regex("[~!?.]")

/** Does [key] start at the beginning of some eojeol of [text] */
internal fun saysWord(text: String, key: String): Boolean {
    if (PUNCT.containsMatchIn(key)) return key in text
    val words = eojeols(text)
    val k = eojeols(key).joinToString(" ")
    if (k.isEmpty()) return false
    return words.indices.any { i -> words.subList(i, words.size).joinToString(" ").startsWith(k) }
}

/**
 * Does any of [keys] start an eojeol. [unless] first removes uses where the same word means something else
 * (「…하는 바람에」 is a reason, not wind)
 */
internal fun saysAny(text: String, keys: List<String>, unless: (String) -> String = { it }): Boolean {
    val t = unless(text)
    return keys.any { saysWord(t, it) }
}

/**
 * Removes 「바람에」 given as a reason — the eojeol before ends in ㄴ (넘어지는 · 서두른 · 뛰어간) or 「던」 — and wind
 * given as the reason ([WIND_AS_CAUSE]). 「바람에 모자가 날아갔어」 · 「바람이 불었어」 stay
 */
internal fun dropCauseBaram(text: String): String = Regex("(\\S+)\\s+바람에").replace(text) { m ->
    val c = m.groupValues[1].last()
    val nieun = c in '가'..'힣' && (c - '가') % 28 == 4
    if (nieun || c == '던') m.groupValues[1] + " " else m.value
}.let { WIND_AS_CAUSE.replace(it, " ") }

/**
 * Wind given as the reason — 「바람이 (세게) 불어서」 · 「바람 때문에」 (lead decision 10-08 #321: the 10-08 device book
 * picked blowing from the cause answer 「바람이 세게 불어서」). The mission is for something blown, not for why
 */
private val WIND_AS_CAUSE = Regex("바람(?:이|에|가)?\\s*(?:\\S+\\s+)?불어서|바람\\s*때문에")
