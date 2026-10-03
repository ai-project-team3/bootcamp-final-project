package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode

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
)

fun DemoState.storyFacts() = StoryFacts(
    mode = mode,
    templateKey = templateKey,
    problem = problem,
    cause = cause,
    solution = solution,
    // Co-op counts as a real day here even for 「좋아해요」(imagined) — the picker does not read it yet;
    // stage 1-b splits it by the co-op reason (design §7-2)
    realDay = isDiary,
)

/**
 * Stage 1-a (design §7-1): the rules that were copied in three places, gathered in one. Behaviour is
 * unchanged — slot 1 rubs (A6); slot 2 is the picture puzzle (A3) for the frames that resolve by
 * 「다시 쌓다 · 맞추다 · 되돌리다」(A 도전-성취 · G 우화-교훈), handing over (E1) for the rest.
 * The word-signal table (§6-1) comes in with the first new mission screen.
 */
fun pickMissions(facts: StoryFacts): BookMissions {
    val slot2 = if (facts.templateKey in PUZZLE_FRAMES) MissionId.A3 else MissionId.E1
    val slot1 = MissionId.A6
    check(slot1.built && slot2.built) { "picked a mission without a screen: $slot1 / $slot2" }
    return BookMissions(slot1, slot2)
}

private val PUZZLE_FRAMES = setOf("A", "G")

/** This book's two missions. Pure on the facts, so a reopened book gets the same ones */
fun DemoState.missions(): BookMissions = pickMissions(storyFacts())
