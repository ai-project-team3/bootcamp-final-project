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
 */
fun DiaryDay.addStroke(stroke: Stroke): Int {
    val b = boxOf(listOf(stroke)) ?: return -1
    fun near(p: DiaryPiece) = boxOf(p.strokes)?.grow(PIECE_GAP)?.touches(b) == true
    val last = pieces.lastOrNull()
    val target = last?.takeIf { it.name == null && near(it) }
        ?: pieces.lastOrNull { it.name == null && near(it) }
    if (target == null) {
        val p = DiaryPiece(id = (pieces.maxOfOrNull { it.id } ?: -1) + 1, strokes = listOf(stroke))
        pieces += p
        return p.id
    }
    val i = pieces.indexOfFirst { it.id == target.id }
    pieces[i] = target.copy(strokes = target.strokes + stroke)
    return target.id
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
