package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoBtn
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
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
}
