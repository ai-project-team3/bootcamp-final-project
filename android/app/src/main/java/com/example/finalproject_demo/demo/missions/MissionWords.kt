package com.example.finalproject_demo.demo.missions

/*
 * ── 미션 낱말 맞추기 — 어절 앞에서만 (#259 · docs/실기기수정_설계_1007.md §4-3 1) ─────────────────────────────
 *
 * 전에는 낱말 표를 문장 아무 데서나 찾았다(부분일치). 그래서 낱말 가운데가 걸렸다 —
 * 「미끄럼틀」의 「끄」 · 「식초를」의 「초를」 · 「놀다가 넘어지는 바람에」의 「바람」(까닭을 말하는 표현) ·
 * 「연기를 했어」(연극)의 「연기」. 이제 표의 낱말은 **어절의 처음에서 시작할 때만** 걸린다
 * (「먼지가」 · 「먼지를」은 걸리고 「흙먼지」는 안 걸린다). 띄어 쓴 두 낱말(「불을 끄」)은 이어지는 어절로 본다.
 * 「후~」처럼 부호가 든 낱말은 글자 그대로 찾는다 — 부호를 떼면 「후회」 · 「후식」이 걸린다.
 */

/** 아이 말의 어절 — 문장 부호 · 따옴표를 떼고 띄어쓰기로 */
internal fun eojeols(text: String): List<String> =
    text.split(Regex("[\\s,.!?~·…\"“”「」『』()]+")).filter(String::isNotEmpty)

private val PUNCT = Regex("[~!?.]")

/** [key] 가 [text] 의 어떤 어절 처음에서 시작하나 */
internal fun saysWord(text: String, key: String): Boolean {
    if (PUNCT.containsMatchIn(key)) return key in text
    val words = eojeols(text)
    val k = eojeols(key).joinToString(" ")
    if (k.isEmpty()) return false
    return words.indices.any { i -> words.subList(i, words.size).joinToString(" ").startsWith(k) }
}

/**
 * [keys] 가운데 하나라도 어절 처음에서 걸리나. [unless] 는 먼저 지울 표현 — 낱말은 같아도 뜻이 다른 쓰임
 * (「…하는 바람에」는 바람이 아니라 까닭이다)
 */
internal fun saysAny(text: String, keys: List<String>, unless: (String) -> String = { it }): Boolean {
    val t = unless(text)
    return keys.any { saysWord(t, it) }
}

/**
 * 까닭을 말하는 「바람에」를 지운다 — 앞 어절이 ㄴ 받침(넘어지는 · 서두른 · 뛰어간)이나 「던」으로 끝날 때.
 * 「바람에 모자가 날아갔어」 · 「바람이 불었어」는 그대로 둔다
 */
internal fun dropCauseBaram(text: String): String = Regex("(\\S+)\\s+바람에").replace(text) { m ->
    val c = m.groupValues[1].last()
    val nieun = c in '가'..'힣' && (c - '가') % 28 == 4
    if (nieun || c == '던') m.groupValues[1] + " " else m.value
}
