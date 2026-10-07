package com.example.finalproject_demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.missions.HELP_AFTER_MS
import com.example.finalproject_demo.ui.missions.HELP_LINE
import com.example.finalproject_demo.ui.missions.MissionHelp
import com.example.finalproject_demo.ui.missions.fillToHalf
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #260 미션 공통 틀 — 15초에 오또가 반쯤 도와주되 끝내 주지는 않는다 · 완료 반짝은 공통 틀에서 한 번만.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionShellTest {
    @get:Rule val compose = createComposeRule()
    private val frozen = motionFrozen

    @After fun restore() { motionFrozen = frozen }

    @Test
    fun fillToHalfStopsAtHalfAndKeepsWhatIsThere() {
        val three = mutableListOf(0f, 0f, 0f)
        fillToHalf(three, 3f)
        assertEquals(listOf(3f, 1.5f, 0f), three)
        val started = mutableListOf(0f, 2f, 0f)
        fillToHalf(started, 3f)
        assertEquals("앞에서부터 반(4.5)까지만", 4.5f, started.sum(), 1e-4f)
        assertTrue("아무것도 다 끝나지는 않는다 — 처음 하나만 찼다", started.count { it >= 3f } <= 1)
        val past = mutableListOf(3f, 3f, 0f)
        fillToHalf(past, 3f)
        assertEquals("이미 반을 넘었으면 그대로", listOf(3f, 3f, 0f), past)
    }

    @Test
    fun ottoHelpsOnceAfterFifteenSecondsAndNeverFinishes() {
        motionFrozen = false
        val d = Director(CoroutineScope(SupervisorJob()))
        var progress by mutableFloatStateOf(0f)
        var calls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MissionHelp(d, progress, done = progress >= 1f) { calls++; progress = 0.5f }
        }
        compose.mainClock.advanceTimeBy(HELP_AFTER_MS - 1_000)
        assertEquals("15초 전에는 돕지 않는다", 0, calls)
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1, calls)
        assertEquals(HELP_LINE, d.s.line)
        assertEquals("반까지만 — 끝은 아이 몫", 0.5f, progress)
        compose.mainClock.advanceTimeBy(HELP_AFTER_MS * 2)
        assertEquals("한 번만 돕는다", 1, calls)
    }

    @Test
    fun noHelpOnAFinishedPageOrWhileTheChildIsMoving() {
        motionFrozen = false
        val d = Director(CoroutineScope(SupervisorJob()))
        var progress by mutableFloatStateOf(0f)
        var done by mutableStateOf(true)
        var calls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MissionHelp(d, progress, done) { calls++ } }
        compose.mainClock.advanceTimeBy(HELP_AFTER_MS + 1_000)
        assertEquals("끝난 쪽은 돕지 않는다", 0, calls)
        done = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(HELP_AFTER_MS - 3_000)
        progress = 0.2f                                     // 아이가 움직이면 다시 센다
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(HELP_AFTER_MS - 3_000)
        assertEquals(0, calls)
        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(1, calls)
        assertNotEquals(null, d.s.line)
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
