package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopItem
import com.example.finalproject_demo.ui.coopKind

/*
 * 같이 만들기 — **이어 받는 질문** (10-02 · 협업 질문 업그레이드 §5 · §6).
 *
 * 뼈대 네 자리(place → problem → cause → solution)의 순서와 칸은 그대로 두고, 2~4번째 자리의 첫 질문만
 * 아이가 앞에서 한 말(장소 · 사람 · 주인공)을 끼운 말로 바꾼다. 첫 자리(place)는 부모 화면에서 미리 본 템플릿 질문 그대로다.
 *
 * 수준(`Level` — 공용 판정 `StoryBank.kt` judge 가 옮긴다. 여기서는 읽기만)
 *   고르며 짓기  무엇 · 누구 + 선택지 먼저, 질문 마지막. 까닭 · 해결 칸은 선택지로 받는다
 *   이어 짓기    무엇 · 어디 + 쉬운 「뭐 했어?」 + 선택지를 붙인 「왜」
 *   까닭 짓기    「왜」 · 「어떻게」 열린 질문. 가정 질문(「네가 ○○였다면?」)은 이 수준 + 좋아해요(상상)에서만
 * 「왜」는 이야기 하나에 두 번까지 — 셋째부터는 「뭐 때문에」로 묻는다.
 *
 * 후보를 차례로 끼워 보고([coopFill]) 갈무리([coopGuard] · 이어 받기)를 통과한 첫 질문을 낸다.
 * 하나도 안 되면 null — 부르는 쪽이 템플릿 질문 그대로 묻는다. 서버를 기다리지 않는다(앱 안에서 바로 만든다).
 */

/** 「왜」 질문 상한 — 이야기 하나에 두 번 */
const val COOP_WHY_MAX = 2

/** 선택지 먼저 · 질문 마지막. 말로 줄 때는 순서를 섞는다 (근거 #10 · #27) */
private fun withChoices(options: List<String>, ask: String) = options.shuffled().joinToString(", ") + ". " + ask

/** 시제 셋 — 다녀왔어요 · 곧 해요 · 좋아해요(이유를 안 골랐으면 상상) */
private fun CoopReason.pick(done: String, soon: String, dream: String) = when (this) {
    CoopReason.DONE -> done; CoopReason.SOON -> soon; CoopReason.DREAM -> dream
}

/**
 * 이 자리의 이어 받기 질문 후보 — 앞이 먼저. 이름 자리가 비면 그 후보는 건너뛴다.
 * [template] 는 지금 이 자리에 나갈 템플릿 질문(「거기서 …」면 들은 곳을 끼워 쓴다), [rungs] 는 그 자리 사다리
 */
internal fun coopFollowUpCandidates(
    slot: String, level: Level, reason: CoopReason, pick: CoopPick?, whyAsked: Int, template: String?, rungs: List<String>,
): List<String> {
    val item = pick?.let { coopItem(it.name) }
    val ladderChoice = rungs.lastOrNull { ". " in it && "," in it }
    val whyOk = whyAsked < COOP_WHY_MAX
    return when (slot) {
        "problem" -> when (level) {
            Level.PICK -> listOf(reason.pick("{place:에서} 뭐 봤어?", "{place:에서} 뭘 볼까?", "{place:에서} 뭘 봤을까?"))
            else -> listOfNotNull(
                template?.takeIf { it.startsWith("거기서 ") }?.replaceFirst("거기서 ", "{place:에서} "),
                reason.pick("{place:에서} 무슨 일이 있었어?", "{place:에서} 무슨 일이 생길까?", "{place:에서} 무슨 일이 생겼을까?"),
            )
        }
        "cause" -> {
            val ask = if (whyOk) reason.pick("{thing:가} 왜 그랬을까?", "{thing:가} 왜 그럴까?", "{thing:가} 왜 그랬을까?")
                else reason.pick("{thing:가} 뭐 때문에 그랬을까?", "{thing:가} 뭐 때문에 그럴까?", "{thing:가} 뭐 때문에 그랬을까?")
            when (level) {
                Level.REASON -> listOf(ask)
                // 고르며 · 이어 짓기 — 선택지를 붙인 「왜」. 고른 요소의 까닭 선택지가 없으면 그 자리 사다리의 선택지 질문
                else -> listOfNotNull(item?.let { withChoices(it.causes, ask) }, ladderChoice)
            }
        }
        "solution" -> {
            val next = reason.pick("그다음에 뭐 했어?", "그다음에 뭐 할까?", "그다음에 뭐 했을까?")
            when (level) {
                Level.PICK -> listOfNotNull(item?.let { withChoices(it.fixes, next) }, ladderChoice)
                Level.CHAIN -> listOf(next)
                Level.REASON -> listOfNotNull(
                    // 가정 질문 — 까닭 짓기 + 상상에서만 (Scenes.kt 소크라틱 원칙과 같은 기준에 상상을 더한다)
                    if (reason == CoopReason.DREAM) hypothetical(pick) else null,
                    reason.pick("{thing:은} 어떻게 됐어?", "{thing:은} 어떻게 될까?", "{thing:은} 어떻게 됐을까?"),
                    reason.pick("그래서 어떻게 됐어?", "그래서 어떻게 될까?", "그래서 어떻게 됐을까?"),
                )
            }
        }
        else -> emptyList()
    }
}

/** 「네가 소방관이었다면 어떻게 했을까?」 — 직업이면 그 이름, 아니면 「거기 있었다면」 */
private fun hypothetical(pick: CoopPick?): String =
    if (pick != null && pick.kind == "job" && coopKind(pick.kind) != null) {
        val n = pick.name.trim()
        "네가 ${n}${if (bat(n)) "이었" else "였"}다면 어떻게 했을까?"
    } else "네가 거기 있었다면 어떻게 했을까?"

/**
 * 이어 받기 질문 하나 — 후보를 끼우고 갈무리를 거친 첫 질문. 없으면 null (템플릿 질문 그대로).
 * [heard] 는 앞 답에서 뗀 이름들(place · who · thing)
 */
internal fun coopFollowUp(
    slot: String, level: Level, reason: CoopReason, heard: Map<String, String>, pick: CoopPick?,
    whyAsked: Int, template: String?, rungs: List<String>,
): String? = coopFollowUpCandidates(slot, level, reason, pick, whyAsked, template, rungs).firstNotNullOfOrNull { c ->
    coopFill(c, heard)?.let { coopGuard(it, reason, CoopSource.HEARD).text }
}
