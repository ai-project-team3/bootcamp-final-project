package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
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

        // 아이가 말하지 않으면(➡️) 질문이 바뀐다 — 동화 모드의 무응답 흐름 그대로다
        val first = s.parentCard
        assertTrue("대답 없음 버튼이 없다", d.push("대답 없음"))
        assertTrue("사다리가 안 내려갔다", await(8_000) { s.parentCard != null && s.parentCard != first } != null)
        assertTrue("사다리 칸이 안 세어졌다", s.parentRung >= 1)

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
