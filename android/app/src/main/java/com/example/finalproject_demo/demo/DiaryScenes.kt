package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.HeroAttr

/*
 * 장면 3′ — 오늘 있었던 일 (일기 모드 · 부모 협업 모드).
 *
 * 동화 모드의 질문 자리(장면 3~10)를 이 하나가 대신한다. **새 화면은 없다** —
 * 질문 세트와 칸 목록만 갈아끼웠다 (일기 §1). 협업 모드는 여기에 **부모 띠** 하나를 더 붙인다 (협업 §3).
 *
 * 묻지 않는 칸: `sound`(공룡 소리는 상상 세계의 것) · `adult`(협업 모드에서만 찬다).
 */

// ── 끝나는 조건 ────────────────────────────────────────────────

/** 끝나는 조건 셋 — 동화 모드와 **같은 셋**을 쓴다. 새로 만들지 않았다 (guidelines/2 §1-1 · 일기 §3) */
fun Director.diaryEnded(): Boolean {
    if (s.endReason != null) return true
    // mascot_pick 을 먼저 본다 — 마스코트가 메워서 네 자리가 "찬" 것을 story_ready 로 읽으면 기록이 거짓이 된다
    if (s.mascotPicks >= 2) {
        s.endReason = "mascot_pick"
        log("mascot_pick 2회 연속 = 사다리가 다 떨어진 지점 → 모인 답변으로 책을 만든다 (일기 §3 · §5)")
        return true
    }
    val over15 = s.diaryTimeUp || (s.diaryStart > 0L && System.currentTimeMillis() - s.diaryStart >= 15 * 60 * 1000)
    if (over15) {
        s.endReason = "timeout"
        log(
            s.coopTimeUpNote()
        )
        return true
    }
    return false
}

/** 기승전결 네 자리가 다 찼나 — 열 걸음을 다 물은 뒤에만 본다 */
private fun Director.diaryReadyNow(): Boolean = DIARY_REQUIRED.all { diaryFilled(it.slot) }

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
 * 리포트 원문 인용에서 빠져야 한다. 누가 지었는지는 [DemoState.author] 에 따로 남겨 책의 작은 표시가 된다
 * (협업 §4-1 · §6). **`by: parent` 를 새로 만들지 않는다** — 스키마도 평가셋도 그대로다.
 */
fun Director.setDiarySlot(slot: String, bookKey: String, value: String, line: String?, by: String, author: String = by) {
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
    if (slot in setOf("place", "problem", "cause", "solution")) s.author[slot] = author
    event("slot_filled", "slot" to slot, "value" to value, "source" to by)
    log(
        "칸 [$slot${if (bookKey != slot) "/$bookKey" else ""}] = \"$value\"  [by: $by]" + when {
            author == "adult" -> " — 부모가 지은 자리다. 책에 작은 표시로 남고 수준 신호 · 리포트 인용에는 안 들어간다 (협업 §4-1 · §6)"
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
    val items = buildList {
        add(WorldItem(diaryHero, 0.22f, 0.34f, 0.11f))
        s.companionArt?.let { add(WorldItem(it, 0.58f, 0.32f, 0.12f)) }
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
    s.stage = diaryStage()

    if (s.isCoop) {
        coopIntro(c)                            // 협업 쪽은 CoopScenes.kt (진웅)
    } else {
        // 마스코트 첫 대사 — 아이 화면에 "일기"라는 말을 쓰지 않는다 (일기 §0)
        say("$c${ya(c)}, 오늘 뭐 했어? 나한테 들려줄래?")
        log("일기 모드 S3′ — 같은 러너 · 같은 판정 · 같은 무응답 흐름을 쓴다. 다른 것은 질문 세트와 칸 목록뿐이다 (일기 §1)")
    }
    log("열 걸음 — 기승전결 네 자리(필수)와 꼬리질문 여섯. 꼬리질문 답은 슬롯 12종의 `extra` 에 원문으로 쌓여 책의 재료가 된다")
    log("⚠️ 일기 질문은 동화 모드보다 어렵다 — 상상이 아니라 기억을 꺼내야 한다. 같은 아이가 낮은 수준으로 나올 수 있다 (일기 §4-5 · 수준 공유 여부는 §7-3 열린 항목)")
    pause(1700)

    for (step in DIARY_STEPS) {
        if (diaryEnded()) break
        if (!step.ask(s)) {
            log("[${step.part} · ${step.bookKey}] 건너뜀 — 앞의 답에 물을 데가 없다 (소크라틱: 아이가 한 말에서 다음 질문이 나온다)")
            continue
        }
        askDiaryStep(step)
    }
    if (s.endReason == null && diaryReadyNow()) {
        s.endReason = "story_ready"
        log("기승전결 네 자리가 다 찼다 → story_ready (일기 §3)")
    }
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
    var rungs = step.rungs(s)
    log("일기 질문 [${step.part} · ${step.bookKey}] 사다리 ${rungs.size}칸 — ${step.probe} · 지금 수준 ${s.level.label}")
    while (true) {
        val q = Question(
            text = rungs.first(),
            kind = step.kind,
            // 사다리 뒤에 그림 3장을 붙이지 않는다 — 일기에서 그림 3장은 앱이 아이 하루를 추측해 보여 주는 것이 된다 (일기 §7-6)
            noCards = true,
            ladder = rungs.drop(1),
            fallback = step.mascot?.invoke(s),
            spoken = v.answers(s),
            drawAnswer = diaryDrawAnswer(step),
            extra = listOf(
                DemoBtn("⏱ (시연) 15분 지난 것으로 — 끝나는 조건 셋째") { s.diaryTimeUp = true; send(Reply.Silent) },
            ),
            id = v.id,
        )
        val r = askOrCoopAsk(q)                 // 협업이면 소리 없이 부모 띠에 띄운다 (CoopScenes.kt)
        judge(v, r, q.text)

        if (r is Reply.Tapped && r.byMascot) {
            s.mascotPicks++
            setDiarySlot(step.slot, step.bookKey, diarySlotOf(r.value), diaryLineOf(r.value), "mascot")
            log("사다리가 다 떨어짐 → mascot_pick ${s.mascotPicks}회 연속 (일기 §3 · §4)")
            if (step.slot == "place") log("⚠️ 장소는 구체적으로 지어내지 않았다 — 아이가 가지 않은 곳이 그 아이의 하루로 적히면 안 된다 (일기 §3-2)")
            return
        }

        val value = diaryValueOf(r)
        if (value.isNotEmpty()) {
            s.mascotPicks = 0                       // 연속이 끊긴다
            val by = if (r is Reply.Spoke) "child" else "card"
            setDiarySlot(step.slot, step.bookKey, diarySlotOf(value), diaryLineOf(value), by)
            (r as? Reply.Spoke)?.answer?.let { afterDiaryAnswer(step, it) }
            if (!step.required) mark("diarytail")
            if (r is Reply.Tapped) log("그림으로 답함 → mode: draw 로 남기고 by 는 card. 수준 신호로 세지 않는다 (일기 §5-1)")
            s.stage = diaryStage(bump = true)
            return
        }

        // 말은 했는데 칸이 안 찼다 ("몰라") — 사다리에 남은 칸이 있으면 질문을 바꿔 다시 묻는다
        if (rungs.size <= 1) {
            val fb = step.mascot?.invoke(s)
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
        rungs = rungs.drop(1)
        log("말은 했지만 칸이 안 찼다 → 답을 고르게 하지 않고 사다리 한 칸 아래 질문으로 바꾼다 (일기 §4)")
        pause(700)
    }
}

/** 말로 답했을 때 따라오는 것들 — 마음 · 이름 사전 · 같이 있던 사람 · 순차 답 함정 */
private fun Director.afterDiaryAnswer(step: DiaryStep, a: Answer) {
    val c = s.childName

    // 마음을 말했으면 선택 칸 reaction 이 찬다. 마음 말하기는 수준 판단에 쓰지 않는다 (일기 §2-2 · 안치영 #4)
    if (a.emo.isNotEmpty() && s.reaction == null && step.slot != "reaction") {
        setDiarySlot("reaction", "reaction", "${a.emo}던 마음", "$c${eun(c)} ${a.emo}던 마음이 한참 남았어요", "child")
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
    say("오늘 만난 $f${eul(f)} 그려 줄래?")
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
private suspend fun Director.finishDiary() {
    buttons()
    inputs(false, false)
    s.parentCard = null; s.parentAsk = null
    if (s.endReason == null) s.endReason = "story_ready"
    val bySelf = DIARY_REQUIRED.count { s.slotBy[it.bookKey] == "child" || s.slotBy[it.bookKey] == "card" }

    // 일기 §7-4 — 칸이 하나도 안 찼을 때(완전 무응답)만 남는 문제. 어느 쪽인지는 아직 정하지 않았다
    if (bySelf == 0 && s.author.values.none { it == "adult" }) {
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
            s.placeLabel = null; s.mascotPicks = 0; s.companionKind = ""; s.author.clear()
            log("동화 모드로 넘어간다 — 같은 세션 안에서 재료만 상상으로 바꾼다")
            go(Scene.PLACE)
        } else {
            log("오늘은 여기까지 — 벌점도 아쉬움 표현도 없다 (구현대본 §5)")
            goHome()
        }
        return
    }

    // 빈 자리는 LLM이 이야기로 메운다. 메운 자리는 by: mascot 이다 (일기 §5 · §5-1)
    DIARY_REQUIRED.forEach { st ->
        if (!diaryFilled(st.slot)) {
            val a = st.mascot?.invoke(s) ?: return@forEach
            setDiarySlot(st.slot, st.bookKey, diarySlotOf(a.value), diaryLineOf(a.value), "mascot")
            log("빈 칸 [${st.slot}] 은 이야기로 메운다 — 이 모드는 일기가 아니라 동화책이다. 아이 말은 씨앗이고 나머지는 원래 이야기다 (일기 §5)")
        }
    }
    mark("diary")
    val why = when (s.endReason) {
        "mascot_pick" -> "mascot_pick 2회 연속"
        "timeout" -> "15분 경과"
        else -> "story_ready (기승전결 네 자리)"
    }
    val tails = listOf("companion", "detail", "reaction", "said", "after", "keep").count { s.slots[it] != null }
    log("일기 모드 끝 — 끝난 조건: $why · 아이 · 카드가 채운 필수 칸 $bySelf/4 · 꼬리질문으로 더 모은 문장 ${tails}개 (이만큼 마스코트가 메울 자리가 줄었다)")
    coopFinishLog()                             // 협업 쪽은 CoopScenes.kt (진웅)
    say("오늘 이야기가 다 모였어! 이제 동화책으로 만들어 줄게.")
    pause(2000)
    go(Scene.MAKING)
}

// 부모 협업 모드의 코드는 `CoopScenes.kt` 로 옮겼다 (09-22) —
// 일기와 협업을 다른 사람이 맡기로 해서 한 파일을 둘이 고치지 않게 갈랐다.
// 이 파일에 남은 갈고리는 셋뿐이다: coopIntro · askOrCoopAsk · coopFinishLog
