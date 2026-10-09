package com.example.finalproject_demo.demo

import com.example.finalproject_demo.demo.scene.HERO_SPOT
import com.example.finalproject_demo.demo.scene.FRIEND_SPOT
import com.example.finalproject_demo.demo.scene.PROBLEM_SPOT

/** A role is an illustration binding, never a new server slot or a new story fact. */
internal data class CharacterRequest(val role: String, val words: String)

// Conservative local recognition: uncertain names stay in the text rather than inventing a visible actor.
private val ACTOR_NOUNS = listOf(
    "괴물", "고양이", "강아지", "사자", "호랑이", "토끼", "곰", "늑대", "여우", "원숭이", "악어",
    "코끼리", "상어", "물고기", "공룡", "드래곤", "외계인", "도깨비", "유령", "마녀", "마법사",
    "도둑", "해적", "로봇", "공주", "왕자", "할머니", "할아버지", "엄마", "아빠", "친구",
)
private val ACTOR = Regex("([가-힣]*(?:${ACTOR_NOUNS.joinToString("|")}))(?:이|가|은|는)(?=\\s|$)")
private val BARE_ACTOR = Regex("[가-힣]*(?:${ACTOR_NOUNS.joinToString("|")})")
private val NEGATED_ACTOR = Regex("^(?:안\\s*나|안\\s*왔|없|아니)|(?:나오지|오지|있지)\\s*않|(?:사실\\s*)?없었")
private val ACTOR_MODIFIERS = setOf("도둑", "아기", "꼬마", "거인", "빨간", "파란", "노란", "커다란", "작은", "큰", "무서운")

internal fun problemActorIn(text: String): String? {
    val cleaned = text.trim().trimEnd('.', '!', '?')
    if (BARE_ACTOR.matches(cleaned)) return cleaned
    val words = cleaned.split(Regex("\\s+"))
    if (words.size > 1 && words.dropLast(1).all { it in ACTOR_MODIFIERS } && BARE_ACTOR.matches(words.last()))
        return words.joinToString(" ")
    for (clause in cleaned.split(Regex("[.!?。\\n,]"))) {
        for (match in ACTOR.findAll(clause)) {
            val before = clause.substring(0, match.range.first).trimEnd()
            // A noun embedded in another noun (e.g. 사자 모양 과자) is not a character subject.
            if (match.range.first > 0 && !clause[match.range.first - 1].isWhitespace()) continue
            val after = clause.substring(match.range.last + 1).trimStart()
            if (after.isBlank() || NEGATED_ACTOR.containsMatchIn(after)) continue
            val modifier = before.substringAfterLast(' ').takeIf { it in ACTOR_MODIFIERS }
            return listOfNotNull(modifier, match.groupValues[1]).joinToString(" ")
        }
    }
    return null
}

private fun sameActor(a: String, b: String): Boolean {
    val left = a.replace(" ", "").trim()
    val right = b.replace(" ", "").trim()
    return left.isNotBlank() && right.isNotBlank() &&
        (left == right || left.endsWith(right) || right.endsWith(left))
}

internal fun DemoState.storyProblemCharacter(): String? {
    if (mode != StoryMode.STORY) return null
    if (readingSavedCast) return savedProblemCharacter
    return listOf("problem", "cause").asSequence()
        .filter { slotBy[it] == "child" }
        .mapNotNull { slots[it]?.let(::problemActorIn) }
        .firstOrNull { actor ->
            listOfNotNull(slots["newcomer"], slots["name"], storyHeroCall, childName).none { sameActor(actor, it) }
        }
}

internal fun DemoState.charactersToDraw(): List<CharacterRequest> = buildList {
    friendToDraw()?.let { add(CharacterRequest("friend", it)) }
    storyProblemCharacter()?.takeIf { storyPresetMatch(it) == null }?.let { add(CharacterRequest("problem", it)) }
}

/** Reopened books carry role/image bindings even though they have no live conversation slots. */
internal fun DemoState.storyProblemDoll(): GeneratedFriend? = generatedCharacters.firstOrNull {
    it.role == "problem" && mode == StoryMode.STORY && (it.words == storyProblemCharacter() || readingSavedCast)
}

internal fun DemoState.storyProblemArt(): Art? {
    val words = storyProblemCharacter() ?: storyProblemDoll()?.words ?: return null
    // A neutral marker avoids substituting an unrelated character when generation is unavailable.
    return storyProblemDoll()?.let { Art.Img(it.image, Art.Emoji("✨"), it.rig) }
        ?: storyPresetMatch(words) ?: Art.Emoji("✨")
}

/** Keep the kit stable as the newcomer and problem actor arrive asynchronously. */
internal val DemoState.sceneActorCapacity: Int get() = if (mode == StoryMode.STORY) 3 else 2

internal fun DemoState.storyConversationWorld() = Stage.World(buildList {
    add(WorldItem(storyHeroArt, HERO_SPOT.x, .32f, .11f, depth = HERO_SPOT.depth))
    if (drawing.isNotEmpty() || storyFriendDoll != null)
        add(WorldItem(friendArt, FRIEND_SPOT.x, .32f, .13f, depth = FRIEND_SPOT.depth))
    storyProblemArt()?.let { add(WorldItem(it, PROBLEM_SPOT.x, .32f, .11f, depth = PROBLEM_SPOT.depth)) }
})

internal fun DemoState.storyProblemOnPage(kind: PageKind, caption: String): Art? {
    // Mission views own their touch regions. Never overlay another actor on top of their tools.
    if (kind in setOf(PageKind.COVER, PageKind.RUB, PageKind.DRAG)) return null
    val words = storyProblemCharacter() ?: storyProblemDoll()?.words ?: return null
    val noun = ACTOR_NOUNS.firstOrNull { words.endsWith(it) } ?: words
    return storyProblemArt()?.takeIf { words in caption || noun in caption }
}
