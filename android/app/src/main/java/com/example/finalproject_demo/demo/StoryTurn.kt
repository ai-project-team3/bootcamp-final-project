package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask

/** 서버 판정을 동화 모드의 자료와 다음 질문에 반영한다. 서버 호출과 이름 가리기는 공통 경로가 담당한다. */
fun DemoState.applyStoryVerdict(verdict: Server.Verdict, by: String) {
    if (mode != StoryMode.STORY) return
    if (verdict.reason == "blocked_by_filter") return
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
            it !in storyUnneededSlots && (slots[it].isNullOrBlank() || verdict.unclear)
    }
    storyClarificationSlot = storyNextSlot?.takeIf { verdict.unclear }
}

suspend fun Director.askStory(
    question: Question, askedSlot: String?,
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Reply {
    var currentQuestion = question
    val conversationStage = s.stage
    while (true) {
    s.stage = conversationStage
    val reply = ask(currentQuestion)
    if (!Server.liveFor(s.mode)) return reply
    val utterance = when (reply) {
        is Reply.Spoke -> reply.text
        is Reply.Tapped -> reply.label
        else -> return reply
    }
    val by = if (reply is Reply.Spoke) "child" else if ((reply as Reply.Tapped).byMascot) "mascot" else "card"
    var response = s.exchangeStoryTurn(askedSlot, question.text, utterance, by, request)
    while (response?.verdict == null) {
        inputs(false, false)
        s.stage = Stage.Confirm(Art.Mascot, "다시 연결", "방으로")
        say("연결이 잠깐 끊겼어. 방금 이야기는 기억하고 있어. 다시 해 볼까?")
        buttons(DemoBtn("다시 연결") { send(Reply.Tapped("ok", "다시 연결")) },
            DemoBtn("방으로") { send(Reply.Tapped("no", "방으로")) })
        if (awaitValue("ok", "no") == "no") {
            goHome()
            throw kotlinx.coroutines.CancellationException("Story connection retry declined")
        }
        s.stage = Stage.Making("이야기를 다시 연결하는 중…")
        buttons()
        response = s.exchangeStoryTurn(askedSlot, question.text, utterance, by, request)
    }
    if (response.verdict!!.reason == "blocked_by_filter") {
        log("서버 안전 판정으로 답을 책 재료에서 제외 · 다른 이야기로 이어가기")
        currentQuestion = question.copy(text = "다른 생각도 들려줄래? ${question.text}")
        continue
    }
    response?.verdict?.fills?.filter { it.first in Server.SLOTS && it.second.isNotBlank() }?.forEach { (slot, value) ->
        event("slot_filled", "slot" to slot, "value" to value, "source" to by)
    }
    s.storyServerQuestion = response?.line?.question
    val line = response?.line
    val reaction = listOfNotNull(line?.ack?.takeIf(String::isNotBlank), line?.expand?.takeIf(String::isNotBlank))
        .joinToString(" ")
    if (reaction.isNotBlank()) {
        say(reaction)
        pause(600)
    }
    // Real STT replies carry no scripted Answer. Keep the child's exact words for the
    // existing recorder and attach only the signals the server actually returned.
    if (reply !is Reply.Spoke) return reply
    val verdict = response?.verdict
    return reply.copy(answer = Answer(
        text = reply.text,
        reason = verdict?.s1Reason == true,
        el = if (verdict?.s2Addition == true) setOf("추가") else emptySet(),
        emo = verdict?.emotion.orEmpty(),
    ))
    }
}

suspend fun DemoState.exchangeTurn(
    mode: String, askedSlot: String?, question: String, utterance: String,
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Server.TurnResult? {
    if (utterance.isBlank()) return null
    val mask = nameMask()
    val response = request(Server.Turn(
        mode = mode,
        slots = mask.maskSlots(if (mode == "story") storyServerInput().slots else slots),
        askedSlot = askedSlot?.takeIf { it in Server.SLOTS },
        question = mask.mask(question),
        utterance = mask.mask(utterance),
        turn = turn,
        template = if (mode == "story") templateKey else null,
        level = level.name.lowercase(),
    )) ?: return null
    val verdict = response.verdict?.copy(
        fills = response.verdict.fills.map { (slot, value) -> slot to mask.unmask(value) },
    )
    val line = response.line?.let {
        Server.Line(mask.unmask(it.ack), it.expand?.let(mask::unmask), it.question?.let(mask::unmask))
    }
    return Server.TurnResult(verdict, line)
}

/** Story owns how to apply the result; other modes use exchangeTurn without changing their state. */
suspend fun DemoState.exchangeStoryTurn(
    askedSlot: String?, question: String, utterance: String,
    by: String = "child",
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Server.TurnResult? {
    if (mode != StoryMode.STORY) return null
    return exchangeTurn("story", askedSlot, question, utterance, request)?.also { response ->
        response.verdict?.let { applyStoryVerdict(it, by) }
    }
}
