package com.example.finalproject_demo.demo

/*
 * 그림일기 화이트보드의 규칙 (D1 · D5) — 화면 없이 검사할 수 있는 것만.
 *
 *   조각 묶기   새 획은 **위치로** 조각에 붙는다. 떨어진 곳에 그리면 새 조각.
 *               이름이 붙은 조각은 겹쳐 그린 것을 빨아들이지 않는다 — 「집」 앞에 그린 사람은 따로 묶인다
 *               (docs/review/일기모드_0929 20 · 21)
 *   잘라 맞추기 그림일기의 그림 칸은 3:1 이다. 화이트보드 전체가 아니라 **그린 부분만** 잘라 같은 비율로 맞춘다 (47)
 *
 * 좌표는 모두 화이트보드 크기에 대한 비율(0~1)이다 — x 는 폭, y 는 높이 기준. 화이트보드의 폭/높이는 [DemoState.drawingAspect].
 */

/*
 * 그림일기 화면 여섯 — docs/일기모드_UI.html 의 D0 · D1 · D3 · D4 · D5 · D6.
 * 공용 화면(`Stage.Making` · `Stage.Gifts`)을 빌리지 않는다 — 일기만의 모양이라서.
 */

/** D0 — 방에서 손 흔드는 오또 옆에 [그릴래!] · [그림 없이 말할래] */
data object DiaryStart : DiaryStage

/** D1 — 그리는 판 */
data class DiaryBoard(
    /** 오또 그림이 와서 「어떤 게 좋아?」를 고르는 조각 id. null 이면 그리기만 */
    val pick: Int? = null,
) : DiaryStage

/**
 * 다 그린 뒤 빈 칸을 묻는 화면(D3) — 엎드린 오또 옆에 아이 그림을 꽂아 두고, 묻는 말은 늘 쓰는 아래 대사 칸으로.
 * 그림판을 내린다 — 이제 그리는 때가 아니라 말하는 때다
 */
data object DiaryAsk : DiaryStage

/** D4 — 「그림일기를 꿰매는 중…」 */
data object DiaryStitch : DiaryStage

/** D5 — 그림일기 한 쪽 */
data class DiaryPaper(val index: Int) : DiaryStage

/** D6 — 아이 그림이 표지인 오늘 그림일기 → [책장에 꽂기] */
data object DiaryGift : DiaryStage

/** 붓이 이만큼 멈추면 오또가 말을 건다 (프로토타입 1.6초 · 3~7세 값은 조카 관찰로 정한다 — 흐름 HTML 「정해야 할 것」) */
const val BRUSH_PAUSE_MS = 1_600L

/**
 * 크레용을 바꾼 뒤 기다리는 시간 — 색을 고르는 것은 「이어서 그린다」는 뜻이라 [BRUSH_PAUSE_MS] 보다 길게 둔다.
 * 10-01 실기기: 한 조각을 그리다 색을 바꾸러 가는 사이 1.6초가 지나 오또가 다 그린 조각으로 알고 물었다
 */
const val COLOR_PAUSE_MS = 3_000L

/** 크레용을 고른 뒤에 온 붓 멈춤의 표시 — 이어 그리려고 색을 고른 것이라 「다 그렸어?」를 셀 멈춤이 아니다 */
const val CRAYON_PAUSE = "크레용"

/** 이만큼 떨어져 있으면 다른 조각이다 (화이트보드 폭 · 높이의 비율) */
internal const val PIECE_GAP = 0.06f

/** 비율 좌표의 네모 */
data class BoardBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun grow(by: Float) = BoardBox(left - by, top - by, right + by, bottom + by)
    fun touches(o: BoardBox) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    operator fun plus(o: BoardBox) = BoardBox(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

fun boxOf(strokes: List<Stroke>): BoardBox? {
    val pts = strokes.flatMap { it.pts }
    if (pts.isEmpty()) return null
    return BoardBox(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
}

/**
 * 새 획 하나를 조각에 붙이고 그 조각의 id 를 돌려준다.
 *
 * 1. 방금 그리던 조각(마지막 조각)이 **이름이 없고** 가까우면 거기에 — 떼어 그린 머리 · 몸 · 팔이 한 조각이 된다
 * 2. 아니면 가까운 **이름 없는** 조각에
 * 3. 아니면 새 조각. 이름 붙은 조각은 가까워도 빨아들이지 않는다
 *
 * 0. 단, **크레용을 바꿔 방금 그리던 조각에 이어 그리면** 그 조각이다 — 그새 이름이 붙었어도.
 *    색을 고르는 데 오래 걸리면 그 사이 오또가 묻고 이름이 붙는다(10-01 진웅). 새 색으로 이어 긋는 선도
 *    다른 데에 그을 때까지 그 조각에 붙는다
 *
 * 0 · 1 · 2 는 **오또가 이야기를 마치기 전에** 그은 선에만 — 그 뒤에 그은 선은 새 조각이 되어 오또가 묻는다
 * ([DiaryDay.talkedAbout] · 10-06 실기기: 이야기한 조각 위에 새로 그린 것이 합쳐지고 「여기는 어디야?」가 나왔다)
 */
fun DiaryDay.addStroke(stroke: Stroke): Int {
    val b = boxOf(listOf(stroke)) ?: return -1
    val at = pieces.sumOf { it.strokes.size }            // 이 획이 판에서 몇 번째인가 — catchUp 이 차례대로 붙인다
    fun touchesIt(p: DiaryPiece) = boxOf(p.strokes)?.grow(PIECE_GAP)?.touches(b) == true
    fun near(p: DiaryPiece) = touchesIt(p) && openFor(p.id, at)
    fun put(target: DiaryPiece): Int {
        val i = pieces.indexOfFirst { it.id == target.id }
        pieces[i] = target.copy(strokes = target.strokes + stroke)
        lastStroke = stroke
        return target.id
    }
    // 배경선 — 판을 가로지르는 납작한 선(땅 · 하늘 · 바다). 배경 조각끼리만 묶고, 물체와 섞지 않는다
    if (isBackgroundStroke(b)) {
        continuing = null
        pieces.lastOrNull { it.role == PieceRole.BACKGROUND && touchesIt(it) }?.let { return put(it) }
        // 배경을 두 획에 나눠 긋기도 한다 — 바로 전에 같은 색으로 이어 그은 첫 획(휘어 내려와 납작하지 않다)은 물체가 됐다.
        // 아직 이름도 없고 오또가 이야기하지도 않은 그 조각은 새 배경으로 옮긴다 (10-06 실기기 14:09 · 진웅)
        val prev = lastStroke
        val start = prev?.let { s -> pieces.firstOrNull { s in it.strokes } }?.takeIf { p ->
            p.role == PieceRole.OBJECT && p.name == null && openFor(p.id, at) && touchesIt(p) &&
                p.strokes.all { it.color == stroke.color }
        }
        if (start != null) pieces.removeAll { it.id == start.id }
        lastStroke = stroke
        val p = DiaryPiece(id = (pieces.maxOfOrNull { it.id } ?: -1) + 1, strokes = start?.strokes.orEmpty() + stroke, role = PieceRole.BACKGROUND)
        pieces += p
        return p.id
    }
    // 색칠 — 한 조각 안에 들어가 촘촘히 오가는 획은 그 조각을 칠한 것이다(이름이 있어도 · 묻지 않는다). 가장 작은 조각에
    if (isFilling(stroke, b)) {
        pieces.filter { p -> boxOf(p.strokes)?.grow(PIECE_GAP / 3)?.contains(b) == true }
            .minByOrNull { p -> boxOf(p.strokes)!!.let { it.width * it.height } }
            ?.let { continuing = null; return put(it) }
    }
    val prev = lastStroke
    val prevPiece = prev?.let { p -> pieces.firstOrNull { p in it.strokes } }
    val recolored = prev != null && prev.color != stroke.color
    val keepOn = prevPiece?.takeIf { it.name != null && near(it) && (recolored || it.id == continuing) }
    continuing = keepOn?.id
    lastStroke = stroke
    val last = pieces.lastOrNull { it.role == PieceRole.OBJECT }
    val target = keepOn
        ?: last?.takeIf { it.name == null && near(it) }
        ?: pieces.lastOrNull { it.role == PieceRole.OBJECT && it.name == null && near(it) }
    if (target == null) {
        // 무리 — 떨어진 곳에 같은 색 · 비슷한 크기의 작은 것을 또 그렸다(별 · 빗방울 · 꽃). 새 조각 대신 그 무리에 넣는다
        groupFor(stroke, b)?.let { return it }
        val p = DiaryPiece(id = (pieces.maxOfOrNull { it.id } ?: -1) + 1, strokes = listOf(stroke))
        pieces += p
        return p.id
    }
    val i = pieces.indexOfFirst { it.id == target.id }
    pieces[i] = target.copy(strokes = target.strokes + stroke)
    return target.id
}

/** 이름 붙은 조각 중 [piece] 에 닿은 것 — 「○○에 더 그린 거야, 새로 그린 거야?」를 물을 상대 */
fun DiaryDay.namedNeighborOf(piece: DiaryPiece): DiaryPiece? {
    val b = boxOf(piece.strokes)?.grow(PIECE_GAP / 2) ?: return null
    return pieces.lastOrNull { it.id != piece.id && it.name != null && boxOf(it.strokes)?.touches(b) == true }
}

/** 두 조각이 닿아 있나 — 붙여 그린 것은 말만 듣고 합쳐도 된다 */
fun DiaryDay.touching(a: Int, b: Int): Boolean {
    val ba = pieces.firstOrNull { it.id == a }?.let { boxOf(it.strokes) }?.grow(PIECE_GAP / 2) ?: return false
    val bb = pieces.firstOrNull { it.id == b }?.let { boxOf(it.strokes) } ?: return false
    return ba.touches(bb)
}

/**
 * 아이 말이 다른 이름 붙은 조각을 부르나 — 「우리 집 창문」 · 「강아지 꼬리」 · 「우리 집에 그렸어」 → 그 조각.
 * **낱말로** 불러야 한다: 「강아」는 「강아지 뽀삐」 안에 글자로 들어 있지만 부른 것이 아니다(10-01 실기기)
 */
fun DiaryDay.namedIn(text: String, except: Int): DiaryPiece? =
    pieces.filter { it.id != except && it.name != null }.sortedByDescending { it.name!!.length }
        .firstOrNull { callsByName(text, it.name!!) }

/** 이름 뒤에 붙어도 되는 조사 — 그 밖의 글자가 붙으면 다른 낱말이다(「해」 ≠ 「해님」) */
private val PARTICLE = Regex("^(이랑|랑|하고|한테|에서|처럼|이야|이가|은|는|이|가|을|를|에|의|도|야|만)")

private fun callsByName(text: String, name: String): Boolean {
    var at = text.indexOf(name)
    while (at >= 0) {
        val startsWord = at == 0 || !text[at - 1].isLetterOrDigit()
        val rest = text.substring(at + name.length)
        val endsWord = rest.isEmpty() || !rest[0].isLetterOrDigit() || PARTICLE.containsMatchIn(rest) &&
            rest.drop(PARTICLE.find(rest)!!.value.length).let { it.isEmpty() || !it[0].isLetterOrDigit() }
        if (startsWord && endsWord) return true
        at = text.indexOf(name, at + 1)
    }
    return false
}

/** [from] 조각의 선을 [into] 조각에 붙이고 [from] 을 없앤다 — 대화로 「거기에 더 그렸어」라고 정했을 때 */
fun DiaryDay.mergeInto(from: Int, into: Int) {
    val a = pieces.indexOfFirst { it.id == from }
    val b = pieces.indexOfFirst { it.id == into }
    if (a < 0 || b < 0 || a == b) return
    pieces[b] = pieces[b].copy(strokes = pieces[b].strokes + pieces[a].strokes)
    pieces.removeAt(a)
}

/** 배경선 — 판 폭의 이만큼 이상을 가로지르고 */
internal const val BG_MIN_WIDTH = 0.55f
/** 높이는 이만큼 이하인 납작한 선 (판 높이 비율) */
internal const val BG_MAX_HEIGHT = 0.25f
/** 색칠 — 획 길이가 획 상자 긴 변의 이만큼 배 이상이면 촘촘히 오간 것이다(지그재그 · 덧칠) */
internal const val FILL_DENSITY = 3.0f

/** 무리로 묶을 만큼 작은가 — 덩어리의 긴 변이 판의 이만큼 이하 */
internal const val GROUP_MAX_SIDE = 0.15f

/** 무리가 되는 수 — 비슷한 것이 이만큼 모이면 묶는다. 둘(엄마 · 아빠, 두 눈)은 따로 둔다 */
internal const val GROUP_MIN = 3

/**
 * [stroke] 를 무리에 넣고 그 조각 id 를 돌려준다. 넣을 곳이 없으면 null(새 조각).
 * - 이미 있는 무리와 같은 색 · 비슷한 크기(½~2배)면 그 무리에
 * - 같은 색 · 비슷한 크기의 작은 조각이 [GROUP_MIN] - 1 개 이상 있으면 그것들과 함께 새 무리로(이름이 서로 다르면 묶지 않는다 · 이름은 이어받는다)
 * 붙여 그린 획은 이미 위에서 그 조각에 붙었다 — 여기는 떨어져 새로 그린 것만 온다
 */
private fun DiaryDay.groupFor(stroke: Stroke, b: BoardBox): Int? {
    val side = maxOf(b.width, b.height)
    if (side > GROUP_MAX_SIDE) return null
    fun alike(p: DiaryPiece) = p.role != PieceRole.BACKGROUND && p.strokes.all { it.color == stroke.color } &&
        p.clusters().all { c -> boxOf(c)!!.let { cb -> maxOf(cb.width, cb.height).let { it <= GROUP_MAX_SIDE && side in it / 2..it * 2 } } }
    pieces.lastOrNull { it.role == PieceRole.GROUP && alike(it) }?.let { g ->
        pieces[pieces.indexOfFirst { it.id == g.id }] = g.copy(strokes = g.strokes + stroke)
        return g.id
    }
    val mates = pieces.filter { it.role == PieceRole.OBJECT && alike(it) }
    if (mates.size + 1 < GROUP_MIN || mates.mapNotNull { it.name }.distinct().size > 1) return null
    val keep = mates.last()
    mates.dropLast(1).forEach { mergeInto(it.id, keep.id) }
    val i = pieces.indexOfFirst { it.id == keep.id }
    val merged = pieces[i]
    pieces[i] = merged.copy(strokes = merged.strokes + stroke, role = PieceRole.GROUP, name = mates.firstNotNullOfOrNull { it.name })
    return keep.id
}

/** 조각 안의 덩어리들 — 서로 닿는 획끼리. 무리면 덩어리가 여럿이다(별 하나하나) */
fun DiaryPiece.clusters(): List<List<Stroke>> {
    val groups = mutableListOf<MutableList<Stroke>>()
    strokes.forEach { s ->
        val sb = boxOf(listOf(s)) ?: return@forEach
        val touching = groups.filter { g -> boxOf(g)!!.grow(PIECE_GAP).touches(sb) }
        if (touching.isEmpty()) groups += mutableListOf(s)
        else {
            val into = touching.first()
            into += s
            touching.drop(1).forEach { other -> into += other; groups.remove(other) }
        }
    }
    return groups
}

/** 오또에게 보낼 조각 — 무리면 가장 큰 덩어리 하나(별 하나), 아니면 조각 그대로 */
fun DiaryPiece.redrawSample(): DiaryPiece =
    if (role != PieceRole.GROUP) this
    else copy(strokes = clusters().maxByOrNull { c -> boxOf(c)!!.let { it.width * it.height } } ?: strokes)

/** 오또 그림을 놓을 자리 — 무리면 덩어리마다, 아니면 조각 하나 */
fun DiaryPiece.ottoSpots(): List<BoardBox> =
    if (role == PieceRole.GROUP) clusters().mapNotNull { boxOf(it) } else listOfNotNull(boxOf(strokes))

/** 판을 가로지르는 납작한 선인가 — 땅 · 하늘 · 바다. 3~7세 값은 획 기록으로 다시 잡는다 */
internal fun isBackgroundStroke(b: BoardBox): Boolean = b.width >= BG_MIN_WIDTH && b.height <= BG_MAX_HEIGHT

/** 촘촘히 오간 획인가 — 색칠 */
internal fun isFilling(s: Stroke, b: BoardBox): Boolean {
    val side = maxOf(b.width, b.height)
    if (side <= 0f || s.pts.size < 3) return false
    val length = s.pts.zipWithNext { a, c -> kotlin.math.hypot((c.x - a.x).toDouble(), (c.y - a.y).toDouble()) }.sum()
    return length / side >= FILL_DENSITY
}

private fun BoardBox.contains(o: BoardBox) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom

/** 지운 획 하나 — 되살릴 때 그 획이 속했던 조각(이름 · 고른 모습 · 오또 그림까지)을 그대로 돌려놓는다 */
class UndoneStroke(val stroke: Stroke, val pieceBefore: DiaryPiece?, val pieceAt: Int)

/**
 * 마지막 획을 지운다(그림판 ↶). 조각에서도 빼고, 조각에 남은 획이 없으면 조각도 뺀다. 지울 획이 없으면 null
 */
fun DiaryDay.undoStroke(drawing: MutableList<Stroke>): UndoneStroke? {
    val last = drawing.lastOrNull() ?: return null
    catchUp(drawing)
    val at = pieces.indexOfFirst { last in it.strokes }
    val before = pieces.getOrNull(at)
    drawing.removeAt(drawing.lastIndex)
    if (before != null) {
        val left = before.strokes - last
        if (left.isEmpty()) pieces.removeAt(at) else pieces[at] = before.copy(strokes = left)
    }
    lastStroke = drawing.lastOrNull()
    continuing = null
    return UndoneStroke(last, before, at)
}

/** 지운 획을 되살린다(그림판 ↷) — 그 획이 속했던 조각을 지우기 전 모습으로 돌려놓는다 */
fun DiaryDay.redoStroke(drawing: MutableList<Stroke>, u: UndoneStroke) {
    catchUp(drawing)
    drawing += u.stroke
    val before = u.pieceBefore
    if (before == null) { addStroke(u.stroke); return }
    val now = pieces.indexOfFirst { it.id == before.id }
    if (now >= 0) pieces[now] = before else pieces.add(u.pieceAt.coerceIn(0, pieces.size), before)
    lastStroke = u.stroke
}

/** 화이트보드의 획 중 아직 어느 조각에도 안 붙은 것을 붙인다. 붙인 수를 돌려준다 */
fun DiaryDay.catchUp(drawing: List<Stroke>): Int {
    val known = pieces.sumOf { it.strokes.size }
    val fresh = drawing.drop(known)
    fresh.forEach { addStroke(it) }
    return fresh.size
}

/**
 * 그린 부분을 [ratio](폭 : 높이, 화면 기준)로 잘라 낼 네모 — 비율 좌표로.
 * [aspect] 는 화이트보드의 폭/높이다. 그린 부분에 조금 여백을 두고, 모자란 쪽을 늘려 비율을 맞춘다.
 */
fun cropFor(strokes: List<Stroke>, aspect: Float, ratio: Float = 3f, pad: Float = 0.04f): BoardBox {
    val b = boxOf(strokes)?.grow(pad) ?: return BoardBox(0f, 0f, 1f, 1f)
    // 화면 단위로 바꿔서 비율을 맞춘다: 폭 = w * aspect, 높이 = h
    var w = maxOf(b.width, 0.02f) * aspect
    var h = maxOf(b.height, 0.02f)
    if (w / h < ratio) w = h * ratio else h = w / ratio
    val cx = (b.left + b.right) / 2f
    val cy = (b.top + b.bottom) / 2f
    return BoardBox(cx - w / aspect / 2f, cy - h / 2f, cx + w / aspect / 2f, cy + h / 2f)
}
