package com.example.finalproject_demo

import com.example.finalproject_demo.demo.completedStoryBook
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopAsked
import com.example.finalproject_demo.demo.heardPlace
import com.example.finalproject_demo.demo.here
import com.example.finalproject_demo.demo.stopCoopByParent
import com.example.finalproject_demo.ui.templateQuestions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 부모 협업 모드 — **일반 모드처럼 오또가 묻되, 고른 이야기에 맞추고 부모 질문을 끼워 넣는** 흐름 (09-30).
 *
 * - 기승전결 네 자리: 템플릿(`coopPick`)이 있으면 그 맥락의 질문, 없으면 앱 질문
 * - 꼬리질문 자리: 부모가 적은 질문을 적은 순서대로, 다 쓰면 앱 질문
 *
 * 넣어 둔 질문이 하나도 없을 때의 옛 흐름(띠에 소리 없이)은 `DiaryFlowTest`(치영)가 본다. 여기서는 새 흐름만.
 */
class CoopFlowTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) {
            while (!cond()) delay(3)
            true
        }

    private suspend fun Director.tap(part: String): Boolean {
        repeat(14) {
            if (await(2_500) { s.buttons.any { b -> part in b.label } } == null) return false
            s.buttons.first { part in it.label }.onClick()
            if (await(500) { s.buttons.none { b -> part in b.label } } != null) return true
        }
        return false
    }

    private suspend fun Director.push(part: String): Boolean {
        repeat(12) {
            val b = s.buttons.firstOrNull { part in it.label } ?: return false
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || s.buttons.none { x -> part in x.label } } != null) return true
        }
        return false
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + sup)
        val d = Director(scope)
        d.s.speed = 0.01
        try {
            block(d)
        } finally {
            sup.cancel()
        }
    }

    /** 첫 화면에서 [같이 만들기] → 질문을 넣고 → 주인공 → S3′ (부모 모드를 거치지 않는 지름길 — 그 경로는 아래 별도 검사) */
    private suspend fun Director.startCoopWith(vararg questions: String, pick: CoopPick? = null) {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertEquals(StoryMode.COOP, s.mode)
        s.parentQuestions += questions
        s.coopPick = pick
        assertTrue(tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)
    }

    /** 마스코트가 지금 묻고 있는 말 */
    private fun Director.asked(): String = s.line

    /** 책까지 🎲(시연 답)로 밀며 오또가 물은 말을 모은다 */
    private suspend fun Director.walkToBook(): List<String> {
        val askedTexts = mutableListOf<String>()
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 40) {
            if (await(2_000) { s.buttons.any { "🎲" in it.label } } == null) break
            askedTexts += asked()
            if (!push("🎲")) break
            delay(40)
        }
        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) tap("안 그릴래")
        return askedTexts
    }

    private val firefighter = CoopPick("job", "소방관", "soon")

    /** 오또가 실제로 물을 뼈대 네 질문 — 앞에서 말한 곳이 「거기」 자리에 들어간다 (10-01). 이야기를 다 돈 뒤에 부른다 */
    private fun Director.partsAsked(pick: CoopPick = firefighter): List<String> =
        pick.templateQuestions().map { q -> s.heardPlace()?.let(q::here) ?: q }

    @Test
    fun theMascotReadsTheParentsQuestionOutLoud() = run { d ->
        val s = d.s
        d.startCoopWith("제일 재밌었던 게 뭐였어?")

        // 첫 걸음(어디)은 뼈대 자리라 앱 질문 — 부모 질문은 다음 꼬리질문 자리에 끼워진다
        assertTrue("첫 걸음이 앱 질문이 아니다: ${d.asked()}", await(8_000) { d.asked() == "오늘 어디 갔었어?" } != null)
        assertTrue(d.push("🎲"))
        // 부모 질문이 **말풍선**에 뜬다. 띠에 소리 없이 뜨는 것이 아니다
        assertTrue("마스코트가 부모 질문을 안 읽었다: ${d.asked()}", await(8_000) { d.asked() == "제일 재밌었던 게 뭐였어?" } != null)
        assertTrue("부모 띠가 떴다 — 마스코트가 읽는 흐름에서는 띠가 없다", s.parentCard == null)
        assertTrue("마이크가 안 켜졌다", s.micEnabled)
    }

    /** 부모 「그만하기」 (#36) — 묻던 질문을 거두고, 모인 답으로 책까지 간다. 아이가 한 답은 그대로 남는다 */
    @Test
    fun theParentCanStopEarlyAndTheBookIsStillMade() = run { d ->
        val s = d.s
        d.startCoopWith("오늘 어디 갔었어?", "거기서 무슨 일이 있었어?", "왜 그랬을까?", "그래서 어떻게 됐어?")
        assertTrue(await(8_000) { d.asked() == "오늘 어디 갔었어?" } != null)
        assertTrue(d.push("🎲"))
        // 다음 걸음이 떠서 답을 기다리는 중에 멈춘다 (둘째 걸음은 꼬리질문 「누구랑」이다 — 부모 둘째 줄은 셋째 걸음)
        assertTrue("다음 질문을 안 물었다: ${d.asked()}", await(8_000) { s.buttons.any { "🎲" in it.label } && s.slotBy["place"] == "child" } != null)

        d.stopCoopByParent()
        assertEquals("parent_stop", s.endReason)
        d.stopCoopByParent()                    // 두 번 눌러도 한 번만
        assertTrue("멈춘 뒤 마스코트가 알리지 않았다: ${s.line}", await(5_000) { "여기까지" in s.line } != null)

        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) d.tap("안 그릴래")
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        assertEquals("아이가 한 답이 바뀌었다", "child", s.slotBy["place"])
        assertTrue("빈 필수 칸을 메우지 않았다: ${s.slotBy}", listOf("problem", "cause", "solution").all { s.slotBy[it] == "mascot" })
        assertTrue("멈춘 질문의 답이 칸에 새어 들어갔다", s.slots.values.none { "stop" in it })
        assertTrue("이야기가 끝났는데 부모 질문이 남아 있다", s.parentQuestions.isEmpty())
    }

    /**
     * 협업은 부모가 준비한 것을 다 물으면 끝난다 (09-30 확정 · guidelines/2 §1-1 · #36) — 남은 꼬리질문은 묻지 않는다.
     * 템플릿만 골랐으면 네 자리가 부모가 준비한 전부라, 결말 자리 뒤의 걸음은 묻지 않는다.
     */
    @Test
    fun coopEndsAfterTheSolutionWhenOnlyAStoryWasPicked() = run { d ->
        val s = d.s
        d.startCoopWith(pick = firefighter)

        val askedTexts = d.walkToBook()
        assertTrue("네 자리를 다 안 물었다: $askedTexts", d.partsAsked().all { it in askedTexts })
        // 결말 자리 뒤의 꼬리질문 「다 끝나고 뭐 했어」 · 「내일 또 하고 싶은 거」는 묻지 않는다.
        // ⚠️ 질문 글로 세지 않는다 — 결말 자리에서 답이 칸을 못 채우면 사다리 한 칸 아래 쉬운 말로 다시 묻는다(같은 걸음)
        // 걸음 수로 세지 않는다 — 조건부 걸음(반응 · 한 말)은 답에 따라 건너뛰어 수가 달라진다
        assertEquals("결말 자리 뒤의 걸음까지 갔다", null, s.slots["after"]); assertEquals(null, s.slots["keep"])
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        assertEquals(0, s.partnerTurns)
    }

    /**
     * 부모 질문이 앞쪽 꼬리질문 자리에서 다 떨어져도 **뼈대 네 자리는 끝까지 묻는다** —
     * 부모 질문이 떨어졌다고 바로 끝내면 「무슨 일 · 왜 · 어떻게 됐나」를 마스코트가 지어 채우게 된다.
     */
    @Test
    fun runningOutOfParentQuestionsEarlyStillAsksTheFourParts() = run { d ->
        val s = d.s
        d.startCoopWith("제일 재밌었던 게 뭐였어?", pick = firefighter)

        val askedTexts = d.walkToBook()
        assertTrue("부모 질문을 안 물었다: $askedTexts", "제일 재밌었던 게 뭐였어?" in askedTexts)
        assertTrue("네 자리를 다 안 물었다: $askedTexts", d.partsAsked().all { it in askedTexts })
        // 결말 자리 뒤의 꼬리질문은 묻지 않는다 (걸음 수로 세지 않는다 — 조건부 걸음은 답에 따라 건너뛴다)
        assertEquals("결말 자리에서 멈추지 않았다", null, s.slots["after"]); assertEquals(null, s.slots["keep"])
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        // 칸을 누가 채웠는지는 보지 않는다 — 아이가 끝내 답을 못 하면 마스코트가 채우는 것이 정상이다. 지키는 것은 「물었다」다
    }

    @Test
    fun stopDoesNothingOutsideCoop() = run { d ->
        d.go(Scene.ADULT)
        assertTrue(await { d.s.scene == Scene.ADULT } != null)
        d.stopCoopByParent()
        assertEquals(null, d.s.endReason)
    }

    /**
     * 고른 이야기면 **답도 그 이야기 것**이다 (10-01) — "소방관은 어디서 일할까?" 에 시연 답이 "어린이집!" 이면 안 된다.
     * 시연 서랍의 🗣 답 · 칸에 들어간 값 · 책 문장이 다 그 이야기에서 나온다.
     */
    @Test
    fun aPickedStoryAnswersInItsOwnWordsToo() = run { d ->
        val s = d.s
        d.startCoopWith(pick = firefighter)
        assertTrue(await(8_000) { d.asked() == firefighter.templateQuestions()[0] } != null)
        assertTrue("시연 답이 그 이야기 것이 아니다: ${s.buttons.map { it.label }}", await(3_000) { s.buttons.any { "큰 건물!" in it.label } } != null)
        assertTrue("일기 답이 섞였다", s.buttons.none { "어린이집" in it.label })

        val askedTexts = d.walkToBook()
        val places = setOf("큰 건물", "밖", "사람 많은 곳", "바쁜 곳", "소방관이 일하는 곳")
        // 앞에서 말한 곳이 「무슨 일」 질문의 「거기」 자리에 들어간다 (10-01) — 질문은 소방관에 맞춘 말 (10-02)
        assertTrue("앞 답이 다음 질문에 안 들어갔다: $askedTexts", "${s.place}에서 불이 나면 소방관은 무슨 일을 할까?" in askedTexts)
        assertTrue("칸 값이 그 이야기 것이 아니다: ${s.place}", s.place in places)
        // 꼬리질문 답까지 — 「블록을 높이높이 쌓아 올렸어요」 같은 일기 문장이 소방관 이야기 책에 들어가면 안 된다
        assertTrue("일기 문장이 책에 들어갔다: ${s.slots}", s.slots.values.none { "어린이집" in it || "블록" in it || "미끄럼틀" in it })
    }

    /** 템플릿만 골랐다 — 오또가 네 자리를 그 이야기의 말로 묻고, 나머지는 일반 모드처럼 앱 질문 */
    @Test
    fun aPickedStoryAsksTheFourPartsInItsOwnWords() = run { d ->
        val s = d.s
        val lines = firefighter.templateQuestions()
        d.startCoopWith(pick = firefighter)
        assertTrue("첫 질문이 고른 이야기의 말이 아니다: ${d.asked()}", await(8_000) { d.asked() == lines[0] } != null)
        assertTrue("부모 띠가 떴다", s.parentCard == null)

        val askedTexts = d.walkToBook()
        val parts = d.partsAsked()
        val at = parts.map { askedTexts.indexOf(it) }
        assertTrue("네 자리를 다 안 물었다: $askedTexts", at.all { it >= 0 })
        assertEquals("네 자리의 순서가 어긋났다: $askedTexts", at.sorted(), at)
        assertTrue("꼬리질문 자리에 앱 질문이 안 나왔다: $askedTexts", askedTexts.size > lines.size)

        // 템플릿 질문은 오또의 질문이다 — 어른의 말로 세지 않는다. 리포트에는 답이 남는다
        assertEquals(0, s.partnerTurns)
        assertTrue("어른 발화로 남았다", s.events.none { it.startsWith("utterance") && "speaker=adult" in it })
        assertEquals(parts, s.coopAsked.map { it.question })
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        assertEquals("이야기가 끝났는데 고른 이야기가 남아 있다", null, s.coopPick)
    }

    /** 템플릿 + 부모 질문 둘 — 뼈대는 템플릿, 부모 질문은 꼬리질문 자리에 적은 순서대로 끼워진다 */
    @Test
    fun parentQuestionsAreSlottedBetweenThePickedStorysParts() = run { d ->
        val s = d.s
        val lines = firefighter.templateQuestions()
        // 빈 줄은 건너뛴다 (입력 화면이 빈 줄을 남겨 둔다)
        d.startCoopWith("제일 재밌었던 게 뭐였어?", "", "거기서 누구를 만났어?", pick = firefighter)

        val askedTexts = d.walkToBook()
        val first = askedTexts.indexOf("제일 재밌었던 게 뭐였어?")
        val second = askedTexts.indexOf("거기서 누구를 만났어?")
        assertTrue("부모 질문을 안 물었다: $askedTexts", first >= 0 && second >= 0)
        assertTrue("적은 순서대로 안 물었다: $askedTexts", first < second)
        assertTrue("부모 질문이 뼈대 첫 자리를 차지했다: $askedTexts", askedTexts.indexOf(lines[0]) < first)
        assertTrue("네 자리를 다 안 물었다: $askedTexts", d.partsAsked().all { it in askedTexts })
        assertFalse("빈 자리를 빈 문장으로 물었다", askedTexts.any { it.isBlank() })
        assertEquals("함께하기 축 = 물어본 부모 질문 수", 2, s.partnerTurns)
        assertEquals("마지막으로 쓴 부모 질문", "거기서 누구를 만났어?", s.adultLine)

        // 부모 질문은 어른의 말로 기록된다. `by` 3종은 늘지 않는다 (협업 §4-1 · §8)
        assertTrue("부모 질문이 speaker=adult 로 안 남았다", s.events.any { it.startsWith("utterance") && "speaker=adult" in it && "제일 재밌었던 게 뭐였어?" in it })
        assertTrue("템플릿 질문이 어른 발화로 남았다", s.events.none { it.startsWith("utterance") && "speaker=adult" in it && lines[0] in it })
        assertTrue("출처에 parent 가 생겼다", s.slotBy.values.all { it in setOf("child", "card", "mascot") })
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        // 이야기가 끝나면 홀더를 비운다 — 오늘 넣은 질문이 다음 이야기에 또 나오면 안 된다. 리포트 재료는 남는다
        assertTrue("이야기가 끝났는데 부모 질문이 남아 있다", s.parentQuestions.isEmpty())
        assertEquals(null, s.coopPick)
        assertEquals(2, s.partnerTurns)
    }

    /** 부모 화면이 받는 만큼(COOP_MAX)은 꼬리질문 자리가 모자라지 않아 다 묻는다 */
    @Test
    fun asManyQuestionsAsTheParentScreenTakesAreAllAsked() = run { d ->
        val s = d.s
        val mine = (1..com.example.finalproject_demo.ui.COOP_MAX).map { "질문 $it 에서 뭐가 제일 좋았어?" }
        d.startCoopWith(*mine.toTypedArray())

        val askedTexts = d.walkToBook()
        assertEquals("적은 순서대로 다 묻지 않았다: $askedTexts", mine, askedTexts.filter { it in mine })
        // 이야기가 끝나면 홀더(parentQIndex 포함)는 비워지므로 리포트 재료인 partnerTurns 로 센다
        assertEquals(mine.size, s.partnerTurns)
    }

    /**
     * 부모 모드에서 넣은 질문이 **[아이 모드로] → [같이 만들기]** 를 지나 살아남는가.
     *
     * 실제 경로 그대로다: 부모 모드를 나가는 버튼이 `goHome()` 이고, 그다음 첫 화면에서 모드를 고른다.
     * 9/22에 `resetStory()`(시작) → `goHome()`(부모 모드를 나갈 때) 순으로 잘못된 자리에서 비워졌다.
     * 지금은 이야기 끝(`coopFinishLog`)에서만 비운다(조장 `07289a3`). 이 검사가 그 자리를 지킨다.
     */
    @Test
    fun questionsEnteredInParentModeSurviveLeavingParentModeAndStartingTheStory() = run { d ->
        val s = d.s
        // 부모 모드 입력 화면이 하는 일 그대로
        s.parentQuestions += listOf("거기서 뭐가 제일 재밌었어?", "누구를 만났어?")
        // [아이 모드로]
        d.goHome()
        assertTrue(await { s.scene == Scene.ADULT } != null)

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue(d.tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)
        assertTrue("시작하자마자 멎었다", await(8_000) { d.asked() == "오늘 어디 갔었어?" } != null)
        assertTrue(d.push("🎲"))
        assertTrue("넣어 둔 질문이 시작 때 사라졌다: ${d.asked()}", await(8_000) { d.asked() == "거기서 뭐가 제일 재밌었어?" } != null)
        assertEquals(2, s.parentQuestions.size)
    }

    /**
     * 책을 다 읽고 선물 화면에서 **아이 화면에 [책장에 꽂기] 버튼이 뜨는가.**
     *
     * 9/22 진웅 실기기 보고: 협업 모드에서 "친구와 함께" 선물 뒤 화면이 멎는다.
     * `GiftsView`(Screen.kt)는 `Stage.Gifts.shown >= 2` 일 때만 버튼을 그리는데, 일기·협업 모드에서 그림을 안 그린 날은
     * `sceneEnd`(Scenes.kt)가 무지개 크레용을 건너뛰어 `Gifts(1)` 에 머문다 → 버튼 없음 → 감독은 "shelf" 를 영원히 기다린다.
     * 시연 서랍의 [📚 책장에 꽂기] 버튼은 뜨므로 서랍으로 미는 검사는 이걸 못 본다 — 그래서 **화면 조건**을 본다.
     */
    @Test
    fun theShelfButtonAppearsOnTheChildScreenAfterTheGifts() = run { d ->
        val s = d.s
        d.startCoopWith("오늘 어디 갔었어?", "무슨 일이 있었어?")
        // 책까지
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 40) {
            if (await(2_000) { s.buttons.any { "🎲" in it.label } } == null) break
            if (!d.push("🎲")) break
            delay(40)
        }
        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) d.tap("안 그릴래")
        assertTrue("책까지 못 갔다", await(20_000) { s.scene == Scene.BOOK } != null)

        // 책 → 친구 평가 → 선물: 시연 서랍 버튼 중 대답 없는 것을 빼고 하나씩 민다 (치영 StoryFlowTest 방식)
        fun pick() = s.buttons.firstOrNull { "🖐" in it.label }
            ?: s.buttons.firstOrNull { "✅" in it.label }
            ?: s.buttons.firstOrNull { "▶" in it.label }
            ?: s.buttons.firstOrNull { "책장에 꽂기" !in it.label && "🤐" !in it.label && "😶" !in it.label }
        guard = 0
        while (s.scene != Scene.END && guard++ < 60) {
            if (await(4_000) { pick() != null } == null) break
            val b = pick()!!
            val before = s.lineId
            b.onClick()
            await(600) { s.lineId != before || pick()?.label != b.label }
            delay(20)
        }
        assertTrue("선물 화면까지 못 갔다: scene=${s.scene}", s.scene == Scene.END)

        // 감독이 "shelf" 를 기다리기 시작하는 순간(서랍에 [책장에 꽂기]가 뜬 뒤) 아이 화면에도 버튼이 있어야 한다
        assertTrue(await(10_000) { s.buttons.any { "책장에 꽂기" in it.label } } != null)
        val gifts = s.stage as? com.example.finalproject_demo.demo.Stage.Gifts
        assertTrue("선물 화면이 아니다: ${s.stage}", gifts != null)
        // 고친 뒤에는 **선물 수가 아니라 `done`** 이 버튼을 그린다 — 안 그린 날은 선물이 하나뿐이다 (9/22)
        assertTrue(
            "아이 화면에 [책장에 꽂기]가 없다 — Gifts(shown=${gifts!!.shown}, done=${gifts.done}). 여기서 앱이 멎는다",
            gifts.done,
        )

        // 꽂은 책은 **저장된 책**이어야 한다 — 책장에서 다시 열리고 쪽마다 문장이 있다 (10-02 사용자 보고: 같이 만들기 책만 내용이 안 남았다)
        val title = s.title
        s.buttons.first { "책장에 꽂기" in it.label }.onClick()
        assertTrue("책장으로 못 갔다: line=${s.line} tpl=${s.template?.key} pages=${s.pageCount} " +
            "caps=${(1..s.pageCount).map { s.bookCaption(it) }} book=${s.completedStoryBook() != null}",
            await(5_000) { s.scene == Scene.SHELF } != null)
        val shelved = s.shelf.first()
        val id = assertNotNullMsg("같이 만들기 책이 저장되지 않았다(책장 표지만 있다)", shelved.savedStoryId)
        val book = assertNotNullMsg("저장 목록에 없다", d.savedStory(id))
        assertEquals(title, book.title)
        assertEquals(s.pageCount, book.pages.size)
        assertTrue("빈 쪽이 있다: ${book.pages}", book.pages.all { it.caption.isNotBlank() })
        val reread = com.example.finalproject_demo.demo.DemoState()
        assertTrue("다시 열 수 없다", reread.restoreStoryBook(book))
        assertEquals(book.pages.map { it.caption }, (1..reread.pageCount).map { reread.bookCaption(it) })
    }

    private fun <T> assertNotNullMsg(msg: String, v: T?): T { assertTrue(msg, v != null); return v!! }

    @Test
    fun withNothingPreparedTheOldBandFlowStays() = run { d ->
        val s = d.s
        d.startCoopWith()   // 아무것도 안 넣음
        assertTrue("옛 흐름이면 띠에 떠야 한다", await(8_000) { s.parentCard != null } != null)
        assertEquals(0, s.parentQIndex)
    }
}
