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
 * 부모 협업 모드 — **부모가 미리 넣은 질문을 마스코트가 읽는** 새 흐름 (부모협업모드_구현설계.md §1-① · ②).
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
    private suspend fun Director.startCoopWith(vararg questions: String) {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertEquals(StoryMode.COOP, s.mode)
        s.parentQuestions += questions
        assertTrue(tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)
    }

    /** 마스코트가 지금 묻고 있는 말 */
    private fun Director.asked(): String = s.line

    @Test
    fun theMascotReadsTheParentsQuestionOutLoud() = run { d ->
        val s = d.s
        d.startCoopWith("오늘 어디 갔었어?", "거기서 뭐가 제일 재밌었어?")

        // 첫 걸음(어디) — 부모 질문이 **말풍선**에 뜬다. 띠에 소리 없이 뜨는 것이 아니다
        assertTrue("마스코트가 부모 질문을 안 읽었다: ${d.asked()}", await(8_000) { d.asked() == "오늘 어디 갔었어?" } != null)
        assertTrue("부모 띠가 떴다 — 마스코트가 읽는 흐름에서는 띠가 없다", s.parentCard == null)
        assertTrue("마이크가 안 켜졌다", s.micEnabled)
    }

    @Test
    fun parentQuestionsGoToTheirOwnPartAndTheAppFillsTheRest() = run { d ->
        val s = d.s
        // 「어디」와 「왜」만 넣었다 — 「무슨 일」 「어떻게 됐나」 는 비어 있다
        d.startCoopWith("오늘 어디 갔었어?", "", "왜 그랬을까, 지호 생각엔?")

        val askedTexts = mutableListOf<String>()
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 40) {
            if (await(2_000) { s.buttons.any { "🎲" in it.label } } == null) break
            askedTexts += d.asked()
            if (!d.push("🎲")) break
            delay(40)
        }
        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) d.tap("안 그릴래")

        assertTrue("「어디」 자리에서 부모 질문을 안 물었다: $askedTexts", "오늘 어디 갔었어?" in askedTexts)
        assertTrue("「왜」 자리에서 부모 질문을 안 물었다: $askedTexts", "왜 그랬을까, 지호 생각엔?" in askedTexts)
        assertFalse("빈 자리를 빈 문장으로 물었다", askedTexts.any { it.isBlank() })
        assertEquals("함께하기 축 = 물어본 부모 질문 수", 2, s.partnerTurns)
        assertEquals("마지막으로 쓴 부모 질문", "왜 그랬을까, 지호 생각엔?", s.adultLine)

        // 부모 질문은 어른의 말로 기록된다. `by` 3종은 늘지 않는다 (협업 §4-1 · §8)
        assertTrue("부모 질문이 speaker=adult 로 안 남았다", s.events.any { it.startsWith("utterance") && "speaker=adult" in it && "오늘 어디 갔었어?" in it })
        assertTrue("출처에 parent 가 생겼다", s.slotBy.values.all { it in setOf("child", "card", "mascot") })
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
        // 이야기가 끝나면 홀더를 비운다 — 오늘 넣은 질문이 다음 이야기에 또 나오면 안 된다. 리포트 재료는 남는다
        assertTrue("이야기가 끝났는데 부모 질문이 남아 있다", s.parentQuestions.isEmpty())
        assertEquals(2, s.partnerTurns)
    }

    @Test
    fun freeQuestionsAreAskedAfterTheFourPartsInOrder() = run { d ->
        val s = d.s
        d.startCoopWith("오늘 어디 갔었어?", "무슨 일이 있었어?", "왜 그랬을까?", "그래서 어떻게 됐어?", "제일 재밌었던 거 하나만 말해 줄래?")

        val askedTexts = mutableListOf<String>()
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 40) {
            if (await(2_000) { s.buttons.any { "🎲" in it.label } } == null) break
            askedTexts += d.asked()
            if (!d.push("🎲")) break
            delay(40)
        }
        val free = askedTexts.indexOf("제일 재밌었던 거 하나만 말해 줄래?")
        val where = askedTexts.indexOf("오늘 어디 갔었어?")
        assertTrue("자유 질문을 안 물었다: $askedTexts", free >= 0)
        assertTrue("자유 질문이 「어디」보다 먼저 나왔다", free > where)
        // 이야기가 끝나면 홀더(parentQIndex 포함)는 비워지므로 리포트 재료인 partnerTurns 로 센다
        assertEquals("다섯 개를 다 쓰지 못했다", 5, s.partnerTurns)
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
        s.parentQuestions += listOf("오늘 어디 갔었어?", "거기서 뭐가 제일 재밌었어?")
        // [아이 모드로]
        d.goHome()
        assertTrue(await { s.scene == Scene.ADULT } != null)

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        assertTrue(d.tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)
        assertTrue("넣어 둔 질문이 시작 때 사라졌다", await(8_000) { d.asked() == "오늘 어디 갔었어?" } != null)
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
        assertTrue(
            "아이 화면에 [책장에 꽂기]가 없다 — Gifts.shown=${gifts!!.shown} · giftCount=${s.giftCount}. 여기서 앱이 멎는다",
            gifts.shown >= s.giftCount,
        )
    }

    @Test
    fun withNoQuestionsTheOldBandFlowStays() = run { d ->
        val s = d.s
        d.startCoopWith()   // 아무것도 안 넣음
        assertTrue("옛 흐름이면 띠에 떠야 한다", await(8_000) { s.parentCard != null } != null)
        assertEquals(0, s.parentQIndex)
    }
}
