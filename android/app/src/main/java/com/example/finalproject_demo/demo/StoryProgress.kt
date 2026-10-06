package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

enum class StoryPreparationPhase(val label: String) {
    COLLECTING("이야기 모으기"),
    FINISHING("그림·소리 마무리"),
    WRITING("책 만들기"),
}

/**
 * The live track runs in [STEPS] fine steps but shows [BEADS] star beads, so it can rise a little every
 * answer without drawing forty stars.
 */
data class StoryPreparationProgress(val filled: Int, val phase: StoryPreparationPhase) {
    val total: Int get() = STEPS

    companion object {
        const val STEPS = 40
        const val BEADS = 10
        /** Collecting stays below this (85%) — 36 is FINISHING, 40 is WRITING */
        const val COLLECTING_MAX = 34
    }
}

/**
 * Track position while the story is still being collected.
 *
 * 10-06 (종훈): the track sat near half for most of a live story and then jumped to 90%. It counted only
 * server-confirmed slots against a fixed ten, and story_ready usually comes after four or five slots.
 * Now every real answer counts as well as every confirmed fact, against an expected total that grows with
 * them (`c / max(8, c + 2)`), so a typical five-slot story reaches about 70% before story_ready.
 * This is display only — it never ends or extends the story (rule 3: no turn cap).
 */
internal fun DemoState.liveCollectingSteps(): Int {
    val facts = slots.count { (key, value) -> key in Server.SLOTS && key != "extra" && value.isNotBlank() }
    val c = facts + storyAnswers
    val steps = if (c == 0) 0 else (StoryPreparationProgress.COLLECTING_MAX * c / maxOf(8, c + 2))
    return maxOf(steps, storyGaugeHigh).coerceAtMost(StoryPreparationProgress.COLLECTING_MAX)
}

/** Called after each live turn (and undo) so a removed fact never moves the track back */
internal fun DemoState.holdStoryGauge() {
    storyGaugeHigh = liveCollectingSteps()
}

/** Preparation milestones, not an estimate of the remaining AI questions. */
val DemoState.liveStoryProgress: StoryPreparationProgress?
    get() {
        if (mode != StoryMode.STORY || !Server.liveFor(mode)) return null
        if (!storyReady) return StoryPreparationProgress(liveCollectingSteps(), StoryPreparationPhase.COLLECTING)
        if (scene == Scene.MAKING || scene == Scene.BOOK) {
            // The live flow reaches MAKING only after finishing the drawing/sound/background work.
            // A full track means the story is ready to make into a book, not that generation has finished.
            return StoryPreparationProgress(StoryPreparationProgress.STEPS, StoryPreparationPhase.WRITING)
        }
        return StoryPreparationProgress(StoryPreparationProgress.COLLECTING_MAX + 2, StoryPreparationPhase.FINISHING)
    }
