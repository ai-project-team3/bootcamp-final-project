package com.example.finalproject_demo.net

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.bat

/**
 * Real names stay on the phone (rule 6). Everything that leaves — slots, utterances, the
 * question just asked — goes through [mask]; everything that comes back — captions, mascot
 * lines — goes through [unmask]. The mapping lives only here, never on the server.
 *
 * The child is `{주인공}`; friends are `{친구1}`, `{친구2}`… in the order given. Build one per
 * session with [DemoState.nameMask] and use the same one both ways, or `{친구1}` could come
 * back as a different friend.
 *
 * ⚠️ It only hides names it was told. A friend the child names for the first time mid-sentence
 * is not in the list yet — add it (and rebuild) before that text is sent.
 */
class NameMask(child: String?, friends: List<String> = emptyList()) {

    /** real name → placeholder. Longest first, so "민수아" is not eaten as "민수" + "아". */
    private val toMark: List<Pair<String, String>>
    private val toName: Map<String, String>

    init {
        val pairs = buildList {
            child?.trim()?.takeIf { usable(it) }?.let { add(it to HERO) }
            friends.map { it.trim() }.filter { usable(it) && it != child?.trim() }.distinct()
                .forEachIndexed { i, f -> add(f to "{친구${i + 1}}") }
        }
        toMark = pairs.sortedByDescending { it.first.length }
        toName = pairs.associate { (name, mark) -> mark to name }
    }

    /** Names → placeholders. A name counts only at the start of a word, so "지호" does not hide inside "보지호수". */
    fun mask(text: String): String {
        var out = text
        for ((name, mark) in toMark) {
            out = Regex("(?<![가-힣A-Za-z])" + Regex.escape(name)).replace(out, Regex.escapeReplacement(mark))
        }
        return out
    }

    fun maskSlots(slots: Map<String, String?>): Map<String, String?> = slots.mapValues { (_, v) -> v?.let { mask(it) } }

    /**
     * Placeholders → names, fixing the particle after each one: the model writes `{주인공}는`
     * without knowing the name, and "지민는" reads wrong to a parent. A placeholder with no
     * name (a friend the server invented, or a name never given) becomes [fallback].
     */
    fun unmask(text: String, fallback: String = "친구"): String =
        MARK.replace(text) { m ->
            val name = toName[m.groupValues[1]] ?: if (m.groupValues[1] == HERO) "주인공" else fallback
            name + fixParticle(name, m.groupValues[2])
        }

    companion object {
        const val HERO = "{주인공}"

        // placeholder + the particle glued to it (longest alternatives first)
        private val MARK = Regex("(\\{주인공\\}|\\{친구\\d+\\})(으로|이랑|이는|이가|은|는|이|가|을|를|과|와|로|랑|아|야)?")

        /** A one-letter name would hide inside ordinary words; blank is nothing. */
        private fun usable(n: String) = n.length >= 2 && !n.startsWith("{")

        private val PAIRS = mapOf(
            "은" to ("은" to "는"), "는" to ("은" to "는"),
            "이" to ("이" to "가"), "가" to ("이" to "가"),
            "을" to ("을" to "를"), "를" to ("을" to "를"),
            "과" to ("과" to "와"), "와" to ("과" to "와"),
            "이랑" to ("이랑" to "랑"), "랑" to ("이랑" to "랑"),
            "아" to ("아" to "야"), "야" to ("아" to "야"),
        )

        internal fun fixParticle(name: String, p: String): String {
            if (p.isEmpty() || p == "이는" || p == "이가") return p
            if (p == "으로" || p == "로") {
                val last = name.last().code - 0xAC00
                return if (bat(name) && last % 28 != 8) "으로" else "로"    // ㄹ받침은 "로"
            }
            val (withB, without) = PAIRS[p] ?: return p
            return if (bat(name)) withB else without
        }
    }
}

/**
 * The session's mask: the child's name, and the friend in this story if it has a real name.
 * A story character the child named ("뿌뿌") is masked too — a child may name it after a real
 * friend, and hiding a made-up name costs nothing because it comes back on the phone.
 */
fun DemoState.nameMask(): NameMask = NameMask(childName, listOfNotNull(friendName.takeUnless { it.startsWith("{") }))
