package com.example.finalproject_demo.ui

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.eul
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.ga

/**
 * 협업 탭 템플릿 — **장소 · 직업 · 스포츠 → 요소 하나 → 고른 이유** (09-29 · 엔드픽처 「같이 만들기」).
 *
 * 어른이 부모 모드에서 고르는 것은 **질문 줄이 아니라 이야기의 맥락**이다 (09-30 사용자 결정).
 * 흐름은 일반 모드처럼 오또가 걸음마다 묻고, 기승전결 네 자리에서만 고른 요소 · 이유에 맞춘 질문으로 묻는다
 * ([templateQuestions] — LLM이 붙으면 이 맥락을 프롬프트에 넣는다). 부모가 따로 적은 질문은 꼬리질문 자리에 끼워진다.
 *
 * ⚠️ 네 줄의 순서는 place → problem → cause → solution 이다.
 *    `CoopScenes.kt` 의 `COOP_PART_SLOTS` 가 줄 0~3을 이 순서로 칸에 짝짓는다 — 어긋나면 답이 다른 칸에 들어간다.
 *    그래서 템플릿마다 이야기 모양(탐험 · 임무 · 도전)이 달라도 **묻는 자리**는 같다: 어디 → 무슨 일 → 왜 → 어떻게 됐나.
 * - **고른 이유**가 같은 요소의 질문을 바꾼다 — 다녀왔으면 기억(과거), 곧 하면 기대(미래), 좋아하면 상상.
 *   다른 모드와 가르는 장치가 이것이다. 이유를 안 고르면 상상(`dream`)으로 묻는다
 * - 요소는 목록에서 고르거나 **직접 쓴다** — 질문 틀이 이름만 끼워 넣으므로 어떤 이름이든 같은 틀로 묻는다
 * - 모든 줄이 [questionHint] 에 안 걸려야 한다 — 우리 예시가 우리 귀띔에 걸리면 모순이다
 * - 아이 이름은 넣지 않는다. 질문 글이 로그 · 리포트에 남으므로 실명이 섞이지 않게 한다 (규칙 6)
 * - 4번 줄(solution)은 **결말**을 묻는다 — 바람("~하고 싶어?")은 넣지 않는다
 */
enum class CoopReason(val key: String) { DONE("done"), SOON("soon"), DREAM("dream") }

class CoopKind(
    val key: String,
    val emoji: String,
    val title: String,
    /** 이야기 모양 — 카드에 보이는 한 줄 */
    val arc: String,
    val items: List<String>,
    /** 이유 버튼 이름 — 템플릿마다 말이 다르다 (다녀왔어요 · 체험했어요 · 해 봤어요) */
    val reasonLabels: Map<CoopReason, String>,
    /** 이유 버튼 아래 예시 */
    val reasonExamples: Map<CoopReason, String>,
    /** 직접 쓰기 칸의 예시 */
    val customExample: String,
    private val build: (name: String, reason: CoopReason) -> List<String>,
) {
    /**
     * 네 줄 — place · problem · cause · solution. 이유가 없으면 상상으로.
     * 목록에 있는 요소면 problem 줄을 그 요소에 맞춘 말로 바꾼다([COOP_ITEMS] — 10-02).
     * 직접 쓴 요소는 프로필이 없어 틀 그대로 묻는다
     */
    fun questions(name: String, reason: CoopReason? = null): List<String> {
        val n = name.trim()
        val r = reason ?: CoopReason.DREAM
        val lines = build(n, r)
        val item = coopItem(n)?.takeIf { n in items } ?: return lines
        return lines.toMutableList().also { it[1] = item.problem.getValue(r) }
    }
}

val COOP_KINDS = listOf(
    CoopKind(
        "place", "🗺️", "장소", "탐험 이야기",
        listOf("우리집", "학교", "놀이공원", "아쿠아리움", "동물원"),
        mapOf(CoopReason.DONE to "다녀왔어요", CoopReason.SOON to "곧 가요", CoopReason.DREAM to "좋아해요"),
        mapOf(CoopReason.DONE to "어제 다녀왔어요", CoopReason.SOON to "주말에 가요", CoopReason.DREAM to "그냥 좋아해요"),
        "할머니 집",
    ) { x, r ->
        when (r) {
            CoopReason.DONE -> listOf("${x}에 가서 어디가 제일 좋았어?", "거기서 무슨 일이 있었어?", "왜 그런 일이 생겼을까?", "그래서 어떻게 됐어?")
            CoopReason.SOON -> listOf("${x}에 가면 어디에 제일 먼저 가 볼까?", "거기서 무슨 일이 생길까?", "왜 그런 일이 생길까?", "그 일은 어떻게 끝날까?")
            CoopReason.DREAM -> listOf("${x}에서 어디가 제일 좋아?", "거기서 어떤 신기한 일이 생길까?", "왜 그런 일이 생겼을까?", "그래서 어떻게 됐을까?")
        }
    },
    CoopKind(
        "job", "🚒", "직업", "임무 이야기",
        listOf("소방관", "의사", "요리사", "경찰관", "우주비행사"),
        mapOf(CoopReason.DONE to "체험했어요", CoopReason.SOON to "곧 체험해요", CoopReason.DREAM to "꿈이에요"),
        mapOf(CoopReason.DONE to "견학을 다녀왔어요", CoopReason.SOON to "다음 주에 견학 가요", CoopReason.DREAM to "되고 싶대요"),
        "선생님",
    ) { x, r ->
        when (r) {
            CoopReason.DONE -> listOf("$x 체험하러 가서 어디를 가 봤어?", "거기서 무슨 일을 해 봤어?", "왜 그 일을 할까?", "일이 다 끝나고 어떻게 됐어?")
            CoopReason.SOON -> listOf("$x${eun(x)} 어디서 일할까?", "거기서 무슨 일을 할까?", "왜 그 일이 필요할까?", "일이 다 끝나면 어떻게 될까?")
            CoopReason.DREAM -> listOf("네가 $x${ga(x)} 되면 어디서 일할까?", "거기서 어떤 일이 생길까?", "왜 그런 일이 생겼을까?", "그래서 어떻게 고쳤을까?")
        }
    },
    CoopKind(
        "sport", "⚽", "스포츠", "도전 이야기",
        listOf("축구", "농구", "야구", "수영", "태권도"),
        mapOf(CoopReason.DONE to "해 봤어요", CoopReason.SOON to "곧 시작해요", CoopReason.DREAM to "좋아해요"),
        mapOf(CoopReason.DONE to "친구들이랑 해 봤어요", CoopReason.SOON to "다음 달부터 배워요", CoopReason.DREAM to "보는 걸 좋아해요"),
        "줄넘기",
    ) { x, r ->
        when (r) {
            CoopReason.DONE -> listOf("$x${eul(x)} 어디서 했어?", "$x 하다가 무슨 일이 있었어?", "왜 그랬을까?", "그래서 어떻게 됐어?")
            CoopReason.SOON -> listOf("$x${eul(x)} 어디서 배울까?", "처음 하는 날 무슨 일이 생길까?", "왜 그런 일이 생길까?", "그 일은 어떻게 끝날까?")
            CoopReason.DREAM -> listOf("$x 경기가 어디서 열릴까?", "경기에서 어떤 일이 생길까?", "왜 그런 일이 생겼을까?", "경기는 어떻게 끝났을까?")
        }
    },
)

fun coopKind(key: String): CoopKind? = COOP_KINDS.firstOrNull { it.key == key }

/**
 * 목록 요소마다 이야기 재료 (10-02 사용자 요청 — 고른 요소에 맞춰 질문 · 배경도 바뀌어야 한다).
 *
 * - [problem] 기승전결의 「무슨 일」 질문 — 고른 이유(다녀옴 · 곧 · 상상)마다. 의문사 하나, 「언제」 없음([questionHint])
 * - [spots] · [troubles] · [causes] · [fixes] 쉬운 질문의 선택지 — 장소 · 무슨 일 · 까닭 · 해결 자리에 그 요소다운 말로
 *   (선택지 먼저 · 질문 마지막 — 묻는 말은 걸음 정의 그대로 두고 선택지만 갈아 끼운다, `CoopTemplatePack.kt`)
 * - [bg] 같이 만들기 화면 배경 — 같은 펠트 화풍 그림(`res/drawable/bg_*`). 일기 모드는 이 표를 보지 않는다
 *
 * 「거기서」가 들어간 줄은 앞에서 아이가 말한 곳으로 바뀐다(`CoopTemplatePack.kt` [here]).
 */
class CoopItem(
    val bg: String,
    val problem: Map<CoopReason, String>,
    val spots: List<String>,
    val troubles: List<String>,
    val causes: List<String>,
    val fixes: List<String>,
)

private fun item(
    bg: String, done: String, soon: String, dream: String,
    spots: List<String>, troubles: List<String>, causes: List<String>, fixes: List<String>,
) = CoopItem(bg, mapOf(CoopReason.DONE to done, CoopReason.SOON to soon, CoopReason.DREAM to dream), spots, troubles, causes, fixes)

val COOP_ITEMS: Map<String, CoopItem> = mapOf(
    // ── 장소 · 탐험 ──
    "우리집" to item("bg_home",
        "집에서 놀다가 무슨 일이 있었어?", "집에 가면 무슨 일이 생길까?", "밤에 집에서 어떤 신기한 일이 생길까?",
        listOf("거실", "내 방", "부엌"), listOf("장난감이 사라진 일", "우유를 쏟은 일", "동생이 운 일"),
        listOf("바빠서", "정리를 안 해서", "실수로"), listOf("같이 찾아보기", "깨끗이 닦기", "꼭 안아 주기")),
    "학교" to item("bg_school",
        "학교에서 무슨 일이 있었어?", "학교에 가면 무슨 일이 생길까?", "학교에서 어떤 신기한 일이 생길까?",
        listOf("교실", "운동장", "도서관"), listOf("친구랑 다툰 일", "숙제를 잊은 일", "새 친구를 만난 일"),
        listOf("처음이라서", "급해서", "깜빡해서"), listOf("먼저 사과하기", "선생님께 말하기", "같이 놀기")),
    "놀이공원" to item("bg_themepark",
        "놀이기구를 타다가 무슨 일이 있었어?", "놀이기구를 타면 무슨 일이 생길까?", "놀이기구가 살아나면 어떤 일이 생길까?",
        listOf("회전목마", "롤러코스터", "대관람차"), listOf("줄이 아주 긴 일", "풍선을 놓친 일", "무서운 놀이기구"),
        listOf("사람이 많아서", "바람이 불어서", "키가 작아서"), listOf("다른 놀이기구 타기", "새 풍선 받기", "손 꼭 잡고 타기")),
    "아쿠아리움" to item("bg_aquarium",
        "물고기를 보다가 무슨 일이 있었어?", "물고기를 보러 가면 무슨 일이 생길까?", "물고기가 말을 걸면 어떤 일이 생길까?",
        listOf("상어 수조", "해파리 방", "펭귄 마을"), listOf("상어가 다가온 일", "펭귄이 미끄러진 일", "물고기가 숨은 일"),
        listOf("배가 고파서", "깜짝 놀라서", "부끄러워서"), listOf("조용히 기다리기", "손 흔들어 주기", "사육사에게 알리기")),
    "동물원" to item("bg_zoo",
        "동물을 보다가 무슨 일이 있었어?", "동물을 보러 가면 무슨 일이 생길까?", "동물들이 우리 밖으로 나오면 어떤 일이 생길까?",
        listOf("사자 우리", "기린 마당", "원숭이 산"), listOf("원숭이가 장난친 일", "사자가 크게 운 일", "기린이 밥 먹는 모습"),
        listOf("배가 고파서", "심심해서", "깜짝 놀라서"), listOf("멀리서 지켜보기", "사육사에게 알리기", "손 흔들어 주기")),
    // ── 직업 · 임무 ──
    "소방관" to item("bg_firestation",
        "거기서 불 끄는 연습을 하다가 무슨 일이 있었어?", "거기서 불이 나면 소방관은 무슨 일을 할까?", "네가 소방관이 되어 출동하면 어떤 일이 생길까?",
        listOf("소방차 차고", "출동 준비실", "훈련장"), listOf("큰불이 난 집", "나무 위 고양이", "연기가 가득한 건물"),
        listOf("불장난을 해서", "전선이 낡아서", "고양이가 올라가서"), listOf("물을 뿌려서 끄기", "사다리 타고 구하기", "모두 데리고 나오기")),
    "의사" to item("bg_hospital",
        "거기서 의사 선생님이 무슨 일을 하셨어?", "거기서 아픈 친구를 만나면 의사는 무슨 일을 할까?", "네가 의사가 되면 병원에서 어떤 일이 생길까?",
        listOf("진료실", "주사실", "약국"), listOf("열이 나는 친구", "다리를 다친 친구", "배가 아픈 친구"),
        listOf("감기에 걸려서", "뛰다가 넘어져서", "찬 걸 많이 먹어서"), listOf("약 먹고 푹 쉬기", "붕대 감아 주기", "따뜻하게 안아 주기")),
    "요리사" to item("bg_kitchen",
        "거기서 요리를 해 보다가 무슨 일이 있었어?", "거기서 요리사는 무슨 음식을 만들까?", "네가 요리사가 되면 주방에서 어떤 일이 생길까?",
        listOf("주방", "식당 홀", "재료 창고"), listOf("냄비가 넘친 일", "재료가 모자란 일", "손님이 잔뜩 온 일"),
        listOf("불이 너무 세서", "장을 못 봐서", "음식이 맛있어서"), listOf("불을 줄이기", "다른 재료로 바꾸기", "다 같이 힘 모으기")),
    "경찰관" to item("bg_police",
        "거기서 경찰관 체험을 하다가 무슨 일이 있었어?", "거기서 경찰관은 무슨 일을 할까?", "네가 경찰관이 되면 길에서 어떤 일이 생길까?",
        listOf("경찰서", "순찰차", "횡단보도"), listOf("길 잃은 아이", "잃어버린 지갑", "꽉 막힌 찻길"),
        listOf("사람이 많아서", "급하게 가다가", "신호를 못 봐서"), listOf("엄마를 찾아 주기", "주인에게 돌려주기", "호루라기 불기")),
    "우주비행사" to item("bg_space",
        "거기서 우주 체험을 하다가 무슨 일이 있었어?", "우주에 가면 우주비행사는 무슨 일을 할까?", "네가 우주에 가면 어떤 신기한 일이 생길까?",
        listOf("우주선 안", "달 표면", "우주 정거장"), listOf("둥둥 떠다니는 물건", "고장 난 우주선", "처음 보는 외계 친구"),
        listOf("중력이 없어서", "돌멩이에 부딪혀서", "길을 잃어서"), listOf("끈으로 묶기", "다 같이 고치기", "인사하고 친구 되기")),
    // ── 스포츠 · 도전 ──
    "축구" to item("bg_soccer",
        "축구공을 차다가 무슨 일이 있었어?", "축구공을 처음 차면 무슨 일이 생길까?", "축구 경기에서 어떤 멋진 일이 생길까?",
        listOf("운동장", "골대 앞", "벤치"), listOf("공이 골대를 맞은 일", "넘어진 일", "골을 넣은 일"),
        listOf("너무 세게 차서", "잔디가 미끄러워서", "열심히 연습해서"), listOf("다시 일어나기", "친구에게 패스하기", "다 같이 기뻐하기")),
    "농구" to item("bg_basketball",
        "농구공을 던지다가 무슨 일이 있었어?", "농구공을 처음 던지면 무슨 일이 생길까?", "농구 경기에서 어떤 멋진 일이 생길까?",
        listOf("농구장", "골대 밑", "벤치"), listOf("공이 안 들어간 일", "공을 놓친 일", "슛이 쏙 들어간 일"),
        listOf("골대가 높아서", "공이 미끄러워서", "연습을 많이 해서"), listOf("다시 던지기", "친구에게 패스하기", "힘차게 박수 치기")),
    "야구" to item("bg_baseball",
        "방망이를 휘두르다가 무슨 일이 있었어?", "방망이를 처음 잡으면 무슨 일이 생길까?", "야구 경기에서 어떤 멋진 일이 생길까?",
        listOf("타석", "외야", "더그아웃"), listOf("헛스윙한 일", "공이 멀리 날아간 일", "공을 잡은 일"),
        listOf("공이 빨라서", "힘껏 휘둘러서", "눈을 크게 떠서"), listOf("다시 휘두르기", "힘껏 달리기", "하이파이브하기")),
    "수영" to item("bg_pool",
        "물에 들어가서 무슨 일이 있었어?", "물에 처음 들어가면 무슨 일이 생길까?", "물속에서 어떤 신기한 일이 생길까?",
        listOf("얕은 물", "깊은 물", "물 미끄럼틀"), listOf("물을 먹은 일", "물에 둥둥 뜬 일", "물안경을 잃어버린 일"),
        listOf("물이 차가워서", "숨을 참아서", "발차기를 해서"), listOf("킥판 잡기", "선생님 손잡기", "천천히 숨쉬기")),
    "태권도" to item("bg_taekwondo",
        "발차기를 하다가 무슨 일이 있었어?", "도장에 처음 가면 무슨 일이 생길까?", "태권도 대회에서 어떤 멋진 일이 생길까?",
        listOf("도장", "매트 위", "거울 앞"), listOf("송판을 깬 일", "넘어진 일", "띠를 받은 일"),
        listOf("힘껏 차서", "다리가 꼬여서", "열심히 연습해서"), listOf("다시 일어나기", "크게 기합 넣기", "인사하고 마무리하기")),
)

/** 목록에 있는 요소의 재료, 직접 쓴 요소면 null */
fun coopItem(name: String): CoopItem? = COOP_ITEMS[name.trim()]

/** 펠트 그림 이름 (ComfyUI · tools/gen_coop.py) — 없으면 부르는 쪽이 이모지로 대신한다 */
fun coopKindArt(k: CoopKind) = "coop_kind_${k.key}"
fun coopReasonArt(r: CoopReason) = "coop_why_${r.key}"
const val COOP_CUSTOM_ART = "coop_el_custom"
val COOP_ITEM_ART = mapOf(
    "우리집" to "coop_el_home", "학교" to "coop_el_school", "놀이공원" to "coop_el_park", "아쿠아리움" to "coop_el_aquarium", "동물원" to "coop_el_zoo",
    "소방관" to "coop_el_firefighter", "의사" to "coop_el_doctor", "요리사" to "coop_el_chef", "경찰관" to "coop_el_police", "우주비행사" to "coop_el_astronaut",
    "축구" to "coop_el_soccer", "농구" to "coop_el_basketball", "야구" to "coop_el_baseball", "수영" to "coop_el_swim", "태권도" to "coop_el_taekwondo",
)

const val COOP_NAME_MAX = 10

/** 직접 쓴 요소 이름 — 다듬은 값, 쓸 수 없으면 null. 한글 · 영문 · 숫자 · 띄어쓰기, 10자까지 */
fun cleanCoopName(raw: String): String? {
    val v = raw.trim().replace(Regex("\\s+"), " ")
    if (v.isEmpty() || v.length > COOP_NAME_MAX) return null
    return v.takeIf { Regex("^[가-힣a-zA-Z0-9 ]+$").matches(it) }
}

/** 저장된 이유 키 → [CoopReason]. 없거나 모르는 값이면 null (상상으로 묻는다) */
fun CoopPick.reasonOrNull(): CoopReason? = reason?.let { r -> CoopReason.entries.firstOrNull { it.key == r } }

/**
 * 고른 이야기에서 오또가 기승전결 네 자리에 물을 질문 — place · problem · cause · solution 순.
 * **LLM이 붙기 전의 대역이다.** 실제로는 요소 · 이유 · 그때까지 찬 칸을 프롬프트에 넣어 매 걸음 만든다.
 * 모르는 템플릿이면 빈 목록 — 그때는 앱 질문을 그대로 묻는다.
 */
fun CoopPick.templateQuestions(): List<String> = coopKind(kind)?.questions(name, reasonOrNull()).orEmpty()
