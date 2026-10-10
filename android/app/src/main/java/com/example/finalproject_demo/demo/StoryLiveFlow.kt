package com.example.finalproject_demo.demo

import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.*

/** The live conversation enters here once; script scenes remain available with the switch off. */
suspend fun Director.liveStoryConversation() = coroutineScope {
    // Home can cancel an acknowledgement after its verdict changed slots but before presentation.
    s.syncStoryPresentation()
    var imagePlace = s.storyBackgroundPlace
    var imageJob: Job? = null
    var backgroundPending = false
    var waitingConversation: Stage.Show? = null
    var friendDrawingPrepared = false
    var deferredSlot: String? = null
    var finalPlaceChecked = false

    // 친구는 아이가 그렸을 때만 선다 — 안 그렸으면 friendArt 가 대본의 기본 낙서라
    // 배경이 생기면 오른쪽에 「이상한 애」가 늘 떠 있었다 (10-02 조장 실기기)
    fun conversationWorld() = s.storyConversationWorld()

    fun refreshCast() {
        if (s.stage is Stage.World) s.stage = conversationWorld()
    }

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
        s.storyBackgroundPlace = null
        if (s.sceneKit != null) {
            log("background route=kit place=$place kit=${s.sceneKit}")
            // 10-05 scene kit: the place is drawn from pre-made felt pieces at once — no /image request, nothing
            // to wait for. The generated background below stays as the documented alternative
            // (`docs/배경_조각_목록.md` §1 (가)); `SceneKits.liveStory = false` brings it back.
            backgroundPending = false
            s.storyBackground = null
            if (s.stage is Stage.World || s.stage === waitingConversation) showConversation()
            log("scene kit ${s.sceneKit} draws the place · no /image request")
            // the book draws one picture, not pieces — save the kit as that picture (#222: a 바닷가 book fell back to snow)
            val kit = SceneKits.all[s.sceneKit] ?: return
            val seed = s.sceneSeed
            imageJob = launch {
                val saved = saveKitPicture(kit, seed)
                if (saved != null && s.slots["place"] == place && s.sceneKit == kit.key) {
                    s.storyBackground = saved
                    s.storyBackgroundPlace = place
                    log("scene kit ${kit.key} saved as the book's picture")
                }
            }
            return
        }
        log("background route=generated place=$place")
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
                val png = withTimeoutOrNull(15_000) { Server.image(mask.mask(place), "story", s.bookStyle) }
                val saved = png?.let { withContext(Dispatchers.IO) { saveStoryImage(it) } }
                currentCoroutineContext().ensureActive()
                if (s.place == place && imagePlace == place) {
                    s.storyBackground = saved
                    s.storyBackgroundPlace = place
                    backgroundPending = false
                    log("background result=${if (saved == null) "preset" else "generated"} place=$place")
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
    // Restart only an unfinished request. Mark it pending before rendering any question,
    // otherwise an interrupted generation can flash the unrelated snow preset on resume.
    updateBackground()
    if (s.storyStartedAtMs == 0L) s.storyStartedAtMs = System.currentTimeMillis()
    // 되돌리기 · 앞으로 가기 — 잘못 알아들은 답을 직전 차례째로 무른다 (10-02 · demo/TurnHistory)
    val history = TurnHistory(s)
    run {
        while (true) {
            val end = s.storyEndCondition()
            val checkPlace = end != null && s.slots["place"].isNullOrBlank() && !finalPlaceChecked
            if (end != null && !checkPlace) { s.endReason = end; break }
            val prompt = if (checkPlace) StoryPrompt("place", "이 이야기는 어디에서 있었어?")
                else s.nextStoryPrompt(s.storyServerQuestion, deferredSlot) ?: break
            if (prompt.slot == "sound" && !s.storySoundAttempted) {
                recordStorySound()
                notifyStorySoundChoice(prompt)
                continue
            }
            // 10-05 device: the judge asks what the newcomer looks like (judge prompt example 「종류만 말했으니
            // 생김새를 더 묻는다」) and then the app asked to draw it as well. The drawing is that answer.
            // 10-05 second round: also when the slot is still empty — 「바늘괴물은 어떻게 생겼어?」 asked it for the
            // first time (the monster was only in the problem), then the drawing came on top of the spoken answer
            val looks = prompt.slot == "newcomer" && Regex("생겼|모습|생김새").containsMatchIn(prompt.text)
            if (looks && s.slots["newcomer"].isNullOrBlank()) {
                // the one asked about, from the question itself: 「바늘괴물은 어떻게 생겼어?」 → 바늘괴물 (the child named it earlier)
                // never a sentence — 「바람이 불어서 나무가 쓰러졌어는 어떻게 생겼어?」 (StoryNameGuard.kt · #375 review)
                Regex("^(.+?)(은|는|이|가) (어떻게|어떤)").find(prompt.text)?.groupValues?.get(1)?.trim()
                    ?.takeUnless(::storyLooksLikeSentence)?.let {
                    s.slots["newcomer"] = it; s.slotBy["newcomer"] = "child"; s.syncStoryPresentation()
                }
            }
            if (prompt.slot == "newcomer" && !s.slots["newcomer"].isNullOrBlank() && (looks || s.storyClarificationSlot == "newcomer")) {
                if (!friendDrawingPrepared) { prepareStoryFriendDrawing(); friendDrawingPrepared = true }
                drawFriend(::refreshCast)
                s.storyClarificationSlot = null; s.storyNextSlot = null; s.storyServerQuestion = null
                log("새 친구 생김새 질문 → 그리기로 대신함")
                continue
            }
            showConversation()
            val variant = liveVariant(prompt)
            val base = variant.toQuestion(s)
            // Never the script bank's wording in a live story: for a place outside the three themes the theme
            // falls back to dino, so 「기차가 어디로 먼저 갈까?」 asked about a train nobody mentioned (10-05)
            val question = base.copy(text = prompt.text)
            history.before()
            val reply = askStory(question, prompt.slot, singleAttempt = checkPlace)
            if (TurnHistory.isNav(reply)) {
                val undo = (reply as Reply.Tapped).value == TurnHistory.UNDO
                if (if (undo) history.undo() else history.redo()) {
                    event(if (undo) "turn_undone" else "turn_redone")
                    log(if (undo) "↩ 직전 차례를 되돌림 — 같은 질문을 다시" else "↪ 되돌린 차례를 다시 적용")
                    say(if (undo) "그럼 다시 말해 줄래?" else "좋아, 아까 그 이야기로 갈게!")
                    updateBackground()
                    drawFriend(::refreshCast)
                    s.holdStoryGauge()
                }
                continue
            }
            if (checkPlace) finalPlaceChecked = true
            // Silence leaves this slot open; it is neither speech nor a mascot choice.
            if (reply !is Reply.Spoke && reply !is Reply.Tapped) {
                deferredSlot = prompt.slot
                continue
            }
            deferredSlot = null
            s.storyAnswers++
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
            drawFriend(::refreshCast)
            // The third conversation turn chooses the local template. An early server finish
            // still needs a page plan, but does not force extra questions just to reach turn 3.
            if (s.templateKey == null && (s.turn >= 3 || s.storyReady)) decideTemplate("서버 대화")
            mark("live:${prompt.slot ?: "extra"}")
            s.holdStoryGauge()
            history.done()
        }
        true
    }
    if (s.templateKey == null) decideTemplate("대화 종료")
    if (!s.storySoundAttempted) recordStorySound()
    val pendingPictures = coroutineContext[Job]!!.children.filter { !it.isCompleted }.toList()
    if (pendingPictures.isNotEmpty()) {
        inputs(false, false)
        buttons()
        waitingConversation = null
        s.stage = Stage.Show(s.storyHeroArt, "이야기 그림을 마무리하는 중…")
    }
    pendingPictures.joinAll()
    val filledSlots = s.slots.filterValues { it.isNotBlank() }.keys.joinToString(" · ")
    log("Story conversation finished: ${s.endReason} · ${s.turn} turns · verdict-filled slots: $filledSlots")
    go(Scene.MAKING)
}

/** An unanswered place stays empty; the accepted first scene can still describe a background. */
internal suspend fun Director.completeStoryBackgroundFromBook() {
    if (s.mode != StoryMode.STORY || !Server.liveFor(s.mode) ||
        !s.slots["place"].isNullOrBlank() || s.storyBackground != null) return
    val scene = s.storyCaptions?.firstOrNull()?.takeIf(String::isNotBlank) ?: return
    s.stage = Stage.Show(s.storyHeroArt, "이야기 그림을 마무리하는 중…")
    inputs(false, false)
    buttons()
    val png = withTimeoutOrNull(15_000) { Server.image(s.nameMask().mask(scene), "story", s.bookStyle) }
    val saved = png?.let { withContext(Dispatchers.IO) { saveStoryImage(it) } }
    currentCoroutineContext().ensureActive()
    s.storyBackground = saved
    log(if (saved == null) "첫 장면 배경 생성 실패 또는 15초 경과 → 프리셋 유지"
        else "장소 미확정 → 첫 장면에서 배경 생성 · 아이의 장소 칸은 비워 둠")
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
        // a new place gets a new kit layout seed; the same place keeps its scene (turn after turn, undo/redo)
        if (s.place != place) s.sceneSeed = kotlin.random.Random.nextLong()
        // the three app themes (공룡 나라 · 우주 · 바다) go to their kits too — the kit has a floor and lives (birds, bubbles);
        // the theme picture is only for a place no kit matches (10-06 lead decision on #222 · device-checked all eight kits)
        // a kit in this book's style only — felt pieces under a crayon book would put two styles on one stage (WorldStyle)
        s.sceneKit = if (SceneKits.liveStory) SceneKits.matching(place)?.takeIf { WorldStyle.kitReady(it, s.bookStyle) }?.key else null
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
    // 10-05: 「맛있는 간식을 주는 거야」 fell to the default shiny stone
    listOf("간식", "과자", "사탕", "빵", "먹을 거", "먹을것", "밥").any { it in text } -> "share" to "snack"
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
