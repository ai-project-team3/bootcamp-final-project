package com.example.finalproject_demo.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/*
 * 그림일기 책 짜기 (D5 · docs/일기모드_흐름.html 「엔드 픽처」).
 *
 * 쪽은 **칸이 찬 만큼만** 생긴다 — 그림 쪽 · 기(place) · 승(problem) · 전(reaction) · 결(solution) · 맺음(keep).
 * 주어는 「나는」, 과거 -어요체. 빈 칸은 지어내지 않고 「아직 듣지 못했어요」로 비었다고 쓴다.
 *
 * 여기서 만드는 문장은 **서버가 없거나 실패했을 때의 책**이다. 서버가 되면 `/story`(mode: diary)가 쪽마다
 * 문장을 쓰고 이 쪽 목록의 차례 · 수를 따른다. 조각이 쪽마다 어떻게 나오는지([DiaryPage.cast] · [DiaryPage.move])는
 * 서버 문장에도 같이 적용된다.
 */

enum class DiaryPageKind(val slot: String, val bookKey: String) {
    DRAWING("extra", "whiteboard"),
    PLACE("place", "place"),
    PROBLEM("problem", "problem"),
    REACTION("reaction", "reaction"),
    SOLUTION("solution", "solution"),
    KEEP("extra", "keep"),
    /** 🧩 내 그림 맞추기 — 아이가 한 행동을 말한 날, 그림이 있으면 맨 뒤에 (프로토타입 A3 · 09-29 조장) */
    PUZZLE("extra", "puzzle"),
}

/** 문장이 조각을 어떻게 움직이나 — 문장의 낱말에서 읽는다 (동화 책의 [motionFrom] 과 같은 방식) */
enum class PieceMove { TOPPLE, BUILD, DROOP, WALK, HOP, BOB }

/**
 * 그림일기 한 쪽.
 *
 * @param cast 앞으로 나와 움직이는 조각 이름. 나머지 조각은 흐리게 뒤에 남는다
 * @param still 문장이 **장소로** 쓴 조각(「우리 집 앞에」) — 앞으로 나오되 걷지 않는다
 * @param with 「엄마랑 미끄럼틀 탔어」의 엄마처럼 **같이 있던** 조각 — 문장의 움직임 대신 통통 뛴다 (프로토타입 `withWho`)
 * @param closing 마지막 쪽의 오늘 기분 줄. [asksFeel] 이면 아직 안 골랐다 — 「오늘은」까지 써 두고 얼굴을 누르게 한다
 */
data class DiaryPage(
    val kind: DiaryPageKind,
    val text: String,
    val by: String?,
    val cast: List<String>,
    val move: PieceMove,
    val still: Set<String> = emptySet(),
    val with: Set<String> = emptySet(),
    val tail: String? = null,
    val closing: String? = null,
    val asksFeel: Boolean = false,
    /** 쪽 구성의 extra 쪽이면 그 말(「양동이: 물 떠 왔어」 · #220) — 조각 이야기 쪽이면 그 조각으로 확대한다 */
    val item: String? = null,
)

/** 앱이 정한 일기 책 쪽 하나 (#220 ③) — 서버 쪽 종류(`DIARY_KIND_MEANING`) · 앱 쪽 종류 · extra 쪽이면 그 말 */
data class DiaryPlanPage(val serverKind: String, val kind: DiaryPageKind, val item: String? = null)

/**
 * extra 의 말을 다른 칸(또는 앞의 말)이 거의 그대로 했나 — 낱말 앞 두 글자(「아빠랑」→「아빠」 · 「만들었어」→「만들」)의
 * [SAID_ALREADY] 이상이 한 칸에 있으면. 그런 말에 쪽을 주면 모델이 같은 뜻을 피하다 아이가 하지 않은 말로 쪽을 채웠다
 * (10-06 측정: 「아빠: 모래성 같이 만들었어」 + 무슨 일 「아빠랑 모래성 만들었어」 → 「그림 속에 모래성과 양동이가 있었어요」)
 */
internal fun saidAlready(item: String, others: List<String>): Boolean {
    fun stems(t: String) = t.split(Regex("[\\s:,.!?~]+")).filter(String::isNotEmpty).map { it.take(2) }.toSet()
    val mine = stems(item).takeIf(Set<String>::isNotEmpty) ?: return true
    return others.any { o -> stems(o).let { theirs -> mine.count { it in theirs } >= SAID_ALREADY * mine.size } }
}

internal const val SAID_ALREADY = 0.7

/** 그림 · 맞추기 쪽을 뺀 글 쪽 상한 — 옛 설계의 8쪽 (#220) */
internal const val DIARY_PLAN_MAX = 8

/**
 * 일기 책 쪽 구성 — 찬 칸만 이 차례로(#220 ③). [slots] 는 칸 → 아이 말(`keep` · `extra` 포함 · 한 말의 둘째 칸은 뺀 것).
 * 장소+누구랑(DEPART) → 무슨 일(SHAKE) → extra 의 말 하나에 한 쪽(RUB) → 기분+왜를 한 쪽(FAIL) → 어떻게 됐나(DRAG) → 내일(TOGETHER).
 * 기분과 왜를 한 쪽에 두는 것은 같은 말이 두 쪽에 나오던 것을 막으려고다(10-06 「지루했어요」 · 「재미없었어요」).
 * [DIARY_PLAN_MAX] 를 넘으면 extra 의 뒤쪽 말부터 뺀다
 */
fun diaryPagePlan(slots: Map<String, String?>): List<DiaryPlanPage> {
    fun has(k: String) = !slots[k].isNullOrBlank()
    val head = buildList {
        if (has("place") || has("companion")) add(DiaryPlanPage("DEPART", DiaryPageKind.PLACE))
        if (has("problem")) add(DiaryPlanPage("SHAKE", DiaryPageKind.PROBLEM))
    }
    val tail = buildList {
        if (has("reaction") || has("cause")) add(DiaryPlanPage("FAIL", DiaryPageKind.REACTION))
        if (has("solution")) add(DiaryPlanPage("DRAG", DiaryPageKind.SOLUTION))
        if (has("keep")) add(DiaryPlanPage("TOGETHER", DiaryPageKind.KEEP))
    }
    val said = listOf("place", "companion", "problem", "reaction", "cause", "solution", "keep").mapNotNull { slots[it]?.takeIf(String::isNotBlank) }
    val items = slots["extra"].orEmpty().split(" / ").map(String::trim).filter(String::isNotEmpty)
        .fold(listOf<String>()) { kept, it -> if (saidAlready(it, said + kept)) kept else kept + it }
        .take((DIARY_PLAN_MAX - head.size - tail.size).coerceAtLeast(0))
    return head + items.map { DiaryPlanPage("RUB", DiaryPageKind.PROBLEM, it) } + tail
}

/** 책을 짜는 데 필요한 것만 — 칸의 책 문장(아이 말) · 출처 · 조각 이름 · 오늘 기분 */
data class DiaryBookInput(
    /** bookKey → 아이가 한 말(또는 대본의 책 문장). 비어 있으면 그 쪽이 없다 */
    val lines: Map<String, String>,
    val by: Map<String, String> = emptyMap(),
    val pieceNames: List<String> = emptyList(),
    val hasDrawing: Boolean = false,
    val feel: DiaryFeel? = null,
    /** 서버(`/story` diary)가 쓴 쪽 문장 — 있으면 그림 쪽 뒤를 이 쪽들로 짠다. 없으면 앱 문장 */
    val written: List<String>? = null,
    /** 놀이(미션) 쪽을 붙이나 — 앱의 책은 붙이고, 쪽 짜기만 보는 검사는 끈다 */
    val missions: Boolean = false,
    /** 그림을 세 줄로 나눠 줄마다 선이 있나 — 없으면 🧩 조각 하나가 흰 카드다([puzzleStripsAllDrawn]) */
    val puzzle: Boolean = true,
    /** [written] 을 받을 때 보낸 쪽 구성 — 같은 수면 쪽 종류를 짐작하지 않고 이것을 쓴다(#220 ③) */
    val plan: List<DiaryPlanPage>? = null,
)

/** 🧩 퍼즐 조각 수 — 화면(`DiaryViews` PuzzlePanel)도 이 수로 나눈다 */
const val PUZZLE_STRIPS = 3

/**
 * 그림을 퍼즐처럼 세로 [PUZZLE_STRIPS] 줄로 나눴을 때 줄마다 선이 지나가나. 화면과 같은 자르기([cropFor])로 본다.
 * 10-01 실기기: 떨어진 동그라미 둘을 그린 날 가운데 조각이 흰 카드라 무엇을 맞추는지 알 수 없었다
 */
fun puzzleStripsAllDrawn(strokes: List<Stroke>, aspect: Float): Boolean {
    val pts = strokes.flatMap { it.pts }
    if (pts.isEmpty()) return false
    val crop = cropFor(strokes, aspect)
    val w = crop.width / PUZZLE_STRIPS
    return (0 until PUZZLE_STRIPS).all { i -> val l = crop.left + i * w; pts.any { it.x in l..(l + w) } }
}

/** 퍼즐 쪽의 글 — 원고지 대신 놀이 안내가 들어간다 */
const val PUZZLE_TEXT = "놀이 · 내 그림 맞추기"

/** 아이가 한 행동 — 이런 말이 있는 날에만 놀이를 붙인다(지어낸 것 없이 아이 것만 · 프로토타입 ACTION) */
private val ACTION = Regex("(갔|왔|했|놀았|놀고|놀다|탔|먹|쌓|그렸|만들|뛰|넘어|들어|봤|잤|읽|씻|달렸|던졌|찼|걸었|올라|내려|쳤|불었|춤|일어났)")

/** 그림이 있고 아이가 한 행동을 말한 날이면 맨 뒤에 퍼즐 쪽 — 기분 줄은 그 앞 쪽에 남는다 */
private fun withMissions(pages: MutableList<DiaryPage>, input: DiaryBookInput): List<DiaryPage> {
    if (!input.missions || !input.hasDrawing || !input.puzzle) return pages
    // 앱이 지은 문장(「…에 갔어요」)이 아니라 아이가 한 말 원문으로 본다
    val acted = listOf("place", "problem", "solution").any { k -> input.lines[k]?.let(ACTION::containsMatchIn) == true }
    if (!acted) return pages
    pages += DiaryPage(DiaryPageKind.PUZZLE, PUZZLE_TEXT, by = null, cast = input.pieceNames, move = PieceMove.HOP)
    return pages
}

const val NOT_HEARD_AFTER = "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요."
const val NOT_HEARD_THERE = "거기서 있었던 일은 아직 듣지 못했어요."
const val FEEL_LEAD = "오늘은"

fun DemoState.diaryBookInput(): DiaryBookInput = readingDiary ?: DiaryBookInput(
    lines = DiaryPageKind.entries.filter { it.bookKey !in diaryDay.sameSaying }
        .mapNotNull { k -> slots[k.bookKey]?.trim()?.takeIf(String::isNotEmpty)?.let { k.bookKey to it } }.toMap(),
    by = slotBy.toMap(),
    pieceNames = diaryDay.pieceNames,
    hasDrawing = sceneDrawing.isNotEmpty() || diaryDay.pieces.isNotEmpty(),
    feel = diaryDay.feel,
    written = diaryDay.written,
    plan = diaryDay.writtenPlan,
    missions = true,
    puzzle = puzzleStripsAllDrawn(
        diaryDay.pieces.flatMap { it.strokes }.ifEmpty { sceneDrawing.toList() },
        drawingAspect.takeIf { it > 0f } ?: 1f,
    ),
)

/** 그림일기 쪽 목록. 그림도 말도 없으면 빈 목록 — 책 없이 조용히 끝난다(D6) */
fun buildDiaryBook(input: DiaryBookInput): List<DiaryPage> {
    val names = input.pieceNames
    val lines = input.lines
    fun line(k: DiaryPageKind) = lines[k.bookKey]?.trim()?.takeIf(String::isNotEmpty)
    fun by(k: DiaryPageKind) = input.by[k.bookKey]

    fun page(kind: DiaryPageKind, text: String, castAll: Boolean = false) = DiaryPage(
        kind = kind,
        text = text,
        by = by(kind),
        cast = if (castAll) names else names.filter { mentions(text, it) },
        move = moveFrom(text).let { if (it == PieceMove.BOB) kindMove(kind) else it },
        still = names.filter { asPlace(text, it) }.toSet(),
        with = if (castAll) emptySet() else names.filter { Regex(Regex.escape(it) + "(이랑|랑|하고|와|과)").containsMatchIn(text) }.toSet(),
    )

    val pages = mutableListOf<DiaryPage>()
    if (input.hasDrawing) {
        val listed = drawnList(names)
        val text = if (listed.isEmpty()) "내가 오늘 그린 그림이에요." else "나는 오늘 ${listed.joinToString(", ")}${eul(listed.last())} 그렸어요."
        pages += page(DiaryPageKind.DRAWING, text, castAll = true).copy(by = if (names.isEmpty()) null else "child")
    }
    val written = input.written?.map(String::trim)?.filter(String::isNotEmpty)
    if (!written.isNullOrEmpty()) {
        // 서버가 쓴 쪽 — 서버는 「일 · 마음」을 한 쪽에 합쳐 쓰기도 해서 앱의 칸마다 한 쪽과 수가 다를 수 있다.
        // 쪽 종류는 조각 움직임 · 장소 처리에만 쓰이므로 찬 칸의 차례로 어림한다. 빈 결말은 서버가 비었다고 쓴다(규칙 1)
        // 앱이 쪽 구성을 보냈고 그 수대로 왔으면 그 차례가 곧 쪽 종류다(#220 ③). 아니면(옛 책 · 구성 없이 쓴 책) 짐작한다
        val plan = input.plan?.takeIf { it.size == written.size }
        val kinds = plan?.map { it.kind } ?: writtenKinds(STORY_KINDS.filter { line(it) != null }, written.size)
        written.forEachIndexed { i, text -> pages += page(kinds[i], text, castAll = kinds[i] == DiaryPageKind.PLACE).copy(item = plan?.get(i)?.item) }
        closeWithFeel(pages, line(DiaryPageKind.REACTION) != null, input.feel)
        return withMissions(pages, input)
    }
    line(DiaryPageKind.PLACE)?.let { pages += page(DiaryPageKind.PLACE, placeSentence(it), castAll = true) }
    line(DiaryPageKind.PROBLEM)?.let { pages += page(DiaryPageKind.PROBLEM, yoAll(it)) }
    line(DiaryPageKind.REACTION)?.let { pages += page(DiaryPageKind.REACTION, joinWith("그때", yoAll(it))) }
    line(DiaryPageKind.SOLUTION)?.let {
        // 문제가 없던 날에는 해결 말투를 쓰지 않는다 · 마스코트가 메운 결말에는 「마침내」를 붙이지 않는다 (guidelines/7 §5-1)
        val text = if (by(DiaryPageKind.SOLUTION) == "mascot") yoAll(it)
        else joinWith(if (troubled(lines)) "마침내" else "그러고 나서", yoAll(it))
        pages += page(DiaryPageKind.SOLUTION, text)
    }
    line(DiaryPageKind.KEEP)?.let { pages += page(DiaryPageKind.KEEP, yoAll(it)) }

    // 빈 결말은 지어내지 않고 비었다고 쓴다 — 일어난 일 쪽 끝에 붙인다
    if (line(DiaryPageKind.SOLUTION) == null) {
        val at = pages.indexOfLast { it.kind == DiaryPageKind.PROBLEM }.takeIf { it >= 0 }
        val placeAt = pages.indexOfLast { it.kind == DiaryPageKind.PLACE }.takeIf { it >= 0 }
        when {
            at != null -> pages[at] = pages[at].copy(tail = NOT_HEARD_AFTER)
            placeAt != null -> pages[placeAt] = pages[placeAt].copy(tail = NOT_HEARD_THERE)
        }
    }

    closeWithFeel(pages, line(DiaryPageKind.REACTION) != null, input.feel)
    return withMissions(pages, input)
}

/** 그림 쪽 다음에 오는 쪽 종류 — 책의 차례(기 · 승 · 전 · 결 · 맺음) */
private val STORY_KINDS = listOf(DiaryPageKind.PLACE, DiaryPageKind.PROBLEM, DiaryPageKind.REACTION, DiaryPageKind.SOLUTION, DiaryPageKind.KEEP)

/**
 * 서버 쪽 [n] 개에 붙일 종류 — 찬 칸의 차례대로. 서버 쪽이 적으면(합쳐 썼다) 맺음은 끝에 남기고,
 * 많으면 마지막 종류를 잇는다. 찬 칸이 없으면 모두 일(PROBLEM)
 */
internal fun writtenKinds(filled: List<DiaryPageKind>, n: Int): List<DiaryPageKind> {
    if (filled.isEmpty()) return List(n) { DiaryPageKind.PROBLEM }
    if (filled.size >= n) {
        val head = filled.take(n)
        return if (DiaryPageKind.KEEP in filled && DiaryPageKind.KEEP !in head) head.dropLast(1) + DiaryPageKind.KEEP else head
    }
    return filled + List(n - filled.size) { filled.last() }
}

/** 마지막 줄은 오늘 기분 — 마음을 말했으면 그 말이 곧 기분이라 덧붙이지 않는다 */
private fun closeWithFeel(pages: MutableList<DiaryPage>, saidFeeling: Boolean, feel: DiaryFeel?) {
    if (pages.isEmpty() || saidFeeling) return
    pages[pages.lastIndex] = pages.last().copy(closing = feel?.line, asksFeel = feel == null)
}

/** 어긋난 일이 있던 날인가 — 앱 `hadTrouble()` · `/story` 프롬프트 규칙 4 와 같은 낱말 */
internal fun troubled(lines: Map<String, String>): Boolean =
    TROUBLE_WORDS.any { it in lines["problem"].orEmpty() } ||
        TROUBLE_FEELINGS.any { it in lines["reaction"].orEmpty() }

/**
 * 아이 말 → -어요체 한 문장. **서버가 없을 때만 쓰는 어림**이다 — 진짜 문장은 `/story` 가 쓴다.
 * 이미 -요 로 끝나면 그대로, 「거야」는 「거예요」, 「-어 · -아 · -해 …」는 「-요」를 붙인다.
 */
/** 한 칸에 덧붙은 말(「앞 말 / 새 말」 · #220)은 문장마다 「~요」로 */
internal fun yoAll(raw: String): String = raw.split(" / ").map(String::trim).filter(String::isNotEmpty).joinToString(" ") { yo(it) }

internal fun yo(raw: String): String {
    val t = raw.trim().trimEnd('.', '!', '?', '~').trim()
    return when {
        t.isEmpty() -> ""
        t.endsWith("요") -> "$t."
        t.endsWith("거야") -> t.removeSuffix("거야") + "거예요."
        t.last() in "어아해워와지" -> "${t}요."
        else -> "$t."
    }
}

private val DONE_THERE = Regex("(갔|왔|있었|놀았|했)(어|어요)?$")
private val SUBJECT = Regex("^(나는|내가|나)\\s")

/** 장소 한 줄. 「놀이터」면 「나는 오늘 놀이터에 갔어요.」, 이미 문장이면(「어린이집에 갔어요」) 주어만 붙인다 */
internal fun placeSentence(raw: String): String {
    val t = raw.trim().trimEnd('.', '!', '?', '~').trim()
    if (DONE_THERE.containsMatchIn(t)) {
        val body = yo(t)
        return if (SUBJECT.containsMatchIn(body)) body else "나는 오늘 ${body.removePrefix("오늘 ")}"
    }
    return "나는 오늘 ${t.removeSuffix("에서").removeSuffix("에")}에 갔어요."
}

private const val PARTICLES = "은|는|이|가|을|를|도|랑|이랑|하고|와|과|의|에|에서|앞|옆|안|뒤|위|만"

/** 문장이 그 조각을 **낱말로** 부르나 (「나」는 「나무」 · 「하나」 속에서 세지 않는다). 「나」는 「내가」로도 부른다 */
internal fun mentions(text: String, name: String): Boolean {
    val re = Regex("(^|[\\s,])${Regex.escape(name)}($PARTICLES)?(?=[\\s,.!?]|$)")
    return re.containsMatchIn(text) || (name == "나" && Regex("(^|\\s)내가(\\s|$)").containsMatchIn(text))
}

/**
 * 1쪽 「나는 오늘 ○○를 그렸어요」에 늘어놓을 이름 — 다른 이름 안에 낱말로 들어 있는 이름은 뺀다.
 * 「강아지, 우리 집 강아지 뽀삐」 → 「우리 집 강아지 뽀삐」 · 「나, 엄마랑 나」 → 「엄마랑 나」. 조각은 그대로 다 나온다(문장만)
 */
internal fun drawnList(names: List<String>): List<String> =
    names.filter { n -> names.none { m -> m != n && mentions(m, n) } }

/** 문장이 그 조각을 장소로 쓰나 (「우리 집 앞에」 · 「놀이터에서」) — 그 조각은 걷지 않는다 */
internal fun asPlace(text: String, name: String): Boolean =
    Regex("${Regex.escape(name)}\\s*(앞|옆|안|뒤|위|밑)?\\s*(에|에서)(\\s|$|[.,!?])").containsMatchIn(text)

/** 문장에 움직임 말이 없을 때 쪽 종류대로 (#220 ④) — 일어난 일은 들썩, 마음은 통통. 나머지는 살랑 */
internal fun kindMove(kind: DiaryPageKind): PieceMove = when (kind) {
    DiaryPageKind.PROBLEM -> PieceMove.WALK
    DiaryPageKind.REACTION -> PieceMove.HOP
    else -> PieceMove.BOB
}

/**
 * 이 쪽에서 다가갈 조각 (#220 ④) — 빈 목록이면 그림 전체. 쪽마다 같은 그림 전체였던 것을 쪽마다 다른 자리로.
 * 조각 이야기 쪽(「양동이: 물 떠 왔어」)은 그 조각, 다른 쪽은 문장에 나온 조각. 배경은 다가갈 곳이 아니다.
 * 그림 · 장소 · 맞추기 쪽은 늘 전체 — 무엇을 그렸는지 · 어디였는지 보여 주는 쪽이다
 */
fun pageFocus(page: DiaryPage, pieces: List<DiaryPiece>): List<DiaryPiece> {
    if (page.kind == DiaryPageKind.DRAWING || page.kind == DiaryPageKind.PLACE || page.kind == DiaryPageKind.PUZZLE) return emptyList()
    val things = pieces.filter { it.role != PieceRole.BACKGROUND }
    val about = page.item?.substringBefore(":", "")?.trim()?.takeIf(String::isNotEmpty)
    about?.let { name -> things.filter { it.name == name }.takeIf(List<DiaryPiece>::isNotEmpty)?.let { return it } }
    return things.filter { it.name != null && it.name in page.cast }
}

internal fun moveFrom(text: String): PieceMove = when {
    Regex("무너|쓰러|넘어|떨어|와르르").containsMatchIn(text) -> PieceMove.TOPPLE
    Regex("쌓|세웠|만들|지었|고쳤").containsMatchIn(text) -> PieceMove.BUILD
    Regex("울었|속상|슬펐|아팠").containsMatchIn(text) -> PieceMove.DROOP
    Regex("갔어|왔어|걸어|도착").containsMatchIn(text) -> PieceMove.WALK
    Regex("놀았|뛰|달렸|신났|웃었|재밌").containsMatchIn(text) -> PieceMove.HOP
    else -> PieceMove.BOB
}

/**
 * 그림 칸이 보여 줄 판의 자리 — 보통은 그린 부분만. 오또가 다시 그린 배경(#168)이 있으면 그 그림이 판 전체라 판 전체를 —
 * 그린 부분만 자르면 판 전체 그림이 잘린 자리에 맞춰져 위로 밀려 보였다 (10-07 실기기 · 다 그린 뒤 질문 · 그림일기 읽기)
 */
internal fun pictureCrop(pieces: List<DiaryPiece>, aspect: Float, ratio: Float = 3f): BoardBox {
    val ottoBackdrop = pieces.any { it.role == PieceRole.BACKGROUND && it.look == PieceLook.OTTO }
    val frame = if (ottoBackdrop) listOf(Stroke(Color.Transparent, listOf(Offset(0f, 0f), Offset(1f, 1f)))) else pieces.flatMap { it.strokes }
    return cropFor(frame, aspect, ratio, pad = if (ottoBackdrop) 0f else 0.04f)
}
