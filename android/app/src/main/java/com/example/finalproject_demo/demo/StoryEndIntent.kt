package com.example.finalproject_demo.demo

/**
 * The child says the story is over — 「이제 끝이야」 (10-09 device · Laya v5). The judge returned fills=[] ready=false
 * next=sound, and Otto kept asking 「이야기를 조금 더 들려줄래?」; the book was never made.
 *
 * Only a whole answer that is an ending word counts (「끝이야」 · 「이제 그만할래」 · 「다 했어」); 「끝까지 달려갔어」 is a story.
 */
private val END_WORDS = Regex(
    "^(?:이제|그럼|이걸로|여기서|오늘은|이야기는?|우리)*" +
        "(?:끝|끝이야|끝이에요|끝이다|끝났어|끝났어요|끝났다|끝낼래|끝낼래요|끝내자|끝낼게|끝낼게요|" +
        "그만|그만할래|그만할래요|그만하자|그만할게|그만할게요|다했어|다했어요|다끝났어|이제됐어|됐어|됐어요)$"
)

internal fun storyEndIntent(text: String): Boolean =
    END_WORDS.matches(text.trim().replace(Regex("[\\s.!?,…~]+"), ""))

/** What a book needs before the child may end it — something happened and it was solved */
internal val STORY_END_CORE = listOf("problem", "solution")

/** The gentle closing question when the child wants to end but the book still misses [slot] */
internal val STORY_CLOSING_QUESTIONS = mapOf(
    "problem" to "끝내기 전에, 어떤 일이 있었는지 하나만 들려줄래?",
    "solution" to "좋아, 그럼 마지막으로! 이야기는 어떻게 끝났어?",
)

/**
 * After a turn the judge did not end: the child's ending words end the story when the core is there; otherwise the
 * next question is the closing one for the missing core slot. Returns whether it acted.
 */
internal fun DemoState.applyStoryEndIntent(utterance: String, by: String): Boolean {
    if (mode != StoryMode.STORY || storyReady) return false
    if (by != "child" || !storyEndIntent(utterance)) {
        // the closing question was answered — the child already said the story is over, so the book is made now, filled
        // or not; asking once more made the child say 「끝이야」 a second time (10-09 device · laya 4th round)
        if (!storyEndAsked) return false
        endReason = "story_ready"
        com.example.finalproject_demo.net.Trace.line("story_verdict", "closing question answered → story_ready" +
            (STORY_END_CORE.firstOrNull { slots[it].isNullOrBlank() }?.let { " ([$it] still empty)" } ?: ""))
        return true
    }
    val missing = STORY_END_CORE.firstOrNull { slots[it].isNullOrBlank() }
    // asked to end a second time — the child's wish wins; the book is made from what was told
    if (missing == null || storyEndAsked) {
        endReason = "story_ready"
        com.example.finalproject_demo.net.Trace.line("story_verdict",
            "child ended the story → story_ready" + if (missing != null) " (asked twice · [$missing] empty)" else "")
        return true
    }
    storyEndAsked = true
    storyNextSlot = missing; storyClarificationSlot = null
    storyServerQuestion = STORY_CLOSING_QUESTIONS.getValue(missing); storyAnswerOptions = null
    com.example.finalproject_demo.net.Trace.line("story_verdict", "child wants to end — [$missing] empty → closing question")
    return true
}
