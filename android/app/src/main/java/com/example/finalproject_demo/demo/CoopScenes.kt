package com.example.finalproject_demo.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.ui.coopItem
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.ui.reasonOrNull
import com.example.finalproject_demo.ui.templateQuestions

/**
 * 부모 협업 모드 — **일반 모드처럼 오또가 묻되, 부모가 고른 이야기에 맞춰 묻는 모드**다 (09-30 사용자 결정).
 *
 * 왜 이 파일이 따로 있나 (09-22)
 *   협업은 일기 모드의 흐름을 그대로 쓴다. 그래서 처음에는 `DiaryScenes.kt` 안에
 *   `isCoop` 분기로 들어가 있었다. 그런데 **일기와 협업을 다른 사람이 맡기로 하면서**
 *   한 파일을 둘이 고치게 됐다 — `guidelines/9_역할과_작업.md` §9-4.
 *   그래서 협업 쪽만 뽑아냈다. 일기 쪽에는 **갈고리만 남는다.**
 *
 * 흐름 (09-30 · 전에는 템플릿이 부모 질문 네 줄을 채우고 오또가 그 줄을 읽었다)
 *   - **걸음은 일반 모드와 같다** — `COOP_STEPS` 를 차례로 묻고, 사다리 · 무응답도 [Director.ask] 가 똑같이 처리한다.
 *   - **템플릿(`coopPick`)은 맥락이다.** 기승전결 네 자리에서 오또가 고른 요소 · 이유에 맞춘 질문으로 묻는다
 *     ([templateQuestions] — LLM이 붙기 전의 대역. 실제로는 이 맥락과 찬 칸을 프롬프트에 넣어 만든다).
 *   - **부모가 적은 질문(`parentQuestions`)은 끼워 넣는 것이다.** 꼬리질문 자리에 적은 순서대로 들어간다.
 *     네 자리는 이야기 뼈대라 템플릿 · 앱 질문이 맡는다 — 부모 질문의 답이 엉뚱한 칸에 들어가지 않게.
 *   - 둘 다 없으면 9/21 옛 흐름(질문이 소리 없이 부모 띠에 뜨고 어른이 읽는다). 소파가 이 경우를 막아서
 *     아이 화면에서는 닿지 않고, `DiaryFlowTest` 의 협업 검사가 본다.
 *
 * **받아주기 · 되돌려주기 · 낭독은 넘기지 않는다** (§2-2).
 */

/** 기승전결 네 자리 — 템플릿 질문 0~3이 이 순서로 짝지어진다 */
private val COOP_PART_SLOTS = listOf("place", "problem", "cause", "solution")

/** 이 걸음의 자리(`diary_<bookKey>`). 기승전결 네 자리면 0~3, 꼬리질문이면 null */
private fun partIndexOf(q: Question): Int? =
    q.id.removePrefix("diary_").let { key -> COOP_PART_SLOTS.indexOf(key).takeIf { it >= 0 } }

/** 부모가 실제로 적은 질문이 하나라도 있나 — 빈 줄은 안 센다 (입력 화면이 빈 줄을 남겨 둔다) */
val DemoState.hasCoopQuestions: Boolean get() = parentQuestions.any { it.isNotBlank() }

/** 같이 만들기가 준비됐나 — 템플릿을 골랐거나 질문을 하나라도 적었으면. 소파의 🎁 · 새 흐름이 이것을 본다 */
val DemoState.coopReady: Boolean get() = coopPick != null || hasCoopQuestions

/**
 * 같이 만들기 화면 배경 (10-02 사용자 요청 — 고른 요소에 맞춰야 한다). 일기 모드는 이 길을 타지 않는다.
 *
 * 1. 목록에 있는 요소면 그 요소의 배경([COOP_ITEMS]) — 소방관이면 소방서, 축구면 축구장.
 *    아이 말보다 앞선다: 「불 난 집에 갔어」의 「집」이 일기 장소 낱말에 걸려 거실이 나오면 안 된다
 * 2. 직접 쓴 요소면 아이가 말한 곳, 그다음 요소 이름에서 일기 장소 낱말을 찾는다(「할머니 집」 → 할머니 집)
 * 3. 고른 이야기가 없으면(질문만 적었으면) 일기와 같이 아이가 말한 곳으로
 */
internal fun DemoState.coopBackdrop(): String {
    storyBackground?.let { return it }   // 책장에서 다시 연 책 — 저장해 둔 배경 그대로 (10-02)
    val pick = coopPick ?: return diaryPlaceBg(placeLabel)
    coopItem(pick.name)?.let { return it.bg }
    val spoken = diaryPlaceBg(placeLabel)
    return if (spoken != DIARY_BG_FALLBACK) spoken else diaryPlaceBg(pick.name)
}

/** 이 걸음에 누구의 질문을 쓰나 — 템플릿 맥락으로 만든 오또 질문, 부모가 적은 질문, 앱 질문(null) */
private sealed interface CoopLine {
    data class Template(val text: String) : CoopLine
    data class Parent(val text: String) : CoopLine
    /** 서버(`/turn`)의 LLM 이 앞 답을 보고 만든 다음 질문 (10-01 · #53 A) */
    data class Llm(val text: String) : CoopLine
}

/**
 * 이 걸음에 쓸 질문. 네 자리는 템플릿 질문, 꼬리질문 자리는 부모가 적은 질문을 **순서대로** 하나씩.
 *
 * **한 걸음에 한 번만 쓴다.** 아이가 "몰라"라고 하면 같은 걸음을 사다리 한 칸 아래 질문으로 다시 묻는데,
 * 그때는 고른 질문을 고집하지 않고 **앱의 쉬운 질문**이 나가야 한다 (구현설계 §1-②).
 */
private fun DemoState.takeCoopLine(q: Question): CoopLine? {
    val track = coopTrack
    if (q.id in track.askedSteps) return null          // 다시 묻는 자리 — 앱 질문으로
    track.askedSteps += q.id
    val idx = partIndexOf(q)
    // 앞에서 말한 곳을 「거기」 자리에 — 사다리(CoopTemplatePack)와 같은 말이 나가게 (10-01)
    if (idx != null) return coopPick?.templateQuestions()?.getOrNull(idx)?.let { t -> CoopLine.Template(heardPlace()?.let(t::here) ?: t) }
    val mine = parentQuestions.filter { it.isNotBlank() }.getOrNull(track.parentUsed) ?: return null
    track.parentUsed++
    parentQIndex++
    return CoopLine.Parent(mine)
}

/**
 * 고른 이야기의 질문 · 부모가 적은 질문 하나에 아이가 뭐라고 했나 — 부모 리포트의 재료.
 * `by` 는 출처 3종 그대로(`child` · `card` · `mascot`), 답이 없으면 null (구현설계 §2-3).
 */
data class CoopAsked(val question: String, val answer: String?, val by: String?)

/** 이 이야기에서 어느 걸음에 이미 물었고, 부모 질문을 몇 개 썼고, 질문마다 아이가 뭐라고 했나 */
private class CoopTrack {
    val askedSteps = mutableSetOf<String>()
    var parentUsed = 0
    val asked = mutableListOf<CoopAsked>()
    /** 방금 `/turn` 이 정한 다음 칸과 그 칸을 묻는 LLM 질문 — 바로 다음 걸음에서 한 번만 쓰고 버린다 */
    var llmNext: Pair<String, String>? = null
    /** 앞 답에서 뗀 이름 — place · who · thing (이어 받기 질문에 끼운다 · CoopHeard.kt) */
    val heard = mutableMapOf<String, String>()
    /** 이 이야기에서 나간 「왜」 질문 수 — 두 번까지 (CoopQuestions.kt) */
    var whyAsked = 0
    /** 뼈대 네 자리(0~3)에서 처음 물은 질문 — 이어 받기로 문장이 바뀌어도 네 자리를 다 물었는지 본다 */
    val partQuestions = sortedMapOf<Int, String>()
    /** 엉뚱한 답이 나온 자리 · 「진짜로는」으로 다시 물은 자리 — 자리마다 한 번만 되돌린다 */
    var wildFor: String? = null
    var wildAsked: String? = null
    /** 바로 앞 받아주기 — 같은 말이 이어 나오지 않게 */
    var lastAck: String? = null
    /** 받아주기 직후 한 번 부른 `/turn` — 수준 신호와 칸 값이 같이 쓴다(두 번 부르지 않는다). 아이 말이 키 */
    var liveTurn: LiveTurn? = null
    /** 지금 방식 · 바뀐 방식을 견주는 이야기 하나의 수치 (§11) — 끝날 때 `coop_session` 으로 남긴다 */
    val stats = CoopSessionStats()
}

/**
 * 이야기 하나의 수치 — 「몰라」 수 · 말로 한 답의 평균 길이(공백 뺀 글자) · 갈무리에 걸린 수(출처별) · 걸린 시간.
 * 점수가 아니다 — 부모 화면에 보이지 않고 로그 · 이벤트에만 남는다
 */
internal class CoopSessionStats(val startedAt: Long = System.currentTimeMillis()) {
    var dontKnows = 0
    val answerChars = mutableListOf<Int>()
    val guardHits = sortedMapOf<String, Int>()

    fun count(r: Reply) {
        if (r !is Reply.Spoke) return
        val t = r.text.trim()
        if (isNonAnswer(t) || dontKnow(t)) dontKnows++ else answerChars += t.count { !it.isWhitespace() }
    }

    val averageChars: Double get() = if (answerChars.isEmpty()) 0.0 else answerChars.average()
}

/** 이 이야기의 수치 — 검사가 읽는다 */
internal val DemoState.coopStats: CoopSessionStats? get() = trackByState[this]?.stats

/** 진짜 마이크 답 하나에 부른 `/turn` 결과 — 서버가 꺼졌거나 응답이 없으면 [result] 가 null */
private class LiveTurn(val utterance: String, val result: Server.TurnResult?)

/** 이 이야기에서 뼈대 네 자리마다 처음 물은 질문 (자리 순서대로). 검사 · 로그가 읽는다 */
internal val DemoState.coopPartQuestions: List<String> get() = trackByState[this]?.partQuestions?.values?.toList().orEmpty()

/**
 * 협업 질문 방식 — **바뀐 방식**(이어 받기 · 갈무리 · 짧은 받아주기)과 **지금 방식**(09-30 흐름 그대로)을 견줘 보는 스위치.
 * 시연 서랍에서 협업일 때만 보인다. 기본은 바뀐 방식 (10-02)
 */
object CoopLab {
    var followUps by mutableStateOf(true)
}

/** 걸음 자리 → 이름이 놓이는 모양 */
private fun roleOf(stepKey: String): Pair<String, CoopRole>? = when (stepKey) {
    "place" -> "place" to CoopRole.PLACE
    "companion" -> "who" to CoopRole.WHO
    "problem" -> "thing" to CoopRole.THING
    else -> null
}

/**
 * `Model.kt` 에 칸을 더하지 않고 상태마다 붙여 둔다(약한 참조). 이야기가 시작될 때([coopIntro]) 새로 만든다.
 * 홀더에 자리·답 칸이 들어오면(조장) 이 보조 기록은 홀더 쪽으로 옮긴다.
 */
private val trackByState = java.util.WeakHashMap<DemoState, CoopTrack>()
private val DemoState.coopTrack: CoopTrack
    get() = trackByState[this] ?: CoopTrack().also { trackByState[this] = it }

private fun DemoState.newCoopTrack() { trackByState[this] = CoopTrack() }

/** LLM 질문을 받을 수 있는 걸음 — 칸 이름이 걸음과 하나로 맞는 것만. 꼬리질문 `extra` 는 여러 걸음이 같은 칸이라 뺀다 */
private val LLM_QUESTION_STEPS = setOf("place", "problem", "cause", "solution", "companion", "reaction")

/**
 * 이 걸음에 서버 LLM 질문을 쓸까 (#53 A · 동화의 `Director.askSlot` 과 같은 방식).
 * **앱이 지금 물을 칸과 서버가 정한 다음 칸이 같을 때만** 쓴다 — 걸음 순서 · 뼈대 네 칸 · 부모 질문 자리는 앱 규칙 그대로다.
 * 서버가 꺼졌거나 질문이 없으면 null → 대본.
 */
private fun DemoState.llmQuestionFor(q: Question, next: Pair<String, String>?): String? {
    if (next == null || !Server.liveFor(mode) || !coopLlmQuestionsFit) return null
    val key = q.id.removePrefix("diary_")
    return next.second.takeIf { key in LLM_QUESTION_STEPS && next.first == key && it.isNotBlank() }
}

/**
 * 서버 질문의 시제가 이 이야기와 맞나. 서버 프롬프트(`eval/line_prompt.md`)는 협업을 「오늘 있었던 일 · 과거형」으로 묻는다.
 * 그래서 **지난 일(다녀왔어요)과 이야기를 안 고른 경우만** 켠다 — 곧 해요 · 좋아해요에 쓰면 「뭐 했어?」로 묻게 된다.
 * ⚠️ 서버가 고른 이유로 시제를 가르게 되면(#53 C) 이 조건을 `true` 로 바꾼다.
 */
private val DemoState.coopLlmQuestionsFit: Boolean
    get() = coopPick.let { it == null || it.reasonOrNull() == CoopReason.DONE }

/**
 * `/turn` 에 함께 보내는 고른 이야기 (#53 B) — 「같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)」.
 * 서버는 지금 이 칸을 동화 틀 이름으로만 읽는다. 시제를 가르는 일은 서버가 정하면(#53 C) 그쪽이 한다
 */
internal fun DemoState.coopTurnContext(): String? = coopPick?.let { p ->
    val k = coopKind(p.kind) ?: return@let null
    val r = p.reasonOrNull()
    val tense = when (r) { CoopReason.DONE -> "지난 일"; CoopReason.SOON -> "앞으로 할 일"; else -> "상상 이야기" }
    "같이 만들기 · ${k.title} · ${p.name} · ${r?.let { k.reasonLabels[it] } ?: "이유 없음"}($tense)"
}

/**
 * 묻지 않고 지나간 뼈대 걸음을 「물은 것」으로 친다 — 앞 답에서 이미 찬 칸이라 건너뛴 걸음 (10-01).
 * 안 치면 [coopQuestionsAllAsked] 가 네 자리를 다 물었다고 보지 않아 남은 꼬리질문을 끝까지 묻는다.
 */
internal fun DemoState.coopCoverPart(stepId: String) { coopTrack.askedSteps += stepId }

/** 이 이야기에서 템플릿 · 부모 질문에 아이가 한 답들 — 부모 리포트가 읽는다. 이야기가 끝나도 남는다(다음 이야기가 시작되면 새로) */
val DemoState.coopAsked: List<CoopAsked> get() = trackByState[this]?.asked.orEmpty()

/** 협업 모드에서만 붙는 첫 안내. 일기 모드는 이 함수를 부르지 않는다. */
suspend fun Director.coopIntro(childName: String) {
    s.newCoopTrack()
    if (s.coopReady) {
        // 템플릿으로 골랐으면 무슨 이야기인지 먼저 알려 준다 (09-29) — 호칭은 "부모님" (사용자 결정)
        val pick = s.coopPick
        if (pick != null) say("${childName}${ya(childName)}, 부모님이 고른 ‘${pick.name}’ 이야기를 같이 만들어 보자!")
        else say("${childName}${ya(childName)}, 부모님이 물어보고 싶은 게 있대! 내가 같이 물어볼게.")
        log("부모 협업 모드 — 오또가 일반 모드처럼 걸음마다 묻는다. " +
            (pick?.let { "기승전결 네 자리는 고른 ‘${it.name}’(${it.reason ?: "이유 없음 → 상상"})에 맞춘 질문 — LLM이 붙으면 이 맥락을 프롬프트에 넣는다. " } ?: "") +
            "부모가 적은 질문 ${s.parentQuestions.count { it.isNotBlank() }}개는 꼬리질문 자리에 끼워 묻는다 (09-30)")
        return
    }
    say("오늘은 ${childName}랑 어른이 같이 만들 거야! 질문은 아래에 띄워 줄게.")
    log("부모 협업 모드 — 고른 이야기도 적은 질문도 없어 옛 흐름: AI가 질문 카드를 띄우면 **부모가 읽고 자기 말로 묻는다.** (9/21 설계)")
    log("⚠️ 되돌려주기를 넘기지 않는 이유: 발음이 어긋났을 때 고쳐 말해 주되 **지적하지 않는 것**이 부모가 가장 못하는 일이다 (협업 §2-2)")
}

/**
 * 질문을 던지는 갈고리. 일기 모드는 이것만 부르고 협업인지 아닌지 모른다.
 * 사다리도 무응답도 [Director.ask] 가 동화 모드와 똑같이 처리한다.
 */
suspend fun Director.askOrCoopAsk(q: Question): Reply = when {
    !s.isCoop -> ask(q)
    s.coopReady -> coopAskInFlow(q)
    else -> coopAsk(q)
}

/**
 * 새 흐름 — 오또가 묻는다. 네 자리는 템플릿 맥락에 맞춘 질문, 꼬리질문 자리는 부모가 적은 질문이 있으면 그것.
 * 사다리(쉬운 질문)는 앱 것을 그대로 둔다: 아이가 답을 못 하면 앱이 더 쉬운 말로 바꿔 묻는다.
 */
private suspend fun Director.coopAskInFlow(q: Question): Reply {
    if (!CoopLab.followUps) return coopAskInFlowBefore(q).also { s.coopTrack.stats.count(it) }
    mark("coop")
    val track = s.coopTrack
    val llm = track.llmNext.also { track.llmNext = null }
    val firstAsk = q.id !in track.askedSteps
    val idx = partIndexOf(q)
    val key = q.id.removePrefix("diary_")
    val reason = s.coopPick?.reasonOrNull() ?: CoopReason.DREAM
    val scripted = s.takeCoopLine(q)

    /** 갈무리 — 통과하면 그 말, 못 고치면 null. 어디서 왔고 무엇에 걸렸는지 남긴다 */
    fun guarded(text: String, src: CoopSource): String? {
        val g = coopGuard(text, reason, src)
        if (g.issues.isNotEmpty()) log("[$key] 갈무리(${src.label}) ${if (g.ok) "고침" else "사다리로"} — ${g.issues.joinToString(" · ")} · 원문 \"$text\"" + (g.text?.let { " → \"$it\"" } ?: ""))
        if (g.issues.isNotEmpty()) event("coop_guard", "source" to src.name, "ok" to g.ok, "issues" to g.issues.joinToString("|"))
        if (g.issues.isNotEmpty()) track.stats.guardHits.merge(src.name, 1, Int::plus)
        return g.text
    }

    // 서버 LLM 질문은 처음 묻는 자리에서만 · 갈무리를 통과했을 때만 (부모 질문 자리면 묻지 않는다)
    val llmText = if (firstAsk && scripted !is CoopLine.Parent) s.llmQuestionFor(q, llm)?.let { guarded(it, CoopSource.LLM) } else null
    // 엉뚱한 답(다녀왔어요 · 곧 해요의 상상 낱말) 뒤 한 번 — 같은 자리를 「진짜로는」으로
    val redirect = !firstAsk && track.wildFor == key && track.wildAsked != key

    // 부모 질문이 먼저 — 몰래 바꾸지 않는다(질문 하나만 남긴다). 그다음 서버 LLM 질문, 그다음 이어 받기 · 템플릿, 그다음 사다리
    val (text, src) = when {
        scripted is CoopLine.Parent -> (guarded(scripted.text, CoopSource.PARENT) ?: scripted.text) to CoopSource.PARENT
        llmText != null -> llmText to CoopSource.LLM
        redirect -> {
            track.wildAsked = key
            coopRedirect(key, reason) to CoopSource.HEARD
        }
        scripted is CoopLine.Template && idx != null && idx >= 1 -> {
            // 2~4번째 자리 — 앞 답을 끼운 이어 받기. 못 만들면 템플릿 질문 그대로 (첫 자리는 미리 본 템플릿 그대로)
            val raw = s.coopPick?.templateQuestions()?.getOrNull(idx)
            coopFollowUp(key, s.level, reason, track.heard, s.coopPick, track.whyAsked, raw, listOf(q.text) + q.ladder)
                ?.let { it to CoopSource.HEARD }
                ?: ((guarded(scripted.text, CoopSource.TEMPLATE) ?: q.text) to CoopSource.TEMPLATE)
        }
        scripted is CoopLine.Template -> (guarded(scripted.text, CoopSource.TEMPLATE) ?: q.text) to CoopSource.TEMPLATE
        else -> (guarded(q.text, CoopSource.LADDER) ?: q.text) to CoopSource.LADDER
    }
    log("[$key] ${src.label} 질문 → \"$text\" · 수준 ${s.level.label}" + (if (src == CoopSource.HEARD) " · 들은 이름 ${track.heard}" else ""))
    if ("왜" in text) track.whyAsked++
    if (idx != null && firstAsk) track.partQuestions[idx] = text
    val r = ask(q.copy(text = text, silent = false))
    track.stats.count(r)
    // 부모 리포트 「고른 이야기 · 적은 질문에 한 답」 — 지금 방식과 같이 앱 기본 질문 · 사다리는 남기지 않는다
    if (src != CoopSource.LADDER) track.asked += when (r) {
        is Reply.Spoke -> CoopAsked(text, r.text, "child")
        is Reply.Tapped -> CoopAsked(text, r.label, if (r.byMascot) "mascot" else "card")
        else -> CoopAsked(text, null, null)
    }
    if (src == CoopSource.PARENT) {
        event("utterance", "speaker" to "adult", "mode" to "typed", "text" to text)
        s.partnerTurns++
        s.adultLine = text
    }
    if (r is Reply.Spoke) {
        // 다녀왔어요 · 곧 해요에 상상 낱말 — 한 번만 「진짜로는」으로 되돌린다. 두 번째면 그대로 받는다
        val wild = isWildForReality(r.text, reason) && track.wildFor != key
        if (wild) { track.wildFor = key; log("[$key] 실제 일 이야기에 상상 낱말 → 고치지 않고 받아 준 뒤 한 번만 「진짜로는」으로 묻는다") }
        // 앞 답에서 이름 하나 — 다음 자리 질문에 끼운다. 거친 말이 섞인 이름 · 되돌릴 상상 낱말은 끼우지 않는다
        if (!wild) roleOf(key)?.let { (slot, role) ->
            coopNameFrom(r.text, role)?.takeIf { !hasRoughWord(it) }?.let { track.heard[slot] = it; log("[$key] 들은 이름 $slot=「$it」 (다음 질문에 끼운다)") }
        }
        // 받아주기 한마디 — 아이 말에서 뗀 이름 하나. 「몰라」 · 「응」은 되비추지 않는다. 다음 질문과 합쳐 두 문장
        coopAck(r.text, roleOf(key)?.second, reason, wild, track.lastAck)?.let { track.lastAck = it; say(it); pause(700) }
        // 진짜 마이크 답에 수준 신호를 단다 — 공용 판정이 신호 없는 답을 늘 「내림」으로 세던 것 (CoopSignals.kt)
        if (r.isLiveSpeech()) return r.copy(answer = coopLiveSignals(q, text, r.text, wild))
    }
    return r
}

/**
 * 진짜 마이크 답의 수준 신호. 서버를 켰으면 `/turn` 을 **여기서 한 번** 부르고(받아주기 직후 — 기다림은 전과 같다)
 * 그 결과를 [coopLiveValue] 가 칸 값에 다시 쓴다. 「몰라」 · 되돌릴 엉뚱한 답이면 전처럼 부르지 않는다.
 */
private suspend fun Director.coopLiveSignals(q: Question, question: String, said: String, wild: Boolean): Answer {
    val text = said.trim()
    val step = COOP_STEPS.firstOrNull { "diary_${it.bookKey}" == q.id }
    val turn = if (step != null && !wild && !isNonAnswer(text) && Server.liveFor(s.mode)) {
        val asked = step.slot.takeIf { it in Server.SLOTS && it != "extra" }
        s.exchangeTurn("coop", asked, question, text, template = s.coopTurnContext())
    } else null
    s.coopTrack.liveTurn = LiveTurn(text, turn)
    val a = coopSignals(text, question, turn?.verdict)
    log("[${q.id.removePrefix("diary_")}] 수준 신호 — " + (if (turn?.verdict != null) "서버 판정" else "앱 규칙") +
        " · 까닭 ${if (a.reason) "○" else "×"} · 요소 ${a.el.ifEmpty { setOf("-") }.joinToString("·")} · 잇는 말 ${if (a.con) "○" else "×"}" +
        (if (isChoiceQuestion(question)) " (선택지 답이라 신호 없음)" else ""))
    return a
}

/** 지금 방식(09-30 흐름) — 비교용으로 그대로 둔다. 시연 서랍에서 고른다 */
private suspend fun Director.coopAskInFlowBefore(q: Question): Reply {
    mark("coop")
    val llm = s.coopTrack.llmNext.also { s.coopTrack.llmNext = null }
    val firstAsk = q.id !in s.coopTrack.askedSteps
    val line = s.takeCoopLine(q).let { scripted ->
        // 부모 질문이 먼저다. 그다음이 LLM 질문, 그다음이 대본 (#53 A)
        if (scripted is CoopLine.Parent || !firstAsk) scripted
        else s.llmQuestionFor(q, llm)?.let { CoopLine.Llm(it) } ?: scripted
    }
    val text = when (line) {
        null -> {
            log("[${q.id}] 앱 질문을 그대로 묻는다: \"${q.text}\"")
            return ask(q)
        }
        is CoopLine.Template -> line.text.also { log("[${q.id}] 고른 이야기에 맞춘 질문 → \"$it\" (LLM 대역 · 앱 질문 \"${q.text}\" 은 사다리 뒤에 남는다)") }
        is CoopLine.Llm -> line.text.also { log("[${q.id}] 서버 LLM 이 앞 답을 보고 만든 질문 → \"$it\" (대본 \"${q.text}\" 은 사다리 뒤에 남는다 · #53)") }
        is CoopLine.Parent -> line.text.also { log("[${q.id}] 부모가 적은 질문을 끼워 묻는다: \"$it\" (앱 질문 \"${q.text}\" 은 사다리 뒤에 남는다)") }
    }
    val r = ask(q.copy(text = text, silent = false))
    // 부모 리포트 「고른 이야기 · 적은 질문에 한 답」 — 템플릿 질문도 부모가 고른 이야기라 함께 남긴다
    s.coopTrack.asked += when (r) {
        is Reply.Spoke -> CoopAsked(text, r.text, "child")
        is Reply.Tapped -> CoopAsked(text, r.label, if (r.byMascot) "mascot" else "card")
        else -> CoopAsked(text, null, null)
    }
    if (line is CoopLine.Parent) {
        // 부모가 지은 질문이라는 것은 기록에 남는다 — payload.speaker: adult. `by` 3종은 늘리지 않는다 (협업 §4-1 · §8)
        event("utterance", "speaker" to "adult", "mode" to "typed", "text" to text)
        s.partnerTurns++
        s.adultLine = text          // 부모 리포트의 "어른이 한 말" — 마지막으로 쓴 부모 질문
    }
    if (r is Reply.Spoke) coopReact(r)
    return r
}

/** 옛 흐름 — 질문 카드를 **소리 없이** 부모 띠에 띄우고 기다린다. 고른 이야기도 적은 질문도 없을 때만. */
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
    if (s.coopReady) {
        val left = s.parentQuestions.count { it.isNotBlank() } - s.parentQIndex
        log("같이 짓기 — 부모가 적은 질문 ${s.parentQIndex}개를 꼬리질문 자리에 끼워 물었다" +
            (if (left > 0) " · ${left}개는 꼬리질문 자리가 모자라 못 물었다" else "") + ". 부모 리포트 「함께하기」 축의 재료다 (협업 §4-2)")
        coopSessionLog()
        // 이야기마다 비운다 — 오늘 고른 이야기 · 적은 질문이 내일 또 나오면 안 된다(조장). 비우는 자리는 **이야기가 끝난 여기**다.
        // 리포트에 남는 것은 `partnerTurns` · `adultLine` · `utterance speaker=adult` 이벤트라 홀더가 비어도 된다.
        s.clearParentQuestions()
        return
    }
    // ⚠️ "어른이 지은 자리"를 세지 않는다 — [내가 답할래]를 뺀 뒤로 그 수는 **언제나 0**이라
    // "어른이 아무것도 안 했다"로 읽힌다. 실제로는 어른이 **모든 질문을 읽어 주었다** (9/21).
    val byChild = s.slotBy.values.count { it == "child" }
    log("같이 짓기 — 어른이 읽어 준 질문 ${s.partnerTurns}번 · 그중 아이가 자기 말로 채운 자리 $byChild. 채점처럼 보이면 안 되므로 책에는 남기지 않는다 (협업 §6)")
}

/** 지금 방식 · 바뀐 방식을 견주는 수치 한 줄 (협업 질문 업그레이드 §11) */
private fun Director.coopSessionLog() {
    val st = s.coopTrack.stats
    val way = if (CoopLab.followUps) "바뀐 방식" else "지금 방식"
    val secs = (System.currentTimeMillis() - st.startedAt) / 1000
    val avg = "%.1f".format(st.averageChars)
    val end = s.endReason ?: "done"
    log("협업 수치($way) — 「몰라」 ${st.dontKnows}번 · 답 평균 ${avg}자(공백 뺌, ${st.answerChars.size}개) · 끝 $end · ${secs}초 · 갈무리 ${st.guardHits.ifEmpty { mapOf("없음" to 0) }}")
    event("coop_session", "way" to if (CoopLab.followUps) "new" else "before", "dont_know" to st.dontKnows,
        "avg_chars" to avg, "answers" to st.answerChars.size, "end" to end, "secs" to secs,
        "guard" to st.guardHits.entries.joinToString("|") { "${it.key}:${it.value}" })
}

/**
 * 부모 「그만하기」 (#36 · 09-30) — 어른이 옆에 있으니 끝낼 권한은 어른에게 준다. 아이 화면의 버튼이 아니라
 * 확인 창을 한 번 거친다(`OttoShell` · `KidTopBar`).
 *
 * 멈추면 **모인 답으로 책을 만든다** — 끝나는 이유만 `parent_stop` 이고, 뒤는 다 같은 마무리(`finishDiary`)다:
 * 빈 필수 칸은 마스코트가 이야기로 메우고(`by: mascot`), 아이 답이 하나도 없으면 끝낼지 묻는다.
 * `endReason` 은 앱 안에서만 쓰는 값이라(로그 · 마무리 문구) 서버 규격에는 영향이 없다.
 *
 * 묻고 있던 질문은 **취소한다.** 탭 · 무응답 신호로 깨우면 그 값이 칸에 들어가거나 쉬운 질문으로 다시 묻는다.
 */
fun Director.stopCoopByParent() {
    if (!s.isCoop || s.scene != Scene.DIARY || s.endReason != null) return
    s.endReason = "parent_stop"
    log("부모가 「그만하기」를 눌렀다 → 묻던 질문을 거두고, 지금까지 모인 답으로 책을 만든다 (#36)")
    replaceScene {
        say("부모님이 오늘은 여기까지래! 지금까지 한 이야기로 책을 만들어 줄게.")
        pause(1500)
        finishDiary()
    }
}

/**
 * 부모가 준비한 것을 **다 물었나** — 협업이 끝나는 조건 (09-30 확정 · guidelines/2 §1-1 · #36).
 *
 * 두 가지가 다 참이어야 한다 (09-30 흐름 개정):
 * - **뼈대 네 자리를 다 물었다** — 부모 질문은 꼬리질문 자리에 끼워지므로 앞쪽에서 먼저 떨어질 수 있다.
 *   그때 바로 끝내면 「무슨 일 · 왜 · 어떻게 됐나」를 묻지도 않고 마스코트가 지어 채운다.
 *   템플릿만 골랐으면 이 네 자리가 부모가 준비한 전부다
 * - **부모가 적은 질문을 다 물었다** — 빈 줄은 세지 않는다. 사다리로 다시 묻는 자리는 부모 질문을 한 번만 쓰므로
 *   `parentQIndex` 는 쓴 수 그대로다. 걸음이 건너뛰어져 못 물은 질문이 있으면 참이 되지 않고, 그때는 열한 걸음이 끝나며 마무리된다.
 *
 * 준비한 것이 없는 옛 흐름(띠)에서는 참이 되지 않는다.
 */
val DemoState.coopQuestionsAllAsked: Boolean
    get() = isCoop && coopReady &&
        COOP_PART_SLOTS.all { "diary_$it" in coopTrack.askedSteps } &&
        parentQIndex >= parentQuestions.count { it.isNotBlank() }

// ── 진짜 마이크 답 → 칸 값 (#47 · 10-01) ─────────────────────────────

/**
 * 진짜 마이크로 들은 답인가. 대본 답은 `value`(칸 값)와 [Answer] 꼬리표를 달고 오고,
 * 받아쓰기 답은 글자(`text`)만 있다 (`AGENTS.md` 「진짜 마이크 답에는 대본 꼬리표가 없다」).
 */
internal fun Reply.Spoke.isLiveSpeech(): Boolean = value.isEmpty() && (answer == null || answer.lv == COOP_LIVE_LV)

/** 칸에 넣지 않는 짧은 말 — 「몰라」 · 「글쎄」 · 「응」. 이때는 사다리를 한 칸 내려가 쉬운 말로 다시 묻는다 */
private val NON_ANSWER_STARTS = listOf("몰라", "모르겠", "모름", "글쎄", "기억 안", "기억이 안", "생각 안", "생각이 안", "잘 모르")
private val NON_ANSWER_WORDS = setOf("응", "어", "음", "아니", "네", "예", "없어", "몰라요", "싫어")

internal fun isNonAnswer(text: String): Boolean {
    val t = text.trim().trimEnd('.', '!', '?', '~', ' ')
    if (t.isEmpty() || t in NON_ANSWER_WORDS) return true
    // 짧은 말에서만 본다 — 「친구가 없어서 슬펐어」 같은 긴 답을 「몰라」로 버리면 안 된다
    return t.length <= 10 && NON_ANSWER_STARTS.any { t.startsWith(it) }
}

/**
 * 협업 질문에 아이가 **진짜 말로** 답했을 때 이 걸음의 칸 값 (#47 1번 · 2번).
 *
 * - 서버를 켰으면 `/turn`(`exchangeTurn("coop", …)`)이 **내용을 보고** 칸을 고른다. 이 걸음의 칸이 채워지면 그 값을 쓰고,
 *   다른 뼈대 칸이 함께 나왔으면(「놀이터 갔는데 친구가 밀었어」) 비어 있는 것만 같이 채운다.
 *   이 걸음 칸이 안 나오면 null — 질문에 맞는 답이 아니었으니 사다리로 다시 묻는다
 *   (부모가 바꾼 질문 「좋아하는 색은?」의 「빨강」이 `problem` 에 들어가지 않는다)
 * - 꼬리질문 자리(`extra`)는 아이 말 그대로 둔다 — 서버가 줄인 값보다 원문이 책 재료다
 * - 서버가 없거나 응답이 없으면 **아이 말 그대로** 쓴다. 대본 값을 읽던 때(`r.value`)는 진짜 답이 늘 비어
 *   마스코트가 칸을 지어냈다 — 그 버그가 1번이다
 * - 「몰라」류는 null
 */
internal suspend fun Director.coopLiveValue(step: DiaryStep, question: String, r: Reply.Spoke): String? {
    val text = r.text.trim()
    if (isNonAnswer(text)) return null
    // 다녀왔어요 · 곧 해요의 엉뚱한 답 — 처음 한 번은 칸에 넣지 않고 「진짜로는」으로 다시 묻는다 (바뀐 방식 · 10-02)
    if (CoopLab.followUps && s.coopTrack.wildFor == step.bookKey && s.coopTrack.wildAsked != step.bookKey) {
        log("[${step.bookKey}] 상상 낱말이라 이번엔 칸에 넣지 않는다 → 「진짜로는」으로 한 번 더")
        return null
    }
    if (!Server.liveFor(s.mode)) return text
    val asked = step.slot.takeIf { it in Server.SLOTS && it != "extra" }
    // 바뀐 방식은 받아주기 직후 이미 한 번 불렀다(coopLiveSignals) — 그 결과를 쓴다
    val cached = s.coopTrack.liveTurn?.takeIf { it.utterance == text }.also { s.coopTrack.liveTurn = null }
    val turn = if (cached != null) cached.result else s.exchangeTurn("coop", asked, question, text, template = s.coopTurnContext())
    // 서버가 정한 다음 칸 · 질문을 다음 걸음에 쓸 수 있게 둔다 (#53 A) — 맞지 않으면 다음 걸음이 버린다
    s.coopTrack.llmNext = turn?.verdict?.nextSlot?.let { slot -> turn.line?.question?.takeIf(String::isNotBlank)?.let { slot to it } }
    val verdict = turn?.verdict
    if (verdict == null) {
        log("[${step.bookKey}] /turn 응답 없음 → 아이 말 그대로 칸에 넣는다")
        return text
    }
    if (verdict.reason == "blocked_by_filter") {
        log("[${step.bookKey}] 서버 안전 판정으로 이 답은 책 재료에서 뺀다 → 다시 묻는다")
        return null
    }
    val fills = verdict.fills.filter { (slot, v) -> slot in Server.SLOTS && slot != "extra" && v.isNotBlank() }
    fills.filter { (slot, _) -> slot != step.slot && slot in COOP_SKELETON && !diaryFilled(slot) }.forEach { (slot, v) ->
        setDiarySlot(slot, slot, v.trim(), v.trim(), "child")
        log("아이 말에 [$slot] 도 들어 있었다 → 비어 있던 그 칸도 채운다 (/turn)")
    }
    if (asked == null) return text
    return fills.firstOrNull { it.first == step.slot }?.second?.trim().also {
        if (it == null) log("[${step.slot}] /turn 이 이 칸을 못 찾았다 — 질문에 맞는 답이 아니었다 → 사다리")
    }
}

private val COOP_SKELETON = setOf("place", "problem", "cause", "solution")

// ── 책 문장 — /story (#47 2번 · 10-01) ──────────────────────────────

/** 꼬리질문으로 모은 문장 — 서버에는 `extra` 한 칸으로 보낸다. 맺음(`keep`)은 따로 간다 */
private val COOP_TAIL_KEYS = listOf("detail", "said", "try", "after")

/**
 * 서버를 켰으면 협업 책 문장을 `/story`(`mode: "coop"`)로 받는다. 지금 책은 틀 문장(`diaryTemplate()`) 빈칸 채우기라 딱딱하다.
 *
 * 책의 모양(쪽 수 · 차례)은 앱이 정한다 — 협업 책 틀의 쪽 목록을 `pages` 로 보내 **정확히 그 수만큼** 받는다.
 * 받은 문장은 `storyCaptions` 에 담고 책은 그 문장으로 그린다(`StoryBank.kt` `bookCaption`).
 * 실패하거나 수가 안 맞으면 지금 틀 문장 그대로다. 이름은 보낼 때 가리고 받은 글에서 되돌린다(규칙 6).
 */
suspend fun Director.coopWriteBook() {
    if (!s.isCoop || !Server.liveFor(s.mode)) return
    val pages = s.template?.pages ?: return
    s.stage = Stage.Making("이야기 문장을 쓰는 중… (${pages.size}쪽)")
    val mask = s.nameMask()
    val tails = COOP_TAIL_KEYS.mapNotNull { s.slots[it]?.takeIf(String::isNotBlank) }
    val slots = mapOf(
        "place" to s.place, "problem" to s.problem, "cause" to s.cause, "solution" to s.solution,
        "reaction" to s.reaction, "companion" to s.friend,
        "extra" to tails.joinToString(" / ").ifBlank { null },
    )
    val captions = Server.story(
        mode = "coop",
        slots = mask.maskSlots(slots),
        slotBy = s.slotBy.filterKeys { it in Server.SLOTS },
        keep = s.slots["keep"]?.takeIf(String::isNotBlank)?.let(mask::mask),
        level = s.level.name.lowercase(),
        // 미션 쪽에 미션 ID 를 단다 — 서버가 그 쪽을 미션 직전 상황으로 끝맺는다 (#52 3번 · 동화 `storyPagePlan` 과 같은 표)
        pages = pages.map { Server.Page(it.kind.name, s.coopPageMission(it.kind)) },
        // 고른 이야기와 이유 — 이유에 따라 책 시제가 갈린다(곧 해요 = 앞으로 할 일 · 좋아해요 = 상상) (#52 1번 · 서버 `77a9d5c`)
        template = s.coopTurnContext()?.let(mask::mask),
        reason = s.coopStoryReason(),
    )?.map(mask::unmask)
    if (s.useCoopCaptions(captions)) log("서버가 쓴 협업 책 문장 ${pages.size}쪽을 받음 (/story)")
    else log("협업 책 문장 생성 실패 또는 쪽 수 불일치 → 틀 문장 그대로")
}

/**
 * 서버가 쓴 협업 책에서 **미션을 끝낸 뒤** 그 쪽에 붙는 결과 문장 (#52 2번). 서버 문장은 미션 직전에서 끝나고 결과는 앱이 쓴다.
 * 틀 문장 책에는 이 결과가 원래 들어 있었다(「먼지를 탈탈 털어 냈어」) — 서버 문장 책에서만 빠졌던 것을 채운다.
 * 아직 안 끝냈거나 미션 쪽이 아니면 null
 */
internal fun DemoState.coopMissionResult(kind: PageKind): String? = when (kind) {
    PageKind.RUB -> if (m1Result != null) mission1().blobName.let { "${it}${ga(it)} 사라졌어요." } else null
    PageKind.DRAG -> if (m2Result != null) {
        if (templateKey in setOf("A", "G")) "그림 조각을 모두 맞춰 한 장면을 완성했어요."
        else "$childName${eun(childName)} ${m2Clause()}"     // 같이 간 사람이 없으면 「오늘 이야기를 들어준 마스코트에게 …」
    } else null
    else -> null
}

/**
 * 협업 책 쪽의 미션 ID (`docs/미션_구상.md` §3). 미션 1 = 문지르기(A6), 미션 2 = 건네주기(E1) ·
 * 틀 A · G 면 그림 퍼즐(A3) — 책 화면(`Book.kt`)이 그리는 미션과 같아야 한다. 미션 쪽이 아니면 null
 */
internal fun DemoState.coopPageMission(kind: PageKind): String? = when (kind) {
    PageKind.RUB -> "A6"
    PageKind.DRAG -> if (templateKey in setOf("A", "G")) "A3" else "E1"
    else -> null
}

/**
 * `/story` 의 `reason` — 고른 이야기가 있으면 그 이유. **이유를 안 골랐으면 `dream`** — 앱이 질문을 상상 이야기로 했으니
 * 책도 상상으로 써야 한다(서버는 비면 「있었던 일」로 쓴다). 이야기를 안 고르고 질문만 적었으면 null(있었던 일 · 일기형).
 */
internal fun DemoState.coopStoryReason(): String? = coopPick?.let { (it.reasonOrNull() ?: CoopReason.DREAM).key }

/** 쪽 수가 맞고 빈 문장이 없을 때만 쓴다 — 하나라도 어긋나면 틀 문장 책 */
fun DemoState.useCoopCaptions(captions: List<String>?): Boolean {
    val expected = template?.pages?.size
    val valid = isCoop && expected != null && captions != null && captions.size == expected && captions.all { it.isNotBlank() }
    storyCaptions = if (valid) captions!!.toList() else null
    return valid
}

/** 책 이름 — 협업이면 "같이 지은"을 붙인다. */
fun coopBookName(s: DemoState): String =
    if (s.isCoop) "같이 지은 오늘 이야기" else "오늘 이야기"

