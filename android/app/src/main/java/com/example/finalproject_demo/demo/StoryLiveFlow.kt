package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.HeroAttr
import kotlinx.coroutines.withTimeoutOrNull

/** The live conversation enters here once; script scenes remain available with the switch off. */
suspend fun Director.liveStoryConversation() {
    if (s.storyStartedAtMs == 0L) s.storyStartedAtMs = System.currentTimeMillis()
    val remaining = (15 * 60 * 1000L - (System.currentTimeMillis() - s.storyStartedAtMs)).coerceAtLeast(1)
    val completed = withTimeoutOrNull(remaining) {
        while (true) {
            val end = s.storyEndCondition(s.storyStartedAtMs, System.currentTimeMillis())
            if (end != null) { s.endReason = end; break }
            val prompt = s.nextStoryPrompt(s.storyServerQuestion) ?: break
            s.stage = Stage.World(listOf(
                WorldItem(Art.HeroArt(s.heroAttr ?: HeroAttr()), 0.25f, 0.32f, 0.11f, depth = 1f),
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
                val additions = (s.template?.let { it.plot + it.ending }.orEmpty())
                    .mapNotNull { slot -> s.slots[slot]?.let { "$slot: $it" } }
                s.slots["extra"] = additions.joinToString("; ")
                s.slotBy["extra"] = by
                event("slot_filled", "slot" to "extra", "of" to prompt.slot, "value" to value, "source" to by)
            } else if (by != "child" && prompt.slot != null) {
                // A visible card or an audible mascot choice is a real choice, not child speech.
                s.slots[prompt.slot] = value
                s.slotBy[prompt.slot] = by
                s.storyNextSlot = null
                s.storyServerQuestion = null
                event("slot_filled", "slot" to prompt.slot, "value" to value, "source" to by)
            }
            s.mascotPicks = if (by == "mascot") s.mascotPicks + 1 else 0
            if (by != "mascot") judge(variant, reply, question.text)
            syncStoryPresentation()
            // The third conversation turn chooses the local template. An early server finish
            // still needs a page plan, but does not force extra questions just to reach turn 3.
            if (s.templateKey == null && (s.turn >= 3 || s.storyReady)) decideTemplate("서버 대화")
            mark("live:${prompt.slot ?: "extra"}")
        }
        true
    }
    if (completed == null) s.endReason = "time_limit"
    if (s.templateKey == null) decideTemplate("대화 종료")
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
