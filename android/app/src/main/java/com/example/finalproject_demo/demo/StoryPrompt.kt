package com.example.finalproject_demo.demo

/** 서버 판정이 고른 칸을 먼저 묻는다. 서버가 다음 칸을 비웠을 때만 틀의 빈칸을 사용한다. */
data class StoryPrompt(val slot: String?, val text: String, val templateOnly: Boolean = false)

private val CORE_QUESTIONS = mapOf(
    "place" to "오늘은 어디로 가 볼까?",
    "problem" to "어떤 일이 생겼어?",
    "reaction" to "그래서 어떻게 됐어?",
    "cause" to "왜 그런 일이 생겼을까?",
    "newcomer" to "새로 나온 친구는 누구야?",
    "name" to "그 친구를 뭐라고 부를까?",
    "companion" to "누구와 함께 갔어?",
    "sound" to "그 친구는 어떤 소리를 낼까?",
    "adult" to "함께 있는 사람은 뭐라고 했어?",
    "solution" to "이야기는 어떻게 끝났어?",
)

fun DemoState.nextStoryPrompt(serverQuestion: String? = null, deferredSlot: String? = null): StoryPrompt? {
    if (mode != StoryMode.STORY || storyReady) return null
    fun open(slot: String) = slot != deferredSlot && slot !in storyUnneededSlots && slots[slot].isNullOrBlank()
    // Never asked by the app in a live story (10-05 device round):
    //  - sound once the child has recorded it — the judge read 「친구의 소리를 직접 만들었어요」 as unclear and
    //    asked 「사육사 아저씨는 어떤 소리를 낼까?」 four turns in a row
    //  - companion — the session already asked who is here (할아버지); 「또치와 누가 함께할까?」 came out of nowhere.
    //    It still fills when the child brings someone in on their own
    fun skipped(slot: String) = (slot == "sound" && storySoundAttempted) || slot == "companion"

    // 첫 두 턴은 장소와 사건을 확인한다. 한 답이 둘 다 채웠으면 같은 질문을 되묻지 않는다.
    val probe = when {
        turn == 0 && open("place") -> "place"
        turn < 2 && open("problem") -> "problem"
        else -> null
    }
    val server = storyNextSlot?.takeIf {
        it != deferredSlot && it in CORE_QUESTIONS && it !in storyUnneededSlots && !skipped(it) && (open(it) || it == storyClarificationSlot)
    }
    val slot = probe ?: server
    if (slot != null) return StoryPrompt(slot, serverQuestion?.takeIf { slot == server && it.isNotBlank() } ?: CORE_QUESTIONS.getValue(slot))

    // 템플릿 고유 질문은 12개 공통 칸이 아니다. 답을 앱 책의 해당 칸에만 둔다.
    val local = template?.let { (it.plot + it.ending).firstOrNull(::open) }
    if (local != null) return StoryPrompt(local, "그다음에는 무슨 일이 있었어?", templateOnly = true)

    // 응답이 불완전할 때의 최소 복구. 12칸의 순서를 정상 흐름에 강요하지 않는다.
    val missing = listOf("place", "problem", "reaction", "cause", "solution").firstOrNull(::open)
    if (missing != null) return StoryPrompt(missing, CORE_QUESTIONS.getValue(missing))
    return StoryPrompt(null, "이야기를 조금 더 들려줄래?")
}

/** 시간과 대리 선택 횟수로 끝내지 않는다. story_ready만 책 제작을 시작한다. */
fun DemoState.storyEndCondition(): String? = when {
    mode != StoryMode.STORY -> null
    storyReady -> "story_ready"
    else -> null
}

/** 실제 아이의 답만 채운다. 빈칸을 넘긴 사실은 아이가 말했다는 기록으로 남기지 않는다. */
fun DemoState.recordTemplateAnswer(prompt: StoryPrompt, value: String, by: String) {
    if (!prompt.templateOnly || prompt.slot == null || value.isBlank()) return
    slots[prompt.slot] = value.trim()
    slotBy[prompt.slot] = by
}
