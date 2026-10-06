package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

enum class StoryPreparationPhase(val label: String) {
    COLLECTING("이야기 모으기"),
    FINISHING("그림·소리 마무리"),
    WRITING("책 만들기"),
}

data class StoryPreparationProgress(val filled: Int, val phase: StoryPreparationPhase) {
    val total: Int get() = 10
}

/** Preparation milestones, not an estimate of the remaining AI questions. No session state is added. */
val DemoState.liveStoryProgress: StoryPreparationProgress?
    get() {
        if (mode != StoryMode.STORY || !Server.liveFor(mode)) return null
        if (!storyReady) {
            // Distinct confirmed facts light up to eight stars. The last two mark final preparation
            // and starting the book, not extra questions that the app should force the child to answer.
            val facts = slots.count { (key, value) -> key in Server.SLOTS && key != "extra" && value.isNotBlank() }
            return StoryPreparationProgress(facts.coerceAtMost(8), StoryPreparationPhase.COLLECTING)
        }
        if (scene == Scene.MAKING || scene == Scene.BOOK) {
            // The live flow reaches MAKING only after finishing the drawing/sound/background work.
            // A full track means the story is ready to make into a book, not that generation has finished.
            return StoryPreparationProgress(10, StoryPreparationPhase.WRITING)
        }
        return StoryPreparationProgress(9, StoryPreparationPhase.FINISHING)
    }
