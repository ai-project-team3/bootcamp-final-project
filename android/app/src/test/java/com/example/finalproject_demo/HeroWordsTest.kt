package com.example.finalproject_demo

import com.example.finalproject_demo.demo.heroValueIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Real speech has no scripted value — the hero's look is read from the words (09-29 S25+ crash on 「드레스」). */
class HeroWordsTest {
    @Test
    fun theAnswerThatCrashedTheAppIsJustNotFound() {
        assertNull("no colour in 「드레스」 — keep the look, do not crash", heroValueIn("shirt", "드레스"))
    }

    @Test
    fun oneQuestionLooksOnlyAtItsOwnPart() {
        assertEquals("shirt" to "F25C4C", heroValueIn("shirt", "빨간 드레스!"))
        assertEquals("hair" to "long", heroValueIn("hair", "긴 머리가 좋아"))
        assertEquals("glasses" to "none", heroValueIn("glasses", "안경 싫어!"))
        assertNull("the hair question must not read a colour", heroValueIn("hair", "파랑!"))
    }

    @Test
    fun whatToChangeLooksAtAllParts() {
        assertEquals("glasses" to "square", heroValueIn(null, "네모 안경 씌워 줘"))
        assertEquals("hair" to "tied", heroValueIn(null, "머리 묶어 줘"))
        assertEquals("shirt" to "F9B233", heroValueIn(null, "노란 옷!"))
    }
}
