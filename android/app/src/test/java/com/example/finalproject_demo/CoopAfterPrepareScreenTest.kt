package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopPlan
import com.example.finalproject_demo.demo.CoopPlanExtras
import com.example.finalproject_demo.demo.CoopPlanStore
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.CoopShelved
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPlanFromJson
import com.example.finalproject_demo.demo.coopReady
import com.example.finalproject_demo.demo.coopPlanToJson
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.ui.ParentView
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.ui.CoopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #255 (10-07 device) — [있었던 일로 준비하기] seemed to do nothing: the draft was filled, but the template cards kept the
 * kind they read on first draw (none), so no kind · item · reason showed and the heading stayed the usual one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class CoopAfterPrepareScreenTest {
    @get:Rule val compose = createComposeRule()

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

    @Test
    fun preparingFromTheBoxShowsTheDraftAndSavingPairsTheBooks() {
        val d = Director(CoroutineScope(SupervisorJob()))
        val s = d.s
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf("coop_books", "coop_plan").forEach { context.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit() }
        CoopPlan.attach(s, MemoryStore())
        CoopShelf.attach(s, com.example.finalproject_demo.demo.LocalCoopBookStore(context))
        // a 「곧 가요」 동물원 book on the shelf puts its card in the box
        s.mode = StoryMode.COOP
        s.coopPick = CoopPick("place", "동물원", "soon")
        s.place = "사자 우리"; s.placeLabel = "사자 우리"; s.slots["place"] = "사자 우리에 갈 거예요"
        s.title = "지호의 두근두근 동물원 이야기"
        s.storyCaptions = (1..s.pageCount).map { "${it}쪽 문장" }
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(s))
        val before = CoopShelf.books(s).first().id
        // the box card that book leaves (CoopAfterBoxTest covers how it gets there); no plan saved yet
        CoopPlan.rememberAfter(s, com.example.finalproject_demo.demo.CoopAfter("place", "동물원", before, s.title!!, "2026-10-07"))
        s.coopPick = null
        assertTrue("no box card", CoopPlan.after(s).isNotEmpty())

        compose.setContent { ParentView(d, "coop") }
        compose.onNodeWithText("있었던 일로 준비하기").performScrollTo().performClick()
        compose.waitForIdle()

        // the heading says what is being prepared
        compose.onNodeWithText("‘동물원’ 다녀온 이야기를 준비하고 있어요").assertExists()
        // the draft shows: the 다녀왔어요 preview of Otto's questions only appears once kind · item · reason are on screen
        val first = coopKind("place")!!.questions("동물원", CoopReason.DONE)[0]
        compose.onNodeWithText("“$first”").performScrollTo().assertExists()
        assertEquals("nothing is saved before [저장하기]", null, s.coopPick)

        compose.onNodeWithText("저장하기").performClick()
        compose.waitForIdle()
        assertEquals(CoopPick("place", "동물원", "done"), s.coopPick)
        assertTrue(s.coopReady)
        assertEquals(before, CoopPlan.beforeBookId(s))
    }
}
