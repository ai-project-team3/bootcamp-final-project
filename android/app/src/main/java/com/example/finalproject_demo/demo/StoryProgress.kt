package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

enum class StoryPreparationPhase(val label: String) {
    COLLECTING("이야기 모으기"),
    FINISHING("그림·소리 마무리"),
    WRITING("책 만들기"),
    COMPLETE("책 완성"),
}

data class StoryPreparationProgress(val filled: Int, val phase: StoryPreparationPhase) {
    val total: Int get() = 10
}

/** Preparation milestones, not an estimate of the remaining AI questions. No session state is added. */
val DemoState.liveStoryProgress: StoryPreparationProgress?
    get() {
        if (mode != StoryMode.STORY || !Server.liveFor(mode)) return null
        if (!storyReady) {
            // Distinct confirmed story facts light up to six stars. Repeated answers, template changes
            // and an unknown number of follow-up questions must not change the scale or finish a book.
            val facts = slots.count { (key, value) -> key in Server.SLOTS && key != "extra" && value.isNotBlank() }
            return StoryPreparationProgress(facts.coerceAtMost(6), StoryPreparationPhase.COLLECTING)
        }
        if (scene == Scene.BOOK || (scene == Scene.MAKING && (stage as? Stage.Making)?.progress == 1f)) {
            return StoryPreparationProgress(10, StoryPreparationPhase.COMPLETE)
        }
        if (scene == Scene.MAKING) {
            // The live flow reaches MAKING only after finishing the drawing/sound/background work.
            // Its simulated loading animation must never stand in for the actual story response.
            return StoryPreparationProgress(9, StoryPreparationPhase.WRITING)
        }
        return StoryPreparationProgress(7, StoryPreparationPhase.FINISHING)
    }
