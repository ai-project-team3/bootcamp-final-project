package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * 화면은 `DiaryBoard`(D1 · 붓이 멈추면 `pause` 를 보낸다) · `DiaryAsk`(D3) · `DiaryPaper`(D5) — `ui/DiaryViews.kt`.
 * 오또 그림은 서버 모드면 `/image` redraw(#32 · `DiaryRedraw.kt`), 대본이면 그림 글자다. 시연 버튼으로도 붓 멈춤을 낼 수 있다.
 */

/**
 * 오또가 먼저 「나도 ○○ 그려볼까?」라고 하는 수(한 판).
 * 제안마다 GPU 가 약 8초씩 그려서 상한을 둔다 — 아이가 「그려줘」로 직접 부탁하는 건 세지 않는다 (10-02 진웅: 2 → 4).
 * 그리는 동안의 이름 질문에는 상한이 없다
 */
internal const val OTTO_OFFERS = 4

/**
 * 그리면서 듣는 D1 질문의 첫 답 기다림(초). 기본 5초는 손을 멈추고 답하기엔 짧다(10-01 진웅 · 프로토타입 15초 · 흐름 10초).
 * 말이 끊기지 않게 언제 거둘지(오래 그리는 중 · 손을 놓고 멈춤)는 따로 정한다
 */
internal const val D1_WAIT_SEC = 10.0

/**
 * 다 그린 뒤(D3) 한 칸을 묻는 횟수 — 한 번 묻고, 쉽게 바꿔 한 번 더. 그래도 못 들으면 비워 둔다(카드 · 마스코트 채움 없음).
 * 전체 질문 수 상한은 없다 — 다른 모드처럼 칸마다만 막는다(규칙 3 · 10-02 진웅 #89). 전에는 전체 3번이라 결말을 못 묻고 비웠다
 */
internal const val SLOT_TRIES = 2

/** 마무리를 제안하는 때 — 끝내는 시간이 아니다 (guidelines/2 §1-1 · 09-30) */
internal const val WRAP_UP_MS = 30L * 60 * 1000

/** 시작하고 [WRAP_UP_MS] 가 지났나 — 시연 버튼으로 켠 것도 같이 본다. 전에는 시연 버튼만 켜서 실기기에서 안 떴다 (#64-1) */
private fun Director.diaryRanLong(): Boolean =
    s.diaryTimeUp || (s.diaryStart > 0L && System.currentTimeMillis() - s.diaryStart >= WRAP_UP_MS)

suspend fun Director.pictureDiary() {
    val day = s.newDiaryDay()
    s.diaryStart = System.currentTimeMillis()
    s.diaryTimeUp = false
    log("그림일기 — 그리는 동안 짧게 묻고, 다 그리면 빈 칸을 칸마다 ${SLOT_TRIES}번까지 묻는다. 빈 칸은 메우지 않는다 (흐름 HTML · #89)")

    s.progressVisible = false        // 위쪽 별 막대는 일기 화면이 따로 그린다 (D3 별 두 개)
    if (startDrawing() == "draw") {
        day.drawingTalk = true
        try { drawWhileTalking(day) } finally { day.drawingTalk = false; day.pendingTap = null }
        askPieceStoriesAfterDrawing(day)
    } else log("그림 없이 말로 — D3 로 바로 간다")
    askEmptySlots()
    finishPictureDiary(day)
}

// ── D0 ─────────────────────────────────────────────────────────

/** 그릴까? — 방에서 오또가 손 흔들며 묻는다. [그릴래!] · [그림 없이 말할래] (docs/일기모드_UI.html D0) */
private suspend fun Director.startDrawing(): String {
    s.stage = DiaryStart
    inputs(false, false)
    say("오늘 있었던 일을 그려 볼래? 생각나는 것부터 그려 줘.")
    buttons(
        DemoBtn("🖍 그릴래") { send(Reply.Tapped("draw", "그릴래")) },
        DemoBtn("🙅 그림 없이 이야기할래") { send(Reply.Tapped("skip", "그림 없이")) },
    )
    val v = awaitValue("draw", "skip")
    if (v == "skip") s.drawing.clear() else s.stage = DiaryBoard()
    return v
}

// ── D1 ─────────────────────────────────────────────────────────

/** 물을 것이 없는 멈춤이 이만큼 쌓이면 「다 그렸어?」 — 매번 묻지 않는다(지켜보는 틈을 둔다) */
internal const val DONE_CHECK_EVERY = 2

/**
 * 그리는 동안. 붓이 멈출 때마다 새 조각이 생기고, 이름 없는 조각이면 묻는다.
 * 질문은 흐름을 막지 않는다 — 답이 없으면 같은 질문을 다시 하지 않고 그리기로 돌아간다.
 * 그림판 옆 버튼은 없다 — 물을 것이 떨어지면 오또가 멈춘 틈에 「다 그렸어?」라고 묻는다 (docs/일기모드_UI.html 규칙)
 */
private suspend fun Director.drawWhileTalking(day: DiaryDay) = coroutineScope {
    var offers = 0
    var quiet = 0                                // 물을 것 없이 지나간 멈춤 수
    var afterCrayon = false                      // 이번 멈춤이 크레용을 고른 뒤에 왔나
    val storyAsked = mutableSetOf<String>()      // 그리는 중에 물은 이야기 칸 — 한 번씩만
    var pieceStories = 0                          // 그리는 중에 물은 조각 이야기 — [PIECE_STORY_D1] 까지 (#220 ②)
    val waiting = mutableListOf<OttoOrder>()      // 오또가 그리고 있는 조각 — 다 되면 다음 멈춤에 보여 준다
    val askedPieces = mutableSetOf<Int>()        // 한 번 물은 조각은 다시 묻지 않는다(답이 없었어도)
    val held = mutableListOf<Pair<Int, String>>() // 미뤄 둔 「나도 그려볼까?」 — 조각 · 이름 (앞 것부터 · 덮어쓰지 않는다)
    say("좋아! 다 그리면 알려 줘.")
    while (true) {
        // 이야기를 마친 조각 — 이제부터 그 위 · 곁에 긋는 선은 새 그림으로 보고 묻는다. 이야기하는 사이 그은 선까지는 그 조각이다 (10-06 실기기)
        askedPieces.forEach { day.talkedAbout(it, s.drawing.size) }
        buttons(
            DemoBtn("✏️ (시연) 붓이 멈춤 — 조각 하나를 그렸다") { send(Reply.Tapped("pause", "멈춤")) },
            DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) },
            DemoBtn("🗣 (먼저 말함) \"다 그렸어!\"") { send(Reply.Spoke("다 그렸어!")) },
            DemoBtn("🗣 (먼저 말함) \"너도 그려줘!\"") { send(Reply.Spoke("너도 그려줘!")) },
            DemoBtn("🗣 (먼저 말함) \"이건 강아지야\"") { send(Reply.Spoke("이건 강아지야")) },
        )
        // 아이는 아무 때나 먼저 말해도 된다 — 마이크를 열어 둔다. 오또가 말하는 사이 온 붓 멈춤은 남겨 두었다가 받는다
        inputs(mic = true, next = false)
        day.watching = true
        // 오또 그림이 다 되면 붓 멈춤을 또 기다리지 않는다 — 아이가 멈추고 기다리면 그림이 영영 안 떴다(10-02 실기기).
        // 그리는 중 · 말하는 중이면 끝날 때까지 기다렸다가 멈춤처럼 알린다
        val arts = waiting.mapNotNull { it.art }
        val wake = launch {
            if (arts.isEmpty()) return@launch
            while (arts.none { it.isCompleted }) delay(WATCH_STEP_MS)
            while (day.penDown || s.micOn) delay(WATCH_STEP_MS)
            send(Reply.Tapped("pause", "오또 그림"))
        }
        // 오또가 말하던 사이 남겨 둔 붓 멈춤이 있으면 기다리지 않고 받는다 — 아이가 새로 그린 것을 놓치지 않는다
        // 오또가 묻는 사이 누른 그림판 조작이 먼저다 — 아이가 직접 누른 것이라 붓 멈춤보다 앞에 둔다 (10-05 진웅)
        val tapped = day.pendingTap?.also { day.pendingTap = null; log("오또가 묻는 사이 누른 「${it.label}」 → 이제 받는다") }
        val missed = if (tapped != null) null else day.pendingPause?.takeIf { !s.micOn && !day.penDown }
        if (missed != null) { day.pendingPause = null; log("오또가 말하는 사이 온 붓 멈춤 → 이제 받는다") }
        val r = try { tapped ?: if (missed != null) Reply.Tapped("pause", missed) else awaitReply() } finally { wake.cancel() }
        if (r == day.pendingTap) day.pendingTap = null           // 남겨 둔 것과 같은 탭이 줄로도 왔다 — 두 번 하지 않는다
        day.watching = false
        when {
            r is Reply.Tapped && r.value == "pause" -> afterCrayon = r.label == CRAYON_PAUSE
            // [그리기 싫어](skip)도 그리기를 끝낸다 — 시연 서랍
            r is Reply.Tapped && (r.value == "done" || r.value == "skip") -> break
            // ✨ 이름표를 톡 — 왔던 오또 그림을 다시 고른다
            r is Reply.Tapped && r.value.startsWith("look:") -> {
                day.pieces.firstOrNull { it.id == r.value.removePrefix("look:").toIntOrNull() && it.ottoPng != null }
                    ?.let { showOttoDrawing(day, it) }
                continue
            }
            // 그림판 오른쪽 [그려 줘] — 「그려줘」라고 말한 것과 같다 (10-05 진웅)
            r is Reply.Tapped && r.value == "drawme" -> {
                if (drawMe(this, day, waiting, null)) offers++
                continue
            }
            // 그림판 오른쪽 [이름 고치기] — 이름표를 길게 누른 것과 같다. 어느 조각인지는 [renameTarget] (10-05 진웅)
            r is Reply.Tapped && r.value == "rename" -> {
                val piece = s.renameTarget(day)
                if (piece != null) askRename(day, piece) else { say("이름표가 붙은 그림이 아직 없어!"); pause(600) }
                continue
            }
            // 이름표를 길게 — 이름을 다시 묻고 고친다 (10-02 진웅 — 잘못 들은 이름을 고칠 수 있어야 한다)
            r is Reply.Tapped && r.value.startsWith("rename:") -> {
                day.pieces.firstOrNull { it.id == r.value.removePrefix("rename:").toIntOrNull() && it.name != null }
                    ?.let { askRename(day, it) }
                continue
            }
            // 그냥 이름표를 톡 — 이름을 불러 준다 (프로토타입 tapTag). 새 획을 긋기 전까지 「그려줘」는 이 조각이다
            r is Reply.Tapped && r.value.startsWith("name:") -> {
                day.pieces.firstOrNull { it.id == r.value.removePrefix("name:").toIntOrNull() }?.let { p ->
                    day.focus = p.id to s.drawing.size
                    p.name?.let { say("${you(it)}!") }
                }
                continue
            }
            r is Reply.Spoke -> {
                when (heardWhileDrawing(day, r, askedPieces)) {
                    Heard.DONE -> break
                    Heard.DRAW_ME -> if (drawMe(this, day, waiting, r.text)) offers++
                    Heard.NAMED -> {
                        // 묻지 않았는데 이름을 말했다 — 물어서 들은 이름처럼 「나도 그려볼까?」를 바로 (전에는 빠졌다 · 10-01 실기기)
                        val named = day.pieces.firstOrNull { s.drawing.lastOrNull() in it.strokes }?.takeIf { it.name != null }
                        if (named != null && offers < OTTO_OFFERS && named.ottoPng == null && waiting.none { it.pieceId == named.id }) {
                            askedPieces += named.id
                            when (offerAndOrder(this, day, waiting, named.id, named.name!!)) {
                                "yes" -> offers++
                                "done" -> break
                                MOVED_ON -> held += named.id to named.name!!
                            }
                        }
                    }
                    Heard.OTHER -> {}
                }
                continue
            }
            else -> continue
        }

        // 다 그려진 오또 그림이 먼저다 — 아이가 부탁한 것이라. 아직 그리는 중이면 기다리지 않고 지나간다
        val ready = waiting.firstOrNull { it.art?.isCompleted != false }
        if (ready != null) {
            waiting -= ready
            if (receiveOttoDrawing(day, ready)) continue
        }
        // 8초가 넘도록 안 오면 한 번만 알린다(규칙 8) — 서버는 13초에 원본으로 돌려준다
        waiting.firstOrNull { !it.late && System.currentTimeMillis() - it.since > OTTO_LATE_MS }?.let {
            it.late = true
            say("오또 그림은 조금 뒤에 올 거야! 계속 그리고 있어.")
            pause(500)
        }

        day.catchUp(s.drawing)
        val piece = pieceBeingDrawn(day)?.takeIf { it.id !in askedPieces }
        // 그린 조각마다 묻는다 — 몇 번까지라는 상한은 없다. 빈도는 붓 멈춤 · 손 움직임이 정한다 (10-02 진웅 · 흐름 「간격으로만」)
        if (piece != null) {
            askedPieces += piece.id
            // 배경을 그렸다 — 「뭐 그린 거야?」 대신 「여기는 어디야?」(장소를 아직 모를 때). 답은 장소 칸으로
            if (piece.role == PieceRole.BACKGROUND && "place" !in storyAsked && s.slots["place"].isNullOrBlank()) {
                storyAsked += "place"
                log("배경을 그렸다 → 장소를 묻는다")
                if (askStoryWhileDrawing(day, Triple("place", D1_FIRST_STORY, "place"), ring = piece.id) == "done") break
                continue
            }
            val linesBefore = s.drawing.size
            val answer = askPieceName(day, piece)
            if (answer.finished) break
            // 다른 조각을 그리러 갔다 — 이 조각은 안 물은 것으로 두고(나중에 · D3), 다음 멈춤에 지금 그리는 조각을 먼저 묻는다
            if (answer.movedOn) { askedPieces -= piece.id; continue }
            val name = answer.name
            // 배경은 먼저 「그려볼까?」를 묻지 않는다 — 아이가 [그려 줘]를 누르거나 말할 때만 판 전체로 그린다 (#168 · 10-06 진웅)
            if (name == null || offers >= OTTO_OFFERS || piece.role == PieceRole.BACKGROUND) continue
            // 답하는 사이 새 선을 긋기 시작했으면 그리기를 끊지 않는다 — 제안은 물을 것 없는 다음 멈춤에
            if (s.drawing.size > linesBefore) {
                held += piece.id to name
                log("「$name」 이름을 듣는 사이 새로 그리기 시작했다 → 「나도 그려볼까?」는 다음 조용한 멈춤에")
                continue
            }
            when (offerAndOrder(this, day, waiting, piece.id, name)) {
                "yes" -> offers++
                "done" -> break
                MOVED_ON -> held += piece.id to name            // 묻는 사이 다른 걸 그리러 갔다 · 말하는 중 — 조용한 멈춤에 다시
            }
            continue
        }
        // 미뤄 둔 사이 바뀐 것을 본다 — 이름을 고쳤거나 [그려 줘]로 이미 주문 · 받은 조각이면 옛 이름으로 다시 묻지 않는다
        // (10-05 실기기: 「나도 너 안먹어를 그려볼까?」가 나무로 고치고 오또 그림을 받은 뒤 다시 나왔다)
        held.removeAll { (id, name) -> (day.offerable(id, waiting) == null).also { gone -> if (gone) log("미뤄 둔 「나도 $name 그려볼까?」 — 그사이 이미 그렸거나 주문했다 → 묻지 않는다") } }
        val h = held.removeFirstOrNull()?.let { (id, _) -> day.offerable(id, waiting) }
        if (h != null && offers < OTTO_OFFERS) {
            when (offerAndOrder(this, day, waiting, h.id, h.name!!)) {
                "yes" -> offers++
                "done" -> break
                MOVED_ON -> held.add(0, h.id to h.name!!)
            }
            continue
        }
        log("붓 멈춤 — 방금 그린 조각은 이름이 있거나 이미 물었다. 묻지 않는다")
        if (afterCrayon) continue                      // 색을 고르고 이어 그릴 참이다 — 「다 그렸어?」로 세지 않는다
        // 물을 조각이 없는 멈춤 — 이야기를 하나 묻는다. 첫 질문은 고정(구워 둔 목소리), 다음은 서버가 앞 말을 받아 쓴 질문
        // (「놀이터에서 무슨 일이 있었어?」 — 그림 질문이 사이에 끼어도 알아듣게). 칸마다 한 번 · [D1_STORY_MAX] 번까지 · 나머지는 D3 (10-02 진웅)
        // 그린 사람 · 물건 이야기 — 장소를 먼저 듣고 나서(장소 질문이 먼저다). 다 못 물은 것은 다 그린 뒤 (#220 ②)
        val placeFirst = "place" !in storyAsked && s.slots["place"].isNullOrBlank()
        val pieceStory = if (placeFirst || pieceStories >= PIECE_STORY_D1) null else s.pieceStoryQuestion(day)
        if (pieceStory != null) {
            pieceStories++
            if (askPieceStory(day, pieceStory.first, pieceStory.second, drawing = true) == "done") break
            continue
        }
        val story = nextD1Story(day, storyAsked)
        if (story != null) {
            storyAsked += story.third
            if (askStoryWhileDrawing(day, story) == "done") break
            continue
        }
        if (++quiet < DONE_CHECK_EVERY) continue
        quiet = 0
        if (askDoneDrawing()) break
    }
    if (waiting.isNotEmpty()) {
        waiting.forEach { it.art?.cancel() }
        log("아직 그리는 중인 오또 그림 ${waiting.size}장은 버린다 — 다 그렸으니 기다리게 하지 않는다")
    }
    inputs(false, false)
    keepBoard()
    if (s.sceneDrawing.isEmpty() && day.pieces.all { it.strokes.isEmpty() }) log("그린 것이 없다 → 그림 없는 날")
    say(praiseFor(day.pieceNames))
    pause(900)
}

/** 다 그렸을 때 — 이번에 그린 것들을 한꺼번에 칭찬한다(「강아지랑 우리 집 멋지다!」). 이름이 없으면 그림 전체를 */
internal fun praiseFor(childNames: List<String>): String {
    val names = childNames.map(::you)
    if (names.isEmpty()) return "다 그렸구나! 멋지다!"
    if (names.size == 1) return "다 그렸구나! ${names[0]} 멋지다!"
    val head = names.dropLast(1)
    val rang = if (bat(head.last())) "이랑" else "랑"
    return "다 그렸구나! ${head.joinToString(", ")}$rang ${names.last()} 멋지다!"
}

/** 오또 그림이 이만큼 늦으면 「조금 뒤에 올 거야」 (규칙 8 · 프로토타입 8초) */
internal const val OTTO_LATE_MS = 8_000L

/** 오또에게 부탁한 그림 한 장 — [art] 가 null 이면 대본(서버 없음) · 그림 글자로 보여 준다 */
private class OttoOrder(val pieceId: Int, val art: Deferred<ByteArray?>?) {
    val since = System.currentTimeMillis()
    var late = false
}

/** 그리는 중에 아이가 먼저 한 말 */
private enum class Heard { DONE, DRAW_ME, NAMED, OTHER }

private val DRAW_ME = Regex("너도 ?그려|오또도 ?그려|같이 ?그려|그려 ?줘|그려 ?줄래")

/**
 * 묻지 않았는데 한 말이 **이야기**인가(#220) — 두 어절 이상이거나 한 일을 말하는 끝(「~했어」 · 「~졌어」 · 「~갔어요」).
 * 한 낱말(「양동이」 · 「응」)은 조각 이름이거나 대답이라 판정에 보내지 않는다
 */
internal fun isStoryTalk(text: String): Boolean {
    val t = text.trim().trimEnd('.', '!', '?', '~', '…').trim()
    if (t.isEmpty() || dontKnow(t)) return false
    if (t.split(Regex("\\s+")).size >= 2) return true
    // 끝이 「어」 · 「어요」 · 「요」이고 그 앞 글자에 받침 ㅆ — 「했어」 「넘어졌어」 「갔어요」
    val stem = t.removeSuffix("요").removeSuffix("어")
    val last = stem.lastOrNull() ?: return false
    return stem.length < t.length && last in '가'..'힣' && (last - '가') % 28 == 20
}

/**
 * 그리는 중에 들은 말 — 「다 그렸어」면 끝, 「너도 그려줘」면 묻지 않고 바로 오또가 그린다(이름이 아직 없어도),
 * 방금 그리던 조각에 이름이 없으면 그 말을 이름으로 받는다(「이건 강아지야」). 아이 말은 모두 아이 출처다.
 */
private suspend fun Director.heardWhileDrawing(day: DiaryDay, r: Reply.Spoke, asked: Set<Int>): Heard {
    s.reactions++
    event("utterance", "speaker" to "child", "mode" to "voice", "text" to r.text)
    if (yesNoOf(r.text) == "done") return Heard.DONE
    if (DRAW_ME.containsMatchIn(r.text)) return Heard.DRAW_ME
    day.catchUp(s.drawing)
    // 「요 아니야, 집이야」 — 언제든 붙은 이름을 고친다
    day.correctionIn(r.text)?.let { (piece, name) -> setPieceName(day, piece.id, name, r.text); return Heard.OTHER }
    val piece = pieceBeingDrawn(day)
    // 물었는데 이름을 못 받은 조각이면 다시 한 말은 이름을 고쳐 말하는 것이다 — 「○○야」 꼴이 아니어도 받는다
    val retry = piece != null && piece.id in asked
    if (piece != null && (retry || soundsLikeAName(r.text)) && nameThePiece(day, piece, r) != null) return Heard.NAMED
    // 이름 붙은 조각에 이어 그리고 「조개 그렸어」 — 조각은 그대로, 그린 것 이름에 더한다(10-05 실기기 · 바다에 붙여 그린 조개)
    if (piece == null && saysWhatWasDrawn(r.text)) {
        val more = pieceNameFrom(r)?.takeIf { it !in day.pieceNames }
        if (more != null) {
            day.alsoDrawn += more
            s.slots["whiteboard"] = day.pieceNames.joinToString(", ")
            s.slotBy["whiteboard"] = "child"
            event("slot_filled", "slot" to "extra", "of" to "whiteboard", "value" to more, "source" to "child")
            quote(r.text)
            say("${you(more)}도 그렸구나!")
            log("「${r.text}」 — 이름 붙은 조각에 이어 그렸다 → 조각은 그대로, 그린 것에 「$more」를 더한다 (아이 말 · child)")
            pause(700)
            return Heard.OTHER                                  // 새 조각이 아니다 — 「나도 그려볼까?」를 다시 묻지 않는다
        }
    }
    say("그렇구나! 계속 그려 봐.")
    if (isStoryTalk(r.text)) keepUntoldStory(day, r)
    return Heard.OTHER
}

/**
 * 그리는 중 묻지 않았는데 한 이야기(「아빠가 도와줬어」)를 책 재료로 남긴다(#220 ①). 전에는 받기만 하고 버렸다.
 * 서버 모드면 `/turn` 판정이 고른 칸에 **덧붙이고**, 맞는 칸이 없거나 서버가 없으면 `extra` 에 쌓는다 — 모두 아이 출처.
 * 오또는 이미 구운 짧은 말로 받았다 — 서버 대사(목소리)는 쓰지 않는다(한 번 1원 안쪽 · #172 비용의 대부분이 목소리)
 */
private suspend fun Director.keepUntoldStory(day: DiaryDay, r: Reply.Spoke) {
    val said = r.text.trim()
    if (Server.liveFor(s.mode)) {
        day.turnCalls++
        val result = s.exchangeTurn("diary", null, "", said)
        val v = result?.verdict
        if (v?.reason == "blocked_by_filter") { log("서버 안전 판정 — 그리는 중 한 말을 책 재료에서 뺀다"); return }
        val fills = v?.fills.orEmpty().filter { (slot, value) -> slot in Server.SLOTS && value.isNotBlank() }
        if (fills.isNotEmpty()) {
            quote(said)
            fills.forEachIndexed { i, (slot, value) ->
                val key = bookKeyOf(slot, "")
                addToDiarySlot(slot, key, value.trim(), if (i == 0) said else value.trim())
                if (i > 0) day.sameSaying += key
            }
            result?.let { serverNext(it) }?.let { day.nextStory = it }
            log("그리는 중 묻지 않은 이야기 「$said」 → 판정 ${fills.joinToString { it.first }} 에 덧붙임 (child · #220)")
            return
        }
    }
    quote(said)
    addToDiarySlot("extra", "extra", said, said)
    log("그리는 중 묻지 않은 이야기 「$said」 → 맞는 칸이 없어 extra 에 쌓음 (child · #220)")
}

/** 칸에 **덧붙인다** — 이미 있으면 「앞 말 / 새 말」(#220 · 두 사건을 덮어쓰지 않는다). 같은 말이면 그대로 */
private fun Director.addToDiarySlot(slot: String, bookKey: String, value: String, line: String) {
    val before = s.slots[bookKey]?.takeIf(String::isNotBlank)
    if (before != null && line in before.split(" / ")) return
    setDiarySlot(slot, bookKey, value, if (before == null) line else "$before / $line", "child")
}

/**
 * 「응」 — 서버 모드면 그 조각만 PNG 로 만들어 뒤에서 `/image` redraw 를 부른다. 아이는 계속 그린다(기다리는 화면 없음).
 * 조각 이름은 가려서 보낸다(규칙 6).
 */
private fun Director.orderOttoDrawing(scope: CoroutineScope, piece: DiaryPiece, name: String): OttoOrder {
    if (!Server.liveFor(s.mode)) return OttoOrder(piece.id, null)
    // 배경은 판 전체를 보낸다 — 땅 · 하늘은 그은 자리가 뜻이다(#168). 무리는 가장 큰 덩어리 하나만 보낸다 —
    // 받은 그림을 덩어리 자리마다 찍는다(서버는 여럿을 보내면 자리를 바꿔 다시 짠다 · 10-02 측정)
    val backdrop = piece.role == PieceRole.BACKGROUND
    val png = if (backdrop) boardToPng(piece, s.drawingAspect) else pieceToPng(piece.redrawSample(), s.drawingAspect)
    if (png == null) {
        log("오또 그림 부탁 — 조각에 선이 없다. 원본 그대로")
        return OttoOrder(piece.id, scope.async { null })
    }
    val words = s.nameMask().mask(drawWords(name))
    log("오또 그림 부탁 → /image redraw${if (backdrop) " 배경(판 전체)" else ""} (PNG ${png.size / 1024} KB · 우리 서버까지만 · 서버는 쓰고 지운다)")
    return OttoOrder(piece.id, scope.async { if (backdrop) requestBackgroundRedraw(png, words) else requestRedraw(png, words) })
}

/**
 * 부탁한 그림이 왔다. 그림이면 조각에 붙이고 고르게 한다(참).
 * 서버가 못 그렸으면(검사 · 늦음 · 실패) 원본 그대로 두고 짧게만 알린다 — 프리셋으로 바꾸지 않는다(참).
 */
private suspend fun Director.receiveOttoDrawing(day: DiaryDay, order: OttoOrder): Boolean {
    val i = day.pieces.indexOfFirst { it.id == order.pieceId }
    if (i < 0) return false
    val job = order.art ?: run { showOttoDrawing(day, day.pieces[i]); return true }
    val png = job.await()
    event("image_request", "type" to "redraw", "result" to if (png != null) "generated" else "original")
    if (png == null) {
        log("오또 그림이 안 왔다(검사 · 늦음 · 실패) → 아이 원본 그대로")
        say("앗, 이번엔 내가 잘 못 그렸어. 네 그림이 최고야!")
        pause(700)
        return true
    }
    day.pieces[i] = day.pieces[i].copy(ottoPng = png)
    s.images++
    runCatching { android.util.Log.i("Diary", "redraw shown #${fingerprint(png)} on 「${day.pieces[i].name}」") }
    showOttoDrawing(day, day.pieces[i])
    return true
}

/** 마지막 획이 붙은 조각 — 이름이 아직 없을 때만 물을 거리다 */
private fun Director.pieceBeingDrawn(day: DiaryDay): DiaryPiece? {
    val lastStroke = s.drawing.lastOrNull() ?: return null
    return day.pieces.firstOrNull { lastStroke in it.strokes }?.takeIf { it.name == null }
}

/** 그린 사람 · 물건 이야기를 묻는 수 — 그리는 중 · 다 그린 뒤(못 물은 것) (#220 ②) */
internal const val PIECE_STORY_D1 = 3
internal const val PIECE_STORY_D3 = 2

/** 그리는 중에 묻는 이야기 질문 수 — 나머지 칸은 다 그린 뒤(D3)에 묻는다. 그림이 중심이다 */
private const val D1_STORY_MAX = 2

/** 그리는 중 첫 이야기 질문 — 모든 아이에게 같은 말이라 앱에 구운 목소리로 바로 나간다 */
private const val D1_FIRST_STORY = "여기는 어디야?"

/**
 * 다음 이야기 질문 — (판정 슬롯 · 질문 · 책 키) 또는 null.
 * 1. 서버가 앞 답을 받아 고른 질문이 있으면 그것(맥락이 담긴 질문)
 * 2. 아직 하나도 안 물었고 장소가 비었으면 「여기는 어디야?」
 * 3. 서버가 없으면(대본) 「거기서 무슨 일이 있었어?」
 */
private fun Director.nextD1Story(day: DiaryDay, asked: Set<String>): Triple<String, String, String>? {
    if (asked.size >= D1_STORY_MAX) return null
    fun open(key: String) = key !in asked && s.slots[key].isNullOrBlank()
    // 첫 질문 「여기는 어디야?」는 구운 목소리 그대로 — 그 뒤 질문은 그린 것이 있으면 그것을 실마리로 (DiaryClue.kt)
    day.nextStory?.takeIf { open(it.third) }?.let { return withClue(day, it) }
    if (asked.isEmpty() && open("place")) return Triple("place", D1_FIRST_STORY, "place")
    if (!Server.liveFor(s.mode) && open("problem")) return withClue(day, Triple("problem", atPlace(s, "무슨 일이 있었어?"), "problem"))
    return null
}

/**
 * 그리는 중 이야기 질문 하나 — 그리기를 막지 않는다(다른 조각을 그리기 시작하면 조용히 거둔다).
 * 서버 모드면 `/turn` 으로 판정해 칸을 아이 출처로 채우고, 오또가 받아 준 뒤 서버가 고른 다음 질문을 [DiaryDay.nextStory] 에 둔다.
 * 서버가 없거나 실패하면 아이 말 그대로 물은 칸에. 「몰라」 · 말이 없으면 비워 두고 D3 가 묻는다.
 * 돌려주는 값: done(그리기를 끝냈다) · 그 밖
 */
private suspend fun Director.askStoryWhileDrawing(day: DiaryDay, story: Triple<String, String, String>, ring: Int? = null): String {
    val (slot, text, key) = story
    day.nextStory = null
    val q = Question(
        text = text, kind = Kind.EASY, noCards = true,
        spoken = DIARY_STEPS.firstOrNull { it.bookKey == key }?.answers(s).orEmpty(),
        extra = listOf(DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) }),
        id = "diary_d1_$key", waitSec = D1_WAIT_SEC,
    )
    day.askingPiece = ring                                   // 배경을 보고 묻는 질문이면 그 배경에 고리
    val r = try { askWhileDrawing(q, day, about = ring) } finally { day.askingPiece = null }
    if (r is Reply.Tapped && (r.value == "done" || r.value == "skip")) return "done"
    val said = (r as? Reply.Spoke)?.text?.trim().orEmpty()
    if (r !is Reply.Spoke || said.isEmpty() || dontKnow(said)) {
        if (r is Reply.Spoke) { say("괜찮아, 계속 그려 봐!"); pause(600) }
        log("그리는 중 이야기 [$key] — 못 들었다 → 비워 두고 다 그린 뒤 묻는다")
        return "skip"
    }
    if (Server.liveFor(s.mode)) {
        day.turnCalls++
        val result = s.exchangeTurn("diary", slot, text, said)
        val v = result?.verdict
        if (v != null && v.reason != "blocked_by_filter") {
            fillFromVerdict(day, v, r, key)
            sayReaction(result, r)
            day.nextStory = serverNext(result)
            log("그리는 중 이야기 [$key] — 판정이 칸을 채웠다 · 다음 이야기 ${day.nextStory?.second ?: "없음(D3)"}")
            return "ok"
        }
        if (v != null) { log("서버 안전 판정 — 이 답은 책 재료에서 뺀다"); return "skip" }
        log("판정 서버가 답하지 않았다 → 아이 말을 물은 칸에 그대로")
    }
    val scripted = r.answer?.value?.takeIf(String::isNotBlank)          // 시연 대본 답이면 값이 붙어 온다
    setDiarySlot(slot, key, scripted?.let(::diarySlotOf) ?: said, scripted?.let(::diaryLineOf) ?: said, "child")
    sayAck(said, slot)
    log("그리는 중 이야기 [$key] = 「$said」 (child) — 다 그린 뒤에는 묻지 않는다")
    return "ok"
}

/**
 * 그린 사람 · 물건 하나의 이야기를 묻는다(#220 ②) — 답은 조각과 짝지어 두고 책 재료 `extra` 에 「이름: 말」로 쌓는다(아이 출처).
 * 서버 판정에는 보내지 않는다(호출이 늘지 않는다) — 어느 쪽에 쓸지는 책 쓰는 쪽이 정한다.
 * 돌려주는 값: done(그리기를 끝냈다) · ok · skip
 */
private suspend fun Director.askPieceStory(day: DiaryDay, clue: DrawnClue, text: String, drawing: Boolean): String {
    day.pieceStoryAsked += clue.pieceId
    day.cluesUsed += clue.pieceId                        // 그림 실마리로 같은 조각을 또 짚지 않는다
    val q = Question(
        text = text, kind = Kind.EASY, noCards = true, id = "diary_piece_story", waitSec = D1_WAIT_SEC,
        extra = if (drawing) listOf(DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) }) else emptyList(),
    )
    val r = if (drawing) {
        day.askingPiece = clue.pieceId
        try { askWhileDrawing(q, day, about = clue.pieceId) } finally { day.askingPiece = null }
    } else ask(q)
    if (r is Reply.Tapped && (r.value == "done" || r.value == "skip")) return "done"
    val said = (r as? Reply.Spoke)?.text?.trim().orEmpty()
    if (said.isEmpty() || dontKnow(said)) {
        if (drawing && r is Reply.Spoke) { say("괜찮아, 계속 그려 봐!"); pause(600) }
        log("「${clue.name}」 이야기 — 못 들었다 → 비워 둔다")
        return "skip"
    }
    day.pieceStories[clue.pieceId] = said
    quote(said)
    addToDiarySlot("extra", "extra", "${clue.name}: $said", "${clue.name}: $said")
    say(if (drawing) "그렇구나! 계속 그려 봐." else "그랬구나!")
    pause(600)
    log("「${clue.name}」 이야기 = 「$said」 (child · extra 에 쌓음 · #220)")
    return "ok"
}

/** 다 그린 뒤 — 그리는 중에 못 물은 사람 · 물건 이야기를 [PIECE_STORY_D3] 개까지 (#220 ②) */
private suspend fun Director.askPieceStoriesAfterDrawing(day: DiaryDay) {
    s.stage = DiaryAsk
    repeat(PIECE_STORY_D3) {
        val (clue, text) = s.pieceStoryQuestion(day) ?: return
        askPieceStory(day, clue, text, drawing = false)
    }
}

/** 조각을 물은 결과 — 붙은 이름 · 그리기를 끝냈나 · 다른 조각으로 넘어가 거뒀나(그 조각은 나중에 다시 물을 수 있다) */
private data class PieceAnswer(val name: String?, val finished: Boolean = false, val movedOn: Boolean = false)

/**
 * 「지금 그리는 건 뭐야?」 — 아이가 붙인 이름만 조각 이름이 된다.
 * [PieceAnswer.finished] 면 묻는 사이에 아이가 [다 그렸어]를 눌렀다 — 그리기를 끝낸다.
 */
private suspend fun Director.askPieceName(day: DiaryDay, piece: DiaryPiece): PieceAnswer {
    // 이름 붙은 조각에 닿게 그렸으면 — 거기에 더 그린 건지, 새로 그린 건지를 먼저 묻는다(프로토타입 규칙)
    val neighbor = day.namedNeighborOf(piece)
    val r = if (neighbor != null) askMoreOrNew(day, piece, neighbor) else {
        val q = Question(text = "우와, 지금 그리는 건 뭐야?", kind = Kind.EASY, noCards = true, spoken = PIECE_ANSWERS, id = "diary_piece", waitSec = D1_WAIT_SEC)
        day.askingPiece = piece.id
        try { askWhileDrawing(q, day, about = piece.id) } finally { day.askingPiece = null }
    }
    if (r is Reply.Tapped && (r.value == "done" || r.value == "skip")) return PieceAnswer(null, finished = true)
    if (r is Reply.Tapped && r.value == MOVED_ON) return PieceAnswer(null, movedOn = true)
    if (neighbor != null && r is Reply.Spoke && addedTo(r)) {
        day.mergeInto(piece.id, neighbor.id)
        say("${you(neighbor.name.orEmpty())}에 더 그렸구나!")
        log("「${neighbor.name}」에 더 그린 선 → 그 조각에 합친다 (아이 말)")
        pause(700)
        return PieceAnswer(neighbor.name)
    }
    var name = (r as? Reply.Spoke)?.let { nameThePiece(day, piece, it) }
    // 말은 했는데 이름이 안 나왔다(잘못 알아들음 · 딴말) — 한 번만 다시 묻는다. 「몰라」면 다시 묻지 않는다 (10-02 실기기)
    if (name == null && r is Reply.Spoke && !dontKnow(r.text) && day.pieces.any { it.id == piece.id }) {
        val again = Question(text = "잘 못 들었어. 뭐 그린 거야?", kind = Kind.EASY, noCards = true, spoken = PIECE_ANSWERS, id = "diary_piece", waitSec = D1_WAIT_SEC)
        day.askingPiece = piece.id
        val r2 = try { askWhileDrawing(again, day, about = piece.id) } finally { day.askingPiece = null }
        if (r2 is Reply.Tapped && (r2.value == "done" || r2.value == "skip")) return PieceAnswer(null, finished = true)
        if (r2 is Reply.Tapped && r2.value == MOVED_ON) return PieceAnswer(null, movedOn = true)
        name = (r2 as? Reply.Spoke)?.let { nameThePiece(day, piece, it) }
    }
    if (name == null) {
        say(if (r is Reply.Spoke) "그래, 계속 그려 봐." else "계속 그려 봐!")
        log("조각 이름을 못 들었다 → 이름 없이 둔다. 아이가 다시 말하면 이 조각 이름으로 받는다")
        return PieceAnswer(null)
    }
    return PieceAnswer(name)
}

/**
 * 「그려줘」 — 말로(「강아지 그려줘」) 또는 그림판의 [그려 줘] 버튼으로. 오또가 그 조각을 뒤에서 그린다. 주문했으면 참.
 * 부른 이름이 있으면 그 조각, 없으면 방금 누른 이름표(그 뒤로 새 획이 없을 때), 아니면 방금 그리던 조각 (10-02 실기기)
 */
private suspend fun Director.drawMe(scope: CoroutineScope, day: DiaryDay, waiting: MutableList<OttoOrder>, said: String?): Boolean {
    day.catchUp(s.drawing)
    val tapped = day.focus?.takeIf { it.second == s.drawing.size }?.let { (id, _) -> day.pieces.firstOrNull { it.id == id } }
    val target = said?.let { day.namedIn(it, except = -1) } ?: tapped
        ?: day.pieces.lastOrNull { s.drawing.lastOrNull() in it.strokes } ?: day.pieces.lastOrNull()
    // The lines carry no piece name, so they play baked from the app instead of /tts (10-06 lead: 3 of the
    // 4-5 server lines per Otto drawing were these). The board already marks the piece; the log names it
    val label = target?.name ?: "이름 없는 조각"
    when {
        target == null -> say("그림을 먼저 그려 줘! 그다음에 나도 그려 볼게.")
        waiting.any { it.pieceId == target.id } -> {
            log("오또 그림 — 「$label」 그리는 중")
            say("나도 지금 그리고 있어! 조금만 기다려 줘.")
        }
        target.ottoPng != null -> {
            log("오또 그림 — 「$label」 벌써 그렸다")
            say("벌써 그렸어! 반짝이는 이름표를 눌러 봐.")
        }
        else -> {
            // 이름 없는 배경은 아이가 말한 장소로 주문한다 — 배경을 그리면 「여기는 어디야?」를 물었다 (#168).
            // 이름 없는 물체는 먼저 무엇인지 묻는다 — 「아이가 그린 그림」으로는 서버가 무엇인지 몰라 늘 거절했다 (10-06 실기기)
            val words = target.name
                ?: s.slots["place"]?.takeIf { target.role == PieceRole.BACKGROUND && it.isNotBlank() }
                ?: askNameToDraw(day, target)
                ?: return false
            log("오또 그림 — 「$words」 그린다")
            say("나도 그려 볼게! 더 그리고 있어!")
            waiting += orderOttoDrawing(scope, target, words)
            pause(600)
            return true
        }
    }
    return false
}

/**
 * [그려 줘]를 누른 조각에 이름이 없다 — 무엇인지 물어 이름을 붙이고 그 이름을 돌려준다. 못 들었으면 null(주문하지 않는다).
 * 묻는 말 · 못 들었을 때의 말은 앱에 구운 대사라 `/tts` 를 부르지 않는다
 */
private suspend fun Director.askNameToDraw(day: DiaryDay, piece: DiaryPiece): String? {
    val q = Question(text = "우와, 지금 그리는 건 뭐야?", kind = Kind.EASY, noCards = true, spoken = PIECE_ANSWERS, id = "diary_piece", waitSec = D1_WAIT_SEC)
    day.askingPiece = piece.id
    val r = try { askWhileDrawing(q, day, about = piece.id) } finally { day.askingPiece = null }
    if (r is Reply.Tapped && r.value == MOVED_ON) return null       // 다른 걸 그리러 갔다 — 말없이
    val name = (r as? Reply.Spoke)?.let { pieceNameFrom(it) }
    if (name == null || !setPieceName(day, piece.id, name, (r as Reply.Spoke).text)) {
        log("오또 그림 — 이름 없는 조각 · 이름을 못 들었다 → 주문하지 않는다")
        say("그래, 그대로 둘게!")
        pause(600)
        return null
    }
    return name
}

/**
 * [이름 고치기]가 가리키는 조각 — 방금 누른 이름표(그 뒤로 새 획이 없을 때), 아니면 방금 그리던 조각, 아니면 마지막으로 이름 붙은 조각.
 * 이름 없는 조각은 고칠 이름이 없다 — 붓이 멈추면 오또가 먼저 묻는다
 */
internal fun DemoState.renameTarget(day: DiaryDay): DiaryPiece? {
    day.catchUp(drawing)
    val named = day.pieces.filter { it.name != null }
    val tapped = day.focus?.takeIf { it.second == drawing.size }?.let { (id, _) -> named.firstOrNull { it.id == id } }
    return tapped ?: named.lastOrNull { drawing.lastOrNull() in it.strokes } ?: named.lastOrNull()
}

/** 이름표를 길게 눌렀다 — 「이건 뭐야? 다시 말해 줘!」. 이름이 나오면 고치고, 아니면 그대로 둔다 */
private suspend fun Director.askRename(day: DiaryDay, piece: DiaryPiece) {
    val q = Question(text = "이건 뭐야? 다시 말해 줘!", kind = Kind.EASY, noCards = true, spoken = PIECE_ANSWERS, id = "diary_rename", waitSec = D1_WAIT_SEC)
    day.askingPiece = piece.id
    val r = try { askWhileDrawing(q, day, about = piece.id) } finally { day.askingPiece = null }
    if (r is Reply.Tapped && r.value == MOVED_ON) return          // 다른 걸 그리러 갔거나 다른 조작을 눌렀다 — 「그대로 둘게」 없이
    val name = (r as? Reply.Spoke)?.let { pieceNameFrom(it) }
    val now = you(piece.name.orEmpty())
    when {
        name == null -> { say("그래, 그대로 둘게!"); pause(600) }
        name == piece.name -> { say("맞아, ${now}${ida(now)}!"); pause(600) }
        else -> setPieceName(day, piece.id, name, (r as Reply.Spoke).text)
    }
}

/**
 * 그림판에서 누른 조작 — [그려 줘] · [이름 고치기] · 이름표(톡 · 길게 · ✨). 오또가 묻는 중에도 받고, 답으로 섞지 않는다.
 * [다 그렸어](done)는 여기 없다 — 묻는 질문이 끝을 알아야 해서 전처럼 답으로 돌려준다
 */
internal fun isBoardTool(value: String): Boolean =
    value == "drawme" || value == "rename" || value.startsWith("rename:") || value.startsWith("name:") || value.startsWith("look:")

/**
 * 그림판 조작을 보낸다. 오또가 지켜보는 중이 아니면 [DiaryDay.pendingTap] 에도 남긴다 — 묻는 말이 끝나고 답을 기다리기 직전에
 * 누르면 기다리기 전에 비우는 입력과 함께 사라졌다. 줄로 먼저 닿으면 그리기 흐름이 남긴 것을 지운다(두 번 하지 않는다)
 */
fun Director.sendBoardTool(r: Reply.Tapped) {
    if (!s.diaryDay.watching) s.diaryDay.pendingTap = r
    send(r)
}

/** D1 질문을 조용히 거둔 까닭 — 아이가 다른 조각을 그리기 시작했다 · 손을 놓고 말이 없었다 */
internal const val MOVED_ON = "@moved_on"
internal const val WENT_QUIET = "@quiet"

/**
 * 그리면서 묻는 D1 질문 — `ask()` 대신 **손의 움직임**으로 기다린다 (10-01 진웅 · 안 A).
 * 1. 묻는 조각([about])을 계속 그리면 질문을 열어 두고 시간을 세지 않는다
 * 2. 다른 조각을 그리기 시작하면 조용히 거둔다([MOVED_ON]) — 지금 그리는 그림에 대한 질문이 먼저다
 * 3. 손을 놓고 [Question.waitSec] 동안 말이 없으면 거둔다([WENT_QUIET]). 말하는 중(마이크)에는 세지 않는다
 *
 * 아이가 한 말만 주고받기로 센다(`acceptSpoken`) — 거둔 것은 답이 아니라 세지 않는다(규칙 5).
 * `ask()` 로 거두면 「괜찮아, 다음에 같이 생각해 보자!」가 나오거나(무응답) 탭으로 세어져서 따로 둔다.
 */
private suspend fun Director.askWhileDrawing(q: Question, day: DiaryDay, about: Int?): Reply = coroutineScope {
    // 아이가 말하는 중(녹음 중)이면 묻지 않는다 — 오또 목소리가 아이 말과 같이 녹음된다(10-01 실기기).
    // 그 말은 그리기 판이 받아 먼저 듣는다(이름이면 이름으로). 이 질문은 [MOVED_ON] 처럼 미룬다
    if (s.micOn) {
        log("「${q.text}」 — 아이가 말하는 중이라 묻지 않고 미룬다(목소리가 녹음에 섞이지 않게)")
        return@coroutineScope Reply.Tapped(MOVED_ON, "말하는 중")
    }
    val linesAtAsk = s.drawing.size                     // 오또가 묻는 말을 하는 사이에 그은 선도 본다
    say(q.text)
    val answers = q.spoken.map { a -> DemoBtn("🗣 \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) } }
    val waitMs = ((q.waitSec ?: D1_WAIT_SEC) * 1000).toLong()
    // 조용함은 **실제로 흐른 시간**으로 센다 — 돈 횟수로 세면 OS 타이머 정밀도에 따라 달라졌다
    // (리눅스 CI 에서 1ms 잠이 정말 1ms 라 0.1초 만에 거뒀다 · #66). 빠르게 돌리는 시연 · 검사도 최소 [QUIET_FLOOR_MS] 는 기다린다
    val quietLimitMs = maxOf((waitMs * s.speed).toLong(), minOf(waitMs, QUIET_FLOOR_MS))
    val watch = launch {
        // 조용함은 오또가 묻는 말을 다 한 뒤부터 센다. 기다림은 여기서 한다 — 답을 받는 쪽([awaitReplyShowing])은 미리 열어 둔다
        if (Server.liveFor(s.mode)) { awaitVoice(); pause(300) } else pause(1200)
        var seen = linesAtAsk
        var quietMs = 0L
        var last = System.nanoTime()
        while (true) {
            delay((WATCH_STEP_MS * s.speed).toLong().coerceAtLeast(1))
            val now = System.nanoTime()
            val stepMs = (now - last) / 1_000_000
            last = now
            if (s.micOn || day.penDown) { quietMs = 0; continue }      // 말하는 중 · 선을 긋는 중은 조용한 게 아니다
            if (s.drawing.size > seen) {
                val fresh = s.drawing.subList(seen, s.drawing.size).toList()
                seen = s.drawing.size
                day.catchUp(s.drawing)
                val sameOne = about != null && fresh.all { st -> day.pieces.firstOrNull { st in it.strokes }?.id == about }
                if (!sameOne) { send(Reply.Tapped(MOVED_ON, "다른 조각을 그림")); return@launch }
                quietMs = 0
                continue
            }
            quietMs += stepMs
            if (quietMs >= quietLimitMs) { send(Reply.Tapped(WENT_QUIET, "조용함")); return@launch }
        }
    }
    // 앞 입력을 비우고 **나서** 마이크를 연다 — 전에는 마이크를 연 뒤 오또 말이 끝나길 기다렸다가 비워서,
    // 오또가 말하는 사이 🎤 를 눌러 한 답(「아니」)이 버려지고 10초 뒤 「말이 없었다」로 거뒀다 (10-05 실기기 · VoiceInbox)
    val got = try {
        awaitReplyShowing {
            inputs(mic = true, next = true)
            buttons(*(answers + DemoBtn("🤐 대답 없음") { send(Reply.Silent) } + q.extra).toTypedArray())
        }
    } finally { watch.cancel() }
    // 그림판 조작([그려 줘] · [이름 고치기] · 이름표)은 이 질문의 답이 아니다 — 남겨 두고 질문을 거둔다.
    // 부르는 쪽은 다른 조각을 그리러 간 것처럼([MOVED_ON]) 조용히 넘어가고, 그리기 흐름이 그 조작을 바로 받는다 (10-05 진웅)
    val tool = got is Reply.Tapped && isBoardTool(got.value)
    if (tool) day.pendingTap = got as Reply.Tapped
    val r = if (tool) Reply.Tapped(MOVED_ON, (got as Reply.Tapped).label) else got
    // 실기기에서 어느 길로 끝났는지 본다 — 질문 · 끝난 까닭만(아이 말은 남기지 않는다)
    val outcome = when (got) { is Reply.Spoke -> "answered"; is Reply.Tapped -> got.value; else -> got.javaClass.simpleName }
    runCatching { android.util.Log.i("Diary", "D1 「${q.text}」 → $outcome") }         // 단위 테스트에는 Log 가 없다
    DiaryTrace.ask(q.text, outcome)
    when {
        r is Reply.Spoke -> acceptSpoken(r.text)
        tool -> { s.line = ""; log("「${q.text}」 — 아이가 「${(got as Reply.Tapped).label}」을 눌렀다 → 거두고 그 조작으로") }
        r is Reply.Tapped && r.value == MOVED_ON -> { s.line = ""; log("「${q.text}」 — 다른 조각을 그리기 시작했다 → 조용히 거둔다(지금 그리는 그림이 먼저)") }
        // 거둔 질문을 말풍선에 남기지 않는다 — 묻는 말은 접히지 않아서 답을 기다리는 것처럼 보였다(10-02 실기기)
        r is Reply.Tapped && r.value == WENT_QUIET -> { s.line = ""; log("「${q.text}」 — 손을 놓고 ${waitMs / 1000}초 말이 없었다 → 거둔다") }
        r is Reply.Tapped -> acceptTap(r)
    }
    inputs(mic = false, next = false)
    r
}

/** 손의 움직임을 보는 간격 */
private const val WATCH_STEP_MS = 100L

/** 빠르게(speed < 1) 돌릴 때도 D1 질문이 적어도 이만큼(실제 시간)은 답을 기다린다 — 실기기 10초에는 영향이 없다 */
private const val QUIET_FLOOR_MS = 2_000L

/** 「○○에 더 그린 거야, 새로 그린 거야?」 — [piece] 를 가리키며 묻는다 */
private suspend fun Director.askMoreOrNew(day: DiaryDay, piece: DiaryPiece, named: DiaryPiece): Reply {
    val q = Question(
        text = "${you(named.name.orEmpty())}에 더 그린 거야, 새로 그린 거야?",
        kind = Kind.EASY,
        noCards = true,
        spoken = listOf(Answer("더 그렸어!", MORE_HERE, lv = 1), Answer("새로 그렸어, 땅이야.", "땅", lv = 2)),
        id = "diary_piece",
        waitSec = D1_WAIT_SEC,
    )
    day.askingPiece = piece.id
    return try { askWhileDrawing(q, day, about = piece.id) } finally { day.askingPiece = null }
}

/** 대본 답의 값 — 「거기에 더 그렸어」 */
private const val MORE_HERE = "@more"

private val MORE = Regex("더 ?그렸|이어서|거기에|같이 그린|붙여")

/** 「더 그렸어」 — 닿은 이름 조각에 붙인다. 「새로 그렸어」면 아니다 */
private fun addedTo(r: Reply.Spoke): Boolean =
    r.answer?.value == MORE_HERE || (MORE.containsMatchIn(r.text) && !r.text.contains("새로"))

/**
 * 아이 말에서 조각 이름을 받아 붙인다 — 아이가 말한 이름만(규칙 5). 이름이 아니면 null, 아무것도 안 바꾼다.
 * 다른 이름 조각을 부르면(「우리 집 창문」 · 「강아지 꼬리」 · 「우리 집에 그렸어」) 그 조각에 합친다 —
 * 닿아 있을 때만 바로. 떨어져 있으면 「○○에 더 그린 거야, 새로 그린 거야?」를 먼저 묻는다(멀리 그린 다른 강아지일 수 있다)
 */
private suspend fun Director.nameThePiece(day: DiaryDay, piece: DiaryPiece, r: Reply.Spoke): String? {
    if (r.answer?.value == MORE_HERE) return null
    var said = r
    day.namedIn(r.text, except = piece.id)?.takeIf { r.answer == null || r.answer.value.isBlank() }?.let { other ->
        val again = if (day.touching(piece.id, other.id)) null else askMoreOrNew(day, piece, other)
        if (again == null || again is Reply.Spoke && addedTo(again)) {
            day.mergeInto(piece.id, other.id)
            quote(r.text)
            say("${you(other.name!!)}${eul(you(other.name!!))} 더 그렸구나!")
            log("「${r.text}」 — 「${other.name}」을 불렀다 → 그 조각에 합친다 (아이 말${if (again != null) " · 떨어져 있어 물었다" else ""})")
            pause(700)
            return other.name
        }
        // 새로 그렸다 — 새 이름을 말했으면 그것, 아니면 처음 한 말에서 이름을 뗀다
        if (again is Reply.Spoke && pieceNameFrom(again) != null) said = again
    }
    val name = pieceNameFrom(said) ?: return null
    // 「해랑 구름」 — 앞에 이름 없이 그린 조각이 있으면 그린 차례대로 나눠 붙인다(10-05 실기기 · 한 조각에 「해랑 구름」이 붙었다)
    splitAcross(name, piece, day.pieces)?.let { split ->
        split.forEach { (id, part) -> setPieceName(day, id, part, said.text, speak = false) }
        quote(said.text)
        say("${you(name)}${ida(you(name))}구나!")
        log("조각 이름 「$name」 — 그린 차례대로 나눠 붙였다: ${split.joinToString(" · ") { "${it.first}=${it.second}" }} (아이 말 · child)")
        pause(700)
        return split.last().second
    }
    if (!setPieceName(day, piece.id, name, said.text)) return null
    return name
}

/**
 * 조각 이름을 아이 말로 붙이거나 고친다 — 아이 출처(규칙 5). 이름이 바뀌면 옛 이름으로 그린 오또 그림은 떼고 원본으로 둔다.
 * 고칠 때는 「아, 집이구나!」 (10-02 진웅 — 잘못 들은 이름을 고칠 수 있어야 한다)
 */
private suspend fun Director.setPieceName(day: DiaryDay, pieceId: Int, name: String, said: String, speak: Boolean = true): Boolean {
    val i = day.pieces.indexOfFirst { it.id == pieceId }
    if (i < 0) return false
    val old = day.pieces[i].name
    val redrawn = old != null && (day.pieces[i].ottoPng != null || day.pieces[i].look == PieceLook.OTTO)
    day.pieces[i] = day.pieces[i].copy(name = name).let { if (redrawn) it.copy(ottoPng = null, look = PieceLook.ORIGINAL) else it }
    s.slots["whiteboard"] = day.pieceNames.joinToString(", ")
    s.slotBy["whiteboard"] = "child"
    event("slot_filled", "slot" to "extra", "of" to "whiteboard", "value" to name, "source" to "child")
    DiaryTrace.name(pieceId, name)
    if (!speak) return true                                     // 여럿을 한 번에 — 받아 주기는 부른 쪽에서 한 번
    quote(said)
    if (old == null) {
        say("${you(name)}${ida(you(name))}구나!")
        log("조각 이름 「$name」 — 아이가 말한 이름 (extra · whiteboard · child)")
    } else {
        say("아, ${you(name)}${ida(you(name))}구나!")
        log("조각 이름 고침 「$old」 → 「$name」 — 아이가 고쳐 말했다${if (redrawn) " · 옛 이름으로 그린 오또 그림은 떼고 원본으로" else ""}")
    }
    pause(700)
    return true
}

/** 「요 아니야, 집이야」 · 「요가 아니고 집」 — 앞말이 붙은 이름, 뒷말이 고친 이름 */
private val NOT_THAT = Regex("^(.+?)\\s*(이|가)?\\s*아니(야|고|라|에요|예요)?[,.!~]*\\s+(.+)$")

/** 아이가 조각 이름을 고쳐 말했나 — 「요 아니야, 집이야」. 앞말이 이 판의 조각 이름이어야 한다. (그 조각 · 새 이름) 또는 null */
internal fun DiaryDay.correctionIn(text: String): Pair<DiaryPiece, String>? {
    val m = NOT_THAT.find(text.trim()) ?: return null
    val said = setOf(m.groupValues[1].trim(), m.groupValues[1].trim() + m.groupValues[2])
    val piece = pieces.firstOrNull { it.name != null && it.name in said } ?: return null
    val name = pieceNameFrom(Reply.Spoke(m.groupValues[4])) ?: return null
    return (piece to name).takeIf { name != piece.name }
}

/**
 * 「나도 ○○ 그려볼까?」에 이름을 고쳐 답했나 — 「아니, 집이야」 · 「요 아니야, 집이야」. 새 이름 또는 null.
 * 「아니, 내 그림이 좋아」 · 「아니 괜찮아」는 이름이 아니다 — 「~야」로 끝나는 이름 꼴만 받는다
 */
internal fun DiaryDay.renameInAnswer(text: String, piece: DiaryPiece): String? {
    correctionIn(text)?.takeIf { it.first.id == piece.id }?.let { return it.second }
    val rest = Regex("^아니[야,.!~ ]*").replace(text.trim(), "").trim()
    if (rest == text.trim() || !soundsLikeAName(rest)) return null
    return pieceNameFrom(Reply.Spoke(rest))?.takeIf { it != piece.name }
}

/**
 * 「나도 ○○ 그려볼까?」를 묻고, 응이면 그 조각을 주문해 [waiting] 에 넣는다. yes · no · done.
 * 「더 그렸어」로 합쳤으면 물은 조각([id])은 없어지고 이름 조각만 남는다 — 그 조각을 그린다
 */
/** 「나도 ○○ 그려볼까?」를 지금 물어도 되는 조각 — 남아 있고 이름이 있고, 오또 그림을 받지도 주문하지도 않았다. 이름은 지금 이름 */
private fun DiaryDay.offerable(id: Int, waiting: List<OttoOrder>): DiaryPiece? =
    pieces.firstOrNull { it.id == id }
        ?.takeIf { it.name != null && it.ottoPng == null && it.look != PieceLook.OTTO && waiting.none { w -> w.pieceId == id } }

private suspend fun Director.offerAndOrder(scope: CoroutineScope, day: DiaryDay, waiting: MutableList<OttoOrder>, id: Int, name: String): String {
    val v = offerOttoDrawing(name, day, id)
    if (v == "yes") (day.pieces.firstOrNull { it.id == id } ?: day.pieces.firstOrNull { it.name == name })
        ?.let { waiting += orderOttoDrawing(scope, it, it.name ?: name) }
    return v
}

/**
 * 「나도 ○○ 그려볼까?」 — 응이면 뒤에서 그리고 아이는 계속 그린다. 기다리는 화면이 없다. yes · no · done
 * 말로 답한다(마이크) — 누를 버튼이 화면에 없기 때문이다. 못 알아들었거나 말이 없으면 「아니」로 둔다
 */
private suspend fun Director.offerOttoDrawing(name: String, day: DiaryDay? = null, pieceId: Int? = null): String {
    val piece = pieceId?.let { id -> day?.pieces?.firstOrNull { it.id == id } }
    var heard = ""
    val v = askYesNo(
        "나도 ${you(name)}${eul(you(name))} 그려볼까?", "diary_offer",
        yes = Answer("응!", "yes", lv = 1), no = Answer("아니, 내 그림이 좋아.", "no", lv = 1),
        rename = if (day != null && piece != null) { text -> heard = text; day.renameInAnswer(text, piece) } else null,
    ).let {
        // 「아니, 집이야」 — 이름을 고쳐 말했다. 고치고 새 이름으로 한 번 더 묻는다
        if (it.startsWith(RENAMED) && day != null && piece != null) {
            val fixed = it.removePrefix(RENAMED)
            setPieceName(day, piece.id, fixed, heard)
            return offerOttoDrawing(fixed)
        }
        when (it) {
            "yes", "done", MOVED_ON -> it        // 묻는 사이 다른 걸 그리러 갔으면 아무 말 없이 — 부른 쪽이 다음에 다시 묻는다
            WENT_QUIET, "silent" -> "quiet"      // 말이 없었다 — 「아니」로 두되 「네 그림이 최고야」는 하지 않는다
            else -> "no"
        }
    }
    when (v) {
        "yes" -> {
            say("나도 그려 볼게! 너도 더 그리고 있어!")
            log("오또 그림 부탁 — 뒤에서 만든다(서버 모드면 /image redraw · 아니면 대본 그림)")
        }
        "no" -> say("좋아, 네 그림이 최고야!")
    }
    if (v == "yes" || v == "no") pause(600)
    return v
}

/** 물을 것이 떨어졌다 — 「다 그렸어? 더 그릴 거 있어?」 참이면 그리기를 끝낸다. 말이 없거나 다시 그리기 시작하면 계속 그리게 둔다 */
private suspend fun Director.askDoneDrawing(): Boolean {
    val v = askYesNo(
        "다 그렸어? 더 그릴 거 있어?", "diary_done",
        yes = Answer("응, 다 그렸어!", "yes", lv = 1), no = Answer("더 그릴래!", "no", lv = 1),
    )
    if (v == "no") { say("좋아, 더 그려 봐!"); pause(600) }
    return v == "yes" || v == "done"
}

private val DONE = Regex("다 그렸|끝났|그만 그릴|그리기 싫")
private val YES = Regex("^\\s*(응|어|그래|좋아|네|예|웅)|그려 ?줘")
private val NO = Regex("아니|싫어|안 ?돼|더 그릴|아직")

/** 아이 말 → done · yes · no · null(모르겠다). 「다 그렸어」가 먼저, 그다음 「아니」 — 「아니, 좋아」는 no */
internal fun yesNoOf(text: String): String? = when {
    DONE.containsMatchIn(text) -> "done"
    NO.containsMatchIn(text) -> "no"
    YES.containsMatchIn(text) -> "yes"
    else -> null
}

/**
 * 예/아니 질문 — 마이크로 듣는다. 대본 답은 값(yes · no)이 붙어 오고, 서버 모드의 말은 글자로 가른다.
 * 돌려주는 값: yes · no · done(시연 서랍 [다 그렸어]) · skip · silent · unclear · [MOVED_ON] · [WENT_QUIET]
 */
private suspend fun Director.askYesNo(text: String, id: String, yes: Answer, no: Answer, rename: ((String) -> String?)? = null): String {
    val q = Question(
        text = text,
        kind = Kind.EASY,
        noCards = true,
        spoken = listOf(yes, no),
        extra = listOf(DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) }),
        id = id,
        waitSec = D1_WAIT_SEC,
    )
    // 어느 조각이든 다시 그리기 시작하면 거둔다(about = null) — 그리는 중인 아이에게 예/아니를 붙잡고 있지 않는다
    return when (val r = askWhileDrawing(q, s.diaryDay, about = null)) {
        is Reply.Tapped -> r.value
        is Reply.Spoke -> r.answer?.value?.takeIf { it.isNotBlank() }
            ?: rename?.invoke(r.text)?.let { RENAMED + it }
            ?: yesNoOf(r.text) ?: "unclear"
        else -> "silent"
    }
}

/** 예/아니 대신 조각 이름을 고쳐 답했다 — 「@renamed:집」 */
private const val RENAMED = "@renamed:"

/** 오또 그림이 왔다 — 보여 주고 아이가 고른다. 원본이 기본값이다 */
private suspend fun Director.showOttoDrawing(day: DiaryDay, piece: DiaryPiece) {
    // 이름 없는 배경은 아이가 말한 장소로 부른다 — 배경은 이름 대신 「여기는 어디야?」를 물었다 (#168 · 10-06 실기기)
    val name = piece.name
        ?: s.slots["place"]?.takeIf { piece.role == PieceRole.BACKGROUND && it.isNotBlank() }
        ?: "배경".takeIf { piece.role == PieceRole.BACKGROUND }
        ?: return
    s.stage = DiaryBoard(pick = piece.id)
    log("오또 그림이 왔다 — 「$name」 · 고르게 한다")
    say("짠! 나도 그려 봤어! 어떤 게 좋아?")                // baked — the board marks which piece (10-06)
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
        say("펑! 내가 그린 거야. 고마워!")
        log("「$name」 → 오또 그림으로 (부모 기록: 아이가 고른 오또 그림 · 원본도 보관)")
    } else {
        say("띠용! 역시 네가 그린 게 최고야!")
        log("「$name」 → 아이 원본 그대로")
    }
    pause(900)
}

/** 그림판의 획을 책에 쓸 그림으로 옮긴다 — 조각에 아직 안 붙은 획도 붙여 둔다 */
private fun Director.keepBoard() {
    if (s.drawing.isEmpty()) return
    s.diaryDay.catchUp(s.drawing)
    DiaryTrace.pieces(s.diaryDay, s.drawing)                 // 다 그렸을 때 조각 묶음 — 기준값을 정할 정답 자리
    s.keepSceneDrawing()
    s.reactions++
    event("make", "kind" to "draw")
}

// ── D3 ─────────────────────────────────────────────────────────

/**
 * 다 그린 뒤 — 빈 칸만 묻는다. 필수(place · problem)가 먼저, 남으면 결말 · 내일. 칸마다 [SLOT_TRIES] 번까지 · 전체 상한은 없다(#89).
 * 서버 모드면 판정의 `next_slot` 과 오또 대사가 다음 질문을 정한다([askEmptySlotsLive]). 대본이면 이 차례다.
 */
private suspend fun Director.askEmptySlots() {
    s.stage = DiaryAsk
    if (Server.liveFor(s.mode)) { askEmptySlotsLive(); return }
    val queue = PICTURE_QUESTIONS.filter { s.slots[it.key].isNullOrBlank() }.toMutableList()
    var asked = 0
    var wrapOffered = false
    var pieceAsked = false
    while (queue.isNotEmpty()) {
        if (!wrapOffered && diaryRanLong()) {
            wrapOffered = true
            if (offerWrapUp()) break
        }
        // 필수 두 칸을 물은 뒤 — 이름 없는 조각 하나를 묻는다(10-02)
        if (!pieceAsked && queue.none { it.key in PICTURE_REQUIRED }) {
            pieceAsked = true
            val unnamed = firstUnnamedPiece()
            if (unnamed != null) { askPieceOnD3(unnamed); continue }
        }
        val pq = queue.removeAt(0)
        asked++
        s.stepsDone++
        askPictureSlot(pq)
        if (s.endReason == null && PICTURE_REQUIRED.all { !s.slots[it].isNullOrBlank() }) {
            s.endReason = "story_ready"
            log("필수 두 칸(place · problem)이 찼다 → story_ready. 남은 칸(결말 · 내일)도 묻는다")
        }
    }
    log("다 그린 뒤 ${asked}칸 물었다")
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
        // 「내일」에 「없어」 — 하고 싶은 게 없다는 답이다. 다시 묻지 않고 칸을 비워 둔다 (10-05 실기기 · 「없어」가 내일 칸에 들어갔다)
        if (pq.key == "keep" && r.answer == null && saysNothing(r.text)) {
            log("[keep] 「${r.text.trim()}」 — 하고 싶은 게 없다 → 비워 둔다. 다시 묻지 않는다")
            say("그렇구나!")
            pause(700)
            return
        }
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
        sayAck(r.text, step.slot)
        return
    }
}

// ── D3 · 서버 판정 (#39 ① · #34) ────────────────────────────────


/** 다 그린 뒤 묻는 칸의 판정 슬롯 — 「내일」은 12칸에 없어 `extra` 로 묻는다 */
private fun judgeSlotOf(key: String) = if (key == "keep") "extra" else key

/** 아직 빈 첫 질문 (판정 슬롯 · 질문 · 책 키) — 서버가 다음 질문을 못 줄 때의 차례 */
private fun Director.firstEmptyQuestion(skip: Set<String> = emptySet()): Triple<String, String, String>? =
    PICTURE_QUESTIONS.firstOrNull { s.slots[it.key].isNullOrBlank() && it.key !in skip }
        ?.let { Triple(judgeSlotOf(it.key), it.ask(s), it.key) }

/**
 * 판정이 `story_ready` 를 줘도 「내일」이 비었으면 그것만 묻는다 — 이야기 칸이 다 찼다는 뜻이지 일기의 맺음까지 들었다는 뜻이 아니다.
 * 판정은 결말 답에서 ready · 다음 칸 없음을 준다(`eval/results.md` 10-02) — 그대로 멈추면 「내일」이 빠진다
 */
private fun Director.tomorrowQuestion(skip: Set<String>): Triple<String, String, String>? =
    firstEmptyQuestion(skip + PICTURE_QUESTIONS.map { it.key }.filter { it != "keep" })

/**
 * 한 칸을 비워 두고 넘어갈 때 다음 질문 — 판정이 이미 `story_ready` 를 줬으면 「내일」만 남는다.
 * 그 뒤에 빈 「누구랑」(#220 ③)까지 다시 묻지 않는다 — 이야기가 다 모였다는 판정을 따른다
 */
private fun Director.afterGivingUp(skip: Set<String>): Triple<String, String, String>? =
    if (s.endReason == "story_ready") tomorrowQuestion(skip) else firstEmptyQuestion(skip)

/** 판정 슬롯 → 책 키. 그림일기 쪽이 있는 칸만 책에 들어가고, 나머지는 칸에만 남는다 */
/**
 * 판정이 다음에 물을 칸으로 고른 [slot] 의 답이 들어갈 책 키 — 일기에서 `extra` 를 물으면 「내일 또 하고 싶은 거」라 `keep` 에 넣는다.
 * 비었나 볼 때도 같은 키로 본다 — 전에는 `extra` 를 봐서 이미 답한 「내일」을 또 물었다 (#64-3)
 */
internal fun askedKeyOf(slot: String): String = if (slot == "extra") "keep" else slot

/**
 * 판정이 채운 칸을 **아이 출처로** 넣는다 — 첫 칸의 책 문장은 아이 말 그대로(판정의 요약이 아니다 · 차별점 「오늘 아이가 한 말 그대로」).
 * 한 말이 두 칸을 채우면 둘째 칸은 책에 다시 보내지 않는다([DiaryDay.sameSaying]). D3 · D1 이야기 질문이 같이 쓴다
 */
private fun Director.fillFromVerdict(day: DiaryDay, v: Server.Verdict, r: Reply.Spoke, askedKey: String) {
    v.fills.forEachIndexed { i, (fillSlot, value) ->
        if (fillSlot !in Server.SLOTS || value.isBlank()) return@forEachIndexed
        val line = if (i == 0) r.text.trim() else value.trim()
        val bookKey = bookKeyOf(fillSlot, askedKey)
        setDiarySlot(fillSlot, bookKey, value.trim(), line, "child")
        if (i > 0) day.sameSaying += bookKey else day.sameSaying -= bookKey
    }
}

/** 오또의 받아 주기 — 서버 대사(받아주기 + 되돌려주기)가 있으면 그것, 없고 칸이 찼으면 아이 말을 되받는다 */
private suspend fun Director.sayReaction(result: Server.TurnResult, r: Reply.Spoke) {
    val reaction = listOfNotNull(result.line?.ack?.takeIf(String::isNotBlank), result.line?.expand?.takeIf(String::isNotBlank)).joinToString(" ")
    if (reaction.isNotBlank()) { say(reaction); pause(600) }
    else if (result.verdict?.fills?.isNotEmpty() == true) sayAck(r.text, result.verdict!!.fills.first().first)
}

/**
 * 오또의 받아 주기(서버 대사가 없을 때) — 아이 문장을 통째로 되받지 않고 **낱말 하나**를(「놀이터구나!」),
 * 그 낱말이 주어면 「그랬구나!」. 「몰라」 · 「응」은 되받지 않고, 바로 앞과 같은 말은 하지 않는다 (협업 coopAck · 10-05 진웅)
 */
private suspend fun Director.sayAck(text: String, slot: String) {
    val role = when (slot) { "place" -> CoopRole.PLACE; "companion", "newcomer" -> CoopRole.WHO; "problem" -> CoopRole.THING; else -> CoopRole.ANY }
    // 곳은 「놀이터 갔어」처럼 「에」 없이 말하기 쉬워 일기 쪽에서 먼저 떼어 준다 → 「놀이터구나!」
    val heard = if (slot == "place") diaryPlaceWord(text) ?: text else text
    val ack = coopAck(heard, role, CoopReason.DONE, last = s.diaryDay.lastAck) ?: return
    s.diaryDay.lastAck = ack
    say(ack)
    pause(600)
}

/**
 * 판정이 고른 다음 질문 — (판정 슬롯 · 질문 · 책 키). 아직 빈 칸이고 서버가 질문을 썼을 때만.
 * 서버 질문은 앞 말을 받아 「놀이터에서 무슨 일이 있었어?」처럼 맥락을 담는다 — 그림 질문이 사이에 끼어도 알아듣게
 */
private fun Director.serverNext(result: Server.TurnResult): Triple<String, String, String>? {
    val slot = result.verdict?.nextSlot?.takeIf { it in Server.SLOTS && s.slots[askedKeyOf(it)].isNullOrBlank() } ?: return null
    val raw = result.line?.question?.takeIf(String::isNotBlank) ?: return null
    // 말하기 전 갈무리 — 질문 하나 · 쉬운 말 · 시제(「내일」은 앞일, 나머지는 지난 일) · 길이. 걸리면 앱 질문으로 (협업 coopGuard · 10-05 진웅)
    val g = coopGuard(raw, if (slot == "extra") CoopReason.SOON else CoopReason.DONE, CoopSource.LLM)
    // 걸리면 판정이 고른 **그 칸**의 앱 질문으로 — 질문 순서는 판정이 정한다(차별점 2). 앱 질문이 없는 칸이면 다음 차례로
    val question = g.text ?: PICTURE_QUESTIONS.firstOrNull { it.key == askedKeyOf(slot) }?.ask?.invoke(s)?.also {
        log("판정 질문 「$raw」 — 갈무리에 걸려 같은 칸의 앱 질문 「$it」으로 (${g.issues.joinToString(" · ")})")
    } ?: run { log("판정 질문 「$raw」 — 갈무리에 걸리고 앱 질문도 없어 다음 차례로 (${g.issues.joinToString(" · ")})"); return null }
    log("판정이 다음 칸을 골랐다 → [$slot] 「$question」" + if (g.changed) " (갈무리: ${g.issues.joinToString(" · ")})" else "")
    return Triple(slot, question, askedKeyOf(slot))
}

/**
 * 판정이 고른 칸을 그림 실마리로 물을 수 있으면 그 틀로 — 칸은 그대로, 문구만(차별점 2). 실마리가 없으면 [q] 그대로
 */
private fun Director.withClue(day: DiaryDay, q: Triple<String, String, String>): Triple<String, String, String> =
    s.clueQuestion(day, q.third)?.let { (_, text, _) ->
        log("그림 실마리 → [${q.third}] 「$text」 (원래 「${q.second}」)")
        Triple(q.first, text, q.third)
    } ?: q

private fun bookKeyOf(slot: String, askedKey: String): String = when (slot) {
    "extra" -> if (askedKey == "keep") "keep" else "extra"
    else -> slot
}

/**
 * 서버 판정으로 묻는다 — 차별점 2(질문 순서가 고정이 아니다).
 *
 * 한 턴: 묻기 → 아이 말 → `/turn`(diary) → 판정이 채운 칸을 **아이 출처로** 넣는다 → 오또가 받아 주고
 * → 판정이 고른 칸을 판정이 쓴 질문으로 묻는다. 수준은 앱 규칙이 계산한다(규칙 4 — 서버는 신호만).
 * 서버가 실패하면 그 턴은 아이 말을 물은 칸에 그대로 넣고 대본 차례로 간다 — 멈추지 않는다.
 */
private suspend fun Director.askEmptySlotsLive() {
    val day = s.diaryDay
    // 그리는 중에 서버가 골라 둔 다음 이야기가 있으면 그것부터 — 아이가 이어서 들은 맥락 그대로
    var next = (day.nextStory?.takeIf { s.slots[it.third].isNullOrBlank() } ?: firstEmptyQuestion())?.let { withClue(day, it) }
    day.nextStory = null
    var asked = 0
    var wrapOffered = false
    val gaveUp = mutableSetOf<String>()          // [SLOT_TRIES] 번 물어도 못 채운 칸 — 다시 묻지 않는다
    val tries = mutableMapOf<String, Int>()     // 칸마다 물은 수 — 전체 상한 없이 칸마다만 막는다 (#89)
    var easyTried: String? = null
    var pieceAsked = false
    while (next != null) {
        if ((tries[next.third] ?: 0) >= SLOT_TRIES) {
            gaveUp += next.third
            log("[${next.third}] ${SLOT_TRIES}번 물었다 → 비워 둔다")
            next = afterGivingUp(gaveUp) ?: break
            continue
        }
        if (!wrapOffered && diaryRanLong()) {
            wrapOffered = true
            if (offerWrapUp()) break
        }
        val (slot, text, key) = next
        // 필수 칸 질문이 아닌 차례가 오면 — 이름 없는 조각 하나를 먼저 묻는다(10-02)
        if (!pieceAsked && key !in PICTURE_REQUIRED) {
            pieceAsked = true
            val unnamed = firstUnnamedPiece()
            if (unnamed != null) { askPieceOnD3(unnamed); continue }
        }
        val step = DIARY_STEPS.firstOrNull { it.bookKey == key } ?: DIARY_STEPS.firstOrNull { it.slot == slot }
        tries[key] = (tries[key] ?: 0) + 1
        asked++
        s.stepsDone++
        val q = Question(
            text = text,
            kind = step?.kind ?: Kind.EASY,
            noCards = true,
            spoken = step?.answers(s).orEmpty(),
            extra = listOf(DemoBtn("⏱ (시연) 30분이 지난 것으로") { s.diaryTimeUp = true; send(Reply.Silent) }),
            id = "diary_$key",
        )
        val r = ask(q)
        if (r !is Reply.Spoke || r.text.isBlank()) {
            judge(step?.variant, r, q.text)
            next = afterGivingUp(gaveUp + key)
            continue
        }
        // 「내일」에 「없어」 — 판정에 보내면 「없어」가 내일 칸에 들어간다. 비워 두고 다시 묻지 않는다 (10-05 실기기)
        if (key == "keep" && saysNothing(r.text)) {
            judge(step?.variant, r, q.text)
            log("[keep] 「${r.text.trim()}」 — 하고 싶은 게 없다 → 비워 둔다. 다시 묻지 않는다")
            say("그렇구나!")
            pause(700)
            gaveUp += key
            next = firstEmptyQuestion(gaveUp)?.let { withClue(day, it) }
            continue
        }
        day.turnCalls++                            // #30 — 세기만 한다
        // 요청 함수를 넘기지 않는다 — 넘기면 Kotlin IR 백엔드가 StoryTurn.kt 에서 죽는다(AddContinuationLowering · 10-01)
        val result = s.exchangeTurn("diary", slot, text, r.text)
        val v = result?.verdict
        if (v == null) {
            log("판정 서버가 답하지 않았다 → 이 턴은 아이 말을 물은 칸에 그대로 넣고 대본 차례로")
            judge(step?.variant, r, q.text)
            if (!dontKnow(r.text)) {
                setDiarySlot(slot, key, r.text.trim(), r.text.trim(), "child")
                sayAck(r.text, slot)
            }
            next = firstEmptyQuestion(gaveUp)
            continue
        }
        if (v.reason == "blocked_by_filter") {
            log("서버 안전 판정 — 이 답은 책 재료에서 뺀다 · 다른 이야기로")
            next = Triple(slot, "다른 이야기도 들려줄래?", key)
            continue
        }
        // 수준 신호는 판정에서, 수준 계산은 앱 규칙에서 (규칙 4)
        judge(step?.variant, r.copy(answer = Answer(
            text = r.text,
            reason = v.s1Reason,
            el = if (v.s2Addition) setOf("추가") else emptySet(),
            emo = v.emotion.orEmpty(),
        )), q.text)
        fillFromVerdict(day, v, r, key)
        if (v.fills.isEmpty()) {
            if (easyTried != key && step != null) {
                easyTried = key
                val easy = PICTURE_QUESTIONS.firstOrNull { it.key == key }?.easy
                if (easy != null) { log("[$key] 채운 칸이 없다 → 한 번만 쉽게 바꿔 묻는다"); next = Triple(slot, easy, key); continue }
            }
            gaveUp += key
            // 필수 칸(어디 · 무슨 일)에 아이가 **진짜로 답했는데** 판정이 두 번 못 받았으면 아이 말 그대로 — 버리면 아이가 한 말이 사라진다.
            // 지어내는 것이 아니라 아이 출처다(규칙 5 · 협업 keepChildAnswer 와 같다 · 10-05 진웅). 「몰라」 · 「응」은 넣지 않는다
            if (key in PICTURE_REQUIRED && !dontKnow(r.text) && !isNonAnswer(r.text.trim())) {
                setDiarySlot(slot, key, r.text.trim(), r.text.trim(), "child")
                quote(r.text)
                sayAck(r.text, slot)
                log("[$key] 판정은 두 번 못 받았지만 아이가 한 말 「${r.text.trim()}」을 그대로 넣는다 (child)")
            } else log("[$key] 또 못 채웠다 → 비워 둔다. 마스코트가 대신 채우지 않는다")
        }
        v.noLongerNeeded?.let { log("판정 — 「$it」 칸은 더 묻지 않아도 된다") }
        if (v.storyReady && s.endReason == null) {
            s.endReason = "story_ready"
            log("판정 story_ready — 남은 물음은 판정이 고른 칸과 「내일」만")
        }
        sayReaction(result, r)
        next = (serverNext(result) ?: if (v.storyReady) tomorrowQuestion(gaveUp) else firstEmptyQuestion(gaveUp))?.let { withClue(day, it) }
    }
    if (s.endReason == null && PICTURE_REQUIRED.all { !s.slots[it].isNullOrBlank() }) s.endReason = "story_ready"
    log("다 그린 뒤 ${asked}번 물었다 · /turn ${day.turnCalls}번 (#30 — 세기만)")
}

/** 그림 쪽에 들어갈, 아직 이름 없는 조각 — 선이 있는 첫 조각 */
private fun Director.firstUnnamedPiece(): DiaryPiece? =
    s.diaryDay.pieces.firstOrNull { it.name == null && it.strokes.isNotEmpty() }

/** D3 — 「이건 뭐 그린 거야?」 그 조각만 꽂아 보여 주고 묻는다. 「몰라」면 이름 없이 책에 싣는다(그림은 아이 것이다) */
private suspend fun Director.askPieceOnD3(piece: DiaryPiece) {
    val day = s.diaryDay
    day.focusPiece = piece.id
    try {
        val r = ask(Question(text = "이건 뭐 그린 거야?", kind = Kind.EASY, noCards = true, spoken = PIECE_ANSWERS, id = "diary_piece_after"))
        val name = (r as? Reply.Spoke)?.let { nameThePiece(day, piece, it) }
        if (name == null) log("다 그린 뒤 조각 이름을 못 들었다 → 이름 없이 책에 싣는다")
    } finally {
        day.focusPiece = null
    }
}

/**
 * 오또가 아이 말을 「너」로 되받아 준다 — 「나는 놀이터 갔어」 → 「너는 놀이터 갔구나!」 (프로토타입 echoBack).
 * 서버가 붙으면 판정의 받아 주기(ack)가 이 일을 하고, 이것은 대본 · 서버 실패 때만 쓴다.
 */
internal fun echoBack(raw: String): String {
    var t = raw.trim().trimEnd('.', '!', '?', '~').trim()
    t = Regex("""(^|\s)나(는|도|랑|를|의)?(?=\s|$)""").replace(t) { m -> "${m.groupValues[1]}너${m.groupValues[2]}" }
    t = Regex("""(^|\s)내가(?=\s|$)""").replace(t) { m -> "${m.groupValues[1]}네가" }
    t = Regex("""(^|\s)내(?=\s)""").replace(t) { m -> "${m.groupValues[1]}네" }
    if (Regex("(았|었|였|했|갔|왔|졌|봤|났|탔|샀|웠)어$").containsMatchIn(t)) return t.dropLast(1) + "구나!"
    if (Regex("(이야|야)$").containsMatchIn(t)) {
        val base = t.replace(Regex("(이야|야)$"), "")
        return "$base${if (bat(base)) "이" else ""}구나!"
    }
    return "$t, 그랬구나!"
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

/**
 * 아이가 말한 곳의 낱말 — 「놀이터 갔어」 · 「할머니 집에 다녀왔어」 → 놀이터 · 할머니 집. 못 떼면 null.
 * 협업의 [coopNameFrom] 을 쓰고, 일기 답에 흔한 「○○ 갔어」(「에」 없이)를 한 번 더 본다
 */
internal fun diaryPlaceWord(raw: String?): String? {
    val t = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    coopNameFrom(t, CoopRole.PLACE)?.let { return it }
    val m = Regex("^(.+?)\\s*(에서|에)?\\s+(갔|다녀왔|놀았|있었|왔)").find(t) ?: return null
    return coopNameFrom(m.groupValues[1], CoopRole.PLACE)
}

/**
 * 「거기서 …」 대신 아이가 말한 곳으로 — 「놀이터에서 무슨 일이 있었어?」 (협업 이어 받기와 같은 틀 · 10-05 진웅).
 * 곳을 못 떼거나 질문 갈무리([coopGuard])에 걸리면 「거기서 …」 그대로
 */
internal fun atPlace(s: DemoState, rest: String): String =
    diaryPlaceWord(s.slots["place"])
        ?.let { coopFill("{place:에서} $rest", mapOf("place" to it)) }
        ?.let { coopGuard(it, CoopReason.DONE, CoopSource.HEARD).text }
        ?: "거기서 $rest"

/** 필수 칸 — `DemoState.reqSlots`(일기 = place · problem, #29)와 같은 둘. 여기는 책 문장 키로 본다 */
internal val PICTURE_REQUIRED = listOf("place", "problem")

/** 다 그린 뒤 묻는 칸 — 이 차례로, 빈 것만 */
internal val PICTURE_QUESTIONS = listOf(
    PictureQuestion("place", { "오늘 어디 갔었어?" }, "아침 먹고 어디 갔어?"),
    // 누구랑 — 그리는 중 사람 조각 이야기나 다른 답에서 이미 나왔으면 찬 칸이라 묻지 않는다 (#220 ③)
    PictureQuestion("companion", { "누구랑 같이 있었어?" }, "혼자 있었어, 아니면 같이 있었어?"),
    PictureQuestion("problem", { if (it.slots["place"].isNullOrBlank()) "오늘 무슨 일이 있었어?" else atPlace(it, "무슨 일이 있었어?") }, "거기서 뭐 했어?"),
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
    pause(900)
    s.stage = DiaryStitch
    say("그림일기를 만들고 있어. 조금만 기다려 줘!")
    if (Server.liveFor(s.mode)) writeDiaryBook(day) else pause(1500)
    day.weatherFromDrawing()
    s.title = s.slots["title"]?.takeIf { it.isNotBlank() } ?: dateTitle()
    event("book", "template" to "그림일기", "pages" to pages.size, "title" to s.title)
    log("그림일기 ${pages.size}쪽 — ${pages.joinToString(" · ") { it.kind.name.lowercase() }} · 날씨 ${day.weather?.label ?: "아이가 고른다"}")

    // D5
    readPictureDiary(day)

    // D6
    giveDiaryBook()
}

/** 책 문장을 부르는 곳 — 테스트가 서버 없이 바꿔 끼운다. (가린 칸 · 출처 · 맺음 원문 · 쪽 구성) → 쪽 문장 */
internal var requestDiaryStory: suspend (Map<String, String?>, Map<String, String>, String?, List<Server.Page>?) -> List<String>? = { slots, by, keep, pages ->
    Server.story("diary", slots, by, keep = keep, pages = pages)
}

/**
 * 책을 기다리는 한도 — 꿰매는 화면이라 아이는 기다리지만, 넘으면 앱 문장으로 간다.
 * 다른 모드의 `/story` 와 같은 60초 — 서버 상한(55초)보다 길어야 서버가 먼저 답한다 (`guidelines/3` 시간 표 · 10-02)
 */
internal const val DIARY_STORY_WAIT_MS = 60_000L

/**
 * D4 — `/story`(diary)로 책 문장을 받는다. 이름은 가려서 보내고 받은 문장에서 푼다(규칙 6).
 * 실패 · 너무 늦음 · 빈 답이면 앱 문장으로 짠 책 그대로 — 멈추지 않는다.
 * ⚠️ 서버는 일기를 3~6쪽만 받는다 — 칸이 적은 날은 서버가 버리고 앱 문장이 된다(#39)
 */
private suspend fun Director.writeDiaryBook(day: DiaryDay) {
    val mask = s.nameMask()
    // 한 말의 둘째 칸은 빼고 보낸다 — 첫 칸에 아이 말 그대로 있다([DiaryDay.sameSaying])
    val slots = mask.maskSlots(Server.SLOTS.associateWith { if (it in day.sameSaying) null else s.slots[it] })
    val keep = s.slots["keep"]?.takeIf(String::isNotBlank)?.let(mask::mask)
    // 쪽 구성은 앱이 정한다(#220 ③) — 서버는 이 차례 · 이 수대로 쓰고, 앱은 쪽 종류를 짐작하지 않는다
    val plan = diaryPagePlan(listOf("place", "companion", "problem", "reaction", "cause", "solution", "keep", "extra")
        .associateWith { k -> if (k in day.sameSaying) null else s.slots[k] }).takeIf(List<DiaryPlanPage>::isNotEmpty)
    val t0 = System.currentTimeMillis()
    // extra 는 쪽을 받은 말만 보낸다 — RUB 쪽마다 차례대로 하나씩 쓰므로(프롬프트 규칙 10) 쪽과 말의 짝이 어긋나지 않게
    val sent = if (plan == null) slots else slots + ("extra" to plan.mapNotNull { it.item }.joinToString(" / ").takeIf(String::isNotEmpty)?.let(mask::mask))
    val written = withTimeoutOrNull(DIARY_STORY_WAIT_MS) { requestDiaryStory(sent, s.slotBy.toMap(), keep, plan?.map { Server.Page(it.serverKind) }) }
        ?.map(mask::unmask)?.filter(String::isNotBlank)
    val ms = System.currentTimeMillis() - t0
    if (written.isNullOrEmpty()) {
        log("책 문장 서버가 답하지 않았다(${ms}ms) → 앱 문장으로 짠 책")
        event("story_request", "mode" to "diary", "result" to "template")
        return
    }
    day.written = written
    day.writtenPlan = plan?.takeIf { it.size == written.size }
    event("story_request", "mode" to "diary", "result" to "generated", "pages" to written.size)
    log("책 문장 ${written.size}쪽 — /story diary (${ms}ms)")
}

/**
 * D6 — 오늘 그림일기를 책으로 준다. 표지는 아이 그림이다. [책장에 꽂기] → 책장.
 * 동화 모드의 선물(해결 방법 도감 · 무지개 크레용)은 주지 않는다 — 일기는 오늘 한 일이 곧 선물이다
 */
private suspend fun Director.giveDiaryBook() {
    s.stage = DiaryGift
    say("오늘 그림일기가 완성됐어! 책장에 꽂아 줄래?")
    buttons(DemoBtn("📚 책장에 꽂기") { send(Reply.Tapped("shelf", "책장")) })
    awaitValue("shelf")
    buttons()
    mark("end")
    val pages = buildDiaryBook(s.diaryBookInput()).size
    val title = s.title ?: s.autoTitleFor()
    // 표지는 오늘 그린 그림 — 조각이 없으면 화이트보드 한 덩어리
    val coverPieces = s.diaryDay.pieces.toList().ifEmpty { if (s.sceneDrawing.isEmpty()) emptyList() else listOf(DiaryPiece(0, s.sceneDrawing.toList())) }
    // 폰 안에 저장한다(#37) — 앱을 꺼도 책장에 남고 「다시 읽기」로 연다. 서버에는 보내지 않는다
    val book = s.completedDiaryBook(title)
    val shelved = when {
        !DiaryShelf.attached(s) -> {
            log("책장에 꽂기 → 저장소가 없다 · 앱을 켜 둔 동안만 남는다")
            ShelfBook(title, s.themeKey, diaryPlaceBg(s.slots["place"]), pages = pages, fresh = true)
        }
        book != null && DiaryShelf.save(s, book) -> {
            log("책장에 꽂기 → 폰 안에 저장(diary_books · 그림 ${book.pieces.size}조각) · 서버에는 보내지 않음")
            keepPageVoices(book)
            book.onShelf(fresh = true)
        }
        // 저장에 실패한 책은 저장된 것처럼 꽂지 않는다 — 동화와 같은 원칙(`Director.saveFinishedStory`)
        else -> { log("책장에 꽂기 → 저장 실패 · 책장에 꽂지 않는다"); null }
    }
    if (shelved != null) {
        // 표지는 책마다 — 제목이 같은 날 두 권이 한 표지를 나눠 쓰지 않게 책 id 로 단다 (#64-2)
        if (coverPieces.isNotEmpty()) s.diaryCovers[shelved.coverKey()] = DiaryCover(coverPieces, s.drawingAspect)
        s.shelf.add(0, shelved)
        rewardsAfterShelving()
        SessionReports.keep(s, shelved.savedStoryId.orEmpty())
    }
    // 실제로 걸린 분 — 고정값이 아니다 (#64-4)
    val minutes = if (s.diaryStart > 0L) ((System.currentTimeMillis() - s.diaryStart) / 60_000L).coerceAtLeast(1L) else null
    event("session_end", "duration" to (minutes?.let { "${it}분" } ?: "모름"), "counted" to s.quotes.size, "total" to (s.quotes.size + 1))
    // 서버 호출 수 — 동화의 선물 화면(`sceneEnd`)과 같은 줄. 전에는 여기서 안 남겨 다 끝낸 일기가 다음 세션 시작 때 unfinished 로 찍혔다 (#172)
    com.example.finalproject_demo.net.Server.callSummary().takeIf { it.isNotEmpty() }?.let {
        log("서버 호출 (이번 세션) · $it")
        com.example.finalproject_demo.net.Trace.line("calls", "diary finished · $it")
        com.example.finalproject_demo.net.Server.resetCalls()
    }
    go(Scene.SHELF)
}

/**
 * 책장에서 그림일기를 다시 연다(#37) — [shelfId] 가 그림일기면 그 책을 펼쳐 끝까지 읽고 참. 아니면 false(동화 등).
 * 읽는 동안만 그 책의 조각 · 문장 · 날씨로 바꿨다가 되돌린다 — 제목은 다시 묻지 않는다
 */
suspend fun Director.openSavedDiary(shelfId: String): Boolean {
    if (!shelfId.startsWith(DIARY_SHELF_ID)) return false
    val book = DiaryShelf.book(s, shelfId) ?: return false
    log("책장 → 그림일기 『${book.title}』 다시 읽기 (${book.madeAt})")
    s.withSavedDiary(book) { day -> readPictureDiary(day, reread = true, bookId = book.id) }
    say("우리가 만든 책들이야!")                        // 책장으로 돌아온다 — 마지막 쪽 문장을 말풍선에 남기지 않는다
    return true
}

/** 오또가 읽는 그 쪽의 문장 — 처음 읽을 때와 책장에서 다시 읽을 때 같은 글이라 목소리를 다시 쓴다(#179) */
internal fun diaryPageCaption(p: DiaryPage): String =
    if (p.kind == DiaryPageKind.PUZZLE) "내 그림을 맞춰 볼까? 조각을 끌어다 제자리에 놓아 봐!"
    else listOfNotNull(p.text, p.tail, p.closing ?: if (p.asksFeel) "$FEEL_LEAD …" else null).joinToString(" ")

/**
 * 책장에 꽂는 책 옆에, 이번 세션에 서버에서 받은 쪽 목소리를 남긴다(#179 · 폰 안 · 오또 목소리).
 * 다시 읽을 때 `/tts` 를 다시 부르지 않는다 — 10-06 실기기: 7쪽 일기 한 번 다시 읽기에 `/tts` 7번 · 약 26원
 */
private fun Director.keepPageVoices(book: SavedDiaryBook) {
    val kept = buildDiaryBook(book.input).map(::diaryPageCaption).distinct()
        .count { line -> voiceOf(line)?.also { DiaryShelf.keepVoice(s, book.id, line, it) } != null }
    if (kept > 0) log("쪽 목소리 ${kept}줄을 책 옆에 남김 — 다시 읽을 때 /tts 를 부르지 않는다 (#179)")
}

/**
 * 한 쪽씩 넘긴다. 마지막 줄이 비었으면(오늘 기분을 말하지 않았다) 얼굴을 눌러 채운다.
 * 책장에서 다시 읽으면([bookId]) 책 옆에 남긴 목소리를 틀고, 없던 쪽은 이번에 받은 목소리를 채워 둔다(#179)
 */
private suspend fun Director.readPictureDiary(day: DiaryDay, reread: Boolean = false, bookId: String? = null) {
    var i = 0
    val missing = mutableSetOf<String>()
    while (true) {
        val pages = buildDiaryBook(s.diaryBookInput())
        val p = pages[i]
        val last = i == pages.lastIndex
        val caption = diaryPageCaption(p)
        s.stage = DiaryPaper(i)
        if (bookId != null) DiaryShelf.voice(s, bookId, caption)?.let { offerVoice(caption, it) } ?: missing.add(caption)
        say(caption)
        val b = mutableListOf<DemoBtn>()
        if (i == 0 && day.weather == null) DiaryWeather.entries.forEach { w ->
            b += DemoBtn("${w.emoji} 날씨 ${w.label}") { send(Reply.Tapped("wx:${w.name}", w.label)) }
        }
        if (p.asksFeel) DiaryFeel.entries.forEach { f ->
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
            r.value == "title" -> if (!reread) askTitle()
            r.value == "prev" -> i = (i - 1).coerceAtLeast(0)
            r.value == "next" -> if (!last) i++ else {
                // 다 읽고 나면 제목을 한 번 묻는다 — 내용을 다 본 뒤라 아이가 붙이기 쉽다. 이미 붙였으면 묻지 않는다 (10-01 안 2)
                if (!reread && s.slotBy["title"] != "child") askTitle()     // 책장에서 다시 읽을 때는 묻지 않는다
                if (bookId != null) missing.forEach { line -> voiceOf(line)?.let { DiaryShelf.keepVoice(s, bookId, line, it) } }
                return
            }
        }
    }
}

/**
 * 아이가 제목을 말하기 전의 제목 — 날짜뿐(「10월 2일 그림일기」). 아이가 하지 않은 말을 제목에 넣지 않는다:
 * 전의 「{장소}에서 만난 {친구}」(공용 `diaryTitle`)는 같이 놀러 간 친구도 「만난」이 됐다. 책장 표지가 제목으로 찾으니 날마다 다르다
 */
internal fun dateTitle(today: java.time.LocalDate = java.time.LocalDate.now()) = "${today.monthValue}월 ${today.dayOfMonth}일 그림일기"

/**
 * 제목을 눌렀다 — 「이 일기 제목은 뭐로 할까?」 아이가 말한 그대로 제목 칸(`title` · 아이 출처)에 넣는다.
 * 말이 없거나 「몰라」면 지금 제목 그대로
 */
private suspend fun Director.askTitle() {
    val names = s.diaryDay.pieceNames
    val q = Question(
        text = "이 일기 제목은 뭐로 할까?",
        kind = Kind.EASY,
        noCards = true,
        spoken = names.take(2).map { Answer("$it 일기", lv = 1) } + Answer("신나는 하루", lv = 1),
        id = "diary_title",
    )
    val r = ask(q)
    val t = (r as? Reply.Spoke)?.text?.trim()?.trimEnd('.', '!', '?', '~')?.trim()
    if (t.isNullOrEmpty() || dontKnow(t)) { log("제목을 못 들었다 → 지금 제목 그대로"); return }
    s.slots["title"] = t
    s.slotBy["title"] = "child"
    s.title = t
    quote(r.text)
    event("slot_filled", "slot" to "title", "value" to t, "source" to "child")
    log("제목 「$t」 — 아이 말 그대로 (title · child)")
    say("『$t』! 좋은 제목이다!")
    pause(900)
}

// ── 말 → 조각 이름 ──────────────────────────────────────────────

private val DONT_KNOW = Regex("몰라|모르겠|글쎄|음+$")

internal fun dontKnow(text: String) = text.isBlank() || DONT_KNOW.containsMatchIn(text)

/** 말 앞의 군말 — 「음…」 「어 그러니까」 「그냥」. 뒤에 띄어쓰기나 부호가 와야 군말이다(「어린이집」은 아니다) */
private val FILLER = Regex("^(음+|어+|아+|저기|그러니까|그니까|그냥|있잖아|이제)[.…,~! ]+")
/** 이름 뒤에 붙는 끝 — 「~야」 「~에요」 「~요」. 「지」는 넣지 않는다: 「강아지」 「돼지」의 끝이다(10-01 실기기) */
private val ENDING = Regex("(야|에요|예요|요|거든|이지)$")
/** 「집이」의 「이」를 떼지 않는 두 글자 낱말 — 받침 뒤 「이」가 낱말의 일부다 */
private val KEEP_I = setOf("종이", "놀이", "먹이", "팽이", "길이", "높이")
private val DREW = Regex("(을|를)?\\s*(그렸어|그리는 거|그리는 중|그리고 있어|그린 거)$")
/** 이름이 아니라 일 · 기분을 말한 끝 — 「배고파」 「그네 탔어」 「노는 거」. 「그네」 「모래」 「의자」는 이름이라 「네 · 래 · 자」는 넣지 않는다 */
private val PREDICATE = Regex("(았어|었어|였어|했어|갔어|왔어|탔어|봤어|났어|졌어|됐어|싶어|고파|아파|졸려|추워|더워|좋아|싫어|줘|할래|갈래|볼래|하자|가자|는 거|은 거|던 거)$")
private val NOT_A_NAME = Regex("^(응|어|웅|네|예|아니|아니야|그래|좋아|싫어)$")
/** 긴 말에서 마지막 낱말을 꾸미는 말 — 「내가 좋아하는 티라노사우루스」 */
private val ADNOMINAL = Regex("(는|은|던|한|인)$")

private val THIS_IS = Regex("^(이건|이거는|이거|저건|저거|요건|얘는|얘)\\s+")
/** 「더 그린 거야, 새로 그린 거야?」의 답 머리 — 이름이 아니다 */
private val NEW_ONE = Regex("^새로 ?(그렸어요|그렸어|그린 거(야|예요|에요|요)?|그린 것)[.,!~ ]*")
private val ENDS_AS_NAME = Regex("(이야|야|이에요|예요)[.!~ ]*$")

/**
 * 묻지 않았는데 들은 말이 이름처럼 들리나 — 「이건 강아지야」 · 「우리 집이야」. 「배고파」 같은 말로 조각 이름을 덮지 않게
 */
internal fun soundsLikeAName(text: String): Boolean {
    val t = text.trim()
    return THIS_IS.containsMatchIn(t) || (ENDS_AS_NAME.containsMatchIn(t) && t.split(Regex("\\s+")).size <= 3) || saysWhatWasDrawn(t)
}

/** 「조개 그렸어」 · 「조개를 그렸어」 — 무엇을 그렸는지 말했다. 「새로 그렸어」는 이름이 없다 */
private fun saysWhatWasDrawn(text: String): Boolean {
    val t = text.trim().trimEnd('.', '!', '?', '~', '…', ' ')
    return DREW.containsMatchIn(t) && !NEW_ONE.containsMatchIn(t) && t.split(Regex("\\s+")).size <= 4
}
private val NOTHING = Regex("(없어|없어요|없는데|없다|없을걸)[.!~ ]*$|^(음+ )?(아니|아니요|아니야)[.!~ ]*$")

/** 「내일 또 하고 싶은 거 있어?」에 「없어」 · 「아니」 — 하고 싶은 게 없다. 칸을 채우지 않는다 */
internal fun saysNothing(text: String): Boolean = NOTHING.containsMatchIn(text.trim())


/**
 * 「우리 집이야!」 → 「우리 집」 · 「아니, 블록이야」 → 「블록」 · 「이건 강아지야」 → 「강아지」. 대본 답에는 값이 붙어 있어 그대로 쓴다.
 * 「몰라」 · 「응」 · 「배고파」처럼 이름이 아닌 말이면 null — 이름 없이 둔다. 이름은 아이 말에서 떼어 낸 조각뿐이다(규칙 5)
 */
internal fun pieceNameFrom(r: Reply.Spoke): String? {
    r.answer?.value?.takeIf { it.isNotBlank() }?.let { return it }
    var t = r.text.trim()
    if (dontKnow(t)) return null
    while (true) { val next = FILLER.replace(t, "").trim(); if (next == t) break; t = next }
    t = t.trimEnd('.', '!', '?', '~', '…', ' ')
    if (NOT_A_NAME.matches(t)) return null
    t = t.removePrefix("아니,").removePrefix("아니 ").trim()
    t = THIS_IS.replace(t, "").trim()
    t = NEW_ONE.replace(t, "").trim()                           // 「새로 그렸어, 땅이야」 → 「땅이야」
    t = t.substringBefore("인데").trim()                      // 「자동차인데 빨간 거」 → 「자동차」
    t = withoutEnding(t)
    t = DREW.replace(t, "").trim()
    t = withoutRepeat(t)
    if (t.isEmpty() || PREDICATE.containsMatchIn(t)) return null
    val words = t.split(Regex("\\s+"))
    if (t.length <= 12 && words.size <= 4) return t
    // 긴 말 — 「내가 좋아하는 티라노사우루스」처럼 마지막 낱말을 꾸미는 말이면 그 낱말만
    val last = words.last()
    return last.takeIf { words.size >= 2 && ADNOMINAL.containsMatchIn(words[words.size - 2]) && last.length in 2..12 }
}

/**
 * 받아쓰기가 말을 되풀이해 적은 것을 하나로 — 「나무 나무」 → 「나무」 · 「우리 집 우리 집」 → 「우리 집」 (10-02 실기기).
 * 같은 낱말이 잇달아 오거나, 말 전체가 같은 두 토막이면 앞 토막만 둔다. 「엄마 아빠」처럼 다른 낱말은 그대로
 */
internal fun withoutRepeat(t: String): String {
    val w = t.split(Regex("\\s+")).filter(String::isNotEmpty)
    val once = w.filterIndexed { i, x -> i == 0 || x != w[i - 1] }
    if (once.size % 2 == 0 && once.size >= 2 && once.take(once.size / 2) == once.drop(once.size / 2)) return once.take(once.size / 2).joinToString(" ")
    return once.joinToString(" ")
}

/** 「집이야」 → 「집」 · 「고양이에요」 → 「고양이」 · 「강아지요」 → 「강아지」. 끝이 없으면 그대로 */
private fun withoutEnding(t: String): String {
    val m = ENDING.find(t) ?: return t
    val s = t.substring(0, m.range.first).trimEnd()
    if (s.isEmpty()) return ""                                  // 「요」 「야」만 남았다 — 이름이 아니다 (10-02 실기기 「새로 그린 거요」 → 「요」)
    return withoutI(s)
}

/**
 * 「집이」 「블록이」의 「이」는 받침 뒤에 붙은 말끝이다. 「아이」 「종이」는 낱말이라 남기고,
 * 세 글자 넘는 「고양이 · 원숭이 · 달팽이 · 멍멍이」는 ㅇ 받침 뒤 「이」까지가 낱말이라 남긴다
 */
private fun withoutI(s: String): String {
    val word = s.substringAfterLast(' ')
    if (word.length < 2 || !word.endsWith("이") || word in KEEP_I) return s
    val before = word[word.length - 2].toString()
    val ieung = (before[0].code - 0xAC00) % 28 == 21
    return if (bat(before) && (word.length == 2 || !ieung)) s.dropLast(1) else s
}

private val AND = Regex("^(.+)(랑|하고)$")

/**
 * 이어 말한 이름을 낱낱이 — 「해랑 구름」 → [해, 구름] · 「미끄럼틀이랑 해」 → [미끄럼틀, 해] · 「집이랑 나무 그리고 해」 → [집, 나무, 해].
 * 「랑 · 하고」가 뒤에 다른 말이 올 때만 가른다(「해랑」 한 마디는 그대로). 「와 · 과」는 「사과」 같은 낱말 끝과 헷갈려 쓰지 않는다
 */
internal fun namesIn(name: String): List<String> {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val parts = mutableListOf<String>()
    var now = mutableListOf<String>()
    fun close() { now.joinToString(" ").takeIf(String::isNotBlank)?.let(parts::add); now = mutableListOf() }
    words.forEachIndexed { i, w ->
        val and = AND.find(w)?.takeIf { i < words.lastIndex }
        when {
            w == "그리고" -> close()
            and != null -> { now += and.groupValues[1]; now = mutableListOf(withoutI(now.joinToString(" "))); close() }
            else -> now += w
        }
    }
    close()
    return parts
}

/**
 * 한 번에 말한 이름 여럿을 그린 차례대로 — 지금 조각([piece])이 마지막 이름, 그 앞 이름은 바로 앞의 이름 없는 조각들에.
 * (조각 id · 이름) 목록, 나눌 수 없으면 null — 이름이 하나거나, 앞에 이름 없는 조각이 모자라면 한 조각에 함께 그린 것이다(「엄마랑 나」)
 */
internal fun splitAcross(name: String, piece: DiaryPiece, pieces: List<DiaryPiece>): List<Pair<Int, String>>? {
    val names = namesIn(name).takeIf { it.size >= 2 } ?: return null
    val before = pieces.filter { it.id < piece.id && it.name.isNullOrBlank() }.sortedBy { it.id }
    if (before.size < names.size - 1) return null
    return (before.takeLast(names.size - 1).map { it.id } + piece.id).zip(names)
}

private val ME = Regex("^(나|저)(랑|하고|와|도|는|를|의|만)?$")
private val MY = Regex("^(내|제)(가)?$")

/**
 * 오또가 아이의 「나」를 부를 때 — 「나」 → 「너」 · 「엄마랑 나」 → 「엄마랑 너」 · 「내 동생」 → 「네 동생」 (프로토타입 `you()`).
 * 낱말 단위라 「나무」 「나비」는 그대로. 조각 이름(아이 말)은 바꾸지 않고 **대사에만** 쓴다(규칙 5)
 */
internal fun you(name: String): String = name.split(" ").joinToString(" ") { w ->
    ME.matchEntire(w)?.let { "너" + it.groupValues[2] } ?: MY.matchEntire(w)?.let { "네" + it.groupValues[2] } ?: w
}

/**
 * 그림 주문에 보낼 말 — 아이 자신(「나 · 저」)은 「아이」로. 서버 주문은 「나」만 오면 누구인지 몰라 거절한다
 * (`not drawable` · 10-01 도메인 서버로 확인: 「나」 거절 · 「아이」 「엄마랑 나」 「내 동생」은 그림). 조각 이름은 그대로 둔다
 */
internal fun drawWords(name: String): String = name.split(" ").joinToString(" ") { w ->
    ME.matchEntire(w)?.let { "아이" + it.groupValues[2] } ?: w
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
