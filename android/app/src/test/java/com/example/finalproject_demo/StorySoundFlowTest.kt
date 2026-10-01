package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorySoundFlowTest {
    private val originalRoot = ChildSound.root
    private val originalCapture = ChildSound.capture
    private val originalBase = Server.base
    private val originalModes = Server.liveModes
    private lateinit var folder: File

    @Before fun setup() {
        folder = Files.createTempDirectory("story_sound_flow").toFile()
        ChildSound.root = folder
        Server.base = null
        Server.liveModes = emptySet()
    }

    @After fun cleanup() {
        ChildSound.capture = originalCapture
        ChildSound.root = originalRoot
        Server.base = originalBase
        Server.liveModes = originalModes
        folder.deleteRecursively()
    }

    @Test fun rerecordingKeepsOnlyTheChosenClipWithoutCountingItAsSpeech() = runBlocking {
        var captures = 0
        ChildSound.capture = {
            captures++
            ShortArray(ChildSound.RATE / 2) { if (it % 2 == 0) 5000 else -5000 }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        try {
            val flow = scope.launch { d.recordStorySound() }
            withTimeout(4_000) {
                while (flow.isActive) {
                    val choices = (d.s.stage as? Stage.CardsRow)?.cards?.map { it.value }.orEmpty()
                    val action = when {
                        "sound:record" in choices -> "sound:record"
                        "sound:retry" in choices && captures == 1 -> "sound:retry"
                        "sound:ok" in choices -> "sound:ok"
                        else -> null
                    }
                    action?.let { d.send(Reply.Tapped(it, "선택")) }
                    delay(20)
                }
                flow.join()
            }
            assertEquals(2, captures)
            assertNotNull(d.s.storySoundClip)
            assertEquals(1, File(folder, "session").listFiles().orEmpty().size)
            assertEquals("child", d.s.slotBy["sound"])
            assertEquals(0, d.s.modeVoice)
            assertEquals(0, d.s.turn)
            assertTrue(d.s.quotes.isEmpty())
            assertTrue(d.s.storySoundAttempted)
        } finally { scope.cancel() }
    }

    @Test fun silenceCanBeSkippedWithoutInventingASoundOrBlockingTheBook() = runBlocking {
        var captures = 0
        ChildSound.capture = { captures++; null }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        try {
            val flow = scope.launch { d.recordStorySound() }
            withTimeout(4_000) {
                while (flow.isActive) {
                    val choices = (d.s.stage as? Stage.CardsRow)?.cards?.map { it.value }.orEmpty()
                    val action = if (captures == 0) "sound:record" else "sound:skip"
                    if (action in choices) d.send(Reply.Tapped(action, "선택"))
                    delay(20)
                }
                flow.join()
            }
            assertEquals(1, captures)
            assertNull(d.s.storySoundClip)
            assertNull(d.s.slots["sound"])
            assertTrue(d.s.storySoundAttempted)
            assertTrue("sound" in d.s.storyUnneededSlots)
            assertFalse(d.s.events.any { it.startsWith("make") })
        } finally { scope.cancel() }
    }

    @Test fun oneRetryTapStartsTheNextRecording() = runBlocking {
        var captures = 0
        ChildSound.capture = { captures++; ShortArray(ChildSound.RATE / 2) { 5000 } }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        suspend fun waitFor(value: String) = withTimeout(2_000) {
            while ((d.s.stage as? Stage.CardsRow)?.cards?.none { it.value == value } != false) delay(10)
        }
        try {
            scope.launch { d.recordStorySound() }
            waitFor("sound:record")
            d.send(Reply.Tapped("sound:record", "소리 내기"))
            waitFor("sound:retry")
            d.send(Reply.Tapped("sound:retry", "다시 녹음"))
            withTimeoutOrNull(700) { while (captures < 2) delay(10) }
            assertEquals("Retry must start capture without another record press", 2, captures)
        } finally { scope.cancel() }
    }
}
