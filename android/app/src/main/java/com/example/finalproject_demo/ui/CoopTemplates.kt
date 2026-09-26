package com.example.finalproject_demo.ui

import com.example.finalproject_demo.demo.eun

/**
 * 협업 탭 템플릿 카드 — 탭하면 질문 네 자리가 한 번에 채워진다 (docs/주말_데모_뼈대.md 「B. 협업」).
 *
 * ⚠️ 네 줄의 순서는 place → problem → cause → solution 이다.
 *    `CoopScenes.kt` 의 `COOP_PART_SLOTS` 가 입력 줄 0~3을 이 순서로 칸에 짝짓는다 — 어긋나면 답이 다른 칸에 들어간다.
 * - 커스텀은 **빈칸 하나까지** (멘토: 「약간」 — 장소 하나 · 부모 호칭 정도)
 * - 문구는 초안이다. 모든 줄이 [questionHint] 에 안 걸려야 한다 — 우리 예시가 우리 귀띔에 걸리면 모순이다
 * - 아이 이름은 넣지 않는다 ("너는"). 질문 글이 부모 발화로 기록되므로 실명이 섞이지 않게 한다 (규칙 6)
 */
data class CoopBlank(
    val label: String,
    val default: String,
    val choices: List<String>,
    /** 칩 말고 글자로도 받나 — 장소는 받고, 호칭·기관은 고르기만 */
    val free: Boolean,
)

class CoopTemplate(
    val key: String,
    val emoji: String,
    val title: String,
    val blank: CoopBlank?,
    private val build: (String) -> List<String>,
) {
    /** 네 줄 — place · problem · cause · solution. 빈칸이 비면 기본값 */
    fun questions(value: String? = null): List<String> =
        build(value?.trim()?.takeIf { it.isNotEmpty() } ?: blank?.default ?: "")

    /** 카드에 보여 줄 제목 — 빈칸 값을 넣어서 */
    fun label(value: String? = null): String =
        blank?.let { title.replace("{}", value?.trim()?.takeIf { v -> v.isNotEmpty() } ?: it.default) } ?: title
}

val COOP_TEMPLATES = listOf(
    CoopTemplate(
        "daycare", "🏫", "오늘 {}",
        CoopBlank("어디", "어린이집", listOf("어린이집", "유치원"), free = false),
    ) { v ->
        listOf(
            "오늘 ${v}에서 어디서 제일 많이 놀았어?",
            "거기서 무슨 일이 있었어?",
            "왜 그랬을까?",
            "그래서 어떻게 됐어?",
        )
    },
    CoopTemplate(
        "weekend", "🚗", "주말 {}",
        CoopBlank("장소", "할머니 집", listOf("할머니 집", "외할머니 집", "공원", "바닷가"), free = true),
    ) { v ->
        listOf(
            "${v}에 가서 어디에 있었어?",
            "${v}에서 뭐가 제일 재밌었어?",
            "왜 재밌었어?",
            "다음에 가면 뭐 하고 싶어?",
        )
    },
    CoopTemplate(
        "work", "💼", "{} 일",
        CoopBlank("누구", "엄마", listOf("엄마", "아빠"), free = false),
    ) { v ->
        listOf(
            "${v}${eun(v)} 어디서 일할까?",
            "거기서 무슨 일을 할까?",
            "왜 그 일을 할까?",
            "너는 어떤 일을 해 보고 싶어?",
        )
    },
    CoopTemplate("today", "☀️", "오늘 뭐 했니", null) {
        listOf(
            "오늘 어디 갔었어?",
            "거기서 무슨 일이 있었어?",
            "왜 그랬어?",
            "그래서 어떻게 됐어?",
        )
    },
)

/** 카드를 탭했을 때 — 앞 네 줄을 템플릿으로 갈고, 5번째부터의 자유 질문은 그대로 둔다 */
fun fillFromTemplate(current: List<String>, questions: List<String>): List<String> =
    questions + current.drop(questions.size)

/** 빈칸을 바꿨을 때 — 아직 템플릿 그대로인 줄만 새 값으로. 부모가 손으로 고친 줄은 안 건드린다 */
fun refillBlank(current: List<String>, old: List<String>, new: List<String>): List<String> =
    current.mapIndexed { i, q -> if (i < old.size && q == old[i]) new[i] else q }
