package com.example.finalproject_demo.ui

/**
 * 부모가 넣은 질문을 **귀띔만** 한다. 막지도, 점수를 매기지도 않는다 (부모협업모드_설계.md §1-⑥).
 *
 * 규칙은 `부모협업모드_설계.md` §5-1의 다섯 가운데 코드로 볼 수 있는 셋이다.
 * 나머지 둘(선택지를 먼저 · 선택지 순서 섞기)은 질문 한 줄만 보고는 알 수 없다.
 *
 * ⚠️ 이 모드가 파는 것이 "아이에게 어떻게 물어야 하는지"(설계 §7)다. 그래서 LLM 없이도 오늘 되는 만큼은 한다.
 * ⚠️ 틀리게 걸려도 해는 없다 — 귀띔이고, 질문은 그대로 저장된다.
 */
data class QuestionHint(val why: String, val example: String)

private val WH_WORDS = listOf(
    "뭐", "무엇", "무슨", "누구", "누가", "누굴", "어디", "왜", "어떻게", "어떤", "언제", "몇",
)

/** "말해 줄래?" 처럼 의문사가 없어도 열린 질문인 것들 — 예/아니오로 안 끝난다 */
private val OPEN_ENDINGS = listOf("말해", "얘기해", "이야기해", "들려", "알려", "설명해")

private fun countWh(q: String): Int = WH_WORDS.count { it in q }

/**
 * 귀띔 한 개, 없으면 null. 우선순위: '언제' → 의문사 둘 → 예/아니오.
 * 빈 줄은 아무 말도 안 한다 — 아직 쓰는 중이다.
 */
fun questionHint(raw: String): QuestionHint? {
    val q = raw.trim()
    if (q.isEmpty()) return null

    if ("언제" in q) return QuestionHint(
        why = "'언제'는 아직 어려워요 — 시간 표현이 안 서요",
        example = "'어디서' 나 '누구랑' 으로 바꿔 보세요",
    )

    val wh = countWh(q)
    if (wh >= 2) return QuestionHint(
        why = "한 번에 하나만 물어보세요 — 아이는 마지막 것만 답해요",
        example = "두 질문으로 나눠서 넣어 보세요",
    )

    val open = OPEN_ENDINGS.any { it in q }
    if (wh == 0 && !open) return QuestionHint(
        why = "예/아니오로 끝나기 쉬워요",
        example = "'뭐가' '누구랑' 같은 말을 넣으면 아이가 더 길게 답해요 — 오늘 제일 재밌었던 게 뭐였어?",
    )

    return null
}
