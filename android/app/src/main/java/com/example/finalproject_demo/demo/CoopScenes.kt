package com.example.finalproject_demo.demo

/**
 * 부모 협업 모드 — **질문하는 사람만 바꾸는 모드**다 (협업 §0).
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
 * 동화 · 일기와 다른 점은 둘뿐이다.
 *   1. 마스코트가 질문을 **소리 내어 읽지 않는다.** 질문은 부모 띠에 뜨고 어른이 자기 말로 묻는다 (§2-1)
 *   2. 띠 왼쪽에 마스코트가 서 있어, 아이에게는 "마스코트가 물어본다"로 보인다
 *
 * **받아주기 · 되돌려주기 · 낭독은 넘기지 않는다.** 발음이 어긋났을 때 고쳐 말해 주되
 * 지적하지 않는 것이 부모가 가장 못하는 일이다 (§2-2).
 */

/** 협업 모드에서만 붙는 첫 안내. 일기 모드는 이 함수를 부르지 않는다. */
suspend fun Director.coopIntro(childName: String) {
    say("오늘은 ${childName}랑 어른이 같이 만들 거야! 질문은 아래에 띄워 줄게.")
    log("부모 협업 모드 — AI가 질문 카드를 띄우면 **부모가 읽고 자기 말로 묻는다.** 받아주기 · 되돌려주기 · 낭독은 마스코트가 그대로 한다 (협업 §2-1 · §2-2)")
    log("⚠️ 되돌려주기를 넘기지 않는 이유: 발음이 어긋났을 때 고쳐 말해 주되 **지적하지 않는 것**이 부모가 가장 못하는 일이다 (협업 §2-2)")
    log("기다리는 시간(5초 · 8초)을 쓰지 않는다 — 부모가 옆에 있으므로 AI가 끼어들 이유가 없다. 60초 아무 입력이 없을 때만 한 번 말을 건다 (협업 §3-1)")
}

/**
 * 질문을 던지는 갈고리. 일기 모드는 이것만 부르고 협업인지 아닌지 모른다.
 *
 * 전에는 띠에 [다르게 물어볼래] · [내가 답할래] 두 버튼을 달아 부모가 사다리를 손으로 내리게 했다.
 * 그런데 버튼을 고르는 것이 일이 되어 아이와 이야기하는 흐름이 끊겼고, 무엇보다 그 버튼이 보내는
 * `coop:next` · `coop:adult` 값이 칸으로 새어 들어가 화면에 `coop:adult` 라는 글자가 그대로 나왔다.
 * 지금은 버튼이 없고, 사다리도 무응답도 [Director.ask] 가 동화 모드와 똑같이 처리한다.
 */
suspend fun Director.askOrCoopAsk(q: Question): Reply =
    if (s.isCoop) coopAsk(q) else ask(q)

/** ASK′ — 질문 카드를 **소리 없이** 부모 띠에 띄우고 기다린다. */
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

private suspend fun Director.coopReact(r: Reply.Spoke) {
    val t = r.text.trimEnd('!', '.', '?')
    say(listOf("$t! 그랬구나~", "우와, $t!", "$t 했구나! 더 들려줘.").random())
    pause(1200)
}

/** 끝났을 때 협업만 남기는 표시와 기록. 일기 모드에서는 아무 일도 하지 않는다. */
fun Director.coopFinishLog() {
    if (!s.isCoop) return
    mark("coop")
    // ⚠️ "어른이 지은 자리"를 세지 않는다 — [내가 답할래]를 뺀 뒤로 그 수는 **언제나 0**이라
    // "어른이 아무것도 안 했다"로 읽힌다. 실제로는 어른이 **모든 질문을 읽어 주었다** (9/21).
    val byChild = s.author.values.count { it == "child" }
    log("같이 짓기 — 어른이 읽어 준 질문 ${s.partnerTurns}번 · 그중 아이가 자기 말로 채운 자리 $byChild. 책 자막에 작은 표시로만 남는다 (채점처럼 보이면 안 된다 · 협업 §6)")
}

/** 15분이 지나 끝날 때의 한 줄 — 협업은 부모가 질문을 고르는 시간이 들어가 더 빨리 닿는다 (협업 §9-3). */
fun DemoState.coopTimeUpNote(): String =
    if (isCoop) "15분 경과 → 끝. ⚠️ 협업 모드는 부모가 질문을 고르는 시간이 들어가 15분에 더 빨리 닿는다 (협업 §9-3)"
    else "15분 경과 → 끝. ⚠️ 15분은 동화 모드 기준이라 취침 루틴에는 길 수 있다 (일기 §7-5)"

/** 책 이름 — 협업이면 "같이 지은"을 붙인다. */
fun coopBookName(s: DemoState): String =
    if (s.isCoop) "같이 지은 오늘 이야기" else "오늘 이야기"

/**
 * 이 쪽을 누가 지었나 — 협업 모드에서만 책에 **작은 표시 하나**로 남는다 (협업 §6 번갈아 짓기).
 * ⚠️ 채점처럼 보이면 안 된다. 누가 지었는지 알아볼 정도이고, 숫자도 순위도 없다.
 */
fun DemoState.pageAuthor(i: Int): String? {
    if (!isCoop) return null
    val slot = when (pageKind(i)) {
        PageKind.DEPART -> "place"
        PageKind.SHAKE -> "problem"
        PageKind.TALK -> "cause"
        PageKind.DRAG -> "solution"
        else -> return null
    }
    return author[slot]?.takeIf { it == "child" || it == "adult" }
}
