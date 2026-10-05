package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookPick
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.reasonOrNull

/**
 * What the picker may look at — only the facts that choose a mission, so the picker stays a pure
 * function (`docs/맞춤미션_설계.md` §5-1). [realDay] is a diary or a co-op 「다녀왔어요 · 곧 해요」 book:
 * a real day must not get a prop the child never said (§3-8).
 */
data class StoryFacts(
    val mode: StoryMode,
    val templateKey: String?,
    val problem: String?,
    val cause: String?,
    val solution: String?,
    val realDay: Boolean,
    /** 꼬리질문 「더 자세히(detail)」 — 자리 1 이 보는 칸 (design §6-1) */
    val detail: String? = null,
    /** 꼬리질문 「제일 먼저 한 일(try)」 — 자리 2 가 보는 칸 (design §6-1) */
    val tried: String? = null,
) {
    /** 자리 2 (해결) 가 보는 아이 말 — solution · try */
    val slot2Words: String get() = listOfNotNull(solution, tried).joinToString(" ")

    /** 자리 1 (사건 직후) 이 보는 아이 말 — problem · cause · detail */
    val slot1Words: String get() = listOfNotNull(problem, cause, detail).joinToString(" ")
}

fun DemoState.storyFacts() = StoryFacts(
    mode = mode,
    templateKey = templateKey,
    problem = problem,
    cause = cause,
    solution = solution,
    // A real day (§3-8): a diary, or a co-op 「다녀왔어요 · 곧 해요」 or questions-only story. A co-op
    // 「좋아해요」(or a pick with no reason, asked as imagination) is not — it may borrow a prop (1-b · §7-2)
    realDay = isDiary && !(isCoop && coopImagined()),
    detail = slots["detail"],
    tried = slots["try"],
)

/** 협업에서 상상으로 물은 이야기인가 — 좋아해요, 또는 이야기를 골랐는데 이유가 없을 때 */
private fun DemoState.coopImagined(): Boolean =
    bookPick?.let { (it.reasonOrNull() ?: CoopReason.DREAM) == CoopReason.DREAM } ?: false

/**
 * Stage 1-a (design §7-1): the rules that were copied in three places, gathered in one. Behaviour is
 * unchanged — slot 1 rubs (A6); slot 2 is the picture puzzle (A3) for the frames that resolve by
 * 「다시 쌓다 · 맞추다 · 되돌리다」(A 도전-성취 · G 우화-교훈), handing over (E1) for the rest.
 * The word-signal table (§6-1) comes in with the first new mission screen.
 */
fun pickMissions(facts: StoryFacts): BookMissions {
    // 1-b (co-op): slot 2 is picked first (design §6-2) — the child's own solution verb (껐어 · 잠갔어) before the
    // things in the trouble (불 · 물이 샜어). No signal: the frame rule as before
    val fix = if (facts.mode == StoryMode.COOP) fixPropIn(facts.slot2Words, facts.slot1Words) else null
    val slot2 = fix?.mission ?: if (facts.templateKey in PUZZLE_FRAMES) MissionId.A3 else MissionId.E1
    // 1-b (co-op first · design §7-1): slot 1 blows (C1) when the child's own words have something to blow —
    // 촛불 · 생일 · 민들레 · 먼지 · 바람. The story mode follows once more co-op missions exist (§7-3)
    // C3 (sound) waits until the server knows its ID (SERVER_KNOWS_C3 · design §8)
    val coop = facts.mode == StoryMode.COOP
    val slot1 = when {
        coop && blowPropIn(facts.slot1Words, facts.realDay) != null -> MissionId.C1
        coop && SERVER_KNOWS_C3 && soundPropIn(facts.slot1Words) != null -> MissionId.C3
        else -> MissionId.A6
    }
    check(slot1.built && slot2.built) { "picked a mission without a screen: $slot1 / $slot2" }
    return BookMissions(slot1, slot2)
}

private val PUZZLE_FRAMES = setOf("A", "G")

/** This book's two missions. Pure on the facts, so a reopened book gets the same ones */
fun DemoState.missions(): BookMissions = pickMissions(storyFacts())
