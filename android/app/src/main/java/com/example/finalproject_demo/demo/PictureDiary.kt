package com.example.finalproject_demo.demo

/*
 * 그림일기 한 바퀴 (일기 모드 · 협업 제외) — docs/일기모드_흐름.html D0 → D1 → D3 → D4 → D5 → D6.
 *
 *   D0 시작        "오늘 있었던 일 하나를 그려 볼래?"  [그릴래] · [그림 없이 이야기할래]
 *   D1 그리며 묻기  붓이 멈추면 "지금 그리는 건 뭐야?" (한 판에 두 번까지) · 이름을 들으면 "나도 그려볼까?" (두 번까지)
 *   D3 빈 칸만 묻기 place · problem 이 비었으면 묻고, 남으면 결말 · 내일 — **다 합쳐 세 번까지**
 *   D4 만드는 중    (서버 연결 뒤 /story)
 *   D5 그림일기     칸이 찬 만큼 쪽 (DiaryBook.kt) · 마지막 줄은 오늘 기분
 *   D6 끝          선물 → 책장. 그림도 말도 없으면 책 없이 조용히 끝낸다
 *
 * 지키는 것
 *   - 빈 칸을 마스코트가 메우지 않는다 — 책이 「아직 듣지 못했어요」로 비었다고 쓴다. 그래서 모든 칸이 `by: child`
 *   - 인식한 낱말은 어느 칸에도 안 들어간다(규칙 5). 조각 이름은 아이가 말한 것만
 *   - 끝나는 조건은 `story_ready` 와 질문이 떨어졌을 때. 15분으로 끝내지 않고, 30분쯤 마무리를 **한 번** 제안한다 (09-30 조장)
 *   - 아이 말은 `r.text` 에서 읽는다. 서버 모드의 답에는 대본 값(`value`)이 없다 (AGENTS.md)
 *
 * 화면은 `DiaryBoard`(D1 · 붓이 멈추면 `pause` 를 보낸다) · `DiaryPaper`(D5) — `ui/DiaryViews.kt`.
 * ⚠️ 아직 **대본**이다 — 오또 그림은 서버가 붙기 전까지 그림 글자이고, 시연 버튼으로도 붓 멈춤을 낼 수 있다.
 */

/** 그리는 동안 묻는 질문 수 · 오또가 그려 주겠다고 하는 수 · 다 그린 뒤 묻는 수 */
internal const val ASK_WHILE_DRAWING = 2
internal const val OTTO_OFFERS = 2
internal const val ASK_AFTER_DRAWING = 3

/** 마무리를 제안하는 때 — 끝내는 시간이 아니다 (guidelines/2 §1-1 · 09-30) */
internal const val WRAP_UP_MS = 30L * 60 * 1000

suspend fun Director.pictureDiary() {
    val day = s.newDiaryDay()
    s.diaryStart = System.currentTimeMillis()
    s.diaryTimeUp = false
    log("그림일기 — 그리는 동안 짧게 묻고, 다 그리면 빈 칸만 ${ASK_AFTER_DRAWING}번까지 묻는다. 빈 칸은 메우지 않는다 (흐름 HTML)")

    when (startDrawing()) {
        "draw" -> drawWhileTalking(day, pausedAlready = false)
        "pause" -> drawWhileTalking(day, pausedAlready = true)
        "skip" -> log("그림 없이 말로 — D3 로 바로 간다")
        else -> log("묻기 전에 다 그렸다 → D3")
    }
    askEmptySlots()
    finishPictureDiary(day)
}

// ── D0 ─────────────────────────────────────────────────────────

/**
 * 그릴까? — 그림판을 먼저 띄워 두고 묻는다. 대답이 없으면 재촉하지 않고 그대로 그리게 둔다.
 * draw · skip · done · pause(대답 없이 바로 그리기 시작해 붓이 멈췄다)
 */
private suspend fun Director.startDrawing(): String {
    s.stage = DiaryBoard()
    inputs(false, false)
    say("오늘 있었던 일 하나를 그려 볼래? 생각나는 것부터 그려 줘.")
    buttons(
        DemoBtn("🖍 그릴래") { send(Reply.Tapped("draw", "그릴래")) },
        DemoBtn("🙅 그림 없이 이야기할래") { send(Reply.Tapped("skip", "그림 없이")) },
    )
    val v = awaitValue("draw", "skip", "done", "pause")
    when (v) {
        "skip" -> s.drawing.clear()
        "done" -> { keepBoard(); if (s.sceneDrawing.isEmpty()) log("빈 화이트보드 → 그림 없는 날") }
    }
    return v
}

// ── D1 ─────────────────────────────────────────────────────────

/**
 * 그리는 동안. 붓이 멈출 때마다 새 조각이 생기고, 이름 없는 조각이면 묻는다.
 * 질문은 흐름을 막지 않는다 — 답이 없으면 같은 질문을 다시 하지 않고 그리기로 돌아간다.
 */
private suspend fun Director.drawWhileTalking(day: DiaryDay, pausedAlready: Boolean) {
    var asked = 0
    var offers = 0
    val waiting = mutableListOf<DiaryPiece>()     // 오또가 그리고 있는 조각 — 다음 멈춤에 보여 준다
    var paused = pausedAlready
    val askedPieces = mutableSetOf<Int>()        // 한 번 물은 조각은 다시 묻지 않는다(답이 없었어도)
    if (!paused) say("좋아! 다 그리면 알려 줘.")
    while (true) {
        buttons(
            DemoBtn("✏️ (시연) 붓이 멈춤 — 조각 하나를 그렸다") { send(Reply.Tapped("pause", "멈춤")) },
            DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) },
        )
        // 그림판의 [그리기 싫어](skip)도 그리기를 끝낸다 — 무시하면 아이가 눌러도 아무 일이 없다
        if (!paused && awaitValue("pause", "done", "skip") != "pause") break
        paused = false

        // 기다리던 오또 그림이 먼저다 — 아이가 부탁한 것이라
        val ready = waiting.removeFirstOrNull()
        if (ready != null) { showOttoDrawing(day, ready); continue }

        day.catchUp(s.drawing)
        val piece = pieceBeingDrawn(day)
        if (piece == null || piece.id in askedPieces) {
            log("붓 멈춤 — 방금 그린 조각은 이름이 있거나 이미 물었다. 묻지 않는다")
            continue
        }
        if (asked >= ASK_WHILE_DRAWING) {
            log("붓 멈춤 — 이번 판에 물을 만큼 물었다(${ASK_WHILE_DRAWING}번). 그리기를 지켜본다")
            continue
        }
        asked++
        askedPieces += piece.id
        val (name, finished) = askPieceName(day, piece)
        if (finished) break
        if (name == null || offers >= OTTO_OFFERS) continue
        when (offerOttoDrawing(name)) {
            "yes" -> { offers++; waiting += day.pieces.first { it.id == piece.id } }
            "done" -> break
        }
    }
    if (waiting.isNotEmpty()) log("아직 그리는 중인 오또 그림 ${waiting.size}장은 버린다 — 다 그렸으니 기다리게 하지 않는다")
    keepBoard()
    if (s.sceneDrawing.isEmpty() && day.pieces.all { it.strokes.isEmpty() }) log("그린 것이 없다 → 그림 없는 날")
    say("다 그렸구나!")
    pause(900)
}

/** 마지막 획이 붙은 조각 — 이름이 아직 없을 때만 물을 거리다 */
private fun Director.pieceBeingDrawn(day: DiaryDay): DiaryPiece? {
    val lastStroke = s.drawing.lastOrNull() ?: return null
    return day.pieces.firstOrNull { lastStroke in it.strokes }?.takeIf { it.name == null }
}

/**
 * 「지금 그리는 건 뭐야?」 — 아이가 붙인 이름만 조각 이름이 된다.
 * 둘째 값이 참이면 묻는 사이에 아이가 [다 그렸어]를 눌렀다 — 그리기를 끝낸다.
 */
private suspend fun Director.askPieceName(day: DiaryDay, piece: DiaryPiece): Pair<String?, Boolean> {
    val q = Question(
        text = "우와, 지금 그리는 건 뭐야?",
        kind = Kind.EASY,
        noCards = true,
        spoken = PIECE_ANSWERS,
        id = "diary_piece",
    )
    val r = ask(q)
    if (r is Reply.Tapped && (r.value == "done" || r.value == "skip")) return null to true
    val name = (r as? Reply.Spoke)?.let { pieceNameFrom(it) }
    if (name == null) {
        say(if (r is Reply.Spoke) "그래, 계속 그려 봐." else "계속 그려 봐!")
        log("조각 이름을 못 들었다 → 이름 없이 둔다. 다시 묻지 않는다")
        return null to false
    }
    val i = day.pieces.indexOfFirst { it.id == piece.id }
    day.pieces[i] = day.pieces[i].copy(name = name)
    s.slots["whiteboard"] = day.pieceNames.joinToString(", ")
    s.slotBy["whiteboard"] = "child"
    event("slot_filled", "slot" to "extra", "of" to "whiteboard", "value" to name, "source" to "child")
    quote((r as Reply.Spoke).text)
    say("${name}${ida(name)}구나!")
    log("조각 이름 「$name」 — 아이가 말한 이름 (extra · whiteboard · child)")
    pause(700)
    return name to false
}

/** 「나도 ○○ 그려볼까?」 — 응이면 뒤에서 그리고 아이는 계속 그린다. 기다리는 화면이 없다. yes · no · done */
private suspend fun Director.offerOttoDrawing(name: String): String {
    say("나도 ${name}${eul(name)} 그려볼까?")
    buttons(
        DemoBtn("🗣 \"응!\"") { send(Reply.Tapped("yes", "응")) },
        DemoBtn("🗣 \"아니\"") { send(Reply.Tapped("no", "아니")) },
        DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) },
    )
    val v = awaitValue("yes", "no", "done", "skip").let { if (it == "skip") "done" else it }
    when (v) {
        "yes" -> {
            say("나도 그려 볼게! 너도 더 그리고 있어!")
            log("오또 그림 부탁 — 뒤에서 만든다(서버 연결 뒤 /image redraw · #32). 지금은 대본 그림")
        }
        "no" -> say("좋아, 네 그림이 최고야!")
    }
    if (v != "done") pause(600)
    return v
}

/** 오또 그림이 왔다 — 보여 주고 아이가 고른다. 원본이 기본값이다 */
private suspend fun Director.showOttoDrawing(day: DiaryDay, piece: DiaryPiece) {
    val name = piece.name ?: return
    s.stage = DiaryBoard(pick = piece.id)
    say("짠! 나도 ${name}${eul(name)} 그려 봤어! 어떤 게 좋아?")
    buttons(
        DemoBtn("🖍 내 그림으로") { send(Reply.Tapped("orig", "내 그림")) },
        DemoBtn("✨ 오또 그림으로 ${ottoEmoji(name)}") { send(Reply.Tapped("otto", "오또 그림")) },
    )
    val pick = awaitValue("orig", "otto")
    s.stage = DiaryBoard()
    val i = day.pieces.indexOfFirst { it.id == piece.id }
    val look = if (pick == "otto") PieceLook.OTTO else PieceLook.ORIGINAL
    day.pieces[i] = day.pieces[i].copy(look = look)
    s.reactions++
    event("utterance", "speaker" to "child", "mode" to "card", "text" to if (look == PieceLook.OTTO) "오또 그림" else "내 그림")
    if (look == PieceLook.OTTO) {
        say("펑! 내가 그린 ${name}${ida(name)}야. 고마워!")
        log("「$name」 → 오또 그림으로 (부모 기록: 아이가 고른 오또 그림 · 원본도 보관)")
    } else {
        say("띠용! 역시 네가 그린 ${name}${ida(name)}!")
        log("「$name」 → 아이 원본 그대로")
    }
    pause(900)
}

/** 그림판의 획을 책에 쓸 그림으로 옮긴다 — 조각에 아직 안 붙은 획도 붙여 둔다 */
private fun Director.keepBoard() {
    if (s.drawing.isEmpty()) return
    s.diaryDay.catchUp(s.drawing)
    s.keepSceneDrawing()
    s.reactions++
    event("make", "kind" to "draw")
}

// ── D3 ─────────────────────────────────────────────────────────

/**
 * 다 그린 뒤 — 빈 칸만 묻는다. 필수(place · problem)가 먼저, 남으면 결말 · 내일. 합쳐 [ASK_AFTER_DRAWING] 번까지.
 * 질문 순서는 서버가 붙으면 판정의 `next_slot` 이 정한다(목요일). 지금은 이 차례다.
 */
private suspend fun Director.askEmptySlots() {
    val queue = PICTURE_QUESTIONS.filter { s.slots[it.key].isNullOrBlank() }.toMutableList()
    var asked = 0
    var wrapOffered = false
    while (queue.isNotEmpty() && asked < ASK_AFTER_DRAWING) {
        if (!wrapOffered && s.diaryTimeUp) {
            wrapOffered = true
            if (offerWrapUp()) break
        }
        val pq = queue.removeAt(0)
        asked++
        s.stepsDone++
        askPictureSlot(pq)
        if (s.endReason == null && PICTURE_REQUIRED.all { !s.slots[it].isNullOrBlank() }) {
            s.endReason = "story_ready"
            log("필수 두 칸(place · problem)이 찼다 → story_ready. 남은 물음 ${ASK_AFTER_DRAWING - asked}번까지는 결말 · 내일을 더 듣는다")
        }
    }
    if (queue.isNotEmpty()) log("다 그린 뒤 ${ASK_AFTER_DRAWING}번을 다 물었다 — 남은 칸 [${queue.joinToString(" · ") { it.key }}] 은 비워 둔다")
}

/**
 * 한 칸 묻기. 「몰라」면 한 번만 쉽게 바꿔 묻고, 또 모르면 비워 두고 넘어간다.
 * 무응답은 [Director.ask] 가 쉬운 질문 한 번 → 「괜찮아」로 넘긴다 (카드 · 마스코트 채움 없음).
 */
private suspend fun Director.askPictureSlot(pq: PictureQuestion) {
    val step = DIARY_STEPS.first { it.bookKey == pq.key }
    var text = pq.ask(s)
    for (tries in 0..1) {
        val q = Question(
            text = text,
            kind = step.kind,
            noCards = true,
            ladder = if (tries == 0) listOf(pq.easy) else emptyList(),
            spoken = step.answers(s),
            extra = buildList {
                step.demoAnswer(s)?.let { a -> add(DemoBtn("🎬 오늘 이야기 시연 답 — \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) }) }
                add(DemoBtn("⏱ (시연) 30분이 지난 것으로") { s.diaryTimeUp = true; send(Reply.Silent) })
            },
            id = "diary_${pq.key}",
        )
        val r = ask(q)
        judge(step.variant, r, q.text)
        if (r !is Reply.Spoke) return
        // 대본 답은 값이 붙어 온다(빈 값 = 「몰라」 · 「그냥」). 서버 모드의 답에는 값이 없다 — 글자를 읽는다
        val raw = r.answer?.value?.takeIf { it.isNotBlank() }
        val said = if (r.answer != null) raw.orEmpty() else r.text.trim().takeUnless { dontKnow(it) }.orEmpty()
        if (said.isEmpty()) {
            if (tries == 0) { log("「몰라」 → 한 번만 쉽게 바꿔 묻는다"); text = pq.easy; continue }
            log("[${pq.key}] 또 모른다 → 비워 둔다. 마스코트가 대신 채우지 않는다")
            say("괜찮아, 생각 안 나도 돼.")
            pause(700)
            return
        }
        val value = if (raw != null) diarySlotOf(raw) else said
        val line = if (raw != null) diaryLineOf(raw) ?: said else said
        setDiarySlot(step.slot, pq.key, value, line, "child")
        quote(r.text)
        s.mascotPicks = 0
        return
    }
}

/** 30분쯤 — 마무리를 **한 번** 제안한다. 더 하고 싶다면 계속한다. 되묻지 않는다 */
private suspend fun Director.offerWrapUp(): Boolean {
    say("오늘 이야기 정말 많이 했다! 이제 그림일기로 만들어 볼까?")
    buttons(
        DemoBtn("🗣 \"응, 만들자\"") { send(Reply.Tapped("wrap", "응")) },
        DemoBtn("🗣 \"더 할래\"") { send(Reply.Tapped("more", "더 할래")) },
    )
    val wrap = awaitValue("wrap", "more") == "wrap"
    log(if (wrap) "30분 — 마무리 제안에 응 → 책으로" else "30분 — 더 하고 싶다 → 계속한다 (다시 제안하지 않는다)")
    if (!wrap) { say("좋아, 더 이야기해 줘!"); pause(600) }
    return wrap
}

internal class PictureQuestion(val key: String, val ask: (DemoState) -> String, val easy: String)

/** 필수 칸 — `DemoState.reqSlots`(일기 = place · problem, #29)와 같은 둘. 여기는 책 문장 키로 본다 */
internal val PICTURE_REQUIRED = listOf("place", "problem")

/** 다 그린 뒤 묻는 칸 — 이 차례로, 빈 것만 */
internal val PICTURE_QUESTIONS = listOf(
    PictureQuestion("place", { "오늘 어디 갔었어?" }, "아침 먹고 어디 갔어?"),
    PictureQuestion("problem", { if (it.slots["place"].isNullOrBlank()) "오늘 무슨 일이 있었어?" else "거기서 무슨 일이 있었어?" }, "거기서 뭐 했어?"),
    PictureQuestion("solution", { "그래서 어떻게 됐어?" }, "그다음엔 뭐 했어?"),
    PictureQuestion("keep", { "내일 또 하고 싶은 거 있어?" }, "내일은 뭐 하고 싶어?"),
)

// ── D4 · D5 · D6 ───────────────────────────────────────────────

private suspend fun Director.finishPictureDiary(day: DiaryDay) {
    buttons()
    inputs(false, false)
    val pages = buildDiaryBook(s.diaryBookInput())
    if (pages.isEmpty()) {
        say("오늘은 여기까지 하고, 내일 또 하자.")
        log("그림도 말도 없다 → 책 없이 끝낸다. 벌점 · 아쉬움 표현 없음 · 동화 모드로 넘기지 않는다 (D6)")
        pause(1500)
        goHome()
        return
    }
    if (s.endReason == null) s.endReason = "story_ready"
    mark("diary")

    // D4
    say("오늘 이야기가 다 모였어! 이제 그림일기로 만들어 줄게.")
    s.stage = Stage.Making("그림일기를 만드는 중…")
    pause(1500)
    day.weatherFromDrawing()
    s.title = s.slots["title"]?.takeIf { it.isNotBlank() } ?: s.diaryTitle()
    event("book", "template" to "그림일기", "pages" to pages.size, "title" to s.title)
    log("그림일기 ${pages.size}쪽 — ${pages.joinToString(" · ") { it.kind.name.lowercase() }} · 날씨 ${day.weather?.label ?: "아이가 고른다"}")

    // D5
    readPictureDiary(day)

    // D6
    say("그림일기 다 만들었다!")
    pause(1200)
    go(Scene.END)
}

/** 한 쪽씩 넘긴다. 마지막 줄이 비었으면(오늘 기분을 말하지 않았다) 얼굴을 눌러 채운다 */
private suspend fun Director.readPictureDiary(day: DiaryDay) {
    var i = 0
    while (true) {
        val pages = buildDiaryBook(s.diaryBookInput())
        val p = pages[i]
        val last = i == pages.lastIndex
        val caption = listOfNotNull(p.text, p.tail, p.closing ?: if (p.asksFeel) "$FEEL_LEAD …" else null).joinToString(" ")
        s.stage = DiaryPaper(i)
        say(caption)
        val b = mutableListOf<DemoBtn>()
        if (i == 0 && day.weather == null) DiaryWeather.entries.forEach { w ->
            b += DemoBtn("${w.emoji} 날씨 ${w.label}") { send(Reply.Tapped("wx:${w.name}", w.label)) }
        }
        if (last && p.asksFeel) DiaryFeel.entries.forEach { f ->
            b += DemoBtn("${f.emoji} ${f.line}") { send(Reply.Tapped("feel:${f.name}", f.line)) }
        }
        // 넘기기가 앞이다 — 동화 책(sceneBook)과 같은 차례. 앞 쪽이 먼저면 앞으로만 누르는 손이 책 앞뒤를 오간다
        b += DemoBtn(if (last) "📔 다 읽었어" else "▶ 다음 쪽") { send(Reply.Tapped("next", "다음")) }
        if (i > 0) b += DemoBtn("◀ 앞 쪽") { send(Reply.Tapped("prev", "앞")) }
        buttons(*b.toTypedArray())
        val r = awaitReply() as? Reply.Tapped ?: continue
        when {
            r.value.startsWith("wx:") -> {
                day.pickWeather(DiaryWeather.valueOf(r.value.removePrefix("wx:")))
                s.reactions++
                event("utterance", "speaker" to "child", "mode" to "card", "text" to "날씨 ${r.label}")
            }
            r.value.startsWith("feel:") -> {
                day.feel = DiaryFeel.valueOf(r.value.removePrefix("feel:"))
                s.reactions++
                s.modeCard++
                event("utterance", "speaker" to "child", "mode" to "card", "text" to r.label)
                log("오늘 기분을 얼굴로 골랐다 → by: card (주고받기에는 세고, 수준 신호 · 인용에는 안 넣는다)")
            }
            r.value == "prev" -> i = (i - 1).coerceAtLeast(0)
            r.value == "next" -> if (last) return else i++
        }
    }
}

// ── 말 → 조각 이름 ──────────────────────────────────────────────

private val DONT_KNOW = Regex("몰라|모르겠|글쎄|음+$")

internal fun dontKnow(text: String) = text.isBlank() || DONT_KNOW.containsMatchIn(text)

private val COPULA = Regex("(이야|야|이에요|예요|이요|요|이지|지|인데|거든)?[.!?~ ]*$")
private val DREW = Regex("(을|를)?\\s*(그렸어|그리는 거야|그리는 중이야|그리고 있어)$")

/**
 * 「우리 집이야!」 → 「우리 집」 · 「아니, 블록이야」 → 「블록」. 대본 답에는 값이 붙어 있어 그대로 쓴다.
 * 「몰라」면 null — 이름 없이 둔다.
 */
internal fun pieceNameFrom(r: Reply.Spoke): String? {
    r.answer?.value?.takeIf { it.isNotBlank() }?.let { return it }
    var t = r.text.trim()
    if (dontKnow(t)) return null
    t = t.removePrefix("아니,").removePrefix("아니").trim()
    t = DREW.replace(t, "").trim()
    t = COPULA.replace(t, "").trim()
    return t.takeIf { it.isNotEmpty() && it.length <= 12 }
}

/** 이름 뒤 「(이)야 · (이)구나」 */
private fun ida(name: String) = if (bat(name)) "이" else ""

/** 대본에서 오또 그림 대신 보여 주는 것 — 서버가 붙으면 /image redraw 의 PNG 로 바뀐다 */
internal fun ottoEmoji(name: String): String = OTTO_EMOJI.entries.firstOrNull { it.key in name }?.value ?: "🎨"

private val OTTO_EMOJI = mapOf(
    "집" to "🏠", "해" to "☀️", "구름" to "☁️", "나무" to "🌳", "꽃" to "🌸", "블록" to "🧱", "탑" to "🗼",
    "공룡" to "🦖", "강아지" to "🐶", "고양이" to "🐱", "자동차" to "🚗", "버스" to "🚌", "엄마" to "👩",
    "아빠" to "👨", "미끄럼틀" to "🛝", "공" to "⚽", "케이크" to "🍰", "나" to "🧒",
)

/** 붓 멈춤에 대답하는 대본 — 한 아이가 그리는 흔한 것들 */
private val PIECE_ANSWERS = listOf(
    Answer("우리 집이야!", "우리 집", lv = 1),
    Answer("나야.", "나", lv = 1),
    Answer("해!", "해", lv = 1),
    Answer("블록으로 쌓은 탑이야.", "탑", lv = 2),
    Answer("엄마랑 나.", "엄마", lv = 2),
    Answer("몰라.", "", lv = 1),
)
