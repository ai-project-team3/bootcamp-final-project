package com.example.finalproject_demo.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.ui.HeroAttr

/**
 * 장면 — title은 시연 서랍용 긴 이름, label은 화면 맨 위 가운데에 보이는 "무엇을 하는 화면인가".
 */
enum class Scene(val title: String, val label: String) {
    ADULT("장면 1 · 시작 화면", "이야기 시작하기"),
    PARTNER("장면 1↳ · 함께할 사람", "함께할 사람 정하기"),
    BESTIARY("장면 2 · 도감 (주인공 고르기)", "주인공 고르기"),
    MAKEHERO("장면 2↳ · 주인공 만들기", "캐릭터 생성하기"),
    PLACE("장면 3 · 어디로 갈까", "어디로 갈지 정하기"),
    EVENT("장면 4 · 누가 흔들었을까", "무슨 일이 생겼는지 알아보기"),
    CAUSE("장면 5 · 왜 그랬을까", "까닭 생각하기"),
    DRAW("장면 6 · 새 친구 그리기", "새 친구 그리기"),
    PLOT("장면 6↳ · 이야기 잇기", "이야기 이어 가기"),
    DINO("장면 7 · 공룡도 데려갈래", "함께 갈 공룡 정하기"),
    SOUND("장면 8 · 공룡 소리", "공룡 소리 내기"),
    CHECK("장면 9 · 중간 확인", "그림 확인하기"),
    SOLUTION("장면 10 · 이야기 매듭짓기", "이야기 매듭짓기"),
    MAKING("장면 11 · 책 만드는 중", "동화책 만드는 중"),
    BOOK("장면 12 · 동화책 · 미션", "동화책 읽기"),
    FRIENDS("장면 13 · 친구 평가", "오늘 만난 친구"),
    END("장면 14 · 선물", "선물 받기"),
    SHELF("장면 15 · 책장", "우리 책장"),
    PARENT("장면 16 · 부모 모드", "부모 모드"),
}

enum class Persona(val childName: String, val label: String) {
    TALKER("지호", "말하기형 · 지호"),
    CHOOSER("하늘", "고르기형 · 하늘"),
    DRAWER("다온", "그리기형 · 다온"),
}

/**
 * 수준 3단계 (역할1 요약 · 조사2 §3). 이름은 부모에게도 보이지 않는다.
 * pages = 이 단계의 책 쪽 수 (6~8쪽 · 사용자 요청 9/17)
 */
enum class Level(val label: String, val rank: Int) {
    PICK("고르며 짓기", 1),
    CHAIN("이어 짓기", 2),
    REASON("까닭 짓기", 3);

    fun up() = entries.getOrElse(ordinal + 1) { this }
    fun down() = entries.getOrElse(ordinal - 1) { this }
}

/** 시연 서랍 — 더미 아이가 어느 수준처럼 답하나 (마이크를 끄면 이 수준의 답이 더 자주 나온다) */
enum class ChildProfile(val label: String, val level: Level?) {
    RANDOM("무작위", null),
    PICK("고르며 짓기처럼", Level.PICK),
    CHAIN("이어 짓기처럼", Level.CHAIN),
    REASON("까닭 짓기처럼", Level.REASON),
}

/**
 * 함께 하는 사람 — 첫 화면 뒤에 고른다 (9/17). 질문 · 자막 · 기록의 호칭이 전부 이걸 따른다.
 * honor = 높임말 (할머니 · 할아버지) · adult = 어른인가 (친구면 아이 말투)
 */
data class Partner(val key: String, val name: String, val img: String, val emoji: String, val adult: Boolean, val honor: Boolean)

val PARTNERS = listOf(
    Partner("mom", "엄마", "ic_p_mom", "👩", adult = true, honor = false),
    Partner("dad", "아빠", "ic_p_dad", "👨", adult = true, honor = false),
    Partner("aunt", "이모", "ic_p_aunt", "👩‍🦰", adult = true, honor = false),
    Partner("grandma", "할머니", "ic_p_grandma", "👵", adult = true, honor = true),
    Partner("grandpa", "할아버지", "ic_p_grandpa", "👴", adult = true, honor = true),
    Partner("friend", "친구", "ic_p_friend", "👧", adult = false, honor = false),
)

fun partner(key: String) = PARTNERS.firstOrNull { it.key == key } ?: PARTNERS.first()

/** 아이가 그림판에 그린 선 하나. 좌표는 0~1로 정규화. */
data class Stroke(val color: Color, val pts: List<Offset>)

/** 화면에 올라가는 그림 한 장. 실제 앱에서는 ComfyUI 생성 이미지가 들어갈 자리. */
sealed interface Art {
    data class HeroArt(val attr: HeroAttr) : Art
    data class DinoArt(val color: Color, val kind: String) : Art
    data class Alien(val k: Int) : Art
    data class Emoji(val text: String) : Art
    data object Rocket : Art
    data object Mascot : Art

    /** 아이 그림 원본 그대로 (27). 안 그렸으면 프리셋 외계인. */
    data class ChildDrawing(val strokes: List<Stroke>, val preset: Int, val aspect: Float = 1f) : Art

    /** ComfyUI로 만든 그림(res/drawable/<name>). 없으면 fallback으로 그린다. */
    data class Img(val name: String, val fallback: Art) : Art
}

data class Card(val label: String, val art: Art, val value: String)

data class WorldItem(
    val art: Art,
    val xf: Float,
    val yf: Float,
    val wf: Float,
    val shake: Boolean = false,
)

/**
 * 배경 그림 **안에 이미 그려져 있는 것** — 구름 · 행성 · 화산 · 산호 …
 * 새 그림을 얹지 않는다. 그 자리를 누르면 그림의 그 부분이 통 튀고 반짝이며 글자가 뜬다 (9/17).
 * 좌표는 배경 그림(1344×768) 기준 비율 · r은 그림 폭 기준 반지름 비율.
 */
data class Hotspot(val key: String, val name: String, val cx: Float, val cy: Float, val r: Float, val tap: String)

private fun h(key: String, name: String, cx: Float, cy: Float, r: Float, tap: String) = Hotspot(key, name, cx, cy, r, tap)

val HOTSPOTS: Map<String, List<Hotspot>> = mapOf(
    "bg_space" to listOf(
        h("moon", "달님", 0.127f, 0.195f, 0.058f, "안녕, 달님!"),
        h("moon", "작은 달", 0.638f, 0.135f, 0.040f, "쏙!"),
        h("planet", "파란 행성", 0.300f, 0.335f, 0.050f, "빙그르르~"),
        h("planet", "분홍 행성", 0.832f, 0.235f, 0.088f, "빙그르르~"),
        h("planet", "주황 행성", 0.614f, 0.805f, 0.046f, "통통!"),
        h("star", "금빛 별", 0.311f, 0.102f, 0.032f, "반짝!"),
        h("star", "보라 별", 0.200f, 0.530f, 0.030f, "반짝반짝!"),
        h("star", "노란 별", 0.068f, 0.425f, 0.030f, "반짝!"),
        h("star", "은빛 별", 0.695f, 0.350f, 0.026f, "반짝!"),
    ),
    "bg_sea" to listOf(
        h("bubble", "거품", 0.165f, 0.215f, 0.035f, "뽀글!"),
        h("bubble", "거품", 0.845f, 0.250f, 0.035f, "뽀글뽀글!"),
        h("seaweed", "미역", 0.090f, 0.400f, 0.048f, "흔들흔들~"),
        h("seaweed", "미역", 0.955f, 0.420f, 0.045f, "살랑살랑~"),
        h("coral", "분홍 산호", 0.120f, 0.745f, 0.065f, "간질간질~"),
        h("coral", "주황 산호", 0.240f, 0.650f, 0.045f, "간질!"),
        h("coral", "보라 산호", 0.815f, 0.640f, 0.060f, "하하 간지러워!"),
        h("rock", "바위", 0.230f, 0.790f, 0.050f, "쿵!"),
        h("rock", "바위", 0.785f, 0.820f, 0.040f, "쿵!"),
    ),
    "bg_dino" to listOf(
        h("cloud", "구름", 0.165f, 0.170f, 0.080f, "둥실~"),
        h("cloud", "구름", 0.845f, 0.150f, 0.070f, "둥실둥실~"),
        h("cloud", "작은 구름", 0.600f, 0.245f, 0.050f, "몽글~"),
        h("cloud", "작은 구름", 0.380f, 0.330f, 0.050f, "몽글몽글~"),
        h("volcano", "화산", 0.715f, 0.520f, 0.090f, "부글부글!"),
        h("tree", "야자나무", 0.080f, 0.420f, 0.065f, "사각사각~"),
        h("tree", "야자나무", 0.930f, 0.380f, 0.060f, "사각사각~"),
        h("rock", "바위", 0.155f, 0.640f, 0.042f, "쿵!"),
        h("dino", "아기 공룡", 0.815f, 0.680f, 0.070f, "안녕!"),
    ),
    "bg_snow" to listOf(
        h("tree", "눈 덮인 나무", 0.105f, 0.470f, 0.080f, "눈이 툭!"),
        h("tree", "눈 덮인 나무", 0.870f, 0.480f, 0.080f, "눈이 툭!"),
        h("tree", "작은 나무", 0.210f, 0.520f, 0.040f, "툭!"),
        h("tree", "먼 나무", 0.770f, 0.560f, 0.034f, "사각!"),
        h("tree", "먼 나무", 0.965f, 0.400f, 0.040f, "사각사각!"),
        h("snowflake", "눈송이", 0.421f, 0.277f, 0.028f, "사르르~"),
    ),
)

/** 장소 한 곳 — 아이가 고른 장소가 세계 · 탈것 · 사건 · 책 자막까지 정한다. */
data class Theme(
    val key: String,
    val label: String,
    val emoji: String,
    val vehicle: String,
    val vehicleArt: Art,
    /** 무응답일 때만 나오는 장소 카드 그림 (펠트 프리셋 · 대기 0초) */
    val cardArt: Art,
    val bg: List<Color>,
    val arrival: String,
    /** 2턴 사건 — 질문에 쓰는 현재형 · 책 자막에 쓰는 과거형 (구현대본 §6) */
    val eventAsk: String,
    val eventLine: String,
    val newcomers: List<Card>,
    val defaultNewcomer: String,
    /** "여기엔 뭐가 있을까?" 답 5개 (값 = 핫스팟 key를 쉼표로) */
    val sightAnswers: List<Answer>,
    /** 여정형(E)에서 지나가는 곳 후보 · 목적지 후보 */
    val stops: List<String>,
    val goals: List<String>,
)

val THEMES = listOf(
    Theme(
        key = "space", label = "우주", emoji = "🚀", vehicle = "로켓", vehicleArt = Art.Img("rocket", Art.Rocket), cardArt = Art.Img("rocket", Art.Emoji("🚀")),
        bg = listOf(Color(0xFF2B1B4A), Color(0xFF4B3A7A), Color(0xFF7E6BA8)),
        arrival = "폭신폭신 우주에 왔어!",
        eventAsk = "로켓이 덜컹덜컹 흔들려", eventLine = "로켓이 덜컹덜컹 흔들렸어요",
        newcomers = listOf(
            Card("외계인", Art.Img("nc_alien", Art.Emoji("👽")), "외계인"),
            Card("운석", Art.Img("nc_meteor", Art.Emoji("☄️")), "운석"),
            Card("바람", Art.Img("nc_wind", Art.Emoji("💨")), "바람"),
        ),
        defaultNewcomer = "외계인",
        sightAnswers = listOf(
            Answer("별!", "star", lv = 1),
            Answer("행성이 있어!", "planet", lv = 2),
            Answer("달님이 웃고 있어!", "moon", lv = 2),
            Answer("별이랑 행성이 있어! 행성은 빙글빙글 돌아!", "star,planet", el = setOf("배경"), lv = 3),
            Answer("동그란 달이 있는데 거기서 토끼가 살아. 그래서 불이 켜져 있어.", "moon", el = setOf("배경"), con = true, lv = 3),
            Answer("행성!", "planet", lv = 1),
        ),
        stops = listOf("반짝이는 별 다리", "달님 마을", "빙글빙글 행성 놀이터"),
        goals = listOf("무지개 별", "별빛 호수"),
    ),
    Theme(
        key = "sea", label = "바닷속", emoji = "🐙", vehicle = "거북이", vehicleArt = Art.Img("turtle", Art.Emoji("🐢")), cardArt = Art.Img("nc_octopus", Art.Emoji("🐙")),
        bg = listOf(Color(0xFF0B3D5B), Color(0xFF1B6F98), Color(0xFF63B3ED)),
        arrival = "출렁출렁 바닷속에 왔어!",
        eventAsk = "소용돌이에 거북이가 빙글빙글 흔들려", eventLine = "소용돌이에 거북이가 빙글빙글 흔들렸어요",
        newcomers = listOf(
            Card("문어", Art.Img("nc_octopus", Art.Emoji("🐙")), "문어"),
            Card("상어", Art.Img("nc_shark", Art.Emoji("🦈")), "상어"),
            Card("인어", Art.Img("nc_mermaid", Art.Emoji("🧜")), "인어"),
        ),
        defaultNewcomer = "문어",
        sightAnswers = listOf(
            Answer("산호!", "coral", lv = 1),
            Answer("미역이 흔들흔들해!", "seaweed", lv = 2),
            Answer("거품이 뽀글뽀글 올라가!", "bubble", lv = 2),
            Answer("바위 밑에 물고기가 숨었어. 무서워서 숨은 거야.", "rock", el = setOf("배경"), reason = true, lv = 3),
            Answer("산호랑 미역이 있어! 미역은 춤을 춰!", "coral,seaweed", el = setOf("배경"), lv = 3),
            Answer("거품!", "bubble", lv = 1),
        ),
        stops = listOf("알록달록 산호 숲", "뽀글뽀글 거품 길", "반짝 진주 동굴"),
        goals = listOf("인어 궁전", "보물 조개 마을"),
    ),
    Theme(
        key = "dino", label = "공룡 나라", emoji = "🦕", vehicle = "기차", vehicleArt = Art.Img("train", Art.Emoji("🚂")), cardArt = Art.Img("dino_long", Art.Emoji("🦕")),
        bg = listOf(Color(0xFF2E5E2A), Color(0xFF5E9A4A), Color(0xFFA7D48A)),
        arrival = "쿵쿵 공룡 나라에 왔어!",
        eventAsk = "땅이 쿵쿵, 기차가 흔들려", eventLine = "땅이 쿵쿵 울리고 기차가 흔들렸어요",
        newcomers = listOf(
            Card("아기 공룡", Art.Img("nc_babydino", Art.Emoji("🐣")), "아기 공룡"),
            Card("원숭이", Art.Img("nc_monkey", Art.Emoji("🐒")), "원숭이"),
            Card("화산", Art.Img("nc_volcano", Art.Emoji("🌋")), "화산"),
        ),
        defaultNewcomer = "아기 공룡",
        sightAnswers = listOf(
            Answer("화산!", "volcano", lv = 1),
            Answer("구름이 둥실둥실!", "cloud", lv = 2),
            Answer("야자나무가 있어!", "tree", lv = 2),
            Answer("화산이 부글부글해. 그래서 땅이 뜨거워.", "volcano", el = setOf("배경"), reason = true, con = true, lv = 3),
            Answer("아기 공룡이 나무 옆에서 놀고 있어!", "dino,tree", el = setOf("배경"), lv = 3),
            Answer("구름!", "cloud", lv = 1),
        ),
        stops = listOf("쿵쿵 발자국 길", "커다란 야자나무 숲", "부글부글 화산 옆길"),
        goals = listOf("공룡 알 둥지", "무지개 폭포"),
    ),
)

/** 프리셋에 없는 장소("눈 오는 데") — 배경만 새로 만들고 뼈대는 공룡 나라를 쓴다 (구현대본 §6) */
val SNOW_SIGHTS = listOf(
    Answer("나무!", "tree", lv = 1),
    Answer("눈송이가 내려!", "snowflake", lv = 2),
    Answer("나무에 눈이 쌓였어!", "tree", lv = 2),
    Answer("나무에 눈이 쌓였어. 추워서 나무도 이불 덮은 거야.", "tree", el = setOf("배경"), reason = true, lv = 3),
    Answer("나무 옆에서 썰매 탈 수 있어! 눈이 폭신하니까.", "tree", el = setOf("배경"), reason = true, con = true, lv = 3),
    Answer("눈!", "snowflake", lv = 1),
)

fun theme(key: String) = THEMES.first { it.key == key }

/** 공룡 프리셋 3장 — 티라노 · 긴목공룡 · 뿔공룡 (초안 장면 7) */
data class DinoKind(val key: String, val label: String, val name: String, val sound: String)

val DINOS = listOf(
    DinoKind("trex", "티라노", "티라노", "크아아앙!"),
    DinoKind("long", "긴목공룡", "긴목공룡", "우우웅~"),
    DinoKind("horn", "뿔공룡", "트리케라톱스", "뿌우우우웅!"),
)

fun dinoKind(key: String) = DINOS.first { it.key == key }

/** 책장에 꽂힌 책 한 권 (책장에서 다시 읽기는 아직 없다 — 꽂히는 것까지) */
data class ShelfBook(val title: String, val themeKey: String, val bgName: String, val pages: Int = 6, val fresh: Boolean = false)

/** 부모 모드 그림체 견본 4종 (결정안건 부록 6) */
data class ArtStyle(val key: String, val name: String, val img: String, val ready: Boolean)

val ART_STYLES = listOf(
    ArtStyle("felt", "양모 펠트 3D", "style_felt", true),
    ArtStyle("crayon", "아이 크레용", "style_crayon", false),
    ArtStyle("hanji", "전래동화 수채 · 색연필", "style_hanji", false),
    ArtStyle("water", "수채화 캐릭터", "style_water", false),
)

/** 무대(화면 가운데)에 지금 무엇이 있는가. 화면의 모든 손짓은 Reply로 감독에게 간다. */
sealed interface Stage {
    data object Empty : Stage
    data object Adult : Stage
    data class PartnerPick(val picked: String? = null) : Stage
    data class Bestiary(val heroes: List<Hero>, val plus: Boolean) : Stage
    data class CardsRow(
        val cards: List<Card>,
        val picked: String? = null,
        val drawerHint: Boolean = false,
    ) : Stage

    data class HeroShow(val attr: HeroAttr?, val caption: String) : Stage

    /** 그림 한 장을 가운데 보여 주기만 한다 (질문하는 동안 앞 화면의 버튼이 남지 않게) */
    data class Show(val art: Art, val caption: String = "") : Stage
    data class HeroBuilder(val attr: HeroAttr) : Stage
    data class Making(val label: String, val progress: Float = -1f) : Stage

    /** ⭐21 좋아 / 싫어 그림 카드. redraws >= 0 이면 "다시 그리기 n/2" 를 함께 보여준다 (24) */
    data class Confirm(
        val art: Art, val ok: String, val no: String, val world: Boolean = false,
        val redraws: Int = -1, val redrawMax: Int = 2,
    ) : Stage

    /**
     * 세계 — 배경 위에 인물을 얹는다.
     * glow = 지금 반짝이게 할 배경 속 것들(핫스팟 key) · pulse가 바뀔 때마다 그 자리가 통 튄다
     */
    data class World(
        val items: List<WorldItem>,
        val brush: Boolean = false,
        val retry: Int = -1,
        val quake: Boolean = false,
        val glow: Set<String> = emptySet(),
        val pulse: Int = 0,
    ) : Stage

    /** forAnswer = 질문에 그림으로 답하는 중 (새 친구 그리기와 달리 등장인물 칸을 건드리지 않는다) */
    data class DrawPad(val forAnswer: Boolean = false) : Stage
    data class MouthTap(val art: Art) : Stage
    data class BookPage(val index: Int, val m1Done: Boolean = false, val m2Done: Boolean = false) : Stage

    /** S10 친구 평가 — 오늘 만난 친구마다 [또 만날래 💛] [안녕 👋] */
    data class FriendRate(val friends: List<RateItem>) : Stage
    data class Gifts(val shown: Int) : Stage

    /** 책장 — fromEnd = 방금 만든 책을 꽂는 중 */
    data class Shelf(val fromEnd: Boolean) : Stage

    /** 비밀번호 4자리 — purpose: "parent"(부모 모드) · "start"(이야기 시작) */
    data class Pin(val purpose: String, val typed: Int = 0) : Stage
    data class Parent(val tab: String) : Stage
}

/** S10에 늘어놓는 친구 한 명. keep=null 이면 아직 고르지 않음 */
data class RateItem(val id: String, val name: String, val art: Art, val keep: Boolean? = null)

data class Hero(val name: String, val attr: HeroAttr)

/** 옷 색 → 생성 그림 이름 조각 */
fun shirtKey(c: Color): String = when (c) {
    Color(0xFFF25C4C) -> "red"
    Color(0xFFF9B233) -> "yellow"
    Color(0xFF3F7BD9) -> "blue"
    else -> "blue"
}

/** 주인공 속성 → res/drawable 이름. 도감 프리셋 2명은 따로 만든 그림, 나머지는 머리×옷×안경 27장 중 하나 */
fun heroImageName(a: HeroAttr): String = when {
    a.glasses == "round" && a.shirt == Color(0xFF5DADE2) && a.hair == "short" -> "hero_glasses"
    a.glasses == "none" && a.shirt == Color(0xFF3F7BD9) && a.hair == "short" && a.eyes == "round" -> "hero_blue"
    else -> "hero_${a.hair}_${shirtKey(a.shirt)}_${a.glasses}"
}

data class DemoBtn(val label: String, val onClick: () -> Unit)

/**
 * 아이가 말할 수 있는 답 하나 — 글과, 그 말이 뜻하는 값 + 발달 판단용 표시 (역할1 조사2 §4).
 *
 * - reason = S1 ('왜 · 어떻게'에 까닭 · 방법을 담아 답함)
 * - el     = S2 (묻지 않았는데 스스로 채운 이야기 요소: 배경 · 계기 · 시도 · 결과)
 * - con    = A1 (그래서 · 그다음에 · ~서 같은 잇는 말) — 보조 신호
 * - lv     = 더미 데이터용: 이 답이 어느 수준 아이의 답처럼 보이나 (1 고르며 · 2 이어 · 3 까닭)
 * - emo    = 마음 말하기 (수준 판단에는 안 씀 · 부모 기록 재료)
 * - kind   = 까닭 종류 등 이야기 갈래를 정하는 값 (템플릿 고르기에 씀)
 */
data class Answer(
    val text: String,
    val value: String = "",
    val reason: Boolean = false,
    val el: Set<String> = emptySet(),
    val con: Boolean = false,
    val lv: Int = 2,
    val emo: String = "",
    val kind: String = "",
) {
    val extra: Boolean get() = el.isNotEmpty()
    val words: Int get() = text.split(" ", "!", ".", "?", ",").count { it.isNotBlank() }
}

/** 아이가 낸 반응. 마이크 · 화면 탭 · 대본 버튼이 모두 이리로 들어온다. */
sealed interface Reply {
    data class Spoke(val text: String, val value: String = "", val answer: Answer? = null) : Reply

    /** byMascot = 아이가 고르지 않아 마스코트가 채운 것 (기록의 source=mascot) */
    data class Tapped(val value: String, val label: String, val byMascot: Boolean = false) : Reply
    data object PartnerOnly : Reply
    data object Silent : Reply
}

/** 한 턴에 대한 판단 기록 — 시연 서랍 "발달 판단" 표 */
data class TurnNote(val q: String, val a: String, val mode: String, val s1: Boolean, val el: Set<String>, val a1: Boolean, val words: Int)

class DemoState {
    var persona by mutableStateOf(Persona.TALKER)
    var profile by mutableStateOf(ChildProfile.RANDOM)
    var speed by mutableStateOf(1.0)
    var firstDay by mutableStateOf(false)

    /** 무응답 대기 타이머(⭐5 · 5초/8초/7초). 데모 기본은 끔 — 마이크는 온/오프로만 */
    var timerOn by mutableStateOf(false)
    var scene by mutableStateOf(Scene.ADULT)

    // ── 함께 하는 사람
    var partnerKey by mutableStateOf("mom")
    val partner: Partner get() = partner(partnerKey)
    val pn: String get() = partner.name

    // ── 이야기 칸 6개 (진행 막대는 칸이 찼나 하는 표시일 뿐, 점수가 아님)
    var place by mutableStateOf<String?>(null)
    var problem by mutableStateOf<String?>(null)
    var cause by mutableStateOf<String?>(null)
    var newcomer by mutableStateOf<String?>(null)
    var friend by mutableStateOf<String?>(null)
    var sound by mutableStateOf<String?>(null)
    var solution by mutableStateOf<String?>(null)
    var title by mutableStateOf<String?>(null)

    /** 템플릿마다 다른 이야기 조각 — 대응 · 도움 · 시도 · 실패 까닭 · 역할 · 지나간 곳 … (StoryBank의 slot key) */
    val slots = mutableStateMapOf<String, String>()

    /** 함께 하는 사람이 이야기에 보탠 한 줄 — 답하지 않으면 null (책에 넣지 않는다) */
    var partnerHelp by mutableStateOf<String?>(null)
    var partnerHelpLine by mutableStateOf<String?>(null)

    /** 필수 칸 6개 = 장소 · 문제 · 까닭 · 등장인물 · 소리 · 해결 (구현대본 §2) */
    val filled: Int
        get() = listOf(place, problem, cause, newcomer, sound, solution).count { it != null }

    // ── 수준 · 템플릿
    var level by mutableStateOf(Level.CHAIN)          // 지난 세션 종료 단계에서 시작
    var levelAtStart by mutableStateOf(Level.CHAIN)
    var nextLevel by mutableStateOf<Level?>(null)
    var templateKey by mutableStateOf<String?>(null)   // 3턴째 확정
    var attribute by mutableStateOf<String?>(null)
    var causeKind by mutableStateOf("lonely")
    val template: StoryTemplate? get() = templateKey?.let { templateOf(it) }
    val notes = mutableStateListOf<TurnNote>()
    var levelWhy by mutableStateOf("")

    /** 지난 이야기들에서 쓴 질문 — 다음 이야기에서 되도록 다른 질문이 나오게 (앱을 끄기 전까지 유지) */
    val usedVariants = mutableStateListOf<String>()
    val askedThisStory = mutableStateListOf<String>()

    // ── 아이가 고른 것들 — 책은 이걸로 만들어진다
    var themeKey by mutableStateOf("space")

    /** 아이가 말한 장소 그대로. 프리셋 유형에 없으면 배경을 새로 만든다 (구현대본 §6) */
    var placeLabel by mutableStateOf<String?>(null)
    var generatedBg by mutableStateOf(false)
    val placeName: String get() = placeLabel ?: th.label

    /** 배경 그림 이름 — 프리셋에 없는 장소는 이번에 새로 만든 배경을 쓴다 (구현대본 §6) */
    val bgName: String get() = if (generatedBg) "bg_snow" else "bg_$themeKey"
    val hotspots: List<Hotspot> get() = HOTSPOTS[bgName].orEmpty()
    val sightAnswers: List<Answer> get() = if (generatedBg) SNOW_SIGHTS else th.sightAnswers

    /** 아이가 "뭐가 있을까?"에 말한 배경 속 것들 (핫스팟 key) */
    val mentioned = mutableStateListOf<String>()

    /** 배경 속 것들을 한 번 통 튀게 할 때마다 1씩 오른다 */
    var pulse = 0

    var newcomerKind by mutableStateOf("외계인")
    var newcomerEmoji by mutableStateOf("👽")
    var dinoKey by mutableStateOf("horn")
    var solutionKey by mutableStateOf("play")

    /** 미션 2에서 친구에게 건네는 것 — 아이가 말한 해결 방법에서 나온다 */
    var solutionItem by mutableStateOf("star")
    val drawing = mutableStateListOf<Stroke>()
    var drawnPreset by mutableStateOf(0)
    var drawingAspect by mutableStateOf(1f)
    var mouth by mutableStateOf<Offset?>(null)
    var friendName by mutableStateOf("{친구1}")
    var causeLine by mutableStateOf("친구가 없어서 심심했어")
    var solutionLine by mutableStateOf("같이 별을 땄어요")
    var m1Result by mutableStateOf<String?>(null)
    var m2Result by mutableStateOf<String?>(null)
    var bookPage by mutableStateOf(0)

    /** 책 화면 위쪽 안내 한 줄 (책은 전체 화면이라 마스코트 말풍선 대신 여기에) */
    var bookNote by mutableStateOf("")
    var soundLine by mutableStateOf("뿌우우우웅!")

    val th: Theme get() = theme(themeKey)

    /** 장면 4에서 나온 새 친구의 그림 */
    val newcomerArt: Art get() = th.newcomers.firstOrNull { it.value == newcomerKind }?.art ?: Art.Emoji(newcomerEmoji)
    val dino: DinoKind get() = dinoKind(dinoKey)
    val friendArt: Art get() = Art.ChildDrawing(drawing.toList(), drawnPreset, drawingAspect)

    // ── 수준 신호 (역할 1)
    var turn by mutableStateOf(0)
    var s1streak by mutableStateOf(0)
    var s1count by mutableStateOf(0)
    var noAnswerStreak by mutableStateOf(0)
    val signals = mutableStateListOf<String>()
    val quotes = mutableStateListOf<String>()
    val feelings = mutableStateListOf<String>()
    var partnerTurns by mutableStateOf(0)

    // ── 그림 · 생성
    var images by mutableStateOf(0)
    var redraws by mutableStateOf(0)
    val redrawMax = 2
    var dinoColor by mutableStateOf(Color(0xFF6FC276))
    var heroAttr by mutableStateOf<HeroAttr?>(null)

    val heroes = mutableStateListOf(
        Hero("안경 쓴 지호", HeroAttr(glasses = "round", shirt = Color(0xFF5DADE2))),
        Hero("파란 옷 지호", HeroAttr(glasses = "none", shirt = Color(0xFF3F7BD9))),
    )

    val achievements = mutableStateListOf<String>()
    var reactions by mutableStateOf(0)

    // ── 화면
    var stage by mutableStateOf<Stage>(Stage.Empty)
    var speaker by mutableStateOf("마스코트")
    var line by mutableStateOf("")

    /** 말풍선 애니메이션을 다시 시작시키는 번호 — 말할 때마다 1씩 오른다 */
    var lineId by mutableStateOf(0)
    var micOn by mutableStateOf(false)
    var micEnabled by mutableStateOf(false)
    var nextEnabled by mutableStateOf(false)

    /** [직접 그리기 🖍️] — 질문에 따라 켜진다 (장면 3에서는 쓰지 않는다) */
    var drawEnabled by mutableStateOf(false)
    var countdown by mutableStateOf<Double?>(null)

    /** 진행 막대를 보여 주는가 */
    var progressVisible by mutableStateOf(false)
    val buttons = mutableStateListOf<DemoBtn>()

    // ── 부모 설정 (앱을 끄기 전까지 유지 · "처음으로"에도 남는다)
    var limitOn by mutableStateOf(true)
    var dailyLimit by mutableStateOf(3)
    var pinToStart by mutableStateOf(false)
    var artStyle by mutableStateOf("felt")

    /** 오늘 쓴 이야기 수 · 남은 하루 별 — 한 권마다 하나씩 쓴다 */
    var usedToday by mutableStateOf(0)
    val dayStars: Int get() = (dailyLimit - usedToday).coerceAtLeast(0)

    /** 시작 화면에 한 번 띄우는 안내 (하루 별 0 등) */
    var notice by mutableStateOf<String?>(null)

    /** 책장 — 지난 책 2권 + 오늘 만든 책 */
    val shelf = mutableStateListOf(
        ShelfBook("문어랑 바닷속 숨바꼭질", "sea", "bg_sea", 7),
        ShelfBook("기차 타고 공룡 나라", "dino", "bg_dino", 6),
    )

    /** S10에서 "또 만날래"로 고른 친구 — 다음 이야기의 확인 카드 후보가 된다 (⭐26) */
    val keptFriends = mutableStateListOf<String>()

    /** 시연 서랍 [그림 생성 느리게] — 8초 안내 · 15초 취소를 실제로 보여준다 (구현대본 §7) */
    var slowImages by mutableStateOf(false)

    /** 말로 만든 주인공 후보들 — 다시 만들기를 다 쓰면 이 중에서 고른다 (결정 29) */
    val heroTries = mutableStateListOf<HeroAttr>()

    // ── 시연 패널
    val log = mutableStateListOf<String>()
    var behind by mutableStateOf("")
    val done = mutableStateListOf<String>()

    /** 리포트 6축의 재료 — 남겨야 할 이벤트 (구현대본 §0-5) */
    val events = mutableStateListOf<String>()

    /** 말한 방식 — 부모 리포트의 원그래프 재료 (많고 적음을 평가하지 않는다) */
    var modeVoice by mutableStateOf(0)
    var modeCard by mutableStateOf(0)
    var modeDraw by mutableStateOf(0)
    var modeSilent by mutableStateOf(0)

    val childName: String get() = persona.childName

    /** 이야기 한 권 분량만 지운다. 책장 · 부모 설정 · 하루 별 · 도감 · 수준(다음 세션 시작점) · 쓴 질문은 남긴다 */
    fun resetStory() {
        place = null; problem = null; cause = null; newcomer = null
        friend = null; sound = null; solution = null; title = null
        slots.clear(); partnerHelp = null; partnerHelpLine = null
        nextLevel?.let { level = it }
        nextLevel = null; levelAtStart = level
        templateKey = null; attribute = null; causeKind = "lonely"; notes.clear(); levelWhy = ""
        askedThisStory.clear()
        themeKey = "space"; placeLabel = null; generatedBg = false
        mentioned.clear()
        newcomerKind = "외계인"; newcomerEmoji = "👽"
        dinoKey = "horn"; solutionKey = "play"; solutionItem = "star"
        drawing.clear(); drawnPreset = 0; mouth = null
        friendName = "{친구1}"; causeLine = "친구가 없어서 심심했어"; soundLine = "뿌우우우웅!"
        solutionLine = "같이 별을 땄어요"; m1Result = null; m2Result = null; bookPage = 0; bookNote = ""
        turn = 0; s1streak = 0; s1count = 0; noAnswerStreak = 0
        signals.clear(); quotes.clear(); feelings.clear(); partnerTurns = 0
        images = 0; redraws = 0; dinoColor = Color(0xFF6FC276)
        heroAttr = null
        achievements.clear(); reactions = 0
        log.clear(); done.clear(); events.clear()
        heroTries.clear()
        modeVoice = 0; modeCard = 0; modeDraw = 0; modeSilent = 0
        shelf.replaceAll { it.copy(fresh = false) }
    }

    /** 앱을 새로 켠 것처럼 전부 지운다 (시연 서랍 "처음부터") */
    fun reset() {
        nextLevel = null
        level = Level.CHAIN
        resetStory()
        heroes.clear()
        heroes += Hero("안경 쓴 지호", HeroAttr(glasses = "round", shirt = Color(0xFF5DADE2)))
        heroes += Hero("파란 옷 지호", HeroAttr(glasses = "none", shirt = Color(0xFF3F7BD9)))
        shelf.clear()
        shelf += ShelfBook("문어랑 바닷속 숨바꼭질", "sea", "bg_sea", 7)
        shelf += ShelfBook("기차 타고 공룡 나라", "dino", "bg_dino", 6)
        limitOn = true; dailyLimit = 3; usedToday = 0; pinToStart = false; artStyle = "felt"; notice = null
        keptFriends.clear(); usedVariants.clear()
        partnerKey = "mom"
        firstDay = false
    }
}

/** 받침에 따라 조사를 고른다. */
fun bat(w: String): Boolean {
    val c = w.lastOrNull()?.code ?: return false
    return c in 0xAC00..0xD7A3 && (c - 0xAC00) % 28 != 0
}

fun ga(w: String) = if (bat(w)) "이" else "가"
fun eun(w: String) = if (bat(w)) "은" else "는"
fun wa(w: String) = if (bat(w)) "과" else "와"
fun eul(w: String) = if (bat(w)) "을" else "를"
fun ro(w: String) = if (bat(w) && (w.last().code - 0xAC00) % 28 != 8) "으로" else "로"
fun ya(w: String) = if (bat(w)) "아" else "야"
fun rang(w: String) = if (bat(w)) "이랑" else "랑"
