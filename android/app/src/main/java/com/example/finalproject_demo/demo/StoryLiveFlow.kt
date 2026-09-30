package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.*

/** The live conversation enters here once; script scenes remain available with the switch off. */
suspend fun Director.liveStoryConversation() = coroutineScope {
    var imagePlace: String? = null
    var imageJob: Job? = null
    var friendDrawingPrepared = false

    fun updateBackground() {
        val place = s.slots["place"]?.takeIf(String::isNotBlank) ?: return
        if (imagePlace == place) return
        imagePlace = place
        imageJob?.cancel()
        s.storyBackground = null
        val mask = s.nameMask()
        imageJob = launch {
            val reminder = launch {
                delay(8_000)
                if (s.place == place) {
                    s.line += "\n배경 그림은 조금 뒤에 올 거야!"
                    log("배경 생성 8초 경과 · 대화는 계속 진행")
                }
            }
            try {
                val png = withTimeoutOrNull(15_000) { Server.image(mask.mask(place), "story") }
                val saved = png?.let { withContext(Dispatchers.IO) { saveStoryImage(it) } }
                if (s.place == place) {
                    s.storyBackground = saved
                    log(if (saved == null) "배경 생성 실패 또는 15초 경과 → 프리셋 유지" else "대화 중 생성 배경 저장 · 무대와 책에 연결")
                }
            } finally { reminder.cancel() }
        }
    }
    if (s.storyStartedAtMs == 0L) s.storyStartedAtMs = System.currentTimeMillis()
    run {
        while (true) {
            val end = s.storyEndCondition(s.storyStartedAtMs, System.currentTimeMillis())
            if (end != null) { s.endReason = end; break }
            val prompt = s.nextStoryPrompt(s.storyServerQuestion) ?: break
            s.stage = Stage.World(listOf(
                WorldItem(s.storyHeroArt, 0.25f, 0.32f, 0.11f, depth = 1f),
                WorldItem(s.friendArt, 0.72f, 0.32f, 0.13f, depth = 0.9f),
            ))
            val variant = liveVariant(prompt)
            val base = variant.toQuestion(s)
            val question = base.copy(text = if (prompt.templateOnly) base.text else prompt.text)
            val reply = askStory(question, prompt.slot)
            val by = when {
                reply is Reply.Spoke -> "child"
                reply is Reply.Tapped && !reply.byMascot -> "card"
                else -> "mascot"
            }
            val value = when (reply) {
                is Reply.Spoke -> reply.text
                is Reply.Tapped -> reply.label
                else -> "아직 정하지 않았어요"
            }
            if (prompt.templateOnly) {
                s.recordTemplateAnswer(prompt, value, by)
                event("slot_filled", "slot" to "extra", "of" to prompt.slot, "value" to value, "source" to by)
            } else if (by != "child" && prompt.slot != null && s.slots[prompt.slot].isNullOrBlank()) {
                // A visible card or an audible mascot choice is a real choice, not child speech.
                s.slots[prompt.slot] = value
                s.slotBy[prompt.slot] = by
                s.storyNextSlot = null
                s.storyServerQuestion = null
                event("slot_filled", "slot" to prompt.slot, "value" to value, "source" to by)
            }
            s.mascotPicks = if (by == "mascot") s.mascotPicks + 1 else 0
            syncStoryPresentation()
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
        }
        true
    }
    if (s.templateKey == null) decideTemplate("대화 종료")
    if (imageJob?.isCompleted == false) {
        inputs(false, false)
        buttons()
        s.stage = Stage.Making("이야기 그림을 마무리하는 중…")
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
        answers = { emptyList() }, fallback = { Answer("아직 정하지 않았어요", "아직 정하지 않았어요") })
}

/** Project confirmed slots into the existing book/world fields; never fill missing child facts. */
private fun Director.syncStoryPresentation() {
    s.slots["place"]?.takeIf(String::isNotBlank)?.let { place ->
        val theme = THEMES.firstOrNull { it.key == place || it.label == place }
        s.themeKey = theme?.key ?: "dino"
        s.placeLabel = place
        s.generatedBg = theme == null
        s.place = place
    }
    s.problem = s.slots["problem"]
    s.cause = s.slots["cause"]
    s.cause?.let { s.causeLine = it }
    s.newcomer = s.slots["newcomer"]
    s.newcomer?.let { s.newcomerKind = it }
    s.slots["name"]?.takeIf(String::isNotBlank)?.let { s.friendName = it }
    s.solution = s.slots["solution"]
    s.solution?.let { s.solutionLine = it }
    s.sound = s.slots["sound"]
    s.sound?.let { s.soundLine = it }
}
