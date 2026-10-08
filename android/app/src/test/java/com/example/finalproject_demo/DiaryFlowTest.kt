package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
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
        if (s.mode == StoryMode.DIARY && await(2_000) { s.buttons.any { "그림 없이 이야기할래" in it.label } } != null) {
            push("그림 없이 이야기할래")
        }
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
        // 그림일기는 주인공을 고르지 않는다 — 방에서 바로 D0 (docs/일기모드_UI.html)
        assertTrue("그림일기로 바로 안 왔다", await { s.scene == Scene.DIARY } != null)
        d.answerAll("🗣", max = 30)
        // 그림일기(09-30) — 책은 일기 장면 안에서 읽고 선물로 간다
        assertTrue(
            "그림일기로 안 넘어갔다 (장면=${s.scene})",
            await(10_000) { s.buttons.any { "다음 쪽" in it.label || "다 읽었어" in it.label } } != null,
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
        assertTrue(
            "⚠️ 두 번째 그림일기가 시작되지 않는다 — 장면이 ${s.scene} 그대로다",
            await(8_000) { s.scene == Scene.DIARY } != null,
        )
    }

    /** A completed story supplies the hero choice on the next story (#342). */
    @Test
    fun aSecondStoryModeRunReusesTheSavedHeroBeforeTheFirstQuestion() = run { d ->
        val s = d.s
        s.templateKey = "A"
        s.storyHeroCall = "콩이"
        assertTrue("The first completed book must be on the shelf", d.saveFinishedStory())

        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("이야기 만들기 탭"))
        assertTrue("함께할 사람 화면이 안 떴다", await { s.scene == Scene.PARTNER } != null)
        // 함께할 사람은 **말로** 답한다 (탭 카드가 아니다)
        assertTrue("함께할 사람을 못 골랐다 (버튼=${s.buttons.map { it.label }})", d.push("🗣"))
        assertTrue(
            "The next story must offer the saved hero",
            await(8_000) { (s.stage as? com.example.finalproject_demo.demo.Stage.CardsRow)?.cards?.any { it.value == "reuse:0" } == true } != null,
        )
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("reuse:0", "콩이"))
        assertTrue(
            "The saved hero choice must proceed to the first story question",
            await(8_000) { s.scene == Scene.PLACE } != null,
        )
        assertEquals("콩이", s.storyHeroCall)
    }

    // ── 1. 일기 모드 ─────────────────────────────────────────────

    @Test
    fun diaryNeverAsksWhoIsMakingItWithYou() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("오늘 있었던 일로"))
        // 9/21 요청 — 일기 모드는 "누구랑 같이 만들래?"를 묻지 않는다
        assertTrue("그림일기로 바로 오지 않았다", await { s.scene == Scene.DIARY } != null)
        assertEquals("함께할 사람을 물었다", StoryMode.DIARY, s.mode)
        assertFalse("함께할 사람 화면을 거쳤다", s.done.contains("partner"))

        // 동화 모드는 예전 그대로 묻는다
        d.go(Scene.ADULT)
        assertTrue(d.tap("이야기 만들기 탭"))
        assertTrue("동화 모드가 함께할 사람을 안 묻는다", await { s.scene == Scene.PARTNER } != null)
    }

    // 일기 모드의 질문 · 책 흐름은 그림일기로 바뀌었다(09-30) — 검사는 PictureDiaryFlowTest 가 한다.
    // 열한 걸음 · 네 자리 · 마스코트가 메우기는 협업 모드의 길로 남았고 아래 협업 검사가 지킨다.

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
        assertTrue("누가 채웠는지(by) 기록이 없다", s.slotBy.isNotEmpty())
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
        assertTrue(await { s.scene == Scene.DIARY } != null)
        assertTrue("story_start 에 mode=diary 가 없다", "mode=diary" in s.events.first { it.startsWith("story_start") })

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue("story_start 에 mode=coop 가 없다", "mode=coop" in s.events.first { it.startsWith("story_start") })
        // 새 이벤트를 만들지 않았다 (일기 §8 · 협업 §8)
        assertTrue("새 이벤트를 만들었다", s.events.none { it.startsWith("diary_") || it.startsWith("coop_") })
    }
}
