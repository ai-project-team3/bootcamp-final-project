package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopPlan
import com.example.finalproject_demo.demo.CoopPlanExtras
import com.example.finalproject_demo.demo.CoopPlanStore
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.CoopShelved
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalCoopBookStore
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopBeforeAfter
import com.example.finalproject_demo.demo.coopPlanFromJson
import com.example.finalproject_demo.demo.coopPlanToJson
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 부모 리포트 「가기 전 · 다녀온 뒤」 칸 (협업모드_확장_설계 §2-5 · A-3).
 * 아이가 말한 것만 따옴표, 카드 · 마스코트는 그렇다고 · 요약은 「무슨 일」이 두 책 모두 아이 말일 때만 · 점수 말 없음.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopBeforeAfterReportTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        listOf("coop_books", "coop_plan").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private class MemoryStore : CoopPlanStore {
        var raw: String? = null
        var extras = CoopPlanExtras()
        override fun load() = raw?.let(::coopPlanFromJson)
        override fun save(pick: CoopPick?, questions: List<String>) {
            raw = if (pick == null && questions.none { it.isNotBlank() }) null else coopPlanToJson(pick, questions)
        }
        override fun loadExtras() = extras
        override fun saveExtras(extras: CoopPlanExtras) { this.extras = extras }
    }

    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private suspend fun Director.tap(part: String): Boolean {
        if (await(4_000) { s.buttons.any { part in it.label } } == null) return false
        s.buttons.first { part in it.label }.onClick()
        return true
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** 이야기 칸 넷과 출처를 넣고 꽂는다 */
    private fun DemoState.shelveBook(pick: CoopPick, title: String, answers: Map<String, Pair<String, String>>): String {
        mode = StoryMode.COOP
        coopPick = pick
        answers["place"]?.let { (v, by) -> place = v; placeLabel = v; slots["place"] = "${v}에 갔어요"; slotBy["place"] = by }
        answers["problem"]?.let { (v, by) -> problem = v; slots["problem"] = v; slotBy["problem"] = by }
        answers["cause"]?.let { (v, by) -> cause = v; slots["cause"] = v; slotBy["cause"] = by }
        answers["solution"]?.let { (v, by) -> solution = v; slots["solution"] = v; slotBy["solution"] = by }
        this.title = title
        storyCaptions = (1..pageCount).map { "${it}쪽 문장" }
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(this))
        return CoopShelf.books(this).first().id
    }

    /** 가기 전 책을 꽂고 → 부모가 상자에서 꺼내 저장 → 다녀온 이야기를 시작해 꽂는다 */
    private suspend fun Director.pairOfBooks(before: Map<String, Pair<String, String>>, after: Map<String, Pair<String, String>>) {
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, LocalCoopBookStore(context))
        s.shelveBook(CoopPick("place", "동물원", "soon"), "지호의 두근두근 동물원 이야기", before)
        val box = CoopPlan.after(s).single()
        s.coopPick = CoopPick("place", "동물원", "done")
        CoopPlan.startAfter(s, box)
        CoopPlan.saved(s)
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertNotNull(await { s.scene == Scene.BESTIARY })
        assertTrue(tap("카드를 탭"))
        assertNotNull(await { s.scene == Scene.DIARY })
        listOf("place", "problem", "cause", "solution").forEach { s.slotBy.remove(it); s.slots.remove(it) }
        s.shelveBook(CoopPick("place", "동물원", "done"), "지호의 동물원 이야기", after)
    }

    @Test
    fun theTwoBooksStandSideBySideInTheChildsOwnWords() = run { d ->
        d.pairOfBooks(
            before = mapOf("place" to ("사자 우리" to "child"), "problem" to ("사자가 으르렁할 거야" to "child"),
                "cause" to ("배고파서" to "child"), "solution" to ("밥을 줘요" to "mascot")),
            after = mapOf("place" to ("기린 마당" to "child"), "problem" to ("기린이 내 손 핥았어" to "child"),
                "cause" to ("먹이 줘서" to "card"), "solution" to ("엄마가 사진 찍었어" to "child")),
        )
        val pair = d.s.coopBeforeAfter()!!
        assertEquals("동물원", pair.name)
        assertEquals(listOf("어디", "무슨 일", "왜", "그래서"), pair.rows.map { it.label })
        val trouble = pair.rows[1]
        assertEquals("“사자가 으르렁할 거야”", trouble.before)
        assertEquals("“기린이 내 손 핥았어”", trouble.after)
        assertEquals("(마스코트가 대신 정했어요)", pair.rows[3].before)
        assertEquals("먹이 줘서 (카드로 골랐어요)", pair.rows[2].after)
        assertEquals("가기 전엔 “사자가 으르렁할 거야”라고 상상했고, 다녀와서는 “기린이 내 손 핥았어”라고 말했어요.", pair.summary)
        val all = (pair.rows.flatMap { listOf(it.before, it.after) } + listOfNotNull(pair.summary)).joinToString(" ")
        listOf("맞았", "틀렸", "늘었", "줄었", "더 길", "점수", "잘했").forEach { assertTrue("$it: $all", it !in all) }
    }

    @Test
    fun noSummaryUnlessBothTroublesAreTheChildsWords() = run { d ->
        d.pairOfBooks(
            before = mapOf("place" to ("사자 우리" to "child"), "problem" to ("사자 보기" to "card")),
            after = mapOf("place" to ("기린 마당" to "child"), "problem" to ("기린이 내 손 핥았어" to "child")),
        )
        val pair = d.s.coopBeforeAfter()!!
        assertNull(pair.summary)
        assertEquals("답하지 않았어요", pair.rows[2].before)
    }

    /** 짝이 없는 책 · 곧 해요 책 리포트에는 칸이 없다 */
    @Test
    fun noPairNoCard() {
        val s = DemoState()
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, LocalCoopBookStore(context))
        s.shelveBook(CoopPick("place", "동물원", "soon"), "곧", mapOf("place" to ("사자 우리" to "child")))
        assertNull("곧 해요 책에 칸이 섰다", s.coopBeforeAfter())
        s.shelveBook(CoopPick("place", "학교", "done"), "다녀옴", mapOf("place" to ("운동장" to "child")))
        assertNull("짝 없는 다녀온 책에 칸이 섰다", s.coopBeforeAfter())
    }

    @Test
    fun thePairShowsOnTheShelfSpineAndGoesAwayWhenOneIsRemoved() = run { d ->
        d.pairOfBooks(mapOf("place" to ("사자 우리" to "child")), mapOf("place" to ("기린 마당" to "child")))
        val ids = d.s.shelf.mapNotNull { it.savedStoryId }.filter { it.startsWith("coop:") }
        assertEquals(2, ids.size)
        assertTrue(ids.all { CoopShelf.hasPair(d.s, it) })
        assertTrue(CoopShelf.delete(d.s, ids.last().removePrefix("coop:")))
        assertTrue(d.s.shelf.mapNotNull { it.savedStoryId }.none { CoopShelf.hasPair(d.s, it) })
    }
}
