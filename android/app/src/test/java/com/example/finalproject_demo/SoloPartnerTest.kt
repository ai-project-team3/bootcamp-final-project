package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SoloPartnerTest {
    @Test fun anExplicitCompanionWinsOverAnAlonePhrase() {
        assertEquals("mom", partnerKeyIn("혼자 안 할래, 엄마랑"))
        assertEquals("dad", partnerKeyIn("혼자는 싫어 아빠랑"))
        assertEquals("mom", partnerKeyIn("엄마랑 혼자 왔어"))
        assertEquals("grandma", partnerKeyIn("할머니하고 같이 할래 혼자는 싫어"))
        assertEquals("solo", partnerKeyIn("엄마는 없고 나 혼자 왔어"))
        assertEquals("solo", partnerKeyIn("엄마랑 아니고 혼자"))
    }

    @Test fun anAloneHelperNeverBecomesAnImaginaryFriend() {
        val s = DemoState().apply {
            partnerKey = "solo"
            templateKey = "C"
            slots["helper"] = "혼자"
        }
        val captions = (1..s.pageCount).map { s.bookCaption(it) }
        assertFalse(captions.any { "내가 같이 있을게" in it || "혼자에게" in it })
    }

    private suspend fun waitFor(predicate: () -> Boolean) {
        withTimeout(3_000) { while (!predicate()) delay(5) }
    }

    @Test fun aloneIsAnAnswerRatherThanAnUnrecognizedPerson() {
        for (line in listOf("혼자", "나 혼자!", "아무도 없어", "나만", "엄마는 없고 나 혼자 왔어")) {
            assertEquals(line, "solo", partnerKeyIn(line))
        }
        assertEquals("mom", partnerKeyIn("혼자 아니야 엄마랑"))
    }

    @Test fun twoUnrecognizedRepliesNeverInventMom() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.go(Scene.PARTNER)
            waitFor { d.s.micEnabled }
            d.send(Reply.Spoke("글쎄 지금은 누구라고 말해야 할지 모르겠어"))
            waitFor { d.s.line.contains("한 번만 더") }
            delay(30)
            d.send(Reply.Spoke("음 아직 누구라고 말할지 모르겠는데"))
            waitFor { d.s.scene != Scene.PARTNER }
            assertEquals("unknown", d.s.partnerKey)
            assertEquals(0, d.s.partnerTurns)
        } finally { scope.cancel() }
    }

    @Test fun aSoloChildSkipsPartnerQuestionsWithoutAnUtterance() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.partnerKey = "solo"; s.speed = 0.01 }
        try {
            val before = d.s.lineId
            assertNull(withTimeout(300) { d.askPartner("어른에게 묻는 질문", listOf(Answer("답", "답"))) })
            assertEquals(before, d.s.lineId)
            assertEquals(0, d.s.partnerTurns)
        } finally { scope.cancel() }
    }

    @Test fun aSoloReplyContinuesWithoutPartnerStateOrInventedHelp() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false; s.partnerHelpLine = "엄마가 안아 주었어요" }
        try {
            d.go(Scene.PARTNER)
            waitFor { d.s.micEnabled }
            d.send(Reply.Spoke("나 혼자!"))
            waitFor { d.s.scene != Scene.PARTNER }
            assertEquals("solo", d.s.partnerKey)
            assertFalse(d.s.hasPartner)
            assertNull(d.s.partnerHelpLine)
            d.s.templateKey = "C"
            val captions = (1..d.s.pageCount).map { d.s.bookCaption(it) }
            assertTrue(captions.none { "엄마" in it || "혼자에게" in it })
            d.s.slots["helper"] = "마법사"
            assertTrue((1..d.s.pageCount).any { "마법사" in d.s.bookCaption(it) })
        } finally { scope.cancel() }
    }

    @Test fun repeatedSilenceDoesNotInventACompanion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.go(Scene.PARTNER)
            waitFor { d.s.micEnabled }
            repeat(3) {
                val previous = d.s.lineId
                d.send(Reply.Silent)
                waitFor { d.s.lineId > previous }
            }
            waitFor { d.s.scene != Scene.PARTNER }
            assertFalse(d.s.hasPartner)
            assertEquals("unknown", d.s.partnerKey)
        } finally { scope.cancel() }
    }
}
