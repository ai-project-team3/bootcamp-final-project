package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.*

/** The live conversation enters here once; script scenes remain available with the switch off. */
suspend fun Director.liveStoryConversation() = coroutineScope {
    var imagePlace: String? = null
    var imageJob: Job? = null
    var backgroundPending = false
    var waitingConversation: Stage.Show? = null
    var friendDrawingPrepared = false

    // 친구는 아이가 그렸을 때만 선다 — 안 그렸으면 friendArt 가 대본의 기본 낙서라
    // 배경이 생기면 오른쪽에 「이상한 애」가 늘 떠 있었다 (10-02 조장 실기기)
    fun conversationWorld() = Stage.World(listOfNotNull(
        WorldItem(s.storyHeroArt, 0.25f, 0.32f, 0.11f, depth = 1f),
        if (s.drawing.isNotEmpty()) WorldItem(s.friendArt, 0.72f, 0.32f, 0.13f, depth = 0.9f) else null,
    ))

    fun showConversation() {
        // World and Making both render bgName, whose unknown-place fallback is snow.
        val stage = if (s.slots["place"].isNullOrBlank() || backgroundPending)
            Stage.Show(s.storyHeroArt) else conversationWorld()
        s.stage = stage
        // Compose may retain an equal stage object; capture the one actually displayed.
        waitingConversation = s.stage as? Stage.Show
    }

    fun updateBackground() {
        val place = s.slots["place"]?.takeIf(String::isNotBlank) ?: return
        if (imagePlace == place) return
        imagePlace = place
        imageJob?.cancel()
        backgroundPending = true
        s.storyBackground = null
        if (s.stage is Stage.World || s.stage === waitingConversation) showConversation()
        val mask = s.nameMask()
        imageJob = launch {
            // 8초 「배경 그림은 조금 뒤에 올 거야!」 자막은 뺐다(10-02 조장) — 대화가 이어지는 중이라 끼어드는 말이었다
            val reminder = launch {
                delay(8_000)
                if (s.place == place) log("배경 생성 8초 경과 · 대화는 계속 진행")
            }
            try {
                val png = withTimeoutOrNull(15_000) { Server.image(mask.mask(place), "story") }
                val saved = png?.let { withContext(Dispatchers.IO) { saveStoryImage(it) } }
                currentCoroutineContext().ensureActive()
                if (s.place == place && imagePlace == place) {
                    s.storyBackground = saved
                    backgroundPending = false
                    // Only refresh our waiting conversation, never a drawing/card/retry stage.
                    if (waitingConversation != null && s.stage === waitingConversation) {
                        waitingConversation = null
                        s.stage = conversationWorld()
                    }
                    log(if (saved == null) "배경 생성 실패 또는 15초 경과 → 프리셋 유지" else "대화 중 생성 배경 저장 · 무대와 책에 연결")
                }
            } finally { reminder.cancel() }
        }
    }
    if (s.storyStartedAtMs == 0L) s.storyStartedAtMs = System.currentTimeMillis()
    // 되돌리기 · 앞으로 가기 — 잘못 알아들은 답을 직전 차례째로 무른다 (10-02 · demo/TurnHistory)
    val history = TurnHistory(s)
    run {
        while (true) {
            val end = s.storyEndCondition()
            if (end != null) { s.endReason = end; break }
            val prompt = s.nextStoryPrompt(s.storyServerQuestion) ?: break
            if (prompt.slot == "sound" && !s.storySoundAttempted) {
                recordStorySound()
                notifyStorySoundChoice(prompt)
                continue
            }
            showConversation()
            val variant = liveVariant(prompt)
            val base = variant.toQuestion(s)
            val question = base.copy(text = if (prompt.templateOnly) base.text else prompt.text)
            history.before()
            val reply = askStory(question, prompt.slot)
            if (TurnHistory.isNav(reply)) {
                val undo = (reply as Reply.Tapped).value == TurnHistory.UNDO
                if (if (undo) history.undo() else history.redo()) {
                    event(if (undo) "turn_undone" else "turn_redone")
                    log(if (undo) "↩ 직전 차례를 되돌림 — 같은 질문을 다시" else "↪ 되돌린 차례를 다시 적용")
                    say(if (undo) "그럼 다시 말해 줄래?" else "좋아, 아까 그 이야기로 갈게!")
                    updateBackground()
                }
                continue
            }
            // Silence leaves this slot open; it is neither speech nor a mascot choice.
            if (reply !is Reply.Spoke && reply !is Reply.Tapped) continue
            val by = when {
                reply is Reply.Spoke -> "child"
                reply is Reply.Tapped && !reply.byMascot -> "card"
                else -> "mascot"
            }
            val value = when (reply) {
                is Reply.Spoke -> reply.text
                is Reply.Tapped -> reply.label
                else -> error("Only an actual answer reaches slot filling")
            }
            if (prompt.templateOnly) {
                s.recordTemplateAnswer(prompt, value, by)
                event("slot_filled", "slot" to "extra", "of" to prompt.slot, "value" to value, "source" to by)
            } else if (by != "child" && prompt.slot != null && s.slots[prompt.slot].isNullOrBlank()) {
                // A visible card or an audible mascot choice is a real choice, not child speech.
                s.slots[prompt.slot] = value
                s.slotBy[prompt.slot] = by
                event("slot_filled", "slot" to prompt.slot, "value" to value, "source" to by)
            }
            s.mascotPicks = if (by == "mascot") s.mascotPicks + 1 else 0
            s.syncStoryPresentation()
            if (by != "mascot") judge(variant, reply, question.text)
            updateBackground()
            if (!friendDrawingPrepared && !s.slots["newcomer"].isNullOrBlank()) {
                prepareStoryFriendDrawing()
                friendDrawingPrepared = true
            }
            // The third conversation turn chooses the local template. An early server finish
            // still needs a page plan, but does not force extra questions just to reach turn 3.
            if (s.templateKey == null && (s.turn >= 3 || s.storyReady)) decideTemplate("서버 대화")
            mark("live:${prompt.slot ?: "extra"}")
            history.done()
        }
        true
    }
    if (s.templateKey == null) decideTemplate("대화 종료")
    if (!s.storySoundAttempted) recordStorySound()
    if (imageJob?.isCompleted == false) {
        inputs(false, false)
        buttons()
        waitingConversation = null
        s.stage = Stage.Show(s.storyHeroArt, "이야기 그림을 마무리하는 중…")
    }
    imageJob?.join()
    log("동화 대화 종료: ${s.endReason} · ${s.turn}턴 · 실제 판정으로 채운 칸 ${s.slots.keys}")
    go(Scene.MAKING)
}

private fun Director.liveVariant(prompt: StoryPrompt): QVariant {
    val slot = prompt.slot.orEmpty()
    val bankSlot = if (slot == "solution") "resolve" else slot
    if (BANK.any { it.slot == bankSlot }) return s.pick(bankSlot)
    return QVariant("live:$slot", slot, Level.entries.toSet(), Kind.EASY, "서버가 고른 다음 이야기 칸",
        text = { prompt.text }, easier = { "천천히 생각해 봐. ${prompt.text}" },
        answers = { emptyList() })
}

/** Project confirmed slots into the existing book/world fields; never fill missing child facts. */
internal fun DemoState.syncStoryPresentation() {
    val s = this
    s.slots["place"]?.takeIf(String::isNotBlank)?.let { place ->
        val theme = THEMES.firstOrNull { it.key == place || it.label == place }
        s.themeKey = theme?.key ?: "dino"
        s.placeLabel = place
        s.generatedBg = theme == null
        s.place = place
    }
    s.problem = s.slots["problem"]
    s.cause = s.slots["cause"]
    s.cause?.let {
        s.causeLine = it
        s.causeKind = storyCauseKind(it)
    }
    s.newcomer = s.slots["newcomer"]
    s.newcomer?.let { s.newcomerKind = it }
    s.slots["name"]?.takeIf(String::isNotBlank)?.let { s.friendName = it }
    s.solution = s.slots["solution"]
    s.solution?.let {
        s.solutionLine = it
        storySolutionProp(it)?.let { (kind, item) -> s.solutionKey = kind; s.solutionItem = item }
    }
    s.sound = s.slots["sound"]
    s.sound?.let { s.soundLine = it }
}

private fun storyCauseKind(text: String): String = when {
    listOf("길을 잃", "길이 헷갈", "길을 못").any { it in text } -> "lost"
    listOf("배고", "배가 고", "먹고 싶").any { it in text } -> "hungry"
    listOf("아파", "아팠", "다쳤", "다쳐").any { it in text } -> "hurt"
    "장난" in text -> "prank"
    listOf("힘이 세", "힘자랑", "힘을 자랑").any { it in text } -> "strong"
    listOf("인사", "안녕").any { it in text } -> "hello"
    listOf("외로", "외롭", "심심", "친구가 없").any { it in text } -> "lonely"
    listOf("놀고 싶", "같이 놀").any { it in text } -> "play"
    else -> "unknown"
}

private fun storySolutionProp(text: String): Pair<String, String>? = when {
    "딸기" in text -> "share" to "strawberry"
    "초대" in text -> "invite" to "invite"
    "풍선" in text -> "play" to "balloon"
    "반창고" in text -> "help" to "bandaid"
    "그림책" in text || "책을" in text -> "share" to "picturebook"
    "블록" in text -> "help" to "block"
    "노래" in text || "춤" in text -> "dance" to "note"
    "돌" in text || "보석" in text -> "gift" to "gem"
    "별" in text -> "star" to "star"
    else -> null
}
