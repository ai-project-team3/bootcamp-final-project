package com.example.finalproject_demo.demo

/**
 * A name answered with its copula — 「아기 펭귄 이름은 뭐야?」 → 「뽀뽀야」 (10-09 device · Laya v5). The judge filled `name`
 * with 「뽀뽀야」 and Otto said 「뽀뽀야의 소리를 직접 만들어 볼까?」 · 「뽀뽀야는 엄마 품에 꼭 안겼어.」.
 *
 * Only the ending is taken off, and only when a name of two syllables or more is left: 「뽀뽀야」 → 뽀뽀 · 「뽀뽀예요」 → 뽀뽀 ·
 * 「뽀야야」 → 뽀야, but 「뽀야」 stays 뽀야. A final 이 is kept (「콩콩이야」 → 콩콩이) — children's names often end in 이,
 * and 「콩콩 + 이야」 cannot be told from 「콩콩이 + 야」. The vocative 아 (「곰돌아」) is left alone for the same reason.
 */
internal fun storyNameWithoutCopula(value: String): String {
    val name = value.trim().trimEnd('.', '!', '?', '~', '…')
    if (name.any { it.isWhitespace() }) return value.trim()
    val bare = when {
        name.endsWith("예요") || name.endsWith("에요") -> name.dropLast(2)
        name.endsWith("야") -> name.dropLast(1)
        else -> return value.trim()
    }
    return if (bare.length >= 2) bare else value.trim()
}
