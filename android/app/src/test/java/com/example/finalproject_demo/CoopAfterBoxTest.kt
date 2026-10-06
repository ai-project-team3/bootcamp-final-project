package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.COOP_AFTER_MAX
import com.example.finalproject_demo.demo.CoopAfter
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopPlan
import com.example.finalproject_demo.demo.CoopPlanExtras
import com.example.finalproject_demo.demo.CoopPlanStore
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.CoopShelved
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalCoopBookStore
import com.example.finalproject_demo.demo.LocalCoopPlanStore
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopAfterAsk
import com.example.finalproject_demo.demo.coopAfterIntroLine
import com.example.finalproject_demo.demo.coopBeforeBookId
import com.example.finalproject_demo.demo.coopStoryPick
import com.example.finalproject_demo.demo.coopPlanFromJson
import com.example.finalproject_demo.demo.coopPlanToJson
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.questionHint
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
 * 「가기 전 · 다녀온 뒤」 짝책 (협업모드_확장_설계 §2 · A-2).
 * 곧 해요 책이 꽂히면 상자에 남고, 부모가 상자에서 꺼내 「다녀왔어요」로 저장하면 그 이야기의 책이 가기 전 책과 짝이 된다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopAfterBoxTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        listOf("coop_books", "coop_plan").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    /** 폰 저장소 대신 — 「앱을 껐다 켠다」는 같은 저장소에 새 상태를 붙이는 것 */
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

    private fun after(name: String, id: String = "b-$name") = CoopAfter("place", name, id, "지호의 두근두근 $name 이야기", "2026-10-06")

    /** 지금 상태로 같이 만들기 책 한 권을 꽂는다 — CoopBookLocalSaveTest 와 같은 재료 */
    private fun DemoState.shelveBook(pick: CoopPick, title: String): String {
        mode = StoryMode.COOP
        coopPick = pick
        place = "사자 우리"; placeLabel = "사자 우리"; slots["place"] = "사자 우리에 갔어요"
        this.title = title
        storyCaptions = (1..pageCount).map { "${it}쪽 문장" }
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(this))
        return CoopShelf.books(this).first().id
    }

    // ── 상자 ──────────────────────────────────────────────

    @Test
    fun theBoxSurvivesARestartNewestFirstAndKeepsThree() {
        val store = MemoryStore()
        val s = DemoState().also { CoopPlan.attach(it, store) }
        listOf("동물원", "아쿠아리움", "놀이공원", "학교").forEach { CoopPlan.rememberAfter(s, after(it)) }
        val again = DemoState().also { CoopPlan.attach(it, store) }
        assertEquals(COOP_AFTER_MAX, CoopPlan.after(again).size)
        assertEquals(listOf("학교", "놀이공원", "아쿠아리움"), CoopPlan.after(again).map { it.name })
    }

    @Test
    fun theSameStoryAgainReplacesTheOlderOne() {
        val s = DemoState().also { CoopPlan.attach(it, MemoryStore()) }
        CoopPlan.rememberAfter(s, after("동물원", "old"))
        CoopPlan.rememberAfter(s, after("아쿠아리움"))
        CoopPlan.rememberAfter(s, after("동물원", "new"))
        assertEquals(listOf("new", "b-아쿠아리움"), CoopPlan.after(s).map { it.beforeBookId })
    }

    @Test
    fun theRealPhoneStoreKeepsTheBoxToo() {
        val s = DemoState().also { CoopPlan.attach(it, LocalCoopPlanStore(context)) }
        CoopPlan.rememberAfter(s, after("동물원"))
        val again = DemoState().also { CoopPlan.attach(it, LocalCoopPlanStore(context)) }
        assertEquals(listOf(after("동물원")), CoopPlan.after(again))
        CoopPlan.dismissAfter(again, after("동물원"))
        assertTrue(CoopPlan.after(DemoState().also { CoopPlan.attach(it, LocalCoopPlanStore(context)) }).isEmpty())
    }

    /** 상자에서 꺼내 저장하면 가기 전 책을 기억하고, 계획이 비면(이야기 끝 · 부모 [삭제하기]) 같이 잊는다 */
    @Test
    fun startingFromTheBoxRemembersTheBeforeBookUntilThePlanIsCleared() {
        val store = MemoryStore()
        val s = DemoState().also { CoopPlan.attach(it, store) }
        CoopPlan.rememberAfter(s, after("동물원"))
        s.coopPick = CoopPick("place", "동물원", "done")
        CoopPlan.startAfter(s, after("동물원"))
        CoopPlan.saved(s)
        assertEquals("b-동물원", CoopPlan.beforeBookId(DemoState().also { CoopPlan.attach(it, store) }))
        assertTrue("꺼낸 칸이 상자에 남았다", CoopPlan.after(s).isEmpty())
        s.clearParentQuestions()
        assertNull(CoopPlan.beforeBookId(s))
        assertNull(store.extras.before)
    }

    // ── 꽂을 때 ───────────────────────────────────────────

    @Test
    fun aSoonBookGoesIntoTheBoxWhenItIsShelved() {
        val s = DemoState()
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, LocalCoopBookStore(context))
        val id = s.shelveBook(CoopPick("job", "소방관", "soon"), "지호의 두근두근 소방관 이야기")
        val box = CoopPlan.after(s).single()
        assertEquals(CoopAfter("job", "소방관", id, "지호의 두근두근 소방관 이야기", box.madeAt), box)
    }

    @Test
    fun doneAndDreamBooksDoNotGoIntoTheBox() {
        listOf(CoopPick("job", "소방관", "done"), CoopPick("job", "소방관", null)).forEach { pick ->
            clear()
            val s = DemoState()
            CoopPlan.attach(s, MemoryStore())
            CoopShelf.attach(s, LocalCoopBookStore(context))
            s.shelveBook(pick, "책")
            assertTrue("${pick.reason} 책이 상자에 들어갔다", CoopPlan.after(s).isEmpty())
        }
    }

    // ── 다녀온 뒤 이야기 — 시작부터 꽂기까지 ──────────────────────

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

    @Test
    fun anAfterStoryIsPairedWithItsBeforeBook() = run { d ->
        val s = d.s
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, LocalCoopBookStore(context))
        val beforeId = s.shelveBook(CoopPick("place", "동물원", "soon"), "지호의 두근두근 동물원 이야기")
        val box = CoopPlan.after(s).single()
        // 부모 — 상자에서 꺼내 「다녀왔어요」로 저장
        s.coopPick = CoopPick("place", "동물원", "done")
        CoopPlan.startAfter(s, box)
        CoopPlan.saved(s)

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertNotNull(await { s.scene == Scene.BESTIARY })
        assertTrue(d.tap("카드를 탭"))
        assertNotNull(await { s.scene == Scene.DIARY })
        // 장면 값은 sceneDiary 가 돌기 전에 바뀐다(Director.go 가 장면 실행을 따로 띄운다) — 오또 소개(coopIntro)가
        // 이 이야기의 기록을 만들 때까지 기다린다. 소개는 첫 대사 전에 고른 이야기와 가기 전 책을 한꺼번에 적는다
        assertNotNull("오또 소개가 돌지 않았다", await { s.coopStoryPick != null })
        assertEquals("시작할 때 가기 전 책을 붙잡아야 한다", beforeId, s.coopBeforeBookId)

        val afterId = s.shelveBook(CoopPick("place", "동물원", "done"), "지호의 동물원 이야기")
        assertEquals(afterId, CoopShelf.pairOf(s, beforeId)?.id)
        assertEquals(beforeId, CoopShelf.pairOf(s, afterId)?.id)
        assertTrue("다녀온 책이 다시 상자에 들어갔다", CoopPlan.after(s).isEmpty())
    }

    /** 가기 전 책을 부모가 지웠으면 다녀온 이야기는 보통 이야기 — 짝 없음 */
    @Test
    fun aRemovedBeforeBookMeansNoPair() = run { d ->
        val s = d.s
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, LocalCoopBookStore(context))
        val beforeId = s.shelveBook(CoopPick("place", "동물원", "soon"), "가기 전")
        val box = CoopPlan.after(s).single()
        s.coopPick = CoopPick("place", "동물원", "done")
        CoopPlan.startAfter(s, box)
        CoopPlan.saved(s)
        assertTrue(CoopShelf.delete(s, beforeId))

        d.go(Scene.ADULT)
        assertTrue(d.tap("같이 만들기"))
        assertNotNull(await { s.scene == Scene.BESTIARY })
        assertTrue(d.tap("카드를 탭"))
        assertNotNull(await { s.scene == Scene.DIARY })
        // 장면 값은 sceneDiary 가 돌기 전에 바뀐다(Director.go 가 장면 실행을 따로 띄운다) — 오또 소개(coopIntro)가
        // 이 이야기의 기록을 만들 때까지 기다린다. 소개는 첫 대사 전에 고른 이야기와 가기 전 책을 한꺼번에 적는다
        assertNotNull("오또 소개가 돌지 않았다", await { s.coopStoryPick != null })
        assertNull(s.coopBeforeBookId)
    }

    // ── 말 ───────────────────────────────────────────────

    @Test
    fun theWordsFitEachKind() {
        assertEquals("동물원에 가기 전에 지은 이야기 기억나? 이번엔 진짜 있었던 일을 들려줘!", coopAfterIntroLine(CoopPick("place", "동물원", "done")))
        assertTrue(coopAfterIntroLine(CoopPick("job", "소방관", "done")).startsWith("소방관 체험하기 전에"))
        assertTrue(coopAfterIntroLine(CoopPick("sport", "수영", "done")).startsWith("수영을 해 보기 전에"))
        assertEquals("동물원, 다녀왔나요?", coopAfterAsk(after("동물원")))
        assertEquals("소방관, 체험했나요?", coopAfterAsk(CoopAfter("job", "소방관", "x", "t", "")))
        assertEquals("축구, 해 봤나요?", coopAfterAsk(CoopAfter("sport", "축구", "x", "t", "")))
        assertNull(questionHint(com.example.finalproject_demo.demo.COOP_AFTER_SUGGESTION))
    }
}
