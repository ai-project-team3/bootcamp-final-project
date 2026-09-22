package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.pageCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일기 모드 · 부모 협업 모드를 **실제로 한 바퀴 돌려 본다** — 글자 검사(DiaryTest)가 아니라 흐름 검사다.
 *
 * 감독(Director)을 그대로 띄우고 대본 버튼을 눌러 답한다. 데모 속도만 아주 빠르게 하고
 * 장면 · 질문 · 판정 · 칸 채우기는 앱이 하는 그대로 돈다.
 */
class DiaryFlowTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) {
            while (!cond()) delay(3)
            true
        }

    /**
     * 대본 버튼을 누른다.
     *
     * 감독은 마스코트가 말을 마칠 때까지 기다렸다가 듣기 시작하고, 그때 그 전에 들어온 입력을 버린다
     * (`Director.drain` — 앞 장면의 입력이 새 질문에 섞이지 않게). 사람은 말이 끝난 뒤에 누르지만
     * 이 검사는 버튼이 뜨자마자 누르므로, 눌러도 안 먹으면 다시 누른다.
     */
    private suspend fun Director.tap(part: String): Boolean {
        repeat(14) {
            if (await(2_500) { s.buttons.any { b -> part in b.label } } == null) {
                println("[tap 실패] \"$part\" · 장면=${s.scene} · 말=\"${s.line}\" · 띠=\"${s.parentCard}\" · 버튼=${s.buttons.map { it.label }}")
                return false
            }
            s.buttons.first { part in it.label }.onClick()
            if (await(500) { s.buttons.none { b -> part in b.label } } != null) return true
        }
        println("[tap 먹히지 않음] \"$part\" · 장면=${s.scene}")
        return false
    }

    /**
     * 대본 버튼을 누르되, 화면이 실제로 움직일 때까지 다시 누른다.
     *
     * 감독은 마스코트 말이 끝난 뒤에 듣기 시작하며 그 전 입력을 버린다(`drain`). 사람은 말이 끝난 뒤 누르지만
     * 검사는 버튼이 뜨자마자 누르므로 첫 클릭이 버려질 수 있다.
     */
    private suspend fun Director.push(part: String): Boolean {
        repeat(12) {
            val b = s.buttons.firstOrNull { part in it.label } ?: return false
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || s.buttons.none { x -> part in x.label } } != null) return true
        }
        return false
    }

    /** 일기 질문을 끝까지 무작위 대본 답으로 밀어 본다 */
    private suspend fun Director.answerAll(button: String = "🎲", max: Int = 24): Int {
        var n = 0
        while (s.scene == Scene.DIARY && n < max) {
            if (await(2_000) { s.buttons.any { button in it.label } } == null) break
            if (!push(button)) break
            n++
            delay(40)
        }
        return n
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + sup)
        val d = Director(scope)
        d.s.speed = 0.01          // 기다리는 시간만 줄인다 (대본 · 판정은 그대로)
        try {
            block(d)
        } finally {
            sup.cancel()
        }
    }

    /**
     * 책을 한 권 만들고 **두 번째 이야기로 들어갈 수 있는가** (9/22).
     *
     * *"동화책을 만들고 도감 부분에서 이후로 안 넘어간다"* 는 지적에서 나왔다.
     * 한 권을 끝내고 처음 화면으로 돌아와 다시 시작하면 도감이 뜨는데, 거기서 주인공을
     * 골라도 다음 장면으로 가지 않는다는 것이다. 한 바퀴를 두 번 돌아 본다.
     */
    @Test
    fun aSecondStoryStillGetsPastTheBestiary() = run { d ->
        val s = d.s

        // ── 첫 번째 이야기 — 책까지
        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("오늘 있었던 일로"))
        assertTrue("도감이 안 떴다", await { s.scene == Scene.BESTIARY } != null)
        assertTrue("첫 이야기에서 주인공을 못 골랐다", d.tap("카드를 탭"))
        assertTrue("질문으로 안 왔다", await { s.scene == Scene.DIARY } != null)
        d.answerAll("🗣", max = 30)
        if (s.buttons.any { "안 그릴래" in it.label }) d.tap("안 그릴래")
        assertTrue(
            "책으로 안 넘어갔다 (장면=${s.scene})",
            await(10_000) { s.scene == Scene.MAKING || s.scene == Scene.BOOK } != null,
        )

        // ── 책을 **끝까지 넘겨** 시작 화면으로 돌아간다.
        //    goHome() 으로 건너뛰면 책 뒤의 장면들(친구 평가 · 끝내기 · 책장)을 지나치게 된다 —
        //    지적받은 증상이 그 구간에서 나올 수 있으므로 실제로 밟는다 (9/22)
        var steps = 0
        while (s.scene != Scene.ADULT && steps < 80) {
            val b = s.buttons.firstOrNull() ?: run { delay(30); null }
            if (b != null) { b.onClick(); steps++ }
            delay(30)
        }
        assertTrue(
            "책 뒤에서 시작 화면으로 못 돌아왔다 (장면=${s.scene} · 버튼=${s.buttons.map { it.label }})",
            s.scene == Scene.ADULT,
        )
        assertTrue("도감이 비었다 — 주인공이 하나도 없으면 고를 수가 없다", s.heroes.isNotEmpty())

        assertTrue("두 번째 이야기를 못 시작했다", d.tap("오늘 있었던 일로"))
        assertTrue("두 번째 이야기에서 도감이 안 떴다", await { s.scene == Scene.BESTIARY } != null)
        assertTrue("도감에 카드 버튼이 없다 (버튼=${s.buttons.map { it.label }})", d.tap("카드를 탭"))
        assertTrue(
            "⚠️ 도감에서 안 넘어간다 — 주인공을 골랐는데 장면이 ${s.scene} 그대로다",
            await(8_000) { s.scene == Scene.DIARY } != null,
        )
    }

    /**
     * **동화 모드**로 한 권 만들고 두 번째 이야기의 도감을 지나갈 수 있는가 (9/22).
     *
     * 동화 모드는 도감 앞에 「함께할 사람」 화면이 하나 더 있다. 지적받은 *"동화책"* 은 이쪽이다.
     */
    @Test
    fun aSecondStoryModeRunStillGetsPastTheBestiary() = run { d ->
        val s = d.s

        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("이야기 만들기 탭"))
        assertTrue("함께할 사람 화면이 안 떴다", await { s.scene == Scene.PARTNER } != null)
        // 함께할 사람은 **말로** 답한다 (탭 카드가 아니다)
        assertTrue("함께할 사람을 못 골랐다 (버튼=${s.buttons.map { it.label }})", d.push("🗣"))
        assertTrue(
            "동화 모드에서 도감이 안 떴다 (장면=${s.scene})",
            await(8_000) { s.scene == Scene.BESTIARY } != null,
        )
        assertTrue("도감에 카드 버튼이 없다 (버튼=${s.buttons.map { it.label }})", d.tap("카드를 탭"))
        assertTrue(
            "⚠️ 동화 모드 도감에서 안 넘어간다 — 장면이 ${s.scene} 그대로다",
            await(8_000) { s.scene == Scene.PLACE } != null,
        )
    }

    // ── 1. 일기 모드 ─────────────────────────────────────────────

    @Test
    fun diaryNeverAsksWhoIsMakingItWithYou() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("오늘 있었던 일로"))
        // 9/21 요청 — 일기 모드는 "누구랑 같이 만들래?"를 묻지 않는다
        assertTrue("도감으로 바로 오지 않았다", await { s.scene == Scene.BESTIARY } != null)
        assertEquals("함께할 사람을 물었다", StoryMode.DIARY, s.mode)
        assertFalse("함께할 사람 화면을 거쳤다", s.done.contains("partner"))

        // 동화 모드는 예전 그대로 묻는다
        d.go(Scene.ADULT)
        assertTrue(d.tap("이야기 만들기 탭"))
        assertTrue("동화 모드가 함께할 사람을 안 묻는다", await { s.scene == Scene.PARTNER } != null)
    }

    @Test
    fun aChildWhoTalksFillsAllFourPartsAndManyTails() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(d.tap("오늘 있었던 일로"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue(d.tap("카드를 탭"))
        assertTrue("S3′ 로 안 왔다", await { s.scene == Scene.DIARY } != null)
        assertEquals("일기 모드의 필수 칸은 기승전결 네 자리", 4, s.reqCount)

        val turns = d.answerAll()
        // 스무고개처럼 늘린 결과 — 네 질문이 아니라 여덟 번 이상 주고받는다
        assertTrue("주고받은 횟수가 ${turns}번뿐 — 8~12턴이 안 나온다", turns >= 8)

        assertTrue("그리기 물음이나 책 만들기로 안 넘어갔다", await(8_000) {
            s.scene == Scene.MAKING || s.scene == Scene.BOOK || s.buttons.any { "안 그릴래" in it.label }
        } != null)
        if (s.buttons.any { "안 그릴래" in it.label }) d.tap("안 그릴래")
        assertTrue("책 만들기로 안 넘어갔다 scene=${s.scene} end=${s.endReason} filled=${s.filled} line=${s.line} btn=${s.buttons.map{it.label}}", await(8_000) { s.scene == Scene.MAKING || s.scene == Scene.BOOK } != null)

        assertEquals("네 자리가 다 찼다", 4, s.filled)
        assertEquals("story_ready", s.endReason)
        // 꼬리질문이 실제로 책 문장을 더했는가 — 질문을 늘린 이유가 이것이다
        val tails = listOf("companion", "detail", "reaction", "said", "after", "keep").count { s.slots[it] != null }
        assertTrue("꼬리질문으로 모은 문장이 ${tails}개뿐", tails >= 3)
        assertTrue("쪽 수가 6~8 밖", s.pageCount in 6..8)
        assertTrue("아이 말이 인용으로 남지 않았다", s.quotes.isNotEmpty())
        // 배경이 아이가 말한 곳을 따라갔는가
        assertNotEquals("배경이 상상 세계 그대로다", "bg_space", s.bgName)
    }

    @Test
    fun diaryDemoAnswersTellOneConsistentDayThroughTheBook() = run { d ->
        val s = d.s
        s.mode = StoryMode.DIARY
        d.go(Scene.DIARY)
        assertTrue(await { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } != null)

        val turns = d.answerAll("🎬 오늘 이야기 시연 답")
        assertEquals("시연 답이 모든 질문을 끝까지 잇지 못했다", 11, turns)
        assertTrue(await { s.buttons.any { "안 그릴래" in it.label } } != null)
        assertTrue(d.tap("안 그릴래"))
        assertTrue(await(8_000) { s.scene == Scene.MAKING || s.scene == Scene.BOOK } != null)

        assertEquals("어린이집", s.place)
        assertEquals("민준이", s.companionKind)
        assertEquals("블록이 무너짐", s.problem)
        assertEquals("너무 높이 쌓아서", s.cause)
        assertEquals("다시 쌓았어", s.solution)
        assertEquals("story_ready", s.endReason)
        assertTrue(listOf("place", "problem", "cause", "solution").all { s.slotBy[it] == "child" })

        val book = (1..s.pageCount).joinToString(" ") { s.bookCaption(it) }
        assertTrue("아이의 블록 이야기가 책에 없다", "블록" in book && "무너" in book)
        assertTrue("친구가 책에 없다", "민준이" in book)
        assertTrue("해결 장면이 책에 없다", "다시 쌓" in book)
    }

    @Test
    fun silenceWalksTheLadderThenEndsOnTwoMascotPicks() = run { d ->
        val s = d.s
        s.mode = StoryMode.DIARY
        d.go(Scene.DIARY)
        assertTrue(await { s.scene == Scene.DIARY } != null)

        val asked = mutableSetOf<String>()
        var guard = 0
        while (s.scene == Scene.DIARY && s.endReason == null && guard++ < 40) {
            if (await(1_500) { s.buttons.any { "대답 없음" in it.label } } == null) break
            asked += s.line
            s.buttons.first { "대답 없음" in it.label }.onClick()
            delay(30)
        }

        assertTrue("사다리가 질문을 바꾸지 않았다 (물어본 질문 ${asked.size}개)", asked.size >= 4)
        assertTrue("카드를 띄웠다 — 일기 모드는 사다리 뒤에 그림 3장을 붙이지 않는다", s.modeCard == 0)
        assertEquals("mascot_pick 2회 연속이 끝나는 조건이다", "mascot_pick", s.endReason)
        assertTrue(s.mascotPicks >= 2)
        assertTrue("완전 무응답 갈림길이 안 나왔다", await { s.buttons.any { "오늘은 여기까지" in it.label } } != null)
        assertTrue("마스코트가 채운 말이 아이 인용으로 새어 나갔다", s.quotes.isEmpty())
        assertEquals("마스코트가 채운 것은 주고받기로 세지 않는다", 0, s.modeVoice)
    }

    // ── 2. 부모 협업 모드 ────────────────────────────────────────

    @Test
    fun coopPutsTheQuestionInTheParentBandNotTheMascotBubble() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue("같이 만들기 버튼이 없다", d.tap("같이 만들기"))
        assertEquals(StoryMode.COOP, s.mode)
        assertTrue("도감으로 바로 오지 않았다", await { s.scene == Scene.BESTIARY } != null)
        assertTrue(d.tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)

        // ASK′ — 질문은 소리 없이 부모 띠에 뜬다. 마스코트는 질문하지 않는다 (협업 §2-1)
        assertTrue("부모 띠에 질문 카드가 안 떴다", await { s.parentCard != null } != null)
        assertFalse("마스코트가 질문을 말해 버렸다: ${s.line}", s.line == s.parentCard)

        // 사다리는 동화 모드와 똑같이 마스코트(=Director)가 내린다. 띠에는 버튼이 없다 (9/21)
        assertTrue("띠에 [다르게 물어볼래] 버튼이 남아 있다", s.buttons.none { "다르게 물어볼래" in it.label })
        assertTrue("띠에 [내가 답할래] 버튼이 남아 있다", s.buttons.none { "내가 답할래" in it.label })

        // 아이가 말하지 않으면(➡️) 질문이 바뀐다 — 동화 모드의 무응답 흐름 그대로다.
        //
        // ⚠️ **순간값을 보면 안 된다** (9/22). 검사에서는 `speed = 0.0` 이라 기다리는 시간이 0이고,
        //    사다리가 한 순간에 끝까지 내려간 뒤 다음 걸음으로 넘어간다. 그때 `parentCard` 는 이미
        //    다음 질문이고 `parentRung` 은 걸음이 바뀌며 0으로 되돌아가 있다.
        //    그래서 **쌓이는 기록(로그)** 으로 본다 — 사다리를 내려갈 때마다 한 줄씩 남는다.
        assertTrue("띠에 [내가 답할래] 버튼이 남아 있다", s.buttons.none { "내가 답할래" in it.label })

        // 아이가 말하지 않으면(➡️) 질문이 바뀐다 — 동화 모드의 무응답 흐름 그대로다.
        //
        // ⚠️ **순간값을 보면 안 된다** (9/22). 검사에서는 기다리는 시간을 거의 0으로 줄여 두어
        //    사다리가 한 순간에 끝까지 내려간 뒤 다음 걸음으로 넘어간다. 그때 `parentCard` 는 이미
        //    다음 질문이고, `parentRung` 은 걸음이 바뀌며(`coopAsk` 가 0으로 되돌린다) 0이다.
        //    띠를 마스코트 말풍선과 번갈아 띄우기 시작한 뒤로(9/22) 이 창이 더 좁아져 실제로 깨졌다.
        //    그래서 **쌓이는 기록(로그)** 으로 본다 — 사다리를 내려갈 때마다 한 줄씩 남는다.
        val first = s.parentCard
        assertTrue("대답 없음 버튼이 없다", d.push("대답 없음"))
        assertTrue(
            "사다리가 안 내려갔다 (처음=\"$first\")",
            await(8_000) { s.log.any { "사다리" in it && "질문을 바꿔 다시" in it } } != null,
        )

        // `by: parent` 를 새로 만들지 않는다 (협업 §4-1)
        assertTrue("출처에 parent 가 생겼다", s.slotBy.values.all { it in setOf("child", "card", "mascot") })
        // 버튼이 보내던 값이 칸으로 새어 들어가지 않는다 (9/21 `coop:adult` 오류)
        assertTrue("칸에 coop: 값이 들어갔다: ${s.slots}", s.slots.values.none { it.startsWith("coop:") || it.startsWith("adult:") })
    }

    @Test
    fun coopRunsToTheBookAndMarksWhoWroteWhat() = run { d ->
        val s = d.s
        s.mode = StoryMode.COOP
        d.go(Scene.DIARY)
        assertTrue(await { s.scene == Scene.DIARY } != null)
        // 장면을 바로 열어도 협업 모드가 일기 모드로 덮이면 안 된다
        assertEquals("협업 모드가 일기 모드로 덮였다", StoryMode.COOP, s.mode)
        assertTrue("협업인데 부모 띠가 안 떴다", await { s.parentCard != null } != null)
        d.answerAll("🗣", max = 30)
        if (s.buttons.any { "안 그릴래" in it.label }) d.tap("안 그릴래")
        assertTrue("책 만들기로 안 넘어갔다", await(8_000) { s.scene == Scene.MAKING || s.scene == Scene.BOOK } != null)
        assertEquals(4, s.filled)
        assertTrue("누가 지었는지 기록이 없다", s.author.isNotEmpty())
        assertTrue("협업인데 어른이 읽어 준 질문이 안 남았다", s.adultLine != null)
        assertTrue("부모 띠가 책에서도 떠 있다", s.parentCard == null)

        // 진행 막대는 **마지막 질문까지 가면 끝까지 차야 한다** (9/22 지적).
        // 전에는 필수 칸(4개)만 세어 질문 열둘을 다 물어도 막대가 3분의 1에서 멈췄고,
        // 그다음에는 "칸이 찼는가" 로 세다가 아이가 답하지 않은 선택 질문 때문에 끝까지 못 갔다
        assertEquals(
            "질문을 다 했는데 진행 막대가 안 찼다 (${s.askDone}/${s.askTotal})",
            s.askTotal, s.askDone,
        )
    }

    // ── 3. 동화 모드가 그대로인가 ─────────────────────────────────

    @Test
    fun theStoryModeStillStartsTheOldWay() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(d.tap("이야기 만들기 탭"))
        assertTrue("동화 모드는 함께할 사람부터다", await { s.scene == Scene.PARTNER } != null)
        assertEquals(StoryMode.STORY, s.mode)
        assertEquals("동화 모드의 필수 칸은 6개", 6, s.reqCount)
        val start = s.events.first { it.startsWith("story_start") }
        assertTrue("story_start 에 mode 가 없다: $start", "mode=story" in start)
        assertTrue("부모 띠가 동화 모드에 떴다", s.parentCard == null)
    }

    @Test
    fun eachModeCarriesItsOwnOneNewEventField() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(d.tap("오늘 있었던 일로"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue("story_start 에 mode=diary 가 없다", "mode=diary" in s.events.first { it.startsWith("story_start") })

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue("story_start 에 mode=coop 가 없다", "mode=coop" in s.events.first { it.startsWith("story_start") })
        // 새 이벤트를 만들지 않았다 (일기 §8 · 협업 §8)
        assertTrue("새 이벤트를 만들었다", s.events.none { it.startsWith("diary_") || it.startsWith("coop_") })
    }
}
