package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 10-06: the phone counts its own server calls per session — the Play build's per-device limit is set from them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerCallCountTest {
    @Test fun everyPostIsCountedByPathAndResetClearsIt() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> JSONObject().put("text", "응") }
        try {
            Server.base = server.base
            Server.resetCalls()
            assertEquals("Nothing called yet", "", Server.callSummary())
            Server.stt(byteArrayOf(0, 1))
            Server.stt(byteArrayOf(0, 1))
            Server.tts("안녕")
            val summary = Server.callSummary()
            assertTrue(summary, summary.startsWith("total 3 · /stt 2 · /tts 1"))
            Server.resetCalls()
            assertEquals("", Server.callSummary())
        } finally {
            Server.base = previous
            server.close()
        }
    }

    @Test fun noAddressMeansNoCall() = runBlocking {
        val previous = Server.base
        try {
            Server.base = null
            Server.resetCalls()
            Server.tts("안녕")
            assertEquals("A script-only app calls nothing", "", Server.callSummary())
        } finally {
            Server.base = previous
        }
    }
}
