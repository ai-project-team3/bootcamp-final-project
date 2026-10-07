package com.example.finalproject_demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.ui.missions.DONE_SCENE_MS
import com.example.finalproject_demo.ui.missions.HINT_AFTER_MS
import com.example.finalproject_demo.ui.missions.HINT_LINE
import com.example.finalproject_demo.ui.missions.HINT_SHOW_MS
import com.example.finalproject_demo.ui.missions.MissionDoneSignal
import com.example.finalproject_demo.ui.missions.ghostAlong
import com.example.finalproject_demo.ui.missions.ghostAlpha
import com.example.finalproject_demo.ui.missions.rememberMissionHint
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #260 미션 공통 틀 — 15초에 흐릿한 예시를 한 번(오또가 대신 하지 않는다) · 완료 신호는 바로 · 완료 반짝은 공통 틀에서 한 번만.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionShellTest {
    @get:Rule val compose = createComposeRule()
    private val frozen = motionFrozen

    @After fun restore() { motionFrozen = frozen }

    /** 감독이 답을 기다리기 시작한 상태 — [Director.awaitReply] 는 기다리기 전에 앞 입력을 비우므로 먼저 걸어 둔다 */
    private fun waiting(d: Director): Deferred<Reply> {
        val started = CompletableDeferred<Unit>()
        return CoroutineScope(Dispatchers.Default).async { started.complete(Unit); d.awaitReply() }
            .also { runBlocking { started.await(); delay(50) } }
    }

    /** 흐릿한 예시는 길 처음에 스며 나와 끝에 머물다 사라진다 · 진짜보다 흐리다 */
    @Test
    fun theGhostWalksThePathAndFades() {
        val path = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f))
        assertEquals(Offset(0f, 0f), ghostAlong(path, 0.1f))
        assertEquals(Offset(100f, 100f), ghostAlong(path, 0.9f))
        assertEquals(0f, ghostAlpha(0f), 1e-4f)
        assertEquals(0f, ghostAlpha(1f), 1e-4f)
        assertTrue((0..100).all { ghostAlpha(it / 100f) <= 0.5f })
    }

    /** 15초에 한 번 — 오또가 말하고(물음표 없이) 예시를 보인 뒤 사라진다. 미션 값은 그대로 · `mission_hint` 기록 */
    @Test
    fun aGhostHintOnceAfterFifteenSecondsThatChangesNothing() {
        motionFrozen = false
        val d = Director(CoroutineScope(SupervisorJob()))
        val progress by mutableFloatStateOf(0f)
        var hint: Float? = null
        val seen = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            hint = rememberMissionHint(d, progress, done = false, mission = "A1")
            hint?.let { seen += it }
        }
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS - 1_000)
        assertEquals("15초 전에는 없다", null, hint)
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(HINT_LINE, d.s.line)
        assertFalse("물음표가 있으면 대화록에 질문으로 남는다", '?' in HINT_LINE)
        assertEquals(1, d.s.events.count { it.startsWith("mission_hint") && "mission=A1" in it })
        compose.mainClock.advanceTimeBy(HINT_SHOW_MS + 500)
        assertEquals("다 보이면 사라진다", null, hint)
        assertTrue("예시가 처음부터 끝까지 흘렀다", seen.isNotEmpty() && seen.max() > 0.8f)
        assertEquals("오또는 미션을 대신 하지 않는다", 0f, progress)
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS * 2)
        assertEquals("한 번만", 1, d.s.events.count { it.startsWith("mission_hint") })
    }

    @Test
    fun noHintOnAFinishedPageOrWhileTheChildIsMoving() {
        motionFrozen = false
        val d = Director(CoroutineScope(SupervisorJob()))
        var progress by mutableFloatStateOf(0f)
        var done by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { rememberMissionHint(d, progress, done, "A5") }
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS + 1_000)
        assertEquals("끝난 쪽은 보이지 않는다", 0, d.s.events.count { it.startsWith("mission_hint") })
        done = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS - 3_000)
        progress = 0.2f                                     // 아이가 움직이면 다시 센다
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS - 3_000)
        assertEquals(0, d.s.events.count { it.startsWith("mission_hint") })
        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(1, d.s.events.count { it.startsWith("mission_hint") })
    }

    /**
     * 다 하면 신호는 **바로** — 1.5초 기다린 뒤 보내면 그 사이 ▶ 로 넘긴 아이의 완료가 사라졌다(#293 리뷰 ②).
     * 미션 화면이 다음 프레임에 사라져도 감독은 완료를 받는다
     */
    @Test
    fun theDoneSignalGoesAtOnceEvenIfThePageTurnsRightAway() {
        motionFrozen = false
        val d = Director(CoroutineScope(SupervisorJob()))
        var onPage by mutableStateOf(true)
        val got = waiting(d)                                // 감독은 책 쪽에서 이미 기다리고 있다
        compose.mainClock.autoAdvance = false
        compose.setContent { if (onPage) MissionDoneSignal(d, finished = true, alreadyDone = false, label = "미션2") }
        compose.mainClock.advanceTimeByFrame()
        onPage = false                                      // 아이가 바로 ▶
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(DONE_SCENE_MS * 2)
        assertEquals(Reply.Tapped("mission", "미션2"), runBlocking { withTimeoutOrNull(2_000) { got.await() } })
    }

    /** 다시 펼친 책(이미 끝난 쪽)은 보내지 않는다 */
    @Test
    fun aFinishedPageSendsNothing() {
        val d = Director(CoroutineScope(SupervisorJob()))
        val got = waiting(d)
        compose.setContent { MissionDoneSignal(d, finished = true, alreadyDone = true, label = "미션1") }
        compose.waitForIdle()
        assertEquals(null, runBlocking { withTimeoutOrNull(300) { got.await() } })
        got.cancel()
    }

    /** 완료 반짝은 MissionDoneSignal 한 곳에서만 — 하나씩 끝날 때는 진행 소리로 (#260 §6-2) */
    @Test
    fun theDoneSparkleLivesInTheShellOnly() {
        val dir = listOf("src/main/java/com/example/finalproject_demo/ui/missions", "app/src/main/java/com/example/finalproject_demo/ui/missions")
            .map(::File).first { it.isDirectory }
        // C1 · A6 은 #282(불기 판정)와 같은 줄이라 그 PR 뒤에 · E1 은 건네주기 장면 PR(#260 ④)에서
        val later = setOf("C1Blow.kt", "A6Rub.kt", "E1Give.kt")
        val offenders = dir.listFiles()!!.filter { it.name != "MissionShell.kt" && it.name !in later }
            .filter { it.readText().contains("Sound.SPARKLE") }.map { it.name }
        assertEquals("완료 반짝을 따로 내는 미션", emptyList<String>(), offenders)
    }
}
