package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.reasonOrNull

/**
 * Co-op's second character (#339 ② · design `docs/같이만들기_등장인물_설계_339.md` §2 · §4). Beside the hero and the companion,
 * one more character the child named stands in the middle spot (PROBLEM_SPOT) — on the conversation stage and on the
 * book pages whose text names it. It rides #351's character list as the role [COOP_SECOND_ROLE].
 */
internal const val COOP_SECOND_ROLE = "second"

/** Tries per second-character request — co-op asks again on the next step after a failure, but not forever (§2-5) */
internal const val COOP_CHARACTER_TRIES = 2

/** A real day has zoo animals and job-day people the story list does not have (§2-2). The story list is unchanged */
private val COOP_ACTOR_NOUNS = ACTOR_NOUNS + listOf(
    // 「말」 is left out — 「말도 안 돼」 is not a horse
    "기린", "펭귄", "판다", "얼룩말", "하마", "코뿔소", "사슴", "오리", "닭", "돼지", "소", "양", "염소",
    "소방관", "경찰관", "의사", "간호사", "요리사", "아기", "형", "오빠", "누나", "언니", "동생", "이모", "삼촌", "고모",
).distinct().sortedByDescending { it.length }

/** A parent question's answer names who received or joined — 「기린한테 사과 줬어」 · 「강아지랑 놀았어」 (§2-2) */
private val COOP_ACTOR = actorPattern(COOP_ACTOR_NOUNS, "이랑|이|가|은|는|한테|에게|랑|하고|도")
private val COOP_BARE_ACTOR = Regex("[가-힣]*(?:${COOP_ACTOR_NOUNS.joinToString("|")})")
private val PARENT_SLOT = Regex("^parent\\d+$")

/** The first actor named in a co-op answer — [problemActorIn]'s rules with co-op's nouns and particles */
internal fun coopActorIn(text: String): String? = actorIn(text, COOP_ACTOR, COOP_BARE_ACTOR)

/**
 * Who the second character is — the child's words as said, or null (§2-1). First an actor in the problem · cause the
 * child gave (by child or card), then one in the parent questions' answers. Never the companion, the hero, something the
 * child drew on the whiteboard, or 「혼자」 · 「아무도」. Diary and story: always null.
 */
internal fun DemoState.coopSecondCharacter(): String? {
    if (!isCoop) return null
    val told = listOf("problem", "cause").filter { slotBy[it] == "child" || slotBy[it] == "card" } +
        slots.keys.filter { PARENT_SLOT.matches(it) }.sortedBy { it.removePrefix("parent").toInt() }
    val others = listOfNotNull(companionKind.takeIf(String::isNotBlank), friendName.takeIf { it.isNotBlank() && !it.startsWith("{") },
        childName.takeIf(String::isNotBlank), storyHeroCall)
    val whiteboard = slots["whiteboard"].orEmpty()
    return told.asSequence().mapNotNull { slots[it]?.let(::coopActorIn) }.firstOrNull { actor ->
        others.none { sameActor(actor, it) } && actor !in whiteboard && "혼자" !in actor && "아무도" !in actor
    }
}

/** A preset for the second character — family · teacher by the last word (§3), then a story preset. Null: ask for a doll */
internal fun coopSecondPreset(words: String): Art? = coopCompanionPreset(words) ?: storyPresetMatch(words)

/** The doll made for the second character — while it is still that character, or from a reopened book */
internal fun DemoState.coopSecondDoll(): GeneratedFriend? = if (!isCoop) null else generatedCharacters.firstOrNull {
    it.role == COOP_SECOND_ROLE && (readingSavedCast || it.words == coopSecondCharacter())
}

/**
 * What stands in the middle spot: the doll, or a preset, or — while waiting or after a failure — nothing on a real day
 * (다녀왔어요 · 곧 해요: a ✨ would read as 「something was there」) and ✨ for 좋아해요, like a story (⚖️2)
 */
internal fun DemoState.coopSecondArt(): Art? {
    val doll = coopSecondDoll()
    val words = coopSecondCharacter() ?: doll?.words ?: return null
    doll?.let { return Art.Img(it.image, coopSecondPreset(words) ?: Art.Emoji("✨"), it.rig) }
    coopSecondPreset(words)?.let { return it }
    return if ((bookPick ?: coopPick)?.reasonOrNull() == CoopReason.DREAM) Art.Emoji("✨") else null
}

/** The second character on a book page — not on the cover or the mission pages, and only where the page text names it (§4-3) */
internal fun DemoState.coopSecondOnPage(kind: PageKind, caption: String): Art? {
    if (kind in setOf(PageKind.COVER, PageKind.RUB, PageKind.DRAG)) return null
    val words = coopSecondCharacter() ?: coopSecondDoll()?.words ?: return null
    val noun = COOP_ACTOR_NOUNS.firstOrNull { words.endsWith(it) } ?: words
    return coopSecondArt()?.takeIf { words in caption || noun in caption }
}
