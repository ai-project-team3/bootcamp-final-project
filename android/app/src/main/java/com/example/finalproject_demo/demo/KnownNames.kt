package com.example.finalproject_demo.demo

/**
 * A name the child already gave, heard a little wrong later, is put back (10-05 device round).
 * The child named the newcomer 「우가우가」; later turns came back 「우거우거랑」 and the judge did not know
 * who that was. Narrow on purpose — the child's words are the record (rule 5):
 * - same number of syllables only (「우가가」 for 「우가우가」 may be the child's own short form — kept)
 * - same first consonant, and at most one changed letter per two syllables (jamo)
 * - the particle after it stays (「우거우거랑」 → 「우가우가랑」)
 */
fun DemoState.knownNames(): List<String> = listOfNotNull(
    storyHeroCall, slots["name"], friendName.takeUnless { it.startsWith("{") },
).map { it.replace(" ", "") }.filter { it.length >= 2 }.distinct()

private val PARTICLES = listOf("한테", "에게", "이랑", "랑", "하고", "와", "과", "이", "가", "은", "는", "을", "를", "의", "도", "야", "아")

fun fixKnownNames(text: String, names: List<String>): String {
    if (names.isEmpty()) return text
    return text.split(" ").joinToString(" ") { word ->
        val particle = PARTICLES.firstOrNull { word.length > it.length && word.endsWith(it) }
        val cores = listOfNotNull(word, particle?.let { word.dropLast(it.length) })
        cores.firstNotNullOfOrNull { core -> names.firstOrNull { closeName(core, it) }?.let { core to it } }
            ?.let { (core, name) -> name + word.removePrefix(core) }
            ?: word
    }
}

internal fun closeName(heard: String, name: String): Boolean {
    if (heard == name || heard.length != name.length || !heard.all(::isSyllable) || !name.all(::isSyllable)) return false
    val a = heard.map(::jamo); val b = name.map(::jamo)
    if (a[0][0] != b[0][0]) return false
    val diff = a.indices.sumOf { i -> (0..2).count { a[i][it] != b[i][it] } }
    return diff in 1..maxOf(1, name.length / 2)
}

private fun isSyllable(c: Char) = c in '가'..'힣'
private fun jamo(c: Char): IntArray { val v = c - '가'; return intArrayOf(v / (21 * 28), (v / 28) % 21, v % 28) }
