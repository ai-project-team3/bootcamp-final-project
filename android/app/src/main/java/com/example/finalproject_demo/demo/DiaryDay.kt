package com.example.finalproject_demo.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * 그림일기 화면 — `Stage` 의 일기 몫. 종류는 이 안에만 더한다(`Model.kt` 의 `Stage` 를 고치지 않는다).
 * 그리는 쪽은 `ui/DiaryViews.kt` 의 `DiaryStageView` 다 (#28).
 */
sealed interface DiaryStage : Stage

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
/**
 * 그림일기 날씨. 이름 붙은 조각을 **낱말로** 읽는다([mentions]) — 글자로 찾으면 「나비」 「비행기」가 비,
 * 「해바라기」가 맑음이 됐다(프로토타입 정규식을 그대로 옮긴 것 · 10-01 검토). 「눈」 하나는 얼굴의 눈일 수 있어 세지 않는다
 */
enum class DiaryWeather(val emoji: String, val label: String, private val words: List<String>) {
    SUN("☀️", "맑음", listOf("해", "해님", "햇님", "햇빛", "햇살", "태양")),
    CLOUD("☁️", "흐림", listOf("구름", "먹구름", "구름들")),
    RAIN("☔", "비", listOf("비", "빗방울", "빗물", "우산", "소나기", "장마")),
    SNOW("⛄", "눈", listOf("눈사람", "눈송이", "눈싸움", "눈꽃"));

    /** 이 이름의 조각이 이 날씨를 그린 것인가 — 날씨를 누르면 그 조각이 반짝인다 */
    fun drew(name: String): Boolean = words.any { mentions(name, it) }

    companion object {
        /** 이름 붙은 조각에서 고른다. 없으면 null — 아이에게 누르게 한다 */
        fun fromPieces(names: List<String>): DiaryWeather? =
            entries.firstOrNull { w -> names.any(w::drew) }
    }
}

/**
 * 오늘 기분 — 그림일기 마지막 줄. 아이가 마음을 말하지 않았을 때만 얼굴을 눌러 채운다(`by: card`).
 * 문장은 「오늘은」까지 써 두고 얼굴이 뒤를 채운다.
 */
enum class DiaryFeel(val emoji: String, val line: String, val word: String) {
    EXCITED("😄", "오늘은 참 신났어요.", "신나"),
    GOOD("🙂", "오늘은 참 좋았어요.", "좋아"),
    UPSET("😢", "오늘은 조금 속상했어요.", "속상해"),
    TIRED("😴", "오늘은 조금 피곤했어요.", "피곤해"),
}

/** 화면이 읽는 것(조각 · 날씨 · 기분)은 Compose 상태라 바뀌면 그림판 · 그림일기가 다시 그려진다 */
class DiaryDay {
    val pieces = mutableStateListOf<DiaryPiece>()

    /** 날씨와 누가 골랐나 — `drawing`(그림에서 알아봄) · `card`(아이가 누름) */
    var weather by mutableStateOf<DiaryWeather?>(null)
    var weatherBy by mutableStateOf<String?>(null)

    var feel by mutableStateOf<DiaryFeel?>(null)

    /** 서버(`/story` diary)가 쓴 책 쪽 문장 — 이름은 이미 풀었다. null 이면 앱 문장으로 짠다 */
    var written by mutableStateOf<List<String>?>(null)

    /** D3 에서 「이건 뭐 그린 거야?」라고 묻는 조각 — 꽂힌 카드가 그 조각만 보여 준다 */
    var focusPiece by mutableStateOf<Int?>(null)

    /** 오또가 그리기를 지켜보는 중 — 이때만 그림판이 붓 멈춤을 알린다(묻는 중 · 고르는 중에는 안 보낸다) */
    var watching by mutableStateOf(false)

    /** 오또가 지금 「뭐 그린 거야?」라고 묻는 조각 — 그림판이 그 조각에 고리를 띄운다 */
    var askingPiece by mutableStateOf<Int?>(null)

    /**
     * 서버 대화 호출(`/turn`) 수 — **세기만 하고 막지 않는다.**
     * 상한을 둘지 · 얼마로 둘지는 #30 에서 정한다. 정해지면 [turnBudget] 에 넣는다(null = 제한 없음).
     */
    /**
     * 한 말이 두 칸을 채웠을 때 둘째 칸(책 키). 그 말은 첫 칸에 아이 말 그대로 이미 들어가므로 책 · `/story` 에는 다시 보내지 않는다 —
     * 판정의 요약(「울었다」)은 주어가 빠져 책이 「나는 울었어요」로 지어냈다(10-01 실기기 · 뽀삐가 울었는데). 판정 상태(`slots`)에는 남긴다
     */
    val sameSaying = mutableSetOf<String>()

    /** 마지막으로 붙인 획 · 색을 바꿔 이어 그리는 중인 조각 — [addStroke] 가 본다 */
    internal var lastStroke: Stroke? = null
    internal var continuing: Int? = null

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

/** 책장에 꽂힌 그림일기의 표지 — 그날 아이가 그린 조각 그대로(오또 그림을 고른 조각은 그 모습). [aspect] 는 화이트보드 폭/높이 */
data class DiaryCover(val pieces: List<DiaryPiece>, val aspect: Float)

private val coversByState = WeakHashMap<DemoState, MutableMap<String, DiaryCover>>()

/**
 * 책 제목 → 표지. 앱을 켜 둔 동안만 남는다 — 기기에 저장하는 것은 책장 저장(#37)이 정한다.
 * 책장 화면은 [hasDiaryCover] 로 물어 있으면 아이 그림 표지를 그린다
 */
val DemoState.diaryCovers: MutableMap<String, DiaryCover>
    get() = coversByState.getOrPut(this) { mutableMapOf() }

fun DemoState.hasDiaryCover(title: String): Boolean = diaryCovers[title]?.pieces?.isNotEmpty() == true

/** 그림일기를 새로 시작한다 — 지난 판의 조각 · 날씨 · 기분 · 호출 수를 버린다 */
fun DemoState.newDiaryDay(): DiaryDay = DiaryDay().also { dayByState[this] = it }
