package com.example.finalproject_demo.demo

import java.util.WeakHashMap

/*
 * 그림일기 한 판의 상태 (docs/일기모드_흐름.html · 09-30).
 *
 * `Model.kt` 에 칸을 더하지 않는다 — 협업이 쓰는 방식(`CoopScenes.kt` 의 약한 참조 홀더)과 같게
 * 상태마다 하나씩 붙여 둔다. 그림일기를 시작할 때 [newDiaryDay] 로 새로 만든다.
 *
 * 칸 값(`place` · `problem` …)과 출처(`by`)는 여기 두지 않는다 — 전처럼 `DemoState.slots` · `slotBy` 가 정본이다.
 * 여기에는 그림일기에만 있는 것만 둔다: 조각 · 날씨 · 오늘 기분 · 서버 호출 수.
 */

/** 조각이 책에서 보이는 모습. 기본은 늘 아이 원본이다 (차별점 1 완화의 선 — #33) */
enum class PieceLook { ORIGINAL, OTTO }

/**
 * 화이트보드에서 위치로 묶인 획 한 덩어리 (D1).
 *
 * [name] 은 **아이가 말한 이름**이다. 인식한 낱말은 여기 넣지 않는다 — 질문 문구에만 쓴다(규칙 5).
 * 좌표는 화이트보드 크기에 대한 비율(0~1)이다.
 */
data class DiaryPiece(
    val id: Int,
    val strokes: List<Stroke>,
    val name: String? = null,
    val look: PieceLook = PieceLook.ORIGINAL,
    /** 오또가 그린 모습 (투명 PNG). 받기 전이거나 실패하면 null — 원본으로 간다 */
    val ottoPng: ByteArray? = null,
) {
    override fun equals(other: Any?) = other is DiaryPiece && other.id == id && other.strokes == strokes &&
        other.name == name && other.look == look && other.ottoPng.contentEquals(ottoPng)

    override fun hashCode() = id
}

/**
 * 날씨 — **지어내지 않는다.** 아이가 해 · 구름 · 비 · 눈을 그리고 그렇게 이름 붙였으면 저절로,
 * 아니면 그림일기 쪽에서 아이가 누른다.
 */
enum class DiaryWeather(val emoji: String, val label: String, private val drawn: Regex) {
    SUN("☀️", "맑음", Regex("해|햇님|햇빛|태양")),
    CLOUD("☁️", "흐림", Regex("구름")),
    RAIN("☔", "비", Regex("비|우산|빗")),
    SNOW("⛄", "눈", Regex("눈사람|눈이|눈싸움"));

    companion object {
        /** 이름 붙은 조각에서 고른다. 없으면 null — 아이에게 누르게 한다 */
        fun fromPieces(names: List<String>): DiaryWeather? =
            entries.firstOrNull { w -> names.any { w.drawn.containsMatchIn(it) } }
    }
}

/**
 * 오늘 기분 — 그림일기 마지막 줄. 아이가 마음을 말하지 않았을 때만 얼굴을 눌러 채운다(`by: card`).
 * 문장은 「오늘은」까지 써 두고 얼굴이 뒤를 채운다.
 */
enum class DiaryFeel(val emoji: String, val line: String) {
    EXCITED("😄", "오늘은 참 신났어요."),
    GOOD("🙂", "오늘은 참 좋았어요."),
    UPSET("😢", "오늘은 조금 속상했어요."),
    TIRED("😴", "오늘은 조금 피곤했어요."),
}

class DiaryDay {
    val pieces = mutableListOf<DiaryPiece>()

    /** 날씨와 누가 골랐나 — `drawing`(그림에서 알아봄) · `card`(아이가 누름) */
    var weather: DiaryWeather? = null
    var weatherBy: String? = null

    var feel: DiaryFeel? = null

    /**
     * 서버 대화 호출(`/turn`) 수 — **세기만 하고 막지 않는다.**
     * 상한을 둘지 · 얼마로 둘지는 #30 에서 정한다. 정해지면 [turnBudget] 에 넣는다(null = 제한 없음).
     */
    var turnCalls = 0
    var turnBudget: Int? = null

    fun canCallTurn(): Boolean = turnBudget.let { it == null || turnCalls < it }

    /** 이름 붙은 조각의 이름 — 그린 차례대로. 같은 이름은 한 번만 */
    val pieceNames: List<String> get() = pieces.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.distinct()

    /** 그림에서 날씨를 알아볼 수 있으면 채운다. 아이가 이미 눌렀으면 건드리지 않는다 */
    fun weatherFromDrawing() {
        if (weatherBy == "card") return
        val w = DiaryWeather.fromPieces(pieceNames)
        weather = w
        weatherBy = w?.let { "drawing" }
    }

    fun pickWeather(w: DiaryWeather) {
        weather = w
        weatherBy = "card"
    }
}

private val dayByState = WeakHashMap<DemoState, DiaryDay>()

/** 이 상태의 그림일기. 아직 없으면 새로 만든다 */
val DemoState.diaryDay: DiaryDay
    get() = dayByState[this] ?: DiaryDay().also { dayByState[this] = it }

/** 그림일기를 새로 시작한다 — 지난 판의 조각 · 날씨 · 기분 · 호출 수를 버린다 */
fun DemoState.newDiaryDay(): DiaryDay = DiaryDay().also { dayByState[this] = it }
