package com.example.finalproject_demo.demo

import com.example.finalproject_demo.demo.missions.soundProp
import com.example.finalproject_demo.demo.missions.slot1Prop
import com.example.finalproject_demo.demo.missions.slot2Prop
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.FixProp
import com.example.finalproject_demo.demo.missions.SoundProp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.ui.coopItem
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.missions
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
    coopReadingBackdrop?.let { return it }   // 책장에서 다시 연 책 — 만들 때의 배경 그대로 (#83 · 읽기 화면 상태에만 있다)
    coopTrack.generatedBg?.let { return it }  // 아이가 말한 곳으로 서버가 그린 배경 — 동화와 같게 (10-05 · CoopServerLine.kt)
    val pick = bookPick ?: return diaryPlaceBg(placeLabel)
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
    // 부모 질문은 자유로운 꼬리 자리에만 — 같이 간 사람 · 기분 · 내일 바람은 책에서 뜻이 있는 칸이다.
    // 「좋아하는 색은?」의 「빨강」이 같이 간 사람이 되어 책에 인물로 서던 것 (10-05)
    val key = q.id.removePrefix("diary_")
    if (key !in COOP_PARENT_STEPS && !key.startsWith(COOP_PARENT_KEY)) return null
    val mine = parentQuestions.filter { it.isNotBlank() }.getOrNull(track.parentUsed) ?: return null
    track.parentUsed++
    parentQIndex++
    track.parentSteps += q.id
    val book = "$COOP_PARENT_KEY${track.parentUsed}"
    track.parentKeyFor[q.id] = book
    track.parentQuestionOf[book] = mine
    return CoopLine.Parent(mine)
}

/** 이 곳의 배경을 아직 안 그렸으면 그린다고 적고 true — 한 이야기에서 같은 곳은 한 번만 (10-05) */
internal fun DemoState.coopClaimBackground(place: String): Boolean =
    (coopTrack.bgAskedFor != place).also { if (it) coopTrack.bgAskedFor = place }

/** 서버가 그린 배경 — 그사이 아이가 다른 곳을 말했으면 버린다 */
internal fun DemoState.coopUseGeneratedBackground(path: String, forPlace: String): Boolean =
    (coopTrack.bgAskedFor == forPlace).also { if (it) coopTrack.generatedBg = path }

/** 지금 같이 만드는 이야기에 서버가 그린 배경 — 그림 정리가 지우지 않게 (`Director.recoverStoryImages` · #80) */
internal val DemoState.coopGeneratedBackground: String? get() = coopTrack.generatedBg

/** 부모 질문을 끼우는 꼬리 자리 — 하던 일 · 한 말 · 해 본 것 · 집에 와서. 답의 뜻이 정해지지 않은 자리들이다 */
internal val COOP_PARENT_STEPS = setOf("detail", "said", "try", "after")

/** 부모 질문의 답을 담는 책 칸 — `parent1` … 적은 순서대로. 걸음의 칸(같이 간 사람 등)을 덮지 않는다 */
internal const val COOP_PARENT_KEY = "parent"

/**
 * 이 걸음의 첫 답이 부모 질문에 한 답이면 그 답을 담을 책 칸(`parent1` …). **한 번만 준다** —
 * 「몰라」 뒤에 사다리로 내려간 앱 질문의 답은 걸음 원래 칸으로 간다.
 */
internal fun DemoState.coopParentAnswerKey(stepId: String): String? = coopTrack.parentKeyFor.remove(stepId)

/** 이 이야기에서 아직 안 물은 다음 부모 질문의 차례(1부터) — 다 물었으면 null. [takeCoopLine] 과 같은 셈 */
internal val DemoState.coopNextParentTurn: Int?
    get() = (coopTrack.parentUsed + 1).takeIf { it <= parentQuestions.count(String::isNotBlank) }

/** 부모 질문과 아이 답 — 적은 순서대로 (질문, 답). `/story` 의 extra 가 읽는다 */
internal val DemoState.coopParentAnswers: List<Pair<String, String>>
    get() = coopTrack.parentQuestionOf.entries.sortedBy { it.key.removePrefix(COOP_PARENT_KEY).toIntOrNull() ?: 0 }
        .mapNotNull { (book, question) -> slots[book]?.takeIf(String::isNotBlank)?.let { question to it } }

/**
 * 고른 이야기의 질문 · 부모가 적은 질문 하나에 아이가 뭐라고 했나 — 부모 리포트의 재료.
 * `by` 는 출처 3종 그대로(`child` · `card` · `mascot`), 답이 없으면 null (구현설계 §2-3).
 */
data class CoopAsked(
    val question: String, val answer: String?, val by: String?,
    /** 부모가 적은 질문이었나 — 리포트 「다음에 넣어 볼 질문」이 이것만 후보로 본다 (CoopReport.kt) */
    val parent: Boolean = false,
)

/** 이 이야기에서 어느 걸음에 이미 물었고, 부모 질문을 몇 개 썼고, 질문마다 아이가 뭐라고 했나 */
private class CoopTrack {
    val askedSteps = mutableSetOf<String>()
    /** 꼬리 칸(detail · said · try · after)마다 마지막으로 물은 질문 — 책에 「질문」에 「답」으로 보낸다 */
    val tailQuestion = mutableMapOf<String, String>()
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
    /** 받아주기 전에 한 번 부른 `/turn` — 수준 신호와 칸 값이 같이 쓴다(두 번 부르지 않는다). 아이 말이 키 */
    var liveTurn: LiveTurn? = null
    /** 지금 방식 · 바뀐 방식을 견주는 이야기 하나의 수치 (§11) — 끝날 때 `coop_session` 으로 남긴다 */
    val stats = CoopSessionStats()
    /** What the child asked Otto — never into a slot or the rejected list (#327 §1, the two bugs) */
    val childAsked = mutableSetOf<String>()
    /** Ladder rungs already previewed for 「뭐를 넣어?」 — when really stepping down, start from the one after (#327 §4-2) */
    val previewedRungs = mutableSetOf<String>()
    /** The last 「궁금하다」 · 「떠올려 봐」 reply — so the same line does not repeat */
    var lastWorldReply: String? = null
    var lastRecallReply: String? = null
    /** Child questions per step — up to [CHILD_QUESTIONS_PER_STEP] per step even across rungs (#332 review P3) */
    val questionsAt = mutableMapOf<String, Int>()
    /** The step whose answer denied the premise of Otto's question (「아니, 안 갔어」 · #327 ② §5) — until the next ask */
    var deniedAt: String? = null
    /** Steps already asked a premise-free question — once per step (§5-1: denied again or still empty → the ladder as now) */
    val premiseFreeAsked = mutableSetOf<String>()
    /** The step whose answer is not kept as a rejected answer even if the judge rejects it — a premise denial · a correction of another slot (it must not become the slot value at the ladder's end) */
    var notRejectedAt: String? = null
    /** 부모가 적은 질문으로 물은 걸음 — 판정이 거절한 답을 그 칸에 넣지 않는다(부모 질문은 칸과 안 맞을 수 있다) */
    val parentSteps = mutableSetOf<String>()
    /** 부모 질문으로 물은 걸음 → 그 답을 담을 책 칸(`parent1` …). 첫 답에 한 번 쓰고 지운다 (10-05) */
    val parentKeyFor = mutableMapOf<String, String>()
    /** 책 칸(`parent1` …) → 그 칸에 물은 부모 질문 */
    val parentQuestionOf = mutableMapOf<String, String>()
    /** 걸음마다 아이가 진짜로 답했는데 `/turn` 판정이 이 칸 답이 아니라고 한 말 — 순서대로 (10-03 실기기) */
    val rejected = mutableMapOf<String, MutableList<String>>()
    /** 이 이야기를 시작할 때 고른 이야기 — 리포트를 열 때는 `coopPick` 이 이미 비어 있다(`clearParentQuestions`) */
    var pick: CoopPick? = null
    /** 「다녀온 뒤」 이야기면 짝이 될 「가기 전」 책 id — 시작할 때 붙잡는다(끝나면 계획과 같이 비므로) · 협업모드_확장_설계 §2 */
    var beforeBookId: String? = null
    /** 아이가 말한 곳으로 서버가 그린 배경(저장 경로)과, 그림을 요청한 곳 — 같은 곳을 두 번 그리지 않는다 (10-05) */
    var generatedBg by mutableStateOf<String?>(null)
    var bgAskedFor: String? = null
}

/**
 * 이야기 하나의 수치 — 「몰라」 수 · 말로 한 답의 평균 길이(공백 뺀 글자) · 갈무리에 걸린 수(출처별) · 걸린 시간.
 * 점수가 아니다 — 부모 화면에 보이지 않고 로그 · 이벤트에만 남는다
 */
internal class CoopSessionStats(val startedAt: Long = System.currentTimeMillis()) {
    var dontKnows = 0
    val answerChars = mutableListOf<Int>()
    val guardHits = sortedMapOf<String, Int>()
    /** Child questions by kind (about · recall · world). Not counted as 「몰라」 or in answer length (#327 §7) */
    val childQuestions = sortedMapOf<String, Int>()
    /** Child negations — premise denied · corrected (#327 ② §7) */
    val childNegations = sortedMapOf<String, Int>()

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
    if (next == null || !Server.liveFor(mode)) return null
    val key = q.id.removePrefix("diary_")
    return next.second.takeIf {
        key in LLM_QUESTION_STEPS && next.first == key && it.isNotBlank() && !(key == "cause" && causeAsksOtherEvent(it, problem, solution))
    }
}

/**
 * cause 자리 서버 질문이 문제 대신 다른 일(해결 · 같이 간 사람이 한 일)의 까닭을 묻나 (#304 1).
 * 꼬리 답 하나가 해결을 먼저 채우면 대사 모델이 「방금 일」의 까닭을 물었다 — 「아빠는 왜 풍선을 잡아줬을까?」.
 * **해결에만 있는 줄기**(낱말 앞 두 글자 · 해결 − 문제)가 질문에 하나라도 있으면 참. 해결이 비었으면 거르지 않는다
 */
internal fun causeAsksOtherEvent(question: String, problem: String?, solution: String?): Boolean {
    if (solution.isNullOrBlank()) return false
    fun stems(t: String) = Regex("[가-힣A-Za-z0-9]{2,}").findAll(t).map { it.value.take(2) }.toSet()
    val onlySolution = stems(solution) - stems(problem.orEmpty())
    return stems(question).any { it in onlySolution }
}

/**
 * The picked story sent with `/turn` and `/story` (#53 B · #52), e.g. "같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)".
 * The server splits the tense on the `reason` sent alongside ([coopStoryReason]), not on this text (#53 C 8da67b0 · #52 77a9d5c).
 */
internal fun DemoState.coopTurnContext(): String? = bookPick?.let { p ->
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

/** 이 이야기를 시작할 때 고른 이야기 · 부모 질문을 몇 개 썼나 — 부모 리포트의 말을 가른다 (CoopReport.kt) */
val DemoState.coopStoryPick: CoopPick? get() = trackByState[this]?.pick
val DemoState.coopParentUsed: Int get() = trackByState[this]?.parentUsed ?: 0
/** 이 이야기가 짝을 지을 「가기 전」 책 id — 「다녀온 뒤」 이야기가 아니면 null */
val DemoState.coopBeforeBookId: String? get() = trackByState[this]?.beforeBookId

/**
 * 지금 이야기의 고른 이야기 — 부모가 고른 것(`coopPick`), 비었으면 이 이야기를 시작할 때 남겨 둔 것.
 * 이야기가 끝나면 `coopFinishLog` 가 `coopPick` 을 비우고 **그다음에** 책을 만든다(배경 · `/story` · 책장 저장).
 * 그래서 책 쪽이 `coopPick` 만 보면 기본 배경(`bg_today`)이 깔리고 `/story` 에 이유 · 고른 이야기가 빠졌다 (10-03 실기기)
 */
internal val DemoState.bookPick: CoopPick? get() = coopPick ?: coopStoryPick?.takeIf { scene !in BEFORE_STORY }

/**
 * 새 이야기를 고르는 화면 — 여기서는 지난 이야기를 대신 읽지 않는다. 지난 이야기를 끝내고 다음엔 질문만 적어 시작하면
 * `coopIntro` 가 새 기록을 만들기 전까지 지난 이야기 배경이 잠깐 보였다 (#99 리뷰 3)
 */
private val BEFORE_STORY = setOf(Scene.ADULT, Scene.PARTNER, Scene.BESTIARY, Scene.MAKEHERO)

/** 협업 모드에서만 붙는 첫 안내. 일기 모드는 이 함수를 부르지 않는다. */
suspend fun Director.coopIntro(childName: String) {
    s.newCoopTrack()
    s.coopTrack.pick = s.coopPick
    // 「다녀온 뒤」 — 상자에서 꺼낸 계획이고, 가기 전 책이 아직 책장에 있을 때만 (협업모드_확장_설계 §2-2)
    s.coopTrack.beforeBookId = CoopPlan.beforeBookId(s)
        ?.takeIf { s.coopPick?.reasonOrNull() == CoopReason.DONE }
        ?.takeIf { id -> CoopShelf.books(s).any { it.id == id } }
    if (s.coopReady) {
        // 템플릿으로 골랐으면 무슨 이야기인지 먼저 알려 준다 (09-29) — 호칭은 "부모님" (사용자 결정)
        val pick = s.coopPick
        if (pick != null) say("${childName}${ya(childName)}, 부모님이 고른 ‘${pick.name}’ 이야기를 같이 만들어 보자!")
        else say("${childName}${ya(childName)}, 부모님이 물어보고 싶은 게 있대! 내가 같이 물어볼게.")
        // 가기 전 책은 이 한 줄에서만 말한다 — 질문 중에 「지난번엔 사자 본댔잖아」로 아이 답을 끌고 가지 않는다(지어내지 않기)
        if (pick != null && s.coopTrack.beforeBookId != null) {
            say(coopAfterIntroLine(pick))
            log("「다녀온 뒤」 이야기 — 가기 전 책(${s.coopTrack.beforeBookId})과 짝이 된다. 가기 전 책은 서버에 보내지 않는다")
        }
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
        // server lines are checked in the tense the server was asked for; our own lines in the flow's reason
        val g = coopGuard(text, if (src == CoopSource.LLM) s.coopServerTense() else reason, src)
        if (g.issues.isNotEmpty()) log("[$key] 갈무리(${src.label}) ${if (g.ok) "고침" else "사다리로"} — ${g.issues.joinToString(" · ")} · 원문 \"$text\"" + (g.text?.let { " → \"$it\"" } ?: ""))
        if (g.issues.isNotEmpty()) event("coop_guard", "source" to src.name, "ok" to g.ok, "issues" to g.issues.joinToString("|"))
        if (g.issues.isNotEmpty()) track.stats.guardHits.merge(src.name, 1, Int::plus)
        return g.text
    }

    // 서버 LLM 질문은 처음 묻는 자리에서만 · 갈무리를 통과했을 때만 (부모 질문 자리면 묻지 않는다)
    val llmText = if (firstAsk && scripted !is CoopLine.Parent) s.llmQuestionFor(q, llm)?.let { guarded(it, CoopSource.LLM) } else null
    if (firstAsk && key == "cause" && llm?.first == "cause" && causeAsksOtherEvent(llm.second, s.problem, s.solution))
        log("[cause] 서버 질문이 해결의 까닭을 물어 버림 — \"${llm.second}\"")
    // 엉뚱한 답(다녀왔어요 · 곧 해요의 상상 낱말) 뒤 한 번 — 같은 자리를 「진짜로는」으로
    val redirect = !firstAsk && track.wildFor == key && track.wildAsked != key
    // The last answer denied this step's premise (「아니, 안 갔어」) — once, an open question with no name and no choices (#327 ② §5-2)
    val premiseFree = if (scripted !is CoopLine.Parent && track.deniedAt == key && key !in track.premiseFreeAsked)
        coopPremiseFree(key, reason)?.let { guarded(it, CoopSource.HEARD) } else null
    track.deniedAt = null
    track.notRejectedAt = null

    // 부모 질문이 먼저 — 몰래 바꾸지 않는다(질문 하나만 남긴다). 그다음 서버 LLM 질문, 그다음 이어 받기 · 템플릿, 그다음 사다리
    val (picked, src) = when {
        scripted is CoopLine.Parent -> (guarded(scripted.text, CoopSource.PARENT) ?: scripted.text) to CoopSource.PARENT
        premiseFree != null -> {
            track.premiseFreeAsked += key
            premiseFree to CoopSource.HEARD
        }
        llmText != null -> llmText to CoopSource.LLM
        redirect -> {
            track.wildAsked = key
            coopRedirect(key, reason) to CoopSource.HEARD
        }
        scripted is CoopLine.Template && idx != null && idx >= 1 -> {
            // 2~4번째 자리 — 앞 답을 끼운 이어 받기. 못 만들면 템플릿 질문 그대로 (첫 자리는 미리 본 템플릿 그대로)
            val raw = s.coopPick?.templateQuestions()?.getOrNull(idx)
            coopFollowUp(key, s.level, reason, track.heard, s.coopPick, track.whyAsked, raw, listOf(q.text) + q.ladder, s.coopHadTrouble())
                ?.let { it to CoopSource.HEARD }
                ?: ((guarded(scripted.text, CoopSource.TEMPLATE) ?: q.text) to CoopSource.TEMPLATE)
        }
        scripted is CoopLine.Template -> (guarded(scripted.text, CoopSource.TEMPLATE) ?: q.text) to CoopSource.TEMPLATE
        else -> (guarded(q.text, CoopSource.LADDER) ?: q.text) to CoopSource.LADDER
    }
    // If the child's 「뭐를 넣어?」 already previewed this rung, start from the one after (#327 §4-2)
    val shown = if (src == CoopSource.LADDER && picked in track.previewedRungs && q.ladder.isNotEmpty()) q.ladder.first() else picked
    if (shown != picked) log("[$key] rung already previewed, the next one → \"$shown\"")
    log("[$key] ${src.label} 질문 → \"$shown\" · 수준 ${s.level.label}" + (if (src == CoopSource.HEARD) " · 들은 이름 ${track.heard}" else ""))
    if ("왜" in shown) track.whyAsked++
    if (idx != null && firstAsk) track.partQuestions[idx] = shown
    val (r, askedLast, stillAsking) = askAnsweringChildQuestions(q, key, shown)
    if (stillAsking) {
        // A third question in one step — the current flow (an easier question). Not into a slot or the rejected list (coopLiveValueAsSaid) · no ack
        log("[$key] third child question → no answer, the easier question as now (#327 ⚖️5)")
        // Returned as is, with no signals or quote — with an answer attached, judge() kept it as a report quote (#332 review P2)
        return r
    }
    track.stats.count(r)
    val text = askedLast
    // 꼬리 질문은 무엇을 물었는지 같이 책에 보낸다 — 「엄마가 뭐라고 할까?」의 답인지 몰라 서버가 「엄마에게 재밌냐고 물어볼 것 같아요」로
    // 말한 사람을 바꿨고, 「제일 먼저 뭐 할 거야?」의 답을 「“물로 끌 거야.”라고 말할 거예요」로 썼다(10-06 실기기 · 촬영 세션)
    if (key in COOP_TAIL_KEYS) track.tailQuestion[key] = text
    // 부모 리포트 「고른 이야기 · 적은 질문에 한 답」 — 지금 방식과 같이 앱 기본 질문 · 사다리는 남기지 않는다
    if (src != CoopSource.LADDER) track.asked += when (r) {
        is Reply.Spoke -> CoopAsked(text, r.text, "child", parent = src == CoopSource.PARENT)
        is Reply.Tapped -> CoopAsked(text, r.label, if (r.byMascot) "mascot" else "card", parent = src == CoopSource.PARENT)
        else -> CoopAsked(text, null, null, parent = src == CoopSource.PARENT)
    }
    if (src == CoopSource.PARENT) {
        event("utterance", "speaker" to "adult", "mode" to "typed", "text" to text)
        s.partnerTurns++
        s.adultLine = text
    }
    if (r is Reply.Spoke) {
        // Negation — did the child deny the premise of Otto's question (「아니, 안 줬어」) or correct an answer (「놀이터 말고 수영장」) (#327 ② §5). Live speech only
        val negation = if (r.isLiveSpeech()) classifyCoopReply(r.text, text).takeIf { it is CoopReply.PremiseDenied || it is CoopReply.Corrected } else null
        val step = COOP_STEPS.firstOrNull { "diary_${it.bookKey}" == q.id }
        val parentStep = src == CoopSource.PARENT || q.id in track.parentSteps
        if (negation != null) {
            val label = if (negation is CoopReply.PremiseDenied) "premise_denied" else "corrected"
            log("[$key] reply kind — $label · 「${r.text.trim()}」 ← 「$text」")
            event("coop_reply_kind", "kind" to label, "text" to r.text.trim())
            track.stats.childNegations.merge(label, 1, Int::plus)
            // A denied name is not put into the next question (§5-2)
            val gone = (negation as? CoopReply.PremiseDenied)?.noun ?: (negation as? CoopReply.Corrected)?.denied
            gone?.let { g -> track.heard.entries.filter { sameSyllables(it.value, g) }.forEach { (slot, n) -> track.heard.remove(slot); log("[$key] 들은 이름 $slot=「$n」 dropped — the child denied it") } }
        }
        if (negation is CoopReply.PremiseDenied) {
            val ack = negationAck(r.text) ?: "그랬구나!"
            // A parent question keeps the answer as said in its parent slot — 「안 줬어」 is an answer the parent wants too. Not asked again
            if (!parentStep) { track.deniedAt = key; track.notRejectedAt = key }
            if (step != null && !step.required && !parentStep) {
                // A tail step — the slot stays empty (⚖️3 · only 「안 했어」 in the book makes an empty page sentence). No judge call; on to the next step
                log("[$key] tail step premise denied → slot left empty, next step (#327 ⚖️3)")
                log("[$key] ack — negation 「$ack」")
                track.lastAck = ack; say(ack); pause(700)
                return r.copy(answer = coopSignals(r.text.trim(), text, null))
            }
        }
        // A correction sends only the corrected words to the judge — without 「아니,」 · 「○○ 말고」. The report record (track.asked) kept the original
        val said = if (negation is CoopReply.Corrected) r.copy(text = negation.instead).also { log("[$key] corrected → only 「${negation.instead}」 goes to the judge") } else r
        // 다녀왔어요 · 곧 해요에 상상 낱말 — 한 번만 「진짜로는」으로 되돌린다. 두 번째면 그대로 받는다
        val wild = negation == null && isWildForReality(r.text, reason) && track.wildFor != key
        if (wild) { track.wildFor = key; log("[$key] 실제 일 이야기에 상상 낱말 → 고치지 않고 받아 준 뒤 한 번만 「진짜로는」으로 묻는다") }
        // One name from the answer for the next question — not a name with a rough word, a wild word to redirect, or a denial
        if (!wild && negation !is CoopReply.PremiseDenied) roleOf(key)?.let { (slot, role) ->
            coopNameFrom(said.text, role)?.takeIf { !hasRoughWord(it) }?.let { track.heard[slot] = it; log("[$key] 들은 이름 $slot=「$it」 (다음 질문에 끼운다)") }
        }
        // 진짜 마이크 답이면 /turn 을 받아주기보다 먼저 부른다 — 받아주기에 서버 대사를 쓰려고 (10-05).
        // 수준 신호도 여기서 단다 — 공용 판정이 신호 없는 답을 늘 「내림」으로 세던 것 (CoopSignals.kt)
        val live = if (r.isLiveSpeech()) coopLiveSignals(q, text, said.text, wild) else null
        // A correction of a filled slot overwrites it — only when the denied name is that slot's value and the judge filled the same slot (⚖️4)
        val fixed = (negation as? CoopReply.Corrected)?.denied?.let { coopOverwriteCorrected(key, it) }
        if (fixed != null) track.notRejectedAt = key
        // The ack for a negation is the app's — no server line · 「우와!」 · 「응응!」 (§5-1)
        val negAck = fixed?.let { "아, $it${if (ga(it) == "이") "이" else ""}구나!" }
            ?: if (negation is CoopReply.PremiseDenied) negationAck(r.text) ?: "그랬구나!" else null
        if (negAck != null) {
            log("[$key] ack — negation 「$negAck」")
            track.lastAck = negAck; say(negAck); pause(700)
            return if (live != null) said.copy(answer = live) else said
        }
        // 받아주기 — 서버의 받아주기 + 되돌려주기(동화와 같게 · CoopServerLine.kt). 없거나 못 쓰면 앱의 한마디:
        // 아이 말에서 뗀 이름 하나. 「몰라」 · 「응」은 되비추지 않는다. 다음 질문과 합쳐 두 문장
        val server = if (live == null || wild) null else coopServerReaction(track.liveTurn?.result?.line)
        if (server != null) log("[$key] 받아주기 — 서버 대사 「$server」")
        // 못 썼으면 까닭을 남긴다 — 서버가 대사를 안 줬나(거절 · 실패), 앱이 버렸나를 다음 실기기에서 가른다 (#304 2)
        else if (live != null && !wild) log("[$key] 받아주기 — 서버 대사 못 씀: ${coopServerDropped(track.liveTurn?.result?.line).joinToString(" · ")}")
        (server ?: coopAck(said.text, roleOf(key)?.second, reason, wild, track.lastAck))?.let { track.lastAck = it; say(it); pause(700) }
        if (live != null) return said.copy(answer = live)
        return said
    }
    return r
}

/**
 * A correction (「놀이터 말고 수영장」) overwrites an **already filled skeleton slot** (#327 ② ⚖️4) — only when [denied] is that slot's value and
 * the last `/turn` filled the same slot. Returns the new value, or null. By child · the change is logged
 */
private fun Director.coopOverwriteCorrected(key: String, denied: String): String? {
    val fills = s.coopTrack.liveTurn?.result?.verdict?.fills ?: return null
    for ((slot, v) in fills) {
        if (slot !in COOP_SKELETON || v.isBlank()) continue
        val now = skeletonValue(slot)?.takeIf(String::isNotBlank) ?: continue
        if (!sameSyllables(now, denied)) continue
        val value = v.trim()
        setDiarySlot(slot, slot, value, value, "child")
        log("[$key] corrected — filled slot [$slot] 「$now」 → 「$value」 (the judge filled the same slot · #327 ⚖️4)")
        event("coop_corrected", "slot" to slot, "from" to now, "to" to value)
        return value
    }
    return null
}

private fun Director.skeletonValue(slot: String): String? = when (slot) {
    "place" -> s.place
    "problem" -> s.problem
    "cause" -> s.cause
    "solution" -> s.solution
    else -> null
}

/** A tail step's answer denied Otto's premise — the slot stays empty and the flow moves on (#327 ② ⚖️3 · DiaryScenes) */
internal fun DemoState.coopTailDenied(step: DiaryStep): Boolean =
    isCoop && !step.required && trackByState[this]?.deniedAt == step.bookKey

/** A required step's premise was denied and the slot is empty; the next ask is the premise-free question — the ladder does not step down (#327 ② §5-1) */
internal fun DemoState.coopPremiseFreeNext(step: DiaryStep): Boolean {
    if (!isCoop || !step.required) return false
    val t = trackByState[this] ?: return false
    return t.deniedAt == step.bookKey && step.bookKey !in t.premiseFreeAsked &&
        coopPremiseFree(step.bookKey, coopPick?.reasonOrNull() ?: CoopReason.DREAM) != null
}

/** Did the child ask Otto this — not into a slot, a parent question slot, or a quote (#332) */
internal fun DemoState.coopChildAsked(text: String): Boolean = trackByState[this]?.childAsked?.contains(text.trim()) == true

/** 「Nobody was there」 for 「누구랑 갔어?」 */
private val NOBODY = setOf("없어", "없었어", "없어요", "아무도", "아무도 없어", "아무도 없었어")

/** Child questions taken per step — from the third on, the current flow (an easier question) (#327 ⚖️5 · user decision 10-08) */
internal const val CHILD_QUESTIONS_PER_STEP = 2

/**
 * Asks, and when the child **asks Otto back**, answers briefly and asks the same question again (#327 §4). No ladder step down and no
 * `/turn` — it is not a slot answer. Live speech only (card and scripted answers pass through).
 * Returns: the last reply · the last thing asked · whether it is a third question passed on without an answer
 */
private suspend fun Director.askAnsweringChildQuestions(q: Question, key: String, first: String): Triple<Reply, String, Boolean> {
    val track = s.coopTrack
    var asked = first
    var r = ask(q.copy(text = asked, silent = false))
    while (r is Reply.Spoke && r.isLiveSpeech()) {
        val kind = classifyCoopReply(r.text, asked)
        // Found the answer while recalling (「엄마 우리 누구랑 갔지? 아 할머니!」) — only the latter part is the answer, also for the judge (#341)
        if (kind is CoopReply.AnswerAfterRecall) {
            log("[$key] answer after recalling: only 「${kind.answer}」 ← 「${r.text.trim()}」")
            r = r.copy(text = kind.answer)
            break
        }
        if (!kind.isQuestion) break
        val said = r.text.trim()
        track.childAsked += said
        val label = when (kind) { is CoopReply.AboutQuestion -> "about"; is CoopReply.Recall -> "recall"; else -> "world" }
        track.stats.childQuestions.merge(label, 1, Int::plus)
        log("[$key] reply kind — $label · 「$said」 ← 「$asked」" + ((kind as? CoopReply.Recall)?.who?.let { " · 부른 사람 $it" } ?: ""))
        event("coop_reply_kind", "kind" to label, "text" to said)
        val n = track.questionsAt[key] ?: 0
        if (n >= CHILD_QUESTIONS_PER_STEP) return Triple(r, asked, true)
        track.questionsAt[key] = n + 1
        val (reply, again) = coopAnswerChildQuestion(kind, asked, q.ladder, track)
        say(reply)
        talkOtto(reply)                                   // keep Otto's answer in the transcript — without 「?」 it is not kept by itself
        pause(700)                                        // no waiting for the adult — ask the child again right away (#341)
        log("[$key] answered the child's question → asking again \"$again\" (${n + 1}/$CHILD_QUESTIONS_PER_STEP)")
        asked = again
        r = ask(q.copy(text = asked, silent = false))
    }
    return Triple(r, asked, false)
}

/**
 * What Otto says to a child's question and what it asks again (#327 §4-2 · stage 1 · app sentences). No 「?」 in Otto's line —
 * with one it would show as an Otto question in the report transcript. Answering world questions (a server line) is stage 2 · the lead's (⚖️2)
 */
private fun coopAnswerChildQuestion(kind: CoopReply, asked: String, ladder: List<String>, track: CoopTrack): Pair<String, String> = when (kind) {
    // Asked what Otto's question means — preview the next rung of the step's ladder (easier words). If none, the same question again
    is CoopReply.AboutQuestion -> {
        val easier = ladder.firstOrNull { it != asked && it !in track.previewedRungs }
        if (easier != null) track.previewedRungs += easier
        "쉽게 다시 물어볼게!" to (easier ?: asked)
    }
    // Recalling something shared (「엄마, 우리 뭐 먹었지?」) — give it back to the child without calling the adult. An adult filling in
    // the answer means more parent involvement (user decision 10-08 · #341). No 「몰라도 괜찮아」 · no adult names. Ask again with the easier rung
    is CoopReply.Recall -> {
        val reply = listOf("생각나는 만큼만 말해 줘!", "천천히 떠올려 봐도 돼!").first { it != track.lastRecallReply }
        track.lastRecallReply = reply
        val easier = ladder.firstOrNull { it != asked && it !in track.previewedRungs }
        if (easier != null) track.previewedRungs += easier
        reply to (easier ?: asked)
    }
    else -> {
        val reply = listOf("오또도 궁금하다! 이따 같이 알아보자.", "좋은 질문이야!").first { it != track.lastWorldReply }
        track.lastWorldReply = reply
        "$reply 다시 물어볼게." to asked
    }
}

/**
 * 진짜 마이크 답의 수준 신호. 서버를 켰으면 `/turn` 을 **여기서 한 번** 부르고(받아주기 전 — 서버의 받아주기 · 되돌려주기를 말하려고, 10-05)
 * 그 결과를 [coopLiveValue] 가 칸 값에 다시 쓴다. 「몰라」 · 되돌릴 엉뚱한 답이면 전처럼 부르지 않는다.
 */
private suspend fun Director.coopLiveSignals(q: Question, question: String, said: String, wild: Boolean): Answer {
    val text = said.trim()
    val step = COOP_STEPS.firstOrNull { "diary_${it.bookKey}" == q.id }
    // 한 번 거절된 말을 똑같이 다시 했으면 판정을 다시 부르지 않는다 — coopLiveValueAsSaid 가 아이 말로 받는다 (10-06 조장)
    val repeated = step != null && s.coopRejectedAnswer(step)?.let { sameSyllables(it, text) } == true
    val turn = if (step != null && !wild && !repeated && !isNonAnswer(text) && Server.liveFor(s.mode)) {
        val asked = step.slot.takeIf { it in Server.SLOTS && it != "extra" }
        s.exchangeTurn("coop", asked, question, text)
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
        // the guard runs here too: with the past-only gate gone, this path would otherwise speak a wrong-tense line
        else s.llmQuestionFor(q, llm)?.let { coopGuard(it, s.coopServerTense(), CoopSource.LLM).text }?.let { CoopLine.Llm(it) } ?: scripted
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
        is Reply.Spoke -> CoopAsked(text, r.text, "child", parent = line is CoopLine.Parent)
        is Reply.Tapped -> CoopAsked(text, r.label, if (r.byMascot) "mascot" else "card", parent = line is CoopLine.Parent)
        else -> CoopAsked(text, null, null, parent = line is CoopLine.Parent)
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
    log("협업 수치($way) — 「몰라」 ${st.dontKnows}번 · 답 평균 ${avg}자(공백 뺌, ${st.answerChars.size}개) · 끝 $end · ${secs}초 · 갈무리 ${st.guardHits.ifEmpty { mapOf("없음" to 0) }}" +
        " · 아이 질문 ${st.childQuestions.ifEmpty { mapOf("없음" to 0) }} · 부정 ${st.childNegations.ifEmpty { mapOf("없음" to 0) }}")
    event("coop_session", "way" to if (CoopLab.followUps) "new" else "before", "dont_know" to st.dontKnows,
        "avg_chars" to avg, "answers" to st.answerChars.size, "end" to end, "secs" to secs,
        "guard" to st.guardHits.entries.joinToString("|") { "${it.key}:${it.value}" },
        "questions" to st.childQuestions.values.sum(), "about_question" to (st.childQuestions["about"] ?: 0),
        "recall" to (st.childQuestions["recall"] ?: 0), "world" to (st.childQuestions["world"] ?: 0),
        "premise_denied" to (st.childNegations["premise_denied"] ?: 0), "corrected" to (st.childNegations["corrected"] ?: 0))
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
    // 그리기 단계(이야기 칸이 다 찬 뒤 · 끝난 이유가 이미 있다) — 그리기를 마치고 바로 책으로. 그린 게 있으면 그 그림으로 (#98)
    if (s.isCoop && s.scene == Scene.DIARY && s.stage is Stage.DrawPad) {
        log("부모가 그리기 단계에서 「그만하기」를 눌렀다 → ${if (s.drawing.isEmpty()) "그리지 않고" else "그린 그림으로"} 책을 만든다 (#98)")
        send(if (s.drawing.isEmpty()) Reply.Tapped("skip", "안 그림") else Reply.Tapped("done", "완료"))
        return
    }
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

/** 말 앞에 붙는 망설임 — 「음… 몰라」 · 「어, 모르겠어」. 뒤에 말이 더 올 때만 뗀다(「아빠」의 「아」는 안 뗀다) */
private val HESITATION = Regex("^(?:(?:으*음+|어+|흠+|아+|그+)[.…,~!\\s]+)+")

internal fun isNonAnswer(text: String): Boolean {
    // 만 3~7세는 「음… 몰라」처럼 망설인 뒤 말한다 — 실기기(10-03)에서 이 말이 책 재료 칸에 들어갔다
    val t = text.trim().trimEnd('.', '!', '?', '~', ' ').replace(HESITATION, "").trim()
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
internal suspend fun Director.coopLiveValue(step: DiaryStep, question: String, r: Reply.Spoke): String? =
    coopLiveValueAsSaid(step, question, r)?.let { if (step.slot == "companion") companionName(it) else it }

private suspend fun Director.coopLiveValueAsSaid(step: DiaryStep, question: String, r: Reply.Spoke): String? {
    val text = r.text.trim()
    // 「없어」 · 「아무도」 to 「누구랑 갔어?」 is an answer — the child went alone (#327 §3-4). Co-op ignores the judge's no_longer_needed,
    // so sending it to the judge never closed the slot (device 10-08) — the companion slot gets 「혼자」 directly
    if (step.slot == "companion" && text.trimEnd('.', '!', '~', ' ') in NOBODY && classifyCoopReply(text, question) == CoopReply.Answer) {
        log("[${step.bookKey}] 「$text」 — nobody was there → companion 「혼자」")
        return "혼자"
    }
    if (isNonAnswer(text)) return null
    // What the child asked Otto — not into a slot, nor the rejected list (which becomes the slot value at the ladder's end). Asked twice,
    // it is still not taken as 「same syllables = what the child wants to say」 (#327 §1, the two bugs)
    if (s.coopChildAsked(text) || classifyCoopReply(text, question).isQuestion) {
        log("[${step.bookKey}] child question 「$text」 — not into the slot or the rejected list → the easier question as now")
        return null
    }
    // 다녀왔어요 · 곧 해요의 엉뚱한 답 — 처음 한 번은 칸에 넣지 않고 「진짜로는」으로 다시 묻는다 (바뀐 방식 · 10-02)
    if (CoopLab.followUps && s.coopTrack.wildFor == step.bookKey && s.coopTrack.wildAsked != step.bookKey) {
        log("[${step.bookKey}] 상상 낱말이라 이번엔 칸에 넣지 않는다 → 「진짜로는」으로 한 번 더")
        return null
    }
    if (!Server.liveFor(s.mode)) {
        // A premise denial (「안 갔어」) is not this slot's value — without the judge it is not kept; the premise-free question follows (#327 ② §5-1)
        if (step.required && s.coopTrack.deniedAt == step.bookKey) return null
        return text
    }
    // 10-06 조장: 한 번 거절된 말을 아이가 🎤 를 다시 눌러 **같은 음절로** 또 말했으면, 그게 아이가 이야기에 넣고 싶은 말이다.
    // 판정을 다시 부르지 않고 바로 아이 말로 받는다(두 번째 거절을 기다리지 않는다 · 서버 한 번 덜) — #100 「딴 얘기」 필드 대신
    s.coopRejectedAnswer(step)?.takeIf { sameSyllables(it, text) }?.let {
        log("[${step.bookKey}] 한 번 거절된 말 「$text」을 아이가 다시 똑같이 했다 → 하고 싶은 말로 받는다 (판정 다시 안 부름)")
        return text
    }
    val asked = step.slot.takeIf { it in Server.SLOTS && it != "extra" }
    // 바뀐 방식은 받아주기 전에 이미 한 번 불렀다(coopLiveSignals) — 그 결과를 쓴다
    val cached = s.coopTrack.liveTurn?.takeIf { it.utterance == text }.also { s.coopTrack.liveTurn = null }
    val turn = if (cached != null) cached.result else s.exchangeTurn("coop", asked, question, text)
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
        .let { f -> if (step.slot == "problem" && s.coopJobTaskIsProblem() && f.none { it.first == "problem" } && !diaryFilled("problem"))
            f.map { if (it.first == "solution") "problem" to it.second else it }.also { m ->
                if (m != f) log("[problem] 곧 해요 · 직업 — 판정이 해결 칸에 넣은 「할 일」을 사건 칸으로 옮긴다(이 틀의 사건 = 그 직업이 할 일)")
            } else f }
    fills.filter { (slot, _) -> slot != step.slot && slot in COOP_SKELETON && !diaryFilled(slot) }.forEach { (slot, v) ->
        val said = if (slot == "companion") companionName(v.trim()) else v.trim()
        setDiarySlot(slot, slot, said, said, "child")
        log("아이 말에 [$slot] 도 들어 있었다 → 비어 있던 그 칸도 채운다 (/turn)")
    }
    if (asked == null) return text
    return fills.firstOrNull { it.first == step.slot }?.second?.trim().also {
        if (it != null) return@also
        log("[${step.slot}] /turn 이 이 칸을 못 찾았다 — 질문에 맞는 답이 아니었다 → 사다리")
        // 우리가 물은 걸음에 아이가 진짜로 한 답 — 사다리가 끝나면 마스코트가 짓는 대신 이 말을 넣는다.
        // 빼는 것은 상상 낱말이 든 그 말 하나뿐 — 「진짜로는」 뒤의 진짜 답은 받는다 (#99 리뷰 2)
        // 「딴 얘기」(「쉬 마려」)도 아이 말로 지킨다 — 다시 물었는데 같은 말을 하면 하고 싶은 말이다(10-06 조장 · #100 · 딴 얘기 필드는 만들지 않음)
        val id = step.variant.id
        val reason = s.bookPick?.reasonOrNull() ?: CoopReason.DREAM
        val denied = s.coopTrack.notRejectedAt == step.bookKey   // premise denied · another slot corrected (#327 ②)
        if (denied) log("[${step.bookKey}] a negation is not kept as a rejected answer — so it never becomes the slot value at the ladder's end")
        if (id !in s.coopTrack.parentSteps && !denied && !isWildForReality(text, reason)) s.coopTrack.rejected.getOrPut(id) { mutableListOf() } += text
        // 판정이 이 칸 답이 아니라고 한 말에서 뗀 이름은 다음 질문에 끼우지 않는다 — 「친구들」이 곳 이름으로 끼어
        // 「친구들에 누구랑 같이 갔어?」 · 「친구들에서 뭐 봤어?」가 세 번 나왔다(10-06 실기기 · 학교 다녀왔어요)
        roleOf(step.bookKey)?.let { (slot, _) -> s.coopTrack.heard.remove(slot)?.let { n -> log("[${step.bookKey}] 들은 이름 $slot=「$n」 뺌 — 판정이 이 칸 답이 아니라고 했다") } }
    }
}

/**
 * 이 칸 값이 판정을 못 받고 들어간 아이 말인가 — 사다리 끝에서 [coopRejectedAnswer] 로 넣은 값.
 * 아이 말이라 책 재료로는 지키지만, 곳 · 사람 **이름**으로 질문에 끼우지는 않는다(10-06 실기기 「친구들에 …」)
 */
internal fun DemoState.coopUnconfirmed(value: String?): Boolean =
    isCoop && value != null && trackByState[this]?.rejected?.values?.any { value.trim() in it.map(String::trim) } == true

/**
 * 판정이 거절했지만 아이가 이 걸음에 진짜로 한 말 — 마지막 것. 없으면 null.
 * 실기기(10-03): 「불 끄기」(곧 해요의 문제) · 「사람 구하기」(해결)를 판정이 사건이 아니라며 받지 않아
 * 사다리 끝에서 마스코트가 「아직 못 들은 ○○」로 지어 채웠다 — 아이 말을 버리고 지어낸 말이 책에 들어갔다.
 * 그래서 쉬운 질문으로 **한 번** 더 묻고, 그래도 거절되면 아이 말을 그 칸에 넣는다(by child).
 * 부모 질문 걸음 · 「몰라」 · 상상 낱말 · 안전 판정은 여기 오지 않는다
 */
internal fun DemoState.coopRejectedAnswer(step: DiaryStep): String? =
    if (!isCoop) null else trackByState[this]?.rejected?.get(step.variant.id)?.lastOrNull()

/** 같은 음절인가 — 띄어쓰기 · 문장부호 · 「~」 같은 꾸밈은 빼고 글자만 본다(받아쓰기가 띄어쓰기를 매번 다르게 한다) */
internal fun sameSyllables(a: String, b: String): Boolean {
    fun core(t: String) = t.filter { it.isLetterOrDigit() }
    return core(a).isNotEmpty() && core(a) == core(b)
}

/** 이 걸음에서 판정이 거절한 아이 답이 몇 번이었나 — 두 번이면 사다리를 더 내려가지 않는다 */
internal fun DemoState.coopRejectedCount(step: DiaryStep): Int =
    if (!isCoop) 0 else trackByState[this]?.rejected?.get(step.variant.id)?.size ?: 0

private val COOP_SKELETON = setOf("place", "problem", "cause", "solution")

/**
 * 「곧 체험해요 · 직업」은 사건 칸이 **그 직업이 할 일**이다 — 「불이 나면 소방관은 무슨 일을 할까?」 · 「왜 그 일이 필요할까?」.
 * 판정은 「물로 불을 꺼」 같은 할 일을 해결 칸에 넣어서, 사건 칸이 비어 「하루 종일 뭐 할까?」로 같은 것을 다시 묻고(「불 꺼」)
 * 결말 질문은 이미 찼다며 건너뛰었다 — 책에 불 끄기가 네 쪽 되풀이됐다(10-06 실기기 · 촬영 세션)
 */
internal fun DemoState.coopJobTaskIsProblem(): Boolean =
    isCoop && coopPick?.kind == "job" && coopPick?.reasonOrNull() == CoopReason.SOON

// ── 책 문장 — /story (#47 2번 · 10-01) ──────────────────────────────

/** 꼬리질문으로 모은 문장 — 서버에는 `extra` 한 칸으로 보낸다. 맺음(`keep`)은 따로 간다 */
private val COOP_TAIL_KEYS = listOf("detail", "said", "try", "after")

/** 이 꼬리 칸에 마지막으로 물은 질문 — 없으면 null(답만 보낸다) */
internal fun DemoState.coopTailQuestion(key: String): String? = if (!isCoop) null else trackByState[this]?.tailQuestion?.get(key)

/**
 * 서버를 켰으면 협업 책 문장을 `/story`(`mode: "coop"`)로 받는다. 지금 책은 틀 문장(`diaryTemplate()`) 빈칸 채우기라 딱딱하다.
 *
 * 책의 모양(쪽 수 · 차례)은 앱이 정한다 — 협업 책 틀의 쪽 목록을 `pages` 로 보내 **정확히 그 수만큼** 받는다.
 * 받은 문장은 `storyCaptions` 에 담고 책은 그 문장으로 그린다(`StoryBank.kt` `bookCaption`).
 * 실패하거나 수가 안 맞으면 지금 틀 문장 그대로다. 이름은 보낼 때 가리고 받은 글에서 되돌린다(규칙 6).
 */
/**
 * 책을 쓰라고 보낼 곳 — 아이 문장에서 **곳 이름**을 뗄 수 있으면 이름만(「소방서에서 일할 것 같았어.」 → 소방서).
 * 문장째 보내면 서버가 받아 적어 책 1쪽이 「소방서에서 일할 것 같다고 생각할 거예요」가 됐다(10-06 실기기).
 * 칸 값은 그대로 둔다 — 부모 리포트는 아이가 한 말 원문을 보여 준다. 이름을 못 떼면(「기린 마당이 보였어」) 원문 그대로
 */
internal fun DemoState.coopBookPlace(): String? = place?.let { p -> bookPlaceName(p) ?: p }

/**
 * 「(곳)에서/에 (서술어)」 꼴에서 곳 이름. 공용 이름 떼기(`coopNameFrom`)는 「소방서」의 「서」를 이음 끝(「가서」)으로 봐서 못 뗀다.
 * 이름답지 않으면 null — 조사가 붙은 어절(「엄마랑 집」) · 「거기」 · ㅆ받침(서술어) · 너무 긴 말
 */
internal fun bookPlaceName(said: String): String? {
    val t = said.trim().trimEnd('.', '!', '?', '~', '…').trim()
    val name = Regex("^(.+?)(?:에서|에)\\s+\\S").find(t)?.groupValues?.get(1)?.trim() ?: return null
    val words = name.split(" ")
    if (name.length > 12 || words.size > 3 || name in setOf("거기", "여기", "저기")) return null
    // 앞 어절에 조사가 붙었으면 곳 이름이 아니라 말 토막(「엄마랑 집」). 마지막 어절은 보지 않는다 — 「제주도」 · 「독도」
    if (words.dropLast(1).any { w -> w.length > 1 && Regex("(랑|하고|와|과|가|이|은|는|을|를|도)$").containsMatchIn(w) }) return null
    if (name.any { c -> c in '가'..'힣' && (c - '가') % 28 == 20 }) return null
    return name
}

suspend fun Director.coopWriteBook() {
    if (!s.isCoop || !Server.liveFor(s.mode)) return
    val pages = s.template?.pages ?: return
    s.stage = Stage.Making("이야기 문장을 쓰는 중… (${pages.size}쪽)")
    val mask = s.nameMask()
    // 부모 질문의 답은 질문과 함께 — 「빨강」만 가면 무엇에 한 답인지 모른다 (10-05)
    val tails = COOP_TAIL_KEYS.mapNotNull { k -> s.slots[k]?.takeIf(String::isNotBlank)?.let { v -> s.coopTailQuestion(k)?.let { "「$it」에 「$v」" } ?: v } } +
        s.coopParentAnswers.map { (question, answer) -> "「$question」에 「$answer」" }
    val slots = mapOf(
        "place" to s.coopBookPlace(), "problem" to s.problem, "cause" to s.cause, "solution" to s.solution,
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
        // 물건은 아이 말에서 나온 것만 — 없으면 서버가 미션 상황 없이 쓴다(실제 하루에 없던 먼지 · 별 · 10-05)
        pages = pages.map { Server.Page(it.kind.name, s.coopPageMission(it.kind), s.coopMissionProp(it.kind), s.missionSource(it.kind)) },
        // 고른 이야기와 이유 — 이유에 따라 책 시제가 갈린다(곧 해요 = 앞으로 할 일 · 좋아해요 = 상상) (#52 1번 · 서버 `77a9d5c`)
        template = s.coopTurnContext()?.let(mask::mask),
        reason = s.coopStoryReason(),
        // 고른 요소 안의 자리 — 쪽 꾸밈과 keywords 에만 쓴다. 선택지 후보(무슨 일 · 까닭 · 해결)는 아이가 말한 게 아니라 안 보낸다 (#113)
        stage = s.coopStage(),
    )?.map(mask::unmask)
    if (s.useCoopCaptions(captions)) log("서버가 쓴 협업 책 문장 ${pages.size}쪽을 받음 (/story)")
    else log("협업 책 문장 생성 실패 또는 쪽 수 불일치 → 틀 문장 그대로")
}

/**
 * 서버가 쓴 협업 책에서 **미션을 끝낸 뒤** 그 쪽에 붙는 결과 문장 (#52 2번). 서버 문장은 미션 직전에서 끝나고 결과는 앱이 쓴다.
 * 틀 문장 책에는 이 결과가 원래 들어 있었다(「먼지를 탈탈 털어 냈어」) — 서버 문장 책에서만 빠졌던 것을 채운다.
 * 아직 안 끝냈거나 미션 쪽이 아니면 null
 */
internal fun DemoState.coopMissionResult(kind: PageKind): String? =
    coopImaginedResult(kind)
        // A 곧 해요 book is in the future tense — 「불이 다 꺼졌어요」 followed 「물을 뿌릴 거예요」 (device 10-06 · CoopTense.kt)
        ?: coopMissionResultAsDone(kind)?.let { if (coopServerTense() == CoopReason.SOON) soonTense(it) else it }

/** Missions played on the board — nothing to imagine, so the server does not write them as imagined either (#340) */
private val ON_BOARD = setOf(MissionId.A3)

/**
 * On a real day (다녀왔어요 · 곧 해요) a mission page the child did not talk about is written by the server as imagined
 * (「오또가 상상해 봤어! … 네가 …줄래?」 · #340 · lead 10-08). Its result closes as imagined too — closing it as a fact
 * (「불이 다 꺼졌어요」) would make the imagined thing part of that day. Null for 좋아해요 (the whole book is imagined),
 * for a page whose prop came from the child's words, and for board missions (the current sentence stays)
 */
internal fun DemoState.coopImaginedResult(kind: PageKind): String? {
    if (coopServerTense() == CoopReason.DREAM || coopMissionInBook(kind)) return null
    val m = missionFor(kind)?.takeIf { it !in ON_BOARD } ?: return null
    val done = when (kind) { PageKind.RUB -> m1Result != null; PageKind.DRAG -> m2Result != null; else -> false }
    if (!done) return null
    return "상상 속에서 " + when (m) {
        MissionId.A6 -> "반짝반짝 깨끗해졌어!"
        MissionId.C1 -> "후~ 다 날아갔어!"
        MissionId.C3 -> soundProp()?.let { "「${it.sound}!」 소리가 울렸어!" } ?: "큰 소리가 울렸어!"
        MissionId.A1 -> "불이 꺼졌어!"
        MissionId.A4 -> "물이 딱 멈췄어!"
        MissionId.D4 -> "공이 골대에 쏙 들어갔어!"
        MissionId.E2 -> "부서진 곳이 고쳐졌어!"
        MissionId.A5 -> "블록 탑이 높이 섰어!"
        MissionId.E1 -> "선물을 건넸어!"
        else -> "해냈어!"
    }
}

private fun DemoState.coopMissionResultAsDone(kind: PageKind): String? = if (!coopMissionInBook(kind)) null else when (kind) {
    PageKind.RUB -> if (m1Result != null) slot1Prop()?.result ?: mission1().let { m -> if (m.named) "${m.blobName}${ga(m.blobName)} 사라졌어요." else "반짝반짝 깨끗해졌어요." } else null
    PageKind.DRAG -> if (m2Result != null) {
        slot2Prop()?.result ?: if (missions().slot2 == MissionId.A3) "그림 조각을 모두 맞춰 한 장면을 완성했어요."
        else "$childName${eun(childName)} ${m2Clause()}"     // 같이 간 사람이 없으면 「오늘 이야기를 들어준 마스코트에게 …」
    } else null
    else -> null
}

/**
 * 협업 책 쪽의 미션 ID (`docs/미션_구상.md` §3). 미션 1 = 문지르기(A6), 미션 2 = 건네주기(E1) ·
 * 틀 A · G 면 그림 퍼즐(A3) — 책 화면(`Book.kt`)이 그리는 미션과 같아야 한다. 미션 쪽이 아니면 null
 */
internal fun DemoState.coopPageMission(kind: PageKind): String? = missionFor(kind)?.name

/**
 * 미션 쪽의 물건 — 서버가 그 쪽을 이 물건으로 세운다(`/story` `pages[].prop`). **아이가 한 말에서 나온 것만** 보낸다.
 *
 * 10-05 실기기: 협업 「동물원 다녀왔어요」 책에 아이가 말한 적 없는 먼지(미션 1)와 별(미션 2)이 들어갔다 —
 * 「무언가 묻거나 가려진 일은 아직 듣지 못했어요. 먼지가 사라졌어요.」 · 「…아빠와 동생에게 반짝이는 별을 건네주었어요.」
 * 아이 말에서 못 찾은 물건이면 null — 서버는 그 쪽을 미션 상황 없이 쓰고, 앱은 결과 문장을 붙이지 않는다([coopMissionInBook]).
 * 미션은 화면 위 놀이로 그대로 한다
 */
internal fun DemoState.coopMissionProp(kind: PageKind): String? = when (kind) {
    PageKind.RUB -> when (val p = slot1Prop()) {
        is BlowProp -> p.word
        is SoundProp -> SOUND_THING[p]
        else -> if (missionFor(kind) == MissionId.A6 && mission1FromChildWords()) mission1().blobName else null
    }
    PageKind.DRAG -> slot2Prop()?.let { FIX_THING[it] }
        ?: if (missionFor(kind) == MissionId.E1 && solutionItem != "star") mission2().itemName else null
    else -> null
}?.takeIf { coopServerTense() == CoopReason.DREAM || it.substringAfterLast(' ') in coopChildSaid() }
// 실제 하루는 그 물건 **이름**을 아이가 말했을 때만 — 말에서 짐작한 물건(「먹었어」 → 딸기 · 「미끄럼틀」 → 모래)은
// 아이가 말하지 않은 물건이라 책에 세우지 않는다(10-05 조장 리뷰 #134). 상상 이야기는 빌려 와도 된다

/** 아이(또는 아이가 고른 카드)가 채운 칸의 말을 한데 — 마스코트가 채운 칸은 빼고 */
private fun DemoState.coopChildSaid(): String {
    val mine = setOf("child", "card")
    val fields = mapOf("place" to place, "problem" to problem, "cause" to cause, "solution" to solution, "reaction" to reaction, "companion" to friend)
    return (slots.filterKeys { slotBy[it] in mine }.values + fields.filterKeys { slotBy[it] in mine }.values.filterNotNull()).joinToString(" ")
}

internal val SOUND_THING = mapOf(SoundProp.SIREN to "소방차", SoundProp.CAR to "자동차", SoundProp.TRAIN to "기차",
    SoundProp.LION to "사자", SoundProp.DOG to "강아지", SoundProp.CHEER to "공")
internal val FIX_THING = mapOf(FixProp.FIRE to "불", FixProp.FAUCET to "수도꼭지", FixProp.BALL to "공",
    FixProp.PIECES to "떨어진 조각", FixProp.BLOCKS to "블록")

/**
 * 이 미션 쪽이 **책 문장에** 들어가나. 상상 이야기(좋아해요)는 늘 들어간다 — 동화처럼 꾸며 써도 된다.
 * 실제 하루(다녀왔어요 · 곧 해요)는 아이 말에서 나온 물건이 있을 때만([coopMissionProp]). 퍼즐(A3)은 장면 그 자체라 늘 들어간다
 */
internal fun DemoState.coopMissionInBook(kind: PageKind): Boolean =
    coopServerTense() == CoopReason.DREAM || missionFor(kind) == MissionId.A3 || coopMissionProp(kind) != null

/**
 * `/story` 의 `reason` — 고른 이야기가 있으면 그 이유. **이유를 안 골랐으면 `dream`** — 앱이 질문을 상상 이야기로 했으니
 * 책도 상상으로 써야 한다(서버는 비면 「있었던 일」로 쓴다). 이야기를 안 고르고 질문만 적었으면 null(있었던 일 · 일기형).
 */
internal fun DemoState.coopStoryReason(): String? = bookPick?.let { (it.reasonOrNull() ?: CoopReason.DREAM).key }

/**
 * The tense the server writes in for this story: the pick's reason, DREAM for a pick with no reason, and DONE
 * when nothing was picked (the server reads a missing reason as a day that happened). Server lines are checked
 * against this, so the guard and the server agree (#53 review).
 */
internal fun DemoState.coopServerTense(): CoopReason =
    bookPick?.let { it.reasonOrNull() ?: CoopReason.DREAM } ?: CoopReason.DONE

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

