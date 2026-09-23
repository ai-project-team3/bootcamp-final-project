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

    /**
     * 일기 모드의 **유일한 새 장면** — 동화 모드의 질문 자리(장면 3~10)를 이 하나가 대신한다.
     * 화면을 새로 만든 것이 아니라 질문 세트와 칸 목록만 갈아끼운 것이다 (일기 설계 §1).
     * ⚠️ 화면에 "일기"라고 쓰지 않는다 — 아이에게 숙제처럼 들린다 (§0). 팀 안에서만 쓰는 이름이다.
     */
    DIARY("장면 3′ · 오늘 있었던 일 (일기 모드)", "오늘 있었던 일 말하기"),
    PLACE("장면 3 · 어디로 갈까", "어디로 갈지 정하기"),
    EVENT("장면 4 · 누가 흔들었을까", "무슨 일이 생겼는지 알아보기"),
    CAUSE("장면 5 · 왜 그랬을까", "까닭 생각하기"),
    DRAW("장면 6 · 새 친구 그리기", "새 친구 그리기"),
    PLOT("장면 6↳ · 이야기 잇기", "이야기 이어 가기"),
    // 이름에 "공룡"을 박아 두지 않는다 — 장소마다 데려가는 친구가 다르다 (9/21)
    DINO("장면 7 · 친구도 데려갈래", "함께 갈 친구 정하기"),
    SOUND("장면 8 · 친구 소리", "친구 소리 내기"),
    CHECK("장면 9 · 중간 확인", "그림 확인하기"),
    SOLUTION("장면 10 · 이야기 매듭짓기", "이야기 매듭짓기"),
    MAKING("장면 11 · 책 만드는 중", "동화책 만드는 중"),
    BOOK("장면 12 · 동화책 · 미션", "동화책 읽기"),
    FRIENDS("장면 13 · 친구 평가", "오늘 만난 친구"),
    END("장면 14 · 선물", "선물 받기"),
    SHELF("장면 15 · 책장", "우리 책장"),
    PARENT("장면 16 · 부모 모드", "부모 모드"),
}

/**
 * 이야기를 무엇으로 짓는가 (일기 설계 §0).
 *
 * - [STORY] 동화 모드 — 재료는 상상. "오늘은 어디로 **가 볼까**?"
 * - [DIARY] 일기 모드 — 재료는 **아이의 실제 하루**. "오늘 어디 **갔었어**?"
 *
 * **일기 모드도 일기를 만들지 않는다. 결과물은 동화 모드와 똑같은 6~8쪽 동화책이다.**
 * 끝나는 조건 · 칸 이름 · 판정 · 리포트는 전부 같은 것을 쓴다. 다른 것은 질문 세트와 칸 목록뿐이다.
 */
enum class StoryMode {
    STORY,
    DIARY,

    /**
     * **부모 협업 모드** (부모협업모드_설계.md §0) — 부모에게 소재를 받는 모드가 아니라 **질문하는 사람을 바꾸는 모드**다.
     *
     * ```
     * 동화 모드   마스코트 ──질문──→ 아이 ──답──→ 판정
     * 협업 모드   AI ──귀띔──→ 부모 ──질문──→ 아이 ──답──→ 판정
     * ```
     *
     * 마스코트는 입을 다무는 게 아니라 역할을 반씩 나눈다 — **질문만 부모에게 넘기고
     * 받아주기 · 되돌려주기 · 낭독은 그대로 한다**(§2-2). 넘기면 안 되는 이유는 부모가 지적하기 때문이다.
     *
     * 질문 데이터는 새로 만들지 않는다. 일기 모드의 사다리를 그대로 띄우고
     * **내리는 주체만 AI → 부모**로 바뀐다(§5). 재료도 아이의 실제 하루라 취침 루틴에 같이 놓인다.
     */
    COOP;

    /** 오늘 있었던 일을 재료로 쓰는가 — 일기 · 협업이 같은 질문 세트를 쓴다 (협업 설계 §5) */
    val usesDiaryQuestions: Boolean get() = this == DIARY || this == COOP
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
/**
 * 아이가 그린 선 하나. [pts]는 그림판 크기로 나눈 0~1 좌표, [w]는 **그림판 폭에 대한 붓 굵기**다.
 *
 * 굵기를 같이 들고 다니는 이유 — 전에는 책에 다시 그릴 때 굵기를 화면 크기로 다시 계산해서,
 * 작게 그린 그림일수록 선이 통통하게 부풀어 "실제로 그린 것보다 뚱뚱하다"는 말이 나왔다 (9/21).
 */
data class Stroke(val color: Color, val pts: List<Offset>, val w: Float = PEN_W)

/** 붓 굵기 — 그림판 폭의 몇 배인가. 그림판에서도 책에서도 이 값 하나만 쓴다 */
const val PEN_W = 0.014f

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

/**
 * 무대 위에 세우는 인물 · 탈것 한 장.
 *
 * ## 왜 [depth] 가 생겼나 (2026-09-23)
 *
 * 조장이 실기기에서 *"원근감도 없는 배경에 캐릭터가 중앙에 붕 떠 있다"* 고 짚었다.
 * 배경은 잘못이 없었다 — **얹는 규칙이 없었다.** 전에는 [yf] 가 위 모서리 높이, [wf] 가 폭이었는데,
 * 그 값들(`yf 0.18~0.34` · `wf 0.10~0.15`)이 인물의 발을 화면 **50~60%** 높이에 두었다.
 * 배경 속 물체들의 발은 **78~90%** 에 있으니 인물만 공중에 뜬 것이다.
 *
 * 이제는 **깊이 한 칸이 높이와 크기를 같이 정한다** — 앞줄 1.0, 지평선 0.0.
 * 뒤로 갈수록 발이 올라가고 작아진다. 규칙과 숫자는 `docs/무대_배치_규칙.md`.
 *
 * @param xf 가로 **가운데** 자리 (0~1). 위 모서리가 아니다 — 크기가 깊이에 따라 변하므로
 *   왼쪽 모서리로 두면 인물이 커질 때마다 옆으로 밀린다
 * @param yf **더 이상 발 높이가 아니다.** 옛 호출부를 그대로 두려고 남겨 놓았을 뿐이고,
 *   실제 높이는 [depth] 가 정한다
 * @param wf 같은 이유로 더 이상 크기를 정하지 않는다
 * @param depth 1.0 = 앞줄(가장 크고 가장 아래) · 0.0 = 지평선(가장 작고 가장 위)
 */
data class WorldItem(
    val art: Art,
    val xf: Float,
    val yf: Float,
    val wf: Float,
    val shake: Boolean = false,
    val depth: Float = 1f,
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

    // ── 일기 모드의 일상 장소 (9/21) ────────────────────────────
    //
    // 전에는 일기 책의 배경에 누를 것이 하나도 없었다. 동화 모드 책에서는 배경 속 것이 통 튀는데
    // 일기 책만 가만히 있었다. 좌표는 `tools/gen_diary.py` 가 만든 그림을 보고 손으로 잡았다.
    "bg_playground" to listOf(
        h("tree", "나무", 0.075f, 0.170f, 0.070f, "사각사각~"),
        h("slide", "미끄럼틀", 0.330f, 0.580f, 0.075f, "쌩~!"),
        h("swing", "그네", 0.430f, 0.520f, 0.050f, "흔들흔들~"),
        h("ladder", "오르는 사다리", 0.760f, 0.500f, 0.060f, "영차 영차!"),
        h("sand", "모래밭", 0.715f, 0.760f, 0.085f, "사르르~"),
        h("ball", "공", 0.145f, 0.755f, 0.028f, "통통!"),
    ),
    "bg_daycare" to listOf(
        h("picture", "벽에 붙인 그림", 0.290f, 0.290f, 0.075f, "내가 그렸어!"),
        h("picture", "집 그림", 0.405f, 0.310f, 0.060f, "우리 집이야!"),
        h("book", "책꽂이", 0.800f, 0.400f, 0.080f, "사락사락~"),
        h("block", "블록", 0.410f, 0.790f, 0.090f, "차곡차곡!"),
        h("chair", "작은 의자", 0.078f, 0.680f, 0.055f, "끼익~"),
        h("plant", "화분", 0.135f, 0.500f, 0.035f, "쑥쑥!"),
    ),
    "bg_park" to listOf(
        h("tree", "커다란 나무", 0.170f, 0.230f, 0.100f, "사각사각~"),
        h("tree", "건너편 나무", 0.920f, 0.220f, 0.075f, "사각사각~"),
        h("bench", "긴 의자", 0.355f, 0.630f, 0.075f, "폭신!"),
        h("pond", "연못", 0.560f, 0.545f, 0.070f, "찰랑찰랑~"),
        h("flower", "꽃밭", 0.105f, 0.730f, 0.070f, "향긋~"),
        h("flower", "분홍 꽃", 0.880f, 0.720f, 0.060f, "향긋~"),
    ),
    "bg_grandma" to listOf(
        h("window", "창문", 0.175f, 0.230f, 0.085f, "해가 넘어가네~"),
        h("plant", "화분", 0.085f, 0.430f, 0.060f, "쑥쑥!"),
        h("cabinet", "찬장", 0.490f, 0.360f, 0.080f, "달그락!"),
        h("teapot", "주전자", 0.580f, 0.585f, 0.045f, "쪼르르~"),
        h("cushion", "방석", 0.780f, 0.560f, 0.080f, "폭신!"),
        h("picture", "액자", 0.785f, 0.130f, 0.050f, "예쁘다!"),
    ),
    "bg_home" to listOf(
        h("window", "창문", 0.240f, 0.270f, 0.085f, "해가 넘어가네~"),
        h("lamp", "전등", 0.475f, 0.390f, 0.045f, "반짝!"),
        h("sofa", "소파", 0.730f, 0.545f, 0.100f, "폭신!"),
        h("basket", "장난감 바구니", 0.110f, 0.650f, 0.070f, "덜그럭!"),
        h("picture", "액자", 0.765f, 0.190f, 0.060f, "우리 집 그림!"),
        h("rug", "동그란 깔개", 0.480f, 0.830f, 0.090f, "폭신폭신~"),
    ),
    "bg_mart" to listOf(
        h("fruit", "과일 칸", 0.400f, 0.300f, 0.080f, "달콤해!"),
        h("fruit", "아래 과일 칸", 0.360f, 0.620f, 0.080f, "싱싱해!"),
        h("cart", "카트", 0.690f, 0.630f, 0.070f, "드르륵~"),
        h("shelf", "선반", 0.090f, 0.440f, 0.075f, "가득가득!"),
        h("shelf", "건너편 선반", 0.800f, 0.410f, 0.070f, "가득가득!"),
        h("window", "창문", 0.620f, 0.180f, 0.070f, "해가 넘어가네~"),
    ),
    "bg_kidscafe" to listOf(
        h("ball", "볼풀", 0.170f, 0.760f, 0.100f, "우수수~"),
        h("slide", "미끄럼틀", 0.760f, 0.630f, 0.080f, "쌩~!"),
        h("slide", "작은 미끄럼틀", 0.165f, 0.560f, 0.060f, "쌩!"),
        h("block", "말랑 블록", 0.520f, 0.650f, 0.070f, "폭신!"),
        h("ladder", "오르는 사다리", 0.890f, 0.560f, 0.045f, "영차 영차!"),
        h("picture", "벽 그림", 0.450f, 0.290f, 0.100f, "예쁘다!"),
    ),
    "bg_hospital" to listOf(
        h("bell", "접수대 종", 0.265f, 0.585f, 0.030f, "땡!"),
        h("ruler", "키 재는 자", 0.205f, 0.290f, 0.055f, "얼마나 컸을까?"),
        h("aidbox", "구급상자", 0.435f, 0.570f, 0.042f, "달칵!"),
        h("plant", "화분", 0.325f, 0.445f, 0.050f, "쑥쑥!"),
        h("chair", "기다리는 의자", 0.700f, 0.685f, 0.095f, "폭신!"),
        h("window", "창밖 그림", 0.760f, 0.300f, 0.100f, "밖에 나가고 싶다!"),
    ),
    "bg_pool" to listOf(
        h("pond", "물", 0.520f, 0.660f, 0.100f, "첨벙!"),
        h("ring", "튜브", 0.330f, 0.585f, 0.070f, "둥실~"),
        h("ring", "작은 튜브", 0.345f, 0.690f, 0.070f, "둥실둥실~"),
        h("ball", "공", 0.695f, 0.575f, 0.055f, "통통!"),
        h("umbrella", "파라솔", 0.785f, 0.180f, 0.070f, "그늘이 시원해!"),
        h("chair", "누울 의자", 0.905f, 0.420f, 0.060f, "폭신!"),
    ),
    "bg_zoo" to listOf(
        h("giraffe", "기린", 0.285f, 0.400f, 0.085f, "높다 높아!"),
        h("elephant", "코끼리", 0.680f, 0.500f, 0.090f, "뿌우우~"),
        h("tree", "나무", 0.095f, 0.180f, 0.090f, "사각사각~"),
        h("tree", "건너편 나무", 0.855f, 0.160f, 0.090f, "사각사각~"),
        h("fence", "울타리", 0.185f, 0.700f, 0.080f, "덜컹!"),
        h("path", "길", 0.520f, 0.850f, 0.070f, "터벅터벅~"),
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
    /** 장면 7 — 이 장소에 있을 법한 **같이 갈 친구** 셋 (9/21: 어디를 가든 공룡이 나오던 것을 고쳤다) */
    val buddies: List<DinoKind>,
    /** 아이가 먼저 하는 말 — 그 말에서 되묻는다 (소크라틱의 기본형 · 구현대본 §0-1) */
    val buddyCall: String,
    /** 되묻는 질문 셋 — 아이가 말한 것의 **생김새**를 묻는다 */
    val buddyAsks: List<String>,
)

/**
 * **같이 갈 친구** 한 종류 (장면 7).
 *
 * 처음에는 공룡 셋뿐이었다. 그래서 우주에 가도 바닷속에 가도 아이가 "공룡도 데려갈래!" 하고
 * 공룡이 로켓에 타는 장면이 나왔다 — 아이가 고른 곳과 아무 상관이 없었다 (9/21 지적).
 * 지금은 **장소마다 그곳에 있을 법한 친구 셋**을 고른다 ([Theme.buddies]).
 *
 * [art]는 그림 파일 이름이다. [DemoState.dinoColor]로 색을 바꿀 수 있는 것은 전과 같다.
 */
data class DinoKind(
    val key: String,
    val label: String,
    val name: String,
    val sound: String,
    val art: String,
    /** 책 자막에 쓰는 생김새 — 종결형 ("목이 길어요") */
    val look: String,
    /**
     * 아이가 생김새로 답할 때 쓰는 **관형형** ("목이 긴") — "~ 거!" 앞에 붙는다.
     *
     * [look]에서 규칙으로 만들지 않고 따로 적는다. 한국어 어미는 규칙으로 바꾸면 깨진다 —
     * "혼자서 빛나요" 에 "거!"를 붙여 **"혼자서 빛나요 거!"** 가 나온 적이 있다 (9/21).
     */
    val said: String,
)

/** 공룡 나라 · "눈 오는 데"의 뼈대도 여기서 가져온다 */
val DINOS = listOf(
    DinoKind("trex", "티라노", "티라노", "크아아앙!", "dino_trex", "이빨이 커요", "이빨이 큰"),
    DinoKind("long", "긴목공룡", "긴목공룡", "우우웅~", "dino_long", "목이 길어요", "목이 긴"),
    DinoKind("horn", "뿔공룡", "트리케라톱스", "뿌우우우웅!", "dino_horn", "뿔이 세 개", "뿔이 세 개인"),
)

val SPACE_BUDDIES = listOf(
    DinoKind("alienbud", "외계인 친구", "삐뽀", "삐비빅 삐뽀!", "bud_alien", "눈이 세 개", "눈이 세 개인"),
    DinoKind("robot", "로봇", "또각이", "위잉 위잉!", "bud_robot", "몸이 네모", "몸이 네모난"),
    DinoKind("babystar", "아기 별", "반짝이", "반짝 반짝!", "bud_star", "혼자서 빛나요", "혼자 빛나는"),
)

val SEA_BUDDIES = listOf(
    DinoKind("dolphin", "돌고래", "뽀뽀", "끼익 끼익!", "bud_dolphin", "헤엄이 빨라요", "헤엄이 빠른"),
    DinoKind("seahorse", "해마", "또르", "또르르르~", "bud_seahorse", "꼬리가 돌돌", "꼬리가 돌돌 말린"),
    DinoKind("starfish", "불가사리", "다섯이", "살랑 살랑~", "bud_starfish", "팔이 다섯 개", "팔이 다섯 개인"),
)

val SNOW_BUDDIES = listOf(
    DinoKind("snowman", "눈사람", "뽀드득", "뽀드득 뽀드득!", "bud_snowman", "당근 코가 있어요", "당근 코가 있는"),
    DinoKind("polarbear", "북극곰", "하양이", "어흐응~", "bud_bear", "털이 폭신해요", "털이 폭신한"),
    DinoKind("penguin", "펭귄", "뒤뚱이", "꽥 꽥!", "bud_penguin", "뒤뚱뒤뚱 걸어요", "뒤뚱뒤뚱 걷는"),
)

val ALL_BUDDIES = DINOS + SPACE_BUDDIES + SEA_BUDDIES + SNOW_BUDDIES

fun dinoKind(key: String) = ALL_BUDDIES.firstOrNull { it.key == key } ?: DINOS.last()

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
        buddies = SPACE_BUDDIES,
        buddyCall = "우주 친구도 데려갈래!",
        buddyAsks = listOf("우주 친구? 어떤 친구야? 어떻게 생겼어?", "우주 친구? 그 친구는 뭐가 제일 멋져?", "어떤 우주 친구가 같이 가면 좋을까?"),
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
        buddies = SEA_BUDDIES,
        buddyCall = "바다 친구도 데려갈래!",
        buddyAsks = listOf("바다 친구? 어떤 친구야? 어떻게 생겼어?", "바다 친구? 그 친구는 뭐가 제일 멋져?", "어떤 바다 친구가 같이 가면 좋을까?"),
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
        buddies = DINOS,
        buddyCall = "공룡도 데려갈래!",
        buddyAsks = listOf("공룡? 어떤 공룡이야? 어떻게 생겼어?", "공룡? 그 공룡은 뭐가 제일 멋져?", "어떤 공룡이 같이 가면 좋을까?"),
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

/**
 * 일기 모드의 배경 색 — 해 질 녘. 취침 루틴에 쓰는 기능이라 밤으로 기울여 둔다.
 * 장소 프리셋(우주 · 바닷속 · 공룡 나라)에 **넣지 않는다** — 넣으면 동화 모드 장소 카드에 "오늘"이 끼어든다.
 */
val DIARY_BG = listOf(Color(0xFF3B2C4A), Color(0xFF7A5A6E), Color(0xFFE9A46B))

/**
 * 일기 모드의 장소 그림 — 아이가 말한 곳을 낱말로 맞춰 고른다.
 *
 * 실제 앱은 아이가 말한 장소로 배경을 **세션 중에 만든다**(CLAUDE.md 규칙 8 · 구현대본 §6).
 * 데모는 그럴 수 없으므로 흔한 여섯 곳을 `tools/gen_diary.py`로 미리 만들어 두고 고른다.
 * 맞는 것이 없으면 그림 없이 [DIARY_BG] 색으로 떨어진다 — **엉뚱한 장소를 보여 주지 않는다.**
 */
val DIARY_PLACES: List<Pair<List<String>, String>> = listOf(
    listOf("놀이터", "미끄럼틀", "그네", "모래") to "bg_playground",
    listOf("어린이집", "유치원", "학교", "교실") to "bg_daycare",
    listOf("할머니", "할아버지", "외갓집") to "bg_grandma",
    listOf("공원", "산책", "나무", "숲") to "bg_park",
    listOf("마트", "시장", "가게", "슈퍼") to "bg_mart",
    // 9/21에 더한 네 곳 — 아이가 자주 말하는데 그림이 없어 색 배경으로 떨어지던 곳들
    listOf("키즈카페", "카페", "놀이방", "볼풀") to "bg_kidscafe",
    listOf("병원", "치과", "주사", "의사") to "bg_hospital",
    listOf("수영장", "바다", "물놀이", "계곡") to "bg_pool",
    listOf("동물원", "동물", "기린", "코끼리") to "bg_zoo",
    listOf("집", "방", "거실") to "bg_home",          // 마지막 — "할머니 집"이 먼저 걸리게
)

/**
 * 아이가 말한 곳 → 배경 그림.
 *
 * ⚠️ 9/22 — 못 찾았을 때 `bg_today` 로 떨어뜨리고 있었는데 **그 그림이 없었다.**
 * 그래서 등록된 열 곳에 안 걸리는 날("축구장 갔어")에는 **배경이 통째로 비어 보였다.**
 * 이름은 그대로 두고 **그림을 만들어 채웠다.** 공원 같은 실제 장소로 떨어뜨리면
 * *"모르는 곳은 엉뚱한 배경을 보여 주지 않는다"* 는 원래 뜻이 깨지기 때문이다 —
 * 농장에 갔다는 아이에게 공원을 보여 주는 셈이 된다. `bg_today` 는 **어디라고 말하지 않는**
 * 노을빛 저녁 들판이다. 다른 배경과 같은 규약으로 ComfyUI에서 뽑았다 —
 * 다시 뽑으려면 `python tools/gen_assets.py bg_today` (그 목록에 프롬프트가 있다).
 *
 * 이 이름이 실제 파일로 있는지는 `StoryTextTest.everyBackgroundTheCodeAsksForActuallyExists` 가 지킨다.
 */
const val DIARY_BG_FALLBACK = "bg_today"

fun diaryPlaceBg(place: String?): String {
    val p = place ?: return DIARY_BG_FALLBACK
    return DIARY_PLACES.firstOrNull { (words, _) -> words.any { it in p } }?.second ?: DIARY_BG_FALLBACK
}


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
    /**
     * 선물 화면 — [shown] 은 불이 켜진 선물 수, [done] 은 **더 나올 선물이 없다**는 뜻이다.
     *
     * 둘을 나눈 까닭 (9/22 진웅 실기기 보고): 화면은 [📚 책장에 꽂기] 를 `shown >= 2` 일 때만 그렸는데,
     * 일기·협업 모드에서 **그림을 안 그린 날**은 무지개 크레용을 건너뛰어 `Gifts(1)` 에 머문다
     * (안 한 일에는 선물을 주지 않는다 — 조사3 §1-3). 그래서 버튼이 안 뜨고 감독은 "shelf" 를
     * 영원히 기다려 **앱이 멎었다.** 선물 개수로 끝을 판단하던 것을 고쳐 끝을 직접 말한다.
     */
    data class Gifts(val shown: Int, val done: Boolean = false) : Stage

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

/**
 * 주인공 속성 → res/drawable 이름.
 * 도감 프리셋 2명은 따로 만든 그림, 나머지는 **머리 × 옷 × 안경 × 하의**.
 * 긴바지는 접미사가 없다 — 먼저 만든 27장(전부 긴바지)을 그대로 쓰기 위해서다.
 */
/**
 * 주인공 **몸 그림** 이름 — `body_{옷색}_{하의}` 또는 `body_{옷색}_{하의}_{머리}`.
 *
 * 9/21에 구조를 바꿨다. 전에는 머리 × 옷 × 안경 × 하의 = **완성본 81장**을 갈아 끼웠는데,
 * 81장이 각각 따로 생성된 그림이라 **토글 하나를 바꾸면 캐릭터가 통째로 다른 아이가 됐다** —
 * 머리를 길게 하면 얼굴이 바뀌고, 옷 색을 바꾸면 하의가 바뀌었다.
 *
 * 지금은 **27장**이다(옷 3 × 하의 3 × 머리 3). 옷 색 · 하의 9장을 같은 아이로 뽑고,
 * 그 9장에서 img2img 로 머리만 바꿔 18장을 더 만들었다(`tools/gen_hero_hair.py`).
 * **안경은 그림에 넣지 않는다** — 얼굴이 27장 모두 같으므로 [HeroImage]가 위에 얹는다.
 * 눈(반달 · 별)도 전부터 그렇게 얹어 왔다.
 */
fun heroImageName(a: HeroAttr): String {
    val base = "body_${shirtKey(a.shirt)}_${a.bottom}"
    return if (a.hair == "short") base else "${base}_${a.hair}"
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

    // ── 무엇으로 짓는가 (일기 설계 §0) ─────────────────────────────
    /** 동화 / 일기 / 부모 협업. 첫 화면에서 갈린다. 아이 화면에는 이 이름들이 나오지 않는다 */
    var mode by mutableStateOf(StoryMode.STORY)

    /** 오늘 있었던 일을 재료로 쓰는가 — 일기 모드와 협업 모드가 같은 질문 세트를 쓴다 */
    val isDiary: Boolean get() = mode.usesDiaryQuestions

    /**
     * 무지개 크레용(업적 7 · 그림판 그림을 책에 처음 넣음)을 받는가. 일기·협업에서 안 그린 날은 없다 —
     * 안 한 일에 선물을 주지 않는다 (조사3 §1-3).
     *
     * ⚠️ **이 조건은 여기 한 곳에만 둔다** (09-22). 전에는 `sceneEnd` 에만 있었고 `GiftsView` 는
     * 선물이 늘 2개라고 믿어 `shown >= 2` 에서만 [책장에 꽂기]를 그렸다. 그림 없는 일기·협업은
     * `Gifts(1)` 에 멈춰 **아이 화면에 버튼이 없고 앱이 멎었다** (박진웅 실기기 보고 · `CoopFlowTest`).
     */
    val earnedCrayon: Boolean get() = !isDiary || drawing.isNotEmpty()

    /** 선물 화면에 **그릴** 카드 수 — 받지 않는 선물은 흐리게도 안 그린다. [책장에 꽂기] 는 이 값이 아니라 `Stage.Gifts.done` 이 정한다 */
    val giftCount: Int get() = if (earnedCrayon) 2 else 1

    /** 질문을 부모가 하는가 (협업 설계 §2-2 — 세 조각 중 ③만 넘어간다) */
    val isCoop: Boolean get() = mode == StoryMode.COOP

    // ── 부모 협업 모드 (부모협업모드_설계.md) ───────────────────────
    /**
     * 부모 띠에 떠 있는 질문 카드. **소리 없이** 뜬다 — 부모가 읽고 자기 말로 묻는다 (§2-1 ASK′).
     * 4~5세는 글을 못 읽으므로 아이 화면에 같이 띄워도 된다: 아이는 위 그림을, 부모는 아래 글자를 본다 (§3).
     */
    var parentCard by mutableStateOf<String?>(null)

    /**
     * 부모에게 **마지막으로 준 질문**. 화면에서 내려가도 남는다 (9/22).
     *
     * [parentCard] 는 자막을 번갈아 띄우느라(마스코트가 말할 차례면 비운다) 수시로 지워진다.
     * 사다리를 몇 칸 내려갔는지는 그 표시와 무관하게 세어야 해서 기록을 따로 둔다.
     */
    var parentAsk by mutableStateOf<String?>(null)

    /** 같은 걸음 안에서 **질문이 몇 번 바뀌었나** (사다리를 내려온 칸 수). 첫 질문은 세지 않는다 (9/21) */
    var parentRung by mutableStateOf(0)

    /** 사다리에 아직 남은 칸이 있는가. ⚠️ 띠에서 버튼을 뺀 뒤로 화면에 쓰이지 않는다 (9/21) */
    var parentHasMore by mutableStateOf(false)

    /** 부모 리포트의 "어른이 한 말" — 협업 모드에서 어른이 읽고 물어본 **마지막 질문** (9/21) */
    var adultLine by mutableStateOf<String?>(null)

    /**
     * **부모가 부모 모드에서 미리 넣어 둔 질문들** (09-22 박진웅 요청).
     *
     * ⚠️ **설계가 바뀐 자리다.** `부모협업모드_설계.md` §0은 *"AI가 귀띔하고 부모가 읽어 묻는다"*로
     * 적혀 있는데, 9/21 조장 확인으로 **부모가 부모 모드에서 질문을 미리 커스텀해 두고 아이가
     * 답하는 단순한 모드**가 정본이 됐다. LLM은 **질문을 추천하는 정도**로만 쓴다.
     * 코드(`CoopScenes.kt`)와 문서는 아직 옛 설계이고, 문서 정정은 원저자(안치영)와 조율한다.
     *
     * 비어 있으면 옛 흐름(AI가 띄운 질문을 부모가 읽음)으로 떨어진다 — 그래서 넣기만 해도 안전하다.
     *
     * ⚠️ **이야기 시작에서 비우지 않는다** (09-22 박진웅 지적). `resetStory()` 가 모드를 고른
     * 직후(`Scenes.kt:264`)에 돌기 때문에, 거기서 비우면 부모가 넣은 질문이 [같이 만들기] 를
     * 누르는 순간 사라졌다. 비우는 것은 **협업 이야기가 끝날 때**(`coopFinishLog`)다.
     * ⚠️ `goHome()` 도 아니다 — 부모 모드를 나올 때도 그 길을 타서 입력하자마자 지워졌다 (09-22 박진웅).
     */
    val parentQuestions = mutableStateListOf<String>()

    /**
     * 미리 넣어 둔 질문 중 **몇 개를 썼나**. `parentQuestions.size` 에 닿으면 소진이다.
     * 소진 뒤에 무엇을 하는지는 진웅이 정한다 — AI 추천으로 넘어가나, 마스코트가 이어받나.
     */
    var parentQIndex by mutableStateOf(0)

    /** 미리 넣어 둔 질문이 남았나 */
    val hasParentQuestion: Boolean get() = parentQIndex < parentQuestions.size

    /** 다음 질문을 꺼내고 인덱스를 올린다. 없으면 null */
    fun nextParentQuestion(): String? =
        if (hasParentQuestion) parentQuestions[parentQIndex++] else null

    /**
     * 부모가 넣은 질문을 비운다 — **한 권이 끝났을 때만.**
     * 오늘 넣은 질문이 내일 또 나오면 안 된다. (계정에 남길지는 저장이 붙은 뒤의 일이다)
     */
    fun clearParentQuestions() {
        parentQuestions.clear(); parentQIndex = 0
    }

    /**
     * 협업 모드 아이 화면의 제목 (09-22 박진웅 요청).
     * `Scene.DIARY` 를 협업이 그대로 쓰는데 라벨이 "오늘 있었던 일 말하기"라 협업에서도 그대로 떴다.
     * ⚠️ 협업은 **일기가 아니다** — 부모가 넣은 질문에 답하는 모드라 "있었던 일"이 전제가 아니다.
     * 진웅이 다른 문구를 쓰고 싶으면 [coopLabel] 만 바꾸면 된다.
     */
    var coopLabel by mutableStateOf("오늘 이야기 나누기")

    /** 화면 제목 — 협업일 때만 갈아끼운다. 그리기는 `MainActivity` 가 이 값을 쓴다 */
    val sceneLabel: String get() =
        if (isCoop && scene == Scene.DIARY) coopLabel else scene.label


    /** 일기 모드가 시작된 시각 — 끝나는 조건 셋 중 "15분 경과"를 재는 데 쓴다 (guidelines/2 §1-1) */
    var diaryStart by mutableStateOf(0L)

    /** 시연 서랍 · 대본에서 "15분 지난 것으로" 하고 볼 때 (§7-5는 아직 열린 항목) */
    var diaryTimeUp by mutableStateOf(false)

    /** 마스코트가 대신 채운 것이 **연속으로** 몇 번인가. 2회 연속이 끝나는 조건이다 (§3) */
    var mascotPicks by mutableStateOf(0)

    /** 무엇으로 끝났나 — story_ready · mascot_pick · timeout (§3) */
    var endReason by mutableStateOf<String?>(null)

    /**
     * 칸마다 **누가 채웠나** — `child` · `card` · `mascot` (guidelines/2 §1-4 · 일기 설계 §5-1).
     *
     * ⚠️ 이것을 빼먹으면 일기 설계가 무너진다. §5가 "빈 자리를 이야기로 메워도 된다"고 말할 수 있는
     * 근거가 `by: mascot` 하나다. 안 남기면 메운 문장이 아이가 한 말과 섞여 부모 리포트가 거짓말을 시작한다.
     * `mascot`은 주고받기 횟수 · 수준 신호 · 리포트 원문 인용 · 「말한 방식」 원그래프에서 전부 빠진다.
     */
    val slotBy = mutableStateMapOf<String, String>()

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

    /** 선택 칸 — "그래서 어떻게 됐어 · 기분". 차면 일기 책의 전이 한 쪽 늘어난다 (일기 설계 §2-2) */
    var reaction by mutableStateOf<String?>(null)

    /** 템플릿마다 다른 이야기 조각 — 대응 · 도움 · 시도 · 실패 까닭 · 역할 · 지나간 곳 … (StoryBank의 slot key) */
    val slots = mutableStateMapOf<String, String>()

    /** 함께 하는 사람이 이야기에 보탠 한 줄 — 답하지 않으면 null (책에 넣지 않는다) */
    var partnerHelp by mutableStateOf<String?>(null)
    var partnerHelpLine by mutableStateOf<String?>(null)

    /**
     * 필수 칸 — 동화 모드는 6개(장소 · 문제 · 까닭 · 등장인물 · 소리 · 해결, 구현대본 §2),
     * 일기 모드는 **기승전결 네 자리**(장소 · 문제 · 까닭 · 해결, 일기 설계 §2-1).
     *
     * 일기 모드가 묻지 않는 칸: `sound`(공룡 소리는 상상 세계의 것) · `adult` · `companion` (§2-2).
     * 새 칸 이름은 만들지 않는다 — 판정 스키마의 슬롯 12종 안에서 끝낸다 (guidelines/2 §1-1).
     */
    val reqSlots: List<String?>
        get() = if (isDiary) listOf(place, problem, cause, solution)
        else listOf(place, problem, cause, newcomer, sound, solution)

    val reqCount: Int get() = if (isDiary) 4 else 6
    val filled: Int get() = reqSlots.count { it != null }

    /**
     * 진행 막대가 세는 것 — **물어볼 질문 수**다 (9/22).
     *
     * 전에는 [reqCount](일기 4 · 동화 6)를 썼다. 그런데 실제로 묻는 질문은 일기가 열 걸음 남짓,
     * 동화가 필수 여섯에 템플릿 질문 서넛이다. 그래서 **질문을 여러 개 답해도 막대가 안 움직이다가
     * 한 번에 껑충 뛰었다.** 세 모드 다 같은 증상이었다.
     *
     * 끝나는 조건([diaryReady] · `story_ready`)은 여전히 [filled] · [reqCount] 가 정한다.
     * 여기서 바꾸는 것은 **보이는 막대뿐**이다 — 기승전결 네 자리라는 규격은 그대로다 (일기 §3).
     */
    val askTotal: Int
        get() = (
            if (isDiary) DIARY_STEPS.count { it.ask(this) }.coerceAtLeast(reqCount)
            // 템플릿은 3턴째에 정해진다. 그전에는 **가장 많은 경우(3)로 잡아 둔다** —
            // 0으로 두면 3턴째에 분모가 6 → 9로 늘면서 막대가 **뒤로 물러난다** (9/22)
            else reqCount + (if (templateKey == null) 3 else extraAskSlots.size)
            )
            // 분모가 줄어도 막대가 뒤로 가지 않게 한다. 일기의 걸음 수는 앞의 답에 따라 바뀐다
            .coerceAtLeast(stepsDone)

    /**
     * 동화 모드에서 **필수 칸 말고 더 묻는 것** (템플릿 질문).
     *
     * `resolve` 는 뺀다 — 그 답은 `slots` 가 아니라 필수 칸인 `solution` 으로 들어가서(Scenes §해결),
     * 넣어 두면 **절대 안 차는 칸**이 하나 생겨 막대가 끝까지 가지 못한다.
     */
    private val extraAskSlots: List<String>
        get() = template?.let { t -> (t.plot + t.ending).filter { it != "resolve" } }.orEmpty()

    /**
     * 지금까지 **답이 찬 질문 수**.
     *
     * ⚠️ 처음에는 `turn` 으로 셌는데 **마지막 질문에 답해도 막대가 끝까지 안 찼다** (9/22).
     * 둘의 기준이 달랐기 때문이다:
     *  - 꼬리질문(`extra`)은 `counts = false` 라 `turn` 을 올리지 않는데 [askTotal] 은 센다
     *  - 동화 모드의 `resolve` 는 `slots` 가 아니라 `solution` 으로 들어간다
     *
     * 그래서 양쪽을 **같은 기준(칸이 찼는가)** 으로 맞췄다.
     */
    val askDone: Int
        get() = when {
            // 이야기가 끝났으면 막대도 끝까지 찬다. 기승전결이 일찍 차면 남은 질문을 안 묻고 끝나는데
            // (`story_ready`), 그때 9/10에서 멈춰 있으면 아이는 **덜 한 것처럼** 본다 (9/22)
            endReason != null -> askTotal
            isDiary -> maxOf(
                stepsDone,
                DIARY_STEPS.count { it.ask(this) && !slots[it.bookKey].isNullOrBlank() },
            ).coerceAtMost(askTotal)
            else -> reqSlots.count { it != null } + extraAskSlots.count { !slots[it].isNullOrBlank() }
        }

    /**
     * 일기·협업에서 **지나온 걸음 수** (9/22).
     *
     * 칸이 찼는지로만 세면, 아이가 답하지 않고 넘어간 선택 질문이 하나라도 있으면
     * **마지막 질문까지 가도 막대가 끝까지 가지 않는다.** 물어본 것은 지나온 것으로 센다.
     * 칸이 찬 수와 둘 중 큰 값을 쓰므로, 마스코트가 나중에 메운 칸도 막대에서 사라지지 않는다.
     */
    var stepsDone by mutableStateOf(0)

    /** 기승전결 네 자리가 다 찼는가 = `story_ready` (일기 설계 §3) */
    val diaryReady: Boolean get() = isDiary && filled >= reqCount

    // ── 수준 · 템플릿
    var level by mutableStateOf(Level.CHAIN)          // 지난 세션 종료 단계에서 시작
    var levelAtStart by mutableStateOf(Level.CHAIN)
    var nextLevel by mutableStateOf<Level?>(null)
    var templateKey by mutableStateOf<String?>(null)   // 3턴째 확정
    var attribute by mutableStateOf<String?>(null)
    var causeKind by mutableStateOf("lonely")
    /** 일기 모드는 수준별 템플릿(E·C·D·A·G) 대신 기승전결 한 장짜리를 쓴다 (일기 설계 §5) */
    val template: StoryTemplate? get() = if (isDiary) diaryTemplate(this) else templateKey?.let { templateOf(it) }
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
    // 일기 모드의 장소는 아이가 말한 실제 장소다. 아직 못 들었으면 상상 세계 이름("우주")이 새어 나오지 않게 막는다
    val placeName: String get() = placeLabel ?: if (isDiary) "오늘 있었던 곳" else th.label

    /**
     * 배경 그림 이름 — 프리셋에 없는 장소는 이번에 새로 만든 배경을 쓴다 (구현대본 §6).
     * 일기 모드의 장소는 아이의 실제 하루(어린이집 · 놀이터 …)라 프리셋이 없다.
     * 실제 앱은 아이가 말한 장소로 배경을 한 장 만든다(규칙 8). 데모는 아이가 말한 곳에 맞는
     * 일상 장소 그림 6장을 미리 만들어 두고 고른다([DIARY_PLACES] · `tools/gen_diary.py`).
     * 어디에도 안 맞으면 [worldBg] 그라데이션으로 떨어진다.
     */
    val bgName: String
        get() = when {
            isDiary -> diaryPlaceBg(placeLabel)
            generatedBg -> "bg_snow"
            else -> "bg_$themeKey"
        }

    /** 배경 그림이 없을 때 깔리는 색 — 일기 모드는 해 질 녘 색 (취침 루틴) */
    val worldBg: List<Color> get() = if (isDiary) DIARY_BG else th.bg
    val hotspots: List<Hotspot> get() = HOTSPOTS[bgName].orEmpty()

    /**
     * 일기 책에서 **아이가 말한 것**과 겹치는 배경 속 것 (9/21).
     *
     * 동화 모드는 "거기엔 뭐가 있을까?" 라고 물어 [mentioned]를 채우지만, 일기 모드는 그렇게 묻지 않는다.
     * 대신 아이가 하루를 말하며 이미 꺼낸 낱말("미끄럼틀 탔어" · "블록 쌓았어")을 배경 속 것과 맞춰,
     * 그 자리가 반짝이게 한다. 앱이 없는 것을 만들어 내는 게 아니라 **아이 말에 있던 것만** 켠다.
     */
    val diaryGlow: Set<String>
        get() {
            if (!isDiary) return emptySet()
            val said = slots.values.joinToString(" ") + " " + placeLabel.orEmpty()
            return hotspots.filter { sp ->
                said.contains(sp.name) || (sp.name.length >= 3 && said.contains(sp.name.take(2)))
            }.map { it.key }.toSet()
        }
    val sightAnswers: List<Answer> get() = if (generatedBg) SNOW_SIGHTS else th.sightAnswers

    /**
     * 같이 갈 친구 후보 — 아이가 고른 **장소**가 정한다 (9/21).
     * "눈 오는 데"는 뼈대만 공룡 나라에서 가져오므로 친구는 눈나라 친구로 갈아 끼운다.
     */
    val buddies: List<DinoKind> get() = if (generatedBg) SNOW_BUDDIES else th.buddies
    val buddyCall: String get() = if (generatedBg) "눈나라 친구도 데려갈래!" else th.buddyCall
    val buddyAsks: List<String> get() = if (generatedBg)
        listOf("눈나라 친구? 어떤 친구야? 어떻게 생겼어?", "눈나라 친구? 그 친구는 뭐가 제일 멋져?", "어떤 눈나라 친구가 같이 가면 좋을까?")
    else th.buddyAsks

    /** 아이가 "뭐가 있을까?"에 말한 배경 속 것들 (핫스팟 key) */
    val mentioned = mutableStateListOf<String>()

    /** 배경 속 것들을 한 번 통 튀게 할 때마다 1씩 오른다 */
    var pulse = 0

    var newcomerKind by mutableStateOf("외계인")
    var newcomerEmoji by mutableStateOf("👽")
    /** 같이 갈 친구 — 장면 7에서 장소에 맞는 후보로 정해진다 */
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

    // ── 일기 모드의 소품 · 호칭 ─────────────────────────────────
    // 뼈대(문지르기 · 끌어다 놓기 · 쪽 구성)는 그대로 두고 **소품 그림과 말만 바꾼다** (일기 설계 §7-1 ②).

    /** 책에 나오는 탈것 — 일기 모드에는 로켓 · 거북이 · 기차가 없다. 메고 다닌 가방을 쓴다 */
    val rideArt: Art get() = if (isDiary) Art.Img("prop_bag", Art.Emoji("🎒")) else th.vehicleArt
    val rideName: String get() = if (isDiary) "가방" else th.vehicle

    /** 아이가 그리거나 고른 것이 있을 때만 "아이 그림"이다 (일기 모드는 선택 칸이라 없을 수 있다) */
    val hasChildArt: Boolean get() = drawing.isNotEmpty() || !isDiary

    /**
     * 일기 모드에서 아이가 *"거기 누구랑 있었어?"* 에 말한 사람 — 동행 칸(`companion`)에 들어간다.
     * 이 한 칸이 뒤의 질문을 살린다: 사람이 없으면 *"그 친구는 왜 그랬을까?"* 가 물을 데가 없다 (질문 흐름).
     */
    var companionKind by mutableStateOf("")

    /** 아이가 말한 사람의 그림 — 프리셋에서 고른다. 없으면 아무도 그리지 않는다 */
    val companionArt: Art?
        get() = when {
            "선생님" in companionKind -> Art.Img("dp_teacher", Art.Emoji("🧑‍🏫"))
            "할머니" in companionKind -> Art.Img("ic_p_grandma", Art.Emoji("👵"))
            "할아버지" in companionKind -> Art.Img("ic_p_grandpa", Art.Emoji("👴"))
            "엄마" in companionKind -> Art.Img("ic_p_mom", Art.Emoji("👩"))
            "아빠" in companionKind -> Art.Img("ic_p_dad", Art.Emoji("👨"))
            "언니" in companionKind || "누나" in companionKind || "동생" in companionKind -> Art.Img("dp_friend_g", Art.Emoji("👧"))
            companionKind.isBlank() || "혼자" in companionKind -> null
            else -> Art.Img("dp_friend_b", Art.Emoji("🧒"))
        }

    /**
     * 책과 미션 2의 상대.
     * 아이가 그린 것 → 아이가 말한 사람 → (둘 다 없으면) 아무도 세우지 않는다.
     * ⚠️ 앱이 아이의 하루를 추측해 **없는 친구를 그려 넣지 않는다** (일기 설계 §3-2).
     */
    val friendOrPartnerArt: Art?
        get() = if (hasChildArt) friendArt else companionArt

    /** 말로 부를 이름 — 이름 → 아이가 말한 사람 → 동화 모드의 종류 이름 */
    val friendCallName: String
        get() = friendName.takeUnless { it.startsWith("{") }
            ?: companionKind.takeUnless { it.isBlank() || "혼자" in it }
            ?: if (isDiary) "그 친구" else newcomerKind

    /**
     * 오늘 이야기에 **정말 누가 있었나** (9/22).
     *
     * 일기·협업 모드에서 아이가 아무도 말하지 않은 날이 있다. 그런 날 [friendCallName] 은
     * *"그 친구"* 를 내주는데, 그 이름으로 책을 쓰면 **앱이 없는 친구를 만들어 낸 것**이 된다
     * (일기 설계 §3-2). 미션과 자막은 이 값을 먼저 보고 문장을 고른다.
     */
    val hasCompanion: Boolean
        get() = !isDiary ||
            !friendName.startsWith("{") ||
            (companionKind.isNotBlank() && "혼자" !in companionKind)

    /** 미션 2에서 건넬 상대의 이름 — 아무도 없었던 날에는 마스코트가 받는다 (그림도 마스코트다) */
    val giveTargetName: String get() = if (hasCompanion) friendCallName else "마스코트"

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
        // 둘이 한눈에 달라 보여야 한다 — 안경만 다르면 도감에서 같은 아이로 보인다 (9/21)
        Hero("안경 쓴 지호", HeroAttr(glasses = "round", shirt = Color(0xFF3F7BD9))),
        Hero("빨간 옷 지호", HeroAttr(glasses = "none", shirt = Color(0xFFF25C4C), bottom = "shorts")),
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

    /**
     * 상호작용 원을 **이미 소개했는가** (9/22).
     * 책을 편 처음 몇 초만 둘레가 반짝이고, 그 뒤로는 조용해진다 (ui/Hotspots.kt `introducing`).
     */
    var hotspotIntroShown by mutableStateOf(false)
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
        friend = null; sound = null; solution = null; title = null; reaction = null
        slots.clear(); slotBy.clear(); partnerHelp = null; partnerHelpLine = null
        // 모드는 첫 화면에서 다시 고른다 — 지난 이야기의 모드를 물려받지 않는다
        mode = StoryMode.STORY
        diaryStart = 0L; diaryTimeUp = false; mascotPicks = 0; endReason = null
        companionKind = ""
        parentCard = null; parentAsk = null; parentRung = 0; parentHasMore = false; adultLine = null
        stepsDone = 0; hotspotIntroShown = false
        // ⚠️ 미리 넣어 둔 질문은 **여기서 비우지 않는다** (09-22). 이 함수는 모드를 고른 직후에
        // 돌아서, 여기서 비우면 부모가 방금 넣은 질문이 [같이 만들기] 를 누르는 순간 사라진다.
        // 비우는 곳은 [clearParentQuestions] 이고 부르는 곳은 `coopFinishLog()` 와 `reset()` 이다.
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
        // 앱을 새로 켠 것이므로 부모가 넣은 질문도 지운다 — `resetStory()` 는 이제 안 지운다
        clearParentQuestions()
        heroes.clear()
        heroes += Hero("안경 쓴 지호", HeroAttr(glasses = "round", shirt = Color(0xFF3F7BD9)))
        heroes += Hero("빨간 옷 지호", HeroAttr(glasses = "none", shirt = Color(0xFFF25C4C), bottom = "shorts"))
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

/** ~에게 · ~한테 — 사람 이름 뒤 (받침과 무관하지만 한 곳에서 쓰려고 함수로 둔다) */
fun ege(w: String) = "에게"
fun ya(w: String) = if (bat(w)) "아" else "야"
fun rang(w: String) = if (bat(w)) "이랑" else "랑"
