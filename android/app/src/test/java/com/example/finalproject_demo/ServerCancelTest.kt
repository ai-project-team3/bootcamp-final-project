package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 — 그림일기 화이트보드에서 「나가기」가 바로 안 됐다. 장면을 바꿀 때 앱은 지금 장면이 끝나기를 기다리는데
 * (`Director.go` 의 cancelAndJoin), 서버 응답을 기다리던 장면은 응답이 오거나 읽기 제한 시간이 될 때까지 멈추지 않았다.
 * 취소하면 서버 호출이 바로 끝나야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerCancelTest {
    @Test fun cancellingACallWaitingForASlowServerReturnsAtOnce() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> Thread.sleep(8_000); JSONObject() }
        try {
            Server.base = server.base
            val call = CoroutineScope(Dispatchers.Default).launch { Server.report(JSONObject().put("kind", "other")) }
            delay(500)                                   // the request is out; the server is still thinking (/report waits 10 s for an answer)
            val t0 = System.nanoTime()
            call.cancelAndJoin()
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue("cancel waited $ms ms for the server", ms < 2_000)
        } finally {
            Server.base = previous
            server.close()
        }
    }
}
