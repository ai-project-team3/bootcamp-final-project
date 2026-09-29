package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoBtn
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.pageCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **동화 모드(일반 모드)를 시작 화면부터 시작 화면까지 한 바퀴 돌려 본다** (9/22).
 *
 * 일기 모드 · 협업 모드는 [DiaryFlowTest] 가 한 바퀴를 돌아 보는데 **동화 모드만 그게 없었다.**
 * 있던 것은 시작 부분만 봤다 — `theStoryModeStillStartsTheOldWay` 는 「함께할 사람」 화면까지,
 * `aSecondStoryModeRunStillGetsPastTheBestiary` 는 「장소」 화면까지다.
 *
 * 시연 녹화에 쓸 경로가 중간에 끊기면 녹화 당일에야 알게 된다. 그래서 **에뮬레이터 없이**
 * 감독(Director)을 그대로 띄우고 대본 버튼으로 끝까지 밀어 본다. 기다리는 시간만 줄이고
 * 장면 · 질문 · 판정 · 칸 채우기는 앱이 하는 그대로 돈다.
 */
class StoryFlowTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) {
            while (!cond()) delay(3)
            true
        }

    /**
     * 시연하는 사람처럼 버튼을 고른다.
     *
     * 녹화할 때는 **아이가 말하는 경우**를 보여 준다. 그래서 말로 답하는 버튼(🎲)을 가장 먼저 고르고,
     * 없으면 화면을 탭하는 버튼(🖐 · ✅ · ▶) 순으로 내려간다.
     * 대답하지 않는 버튼(🤐 · 😶)은 고르지 않는다 — 그 길은 [DiaryFlowTest] 가 따로 본다.
     */
    private fun pick(bs: List<DemoBtn>): DemoBtn? =
        bs.firstOrNull { "🎲" in it.label }
            ?: bs.firstOrNull { "🖐" in it.label }
            ?: bs.firstOrNull { "✅" in it.label }
            ?: bs.firstOrNull { "▶" in it.label }
            ?: bs.firstOrNull { "🗣" in it.label }
            ?: bs.firstOrNull { "🤐" !in it.label && "😶" !in it.label }

    /**
     * 한 걸음 민다 — 버튼이 뜰 때까지 기다렸다가 하나 누르고, 화면이 실제로 움직일 때까지 다시 누른다.
     *
     * 감독은 마스코트가 말을 마친 뒤에 듣기 시작하며 그 전에 들어온 입력을 버린다(`Director.drain`).
     * 사람은 말이 끝난 뒤에 누르지만 검사는 버튼이 뜨자마자 누르므로 첫 클릭이 버려질 수 있다.
     *
     * @return 누른 버튼의 이름. 버튼이 끝내 안 뜨면 null
     */
    private suspend fun Director.step(wait: Long = 4_000): String? {
        if (await(wait) { pick(s.buttons) != null } == null) return null
        repeat(12) {
            val b = pick(s.buttons) ?: return null
            val before = s.lineId
            val label = b.label
            b.onClick()
            if (await(600) { s.lineId != before || pick(s.buttons)?.label != label } != null) return label
        }
        return null
    }

    /**
     * 목표 장면에 닿을 때까지 민다. 닿지 못하면 **어디서 멈췄는지** 남긴다.
     *
     * 지나온 장면은 **따로 지켜보는 코루틴**이 적는다. 한 걸음 밀 때마다 한 번씩만 보면
     * 스스로 넘어가는 장면(「만드는 중」)이나 버튼 한 번에 지나가 버리는 곳(「확인」)을 놓친다 —
     * 9/22 첫 실행에서 CHECK · MAKING 이 빠진 것으로 나왔는데 앱이 아니라 검사가 못 본 것이었다.
     *
     * @return 지나온 장면 순서
     */
    private suspend fun Director.walkTo(goal: Set<Scene>, max: Int = 120): List<Scene> = coroutineScope {
        val path = mutableListOf(s.scene)
        val watch = launch { while (isActive) { if (path.last() != s.scene) path += s.scene; delay(1) } }
        var n = 0
        while (s.scene !in goal && n++ < max) {
            if (step() == null) break
            delay(20)
        }
        watch.cancel()
        if (path.last() != s.scene) path += s.scene
        path.toList()
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

    @Test
    fun eightRepliesDoNotEndAnUnfinishedStory() = run { d ->
        d.s.turn = 8
        d.go(Scene.DRAW)

        assertTrue("그림 장면이나 자동 종료까지 도달하지 못했다", await {
            d.s.stage is Stage.DrawPad || d.s.scene == Scene.MAKING
        } != null)
        assertEquals("여덟 번째 답 뒤에 이야기를 강제로 끝냈다", Scene.DRAW, d.s.scene)
        assertTrue("아이에게 다음 이야기 행동을 보여 주지 않았다", d.s.stage is Stage.DrawPad)
    }

    @Test
    fun generatedStoryWithNineScenesCanBeReadToTheEnd() = run { d ->
        d.s.storyCaptions = (1..9).map { "아이의 이야기 $it 쪽이에요." }
        d.go(Scene.BOOK)
        assertTrue("책 화면이 열리지 않았다", await { d.s.stage is Stage.BookPage } != null)

        var clicks = 0
        while (d.s.bookPage < 9 && clicks++ < 30) {
            assertTrue("다음 쪽으로 넘기지 못했다", d.step() != null)
        }
        assertEquals("생성된 마지막 장면에 닿지 못했다", 9, d.s.bookPage)
        assertEquals("마지막 장면의 아이 이야기가 사라졌다", "아이의 이야기 9 쪽이에요.", d.s.bookCaption(9))
        assertTrue("마지막 장면에서 책을 덮지 못했다", d.step() != null)
        assertTrue("마지막 장면 뒤로 넘어가지 못했다", await { d.s.scene == Scene.FRIENDS } != null)
    }

    /** 동화 모드를 책이 펼쳐질 때까지 민다 */
    private suspend fun Director.toBook(): List<Scene> {
        go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", await { s.buttons.any { "이야기 만들기 탭" in it.label } } != null)
        s.buttons.first { "이야기 만들기 탭" in it.label }.onClick()
        assertTrue("동화 모드가 안 시작됐다", await { s.scene == Scene.PARTNER } != null)
        return walkTo(setOf(Scene.BOOK))
    }

    /**
     * **시연 경로 한 바퀴** — 시작 화면에서 출발해 책을 덮고 시작 화면으로 돌아오는가.
     *
     * 여기서 멈추면 시연 녹화가 그 자리에서 끊긴다. 그래서 멈춘 장면과 그때 화면에 있던 것을
     * 실패 메시지에 그대로 남긴다.
     */
    @Test
    fun theDemoPathRunsOneFullLapWithoutGettingStuck() = run { d ->
        val s = d.s

        val toBook = d.toBook()
        assertTrue(
            "책까지 못 갔다 — ${s.scene} 에서 멈췄다 · 말=\"${s.line}\" · 버튼=${s.buttons.map { it.label }}\n" +
                "  지나온 길: ${toBook.joinToString(" → ")}",
            s.scene == Scene.BOOK,
        )

        // 시연에서 보여 주는 장면이 하나도 빠지지 않았는가 (구현대본 §2 순서)
        val mustShow = listOf(
            Scene.PARTNER, Scene.PLACE, Scene.EVENT, Scene.CAUSE,
            Scene.DRAW, Scene.PLOT, Scene.DINO, Scene.SOUND, Scene.CHECK, Scene.SOLUTION,
            Scene.MAKING, Scene.BOOK,
        )
        val missing = mustShow.filter { it !in toBook }
        assertTrue("시연에서 보여 줄 장면을 건너뛰었다: $missing\n  지나온 길: ${toBook.joinToString(" → ")}", missing.isEmpty())

        // 책을 **끝까지 넘겨** 시작 화면으로 돌아온다 — 책 뒤의 친구 평가 · 끝내기 · 책장도 시연에 들어간다
        val after = d.walkTo(setOf(Scene.ADULT))
        assertTrue(
            "책 뒤에서 시작 화면으로 못 돌아왔다 — ${s.scene} 에서 멈췄다 · 버튼=${s.buttons.map { it.label }}\n" +
                "  지나온 길: ${after.joinToString(" → ")}",
            s.scene == Scene.ADULT,
        )
        assertTrue("책 뒤 장면(친구 평가 · 끝내기)을 건너뛰었다: ${after.joinToString(" → ")}", Scene.FRIENDS in after && Scene.END in after)
        assertTrue("만든 책이 책장에 안 남았다", s.heroes.isNotEmpty())
    }

    /**
     * 틀 빈칸(`stop` · `try1` · `helper` …)이 **슬롯 이름으로 새어 나가지 않는가** (09-27 · guidelines/2 §1-1 규칙 1).
     *
     * 슬롯은 12종 **닫힌 목록**이다. 목록 밖은 `slot=extra` 에 `of=<빈칸>` 으로 붙여 내보낸다.
     * 전에는 틀 빈칸 16개가 `slot=stop` 처럼 그대로 나가, 부모 리포트가 12종을 셀 때 숫자가 섞였다.
     */
    @Test
    fun templateBlanksLeaveAsExtraNotAsNewSlotNames() = run { d ->
        d.toBook()
        val core = setOf("place", "problem", "reaction", "cause", "newcomer", "name", "companion", "sound", "adult", "solution", "title", "extra")
        val filled = d.s.events.filter { it.startsWith("slot_filled") }
        val slotOf = { e: String -> Regex("""slot=([^,]+)""").find(e)?.groupValues?.get(1) }
        val leaked = filled.mapNotNull(slotOf).filter { it !in core }.distinct()
        assertTrue("12종 밖 이름이 slot 으로 나갔다: $leaked\n  ${filled.joinToString("\n  ")}", leaked.isEmpty())
        assertTrue(
            "틀 빈칸이 extra + of 로 하나도 안 나갔다 — 한 바퀴에 적어도 한 칸은 묻는다\n  ${filled.joinToString("\n  ")}",
            filled.any { "slot=extra" in it && "of=" in it && "of=sight" !in it },
        )
    }

    /**
     * 한 바퀴 돌고 나면 **아이가 말한 것으로 책이 차 있는가.**
     *
     * 끝까지 가기만 하고 책이 비어 있으면 시연으로 쓸 수 없다.
     */
    @Test
    fun theBookAtTheEndIsMadeOfWhatTheChildSaid() = run { d ->
        val s = d.s
        d.toBook()
        assertEquals("동화 모드가 아니다", StoryMode.STORY, s.mode)

        val emptySlots = listOf("place", "problem", "cause", "newcomer", "sound", "solution")
            .filterIndexed { i, _ -> s.reqSlots[i] == null }
        assertTrue("필수 칸이 비었다: $emptySlots (${s.filled}/${s.reqCount})", emptySlots.isEmpty())
        // `endReason` 은 일기 · 협업 모드가 쓰는 칸이다(DiaryScenes). 동화 모드는 필수 칸 여섯이
        // 차면 끝난다 — 그것을 본다
        assertEquals("필수 칸이 다 안 찼다", s.reqCount, s.filled)

        assertTrue("쪽 수가 6~8 밖이다 (${s.pageCount}쪽)", s.pageCount in 6..8)
        val blankPages = (1..s.pageCount).filter { s.bookCaption(it).isBlank() }
        assertTrue("자막이 빈 쪽이 있다: ${blankPages}", blankPages.isEmpty())

        // 「사건」 장면의 꼬리질문 답이 **책에 실렸는가** (9/22).
        // 전에는 물어보고 저장하기만 하고 어느 틀도 읽지 않았다
        val reaction = s.slots["reaction"].orEmpty()
        assertTrue("사건 장면의 답이 비었다", reaction.isNotBlank())
        val book = (1..s.pageCount).joinToString(" ") { s.bookCaption(it) }
        assertTrue("아이가 답한 \"$reaction\" 가 책 어느 쪽에도 없다", reaction in book)

        assertTrue("아이 말이 인용으로 하나도 안 남았다", s.quotes.isNotEmpty())
        assertTrue("아이가 고른 배경이 안 붙었다", s.bgName.isNotBlank())
    }

    /**
     * **진행 막대가 마지막 질문에서 끝까지 차는가** (9/22 지적).
     *
     * 협업 모드는 `coopRunsToTheBookAndMarksWhoWroteWhat` 가 이미 본다. 동화 모드는 보는 곳이 없었다.
     */
    @Test
    fun theProgressBarIsFullWhenTheStoryIsDone() = run { d ->
        val s = d.s
        d.toBook()
        assertEquals(
            "질문을 다 했는데 진행 막대가 안 찼다 (${s.askDone}/${s.askTotal})",
            s.askTotal, s.askDone,
        )
    }

    // ── 이야기 도중 나갔다가 이어 가기 (09-29 앱 틀 · 🏠 · 🔒) ─────────────────────

    /** 동화 모드를 시작해 「왜 그랬을까」 장면까지 민다 */
    private suspend fun Director.toCause() {
        go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", await { s.buttons.any { "이야기 만들기 탭" in it.label } } != null)
        s.buttons.first { "이야기 만들기 탭" in it.label }.onClick()
        assertTrue("동화 모드가 안 시작됐다", await { s.scene == Scene.PARTNER } != null)
        walkTo(setOf(Scene.CAUSE))
        assertEquals(Scene.CAUSE, s.scene)
    }

    @Test
    fun leavingMidStoryWithHomeAndComingBackResumesWhereItStopped() = run { d ->
        val s = d.s
        d.toCause()
        val used = s.usedToday
        val slots = s.slots.toMap()
        d.leaveToRoom()
        assertTrue("🏠 로 방에 안 돌아왔다", await { s.scene == Scene.ADULT } != null)
        assertEquals("멈춘 장면을 기억하지 않았다", Scene.CAUSE, s.paused)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("resume", "이어서"))
        assertTrue("「이어서」 를 눌렀는데 멈춘 장면으로 안 갔다 — ${s.scene}", await { s.scene == Scene.CAUSE } != null)
        assertEquals("이어 가는데 하루 별을 또 썼다", used, s.usedToday)
        assertEquals("이어 가는데 이야기 조각이 지워졌다", slots, s.slots.toMap())
        assertEquals("이어 간 뒤에도 멈춘 장면이 남았다", null, s.paused)
    }

    @Test
    fun theParentDoorMidStoryAlsoKeepsTheStory() = run { d ->
        val s = d.s
        d.toCause()
        d.openParent()
        assertTrue("🔒 로 어른 확인이 안 떴다", await { s.stage is com.example.finalproject_demo.demo.Stage.Pin } != null)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("pin:ok", "통과"))
        assertTrue("부모 영역으로 안 갔다", await { s.scene == Scene.PARENT } != null)
        assertEquals(Scene.CAUSE, s.paused)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("home", "처음으로"))
        assertTrue("부모 영역에서 방으로 안 돌아왔다", await { s.scene == Scene.ADULT } != null)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("resume", "이어서"))
        assertTrue("부모 영역을 다녀온 뒤 이어 가지 못했다 — ${s.scene}", await { s.scene == Scene.CAUSE } != null)
    }

    @Test
    fun startingANewStoryDropsThePausedOne() = run { d ->
        val s = d.s
        d.toCause()
        d.leaveToRoom()
        assertTrue(await { s.scene == Scene.ADULT } != null)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("start", "이야기 만들기"))
        assertTrue("「새로」 를 눌렀는데 새 이야기가 안 시작됐다", await { s.scene == Scene.PARTNER } != null)
        assertEquals("새 이야기를 시작했는데 멈춘 이야기가 남았다", null, s.paused)
    }

    /**
     * 책까지 만든 뒤 🏠 로 나가고 → 방에서 「✨ 새로」 (09-29 사용자 — 「새로를 누르면 그림책 마지막 부분이 나온다」).
     * 다 만든 책은 「만들던 이야기」가 아니다 — 이어 가기를 묻지 않고, 새로 누르면 새 이야기의 첫 장면이어야 한다
     */
    @Test
    fun leavingAFinishedBookDoesNotOfferToContinueAndNewStartsFresh() = run { d ->
        val s = d.s
        d.toBook()
        assertEquals(Scene.BOOK, s.scene)
        d.leaveToRoom()
        assertTrue(await { s.scene == Scene.ADULT } != null)
        val pausedAt = s.paused
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("start", "이야기 만들기"))
        assertTrue("「새로」 뒤에 새 이야기가 아니라 ${s.scene} · ${s.stage} 가 떴다", await(8_000) { s.scene == Scene.PARTNER } != null)
        delay(500)
        assertEquals("「새로」 뒤 장면", Scene.PARTNER, s.scene)
        assertEquals("다 만든 책인데 「이어서 할까?」 대상으로 남았다", null, pausedAt)
    }

    /** 이야기 도중(책 전) 나갔다가 「✨ 새로」 — 새 이야기의 첫 장면 */
    @Test
    fun newAfterLeavingMidStoryStartsFresh() = run { d ->
        val s = d.s
        d.toCause()
        d.leaveToRoom()
        assertTrue(await { s.scene == Scene.ADULT } != null)
        d.send(com.example.finalproject_demo.demo.Reply.Tapped("start", "이야기 만들기"))
        assertTrue("「새로」 뒤에 ${s.scene} · ${s.stage}", await(8_000) { s.scene == Scene.PARTNER } != null)
        delay(300)
        assertEquals(Scene.PARTNER, s.scene)
    }
}
