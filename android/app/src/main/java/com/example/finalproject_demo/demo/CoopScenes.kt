package com.example.finalproject_demo.demo

/**
 * 부모 협업 모드 — **부모가 미리 적어 둔 질문을 마스코트가 아이에게 묻는 모드**다
 * (부모협업모드_설계.md §0 9/22 개정 · 부모협업모드_구현설계.md).
 *
 * 왜 이 파일이 따로 있나 (09-22)
 *   협업은 일기 모드의 흐름을 그대로 쓴다. 그래서 처음에는 `DiaryScenes.kt` 안에
 *   `isCoop` 분기로 들어가 있었다. 그런데 **일기와 협업을 다른 사람이 맡기로 하면서**
 *   한 파일을 둘이 고치게 됐다 — `guidelines/9_역할과_작업.md` §9-4.
 *   그래서 협업 쪽만 뽑아냈다. 일기 쪽에는 **갈고리만 남는다.**
 *
 *   ⚠️ **반대로 뽑지 않았다.** 일기가 본체이고 협업이 그 위에 얹히는 관계라,
 *   협업을 빼도 일기는 그대로 돌아간다. 일기를 빼면 협업은 돌아가지 않는다.
 *
 * 두 갈래가 있다 (09-22 오후)
 *   **부모가 질문을 넣어 뒀으면** — 새 흐름. 그 걸음의 자리에 맞는 부모 질문을 **마스코트가 소리 내어 읽는다**
 *   (부모가 옆에 없는 것이 기본이라 소리가 없으면 아이가 질문을 못 받는다 · 구현설계 §1-①).
 *   부모 질문이 없는 자리와 소진된 뒤는 마스코트가 그 자리에 필요한 질문을 그대로 묻는다 (§1-② —
 *   지금은 일기 사다리가 대역이고, LLM이 붙으면 찬 칸을 보고 만든 질문이 들어온다).
 *
 *   **하나도 안 넣었으면** — 9/21 옛 흐름 그대로. 질문이 소리 없이 부모 띠에 뜨고 어른이 읽는다.
 *   조장이 홀더를 넣으며 "비어 있으면 옛 흐름으로 떨어진다"고 정한 것이고(`Model.kt` `parentQuestions`),
 *   `DiaryFlowTest`의 협업 검사 둘이 이 흐름을 본다. 마스코트가 읽는 쪽으로 완전히 넘기는 것은
 *   `Director.kt`의 「띠·말풍선 번갈아 띄우기」와 그 검사를 같이 고칠 때다 — 조장 · 치영과 맞춘 뒤.
 *
 * **받아주기 · 되돌려주기 · 낭독은 넘기지 않는다.** 질문의 저자만 부모다 (§2-2).
 */

/** 부모 질문 자리 — 홀더가 `List<String>` 이라 **위치**로 정한다. 자리 필드가 생기면(조장 요청) 그쪽으로 옮긴다 */
private val COOP_PART_SLOTS = listOf("place", "problem", "cause", "solution")

/** 이 걸음의 자리(`diary_<bookKey>`). 기승전결 네 자리면 0~3, 꼬리질문이면 null */
private fun partIndexOf(q: Question): Int? =
    q.id.removePrefix("diary_").let { key -> COOP_PART_SLOTS.indexOf(key).takeIf { it >= 0 } }

/** 부모가 실제로 넣은 질문이 하나라도 있나 — 빈 줄은 안 센다 (입력 화면이 빈 줄을 남겨 둔다) */
val DemoState.hasCoopQuestions: Boolean get() = parentQuestions.any { it.isNotBlank() }

/**
 * 이 걸음에 쓸 부모 질문. 네 자리는 그 자리 것, 꼬리질문은 다섯째부터를 **순서대로** 하나씩 쓴다.
 * 쓴 것은 `parentQIndex`(쓴 개수)로 센다 — 홀더의 `nextParentQuestion()` 은 순서 소비라 자리 매핑과 안 맞아 쓰지 않는다.
 *
 * **한 걸음에 한 번만 쓴다.** 아이가 "몰라"라고 하면 일기 흐름이 같은 걸음을 사다리 한 칸 아래 질문으로 다시 묻는데,
 * 그때는 부모 질문을 고집하지 않고 **앱의 쉬운 질문**이 나가야 한다 (구현설계 §1-②: 못 답하면 상황에 맞춰 바꿔 묻는다).
 */
private fun DemoState.takeCoopQuestion(q: Question): String? {
    val track = coopTrack
    if (q.id in track.askedSteps) return null          // 다시 묻는 자리 — 앱 질문으로
    val idx = partIndexOf(q)
    val text = if (idx != null) {
        parentQuestions.getOrNull(idx)?.takeIf { it.isNotBlank() }
    } else {
        // 꼬리질문 자리 — 자유 질문 중 아직 안 쓴 첫 것
        parentQuestions.drop(COOP_PART_SLOTS.size).filter { it.isNotBlank() }.getOrNull(track.freeUsed)
            ?.also { track.freeUsed++ }
    }
    track.askedSteps += q.id
    if (text != null) parentQIndex++
    return text
}

/**
 * 부모가 넣은 질문 하나에 아이가 뭐라고 했나 — 부모 리포트 「어른이 넣어 둔 질문에 한 답」의 재료.
 * `by` 는 출처 3종 그대로(`child` · `card` · `mascot`), 답이 없으면 null (구현설계 §2-3).
 */
data class CoopAsked(val question: String, val answer: String?, val by: String?)

/** 이 이야기에서 어느 걸음에 이미 물었고, 자유 질문을 몇 개 썼고, 질문마다 아이가 뭐라고 했나 */
private class CoopTrack {
    val askedSteps = mutableSetOf<String>()
    var freeUsed = 0
    val asked = mutableListOf<CoopAsked>()
}

/**
 * `Model.kt` 에 칸을 더하지 않고 상태마다 붙여 둔다(약한 참조). 이야기가 시작될 때([coopIntro]) 새로 만든다.
 * 홀더에 자리·답 칸이 들어오면(조장) 이 보조 기록은 홀더 쪽으로 옮긴다.
 */
private val trackByState = java.util.WeakHashMap<DemoState, CoopTrack>()
private val DemoState.coopTrack: CoopTrack
    get() = trackByState[this] ?: CoopTrack().also { trackByState[this] = it }

private fun DemoState.newCoopTrack() { trackByState[this] = CoopTrack() }

/** 이 이야기에서 부모 질문에 아이가 한 답들 — 부모 리포트가 읽는다. 이야기가 끝나도 남는다(다음 이야기가 시작되면 새로) */
val DemoState.coopAsked: List<CoopAsked> get() = trackByState[this]?.asked.orEmpty()

/** 협업 모드에서만 붙는 첫 안내. 일기 모드는 이 함수를 부르지 않는다. */
suspend fun Director.coopIntro(childName: String) {
    s.newCoopTrack()
    if (s.hasCoopQuestions) {
        say("${childName}${ya(childName)}, 어른이 물어보고 싶은 게 있대! 내가 대신 물어볼게.")
        log("부모 협업 모드 — 부모가 미리 넣어 둔 질문 ${s.parentQuestions.count { it.isNotBlank() }}개를 **마스코트가 소리 내어 읽는다.** 받아주기 · 되돌려주기 · 낭독도 마스코트 (구현설계 §1-① · 설계 §2-2)")
        log("부모 질문이 없는 자리와 소진 뒤는 마스코트가 그 자리에 필요한 질문을 그대로 묻는다 — 지금은 일기 사다리, LLM이 붙으면 찬 칸을 보고 만든 질문 (구현설계 §1-②)")
        return
    }
    say("오늘은 ${childName}랑 어른이 같이 만들 거야! 질문은 아래에 띄워 줄게.")
    log("부모 협업 모드 — 넣어 둔 질문이 없어 옛 흐름: AI가 질문 카드를 띄우면 **부모가 읽고 자기 말로 묻는다.** (9/21 설계 · 홀더가 비면 이쪽으로 떨어진다)")
    log("⚠️ 되돌려주기를 넘기지 않는 이유: 발음이 어긋났을 때 고쳐 말해 주되 **지적하지 않는 것**이 부모가 가장 못하는 일이다 (협업 §2-2)")
}

/**
 * 질문을 던지는 갈고리. 일기 모드는 이것만 부르고 협업인지 아닌지 모른다.
 *
 * 전에는 띠에 [다르게 물어볼래] · [내가 답할래] 두 버튼을 달아 부모가 사다리를 손으로 내리게 했다.
 * 그런데 버튼을 고르는 것이 일이 되어 아이와 이야기하는 흐름이 끊겼고, 무엇보다 그 버튼이 보내는
 * `coop:next` · `coop:adult` 값이 칸으로 새어 들어가 화면에 `coop:adult` 라는 글자가 그대로 나왔다.
 * 지금은 버튼이 없고, 사다리도 무응답도 [Director.ask] 가 동화 모드와 똑같이 처리한다.
 */
suspend fun Director.askOrCoopAsk(q: Question): Reply = when {
    !s.isCoop -> ask(q)
    s.hasCoopQuestions -> coopAskFromParent(q)
    else -> coopAsk(q)
}

/**
 * 새 흐름 — 이 자리에 부모 질문이 있으면 **그 글로, 마스코트 목소리로** 묻는다.
 * 사다리(쉬운 질문)는 앱 것을 그대로 둔다: 아이가 답을 못 하면 부모 질문을 고집하지 않고 앱이 더 쉬운 말로 바꿔 묻는다.
 * 없으면 앱 질문을 마스코트가 묻는다 — 소진 뒤 "필요한 질문을 만들어 묻는다"의 지금 모습이다.
 */
private suspend fun Director.coopAskFromParent(q: Question): Reply {
    val mine = s.takeCoopQuestion(q)
    mark("coop")
    if (mine == null) {
        log("[${q.id}] 이 자리에 부모 질문이 없다 → 마스코트가 필요한 질문을 그대로 묻는다: \"${q.text}\"")
        return ask(q)
    }
    log("[${q.id}] 부모가 넣어 둔 질문 → 마스코트가 읽는다: \"$mine\" (앱 질문 \"${q.text}\" 은 사다리 뒤에 남는다)")
    val r = ask(q.copy(text = mine, silent = false))
    // 부모가 지은 질문이라는 것은 기록에 남는다 — payload.speaker: adult. `by` 3종은 늘리지 않는다 (협업 §4-1 · §8)
    event("utterance", "speaker" to "adult", "mode" to "typed", "text" to mine)
    s.coopTrack.asked += when (r) {
        is Reply.Spoke -> CoopAsked(mine, r.text, "child")
        is Reply.Tapped -> CoopAsked(mine, r.label, if (r.byMascot) "mascot" else "card")
        else -> CoopAsked(mine, null, null)
    }
    s.partnerTurns++
    s.adultLine = mine          // 부모 리포트의 "어른이 한 말" — 마지막으로 쓴 부모 질문
    if (r is Reply.Spoke) coopReact(r)
    return r
}

/** 옛 흐름 — 질문 카드를 **소리 없이** 부모 띠에 띄우고 기다린다. 넣어 둔 질문이 하나도 없을 때만. */
private suspend fun Director.coopAsk(q: Question): Reply {
    s.parentRung = 0
    s.parentHasMore = false
    mark("coop")
    val r = ask(q.copy(silent = true))
    s.parentCard = null; s.parentAsk = null
    if (r is Reply.Spoke) {
        // 부모가 읽고 물은 질문도 기록에 남는다 — payload.speaker: adult. `by` 3종은 늘리지 않는다 (협업 §4-1 · §8)
        event("utterance", "speaker" to "adult", "mode" to "voice", "text" to q.text)
        s.partnerTurns++
        s.adultLine = q.text        // 부모 리포트의 "어른이 한 말" — 어른이 읽고 물어본 마지막 질문
        coopReact(r)
    }
    return r
}

/**
 * 아이가 말한 뒤 마스코트가 하는 세 걸음 — **실제 파이프라인과 같은 순서**다 (09-22 진웅).
 *
 * ```
 * 고정 리액션   즉시.  미리 합성해 둔 대사 — 받아쓰기 → 판정 → 생성이 오는 동안 메우는 말 (guidelines/6 §6-4 filler)
 *    ↓ [LLM 응답이 오는 자리 — 실측 약 6초 (guidelines/9 §9-8). 데모에서는 LLM_GAP 만큼만]
 * 받아주기      아이 말을 되비춘다 (프롬프트 §3 「받아주기」). 지금은 [coopEcho] 대본, LLM이 붙으면 §3 출력이 여기 온다
 *    ↓
 * 다음 질문     부모가 넣은 질문이거나 사다리 질문 — askDiaryStep 이 이어서 묻는다
 * ```
 *
 * 고정 리액션에는 질문도 "더 말해 줘" 같은 재촉도 넣지 않는다 — 다음 질문이 바로 이어지므로 겹친다.
 */
private val COOP_ACKS = listOf("그랬구나~", "우와!", "응응, 듣고 있어.", "오~ 그랬구나.")

/** 고정 리액션 뒤 받아주기가 오기까지 — 실제로는 LLM 왕복이다. 데모라 짧게 둔다 (`s.speed` 로 배속된다) */
private const val LLM_GAP_MS = 1400L

/** 받아주기·질문 없이 넘어가는 답 — "몰라"를 "몰구나"로 되비추면 안 된다 */
private val NO_ECHO = setOf("몰라", "응", "아니", "싫어", "글쎄", "네", "어", "음", "없어")

/**
 * 받아주기 대본 — 아이 말을 **고치지 않고** 어미만 "-구나"로 바꿔 되비춘다. 8어절이 넘으면 뒤쪽 8어절만 (동사가 뒤에 온다).
 * LLM이 붙기 전의 대역이다. 되비출 것이 아니면(짧은 대답 · 빈 말) null — 그때는 고정 리액션만 한다.
 */
fun coopEcho(raw: String): String? {
    val words = raw.trim().trimEnd('!', '.', '?', '~').split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return null
    val t = words.takeLast(8).joinToString(" ")
    if (t in NO_ECHO) return null
    return when {
        t.endsWith("어요") || t.endsWith("아요") -> t.dropLast(2) + "구나!"
        (t.endsWith("어") || t.endsWith("아")) && t.length > 1 -> t.dropLast(1) + "구나!"
        else -> t + if (bat(t)) "이구나!" else "구나!"
    }
}

private suspend fun Director.coopReact(r: Reply.Spoke) {
    say(COOP_ACKS.random())
    val echo = coopEcho(r.text) ?: run { pause(1200); return }
    pause(LLM_GAP_MS)
    say(echo)
    pause(1200)
}

/** 끝났을 때 협업만 남기는 표시와 기록. 일기 모드에서는 아무 일도 하지 않는다. */
fun Director.coopFinishLog() {
    if (!s.isCoop) return
    mark("coop")
    if (s.hasCoopQuestions) {
        log("같이 짓기 — 부모가 넣어 둔 질문 ${s.parentQuestions.count { it.isNotBlank() }}개 중 ${s.parentQIndex}개를 마스코트가 물었다. 부모 리포트 「함께하기」 축의 재료다 (협업 §4-2)")
        // 이야기마다 비운다 — 오늘 넣은 질문이 내일 또 나오면 안 된다(조장). 비우는 자리는 **이야기가 끝난 여기**다.
        // 리포트에 남는 것은 `partnerTurns` · `adultLine` · `utterance speaker=adult` 이벤트라 홀더가 비어도 된다.
        s.clearParentQuestions()
        return
    }
    // ⚠️ "어른이 지은 자리"를 세지 않는다 — [내가 답할래]를 뺀 뒤로 그 수는 **언제나 0**이라
    // "어른이 아무것도 안 했다"로 읽힌다. 실제로는 어른이 **모든 질문을 읽어 주었다** (9/21).
    val byChild = s.slotBy.values.count { it == "child" }
    log("같이 짓기 — 어른이 읽어 준 질문 ${s.partnerTurns}번 · 그중 아이가 자기 말로 채운 자리 $byChild. 채점처럼 보이면 안 되므로 책에는 남기지 않는다 (협업 §6)")
}

/** 15분이 지나 끝날 때의 한 줄 — 협업은 부모가 질문을 고르는 시간이 들어가 더 빨리 닿는다 (협업 §9-3). */
fun DemoState.coopTimeUpNote(): String =
    if (isCoop) "15분 경과 → 끝. ⚠️ 협업 모드는 부모가 질문을 고르는 시간이 들어가 15분에 더 빨리 닿는다 (협업 §9-3)"
    else "15분 경과 → 끝. ⚠️ 15분은 동화 모드 기준이라 취침 루틴에는 길 수 있다 (일기 §7-5)"

/** 책 이름 — 협업이면 "같이 지은"을 붙인다. */
fun coopBookName(s: DemoState): String =
    if (s.isCoop) "같이 지은 오늘 이야기" else "오늘 이야기"

