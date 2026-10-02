package com.example.finalproject_demo.demo

/*
 * 같이 만들기 — **진짜 마이크 답의 수준 신호** (10-02 · 협업 질문 업그레이드 설계 A · B).
 *
 * 공용 판정(`StoryBank.kt` judge)은 답에 달린 [Answer] 의 신호만 읽는다. 대본 답에는 신호가 달려 오지만
 * 진짜 마이크 답은 글자뿐이라(`answer == null`) **언제나 「내림」으로 셌다** — 협업에서는 수준이 내려가기만 했다.
 * 판정 코드는 고치지 않고(공용), 협업 쪽에서 답에 신호를 달아 넘긴다.
 *
 * - 서버가 켜져 있으면 `/turn` 판정의 `s1_reason` · `s2_addition` · `emotion` — 그림일기 · 동화와 같은 옮김(`StoryTurn.kt`)
 * - 서버가 없으면 앱 규칙 [설계] (근거 문서 §4)
 *   S1  「왜」 질문에 이유가 담긴 답(잇는 말이 없어도 인정) · 「어떻게」 질문에 하는 일이 담긴 답
 *   S2  묻지 않은 이야기 요소 — 다시 해 봄(시도) · 그래서 어떻게 됨(결과)
 *   A1  잇는 말(그래서 · 그다음에 · -고 · -서) — 보조
 * - **선택지 질문에 한 답은 신호를 달지 않는다** — 고른 것일 뿐이다 (근거 #26)
 * - 「몰라」 · 「응」은 신호 없음 — 판정이 짧은 답으로 센다(한 번으로는 수준이 안 바뀐다)
 *
 * 표시: [COOP_LIVE_LV] — 이 답이 대본 답이 아니라 진짜 마이크 답이라는 것을 협업 쪽([isLiveSpeech])이 알아본다.
 */

/** 진짜 마이크 답에 단 신호라는 표시 — 대본 답의 수준(1~3)과 겹치지 않는다 */
internal const val COOP_LIVE_LV = 0

private val TRY = Regex("다시|또 해|해 봤|해봤|해 볼|한 번 더")
private val RESULT = Regex("그래서|됐어|돼서|끝났|성공|해냈|나았|찾았|고쳤")
private val LINKERS = Regex("그래서|그다음에|그리고|그러니까|[가-힣](고|서) ")

/** 선택지를 먼저 말한 질문인가 — 「사자 우리, 기린 마당, 원숭이 산. 어디가 좋았어?」 */
internal fun isChoiceQuestion(question: String): Boolean = ". " in question && "," in question.substringBeforeLast(". ")

/**
 * 진짜 마이크 답에 달 신호. [question] 은 오또가 실제로 물은 말, [server] 는 `/turn` 판정(없으면 null → 앱 규칙)
 */
internal fun coopSignals(text: String, question: String, server: com.example.finalproject_demo.net.Server.Verdict?): Answer {
    val t = text.trim()
    val none = Answer(text = t, lv = COOP_LIVE_LV)
    if (t.isEmpty() || isNonAnswer(t) || dontKnow(t)) return none
    if (isChoiceQuestion(question)) return none
    if (server != null) return Answer(
        text = t, reason = server.s1Reason, el = if (server.s2Addition) setOf("추가") else emptySet(),
        emo = server.emotion.orEmpty(), lv = COOP_LIVE_LV,
    )
    val why = "왜" in question || "뭐 때문" in question
    val how = "어떻게" in question
    // 「어떻게」에는 하는 일(서술어)이 담겨야 한다 — 이름 하나(「소방차」)는 방법이 아니다
    val s1 = why || (how && coopNameFrom(t, CoopRole.ANY) == null)
    val el = buildSet {
        if (TRY.containsMatchIn(t)) add("시도")
        if (RESULT.containsMatchIn(t) && !how) add("결과")
    }
    return Answer(text = t, reason = s1, el = el, con = LINKERS.containsMatchIn("$t "), lv = COOP_LIVE_LV)
}
