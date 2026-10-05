package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.coopItem

/**
 * 협업에서 오또가 아이 답에 하는 말 — 서버의 받아주기(ack) + 되돌려주기(expand). 동화 모드와 같게 쓴다 (10-05).
 *
 * 전에는 협업이 `/turn` 의 대사 중 다음 질문만 쓰고 ack · expand 를 버렸다. 그래서 오또의 반응이 늘 앱의 짧은 한마디
 * (`coopAck` — 아이 말에서 뗀 이름 하나)뿐이었다. 서버 대사 프롬프트(`eval/line_prompt.md`)는 이미 협업 시제를 안다 —
 * 다녀왔어요는 지어 얹지 않고, 곧 해요는 기대하는 말, 좋아해요는 동화처럼.
 *
 * 쓸 수 없는 조각은 버린다: 비었거나 · 거친 말이 섞였거나 · 묻는 말이거나(질문은 다음 걸음이 한다) ·
 * 이름 자리표시자가 남았거나 · 너무 긴 것. 둘 다 못 쓰면 null — 앱의 [coopAck] 로 돌아간다.
 */
internal fun coopServerReaction(line: Server.Line?): String? {
    if (line == null) return null
    return listOfNotNull(line.ack, line.expand).map(String::trim).filter(::usableReaction)
        .joinToString(" ").takeIf(String::isNotBlank)
}

/** 받아주기 8어절 · 되돌려주기 한 문장 — 그보다 길면 서버가 규칙을 어긴 것이다 */
private const val REACTION_MAX = 40

private fun usableReaction(text: String): Boolean =
    text.isNotBlank() && text.length <= REACTION_MAX && '?' !in text && '{' !in text && !hasRoughWord(text)

/**
 * `/story` 에 보내는 무대 — 고른 요소 안의 자리(`COOP_ITEMS` 의 spots, 「소방차 차고 · 출동 준비실 · 훈련장」) (#113).
 * 서버 프롬프트는 이것을 쪽 꾸밈과 keywords 에만 쓰고 사건을 만들지 않는다. 직접 쓴 요소 · 고른 이야기가 없으면 null.
 * 요소의 선택지 후보(troubles · causes · fixes)는 보내지 않는다 — 아이가 말한 것이 아니다.
 */
internal fun DemoState.coopStage(): List<String>? = bookPick?.let { coopItem(it.name) }?.spots?.takeIf(List<String>::isNotEmpty)
