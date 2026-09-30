package com.example.finalproject_demo

import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 아이가 만든 소리 — 폰에서 녹음 · 재생 · 보관 (이슈 #42 · 차별점 1) */
class ChildSoundTest {
    private lateinit var dir: File
    private val rate = ChildSound.RATE
    private val realMic = ChildSound.capture

    @Before fun setUp() {
        dir = Files.createTempDirectory("child_sounds").toFile()
        ChildSound.root = dir
    }

    @After fun tearDown() { ChildSound.capture = realMic; dir.deleteRecursively() }

    /** [quietMs] of hiss, [loudMs] of a sound, [quietMs] of hiss again */
    private fun clip(quietMs: Int, loudMs: Int, amp: Int = 6000): ShortArray {
        val q = rate * quietMs / 1000; val l = rate * loudMs / 1000
        return ShortArray(q + l + q) { i ->
            if (i in q until q + l) (if (i % 20 < 10) amp else -amp).toShort() else ((i % 7) - 3).toShort()
        }
    }

    @Test fun quietBeforeAndAfterIsCutOff() {
        val pcm = clip(quietMs = 1500, loudMs = 800)
        val out = ChildSound.trim(pcm)!!
        val ms = out.size * 1000 / rate
        assertTrue("잘라낸 뒤 ${ms}ms — 소리 800ms + 앞뒤 여유", ms in 800..1200)
    }

    @Test fun nothingLoudIsNoSound() {
        assertNull(ChildSound.trim(clip(quietMs = 2000, loudMs = 0)))
    }

    @Test fun itStopsASecondAfterTheSoundEnds() {
        assertFalse("소리 전에는 조용해도 기다린다", ChildSound.soundEnded(loudSeen = false, quietFrames = 200))
        assertFalse(ChildSound.soundEnded(loudSeen = true, quietFrames = 49))
        assertTrue(ChildSound.soundEnded(loudSeen = true, quietFrames = 50))     // 50 × 20 ms = 1 s
    }

    @Test fun recordSavesATrimmedWavInTheSession() = runBlocking {
        ChildSound.capture = { clip(quietMs = 1000, loudMs = 500) }
        val c = ChildSound.record()!!
        assertTrue(c.file.exists() && c.file.parentFile!!.name == "session")
        assertEquals("RIFF", String(c.file.readBytes().copyOfRange(0, 4)))
    }

    @Test fun silenceRecordsNothing() = runBlocking {
        ChildSound.capture = { clip(quietMs = 2000, loudMs = 0) }
        assertNull(ChildSound.record())
        assertFalse(File(dir, "session").listFiles().orEmpty().any())
    }

    @Test fun keptClipsSurviveTheSessionAndOpenFromTheBook() = runBlocking {
        ChildSound.capture = { clip(quietMs = 500, loudMs = 500) }
        val kept = ChildSound.keep(ChildSound.record()!!, "book-1")!!
        val dropped = ChildSound.record()!!
        ChildSound.discardSession()
        assertTrue("책에 넣은 소리는 남는다", kept.file.exists())
        assertFalse("책에 안 넣은 소리는 지운다", dropped.file.exists())
        assertNotNull(ChildSound.find("book-1", kept.id))
        ChildSound.deleteBook("book-1")
        assertNull("책을 지우면 소리도 지운다", ChildSound.find("book-1", kept.id))
    }

    @Test fun aCancelledClipCannotBeKept() = runBlocking {
        ChildSound.capture = { clip(quietMs = 500, loudMs = 500) }
        val c = ChildSound.record()!!
        ChildSound.cancel(c)
        assertNull(ChildSound.keep(c, "book-1"))
    }

    /** 차별점 1 — 이 파일은 서버를 부르지 않는다. 누가 나중에 붙이면 여기서 깨진다 */
    @Test fun theChildsSoundHasNoWayToTheServer() {
        val src = File("src/main/java/com/example/finalproject_demo/sound/ChildSound.kt").readText()
        listOf("Server", "stt(", "transcribe", "http", "URL(").forEach {
            assertFalse("ChildSound.kt 에 「$it」", src.contains(it))
        }
    }
}
