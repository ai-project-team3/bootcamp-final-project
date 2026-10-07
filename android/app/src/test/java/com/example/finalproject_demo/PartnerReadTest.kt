package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.partnerWire
import com.example.finalproject_demo.demo.readPartner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #303 — the server reads 「누구랑?」(Jev, 153/153 against the word list's 44/51 · 10-07) and learns when
 * nobody sits with the child; the word list stays for when the server has nothing.
 */
class PartnerReadTest {
    private fun read(text: String, server: String?, live: Boolean = true) =
        runBlocking { readPartner(text, live) { server } }

    @Test fun theServerWinsWhereTheWordListMisreads() {
        // the list sees 「엄마」 first and picks mom; the child asked for dad
        assertEquals("dad" to "아빠", read("엄마 말고 아빠랑", "dad"))
    }

    @Test fun whatTheChildCalledThemStaysWhenBothAgree() {
        assertEquals("grandma" to "할미", read("우리 할미!", "grandma"))
        assertEquals("friend" to "민수", read("민수", "friend"))
    }

    @Test fun aFriendTheListMissedIsStillAFriend() {
        assertEquals("friend" to "친구", read("하윤이랑 같이 왔어", "friend"))
    }

    @Test fun unknownMeansAskAgainAndSoloIsSolo() {
        assertNull(read("배고파", "unknown"))
        assertEquals("solo" to "혼자", read("나 혼자야", "solo"))
    }

    @Test fun noServerAnswerOrServerOffFallsBackToTheWordList() {
        assertEquals("mom" to "엄마", read("엄마랑!", null))
        assertEquals("mom" to "엄마", read("엄마랑!", "dad", live = false))
        assertEquals("mom" to "엄마", read("엄마랑!", "robot"))           // off-list never decides
    }

    @Test fun theServerHearsWhoSitsWithTheChild() {
        val s = DemoState()
        s.partnerKey = "mom"
        assertEquals("adult", s.partnerWire("story"))
        s.partnerKey = "friend"
        assertEquals("peer", s.partnerWire("story"))
        s.partnerKey = "solo"
        assertEquals("none", s.partnerWire("story"))
        assertEquals("adult", s.partnerWire("coop"))                       // co-op always has the parent
        assertNull(s.partnerWire("diary"))
    }
}
