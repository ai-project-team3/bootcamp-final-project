package com.example.finalproject_demo.demo

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
}

/** 문장이 조각을 어떻게 움직이나 — 문장의 낱말에서 읽는다 (동화 책의 [motionFrom] 과 같은 방식) */
enum class PieceMove { TOPPLE, BUILD, DROOP, WALK, HOP, BOB }

/**
 * 그림일기 한 쪽.
 *
 * @param cast 앞으로 나와 움직이는 조각 이름. 나머지 조각은 흐리게 뒤에 남는다
 * @param still 문장이 **장소로** 쓴 조각(「우리 집 앞에」) — 앞으로 나오되 걷지 않는다
 * @param closing 마지막 쪽의 오늘 기분 줄. [asksFeel] 이면 아직 안 골랐다 — 「오늘은」까지 써 두고 얼굴을 누르게 한다
 */
data class DiaryPage(
    val kind: DiaryPageKind,
    val text: String,
    val by: String?,
    val cast: List<String>,
    val move: PieceMove,
    val still: Set<String> = emptySet(),
    val tail: String? = null,
    val closing: String? = null,
    val asksFeel: Boolean = false,
)

/** 책을 짜는 데 필요한 것만 — 칸의 책 문장(아이 말) · 출처 · 조각 이름 · 오늘 기분 */
data class DiaryBookInput(
    /** bookKey → 아이가 한 말(또는 대본의 책 문장). 비어 있으면 그 쪽이 없다 */
    val lines: Map<String, String>,
    val by: Map<String, String> = emptyMap(),
    val pieceNames: List<String> = emptyList(),
    val hasDrawing: Boolean = false,
    val feel: DiaryFeel? = null,
)

const val NOT_HEARD_AFTER = "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요."
const val NOT_HEARD_THERE = "거기서 있었던 일은 아직 듣지 못했어요."
const val FEEL_LEAD = "오늘은"

fun DemoState.diaryBookInput(): DiaryBookInput = DiaryBookInput(
    lines = DiaryPageKind.entries.mapNotNull { k -> slots[k.bookKey]?.trim()?.takeIf(String::isNotEmpty)?.let { k.bookKey to it } }.toMap(),
    by = slotBy.toMap(),
    pieceNames = diaryDay.pieceNames,
    hasDrawing = sceneDrawing.isNotEmpty() || diaryDay.pieces.isNotEmpty(),
    feel = diaryDay.feel,
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
        move = moveFrom(text),
        still = names.filter { asPlace(text, it) }.toSet(),
    )

    val pages = mutableListOf<DiaryPage>()
    if (input.hasDrawing) {
        val text = if (names.isEmpty()) "내가 오늘 그린 그림이에요." else "나는 오늘 ${names.joinToString(", ")}${eul(names.last())} 그렸어요."
        pages += page(DiaryPageKind.DRAWING, text, castAll = true).copy(by = if (names.isEmpty()) null else "child")
    }
    line(DiaryPageKind.PLACE)?.let { pages += page(DiaryPageKind.PLACE, placeSentence(it), castAll = true) }
    line(DiaryPageKind.PROBLEM)?.let { pages += page(DiaryPageKind.PROBLEM, yo(it)) }
    line(DiaryPageKind.REACTION)?.let { pages += page(DiaryPageKind.REACTION, joinWith("그때", yo(it))) }
    line(DiaryPageKind.SOLUTION)?.let {
        // 문제가 없던 날에는 해결 말투를 쓰지 않는다 · 마스코트가 메운 결말에는 「마침내」를 붙이지 않는다 (guidelines/7 §5-1)
        val text = if (by(DiaryPageKind.SOLUTION) == "mascot") yo(it)
        else joinWith(if (troubled(lines)) "마침내" else "그러고 나서", yo(it))
        pages += page(DiaryPageKind.SOLUTION, text)
    }
    line(DiaryPageKind.KEEP)?.let { pages += page(DiaryPageKind.KEEP, yo(it)) }

    // 빈 결말은 지어내지 않고 비었다고 쓴다 — 일어난 일 쪽 끝에 붙인다
    if (line(DiaryPageKind.SOLUTION) == null) {
        val at = pages.indexOfLast { it.kind == DiaryPageKind.PROBLEM }.takeIf { it >= 0 }
        val placeAt = pages.indexOfLast { it.kind == DiaryPageKind.PLACE }.takeIf { it >= 0 }
        when {
            at != null -> pages[at] = pages[at].copy(tail = NOT_HEARD_AFTER)
            placeAt != null -> pages[placeAt] = pages[placeAt].copy(tail = NOT_HEARD_THERE)
        }
    }

    // 마지막 줄은 오늘 기분 — 마음을 말했으면(전 쪽) 그 말이 곧 기분이라 덧붙이지 않는다
    if (pages.isNotEmpty() && pages.none { it.kind == DiaryPageKind.REACTION }) {
        val last = pages.last()
        pages[pages.lastIndex] = last.copy(closing = input.feel?.line, asksFeel = input.feel == null)
    }
    return pages
}

/** 어긋난 일이 있던 날인가 — 앱 `hadTrouble()` · `/story` 프롬프트 규칙 4 와 같은 낱말 */
internal fun troubled(lines: Map<String, String>): Boolean =
    TROUBLE_WORDS.any { it in lines["problem"].orEmpty() } ||
        TROUBLE_FEELINGS.any { it in lines["reaction"].orEmpty() }

/**
 * 아이 말 → -어요체 한 문장. **서버가 없을 때만 쓰는 어림**이다 — 진짜 문장은 `/story` 가 쓴다.
 * 이미 -요 로 끝나면 그대로, 「거야」는 「거예요」, 「-어 · -아 · -해 …」는 「-요」를 붙인다.
 */
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

/** 문장이 그 조각을 장소로 쓰나 (「우리 집 앞에」 · 「놀이터에서」) — 그 조각은 걷지 않는다 */
internal fun asPlace(text: String, name: String): Boolean =
    Regex("${Regex.escape(name)}\\s*(앞|옆|안|뒤|위|밑)?\\s*(에|에서)(\\s|$|[.,!?])").containsMatchIn(text)

internal fun moveFrom(text: String): PieceMove = when {
    Regex("무너|쓰러|넘어|떨어|와르르").containsMatchIn(text) -> PieceMove.TOPPLE
    Regex("쌓|세웠|만들|지었|고쳤").containsMatchIn(text) -> PieceMove.BUILD
    Regex("울었|속상|슬펐|아팠").containsMatchIn(text) -> PieceMove.DROOP
    Regex("갔어|왔어|걸어|도착").containsMatchIn(text) -> PieceMove.WALK
    Regex("놀았|뛰|달렸|신났|웃었|재밌").containsMatchIn(text) -> PieceMove.HOP
    else -> PieceMove.BOB
}
