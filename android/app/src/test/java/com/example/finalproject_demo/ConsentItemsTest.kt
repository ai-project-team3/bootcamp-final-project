package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.shell.TermsDoc
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-08 종훈 — consent asks for two required items only; nothing is asked for a feature the app does not use.
 * The privacy item still carries what the guardian, the child and the overseas transfer each mean.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentItemsTest {
    @Test fun twoRequiredItemsAndNoOptionalOnes() {
        assertEquals(listOf(TermsDoc.TERMS, TermsDoc.PRIVACY), TermsDoc.entries.toList())
        assertTrue("every item is required", TermsDoc.entries.all { it.required })
    }

    @Test fun thePrivacyItemStillSaysWhoGetsWhatAndWhere() {
        val d = TermsDoc.PRIVACY
        val text = d.parts.joinToString(" ") { p -> p.title + p.body + p.rows.joinToString { it.head + it.body } }
        listOf("법정대리인", "보호자 이메일", "아이 목소리", "OpenAI", "TypeSafe", "미국", "국외 이전", "보유 기간", "거부할 권리")
            .forEach { assertTrue("the privacy item lost 「$it」", it in text) }
    }

    @Test fun noFamilysLinesAskForTypecast() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> JSONObject() }
        try {
            Server.base = server.base
            Server.tts("안녕")
            val asked = server.requests.filter { it.first == "/tts" }.map { it.second.optString("provider") }
            assertEquals(listOf(""), asked.map { if (it == "null") "" else it })
        } finally {
            Server.base = previous
            server.close()
        }
    }
}
