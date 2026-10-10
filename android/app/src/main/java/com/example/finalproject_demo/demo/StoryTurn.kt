package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Trace
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.*

internal data class StoryOptions(val slot: String?, val question: String, val values: List<String>)

/** Only standalone refusals, not a story sentence such as "몰라서 엄마에게 물어봤어". */
internal fun storyNonAnswer(text: String): Boolean {
    val normalized = text.trim().replace(Regex("[\\s.!?,…~]+"), "")
    return normalized in setOf("몰라", "몰라요", "몰라이제", "이제몰라", "모르겠어", "모르겠어요",
        "잘모르겠어", "잘모르겠어요", "더없어", "더없어요", "더없다", "이제더없어", "이제더없어요",
        "생각안나", "생각이안나", "생각안나요", "생각이안나요")
}

private fun DemoState.optionsFor(slot: String?, question: String): List<String> =
    storyAnswerOptions?.takeIf {
        it.slot == slot && it.question == question && storyServerQuestion == question
    }?.values.orEmpty()

/** Keep candidates with their question, so a reset or a different slot cannot reuse them. */
private fun DemoState.rememberStoryQuestion(response: Server.TurnResult) {
    // A null next slot can still carry a concrete follow-up. A rejected named slot
    // must not reopen through that fallback (filled, unneeded, companion or recorded sound).
    val next = response.verdict?.nextSlot
    val accepted = next == null || (storyNextSlot != null && next != "companion" &&
        !(next == "sound" && storySoundAttempted) && !(next == "adult" && !hasPartner))
    storyServerQuestion = response.line?.question?.takeIf { accepted && !storyReady }
    storyAnswerOptions = null
    val line = response.line ?: return
    val question = storyServerQuestion?.takeIf(String::isNotBlank) ?: return
    val options = line.options?.filter(String::isNotBlank)?.distinct()?.take(3).orEmpty()
    if (options.isNotEmpty()) storyAnswerOptions = StoryOptions(storyNextSlot, question, options)
}

/** 서버 판정을 동화 모드의 자료와 다음 질문에 반영한다. 서버 호출과 이름 가리기는 공통 경로가 담당한다. */
/** What [applyStoryVerdict] did with who-slot sentences — the slot to ask once more, and the sentences it turned away */
data class StoryWhoGuard(val reask: String? = null, val rejected: List<String> = emptyList())

fun DemoState.applyStoryVerdict(verdict: Server.Verdict, by: String, askedSlot: String? = null): StoryWhoGuard {
    if (mode != StoryMode.STORY) return StoryWhoGuard()
    if (verdict.reason == "blocked_by_filter") return StoryWhoGuard()
    require(by in setOf("child", "card", "mascot"))

    var reask: String? = null
    val rejected = mutableListOf<String>()
    for ((slot, rawValue) in verdict.fills) {
        var value = rawValue.trim()
        var source = by
        if (slot !in Server.SLOTS || value.isEmpty() || (slot == "adult" && !hasPartner)) continue
        if (slot in STORY_WHO_SLOTS && storyLooksLikeSentence(value)) {
            val said = value
            rejected += said
            value = (if (slot == "newcomer") storyNameInSentence(said) else null) ?: run {
                keepWhoSentence(said, by)
                // asked once more; after that the newcomer is just 「새 친구」 and a name stays empty.
                // Filled while another slot was asked, it is left empty — no friend is made up
                if (slot != askedSlot) ""
                else if (storyWhoReasked.add(slot)) { reask = slot; "" }
                // the app chose 「새 친구」, the child did not say it — mascot (rule 5 · #375 review)
                else if (slot == "newcomer") STORY_NEW_FRIEND.also { source = "mascot" } else ""
            }
            Trace.line("story_verdict", "who-slot [$slot] got a sentence → ${if (value.isEmpty()) "not filled" else "「$value」"}" +
                if (reask == slot) " · asked again" else "")
            if (value.isEmpty()) continue
        }
        slots[slot] = value
        slotBy[slot] = source
    }

    verdict.noLongerNeeded?.takeIf { it in Server.SLOTS }?.let {
        if (it !in storyUnneededSlots) storyUnneededSlots += it
    }

    if (verdict.storyReady) endReason = "story_ready"
    storyNextSlot = verdict.nextSlot?.takeIf {
        !storyReady && it in Server.SLOTS &&
            it !in storyUnneededSlots && (slots[it].isNullOrBlank() || verdict.unclear) &&
            // a who-slot already asked once more is not asked again — the judge kept choosing an empty name (#375 review)
            !(it in STORY_WHO_SLOTS && it in storyWhoReasked && it != reask)
    }
    storyClarificationSlot = storyNextSlot?.takeIf { verdict.unclear }
    return StoryWhoGuard(reask, rejected)
}

/** A turned-away sentence stays in the story as something that happened, with its own source — unless a slot holds it */
private fun DemoState.keepWhoSentence(said: String, by: String) {
    if (slots.values.any { said in it }) return
    val key = WHO_SAID_PREFIX + "%02d".format(slots.keys.count { it.startsWith(WHO_SAID_PREFIX) } + 1)
    slots[key] = said
    slotBy[key] = by
}

/** The question for a who-slot the child answered with a sentence — no server question names that sentence */
private val WHO_REASK = mapOf(
    "newcomer" to "그때 만난 친구는 누구였어?",
    "name" to "그 친구 이름은 뭐라고 부를까?",
)

suspend fun Director.askStory(
    question: Question, askedSlot: String?,
    singleAttempt: Boolean = false,
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Reply {
    var currentQuestion = question
    val conversationStage = s.stage
    while (true) {
        s.stage = conversationStage
        // Once a slot is settled, "더 없어" is an ending intent for the server to judge.
        val reply = if (Server.liveFor(s.mode)) askLiveStoryReply(currentQuestion, s.optionsFor(askedSlot, question.text), singleAttempt) {
            !singleAttempt && !s.storyReady && askedSlot != null &&
                s.slots[askedSlot].isNullOrBlank() && storyNonAnswer(it)
        }
            else ask(currentQuestion)
        if (!Server.liveFor(s.mode) || TurnHistory.isNav(reply)) return reply
        val utterance = when (reply) {
            is Reply.Spoke -> reply.text
            is Reply.Tapped -> reply.label
            else -> return reply
        }
        val by = if (reply is Reply.Spoke) "child" else if ((reply as Reply.Tapped).byMascot) "mascot" else "card"
        val response = exchangeStoryTurnWithRetry(askedSlot, question.text, utterance, by, request)
        if (response.verdict!!.reason == "blocked_by_filter") {
            log("서버 안전 판정으로 답을 책 재료에서 제외 · 다른 이야기로 이어가기")
            currentQuestion = question.copy(text = "다른 생각도 들려줄래? ${question.text}")
            continue
        }
        response.verdict.fills.filter { it.first in Server.SLOTS && it.second.isNotBlank() &&
            (it.first != "adult" || s.hasPartner) }.forEach { (slot, value) ->
            // a who-slot keeps what the guard put there (StoryNameGuard.kt) — nothing when it is asked again,
            // and the app's 「새 친구」 with its mascot source
            if (slot !in STORY_WHO_SLOTS) event("slot_filled", "slot" to slot, "value" to value, "source" to by)
            else s.slots[slot]?.takeIf(String::isNotBlank)?.let {
                event("slot_filled", "slot" to slot, "value" to it, "source" to (s.slotBy[slot] ?: by))
            }
        }
        val line = response.line
        // the question's voice is made while the ack is voiced and played, not after (10-05 trace: −2.5 s a turn)
        line?.question?.let { prefetchSpeech(it) }
        val reaction = storyReaction(line?.ack, line?.expand)
        if (reaction.isNotBlank()) {
            say(reaction)
            pause(600)
        }
        // Real STT replies carry no scripted Answer. Keep the child's exact words for the
        // existing recorder and attach only the signals the server actually returned.
        if (reply !is Reply.Spoke) return reply
        val verdict = response.verdict
        return reply.copy(answer = Answer(
            text = reply.text,
            reason = verdict.s1Reason,
            el = if (verdict.s2Addition) setOf("추가") else emptySet(),
            emo = verdict.emotion.orEmpty(),
        ))
    }
}

/** The same question gets one easier attempt before its server candidates are revealed. */
private suspend fun Director.askLiveStoryReply(
    question: Question, options: List<String>, singleAttempt: Boolean,
    spokenIsNonAnswer: (String) -> Boolean,
): Reply {
    val open = question.copy(
        kind = if (question.kind == Kind.CHOICE) Kind.EASY else question.kind,
        choices = emptyList(), noCards = true, fallback = null, hint = null,
        easierText = if (singleAttempt) null else question.easierText,
        ladder = if (singleAttempt) emptyList() else listOf(question.easierText ?: "천천히 생각해 봐. ${question.text}"),
    )
    val reply = ask(open, silentFollowUp = options.isNotEmpty() && !singleAttempt,
        spokenIsNonAnswer = spokenIsNonAnswer)
    if (reply != Reply.Silent || options.isEmpty() || singleAttempt) return reply
    val cards = options.map { Card(it, Art.Mascot, it) }
    try {
        setListening(question.copy(choices = cards))
        // Reorder the same safe choices at most three times; never invent new candidates.
        repeat(4) { round ->
            val chosen = awaitStoryCardReply {
                s.stage = Stage.CardsRow(if (round == 0) cards else cards.shuffled(), drawerHint = false)
                inputs(mic = true, next = true)
                say(if (round == 0) "이 중에서 골라 볼까? ${options.joinToString(", ")}." else "다시 보고 골라도 돼.")
                log("서버 답 후보 카드 · 교체 ${round}회")
                buttons(DemoBtn("안 고름") { send(Reply.Silent) })
            }
            if (chosen != null && TurnHistory.isNav(chosen)) return chosen
            when (chosen) {
                is Reply.Spoke -> {
                    acceptSpoken(chosen.text)
                    if (!spokenIsNonAnswer(chosen.text)) return chosen
                }
                is Reply.Tapped -> if (chosen.value in options && !chosen.byMascot) {
                    val card = Reply.Tapped(chosen.value, chosen.value)
                    acceptTap(card)
                    return card
                }
                else -> Unit
            }
        }
        val first = options.first()
        say("그럼 오또가 고를게! $first!")
        log("마스코트가 첫 서버 후보를 골라줌: $first · source=mascot")
        s.stage = (s.stage as? Stage.CardsRow)?.copy(picked = first) ?: s.stage
        pause(1300)
        return Reply.Tapped(first, first, byMascot = true)
    } finally {
        setListening(null)
        inputs(mic = false, next = false)
        buttons()
    }
}

/** Once recording starts, wait through STT and unheard retries; one receiver owns the input. */
private suspend fun Director.awaitStoryCardReply(show: () -> Unit): Reply? = coroutineScope {
    // Drain before exposing the next round. A quick reply to visible cards belongs to
    // this round and must survive the voice wait, including a spoken non-answer.
    val receiver = async(start = CoroutineStart.UNDISPATCHED) { awaitReplyShowing(show) }
    try {
        awaitVoice()
        if (!s.timerOn) return@coroutineScope receiver.await()
        var remaining = 7.0
        while (remaining > 0) {
            val received = withTimeoutOrNull((100 * s.speed).toLong().coerceAtLeast(1)) { receiver.await() }
            if (received != null) return@coroutineScope received
            if (s.micOn) return@coroutineScope receiver.await()
            remaining -= 0.1
        }
        null
    } finally { receiver.cancel() }
}

private suspend fun Director.exchangeStoryTurnWithRetry(
    askedSlot: String?, question: String, utterance: String, by: String,
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Server.TurnResult {
    var response = s.exchangeStoryTurn(askedSlot, question, utterance, by, request)
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
        response = s.exchangeStoryTurn(askedSlot, question, utterance, by, request)
    }
    return response
}

/** Notify the server of a local activity/choice, never its recording or a fabricated spoken reply. */
internal suspend fun Director.notifyStorySoundChoice(prompt: StoryPrompt) {
    if (!Server.liveFor(s.mode) || !s.storySoundAttempted || s.storyReady) return
    val recorded = s.storySoundClip != null
    val utterance = if (recorded) "친구의 소리를 직접 만들었어요" else "친구의 소리는 소리 없이 넘어갈래"
    val response = exchangeStoryTurnWithRetry("sound", prompt.text, utterance, if (recorded) "child" else "card")
    response.line?.ack?.takeIf(String::isNotBlank)?.let { say(it); pause(600) }
}

suspend fun DemoState.exchangeTurn(
    mode: String, askedSlot: String?, question: String, utterance: String,
    request: suspend (Server.Turn) -> Server.TurnResult? = { Server.turn(it) },
): Server.TurnResult? {
    if (utterance.isBlank()) return null
    val mask = nameMask()
    Trace.line("turn", "request asked=$askedSlot · ${utterance.length} chars")
    val response = request(Server.Turn(
        mode = mode,
        slots = mask.maskSlots(if (mode == "story") storyServerInput().slots else slots),
        askedSlot = askedSlot?.takeIf { it in Server.SLOTS },
        question = mask.mask(question),
        utterance = mask.mask(utterance),
        turn = turn,
        // story: the template key. coop: the picked story (#53 B), masked like everything else that leaves
        // the phone, plus the reason the server uses for the question's tense (#53 C). Other modes: neither.
        template = when (mode) {
            "story" -> templateKey
            "coop" -> coopTurnContext()?.let(mask::mask)
            else -> null
        },
        level = level.name.lowercase(),
        reason = if (mode == "coop") coopStoryReason() else null,
        names = mask.names,
        partner = partnerWire(mode),
    )) ?: return null
    val verdict = response.verdict?.copy(
        fills = response.verdict.fills.map { (slot, value) -> slot to mask.unmask(value) },
    )
    val line = response.line?.let {
        Server.Line(mask.unmask(it.ack), it.expand?.let(mask::unmask), it.question?.let(mask::unmask),
            it.options?.map { option -> mask.unmask(option).trim() }?.filter(String::isNotBlank)?.take(3)
                ?.takeIf(List<String>::isNotEmpty))
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
    return exchangeTurn("story", askedSlot, question, utterance, request = request)?.also { response ->
        val guard = response.verdict?.let { applyStoryVerdict(it, by, askedSlot) } ?: StoryWhoGuard()
        rememberStoryQuestion(response)
        val reask = guard.reask
        if (reask != null) {
            storyNextSlot = reask; storyClarificationSlot = null
            storyServerQuestion = WHO_REASK.getValue(reask); storyAnswerOptions = null
        } else if (guard.rejected.any { said -> storyServerQuestion?.contains(said) == true }) {
            // the server's next question names a turned-away sentence (「바람이 불어서 나무가 쓰러졌어는 어떻게 생겼어?」) —
            // never asked: the appearance path would write its head back into the newcomer (#375 review P1)
            Trace.line("story_verdict", "next question names a turned-away sentence → app question")
            storyServerQuestion = storyNextSlot?.let(WHO_REASK::get); storyAnswerOptions = null
            if (storyServerQuestion == null) storyClarificationSlot = null
        }
        val verdict = response.verdict
        Trace.line("story_verdict", "asked=$askedSlot by=$by ready=${verdict?.storyReady} " +
            "next=${verdict?.nextSlot} applied=$storyNextSlot unclear=${verdict?.unclear} " +
            "unneeded=${verdict?.noLongerNeeded} fills=${verdict?.fills?.map { it.first }} " +
            "question=${response.line?.question}")
    }
}

/**
 * The spoken reaction: ack, plus expand only when expand brings something new (10-05 device round).
 * The line model often says the same thing twice — 「또치를 아저씨가 동물원으로 데리고 돌아갔구나. 또치와 아저씨가
 * 동물원으로 돌아갔어.」. Words are compared by their first two letters, so 갔구나 / 갔어 count as the same.
 */
internal fun storyReaction(ack: String?, expand: String?): String {
    val a = ack?.trim().orEmpty()
    val e = expand?.trim().orEmpty()
    if (e.isEmpty()) return a
    if (a.isEmpty()) return e
    fun stems(t: String) = Regex("[가-힣A-Za-z0-9]{2,}").findAll(t).map { it.value.take(2) }.toSet()
    val new = stems(e) - stems(a)
    return if (new.size * 2 < stems(e).size.coerceAtLeast(1)) a else "$a $e"
}
