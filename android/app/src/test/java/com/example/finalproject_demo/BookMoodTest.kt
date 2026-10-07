package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BgmMood
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.bookKeyOf
import com.example.finalproject_demo.demo.moodOf
import com.example.finalproject_demo.demo.trackOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 쪽 종류 → 분위기 → 곡 (#221 · 2026-10-07 설계) */
class BookMoodTest {
    @Test fun everyPageKindHasTheAgreedMood() {
        val want = mapOf(
            PageKind.COVER to BgmMood.DISCOVERY, PageKind.MEET to BgmMood.DISCOVERY,
            PageKind.DEPART to BgmMood.ADVENTURE, PageKind.JOURNEY to BgmMood.ADVENTURE,
            PageKind.TALK to BgmMood.PLAYFUL, PageKind.RUB to BgmMood.PLAYFUL, PageKind.DRAG to BgmMood.PLAYFUL,
            PageKind.SHAKE to BgmMood.TENSE, PageKind.FAIL to BgmMood.TENSE,
            PageKind.TOGETHER to BgmMood.ENDING,
        )
        assertEquals(PageKind.entries.toSet(), want.keys)
        want.forEach { (k, m) -> assertEquals("$k", m, moodOf(k)) }
    }

    @Test fun oneBookAlwaysGetsTheSameTrack() {
        val key = bookKeyOf("용감한 공룡", "공룡 나라에 갔어요.")
        assertEquals(trackOf(BgmMood.TENSE, key), trackOf(BgmMood.TENSE, bookKeyOf("용감한 공룡", "공룡 나라에 갔어요.")))
    }

    @Test fun booksSpreadOverBothTracksOfAMood() {
        val used = (1..40).map { trackOf(BgmMood.NIGHT, bookKeyOf("책 $it", "첫 문장 $it")) }.toSet()
        assertEquals(BgmMood.NIGHT.tracks.toSet(), used)
    }

    @Test fun everyTrackIsInTheApp() {
        val dir = File("src/main/assets/bgm")
        BgmMood.entries.flatMap { it.tracks }.forEach { assertTrue(it, File(dir, it).isFile) }
    }
}
