package com.example.finalproject_demo.ui

import com.example.finalproject_demo.demo.eul
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.ga

/**
 * 협업 탭 템플릿 — **장소 · 직업 · 스포츠 → 요소 하나 → 고른 이유** (09-29 · 엔드픽처 「같이 만들기」).
 *
 * 어른이 부모 모드에서 미리 고르면 질문 네 줄이 채워지고, 아이가 오또의 방 소파를 누르면 오또가 이 순서로 묻는다.
 *
 * ⚠️ 네 줄의 순서는 place → problem → cause → solution 이다.
 *    `CoopScenes.kt` 의 `COOP_PART_SLOTS` 가 입력 줄 0~3을 이 순서로 칸에 짝짓는다 — 어긋나면 답이 다른 칸에 들어간다.
 *    그래서 템플릿마다 이야기 모양(탐험 · 임무 · 도전)이 달라도 **묻는 자리**는 같다: 어디 → 무슨 일 → 왜 → 어떻게 됐나.
 * - **고른 이유**가 같은 요소의 질문을 바꾼다 — 다녀왔으면 기억(과거), 곧 하면 기대(미래), 좋아하면 상상.
 *   다른 모드와 가르는 장치가 이것이다. 이유를 안 고르면 상상(`dream`)으로 묻는다
 * - 요소는 목록에서 고르거나 **직접 쓴다** — 질문 틀이 이름만 끼워 넣으므로 어떤 이름이든 같은 틀로 묻는다
 * - 모든 줄이 [questionHint] 에 안 걸려야 한다 — 우리 예시가 우리 귀띔에 걸리면 모순이다
 * - 아이 이름은 넣지 않는다. 질문 글이 부모 발화로 기록되므로 실명이 섞이지 않게 한다 (규칙 6)
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
    /** 네 줄 — place · problem · cause · solution. 이유가 없으면 상상으로 */
    fun questions(name: String, reason: CoopReason? = null): List<String> = build(name.trim(), reason ?: CoopReason.DREAM)
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
            CoopReason.DREAM -> listOf("네가 $x${ga(x)} 되면 어디서 일할까?", "거기서 어떤 일이 생길까?", "왜 그런 일이 생겼을까?", "그래서 어떻게 해결했을까?")
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

/** 카드를 골랐을 때 — 앞 네 줄을 템플릿으로 갈고, 5번째부터의 자유 질문은 그대로 둔다 */
fun fillFromTemplate(current: List<String>, questions: List<String>): List<String> =
    questions + current.drop(questions.size)

/** 요소 · 이유를 바꿨을 때 — 아직 템플릿 그대로인 줄만 새 값으로. 부모가 손으로 고친 줄은 안 건드린다 */
fun refillBlank(current: List<String>, old: List<String>, new: List<String>): List<String> =
    current.mapIndexed { i, q -> if (i < old.size && q == old[i]) new[i] else q }
