package com.example.finalproject_demo.demo

/*
 * 그림일기 — **그린 것을 질문의 실마리로** (10-05 진웅 · 2단계).
 *
 * 서버 프롬프트에 「그린 것」을 알려 줘도 모델은 거의 짚지 않았고, 짚을 때는 「그네에서 무슨 일이 있었어?」처럼
 * 그린 것을 있었던 일로 전제했다(`eval/bench_diary_drawing.py` 10-05). 그래서 앱이 **정해진 틀**로 묻는다.
 *
 * 규칙
 * - 이름 붙은 조각 중 **아이가 아직 말하지 않은 것**만 — 찬 칸 · 아이가 한 말에 그 이름이 있으면 뺀다
 * - 「그렸네」로 그린 사실만 말하고, 질문은 그 조각에 대한 **열린 질문**. 있었던 일로 단정하지 않는다
 *   · 곳   「놀이터, 할머니 집. 오늘 어디 있었어?」 (그린 곳을 선택지로 — 지어낸 선택지가 아니다)
 *   · 것   「미끄럼틀도 그렸네! 미끄럼틀 이야기 해 줄래?」 (❌ 「미끄럼틀에서 무슨 일이 있었어?」 — 탄 일을 전제)
 *   · 사람 「엄마도 그렸네! 엄마는 오늘 뭐 했어?」 (❌ 「엄마랑 뭐 했어?」 — 함께 있었다고 전제)
 * - 앞말을 떼어도 질문만으로 단정이 되지 않는 꼴이다(서버 질문 갈무리는 앞말을 뗀다)
 * - 한 조각은 한 번, 한 판에 [CLUE_MAX] 번까지 — 그림 이야기만 되풀이하지 않게
 * - 칸 순서는 판정이 정한다(차별점 2). 실마리는 판정이 고른 칸의 **문구만** 바꾼다
 */

/** 한 판에 그림 실마리로 묻는 횟수 */
internal const val CLUE_MAX = 2

internal enum class ClueKind { PLACE, PERSON, THING }

internal data class DrawnClue(val pieceId: Int, val name: String, val kind: ClueKind)

/** 곳 이름 — 조각 이름의 끝 낱말이 이것이면 곳(「할머니 집」 · 「우리 동네 놀이터」). 두 글자 이상은 「동네놀이터」처럼 붙여 써도 받고, 한 글자(집 · 방 · 산)는 같을 때만 — 「가방」 · 「우산」은 곳이 아니다. 배경 열쇠말(`DIARY_PLACES`)과 달리 물건(그네 · 미끄럼틀)은 넣지 않는다 */
private val PLACE_NOUNS = listOf(
    "놀이터", "어린이집", "유치원", "학교", "교실", "집", "공원", "숲", "산", "강", "바다", "바닷가", "계곡",
    "마트", "시장", "가게", "슈퍼", "키즈카페", "카페", "병원", "치과", "수영장", "동물원", "도서관", "캠핑장", "방", "거실",
)

/** 사람 — 조각 이름이 이것이거나 이것으로 끝나면 사람(「우리 엄마」 · 「친구」) */
private val PEOPLE = listOf(
    "엄마", "아빠", "친구", "할머니", "할아버지", "동생", "언니", "오빠", "누나", "형", "선생님", "아기", "삼촌", "이모", "고모", "사촌",
)

/** 아이 자신 — 「나」를 그렸어도 실마리가 아니다(「너도 그렸네」는 물을 것이 없다) */
private val ME = setOf("나", "내", "저", "우리", "나랑", "너")

internal fun clueKindOf(piece: DiaryPiece): ClueKind? {
    val name = piece.name?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (name in ME) return null
    val last = name.split(" ").last()
    if (piece.role == PieceRole.BACKGROUND || PLACE_NOUNS.any { last == it || it.length >= 2 && last.endsWith(it) }) return ClueKind.PLACE
    if (PEOPLE.any { last == it }) return ClueKind.PERSON
    return ClueKind.THING
}

/**
 * 이야기를 아직 못 들은 사람 · 물건 조각과 그 질문(#220 ②) — 나중에 그린 것부터. 말투는 실마리와 같다(그린 사실 + 열린 질문).
 * 실마리와 달리 **이름을 말한 것만으로는 빼지 않는다** — 이름은 조각을 물어 들은 것이라 늘 아이 말에 있다.
 * 찬 칸(그림 이름 칸 `whiteboard` 는 빼고)에 그 이름이 이미 나왔으면 이야기를 한 것으로 본다
 */
internal fun DemoState.pieceStoryQuestion(day: DiaryDay): Pair<DrawnClue, String>? {
    val told = slots.filterKeys { it != "whiteboard" }.values.filterNotNull().joinToString(" ")
    val c = day.pieces.reversed().firstNotNullOfOrNull { p ->
        val kind = clueKindOf(p)?.takeIf { it != ClueKind.PLACE } ?: return@firstNotNullOfOrNull null
        val name = p.name!!.trim()
        if (p.id in day.pieceStories || p.id in day.pieceStoryAsked || name in told) null else DrawnClue(p.id, name, kind)
    } ?: return null
    return c to if (c.kind == ClueKind.PERSON) "${c.name}도 그렸네! ${c.name}${eun(c.name)} 오늘 뭐 했어?"
    else "${c.name}도 그렸네! ${c.name} 이야기 해 줄래?"
}

/** 아이가 이미 말한 것 — 찬 칸 값과 아이 말 인용 */
private fun DemoState.saidSoFar(): String = (slots.values.filterNotNull() + quotes).joinToString(" ")

/** 아직 말하지 않은 그린 것 — 나중에 그린 것부터(방금 그린 것이 아이 머릿속에 가장 가깝다) */
internal fun DemoState.unsaidClues(day: DiaryDay): List<DrawnClue> {
    val said = saidSoFar()
    return day.pieces.reversed().mapNotNull { p ->
        val kind = clueKindOf(p) ?: return@mapNotNull null
        val name = p.name!!.trim()
        if (p.id in day.cluesUsed || name in said) null else DrawnClue(p.id, name, kind)
    }.distinctBy { it.name }
}

/**
 * [key] 칸(책 키)을 그림 실마리로 묻는 질문 — (판정 슬롯 · 질문 · 책 키). 맞는 실마리가 없거나 [CLUE_MAX] 를 다 썼으면 null.
 * 고른 실마리는 쓴 것으로 적는다(다시 짚지 않는다)
 */
internal fun DemoState.clueQuestion(day: DiaryDay, key: String): Triple<String, String, String>? {
    if (day.cluesUsed.size >= CLUE_MAX) return null
    val clues = unsaidClues(day)
    val (text, used) = when (key) {
        "place" -> {
            val places = clues.filter { it.kind == ClueKind.PLACE }.take(3).takeIf { it.isNotEmpty() } ?: return null
            val q = if (places.size == 1) "${places[0].name} 그렸네! 오늘 어디 있었어?"
            else "${places.joinToString(", ") { it.name }}. 오늘 어디 있었어?"
            q to places
        }
        "problem" -> {
            val c = clues.firstOrNull { it.kind == ClueKind.THING } ?: return null
            "${c.name}도 그렸네! ${c.name} 이야기 해 줄래?" to listOf(c)
        }
        "companion" -> {
            val c = clues.firstOrNull { it.kind == ClueKind.PERSON } ?: return null
            "${c.name}도 그렸네! ${c.name}${eun(c.name)} 오늘 뭐 했어?" to listOf(c)
        }
        else -> return null
    }
    day.cluesUsed += used.map { it.pieceId }
    return Triple(key, text, key)
}
