package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.TermsDoc
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 10-06: the TypeCast voice is a third-party provision — optional, off by default, and it travels with each /tts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypecastConsentTest {
    @Test fun theItemIsOptionalAndSaysWhatChangesWithoutIt() {
        val d = TermsDoc.TYPECAST_VOICE
        assertFalse("a third-party provision is never required", d.required)
        val text = d.parts.joinToString(" ") { p -> p.title + p.body + p.rows.joinToString { it.head + it.body } }
        assertTrue("names the company", "네오사피엔스" in text)
        assertTrue("refusing keeps every feature", "모든 기능" in text)
    }

    @Test fun onlyAConsentingFamilyAsksForTypecast() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> JSONObject() }
        try {
            Server.base = server.base
            ConsentStore.setTypecastVoice(false)
            Server.tts("안녕")
            ConsentStore.setTypecastVoice(true)
            Server.tts("안녕")
            ConsentStore.withdraw()                         // withdrawing the main consent drops this one too
            Server.tts("안녕")
            val asked = server.requests.filter { it.first == "/tts" }.map { it.second.optString("provider") }
            assertEquals(listOf("", "typecast", ""), asked.map { if (it == "null") "" else it })
        } finally {
            ConsentStore.setTypecastVoice(false)
            Server.base = previous
            server.close()
        }
    }
}
