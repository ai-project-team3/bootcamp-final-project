package com.example.finalproject_demo

import com.example.finalproject_demo.demo.partnerIn
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

    @Test
    fun everyCommonWayToCallSomeone() {
        assertEquals("uncle" to "삼촌", partnerIn("삼촌"))                  // the 09-29 phone loop
        assertEquals("uncle" to "외삼촌", partnerIn("외삼촌이랑 왔어"))
        assertEquals("aunt" to "고모", partnerIn("고모!"))
        assertEquals("grandma" to "외할머니", partnerIn("외할머니랑"))
        assertEquals("teacher" to "선생님", partnerIn("선생님이랑 할래"))
        assertEquals("sibling" to "누나", partnerIn("누나"))
        assertEquals("sibling" to "형", partnerIn("형이랑"))
    }

    @Test
    fun aFriendsNameIsTakenAsAFriendAndKept() {
        assertEquals("friend" to "민수", partnerIn("민수!"))
        assertEquals("friend" to "지민", partnerIn("지민이랑"))
        assertEquals("friend" to "친구", partnerIn("옆집 친구랑"))
        assertNull("not a name", partnerIn("몰라"))
        assertNull("a sentence is not a name", partnerIn("오늘 아무도 없고 그냥 혼자 왔어"))
    }
}
