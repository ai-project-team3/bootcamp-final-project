package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.ui.reasonOrNull

/*
 * 같이 만들기 — 부모 리포트의 말 (10-03 실기기).
 *
 * 「곧 체험해요」로 고른 소방관 이야기인데 리포트가 「오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요」,
 * 「어른이 넣어 둔 질문에 한 답」, 놀이 카드 「오늘 ○○에서 제일 재밌었던 게 뭐였어?」라고 적었다 —
 * 아직 안 간 곳을 다녀온 것처럼, 오또가 물은 템플릿 질문을 부모가 넣은 것처럼 보였다.
 * 그래서 고른 이유(다녀왔어요 · 곧 해요 · 좋아해요)와 부모 질문을 썼는지로 말을 가른다.
 * 리포트를 열 때는 `coopPick` 이 이미 비어 있다(`clearParentQuestions`) — 이야기를 시작할 때 남겨 둔 것을 읽는다.
 */
data class CoopReportCopy(
    /** 책 제목 아래 한 줄 — 무엇으로 지은 책인가 */
    val madeFrom: String,
    /** 제목 줄의 「누구 질문으로 · 」 */
    val who: String,
    /** 아이 답 모음 칸 제목 · 부제 */
    val askedTitle: String,
    val askedSub: String,
    /** 다음에 넣어 볼 질문 하나와 그 까닭 */
    val nextQuestion: String,
    val nextWhy: String,
    /** 오늘 이야기로 해 볼 놀이 질문 3장 — null 이면 일기 모드 것을 그대로 쓴다 */
    val playCards: List<String>?,
)

fun DemoState.coopReportCopy(): CoopReportCopy {
    val pick = coopStoryPick
    val parent = coopParentUsed > 0
    val defaultWhy = "“재밌었어?” 처럼 예/아니오로 끝나는 질문 대신 이렇게 넣어 보세요. 의문사는 하나만, 선택지는 질문보다 먼저."
    if (pick == null) return CoopReportCopy(
        madeFrom = "오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요",
        who = "어른 질문으로 · ",
        askedTitle = "어른이 넣어 둔 질문에 한 답",
        askedSub = "넣은 순서대로 · 아이가 말한 것만 따옴표",
        nextQuestion = "오늘 제일 재밌었던 거 하나만 말해 줄래?",
        nextWhy = defaultWhy,
        playCards = null,
    )
    val reason = pick.reasonOrNull() ?: CoopReason.DREAM
    val label = pick.reasonOrNull()?.let { coopKind(pick.kind)?.reasonLabels?.get(it) }
    val name = pick.name.trim()
    val story = "‘$name’${label?.let { " · $it" } ?: ""}"
    return CoopReportCopy(
        madeFrom = when (reason) {
            CoopReason.DONE -> "$story — 있었던 일로 지은 책이에요"
            CoopReason.SOON -> "$story — 앞으로 할 일을 미리 그려 본 책이에요"
            CoopReason.DREAM -> "$story — 상상으로 지은 책이에요"
        } + if (parent) " · 넣어 둔 질문도 함께 물었어요" else "",
        who = if (parent) "고른 이야기 · 어른 질문으로 · " else "고른 이야기로 · ",
        askedTitle = if (parent) "고른 이야기 질문 · 넣어 둔 질문에 한 답" else "고른 이야기 질문에 한 답",
        askedSub = "물은 순서대로 · 아이가 말한 것만 따옴표",
        nextQuestion = when (reason) {
            CoopReason.DONE -> "오늘 제일 재밌었던 거 하나만 말해 줄래?"
            CoopReason.SOON -> "$name 이야기에서 제일 궁금한 게 뭐야?"
            CoopReason.DREAM -> "이 이야기 다음엔 무슨 일이 생길까?"
        },
        nextWhy = when (reason) {
            CoopReason.DONE -> defaultWhy
            CoopReason.SOON -> "아직 안 해 본 일이라 “뭐 했어?” 대신 “~할까?” “~궁금해?” 처럼 앞으로의 말로 물어보세요. 다녀온 뒤 「${coopKind(pick.kind)?.reasonLabels?.get(CoopReason.DONE) ?: "다녀왔어요"}」로 한 번 더 만들면 생각한 것과 견줄 수 있어요."
            CoopReason.DREAM -> "상상 이야기라 정답이 없어요. “~했을까?” “~할까?” 처럼 열어 두면 아이가 더 길게 지어요."
        },
        playCards = when (reason) {
            CoopReason.DONE -> null
            CoopReason.SOON -> listOf(
                "\"$name 이야기에서 제일 해 보고 싶은 게 뭐야?\"",
                "\"거기서 누구를 만날 것 같아?\"",
                "\"다녀오면 오늘 지은 이야기랑 뭐가 다를까?\"",
            )
            CoopReason.DREAM -> listOf(
                "\"$name 이야기에서 뭐가 제일 좋아?\"",
                "\"이 이야기 다음엔 무슨 일이 생길까?\"",
                "\"네가 이야기 속에 들어가면 뭐 할까?\"",
            )
        },
    )
}
