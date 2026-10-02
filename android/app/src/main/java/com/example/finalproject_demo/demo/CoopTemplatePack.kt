package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopItem
import com.example.finalproject_demo.ui.reasonOrNull
import com.example.finalproject_demo.ui.templateQuestions

/*
 * 고른 이야기(템플릿)에 맞춘 한 걸음 — 쉬운 질문 사다리 · 시연 답 · 마스코트 채움 (10-01 사용자 요청).
 *
 * 뼈대 네 자리의 첫 질문만 템플릿 말로 바꾸면 그 뒤가 일기 그대로 남는다 — "소방관은 어디서 일할까?" 에
 * 시연 답이 "어린이집!", 사다리가 "어린이집, 놀이터, 할머니 집. 오늘은 어디 있었어?" 로 나왔다.
 * 그래서 네 자리에서는 이 셋을 **한 묶음으로** 갈아 끼운다 (`DiaryScenes.kt` `askDiaryStep` 이 [coopPartPack] 을 본다).
 *
 * **LLM이 붙기 전의 대역이다.** 실제로는 요소 · 이유 · 찬 칸을 프롬프트에 넣어 질문을 만들고, 답은 아이가 한다.
 * 여기 답은 시연 서랍의 🎲 · 🗣 와 「아이 흉내」가 고르는 더미다.
 *
 * 규칙
 * - 사다리 첫 칸은 언제나 템플릿 질문([templateQuestions]) 그대로 — 부모 화면 미리 보기와 같은 말이다
 * - 쉬운 질문은 **선택지 먼저, 질문 마지막** (부록 §5-1). 의문사는 하나
 * - 답은 요소 이름만 끼워 넣는다 — 목록에 없는 이름(직접 쓰기)도 같은 틀로 말이 되게 일반적인 말로 쓴다
 * - 시제는 고른 이유를 따른다: 다녀왔어요 = 지난 일, 곧 해요 = 앞으로 할 일, 좋아해요 = 상상 이야기(책에서는 지난 일처럼)
 * - 수준 표시는 기존 걸음과 같다: 까닭을 말하면 `reason`, 이어 말하면 `con`, 이야기 요소는 `el`
 * - **마스코트는 지어내지 않는다 — 다녀왔어요 · 곧 해요는 아이의 실제 일**이라 「아직 못 들은 ○○」로만 채운다 (일기 §3-2와 같은 까닭).
 *   좋아해요(상상)만 마스코트가 이야기를 지어 채운다
 */

/**
 * 한 걸음 묶음 — [rungs] 는 사다리(뼈대 자리면 첫 칸이 템플릿 질문), [answers] 는 시연 답,
 * [mascot] 은 사다리가 다 떨어졌을 때. 꼬리질문은 마스코트가 지어내지 않으므로 null 이다(걸음 정의와 같다)
 */
class CoopPartPack(val rungs: List<String>, val answers: List<Answer>, val mascot: Answer?)

/**
 * 협업에서 고른 이야기가 있으면 그 걸음의 묶음, 아니면 null (걸음 정의 그대로 쓴다).
 * 뼈대 네 자리는 템플릿 · 요소별 묶음, 꼬리질문은 **고른 이유의 시제**에 맞춘 묶음이다 —
 * 꼬리질문 걸음은 「오늘 하루」를 전제로 쓰여 있어(“집에 와서는 뭐 했어?”) 곧 해요 · 상상 이야기에는 맞지 않는다.
 */
fun DemoState.coopPartPack(step: DiaryStep): CoopPartPack? = rawPartPack(step)?.let { p ->
    // 앞에서 아이가 말한 곳을 「거기」 자리에 끼운다 — 「큰 건물!」 다음엔 「큰 건물에서 무슨 일을 할까?」 (10-01)
    val here = heardPlace() ?: return@let p
    CoopPartPack(p.rungs.map { it.here(here) }, p.answers, p.mascot)
}

/**
 * 아이가 말한 곳 — 질문에 끼울 수 있을 만큼 **이름처럼 짧은 말**일 때만. 문장(「큰 건물에서 일할 것 같아」)이거나
 * 마스코트가 「아직 못 들은 …」으로 메운 칸이면 null — 그때는 「거기」를 그대로 둔다
 */
internal fun DemoState.heardPlace(): String? {
    val p = place?.trim()?.removeSuffix("에서")?.removeSuffix("에")?.trim() ?: return null
    if (p.isEmpty() || p.length > 12 || p.split(" ").size > 3 || p.startsWith("아직")) return null
    if (PLACE_NOT_A_NAME.any { p.endsWith(it) }) return null
    return p
}

/** 말끝이 이러면 이름이 아니라 문장이다 (갔어 · 있어요 · 했다 · 몰라 …) */
private val PLACE_NOT_A_NAME = listOf("어", "요", "다", "야", "지", "까", "해", "서", "고", "니", "라")

/** 「거기서」 → 「큰 건물에서」 · 「거기 」 → 「큰 건물에 」 */
internal fun String.here(place: String): String = replace("거기서", "${place}에서").replace("거기 ", "${place}에 ")

private fun DemoState.rawPartPack(step: DiaryStep): CoopPartPack? {
    if (!isCoop) return null
    val pick = coopPick ?: return null
    val reason = pick.reasonOrNull() ?: CoopReason.DREAM
    if (!step.required) return tailPack(step.bookKey, reason, childName, friendCallName)
    val part = listOf("place", "problem", "cause", "solution").indexOf(step.slot).takeIf { it >= 0 } ?: return null
    val first = pick.templateQuestions().getOrNull(part) ?: return null
    val body = when (pick.kind) {
        "place" -> placePart(pick.name.trim(), reason, part)
        "job" -> jobPart(pick.name.trim(), reason, part)
        "sport" -> sportPart(pick.name.trim(), reason, part)
        else -> null
    } ?: return null
    return CoopPartPack(listOf(first) + body.easier.withItemChoices(pick.name, part), body.answers, body.mascot)
}

/**
 * 목록에 있는 요소면 사다리 마지막 칸(선택지 질문)의 **선택지만** 그 요소다운 말로 바꾼다 — 묻는 말은 그대로 (10-02).
 * 「입구 · 한가운데 · 맨 안쪽」 대신 동물원이면 「사자 우리 · 기린 마당 · 원숭이 산」. 직접 쓴 요소는 그대로 둔다
 */
private fun List<String>.withItemChoices(name: String, part: Int): List<String> {
    val item = coopItem(name) ?: return this
    val options = when (part) { 0 -> item.spots; 1 -> item.troubles; 2 -> item.causes; else -> item.fixes }
    val last = lastOrNull() ?: return this
    val ask = last.substringAfterLast(". ", "").ifBlank { return this }
    return dropLast(1) + choices(*options.toTypedArray(), ask = ask)
}

private class Body(val easier: List<String>, val answers: List<Answer>, val mascot: Answer)

/** `칸 값|책 문장` 꼴의 시연 답 */
private fun a(
    text: String, slot: String, line: String,
    lv: Int = 2, reason: Boolean = false, el: Set<String> = emptySet(), con: Boolean = false, emo: String = "",
) = Answer(text = text, value = "$slot|$line", reason = reason, el = el, con = con, lv = lv, emo = emo)

private fun dunno() = Answer("몰라.", "", lv = 1)

/** 선택지 먼저 · 질문 마지막. 순서는 매번 섞는다 (부록 §5-1) */
private fun choices(vararg c: String, ask: String) = c.toList().shuffled().joinToString(", ") + ". " + ask

// 다녀왔어요 · 곧 해요는 아이의 실제 일 — 지어내지 않는다
private fun notHeard(what: String, line: String) = Answer("아직 못 들은 $what", "아직 못 들은 $what|$line")

// ── 장소 · 탐험 이야기 ─────────────────────────────────────────

private fun placePart(x: String, r: CoopReason, part: Int): Body? = when (part) {
    0 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("${x}에 들어가자마자 뭐가 보였어?", choices("입구", "한가운데", "맨 안쪽", ask = "어디가 좋았어?")),
            listOf(
                a("입구!", "$x 입구", "$x 입구가 제일 좋았어요", lv = 1),
                a("맨 안쪽!", "$x 맨 안쪽", "$x 맨 안쪽이 제일 좋았어요", lv = 1),
                a("한가운데 넓은 데. 거기서 뛰었어.", "$x 한가운데", "$x 한가운데 넓은 곳에서 신나게 뛰었어요", con = true, lv = 2),
                a("제일 높은 데. 거기 올라가니까 다 보였어.", "$x 제일 높은 곳", "${x}에서 제일 높은 곳에 올라가니 다 보였어요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("맨 안쪽. 엄마가 거기 재밌다고 해서 가 봤어.", "$x 맨 안쪽", "엄마가 재밌다고 해서 $x 맨 안쪽까지 가 봤어요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            Answer(x, "$x|${x}에 다녀왔어요"),
        )
        CoopReason.SOON -> Body(
            listOf("${x}에 가면 뭐부터 보고 싶어?", choices("입구", "한가운데", "맨 안쪽", ask = "어디부터 갈까?")),
            listOf(
                a("입구부터!", "$x 입구", "${x}에 가면 입구부터 둘러볼 거예요", lv = 1),
                a("맨 안쪽!", "$x 맨 안쪽", "${x}에 가면 맨 안쪽까지 가 볼 거예요", lv = 1),
                a("한가운데 넓은 데. 거기서 뛸 거야.", "$x 한가운데", "$x 한가운데 넓은 곳에서 신나게 뛸 거예요", con = true, lv = 2),
                a("제일 높은 데 갈래. 거기 가면 다 보일 것 같아서.", "$x 제일 높은 곳", "다 보일 것 같아서 ${x}에서 제일 높은 곳에 올라가 볼 거예요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("맨 안쪽. 거기 제일 재밌는 게 있대서.", "$x 맨 안쪽", "제일 재밌는 게 있다고 해서 $x 맨 안쪽부터 가 볼 거예요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            Answer(x, "$x|${x}에 갈 거예요"),
        )
        CoopReason.DREAM -> Body(
            listOf("${x}에 가면 뭐가 있을까?", choices("입구", "한가운데", "맨 안쪽", ask = "어디가 좋아?")),
            listOf(
                a("한가운데!", "$x 한가운데", "$x 한가운데가 제일 좋았어요", lv = 1),
                a("맨 안쪽!", "$x 맨 안쪽", "$x 맨 안쪽이 제일 좋았어요", lv = 1),
                a("제일 높은 데가 좋아.", "$x 제일 높은 곳", "${x}에서 제일 높은 곳이 제일 좋았어요", lv = 2),
                a("제일 높은 데. 거기 올라가면 다 보이니까.", "$x 제일 높은 곳", "${x}에서 제일 높은 곳에 올라가면 다 보였어요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("한가운데 넓은 데. 거기서 친구들이랑 뛰어놀 수 있어서 좋아.", "$x 한가운데", "$x 한가운데 넓은 곳에서 친구들과 뛰어놀았어요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("$x 한가운데", "$x 한가운데|$x 한가운데에서 이야기가 시작되었어요"),
        )
    }
    1 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("거기서 뭐 하고 놀았어?", choices("신났던 일", "깜짝 놀란 일", "속상했던 일", ask = "어떤 일이 있었어?")),
            listOf(
                a("사진 찍었어.", "사진 찍기", "${x}에서 사진을 찍었어요", lv = 1),
                a("구경했어!", "구경", "$x 여기저기를 구경했어요", lv = 1),
                a("줄 서서 오래 기다렸어.", "오래 기다림", "줄을 서서 한참 기다렸어요", lv = 2),
                a("모자가 바람에 날아갔어. 깜짝 놀랐어.", "모자가 날아감", "바람이 불어 모자가 훨훨 날아갔어요", el = setOf("결과"), emo = "놀랐", con = true, lv = 3),
                a("엄마가 안 보였어. 길 잃어버려서 무서웠어.", "길을 잃음", "엄마가 안 보여 길을 잃어버렸어요", el = setOf("결과"), emo = "무서웠", con = true, lv = 3),
            ),
            notHeard("일", "${x}에서 있었던 일은 아직 다 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("거기 가면 뭐 하고 놀까?", choices("신나는 일", "깜짝 놀랄 일", "곤란한 일", ask = "어떤 일이 생길까?")),
            listOf(
                a("사진 찍을 거야.", "사진 찍기", "${x}에서 사진을 찍을 거예요", lv = 1),
                a("구경할 거야!", "구경", "$x 여기저기를 구경할 거예요", lv = 1),
                a("줄이 길어서 오래 기다릴 것 같아.", "오래 기다림", "줄이 길어서 한참 기다릴 것 같아요", lv = 2),
                a("비가 오면 못 놀 수도 있어. 그럼 속상할 거야.", "비가 옴", "비가 오면 밖에서 못 놀 수도 있어요", el = setOf("결과"), emo = "속상했", con = true, lv = 3),
                a("사람이 많아서 길을 잃을 수도 있어. 그래서 조금 무서워.", "길을 잃을 수도 있음", "사람이 많아서 길을 잃을 수도 있어요", reason = true, el = setOf("결과"), emo = "무서웠", con = true, lv = 3),
            ),
            notHeard("일", "${x}에서 어떤 일이 생길지는 다녀와서 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("거기서 무슨 일이 생기면 재밌을까?", choices("말하는 동물", "하늘을 나는 의자", "숨은 보물", ask = "뭐가 나올까?")),
            listOf(
                a("보물 찾았어!", "보물을 찾음", "${x}에서 반짝이는 보물을 찾았어요", lv = 1),
                a("동물이 말했어!", "동물이 말함", "${x}에서 동물이 말을 걸어왔어요", lv = 1),
                a("의자가 하늘로 날아갔어.", "의자가 날아감", "앉아 있던 의자가 하늘로 날아올랐어요", lv = 2),
                a("보물 상자를 찾았는데 안 열렸어.", "보물 상자가 안 열림", "반짝이는 보물 상자를 찾았는데 꽉 잠겨 있었어요", el = setOf("결과"), con = true, lv = 3),
                a("갑자기 불이 다 꺼졌어. 깜깜해서 무서웠어.", "불이 다 꺼짐", "갑자기 ${x}의 불이 모두 꺼졌어요", el = setOf("결과"), emo = "무서웠", con = true, lv = 3),
            ),
            Answer("반짝이는 보물 상자", "보물 상자가 안 열림|반짝이는 보물 상자를 찾았는데 꽉 잠겨 있었어요"),
        )
    }
    2 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("그 일은 왜 그랬을 것 같아?", choices("바람 때문에", "사람이 많아서", "너무 신나서", ask = "왜 그랬을까?")),
            listOf(
                dunno(),
                a("바람 때문에!", "바람이 불어서", "바람이 세게 불었기 때문이에요", reason = true, lv = 1),
                a("사람이 너무 많아서.", "사람이 많아서", "사람이 너무 많았기 때문이에요", reason = true, lv = 2),
                a("바람이 세게 불어서. 그래서 모자가 날아간 거야.", "바람이 세게 불어서", "바람이 세게 불어서 그런 일이 생겼어요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("구경하느라 엄마를 못 봐서. 너무 재밌었거든.", "구경하느라 엄마를 못 봐서", "구경이 너무 재밌어서 엄마를 놓쳤기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 그랬는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("그런 일은 왜 생길 것 같아?", choices("비가 와서", "사람이 많아서", "처음 가 봐서", ask = "왜 그럴까?")),
            listOf(
                dunno(),
                a("비가 와서!", "비가 와서", "비가 올 수도 있기 때문이에요", reason = true, lv = 1),
                a("처음 가 보니까.", "처음 가 봐서", "처음 가 보는 곳이기 때문이에요", reason = true, lv = 2),
                a("사람이 많으면 복잡하니까. 그래서 손 꼭 잡아야 돼.", "사람이 많아 복잡해서", "사람이 많으면 복잡하기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("처음 가 보는 데라 길을 모르니까 그래.", "길을 몰라서", "처음 가 보는 곳이라 길을 모르기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 그럴지는 다녀와서 함께 알아보기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("누가 그런 일을 만들었을까?", choices("마법사", "요정", "바람", ask = "누구 때문일까?")),
            listOf(
                dunno(),
                a("마법 때문에!", "마법 때문에", "누군가 마법을 걸었기 때문이에요", reason = true, lv = 1),
                a("열쇠가 없어서.", "열쇠가 없어서", "상자를 열 열쇠가 없었기 때문이에요", reason = true, lv = 2),
                a("마법사가 마법을 걸어서. 그래서 다 신기해진 거야.", "마법사가 마법을 걸어서", "마법사가 ${x}에 마법을 걸었기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("밤이 돼서 그래. 밤에만 신기한 일이 생기거든.", "밤이 되어서", "밤이 되면 ${x}에 신기한 일이 생기기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("신기한 마법", "신기한 마법|누군가 ${x}에 신기한 마법을 걸었기 때문이에요"),
        )
    }
    else -> when (r) {
        CoopReason.DONE -> Body(
            listOf("그다음에 뭐 했어?", choices("엄마가 도와줬어", "혼자 해냈어", "집에 왔어", ask = "어떻게 됐어?")),
            listOf(
                a("엄마 찾았어!", "엄마를 찾음", "다시 엄마를 찾았어요", lv = 1),
                a("집에 왔어.", "집에 돌아옴", "재미있게 놀고 집에 돌아왔어요", con = true, lv = 1),
                a("직원 선생님이 도와줬어.", "직원이 도와줌", "직원 선생님이 도와주었어요", lv = 2),
                a("엄마 손 꼭 잡고 다녔어. 그래서 안 잃어버렸어.", "손을 꼭 잡음", "엄마 손을 꼭 잡고 다니니 다시는 길을 잃지 않았어요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("모자 주워서 다시 썼어. 이번엔 꽉 잡았어.", "모자를 다시 씀", "모자를 주워 다시 쓰고 이번엔 꽉 잡았어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("그다음엔 뭐 할까?", choices("엄마가 도와줄 거야", "혼자 해낼 거야", "집에 올 거야", ask = "어떻게 끝날까?")),
            listOf(
                a("재밌게 놀고 올 거야!", "재밌게 놀고 옴", "재미있게 놀고 집에 돌아올 거예요", lv = 1),
                a("엄마가 도와줄 거야.", "엄마가 도와줌", "엄마가 도와줄 거예요", lv = 1),
                a("우산 쓰고 놀 거야.", "우산 쓰고 놂", "비가 오면 우산을 쓰고 놀 거예요", lv = 2),
                a("엄마 손 꼭 잡을 거야. 그러면 안 잃어버려.", "손을 꼭 잡음", "엄마 손을 꼭 잡고 다니면 길을 잃지 않을 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("비 오면 안에서 놀 거야. 그래도 재밌을 거야.", "안에서 놂", "비가 오면 안에서 놀아도 재미있을 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "어떻게 끝날지는 다녀와서 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("그다음에 무슨 일이 생겼을까?", choices("요정이 도와줬어", "혼자 해냈어", "모두 같이 했어", ask = "어떻게 됐을까?")),
            listOf(
                a("상자가 열렸어!", "상자가 열림", "드디어 보물 상자가 열렸어요", lv = 1),
                a("요정이 도와줬어.", "요정이 도와줌", "작은 요정이 나타나 도와주었어요", lv = 1),
                a("손전등 켜서 다시 밝아졌어.", "다시 밝아짐", "손전등을 켜니 다시 밝아졌어요", el = setOf("결과"), lv = 2),
                a("주문을 외워서 상자를 열었어. 안에 별이 가득했어.", "주문으로 상자를 엶", "주문을 외워 상자를 열었더니 별이 가득했어요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("동물 친구들이랑 같이 찾았어. 그래서 다 같이 집에 갔어.", "다 같이 찾음", "동물 친구들과 함께 찾아내고 다 같이 돌아갔어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            Answer("모두 함께 웃으며 끝남", "모두 함께 웃으며 끝남|모두 함께 웃으며 ${x}에서 돌아왔어요"),
        )
    }
}

// ── 직업 · 임무 이야기 ─────────────────────────────────────────

private fun jobPart(x: String, r: CoopReason, part: Int): Body? = when (part) {
    0 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("$x${ga(x)} 일하는 곳에서 뭐가 보였어?", choices("일하는 방", "옷 입는 방", "쉬는 방", ask = "어디에 가 봤어?")),
            listOf(
                a("일하는 데!", "$x${ga(x)} 일하는 곳", "$x${ga(x)} 일하는 곳에 가 봤어요", lv = 1),
                a("큰 방!", "큰 방", "$x${ga(x)} 일하는 큰 방에 가 봤어요", lv = 1),
                a("옷 입는 방에 갔어.", "옷 입는 방", "$x 옷을 입어 보는 방에 갔어요", lv = 2),
                a("쉬는 방에 갔어. 거기서 진짜 $x 선생님 만났어.", "쉬는 방", "쉬는 방에서 진짜 $x${eul(x)} 만났어요", el = setOf("배경"), con = true, lv = 3),
                a("일하는 데 갔어. $x${ga(x)} 일하는 거 보고 싶어서.", "$x${ga(x)} 일하는 곳", "$x${ga(x)} 일하는 모습이 보고 싶어서 일하는 곳에 가 봤어요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            Answer("$x${ga(x)} 일하는 곳", "$x${ga(x)} 일하는 곳|$x 체험을 다녀왔어요"),
        )
        CoopReason.SOON -> Body(
            listOf("$x${eun(x)} 무슨 건물에서 일할까?", choices("큰 건물", "밖", "차 안", ask = "어디서 일할까?")),
            listOf(
                a("큰 건물!", "큰 건물", "$x${eun(x)} 큰 건물에서 일할 거예요", lv = 1),
                a("밖에서!", "밖", "$x${eun(x)} 밖에서 일할 거예요", lv = 1),
                a("사람 많은 데서 일해.", "사람 많은 곳", "$x${eun(x)} 사람이 많은 곳에서 일할 거예요", lv = 2),
                a("바쁜 데서 일할 거야. 도와줄 사람이 많으니까.", "바쁜 곳", "도와줄 사람이 많아서 $x${eun(x)} 바쁜 곳에서 일할 거예요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("큰 건물에서 일해. 거기에 일하는 도구가 다 있어서.", "큰 건물", "일하는 도구가 다 있어서 $x${eun(x)} 큰 건물에서 일할 거예요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("$x${ga(x)} 일하는 곳", "$x${ga(x)} 일하는 곳|$x 체험을 하러 갈 거예요"),
        )
        CoopReason.DREAM -> Body(
            listOf("$x${ga(x)} 되면 어디에 가 보고 싶어?", choices("우리 동네", "높은 건물", "바다 위", ask = "어디서 일할까?")),
            listOf(
                a("높은 건물!", "높은 건물", "$x${ga(x)} 되어 높은 건물에서 일했어요", lv = 1),
                a("바다 위에서!", "바다 위", "$x${ga(x)} 되어 바다 위에서 일했어요", lv = 1),
                a("우리 동네에서 일할 거야.", "우리 동네", "$x${ga(x)} 되어 우리 동네에서 일했어요", lv = 2),
                a("우리 동네에서 할래. 친구들 도와주고 싶어서.", "우리 동네", "친구들을 도와주고 싶어서 우리 동네에서 $x${ga(x)} 되었어요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("구름 위 집에서 일할 거야. 거기서 다 보이니까.", "구름 위 집", "다 내려다보이는 구름 위 집에서 $x${ga(x)} 되어 일했어요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("우리 동네", "우리 동네|우리 동네에서 $x${ga(x)} 되었어요"),
        )
    }
    1 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("거기서 뭐 만져 봤어?", choices("옷 입어 보기", "도구 만져 보기", "따라 해 보기", ask = "뭐 해 봤어?")),
            listOf(
                a("옷 입어 봤어!", "옷 입어 보기", "$x 옷을 입어 보았어요", lv = 1),
                a("도구 만져 봤어.", "도구 만져 보기", "$x${ga(x)} 쓰는 도구를 만져 보았어요", lv = 1),
                a("${x}처럼 따라 해 봤어. 재밌었어.", "따라 해 보기", "${x}처럼 따라 해 보았어요", emo = "재밌었", lv = 2),
                a("옷이 너무 커서 넘어질 뻔했어.", "옷이 너무 큼", "옷이 너무 커서 넘어질 뻔했어요", el = setOf("결과"), con = true, lv = 3),
                a("도구가 너무 무거워서 못 들었어. 속상했어.", "도구가 무거움", "도구가 너무 무거워서 들지 못했어요", el = setOf("결과"), emo = "속상했", con = true, lv = 3),
            ),
            notHeard("체험 이야기", "체험에서 한 일은 아직 다 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("$x${eun(x)} 하루 종일 뭐 할까?", choices("사람 도와주기", "도구 쓰기", "옷 입기", ask = "무슨 일을 할까?")),
            listOf(
                a("사람 도와줄 거야.", "사람 도와주기", "$x${eun(x)} 사람들을 도와줄 거예요", lv = 1),
                a("$x 옷 입을 거야!", "옷 입기", "$x 옷을 입어 볼 거예요", lv = 1),
                a("도구 만져 볼 거야.", "도구 만져 보기", "$x${ga(x)} 쓰는 도구를 만져 볼 거예요", lv = 2),
                a("따라 하다가 틀릴 수도 있어. 처음이니까.", "틀릴 수도 있음", "처음이라 따라 하다가 틀릴 수도 있어요", reason = true, el = setOf("결과"), con = true, lv = 3),
                a("도구가 무거워서 못 들 것 같아. 그래도 해 볼 거야.", "도구가 무거움", "도구가 무거워서 들기 힘들 것 같아요", el = setOf("결과"), con = true, lv = 3),
            ),
            notHeard("체험 이야기", "체험에서 할 일은 다녀와서 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("$x${ga(x)} 일하다가 누굴 만났을까?", choices("길 잃은 아이", "나무 위 고양이", "다친 친구", ask = "누가 도와 달라고 했을까?")),
            listOf(
                a("누가 도와 달라고 했어!", "도와 달라는 부탁", "누군가 $x${ga(x)} 된 나에게 도와 달라고 했어요", lv = 1),
                a("친구가 다쳤어.", "친구가 다침", "친구가 넘어져 다쳤어요", lv = 1),
                a("고양이가 나무에 올라갔어.", "고양이가 나무에 올라감", "고양이가 높은 나무에 올라가 내려오지 못했어요", lv = 2),
                a("고양이가 나무에서 못 내려왔어. 야옹야옹 울었어.", "고양이가 내려오지 못함", "고양이가 높은 나무에서 내려오지 못하고 야옹야옹 울었어요", el = setOf("결과"), con = true, lv = 3),
                a("어떤 아이가 길을 잃었어. 엄마를 못 찾아서 울고 있었어.", "아이가 길을 잃음", "길을 잃은 아이가 엄마를 찾지 못해 울고 있었어요", reason = true, el = setOf("결과"), con = true, lv = 3),
            ),
            Answer("도와 달라는 친구", "도와 달라는 부탁|누군가 $x${ga(x)} 된 나에게 도와 달라고 했어요"),
        )
    }
    2 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("$x${eun(x)} 그 일을 왜 할 것 같아?", choices("사람을 도우려고", "멋있으니까", "다치면 안 되니까", ask = "왜 할까?")),
            listOf(
                dunno(),
                a("사람 도와주려고!", "사람을 도우려고", "사람들을 도와주려고 그 일을 해요", reason = true, lv = 1),
                a("다치면 안 되니까.", "다치면 안 되니까", "사람들이 다치면 안 되기 때문이에요", reason = true, lv = 2),
                a("사람들이 다치면 안 되니까. 그래서 $x${ga(x)} 지켜 주는 거야.", "사람들을 지키려고", "사람들이 다치지 않게 $x${ga(x)} 지켜 주기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("$x${ga(x)} 없으면 사람들이 곤란하니까 그래.", "없으면 곤란하니까", "$x${ga(x)} 없으면 사람들이 곤란하기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 그 일을 하는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("$x${ga(x)} 없으면 어떻게 될까?", choices("사람을 도우려고", "동네를 지키려고", "다치면 안 되니까", ask = "왜 필요할까?")),
            listOf(
                dunno(),
                a("사람 도와주려고!", "사람을 도우려고", "사람들을 도와주려면 $x${ga(x)} 필요해요", reason = true, lv = 1),
                a("없으면 큰일 나니까.", "없으면 큰일 나서", "$x${ga(x)} 없으면 큰일이 나기 때문이에요", reason = true, lv = 2),
                a("$x${ga(x)} 없으면 아무도 못 도와주니까. 그래서 꼭 필요해.", "없으면 아무도 못 도와서", "$x${ga(x)} 없으면 아무도 도와줄 수 없기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("우리 동네를 지켜야 하니까. 그래야 다 같이 안전해.", "동네를 지키려고", "우리 동네를 지켜야 모두 안전하기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 필요한지는 체험하면서 알아보기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("그 일은 뭐 때문에 생겼을까?", choices("바람 때문에", "너무 높이 올라가서", "길이 복잡해서", ask = "왜 그랬을까?")),
            listOf(
                dunno(),
                a("바람 때문에!", "바람 때문에", "바람이 세게 불었기 때문이에요", reason = true, lv = 1),
                a("고양이가 새 잡으려고.", "새를 잡으려고", "고양이가 새를 잡으려 했기 때문이에요", reason = true, lv = 2),
                a("고양이가 새를 잡으려다 너무 높이 올라가서. 그래서 무서웠던 거야.", "너무 높이 올라가서", "고양이가 새를 잡으려다 너무 높이 올라갔기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("길이 너무 복잡해서 길을 잃은 거야.", "길이 복잡해서", "길이 너무 복잡했기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("거센 바람", "바람 때문에|거센 바람이 불었기 때문이에요"),
        )
    }
    else -> when (r) {
        CoopReason.DONE -> Body(
            listOf("체험이 다 끝나고 뭐 받았어?", choices("칭찬 받았어", "배지 받았어", "사진 찍었어", ask = "어떻게 됐어?")),
            listOf(
                a("배지 받았어!", "배지를 받음", "체험을 마치고 배지를 받았어요", lv = 1),
                a("집에 왔어.", "집에 돌아옴", "체험을 마치고 집에 돌아왔어요", con = true, lv = 1),
                a("$x 선생님이 칭찬해 줬어.", "칭찬을 받음", "진짜 $x${ga(x)} 칭찬해 주었어요", lv = 2),
                a("끝까지 해서 배지 받았어. 그래서 기분이 좋았어.", "끝까지 해서 배지를 받음", "끝까지 해내고 배지를 받아 기분이 좋았어요", el = setOf("시도", "결과"), emo = "기뻤", con = true, lv = 3),
                a("다 하고 사진 찍었어. 그래서 $x 옷 입은 사진이 생겼어.", "사진을 찍음", "체험을 마치고 $x 옷을 입은 사진을 찍었어요", el = setOf("결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "체험이 어떻게 끝났는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("체험이 끝나면 뭐 하고 싶어?", choices("칭찬 받기", "배지 받기", "사진 찍기", ask = "어떻게 될까?")),
            listOf(
                a("배지 받을 거야!", "배지를 받음", "체험을 마치면 배지를 받을 거예요", lv = 1),
                a("사진 찍을 거야.", "사진을 찍음", "체험을 마치면 사진을 찍을 거예요", lv = 1),
                a("$x 선생님이 칭찬해 줄 거야.", "칭찬을 받음", "진짜 $x${ga(x)} 칭찬해 줄 거예요", lv = 2),
                a("끝까지 해 볼 거야. 그러면 배지 받을 수 있어.", "끝까지 해 봄", "끝까지 해내면 배지를 받을 수 있을 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("무거운 것도 들어 볼 거야. 그래서 진짜 ${x}처럼 할 거야.", "진짜처럼 해 봄", "무거운 도구도 들어 보며 진짜 ${x}처럼 해 볼 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "어떻게 끝날지는 다녀와서 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("그다음에 $x${ga(x)} 뭐 했을까?", choices("사다리를 썼어", "지도를 봤어", "친구랑 같이 했어", ask = "어떻게 고쳤을까?")),
            listOf(
                a("도와줬어!", "도와줌", "$x${ga(x)} 된 내가 도와주었어요", lv = 1),
                a("친구를 병원에 데려갔어.", "병원에 데려감", "다친 친구를 병원에 데려다주었어요", lv = 1),
                a("사다리 타고 구해 줬어.", "사다리로 구함", "사다리를 타고 올라가 구해 주었어요", el = setOf("시도"), lv = 2),
                a("사다리를 길게 펴서 올라갔어. 그래서 고양이를 안고 내려왔어.", "사다리로 고양이를 구함", "사다리를 길게 펴서 올라가 고양이를 안고 내려왔어요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("지도 보고 엄마를 찾아 줬어. 그 아이가 웃었어.", "엄마를 찾아 줌", "지도를 보고 엄마를 찾아 주었더니 아이가 웃었어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            Answer("멋지게 도와줌", "멋지게 도와줌|$x${ga(x)} 된 내가 멋지게 도와주었어요"),
        )
    }
}

// ── 스포츠 · 도전 이야기 ───────────────────────────────────────

private fun sportPart(x: String, r: CoopReason, part: Int): Body? = when (part) {
    0 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("$x 한 곳은 어떤 곳이었어?", choices("운동장", "체육관", "공원", ask = "어디서 했어?")),
            listOf(
                a("운동장!", "운동장", "운동장에서 $x${eul(x)} 했어요", lv = 1),
                a("체육관!", "체육관", "체육관에서 $x${eul(x)} 했어요", lv = 1),
                a("공원에서 했어.", "공원", "공원에서 $x${eul(x)} 했어요", lv = 2),
                a("학교 운동장. 넓어서 거기서 했어.", "학교 운동장", "넓은 학교 운동장에서 $x${eul(x)} 했어요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("체육관에서 했어. 밖에 비가 와서.", "체육관", "밖에 비가 와서 체육관에서 $x${eul(x)} 했어요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            Answer("$x 한 곳", "$x 한 곳|$x${eul(x)} 해 봤어요"),
        )
        CoopReason.SOON -> Body(
            listOf("$x 배우는 곳은 어떤 데일까?", choices("운동장", "체육관", "학원", ask = "어디서 배울까?")),
            listOf(
                a("체육관!", "체육관", "체육관에서 $x${eul(x)} 배울 거예요", lv = 1),
                a("운동장!", "운동장", "운동장에서 $x${eul(x)} 배울 거예요", lv = 1),
                a("집 앞에서 배울 거야.", "집 앞", "집 앞에서 $x${eul(x)} 배울 거예요", lv = 2),
                a("체육관에서 배울 거야. 거기 선생님이 있으니까.", "체육관", "선생님이 계셔서 체육관에서 $x${eul(x)} 배울 거예요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("집 앞 공원에서. 가까워서 매일 갈 수 있어.", "집 앞 공원", "가까워서 매일 갈 수 있는 집 앞 공원에서 배울 거예요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("$x 배우는 곳", "$x 배우는 곳|$x${eul(x)} 배우러 갈 거예요"),
        )
        CoopReason.DREAM -> Body(
            listOf("$x 경기장에는 뭐가 있을까?", choices("큰 경기장", "바닷가", "구름 위", ask = "어디서 열릴까?")),
            listOf(
                a("큰 경기장!", "큰 경기장", "큰 경기장에서 $x 경기가 열렸어요", lv = 1),
                a("바닷가!", "바닷가", "바닷가에서 $x 경기가 열렸어요", lv = 1),
                a("구름 위 경기장에서 열려.", "구름 위 경기장", "구름 위 경기장에서 $x 경기가 열렸어요", lv = 2),
                a("우주 경기장에서 열려. 거기선 몸이 둥둥 떠서.", "우주 경기장", "몸이 둥둥 뜨는 우주 경기장에서 $x 경기가 열렸어요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("큰 경기장. 사람이 엄청 많이 와서 거기서 해.", "큰 경기장", "사람이 아주 많이 와서 큰 경기장에서 $x 경기가 열렸어요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            Answer("큰 경기장", "큰 경기장|큰 경기장에서 $x 경기가 열렸어요"),
        )
    }
    1 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("$x 하면서 뭐가 제일 재밌었어?", choices("이긴 일", "넘어진 일", "잘 안 된 일", ask = "무슨 일이 있었어?")),
            listOf(
                a("이겼어!", "이김", "$x 시합에서 이겼어요", lv = 1),
                a("넘어졌어.", "넘어짐", "$x 하다가 넘어졌어요", lv = 1),
                a("친구랑 부딪혔어.", "친구와 부딪힘", "$x 하다가 친구와 부딪혔어요", lv = 2),
                a("넘어져서 무릎 아팠어. 그래도 계속했어.", "넘어져 무릎이 아픔", "넘어져서 무릎이 아팠지만 계속했어요", el = setOf("결과"), emo = "아팠", con = true, lv = 3),
                a("자꾸 잘 안 됐어. 그래서 속상했어.", "잘 안 됨", "$x${ga(x)} 자꾸 잘 안 돼서 속상했어요", el = setOf("결과"), emo = "속상했", con = true, lv = 3),
            ),
            notHeard("일", "$x 하다가 있었던 일은 아직 다 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("처음 하는 날 뭐가 제일 기대돼?", choices("신나는 일", "어려운 일", "놀라운 일", ask = "무슨 일이 생길까?")),
            listOf(
                a("친구 사귈 거야!", "친구를 사귐", "$x${eul(x)} 배우며 친구를 사귈 거예요", lv = 1),
                a("넘어질 수도 있어.", "넘어질 수도 있음", "처음이라 넘어질 수도 있어요", lv = 1),
                a("처음이라 잘 안 될 것 같아.", "잘 안 될 것 같음", "처음이라 잘 안 될 것 같아요", lv = 2),
                a("처음이라 떨릴 것 같아. 그래도 해 볼 거야.", "떨림", "처음이라 떨리지만 해 볼 거예요", el = setOf("결과"), emo = "떨렸", con = true, lv = 3),
                a("선생님 말을 잘 들어야 해. 안 그러면 다칠 수 있어.", "다칠 수도 있음", "선생님 말씀을 잘 듣지 않으면 다칠 수도 있어요", reason = true, el = setOf("결과"), con = true, lv = 3),
            ),
            notHeard("일", "처음 하는 날 무슨 일이 생길지는 다녀와서 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("경기에서 깜짝 놀랄 일이 뭐였을까?", choices("공이 하늘로", "우리 팀이 지고 있어", "비가 와", ask = "어떤 일이 생길까?")),
            listOf(
                a("우리 팀이 지고 있었어!", "우리 팀이 지고 있음", "우리 팀이 지고 있었어요", lv = 1),
                a("비가 왔어.", "비가 옴", "경기 중에 비가 쏟아졌어요", lv = 1),
                a("공이 하늘로 날아갔어.", "공이 하늘로 날아감", "공이 하늘 높이 날아가 버렸어요", lv = 2),
                a("우리 팀이 지고 있었어. 다들 힘이 없었어.", "우리 팀이 지고 있음", "우리 팀이 지고 있어서 모두 힘이 빠졌어요", el = setOf("결과"), con = true, lv = 3),
                a("공이 구름 위로 날아갔어. 아무도 못 잡았어.", "공이 구름 위로 감", "공이 구름 위로 날아가 아무도 잡지 못했어요", el = setOf("결과"), con = true, lv = 3),
            ),
            Answer("지고 있는 우리 팀", "우리 팀이 지고 있음|우리 팀이 지고 있었어요"),
        )
    }
    2 -> when (r) {
        CoopReason.DONE -> Body(
            listOf("그 일은 왜 그랬을 것 같아?", choices("너무 빨리 뛰어서", "처음 해 봐서", "땅이 미끄러워서", ask = "왜 그랬을까?")),
            listOf(
                dunno(),
                a("빨리 뛰어서!", "빨리 뛰어서", "너무 빨리 뛰었기 때문이에요", reason = true, lv = 1),
                a("처음 해 봐서.", "처음 해 봐서", "처음 해 보는 것이었기 때문이에요", reason = true, lv = 2),
                a("땅이 미끄러워서 그랬어. 비가 왔거든.", "땅이 미끄러워서", "비가 와서 땅이 미끄러웠기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("연습을 많이 해서 이겼어. 매일 했거든.", "연습을 많이 해서", "매일 연습을 많이 했기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 그랬는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("그런 일은 왜 생길 것 같아?", choices("처음 해 봐서", "몸이 아직 작아서", "떨려서", ask = "왜 그럴까?")),
            listOf(
                dunno(),
                a("처음이니까!", "처음이라서", "처음 해 보는 것이기 때문이에요", reason = true, lv = 1),
                a("아직 못 배워서.", "아직 못 배워서", "아직 배우지 않았기 때문이에요", reason = true, lv = 2),
                a("처음 가는 데라 떨려서. 그래도 금방 괜찮아질 거야.", "떨려서", "처음 가는 곳이라 떨리기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
                a("몸이 아직 작아서 그래. 크면 더 잘할 거야.", "몸이 아직 작아서", "아직 몸이 작기 때문이에요", reason = true, el = setOf("배경"), con = true, lv = 3),
            ),
            notHeard("까닭", "왜 그럴지는 배우면서 알아보기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("그 일은 뭐 때문에 생겼을까?", choices("바람 때문에", "상대 팀이 세서", "마법 때문에", ask = "왜 그랬을까?")),
            listOf(
                dunno(),
                a("바람 때문에!", "바람 때문에", "바람이 세게 불었기 때문이에요", reason = true, lv = 1),
                a("상대 팀이 너무 세서.", "상대 팀이 세서", "상대 팀이 너무 셌기 때문이에요", reason = true, lv = 2),
                a("상대 팀이 마법 신발을 신어서. 그래서 엄청 빨랐어.", "마법 신발 때문에", "상대 팀이 마법 신발을 신었기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
                a("너무 세게 차서 그래. 힘이 엄청 셌거든.", "너무 세게 차서", "너무 세게 찼기 때문이에요", reason = true, el = setOf("계기"), con = true, lv = 3),
            ),
            Answer("이상한 바람", "바람 때문에|이상한 바람이 불었기 때문이에요"),
        )
    }
    else -> when (r) {
        CoopReason.DONE -> Body(
            listOf("다 끝나고 뭐 했어?", choices("다시 해 봤어", "선생님이 도와줬어", "집에 왔어", ask = "어떻게 됐어?")),
            listOf(
                a("다시 했어!", "다시 함", "다시 일어나 $x${eul(x)} 했어요", lv = 1),
                a("집에 왔어.", "집에 돌아옴", "$x${eul(x)} 마치고 집에 돌아왔어요", con = true, lv = 1),
                a("선생님이 도와줬어.", "선생님이 도와줌", "선생님이 도와주었어요", lv = 2),
                a("다시 일어나서 끝까지 했어. 그래서 메달 받았어.", "끝까지 해서 메달을 받음", "다시 일어나 끝까지 해내고 메달을 받았어요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("천천히 다시 해 봤어. 이번엔 잘 됐어.", "천천히 다시 해서 잘 됨", "천천히 다시 해 보니 이번엔 잘 되었어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요"),
        )
        CoopReason.SOON -> Body(
            listOf("다 배우고 나면 뭐 하고 싶어?", choices("다시 해 보기", "선생님께 배우기", "친구랑 하기", ask = "어떻게 끝날까?")),
            listOf(
                a("잘하게 될 거야!", "잘하게 됨", "$x${eul(x)} 잘하게 될 거예요", lv = 1),
                a("친구랑 할 거야.", "친구와 함", "친구와 함께 $x${eul(x)} 할 거예요", lv = 1),
                a("선생님이 가르쳐 줄 거야.", "선생님이 가르쳐 줌", "선생님이 가르쳐 주실 거예요", lv = 2),
                a("매일 연습할 거야. 그러면 잘할 수 있어.", "매일 연습함", "매일 연습하면 잘할 수 있을 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("넘어져도 다시 할 거야. 그래서 시합에도 나갈 거야.", "다시 일어나 시합에 나감", "넘어져도 다시 일어나 시합에도 나갈 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            notHeard("뒷이야기", "어떻게 끝날지는 배워 보고 들려주기로 했어요"),
        )
        CoopReason.DREAM -> Body(
            listOf("마지막에 누가 이겼을까?", choices("우리 팀이 이겼어", "같이 웃었어", "다시 했어", ask = "어떻게 끝났을까?")),
            listOf(
                a("우리 팀이 이겼어!", "우리 팀이 이김", "마지막에 우리 팀이 이겼어요", lv = 1),
                a("다 같이 웃었어.", "다 같이 웃음", "경기가 끝나고 모두 함께 웃었어요", lv = 1),
                a("공을 다시 찾아서 이겼어.", "공을 찾아 이김", "공을 다시 찾아 경기에서 이겼어요", el = setOf("결과"), lv = 2),
                a("다 같이 힘을 냈어. 그래서 마지막에 역전했어.", "힘을 모아 역전함", "모두 함께 힘을 내서 마지막에 역전했어요", el = setOf("시도", "결과"), con = true, lv = 3),
                a("사다리 타고 구름 위로 가서 공을 찾았어. 그래서 경기를 끝까지 했어.", "구름 위에서 공을 찾음", "사다리를 타고 구름 위로 올라가 공을 찾아 경기를 끝까지 했어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            Answer("모두 함께 웃으며 끝남", "모두 함께 웃으며 끝남|$x 경기가 끝나고 모두 함께 웃었어요"),
        )
    }
}

// ── 꼬리질문 — 요소 이름 없이 어느 템플릿에도 맞게, 시제만 이유를 따른다 ────────────────

private fun who(text: String, kind: String, line: String, lv: Int, reason: Boolean = false, el: Set<String> = emptySet(), con: Boolean = false) =
    Answer(text = text, value = "$kind|$line", reason = reason, el = el, con = con, lv = lv, kind = kind)

private fun tailPack(key: String, r: CoopReason, c: String, w: String): CoopPartPack? = when (key) {
    // 누구랑 — kind 가 뒤의 「한 말」 걸음을 살린다. 흔한 호칭 밖의 말은 이름 사전으로 간다 (afterDiaryAnswer)
    "companion" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("거기 누구랑 같이 갔어?", choices("엄마", "아빠", "친구", ask = "누구랑 갔어?"), "거기서 누구를 만났어?"),
            listOf(
                who("엄마랑!", "엄마", "엄마와 함께 갔어요", 1),
                who("친구랑!", "친구", "친구와 함께 갔어요", 1),
                who("아빠랑 갔어.", "아빠", "아빠와 함께 갔어요", 2),
                who("할머니랑 갔어.", "할머니", "할머니와 함께 갔어요", 2),
                who("가족 다 같이 갔어. 주말이라서.", "가족", "주말이라 가족이 다 함께 갔어요", 3, reason = true, el = setOf("배경"), con = true),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("누구랑 같이 갈 거야?", choices("엄마", "아빠", "친구", ask = "누구랑 갈까?"), "거기서 누구를 만날까?"),
            listOf(
                who("엄마랑!", "엄마", "엄마와 함께 갈 거예요", 1),
                who("친구랑!", "친구", "친구와 함께 갈 거예요", 1),
                who("아빠랑 갈 거야.", "아빠", "아빠와 함께 갈 거예요", 2),
                who("선생님이랑 갈 거야.", "선생님", "선생님과 함께 갈 거예요", 2),
                who("친구들이랑 다 같이 갈 거야. 같이 가면 더 재밌으니까.", "친구들", "같이 가면 더 재미있어서 친구들과 다 함께 갈 거예요", 3, reason = true, el = setOf("배경"), con = true),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("그 이야기에 누가 같이 나올까?", choices("강아지", "로봇", "친구", ask = "누가 같이 갈까?"), "거기서 누구를 만났을까?"),
            listOf(
                who("강아지랑!", "강아지", "강아지와 함께 갔어요", 1),
                who("친구랑!", "친구", "친구와 함께 갔어요", 1),
                who("로봇이랑 같이 갔어.", "로봇", "로봇과 함께 갔어요", 2),
                who("말하는 토끼랑 갔어.", "토끼", "말하는 토끼와 함께 갔어요", 2),
                who("친구들이랑 다 같이 갔어. 혼자 가면 심심하니까.", "친구들", "혼자 가면 심심해서 친구들과 다 함께 갔어요", 3, reason = true, el = setOf("배경"), con = true),
            ),
            null,
        )
    }
    // 자세히 — 그때 무엇을 어떻게 했나
    "detail" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("그때 뭐 하고 있었어?", "어떻게 했는지 얘기해 줄래?", "그거 어떻게 했는지 보여 줄래?"),
            listOf(
                Answer("그냥 했어.", "", lv = 1),
                a("열심히 봤어.", "열심히 봤어", "$c${eun(c)} 눈을 반짝이며 열심히 보았어요", el = setOf("시도"), lv = 1),
                a("하나하나 다 해 봤어.", "다 해 봤어", "$c${eun(c)} 하나하나 다 해 보았어요", el = setOf("시도"), lv = 2),
                a("천천히 따라 했어. 빨리 하면 틀리니까.", "천천히 따라 했어", "틀리지 않으려고 천천히 따라 했어요", reason = true, el = setOf("시도"), con = true, lv = 3),
                a("처음엔 무서웠는데 해 보니까 재밌었어.", "해 보니 재밌었어", "처음엔 무서웠지만 해 보니 재미있었어요", el = setOf("시도", "결과"), emo = "재밌었", con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("그때 뭐 하고 있을 것 같아?", "어떻게 할지 얘기해 줄래?", "그거 어떻게 할지 보여 줄래?"),
            listOf(
                Answer("그냥 할 거야.", "", lv = 1),
                a("열심히 볼 거야.", "열심히 볼 거야", "$c${eun(c)} 눈을 반짝이며 열심히 볼 거예요", el = setOf("시도"), lv = 1),
                a("하나하나 다 해 볼 거야.", "다 해 볼 거야", "$c${eun(c)} 하나하나 다 해 볼 거예요", el = setOf("시도"), lv = 2),
                a("천천히 따라 할 거야. 빨리 하면 틀리니까.", "천천히 따라 할 거야", "틀리지 않으려고 천천히 따라 할 거예요", reason = true, el = setOf("시도"), con = true, lv = 3),
                a("처음엔 떨려도 해 볼 거야. 그러면 재밌을 거야.", "떨려도 해 볼 거야", "처음엔 떨려도 해 보면 재미있을 거예요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("그때 뭐 하고 있었을까?", "어떻게 했는지 얘기해 줄래?", "그 모습 어떻게 했는지 보여 줄래?"),
            listOf(
                Answer("그냥 했어.", "", lv = 1),
                a("하늘을 날았어!", "하늘을 날았어", "$c${eun(c)} 하늘을 훨훨 날았어요", el = setOf("시도"), lv = 1),
                a("마법 지팡이를 흔들었어.", "지팡이를 흔들었어", "$c${eun(c)} 마법 지팡이를 휘휘 흔들었어요", el = setOf("시도"), lv = 2),
                a("살금살금 걸어갔어. 들키면 안 되니까.", "살금살금 걸어갔어", "들키지 않으려고 살금살금 걸어갔어요", reason = true, el = setOf("시도"), con = true, lv = 3),
                a("크게 소리쳤어. 그랬더니 다들 쳐다봤어.", "크게 소리쳤어", "크게 소리쳤더니 모두가 쳐다보았어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            null,
        )
    }
    // 마음 — 마음 말하기는 수준 판단에 쓰지 않는다 (일기 §2-2)
    "reaction" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("그때 어떤 기분이었어?", choices("좋았어", "속상했어", "무서웠어", ask = "어떤 마음이었어?"), "그때 어떤 얼굴이었어?"),
            listOf(
                a("좋았어!", "기뻤어", "참 기분이 좋았어요", emo = "기뻤", lv = 1),
                a("무서웠어.", "무서웠어", "조금 무서운 마음이 들었어요", emo = "무서웠", lv = 1),
                a("신났어! 또 하고 싶었어.", "신났어", "신이 나서 또 하고 싶었어요", emo = "신났", con = true, lv = 2),
                a("처음엔 떨렸는데 나중엔 괜찮아졌어.", "떨렸다 괜찮아졌어", "처음엔 떨렸지만 나중엔 마음이 편해졌어요", emo = "떨렸", el = setOf("결과"), con = true, lv = 3),
                a("뿌듯했어. 끝까지 해냈으니까.", "뿌듯했어", "끝까지 해내서 마음이 뿌듯했어요", emo = "뿌듯했", reason = true, con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("가기 전에 어떤 기분이야?", choices("설레", "떨려", "궁금해", ask = "어떤 마음이야?"), "그 생각하면 어떤 얼굴이 돼?"),
            listOf(
                a("설레!", "설레", "가는 날이 기다려져 마음이 설레요", emo = "설렜", lv = 1),
                a("떨려.", "떨려", "조금 떨리는 마음이에요", emo = "떨렸", lv = 1),
                a("궁금해! 빨리 가고 싶어.", "궁금해", "궁금해서 빨리 가 보고 싶어요", emo = "궁금했", con = true, lv = 2),
                a("조금 무서운데 그래도 기대돼.", "무섭지만 기대돼", "조금 무섭지만 그래도 기대돼요", emo = "무서웠", el = setOf("결과"), con = true, lv = 3),
                a("신나. 처음 해 보는 거라서.", "신나", "처음 해 보는 거라 신이 나요", emo = "신났", reason = true, con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("그때 어떤 기분이었을까?", choices("신났어", "무서웠어", "놀랐어", ask = "어떤 마음이었을까?"), "그때 어떤 얼굴이었을까?"),
            listOf(
                a("신났어!", "신났어", "신이 나서 가슴이 두근두근했어요", emo = "신났", lv = 1),
                a("놀랐어!", "놀랐어", "깜짝 놀라 눈이 동그래졌어요", emo = "놀랐", lv = 1),
                a("무서웠어. 그래도 꾹 참았어.", "무서웠어", "무서웠지만 꾹 참았어요", emo = "무서웠", con = true, lv = 2),
                a("처음엔 무서웠는데 나중엔 재밌었어.", "무서웠다 재밌었어", "처음엔 무서웠지만 나중엔 재미있었어요", emo = "재밌었", el = setOf("결과"), con = true, lv = 3),
                a("뿌듯했어. 내가 다 해결했으니까.", "뿌듯했어", "스스로 해결해서 마음이 뿌듯했어요", emo = "뿌듯했", reason = true, con = true, lv = 3),
            ),
            null,
        )
    }
    // 같이 간 사람이 한 말 — 사람이 나온 이야기에서만 묻는다 (걸음의 ask 조건 그대로)
    "said" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("$w${ga(w)} 뭐라고 했어?", "$w${eun(w)} 그때 어떻게 했어?", "$w${eun(w)} 어떤 얼굴이었어?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("재밌다고 했어!", "재밌다고 했어", "$w${ga(w)} \"재밌다!\" 하고 말했어요", el = setOf("결과"), lv = 1),
                a("잘했다고 했어.", "잘했다고 했어", "$w${ga(w)} \"잘했어\" 하고 칭찬해 주었어요", el = setOf("결과"), lv = 2),
                a("조심하라고 했어.", "조심하라고 했어", "$w${ga(w)} \"조심해\" 하고 말해 주었어요", el = setOf("시도"), lv = 2),
                a("또 오자고 했어. 그래서 나도 좋다고 했어.", "또 오자고 했어", "$w${ga(w)} \"또 오자\" 하고 말해서 좋다고 했어요", el = setOf("결과"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("$w${ga(w)} 뭐라고 할까?", "$w${eun(w)} 그때 어떻게 할까?", "$w${eun(w)} 어떤 얼굴일까?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("재밌겠다고 할 거야!", "재밌겠다고 할 거야", "$w${ga(w)} \"재밌겠다!\" 하고 말할 거예요", el = setOf("결과"), lv = 1),
                a("조심하라고 할 거야.", "조심하라고 할 거야", "$w${ga(w)} \"조심해\" 하고 말해 줄 거예요", el = setOf("시도"), lv = 2),
                a("손 잡자고 할 거야.", "손 잡자고 할 거야", "$w${ga(w)} 손을 잡자고 할 거예요", el = setOf("시도"), lv = 2),
                a("같이 사진 찍자고 할 거야. 그래야 기억나니까.", "사진 찍자고 할 거야", "$w${ga(w)} 기억하려고 같이 사진을 찍자고 할 거예요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("$w${ga(w)} 뭐라고 했을까?", "$w${eun(w)} 그때 어떻게 했을까?", "$w${eun(w)} 어떤 얼굴이었을까?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("힘내라고 했어!", "힘내라고 했어", "$w${ga(w)} \"힘내!\" 하고 외쳤어요", el = setOf("시도"), lv = 1),
                a("같이 가자고 했어.", "같이 가자고 했어", "$w${ga(w)} \"같이 가자\" 하고 손을 내밀었어요", el = setOf("시도"), lv = 2),
                a("비밀을 알려 줬어.", "비밀을 알려 줬어", "$w${ga(w)} 작은 목소리로 비밀을 알려 주었어요", el = setOf("결과"), lv = 2),
                a("무섭다고 했어. 그래서 내가 손 잡아 줬어.", "무섭다고 했어", "$w${ga(w)} 무섭다고 해서 손을 꼭 잡아 주었어요", el = setOf("시도", "결과"), con = true, lv = 3),
            ),
            null,
        )
    }
    // 그래서 어떻게 했나 — 스스로 해 본 것
    "try" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("그때 제일 먼저 한 게 뭐야?", choices("다시 해 봤어", "도와 달라고 했어", "기다렸어", ask = "어떻게 했어?"), "그때 제일 열심히 한 게 뭐야?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("기다렸어.", "기다렸어", "$c${eun(c)} 차례를 꾹 참고 기다렸어요", el = setOf("시도"), lv = 1),
                a("다시 해 봤어.", "다시 해 봤어", "$c${eun(c)} 포기하지 않고 다시 해 보았어요", el = setOf("시도"), lv = 2),
                a("도와 달라고 했어.", "도와 달라고 했어", "$c${eun(c)} 용기 내어 도와 달라고 말했어요", el = setOf("시도"), lv = 2),
                a("천천히 다시 했어. 빨리 하면 또 그럴까 봐.", "천천히 다시 했어", "이번엔 천천히 다시 해 보기로 했어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("그때 제일 먼저 뭐 할 거야?", choices("다시 해 보기", "도와 달라고 하기", "기다리기", ask = "어떻게 할까?"), "제일 열심히 할 건 뭐야?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("기다릴 거야.", "기다릴 거야", "$c${eun(c)} 차례를 꾹 참고 기다릴 거예요", el = setOf("시도"), lv = 1),
                a("다시 해 볼 거야.", "다시 해 볼 거야", "$c${eun(c)} 포기하지 않고 다시 해 볼 거예요", el = setOf("시도"), lv = 2),
                a("도와 달라고 할 거야.", "도와 달라고 할 거야", "$c${eun(c)} 용기 내어 도와 달라고 말할 거예요", el = setOf("시도"), lv = 2),
                a("천천히 할 거야. 빨리 하면 넘어지니까.", "천천히 할 거야", "넘어지지 않게 천천히 해 볼 거예요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("그때 제일 먼저 뭐 했을까?", choices("주문 외우기", "도와 달라고 하기", "숨기", ask = "어떻게 했을까?"), "제일 열심히 한 건 뭐였을까?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("숨었어!", "숨었어", "$c${eun(c)} 얼른 나무 뒤에 숨었어요", el = setOf("시도"), lv = 1),
                a("주문을 외웠어.", "주문을 외웠어", "$c${eun(c)} 큰 소리로 주문을 외웠어요", el = setOf("시도"), lv = 2),
                a("도와 달라고 외쳤어.", "도와 달라고 외쳤어", "$c${eun(c)} \"도와줘!\" 하고 외쳤어요", el = setOf("시도"), lv = 2),
                a("다 같이 힘을 모았어. 혼자는 못 하니까.", "힘을 모았어", "혼자서는 못 해서 다 같이 힘을 모았어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
    }
    // 다 끝나고 — 그 뒤의 일
    "after" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("다 끝나고 집에 와서 뭐 했어?", choices("밥 먹었어", "잤어", "얘기했어", ask = "집에서 뭐 했어?"), "집에 와서 누구한테 얘기했어?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("밥 먹었어.", "밥 먹었어", "집에 돌아와 저녁을 맛있게 먹었어요", lv = 1),
                a("피곤해서 잤어.", "잤어", "피곤해서 집에 오자마자 잠이 들었어요", reason = true, lv = 2),
                a("사진 보면서 얘기했어.", "사진 보며 얘기했어", "찍은 사진을 보며 이야기를 나누었어요", con = true, lv = 2),
                a("아빠한테 다 얘기했어. 너무 재밌었거든.", "아빠에게 얘기했어", "너무 재밌어서 아빠에게 하나하나 이야기해 주었어요", reason = true, el = setOf("결과"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("다녀와서 뭐 할 거야?", choices("사진 보기", "그림 그리기", "얘기하기", ask = "뭐 하고 싶어?"), "다녀와서 누구한테 얘기할 거야?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("사진 볼 거야.", "사진 볼 거야", "다녀와서 찍은 사진을 볼 거예요", lv = 1),
                a("그림 그릴 거야.", "그림 그릴 거야", "다녀와서 본 것을 그림으로 그릴 거예요", lv = 2),
                a("할머니한테 전화할 거야.", "할머니께 전화할 거야", "다녀와서 할머니께 전화로 이야기할 거예요", lv = 2),
                a("친구한테 다 얘기해 줄 거야. 친구도 가 보고 싶을 거니까.", "친구에게 얘기해 줄 거야", "친구도 가 보고 싶을 테니 하나하나 이야기해 줄 거예요", reason = true, el = setOf("결과"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("이야기가 끝나고 뭐 했을까?", choices("집에 왔어", "잔치를 했어", "잠들었어", ask = "그다음에 뭐 했을까?"), "끝나고 누구한테 얘기했을까?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("집에 왔어.", "집에 왔어", "모험을 마치고 집으로 돌아왔어요", lv = 1),
                a("잔치를 했어!", "잔치를 했어", "모두 모여 신나는 잔치를 열었어요", lv = 2),
                a("포근하게 잠들었어.", "잠들었어", "포근한 이불 속에서 스르르 잠들었어요", lv = 2),
                a("엄마한테 다 얘기했어. 엄마가 깜짝 놀랐어.", "엄마에게 얘기했어", "엄마에게 모험 이야기를 들려주었더니 깜짝 놀랐어요", el = setOf("결과"), con = true, lv = 3),
            ),
            null,
        )
    }
    // 또 하고 싶은 것
    "keep" -> when (r) {
        CoopReason.DONE -> CoopPartPack(
            listOf("다음에 또 가면 뭐 하고 싶어?", choices("또 구경하기", "다른 거 해 보기", "친구 데려가기", ask = "다음엔 뭐 할까?"), "제일 기억에 남는 게 뭐야?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("또 가고 싶어!", "또 가고 싶어", "다음에 또 가고 싶어요", lv = 1),
                a("다른 것도 해 보고 싶어.", "다른 것도 해 보고 싶어", "다음엔 다른 것도 해 보고 싶어요", lv = 2),
                a("친구도 데려가고 싶어.", "친구도 데려가고 싶어", "다음엔 친구도 데려가고 싶어요", lv = 2),
                a("다음엔 끝까지 해 볼 거야. 이번엔 조금 무서웠거든.", "끝까지 해 볼 거야", "다음엔 무서워도 끝까지 해 보기로 마음먹었어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.SOON -> CoopPartPack(
            listOf("거기 가서 꼭 해 보고 싶은 게 뭐야?", choices("구경하기", "사진 찍기", "직접 해 보기", ask = "뭐가 제일 하고 싶어?"), "제일 기대되는 게 뭐야?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("사진 찍을 거야!", "사진 찍을 거야", "가서 꼭 사진을 찍고 싶어요", lv = 1),
                a("직접 해 보고 싶어.", "직접 해 보고 싶어", "가서 꼭 직접 해 보고 싶어요", lv = 2),
                a("제일 큰 거 보고 싶어.", "제일 큰 거 보고 싶어", "가서 제일 큰 것을 보고 싶어요", lv = 2),
                a("끝까지 다 해 볼 거야. 그래야 다음에 친구한테 알려 주지.", "끝까지 해 볼 거야", "친구에게 알려 주려고 끝까지 다 해 보기로 했어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
        CoopReason.DREAM -> CoopPartPack(
            listOf("다음 이야기에서는 뭐 하고 싶어?", choices("또 모험하기", "친구 데려가기", "새로운 데 가기", ask = "다음엔 뭐 할까?"), "이 이야기에서 뭐가 제일 좋았어?"),
            listOf(
                Answer("몰라.", "", lv = 1),
                a("또 모험할래!", "또 모험할래", "다음에도 또 모험을 떠나고 싶어요", lv = 1),
                a("친구도 데려갈래.", "친구도 데려갈래", "다음엔 친구도 데려가고 싶어요", lv = 2),
                a("새로운 데 가 볼래.", "새로운 데 가 볼래", "다음엔 새로운 곳에 가 보고 싶어요", lv = 2),
                a("다음엔 내가 먼저 도와줄 거야. 이번엔 도움을 받았으니까.", "먼저 도와줄 거야", "이번엔 도움을 받았으니 다음엔 먼저 도와주기로 했어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            ),
            null,
        )
    }
    else -> null
}
