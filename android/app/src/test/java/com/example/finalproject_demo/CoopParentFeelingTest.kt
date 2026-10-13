package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.coopParentCover
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.feelingAhead
import com.example.finalproject_demo.demo.feelingNow
import com.example.finalproject_demo.demo.feelingsSaid
import com.example.finalproject_demo.demo.pageCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 부모가 기분을 물었을 때 책의 기분 칸 (#460 ⑤ · 사용자 결정 · #439).
 *
 * - A: 앞 답에 기분이 붙어 먼저 찼어도(「그럼 속상할 거야」) 부모 질문에 직접 한 답이 책의 기분 칸이 된다.
 *   앞 기분은 버리지 않고 리포트 「마음 말하기」에 남는다
 * - B: 부모 질문 답 「설레」를 그대로 쓰지 않고 「…설레는 마음이었어요」로 다듬고, 마음 말로 센다
 * - 아직 안 일어난 기분은 「…속상할 것 같았어요」 — 지난 일(「속상했던 마음이 한참 남았어요」)로 적지 않는다
 * - 출처(`by`)는 부모 질문 칸의 출처를 옮긴다
 */
class CoopParentFeelingTest {

    private val Q = "가기 전에 어떤 기분이 들어?"
    /** Feeling words in the demo answers (CoopSteps `emo`) */
    private val FEELING_WORD = Regex("속상|아팠|아플|기뻤|기쁠|신났|신날|신나|무서|뿌듯|좋았|좋을|설레|슬펐|슬플|화났|화날|걱정")

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private suspend fun Director.tap(part: String): Boolean {
        repeat(14) {
            if (await(2_500) { s.buttons.any { b -> part in b.label } } == null) return false
            s.buttons.first { part in it.label }.onClick()
            if (await(500) { s.buttons.none { b -> part in b.label } } != null) return true
        }
        return false
    }

    /** One demo answer (🗣) — [feeling] picks one that expects a feeling (「…속상할 거야」), else one with no feeling */
    private suspend fun Director.say(feeling: Boolean): Boolean {
        val said = s.buttons.filter { it.label.startsWith("🗣") }
        val b = (if (feeling) said.firstOrNull { FEELING_WORD.containsMatchIn(it.label) && "거야" in it.label } else null)
            ?: said.firstOrNull { !FEELING_WORD.containsMatchIn(it.label) } ?: return false
        val before = s.lineId
        b.onClick()
        await(600) { s.lineId != before }
        return true
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel() }
    }

    /** [같이 만들기] with the parent's feeling question, a 「곧 해요」 zoo story, steps answered until Otto asks it */
    private suspend fun Director.untilTheParentAsks(feelingOnTheWay: Boolean) {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY } != null)
        s.parentQuestions += Q
        s.coopPick = CoopPick("place", "동물원", "soon")
        assertTrue(tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY } != null)
        var pushes = 0
        while (s.line != Q && pushes++ < 8) {
            if (await(4_000) { s.buttons.any { "🎲" in it.label } } == null) break
            // the feeling rides on the 「무슨 일」 answer — the first step that offers one
            assertTrue(say(feelingOnTheWay && s.reaction == null))
            await(3_000) { s.line == Q }
        }
        assertEquals(Q, s.line)
    }

    private suspend fun Director.answer(text: String) {
        assertTrue("마이크가 안 켜졌다: ${s.line}", await(8_000) { s.micEnabled } != null)
        val before = s.line
        val ok = withTimeoutOrNull(10_000) { while (s.line == before || s.line.isBlank()) { send(Reply.Spoke(text)); delay(60) }; true }
        if (ok == null) throw AssertionError("답이 안 먹혔다: $text · ${s.line}")
    }

    /** On to the book with answers that carry no feeling — the captions of every page */
    private suspend fun Director.captions(): List<String> {
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 40) {
            if (await(2_000) { s.buttons.any { "🎲" in it.label } } == null) break
            if (!say(feeling = false)) break
            delay(40)
        }
        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) tap("안 그릴래")
        assertTrue("책까지 못 갔다", await(20_000) { s.scene == Scene.BOOK } != null)
        return (1..s.pageCount).map { s.bookCaption(it) }
    }

    @Test
    fun aParentsFeelingAnswerWinsOverAFeelingPickedUpOnTheWay() = run { d ->
        val s = d.s
        val c = s.childName
        d.untilTheParentAsks(feelingOnTheWay = true)
        // the earlier answer expected a feeling — it is told ahead, not as one that was
        assertEquals("속상할 것 같은 마음", s.reaction)
        assertEquals("$c${eun(c)} 속상할 것 같았어요", s.slots["reaction"])

        d.answer("설레")
        assertTrue("the feeling step did not take the parent's answer: ${s.reaction}", await(10_000) { s.reaction == "설레" } != null)
        assertEquals("$c${eun(c)} 설레는 마음이었어요", s.slots["reaction"])
        assertEquals(s.slotBy["parent1"], s.slotBy["reaction"])
        // the earlier feeling stays in the report's 마음 말하기, next to the parent's answer
        assertTrue("feelings: ${s.feelings}", "속상했" in s.feelings && "설레" in s.feelings)

        val pages = d.captions()
        assertTrue("no feeling page: $pages", pages.any { "$c${eun(c)} 설레는 마음이었어요" in it })
        assertTrue("the earlier feeling is still in the book: $pages", pages.none { "속상" in it })
        assertTrue(feelingsSaid(s.feelings), "‘설레’" in feelingsSaid(s.feelings))
    }

    @Test
    fun aParentsFeelingAnswerIsASentenceInTheBookAndAFeelingInTheReport() = run { d ->
        val s = d.s
        val c = s.childName
        d.untilTheParentAsks(feelingOnTheWay = false)
        assertNull("an earlier answer already filled the feeling: ${s.reaction}", s.reaction)
        d.answer("설레")
        assertTrue(await(10_000) { s.reaction == "설레" } != null)
        assertEquals("$c${eun(c)} 설레는 마음이었어요", s.slots["reaction"])
        assertEquals("child", s.slotBy["reaction"])
        assertEquals("feelings: ${s.feelings}", 1, s.feelings.count { it == "설레" })

        val pages = d.captions()
        assertTrue("「그때 설레.」 is back: $pages", pages.none { it.trim().endsWith("설레.") })
        assertTrue(pages.toString(), pages.any { "그때 $c${eun(c)} 설레는 마음이었어요" in it })
    }

    @Test
    fun theFeelingStepTakesTheSourceOfTheParentsAnswer() {
        val q = mapOf("parent1" to Q)
        assertEquals("card", coopParentCover("reaction", q, mapOf("parent1" to "설레"), mapOf("parent1" to "card"))?.by)
        assertEquals("child", coopParentCover("reaction", q, mapOf("parent1" to "설레"), mapOf("parent1" to "child"))?.by)
        // Otto's own words at the ladder's end are not the child's feeling — the step asks as usual (#439 review)
        assertNull(coopParentCover("reaction", q, mapOf("parent1" to "설레"), mapOf("parent1" to "mascot")))
        assertNull(coopParentCover("reaction", q, mapOf("parent1" to "몰라"), mapOf("parent1" to "child")))
        assertNull(coopParentCover("reaction", mapOf("parent1" to "뭐 보고 싶어?"), mapOf("parent1" to "사자"), mapOf("parent1" to "child")))
    }

    @Test
    fun feelingWordsAreTurnedOnlyWhenTheFormIsKnown() {
        mapOf("속상했" to "속상할", "무서웠" to "무서울", "기뻤" to "기쁠", "아팠" to "아플", "좋았" to "좋을", "신났" to "신날",
            "뿌듯했" to "뿌듯할", "떨렸" to "떨릴").forEach { (past, ahead) -> assertEquals(past, ahead, feelingAhead(past)) }
        assertNull(feelingAhead("신나다"))
        assertNull(feelingAhead("떨려"))

        mapOf("설레" to "설레는", "설레요" to "설레는", "궁금해요" to "궁금한", "무서워" to "무서운", "슬퍼" to "슬픈", "기뻐!" to "기쁜",
            "떨려" to "떨리는", "기대돼" to "기대되는", "재밌어" to "재밌는", "신났어" to "신났던", "좋아" to "좋은", "신나" to "신나는",
        ).forEach { (said, felt) -> assertEquals(said, felt, feelingNow(said)) }
        assertNull(feelingNow("동물 보는 거"))
        assertNotNull(feelingNow("행복해"))
    }
}
