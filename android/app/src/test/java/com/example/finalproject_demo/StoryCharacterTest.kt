package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryCharacterTest {
    @Test
    fun startingAnotherBookClearsTheSelectedImageButKeepsTheBestiaryHero() {
        val state = DemoState()
        val hero = Hero("Generated friend", com.example.finalproject_demo.ui.HeroAttr(), "local:hero.png", "human")
        state.heroes += hero
        state.storyHeroImage = hero.image
        state.storyHeroRig = hero.rig
        state.resetStory()
        assertNull(state.storyHeroImage)
        assertNull(state.storyHeroRig)
        assertEquals(hero, state.heroes.last())
    }

    @Test
    fun spokenHeroCreationDisplaysAndKeepsTheActualServerPng() = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.YELLOW)
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", false)
            .put("rig", "human").put("png_base64", Base64.getEncoder().encodeToString(png)) }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        d.s.speed = 0.01
        d.s.bookStyle = "crayon"            // even in a crayon book the 도감 hero is drawn felt (결정 27)
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.MAKEHERO)
            withTimeout(5_000) { while (d.s.stage !is Stage.CardsRow) delay(10) }
            val feeder = launch {
                while (isActive) {
                    if (d.s.stage is Stage.HeroAnswer) {
                        d.send(Reply.Tapped("ok", "맞아"))
                        delay(40)
                        continue
                    }
                    if (d.s.stage is Stage.CardsRow) d.send(Reply.Tapped("voice", "말로 만들기"))
                    if (d.s.micEnabled) when {
                        "옷" in d.s.line && "머리" !in d.s.line -> d.send(Reply.Tapped("F9B233", "노란 옷"))
                        "안경" in d.s.line && "머리" !in d.s.line -> d.send(Reply.Tapped("square", "네모 안경"))
                        else -> d.send(Reply.Spoke("긴 머리에 빨간 드레스를 입고 안경은 안 써"))
                    }
                    delay(40)
                }
            }
            withTimeout(8_000) { while (d.s.stage !is Stage.Confirm) delay(10) }
            feeder.cancelAndJoin()
            val art = (d.s.stage as Stage.Confirm).art
            assertTrue("confirmation must show generated PNG, not a preset doll", art is Art.Img)
            art as Art.Img
            assertArrayEquals(png, File(art.name.removePrefix("local:")).readBytes())
            val request = server.requests.single { it.first == "/image" }.second
            assertEquals("character", request.getString("kind"))
            assertEquals("the 도감 hero is felt in any book (결정 27 · #331 audit)", "felt", request.getString("style"))
            assertTrue(request.getString("description").contains("드레스"))
            assertTrue("the latest card choice must also reach generation", request.getString("description").contains("F9B233"))
            assertTrue(request.getString("description").contains("square"))
            assertFalse(request.getString("description").contains(d.s.childName))
            d.send(Reply.Tapped("ok", "좋아"))
            // The named story hero proceeds directly to the first question (#342).
            withTimeout(5_000) { while (d.s.stage !is Stage.NameEntry) delay(10) }
            d.send(Reply.Tapped(com.example.finalproject_demo.demo.NAME_TYPED, "콩이"))
            withTimeout(5_000) { while (d.s.scene != Scene.PLACE) delay(10) }
            assertEquals("콩이", d.s.storyHeroCall)
            assertEquals(art.name, d.s.storyHeroImage)
            assertEquals("human", d.s.storyHeroRig)
            withTimeout(5_000) { while (d.s.stage !is Stage.Show || !d.s.micEnabled) delay(10) }
            assertEquals("The generated hero must remain visible on the neutral first-question stage",
                art, (d.s.stage as Stage.Show).art)
            assertEquals(art, d.s.storyHeroArt)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }

    /**
     * 10-06 실기기(같이 만들기) — 「멋있게」 · 「슈퍼히어로」 · 「가면 쓰고 있어」가 모두 「바꿀 모습을 못 찾음」으로 버려지고
     * 기본 인형이 나왔다. 말 그대로 그리는 길이 동화에만 열려 있었다. 협업에서도 아이 말이 그림 요청에 간다
     */
    @Test
    fun coopHeroCreationSendsTheChildsWordsToThePicture() = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.RED)
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", false)
            .put("rig", "human").put("png_base64", Base64.getEncoder().encodeToString(png)) }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        d.s.speed = 0.01
        d.s.mode = StoryMode.COOP
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.COOP)
        try {
            d.go(Scene.MAKEHERO)
            withTimeout(5_000) { while (d.s.stage !is Stage.CardsRow) delay(10) }
            val feeder = launch {
                while (isActive) {
                    if (d.s.stage is Stage.HeroAnswer) { d.send(Reply.Tapped("ok", "맞아")); delay(40); continue }
                    if (d.s.stage is Stage.CardsRow) d.send(Reply.Tapped("voice", "말로 만들기"))
                    if (d.s.micEnabled) when {
                        "머리" in d.s.line -> d.send(Reply.Spoke("멋있게"))
                        "옷" in d.s.line -> d.send(Reply.Spoke("슈퍼히어로"))
                        else -> d.send(Reply.Spoke("가면 쓰고 있어"))
                    }
                    delay(40)
                }
            }
            withTimeout(8_000) { while (d.s.stage !is Stage.Confirm) delay(10) }
            feeder.cancelAndJoin()
            assertTrue("협업도 서버가 그린 인형을 보여 준다", (d.s.stage as Stage.Confirm).art is Art.Img)
            val description = server.requests.single { it.first == "/image" }.second.getString("description")
            listOf("멋있게", "슈퍼히어로", "가면").forEach { assertTrue("「$it」이 그림 요청에 없다: $description", it in description) }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
