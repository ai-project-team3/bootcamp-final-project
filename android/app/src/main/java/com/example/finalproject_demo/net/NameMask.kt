package com.example.finalproject_demo.net

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.bat

/**
 * Names and the placeholders the server writes (10-02 조장 — rule 6 revised).
 *
 * **Names are no longer hidden.** The child's call is a nickname the guardian chose (`ChildCall`),
 * and a name alone does not single out a child — hiding it made the mascot say 「너」 · 「그 친구」
 * and the friend's name never reached the story. So [mask] passes text through untouched, and the
 * voice reads names as they are ([speakable]).
 *
 * What stays: the server's prompts still write the protagonist as `{주인공}` (and may write
 * `{친구n}`), so [unmask] turns those back into names with the particle fixed for the name.
 * The child is `{주인공}`; friends are `{친구1}`, `{친구2}`… in the order given.
 */
class NameMask(child: String?, friends: List<String> = emptyList()) {

    private val toName: Map<String, String>

    init {
        val pairs = buildList {
            child?.trim()?.takeIf { usable(it) }?.let { add(it to HERO) }
            friends.map { it.trim() }.filter { usable(it) && it != child?.trim() }.distinct()
                .forEachIndexed { i, f -> add(f to "{친구${i + 1}}") }
        }
        toName = pairs.associate { (name, mark) -> mark to name }
    }

    /** Kept so callers need not change: names are no longer hidden (10-02), so text passes as is. */
    fun mask(text: String): String = text

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

    /** A line for the mascot's voice: placeholders become names, names stay names (10-02). */
    fun speakable(text: String): String = unmask(text)

    companion object {
        const val HERO = "{주인공}"

        // placeholder + the particle glued to it (longest alternatives first)
        private val MARK = Regex("(\\{주인공\\}|\\{친구\\d+\\})(으로|이랑|이는|이가|은|는|이|가|을|를|과|와|로|랑|아|야)?")

        /** Blank is nothing; a placeholder is not a name. One letter is fine now — nothing is hidden (10-02). */
        private fun usable(n: String) = n.isNotBlank() && !n.startsWith("{")

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

/** The session's names — the child's call and the friend in this story — for turning placeholders back. */
fun DemoState.nameMask(): NameMask = NameMask(
    storyHeroCall ?: childName,          // 아이가 인형에 이름을 지어 줬으면 그 이름이 주인공 (10-02)
    listOfNotNull(
        friendName.takeUnless { it.startsWith("{") },
        // 「누구랑?」에 친구 이름으로 답했으면(「민수」) — 호칭(삼촌 · 형)은 이름이 아니다
        partnerCall.takeIf { partnerKey == "friend" },
    ),
)
