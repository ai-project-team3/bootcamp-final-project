package com.example.finalproject_demo.demo

import com.example.finalproject_demo.demo.scene.FRIEND_SPOT
import com.example.finalproject_demo.demo.scene.HERO_SPOT
import com.example.finalproject_demo.ui.HeroAttr

/*
 * 장면 3′ — 오늘 있었던 일 (일기 모드 · 부모 협업 모드).
 *
 * 동화 모드의 질문 자리(장면 3~10)를 이 하나가 대신한다.
 * **일기 모드는 그림일기**(`PictureDiary.kt` · 09-30)로 간다. 이 파일의 열한 걸음 질문 흐름은
 * **협업 모드**가 쓴다 — 협업은 기존 질문 흐름에 **부모 띠**를 붙인다 (협업 §3 · #36).
 *
 * 묻지 않는 칸: `sound`(공룡 소리는 상상 세계의 것) · `adult`(협업 모드에서만 찬다).
 */

// ── 끝나는 조건 ────────────────────────────────────────────────

/**
 * 협업 모드의 질문 흐름을 멈추는 조건 (09-30 확정 · guidelines/2 §1-1 · #36).
 *
 * 끝나는 조건은 `story_ready` 하나다. 「`mascot_pick` 2회 연속이면 끝」은 없앴다 — 마스코트는 칸을 채울 뿐
 * 이야기를 닫지 않는다. **협업은 시간으로 끊지 않는다** — 뼈대 네 자리와 부모가 넣은 질문을 다 물으면 마무리하고
 * (`coopQuestionsAllAsked` · 아래 루프), 부모 「그만하기」로 언제든 끝낸다(`stopCoopByParent`).
 * 일기(그림일기)는 이 함수를 쓰지 않고 30분쯤 마무리를 한 번 제안한다(`PictureDiary.kt`).
 */
fun Director.diaryEnded(): Boolean = s.endReason != null

/** 기승전결 네 자리가 다 찼나 — 모든 질문을 다 물은 뒤에만 본다 */
private fun Director.diaryReadyNow(): Boolean = COOP_REQUIRED.all { diaryFilled(it.slot) }

/** 답에서 칸 값 꺼내기. "몰라"처럼 값이 없는 답은 빈 문자열이다 */
private fun diaryValueOf(r: Reply): String = when (r) {
    is Reply.Spoke -> r.value
    is Reply.Tapped -> r.value
    else -> ""
}

/** 그 칸이 이미 찼는가 */
fun Director.diaryFilled(slot: String): Boolean = when (slot) {
    "place" -> s.place != null
    "problem" -> s.problem != null
    "cause" -> s.cause != null
    "solution" -> s.solution != null
    "companion" -> s.friend != null
    "reaction" -> s.reaction != null
    else -> false
}

// ── 칸 채우기 · 출처 ────────────────────────────────────────────

/**
 * 칸 하나를 채우고 **누가 채웠는지(`by`)** 를 함께 남긴다.
 *
 * ⚠️ `by` 를 빼먹으면 이 설계가 무너진다 (일기 §8). §5가 "빈 자리를 이야기로 메워도 된다"고 말할 수 있는
 * 근거가 `by: mascot` 하나다. 안 남기면 메운 문장이 아이가 한 말과 섞여 부모 리포트가 거짓말을 시작한다.
 *
 * 협업 모드에서 **부모가 지은 자리**도 `by: mascot` 이다 — 아이가 한 말이 아니므로 수준 신호와
 * 리포트 원문 인용에서 빠져야 한다 (협업 §4-1). **`by: parent` 를 새로 만들지 않는다** — 스키마도 평가셋도 그대로다.
 *
 * 전에는 `author` 를 따로 남겨 책에 🧒/🧑 표시를 붙였다. 새 협업에서 부모는 질문만 넣고 칸을 채우지 않아
 * 표시가 모든 쪽에 🧒 로 같아졌다 — 구분하려고 만든 것이 상수가 되어 지웠다 (09-22 박진웅 결정).
 */
fun Director.setDiarySlot(slot: String, bookKey: String, value: String, line: String?, by: String) {
    when (slot) {
        "place" -> { s.place = value; s.placeLabel = value }
        "problem" -> s.problem = value
        "cause" -> { s.cause = value; s.causeLine = value }
        "solution" -> {
            s.solution = value
            s.solutionLine = line ?: value
            s.solutionItem = diaryGiveItem(value, s)
        }
        "reaction" -> s.reaction = value
        "newcomer" -> s.newcomer = value
        "companion" -> { s.friend = value; s.companionKind = value }
    }
    line?.let { s.slots[bookKey] = it }
    s.slotBy[bookKey] = by
    event("slot_filled", "slot" to slot, "value" to value, "source" to by)
    log(
        "칸 [$slot${if (bookKey != slot) "/$bookKey" else ""}] = \"$value\"  [by: $by]" + when {
            by == "mascot" -> " — 책 자막에는 나오지만 주고받기 횟수 · 수준 신호 · 리포트 원문 인용에서는 빠진다 (일기 §5-1)"
            else -> ""
        }
    )
}

// ── 장면 ───────────────────────────────────────────────────────

private val Director.diaryHero: Art get() = Art.HeroArt(s.heroAttr ?: HeroAttr())

private fun Director.diaryWorld(items: List<WorldItem>, bump: Boolean): Stage.World {
    if (bump) s.pulse++
    // 일기 배경에는 누를 수 있는 배경 속 것(핫스팟)이 없다 — 상상 세계 배경에만 좌표를 정해 두었다
    return Stage.World(items, glow = emptySet(), pulse = s.pulse)
}

/** 무대 — 배경은 아이가 말한 장소, 그 위에 주인공과 (말했다면) 같이 있던 사람 */
private fun Director.diaryStage(bump: Boolean = false): Stage {
    // On a felt kit (co-op, #222) the actors stand on the spots the kit layout keeps clear — HERO_SPOT · FRIEND_SPOT, the
    // same as a story. Elsewhere (a background picture) the spots tuned for those pictures stay.
    val kit = s.isCoop && s.sceneKit != null
    val items = buildList {
        add(if (kit) WorldItem(diaryHero, HERO_SPOT.x, 0.34f, 0.11f, depth = HERO_SPOT.depth) else WorldItem(diaryHero, 0.22f, 0.34f, 0.11f))
        s.companionArt?.let {
            add(if (kit) WorldItem(it, FRIEND_SPOT.x, 0.32f, 0.12f, depth = FRIEND_SPOT.depth) else WorldItem(it, 0.58f, 0.32f, 0.12f))
        }
    }
    return diaryWorld(items, bump)
}

suspend fun Director.sceneDiary() {
    val c = s.childName
    // 아직 아무 칸도 안 찼으면 지금이 시작이다 (시연 서랍에서 이 장면을 바로 열었을 때 15분이 지난 것이 되지 않게)
    if (s.diaryStart == 0L || s.filled == 0) {
        s.diaryStart = System.currentTimeMillis()
        s.diaryTimeUp = false
    }
    // 일기 모드는 그림일기로 — 아래 열한 걸음은 협업 모드의 길이다 (#36)
    if (!s.isCoop) { pictureDiary(); return }
    s.stage = diaryStage()
    coopIntro(c)                                // 협업 쪽은 CoopScenes.kt
    log("${COOP_STEPS.size}걸음 — 기승전결 네 자리(필수)와 꼬리질문 ${COOP_STEPS.size - COOP_REQUIRED.size}. 꼬리질문 답은 기존 슬롯의 `extra` 등에 쌓여 책의 재료가 된다")
    log("⚠️ 일기 질문은 동화 모드보다 어렵다 — 상상이 아니라 기억을 꺼내야 한다. 같은 아이가 낮은 수준으로 나올 수 있다 (일기 §4-5 · 수준 공유 여부는 §7-3 열린 항목)")
    pause(1700)

    for (step in COOP_STEPS) {
        if (diaryEnded()) break
        // 자리가 모자라 남은 부모 질문은 맺음(내일 바람) 앞에서 묻는다 — 부모 질문은 자유 꼬리 자리에만 들어간다 (10-05)
        if (step.bookKey == "keep") askLeftoverParentQuestions()
        if (diaryEnded()) break
        if (!step.ask(s)) {
            log("[${step.part} · ${step.bookKey}] 건너뜀 — 앞의 답에 물을 데가 없다 (소크라틱: 아이가 한 말에서 다음 질문이 나온다)")
            continue
        }
        // 뼈대 칸이 앞 답에서 이미 찼으면 묻지 않는다 — 「놀이터 갔는데 친구가 밀었어」로 「무슨 일」이 찼는데
        // 셋째 걸음에서 「무슨 일이 있었어?」를 또 물으면 아이는 방금 한 말을 되풀이하고, 새 답이 앞 값을 덮는다 (10-01)
        if (step.required && diaryFilled(step.slot)) {
            log("[${step.part} · ${step.bookKey}] 건너뜀 — 앞의 답에서 이미 찼다 (${s.slotBy[step.bookKey] ?: "?"}) · 같은 걸 두 번 묻지 않는다")
            s.coopCoverPart(step.variant.id)
        } else askDiaryStep(step)
        drawCoopBackground()                    // 장소 칸이 찼으면 그곳으로 배경을 그린다 — 기다리지 않는다 (10-05 · CoopServerLine.kt)
        drawFriend()                            // 같이 간 사람에 맞는 그림이 없으면 인형을 만든다 — 기다리지 않는다 (FriendArt.kt)
        // 진행 막대는 **지나온 걸음 수**로 찬다 (9/22). 칸이 찼는지로 세면, 아이가 답하지 않은
        // 선택 질문이 하나라도 있으면 마지막 질문까지 가도 막대가 끝까지 가지 않는다
        s.stepsDone++
        // 뼈대 네 자리와 부모가 넣은 질문을 다 물었으면 남은 꼬리질문은 묻지 않는다 — 부모가 길이를 정한 셈이다 (09-30 확정 · #36)
        if (s.coopQuestionsAllAsked) {
            log("뼈대 네 자리와 부모가 넣은 질문 ${s.parentQIndex}개를 다 물었다 → 남은 꼬리질문은 건너뛰고 마무리한다 (#36)")
            break
        }
    }
    askLeftoverParentQuestions()                // 맺음 걸음을 건너뛰었으면 여기서 — 다 물었으면 아무것도 안 한다
    if (s.endReason == null && diaryReadyNow()) {
        s.endReason = "story_ready"
        log("기승전결 네 자리가 다 찼다 → story_ready (일기 §3)")
    }
    // 네 자리가 덜 찼는데 부모 질문이 끝났다 — 빈 자리는 마무리에서 마스코트가 메운다
    if (s.endReason == null && s.coopQuestionsAllAsked) s.endReason = "questions_done"
    diaryEnded()
    if (s.endReason == "story_ready") diaryDrawStep()
    finishDiary()
}

/**
 * 질문 자리에는 [직접 그리기 🖍️]를 **달지 않는다** (9/21).
 *
 * 전에는 일 · 까닭 · 끝 세 자리에 그리기를 붙여 두었다. 그런데 이 셋은 "무엇을 만들어 줘"가 아니라
 * **있었던 일을 말하는** 질문이라, 그리기 버튼이 거의 모든 질문 옆에 계속 떠 있는 것처럼 보였다.
 * 그리기는 **무언가를 만들어야 하는 자리**에서만 나온다 — 일기에서는 `diaryDrawStep()`(오늘 만난 사람 그리기)이 그 자리이고,
 * 그 걸음이 부모 리포트의 '만들기' 축도 채운다 (일기 §8).
 */
private fun diaryDrawAnswer(@Suppress("UNUSED_PARAMETER") step: DiaryStep): Answer? = null

/**
 * 한 걸음 = 칸 하나 + 사다리 하나.
 *
 * 말이 없으면 **답을 고르게 하지 않고 질문을 바꾼다** (일기 §4). 사다리가 다 떨어진 자리가 `mascot_pick` 이다.
 * 말은 했는데 칸이 안 차면("몰라") 그것도 사다리를 한 칸 내려갈 이유다 — 아이가 답할 수 있는 질문이 아직 남았다.
 */
private suspend fun Director.askDiaryStep(step: DiaryStep) {
    val v = step.variant
    // 협업에서 고른 이야기가 있으면 사다리 · 시연 답 · 마스코트 채움을 그 이야기 것으로 — 뼈대는 요소별, 꼬리질문은 시제별 (CoopTemplatePack.kt)
    val pack = s.coopPartPack(step)
    var rungs = pack?.rungs ?: step.rungs(s)
    log("일기 질문 [${step.part} · ${step.bookKey}] 사다리 ${rungs.size}칸 — ${step.probe} · 지금 수준 ${s.level.label}")
    while (true) {
        val q = Question(
            text = rungs.first(),
            kind = step.kind,
            // 사다리 뒤에 그림 3장을 붙이지 않는다 — 일기에서 그림 3장은 앱이 아이 하루를 추측해 보여 주는 것이 된다 (일기 §7-6)
            noCards = true,
            ladder = rungs.drop(1),
            fallback = if (pack != null) pack.mascot else step.mascot?.invoke(s),
            spoken = pack?.answers ?: v.answers(s),
            drawAnswer = diaryDrawAnswer(step),
            extra = buildList {
                if (!s.isCoop) step.demoAnswer(s)?.let { a ->
                    add(DemoBtn("🎬 오늘 이야기 시연 답 — \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) })
                }
            },
            id = v.id,
        )
        val r = askOrCoopAsk(q)                 // 협업이면 소리 없이 부모 띠에 띄운다 (CoopScenes.kt)
        judge(v, r, q.text)
        // A tail step's answer denied Otto's premise (「안 했어」) — the slot stays empty, on to the next step (#327 ② ⚖️3)
        if (s.coopTailDenied(step)) return

        if (r is Reply.Tapped && r.byMascot) {
            if (keepChildAnswer(step)) return
            s.mascotPicks++
            setDiarySlot(step.slot, step.bookKey, diarySlotOf(r.value), diaryLineOf(r.value), "mascot")
            log("사다리가 다 떨어짐 → mascot_pick ${s.mascotPicks}회 연속 (일기 §3 · §4)")
            if (step.slot == "place") log("⚠️ 장소는 구체적으로 지어내지 않았다 — 아이가 가지 않은 곳이 그 아이의 하루로 적히면 안 된다 (일기 §3-2)")
            return
        }

        // 협업 — 부모 질문에 한 첫 답은 걸음 칸이 아니라 그 질문 칸(`parent1` …)으로 (10-05 · CoopScenes.kt)
        val parentKey = if (s.isCoop) s.coopParentAnswerKey(q.id) else null
        // 진짜 마이크 답은 대본 값(`value`)이 비어 있다 — 글자에서 칸 값을 얻는다 (#47 · CoopScenes.kt)
        val live = (r as? Reply.Spoke)?.takeIf { it.isLiveSpeech() }
        val value = if (live != null) coopLiveValue(step, q.text, live).orEmpty() else diaryValueOf(r)
        if (value.isNotEmpty()) {
            s.mascotPicks = 0                       // 연속이 끊긴다
            val by = if (r is Reply.Spoke) "child" else "card"
            val (slot, bookKey) = if (parentKey != null) "extra" to parentKey else step.slot to step.bookKey
            // 말 그대로의 답은 `칸|문장` 꼴이 아니다 — 문장 자리에도 같은 말을 넣어야 꼬리질문 답이 책에 남는다
            if (live != null) setDiarySlot(slot, bookKey, value, value, by)
            else setDiarySlot(slot, bookKey, diarySlotOf(value), diaryLineOf(value), by)
            (r as? Reply.Spoke)?.answer?.let { afterDiaryAnswer(step, it) }
            if (!step.required) mark("diarytail")
            if (r is Reply.Tapped) log("그림으로 답함 → mode: draw 로 남기고 by 는 card. 수준 신호로 세지 않는다 (일기 §5-1)")
            s.stage = diaryStage(bump = true)
            return
        }

        // 말은 했는데 칸이 안 찼다 ("몰라") — 사다리에 남은 칸이 있으면 질문을 바꿔 다시 묻는다.
        // 협업에서 아이가 진짜로 답했는데 판정이 두 번 거절했으면 더 내려가지 않는다 — 다시 묻는 건 한 번까지
        // The premise was denied and the slot is empty — keep the ladder rung and ask the premise-free question once (#327 ② §5-1)
        val premiseFree = s.coopPremiseFreeNext(step)
        if (!premiseFree && (rungs.size <= 1 || (step.required && s.coopRejectedCount(step) >= 2))) {
            if (keepChildAnswer(step)) return
            val fb = if (pack != null) pack.mascot else step.mascot?.invoke(s)
            if (fb == null) {
                // 꼬리질문은 마스코트가 지어내지 않는다 — 없으면 없는 대로 간다
                log("[${step.bookKey}] 끝까지 안 나옴 → 지어내지 않고 넘어간다 (벌점 · 아쉬움 표현 없음 · 구현대본 §5)")
                return
            }
            s.mascotPicks++
            say("혹시 ${fb.text}일까? 그렇게 해 볼게!")
            setDiarySlot(step.slot, step.bookKey, diarySlotOf(fb.value), diaryLineOf(fb.value), "mascot")
            log("사다리를 다 내려왔는데도 칸이 비었다 → mascot_pick ${s.mascotPicks}회 연속 (일기 §3)")
            pause(1500)
            return
        }
        if (premiseFree) log("[${step.bookKey}] premise denied → same ladder rung, premise-free question once (#327 ② §5-2)")
        else {
            rungs = rungs.drop(1)
            log("말은 했지만 칸이 안 찼다 → 답을 고르게 하지 않고 사다리 한 칸 아래 질문으로 바꾼다 (일기 §4)")
        }
        pause(700)
    }
}

/**
 * 협업 — 사다리 끝에서 마스코트가 짓기 전에, 판정이 거절한 아이 답이 있으면 그 말을 칸에 넣는다 (10-03 실기기).
 * 넣었으면 true. 뼈대 칸만(마스코트가 채우는 자리) — 꼬리질문은 원래 아이 말을 그대로 받는다
 */
private fun Director.keepChildAnswer(step: DiaryStep): Boolean {
    if (!step.required) return false
    val said = s.coopRejectedAnswer(step) ?: return false
    s.mascotPicks = 0
    setDiarySlot(step.slot, step.bookKey, said, said, "child")
    log("[${step.bookKey}] 판정은 이 칸 답이 아니라고 했지만 아이가 진짜로 한 말 \"$said\" 을 넣는다 — 마스코트가 지어 채우지 않는다")
    s.stage = diaryStage(bump = true)
    return true
}

/** 말로 답했을 때 따라오는 것들 — 마음 · 이름 사전 · 같이 있던 사람 · 순차 답 함정 */
private fun Director.afterDiaryAnswer(step: DiaryStep, a: Answer) {
    val c = s.childName

    // 마음을 말했으면 선택 칸 reaction 이 찬다. 마음 말하기는 수준 판단에 쓰지 않는다 (일기 §2-2 · 안치영 #4)
    if (a.emo.isNotEmpty() && s.reaction == null && step.slot != "reaction") {
        val felt = feelingPhrase(a.emo)                 // 서버 판정은 「신나다」 · 「떨려」 꼴도 준다 — 「신나다던」이 되지 않게 (CoopReport.kt)
        setDiarySlot("reaction", "reaction", "$felt 마음", "$c${eun(c)} $felt 마음이 한참 남았어요", "child")
        log("묻지 않았는데 마음을 말했다 → 선택 칸 reaction 이 찼고, 그 걸음은 건너뛴다 (같은 걸 두 번 묻지 않는다)")
    }

    // 같이 있던 사람 — 이 한 칸이 뒤의 질문을 살린다
    if (step.slot == "companion" && a.kind.isNotBlank()) {
        s.companionKind = a.kind
        // 실명은 폰 안 이름 사전에만. 마스코트는 실명으로 부르고 LLM에 보내는 문장에서는 {친구1}로 가린다 (일기 §6 · 조사3 §3-2)
        if (isRealName(a.kind) && s.friendName.startsWith("{")) {
            s.friendName = a.kind
            event("slot_filled", "slot" to "name", "value" to a.kind, "source" to "child")
            s.slotBy["name"] = "child"
            log("이름 \"${a.kind}\" → 폰 안 이름 사전에 넣고 LLM에는 {친구1}로만 보낸다. 일기 모드는 어린이집 친구 · 선생님이 나와 이름 사전 항목이 늘어난다 (일기 §6)")
        }
    }

    // ⚠️ 판정이 가장 많이 틀리는 자리 — 순차 답을 까닭으로 세면 안 된다 (일기 §4-4)
    if (step.slot == "solution" && a.isSequential()) {
        log("⚠️ \"${a.text}\" 는 일이 일어난 차례를 말한 것이다. 까닭(S1)이 아니라 잇는 말(A1)로만 센다 — 평가셋의 '-서' 함정과 같은 지점 (일기 §4-4 · eval/평가셋_라벨링_지침.md §2-8)")
    }
}

/**
 * 선택 칸 `newcomer` — 오늘 만난 사람을 그린다 (일기 §2-2). 그린 그림은 등장 쪽 한 장이 된다.
 * 아무도 나오지 않은 날에는 묻지 않는다 — 없는 친구를 앱이 만들어 내지 않는다 (일기 §3-2).
 */
private suspend fun Director.diaryDrawStep() {
    val f = s.friendCallName
    if (s.companionKind.isBlank() || "혼자" in s.companionKind || s.newcomer != null) return
    s.stage = Stage.DrawPad()
    say(s.coopDrawLine(f) ?: "오늘 만난 $f${eul(f)} 그려 줄래?")   // 협업 곧 해요 · 좋아해요는 「오늘 만난」이 아니다 (CoopReport.kt)
    inputs(false, false)
    buttons(
        DemoBtn("✅ 지금 그린 그림으로 완료") { send(Reply.Tapped("done", "완료")) },
        DemoBtn("🙅 지금은 안 그릴래") { send(Reply.Tapped("skip", "안 그림")) },
    )
    if (awaitValue("done", "skip") == "skip" || s.drawing.isEmpty()) {
        s.drawing.clear()
        log("안 그림 → 아이가 말한 사람의 프리셋 그림으로 간다. 선택 칸이라 책은 그대로 나온다 (일기 §2-2)")
        return
    }
    s.reactions++
    event("make", "kind" to "draw")
    setDiarySlot("newcomer", "newcomer", "$f (아이 그림)", null, "child")
    log("아이 그림은 원본 그대로 책에 들어간다 · AI로 다시 그리지 않는다 (27) → 부모 리포트의 '만들기' 축이 빈칸이 되지 않는다 (일기 §8)")
    s.stage = Stage.Show(s.friendArt, "${s.childName}${ga(s.childName)} 그린 $f")
    say("잘 그렸다! 책에 그대로 넣을게.")
    mark("diarydraw")
    pause(1500)
}

/** 모인 답변으로 책을 만든다 — **책은 언제나 나온다** (일기 §5 · 구현대본 §5) */
internal suspend fun Director.finishDiary() {
    buttons()
    inputs(false, false)
    s.parentCard = null; s.parentAsk = null
    if (s.endReason == null) s.endReason = "story_ready"
    val bySelf = COOP_REQUIRED.count { s.slotBy[it.bookKey] == "child" || s.slotBy[it.bookKey] == "card" }
    val hasChildSeed = s.slotBy.values.any { it == "child" || it == "card" }

    // 필수 네 칸을 못 채워도 꼬리질문에 아이의 답이 있으면 그 말을 재료로 책을 만든다 (일기 §5 · §7-4).
    if (!hasChildSeed) {
        say("오늘은 여기까지 하고 잘까? 아니면 우리 이야기를 하나 지어 볼까?")
        log("⚠️ 씨앗이 0개다 — §5(모인 답변으로 만들기)를 쓸 수 없는 유일한 경우. 끝낼지 동화 모드로 넘길지는 **아직 안 정했다** (일기 §7-4 · 남은 일 §9-4)")
        buttons(
            DemoBtn("🌙 오늘은 여기까지 — 끝내기") { send(Reply.Tapped("home", "끝내기")) },
            DemoBtn("📖 동화 모드로 넘기기") { send(Reply.Tapped("story", "동화 모드")) },
        )
        if (awaitValue("home", "story") == "story") {
            s.mode = StoryMode.STORY
            s.endReason = null
            s.place = null; s.problem = null; s.cause = null; s.solution = null; s.reaction = null; s.friend = null
            listOf("place", "problem", "cause", "solution", "reaction", "companion", "detail", "said", "after", "keep")
                .forEach { s.slots.remove(it); s.slotBy.remove(it) }
            s.placeLabel = null; s.mascotPicks = 0; s.companionKind = ""
            log("동화 모드로 넘어간다 — 같은 세션 안에서 재료만 상상으로 바꾼다")
            go(Scene.PLACE)
        } else {
            log("오늘은 여기까지 — 벌점도 아쉬움 표현도 없다 (구현대본 §5)")
            goHome()
        }
        return
    }

    // 빈 자리는 LLM이 이야기로 메운다. 메운 자리는 by: mascot 이다 (일기 §5 · §5-1)
    COOP_REQUIRED.forEach { st ->
        if (!diaryFilled(st.slot)) {
            val a = st.mascot?.invoke(s) ?: return@forEach
            setDiarySlot(st.slot, st.bookKey, diarySlotOf(a.value), diaryLineOf(a.value), "mascot")
            log("빈 칸 [${st.slot}] 은 이야기로 메운다 — 이 모드는 일기가 아니라 동화책이다. 아이 말은 씨앗이고 나머지는 원래 이야기다 (일기 §5)")
        }
    }
    mark("diary")
    val why = when (s.endReason) {
        "questions_done" -> "부모 질문을 다 물음 (#36)"
        "parent_stop" -> "부모 「그만하기」 (#36)"
        else -> "story_ready (기승전결 네 자리)"
    }
    val tails = COOP_STEPS.filterNot { it.required }.count { s.slots[it.bookKey] != null }
    log("일기 모드 끝 — 끝난 조건: $why · 아이 · 카드가 채운 필수 칸 $bySelf/4 · 꼬리질문으로 더 모은 문장 ${tails}개 (이만큼 마스코트가 메울 자리가 줄었다)")
    coopFinishLog()                             // 협업 쪽은 CoopScenes.kt (진웅)
    say("오늘 이야기가 다 모였어! 이제 ${if (s.isCoop) "이야기책" else "동화책"}으로 만들어 줄게.")   // 같이 만들기는 이야기책 (#98)
    pause(2000)
    go(Scene.MAKING)
}

// 부모 협업 모드의 코드는 `CoopScenes.kt` 로 옮겼다 (09-22) —
// 일기와 협업을 다른 사람이 맡기로 해서 한 파일을 둘이 고치지 않게 갈랐다.
// 이 파일에 남은 갈고리는 셋뿐이다: coopIntro · askOrCoopAsk · coopFinishLog
