package com.example.finalproject_demo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.CoopBookStore
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.CoopShelved
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.SavedCoopBook
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.coopGeneratedBackground
import com.example.finalproject_demo.demo.coopMissionProp
import com.example.finalproject_demo.demo.coopPageMission
import com.example.finalproject_demo.demo.coopSecondCharacter
import com.example.finalproject_demo.demo.coopSecondOnPage
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Co-op book check — the app layer, server off (design review 10-14: scripts from `eval/fixtures_coop_check.jsonl`).
 *
 * Each script is played through the real [Director] flow: the answer for the step Otto is asking (by step id), the
 * parent's answer for a parent question, 「몰라」 for anything the script leaves out. The finished book is written to
 * `build/review/coopcheck/books.json` page by page (kind · caption · mission · who stands · background source) and
 * every page is captured. Template captions only — the server's sentences are the server run's job (bench_coop_book.py).
 *
 * Opt-in (it plays 18 sessions): `COOP_CHECK=1 ./gradlew :app:testDebugUnitTest --rerun --tests '*CoopBookCheckTest*'`.
 * With `COOP_CHECK_SERVER=<backend url>` the same scripts run with co-op live (judge · line · /story · /image · /character)
 * and land in `build/review/coopcheck_server`.
 * It fails only on regressions of the app layer: a script that never reaches the book, a blank page, a mission twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class CoopBookCheckTest {
    @get:Rule val compose = createComposeRule()

    /** The server run (design review 10-14 step 4): `COOP_CHECK_SERVER=http://host:port` — the same scripts with co-op live */
    private val server: String? = System.getenv("COOP_CHECK_SERVER")?.takeIf { it.isNotBlank() }
    private val out = File(if (server != null) "build/review/coopcheck_server" else "build/review/coopcheck")
    /** a live turn waits on the judge and the line (~6 s each) and the book on /story — the template run is quick */
    private val flowMs = if (server != null) 600_000L else 90_000L
    private val bookMs = if (server != null) 180_000L else 30_000L

    private class Memory : CoopBookStore {
        val books = mutableListOf<SavedCoopBook>()
        override fun load() = books.toList()
        override fun save(book: SavedCoopBook) { books.add(0, book) }
    }

    private class Script(j: JSONObject) {
        val id: String = j.getString("id")
        val note: String = j.getString("note")
        val pick = j.getJSONObject("pick").let { CoopPick(it.getString("kind"), it.getString("name"), it.getString("reason")) }
        val parentQuestions = j.getJSONArray("parent_questions").strings()
        val parentAnswers = j.getJSONObject("parent_answers").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
        val answers: Map<String, ArrayDeque<String>> = j.getJSONObject("answers").let { o ->
            o.keys().asSequence().associateWith { ArrayDeque(o.getJSONArray(it).strings()) }
        }
    }

    private suspend fun await(ms: Long, cond: () -> Boolean): Boolean = withTimeoutOrNull(ms) { while (!cond()) delay(5); true } ?: false

    private suspend fun Director.tap(part: String): Boolean {
        if (!await(4_000) { s.buttons.any { part in it.label } }) return false
        s.buttons.first { part in it.label }.onClick()
        return true
    }

    /** The step Otto is asking — the director keeps it private, the check only reads it */
    private fun Director.askingId(): String? = Director::class.java.getDeclaredField("currentQ")
        .apply { isAccessible = true }.get(this).let { (it as? Question)?.id }

    /** Plays one script to the book; the log of what was answered where */
    private suspend fun Director.play(sc: Script): List<String> {
        val said = mutableListOf<String>()
        go(Scene.ADULT)
        assertTrue("${sc.id}: no 같이 만들기", tap("같이 만들기"))
        assertTrue(await(5_000) { s.scene == Scene.BESTIARY })
        s.parentQuestions += sc.parentQuestions
        s.coopPick = sc.pick
        assertTrue("${sc.id}: no card", tap("카드를 탭"))
        assertTrue(await(5_000) { s.scene == Scene.DIARY })
        var answered = -1
        val end = System.currentTimeMillis() + flowMs
        while (s.scene == Scene.DIARY && System.currentTimeMillis() < end) {
            val skipDrawing = s.buttons.firstOrNull { "안 그릴래" in it.label }
            if (skipDrawing != null) { skipDrawing.onClick(); delay(50); continue }
            if (!s.micEnabled || s.lineId == answered || s.line.isBlank()) { delay(10); continue }
            val line = s.line
            val key = askingId()?.removePrefix("diary_")
            val text = sc.parentAnswers[line] ?: sc.answers[key]?.removeFirstOrNull() ?: "몰라"
            said += "[$key] 「$line」 → 「$text」"
            answered = s.lineId
            // a send can land before the mic is really open — send again until the line moves on
            withTimeoutOrNull(if (server != null) 60_000L else 10_000L) { while (s.lineId == answered && s.scene == Scene.DIARY) { send(Reply.Spoke(text)); delay(80) } }
        }
        if (await(4_000) { s.buttons.any { "안 그릴래" in it.label || "오늘은 여기까지" in it.label } }) {
            // no seed from the child at all — finishDiary offers 끝내기 · 동화 모드 instead of a book (일기 §7-4, undecided)
            if (s.buttons.any { "오늘은 여기까지" in it.label }) return said + NO_SEED
            tap("안 그릴래")
        }
        assertTrue("${sc.id}: never reached the book · ${s.scene} · ${s.line}", await(bookMs) { s.scene == Scene.BOOK })
        return said
    }

    private val friendKinds = setOf(PageKind.SHAKE, PageKind.MEET, PageKind.TALK, PageKind.JOURNEY, PageKind.FAIL, PageKind.TOGETHER)

    /** Where the book's background came from (CoopScenes.kt coopBackdrop · CoopServerLine.kt drawCoopBackground) */
    private fun DemoState.backgroundSource(): String = when {
        coopGeneratedBackground != null && sceneKit != null -> "kit:$sceneKit"
        coopGeneratedBackground != null -> "server"
        bgName == "bg_today" -> "bg_today"
        else -> "preset:$bgName"
    }

    private fun DemoState.book(sc: Script, said: List<String>): JSONObject {
        val pages = JSONArray()
        val problems = mutableListOf<String>()
        val missions = mutableListOf<String>()
        for (i in 1..pageCount) {
            val kind = pageKind(i)
            val caption = bookCaption(i)
            val mission = coopPageMission(kind)
            mission?.let { missions += it }
            val second = coopSecondOnPage(kind, caption)?.let { coopSecondCharacter() ?: "?" }
            // the friend spot as Book.kt draws it for co-op (isDiary): DEPART only with someone named, else by page kind
            val companion = companionKind.isNotBlank() && "혼자" !in companionKind &&
                (kind in friendKinds || kind == PageKind.DEPART)
            if (caption.isBlank()) problems += "p$i blank"
            pages.put(JSONObject().put("page", i).put("kind", kind.name).put("caption", caption)
                .put("mission", mission ?: JSONObject.NULL).put("prop", coopMissionProp(kind) ?: JSONObject.NULL)
                .put("second", second ?: JSONObject.NULL).put("companion", if (companion) companionKind else JSONObject.NULL))
        }
        missions.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems += "mission $it twice" }
        return JSONObject().put("id", sc.id).put("note", sc.note)
            .put("pick", "${sc.pick.kind} · ${sc.pick.name} · ${sc.pick.reason}").put("server", server != null)
            .put("title", title ?: JSONObject.NULL).put("end", endReason ?: JSONObject.NULL)
            .put("background", backgroundSource()).put("bgName", bgName)
            .put("slots", JSONObject(slots.toMap())).put("by", JSONObject(slotBy.toMap()))
            .put("second", coopSecondCharacter() ?: JSONObject.NULL).put("companion", companionKind)
            .put("feelings", JSONArray(feelings.toList())).put("mascotPicks", mascotPicks)
            .put("said", JSONArray(said)).put("pages", pages).put("problems", JSONArray(problems))
    }

    @Test
    fun coopBooksFromTheFixedScripts() {
        assumeTrue("opt-in: COOP_CHECK=1", System.getenv("COOP_CHECK") == "1")
        val baseBefore = Server.base
        val modesBefore = Server.liveModes
        if (server != null) { Server.base = server; Server.liveModes = setOf(com.example.finalproject_demo.demo.StoryMode.COOP) }
        else assumeTrue("server must be off for the template run", !Server.liveFor(com.example.finalproject_demo.demo.StoryMode.COOP))
        val fixtures = File("../../eval/fixtures_coop_check.jsonl")
        val only = System.getenv("COOP_CHECK_ONLY")?.split(',')?.toSet()
        val scripts = fixtures.readLines().filter { it.isNotBlank() }.map { Script(JSONObject(it)) }
            .filter { only == null || it.id in only }
        out.mkdirs()

        val shown = mutableStateOf<Stage.SavedStory?>(null)
        val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val viewer = Director(viewScope)
        val frozenBefore = motionFrozen
        motionFrozen = true
        compose.setContent { shown.value?.let { SavedStoryView(viewer, it) } }

        val books = JSONArray()
        val failed = mutableListOf<String>()
        try {
            for (sc in scripts) {
                var made: Pair<DemoState, List<String>>? = null
                runCatching {
                    runBlocking {
                        val sup = SupervisorJob()
                        val d = Director(CoroutineScope(coroutineContext + sup))
                        d.s.speed = 0.01
                        try { made = d.s to d.play(sc) } finally { sup.cancel() }
                    }
                }.onFailure { failed += "${sc.id}: ${it.message}"; books.put(JSONObject().put("id", sc.id).put("error", it.message)) }
                val (s, said) = made ?: continue
                if (said.lastOrNull() == NO_SEED) {
                    books.put(JSONObject().put("id", sc.id).put("note", sc.note).put("end", "no_seed_fork")
                        .put("line", s.line).put("said", JSONArray(said.dropLast(1))))
                    continue
                }
                val book = s.book(sc, said)
                books.put(book)
                if (book.getJSONArray("problems").length() > 0) failed += "${sc.id}: ${book.getJSONArray("problems")}"

                val store = Memory()
                CoopShelf.attach(s, store)
                if (CoopShelf.shelve(s) != CoopShelved.SAVED) { failed += "${sc.id}: not shelved"; continue }
                val saved = store.books.first().book
                for (p in 1..saved.pages.size) {
                    shown.value = Stage.SavedStory(saved, p)
                    compose.waitForIdle()
                    compose.onRoot().captureRoboImage("${out.path}/${sc.id}_p$p.png",
                        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
                }
            }
        } finally {
            motionFrozen = frozenBefore
            Server.base = baseBefore
            Server.liveModes = modesBefore
            viewScope.cancel()
            File(out, "books.json").writeText(books.toString(1), Charsets.UTF_8)
        }
        assertTrue("app-layer regressions:\n" + failed.joinToString("\n"), failed.isEmpty())
    }
}

private const val NO_SEED = "<no seed: 끝내기 · 동화 모드 fork>"

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
