package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The network client speaks the spec's shapes (guidelines/3 §3) and **never throws** (§3-0).
 * A tiny local server stands in for the backend — no keys, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerClientTest {
    private lateinit var http: ServerSocket
    private val seen = mutableMapOf<String, String>()     // path → request body
    private val answer = mutableMapOf<String, Pair<Int, ByteArray>>()

    /** A few lines of HTTP/1.1 — one request per connection, which is all the client does. */
    @Before
    fun start() {
        http = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!http.isClosed) {
                val sock = try { http.accept() } catch (e: Exception) { break }
                sock.use {
                    val input = it.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith(CRLF + CRLF)) head.append(input.read().toChar())
                    val path = head.lineSequence().first().split(" ")[1]
                    val len = LENGTH.find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    val body = ByteArray(len).also { b -> var r = 0; while (r < len) r += input.read(b, r, len - r) }
                    seen[path] = body.decodeToString()
                    val (code, out) = answer[path] ?: (404 to """{"error":true,"message":"no"}""".toByteArray())
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $code X${CRLF}Content-Length: ${out.size}${CRLF}Connection: close$CRLF$CRLF".toByteArray())
                        write(out)
                        flush()
                    }
                }
            }
        }
        Server.base = "http://127.0.0.1:${http.localPort}"
    }

    @After
    fun stop() {
        http.close()
        Server.base = null
    }

    private fun json(path: String, body: String) { answer[path] = 200 to body.toByteArray() }

    @Test
    fun offWithoutAnAddress() = runBlocking {
        Server.base = null
        assertNull("no address must mean no call", Server.judge(turn()))
        assertTrue(!Server.on)
    }

    @Test
    fun judgeSendsAllTwelveSlotsAndTheMode() = runBlocking {
        json("/judge", VERDICT)
        val v = Server.judge(turn())!!
        val sent = JSONObject(seen.getValue("/judge"))
        assertEquals("diary", sent.getString("mode"))
        assertEquals(12, sent.getJSONObject("slots").length())
        assertTrue("empty slots go as null", sent.getJSONObject("slots").isNull("cause"))
        assertEquals(listOf("place" to "놀이터", "problem" to "넘어졌다"), v.fills)
        assertEquals("reaction", v.nextSlot)
        assertTrue(v.s2Addition)
    }

    @Test
    fun aServerErrorBecomesNullNotACrash() = runBlocking {
        answer["/judge"] = 502 to """{"error":true,"message":"vendor"}""".toByteArray()
        assertNull(Server.judge(turn()))
        json("/judge", "not json")
        assertNull(Server.judge(turn()))
    }

    @Test
    fun anUnreachableServerBecomesNull() = runBlocking {
        Server.base = "http://127.0.0.1:1"      // nothing listens on port 1
        assertNull(Server.judge(turn()))
        assertNull(Server.health())
    }

    @Test
    fun storyReturnsCaptionsAndSendsWhoFilledWhat() = runBlocking {
        json("/story", """{"scenes":[{"index":1,"caption":"놀이터에 갔어요.","keywords":"x"},{"index":2,"caption":"넘어졌어요.","keywords":"y"}]}""")
        val caps = Server.story("diary", mapOf("place" to "놀이터"), mapOf("place" to "child", "keep" to "x"), keep = "또 갈래")
        assertEquals(listOf("놀이터에 갔어요.", "넘어졌어요."), caps)
        val sent = JSONObject(seen.getValue("/story"))
        assertEquals("child", sent.getJSONObject("slot_by").getString("place"))
        assertTrue("slot_by keeps only the twelve names", !sent.getJSONObject("slot_by").has("keep"))
        assertEquals("또 갈래", sent.getString("keep"))
        assertTrue("no plan → no pages field (old shape)", !sent.has("pages"))
    }

    @Test
    fun imageGivesPngBytesAndPresetMeansNull() = runBlocking {
        json("/image", """{"preset":false,"reason":"ok","scene":"a beach","png_base64":"iVBORw0KGgo="}""")
        val png = Server.image("바닷가")!!
        assertEquals(0x89.toByte(), png[0])
        assertEquals("바닷가", JSONObject(seen.getValue("/image")).getString("place"))

        json("/image", """{"preset":true,"reason":"check: flagged","scene":"x","png_base64":null}""")
        assertNull("a flagged picture must never reach the screen", Server.image("동굴"))
    }

    @Test
    fun characterGivesPngAndRigAndPresetMeansNull() = runBlocking {
        json("/image", """{"preset":false,"reason":"ok","scene":"robot","rig":"human","png_base64":"iVBORw0KGgo="}""")
        val c = Server.character("파란 눈 로봇")!!
        assertEquals("human", c.rig)
        assertEquals(0x89.toByte(), c.png[0])
        val sent = JSONObject(seen.getValue("/image"))
        assertEquals("character", sent.getString("kind"))
        assertEquals("파란 눈 로봇", sent.getString("description"))

        json("/image", """{"preset":true,"reason":"cutout: nothing but background","scene":"x","png_base64":null}""")
        assertNull(Server.character("유령"))
    }

    @Test
    fun storySendsThePagePlanAndRefusesAShortBook() = runBlocking {
        val plan = listOf(Server.Page("DEPART"), Server.Page("RUB", "A1"), Server.Page("TOGETHER"))
        json("/story", """{"scenes":[{"index":1,"caption":"a","keywords":"x"},{"index":2,"caption":"b","keywords":"x"},{"index":3,"caption":"c","keywords":"x"}]}""")
        assertEquals(listOf("a", "b", "c"), Server.story("story", emptyMap(), pages = plan))
        val sent = JSONObject(seen.getValue("/story")).getJSONArray("pages")
        assertEquals("RUB", sent.getJSONObject(1).getString("kind"))
        assertEquals("A1", sent.getJSONObject(1).getString("mission"))
        assertTrue(sent.getJSONObject(0).isNull("mission"))

        json("/story", """{"scenes":[{"index":1,"caption":"a","keywords":"x"}]}""")
        assertNull("a page short would move the missions", Server.story("story", emptyMap(), pages = plan))
    }

    @Test
    fun sttUploadsMultipartAndEmptyTextMeansNoAnswer() = runBlocking {
        json("/stt", """{"text":""}""")
        assertEquals("", Server.stt(byteArrayOf(1, 2, 3)))
        assertTrue(seen.getValue("/stt").contains("name=\"file\""))
    }

    @Test
    fun ttsReturnsTheAudioBytes() = runBlocking {
        answer["/tts"] = 200 to byteArrayOf(0x49, 0x44, 0x33)
        assertEquals(3, Server.tts("놀이터 갔구나!")!!.size)
    }

    /** #79: the answer options come with the question; blanks are dropped, and none means null */
    @Test
    fun turnCarriesTheAnswerOptions() = runBlocking {
        json("/turn", """{"judge":$VERDICT,"line":{"ack":"바닷속이구나!","expand":null,"question":"거기서 누굴 만났어?","options":["문어","","해파리"]}}""")
        assertEquals(listOf("문어", "해파리"), Server.turn(turn())!!.line!!.options)
        json("/turn", """{"judge":$VERDICT,"line":{"ack":"그랬구나!","expand":null,"question":null,"options":null}}""")
        assertNull(Server.turn(turn())!!.line!!.options)
    }

    @Test
    fun turnReturnsVerdictAndLineAndEitherMayBeMissing() = runBlocking {
        json("/turn", """{"judge":$VERDICT,"line":{"ack":"놀이터구나!","expand":null,"question":"거기서 뭐 했어?"}}""")
        val r = Server.turn(turn(), ask = false)!!
        assertEquals(false, JSONObject(seen.getValue("/turn")).getBoolean("ask"))
        assertEquals("reaction", r.verdict!!.nextSlot)
        assertEquals(Server.Line("놀이터구나!", null, "거기서 뭐 했어?"), r.line)

        json("/turn", """{"judge":null,"line":{"ack":"그랬구나!","expand":null,"question":null}}""")
        val half = Server.turn(turn())!!
        assertNull(half.verdict)
        assertNull(half.line!!.question)
    }

    private fun turn() = Server.Turn("diary", mapOf("place" to null), "place", "오늘 어디 갔었어?", "놀이터 갔어")

    companion object {
        private const val CRLF = "\r\n"
        private val LENGTH = Regex("(?i)content-length: *([0-9]+)")
        const val VERDICT = """{"reason":"r","slot_1":"place","value_1":"놀이터","slot_2":"problem","value_2":"넘어졌다",
            "contradiction":false,"contradiction_with":null,"s1_reason":false,"s2_addition":true,"emotion":null,
            "unclear":false,"unclear_of":null,"next_slot":"reaction","next_reason":"n","no_longer_needed":null,"story_ready":false}"""
    }
}
