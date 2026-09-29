package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

/** 서버 판정을 동화 모드의 자료와 다음 질문에 반영한다. 서버 호출과 이름 가리기는 공통 경로가 담당한다. */
fun DemoState.applyStoryVerdict(verdict: Server.Verdict, by: String) {
    if (mode != StoryMode.STORY) return
    require(by in setOf("child", "card", "mascot"))

    for ((slot, rawValue) in verdict.fills) {
        val value = rawValue.trim()
        if (slot !in Server.SLOTS || value.isEmpty()) continue
        slots[slot] = value
        slotBy[slot] = by
    }

    verdict.noLongerNeeded?.takeIf { it in Server.SLOTS && it != "extra" }?.let {
        if (it !in storyUnneededSlots) storyUnneededSlots += it
    }

    if (verdict.storyReady) endReason = "story_ready"
    storyNextSlot = verdict.nextSlot?.takeIf {
        !storyReady && it in Server.SLOTS && it != "extra" &&
            it !in storyUnneededSlots && slots[it].isNullOrBlank()
    }
}
