package com.example.finalproject_demo

import com.example.finalproject_demo.demo.partnerKeyIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Real speech carries no scripted value — the partner is read from the words (09-29 S25+ loop). */
class PartnerWordsTest {
    @Test
    fun whatTheServerActuallyHeardOnThePhone() {
        // the real transcripts from the 09-29 phone test
        assertEquals("mom", partnerKeyIn("엄마"))
        assertEquals("mom", partnerKeyIn("엄마 엄마 집안에 뜬다고요?"))
        assertEquals("dad", partnerKeyIn("아빠! 아빠! 아빠! 아빠!"))
    }

    @Test
    fun longerNamesWinAndPlainWordsDoNotMatch() {
        assertEquals("grandpa", partnerKeyIn("할아버지랑 할래"))
        assertEquals("grandma", partnerKeyIn("할머니!"))
        assertEquals("friend", partnerKeyIn("옆집 친구랑"))
        assertNull(partnerKeyIn("몰라"))
    }
}
