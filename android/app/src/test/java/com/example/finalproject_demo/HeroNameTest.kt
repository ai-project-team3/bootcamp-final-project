package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.NAME_AGAIN
import com.example.finalproject_demo.demo.NAME_OK
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.askHeroName
import com.example.finalproject_demo.demo.heroNameFrom
import com.example.finalproject_demo.ui.HeroAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.example.finalproject_demo.net.nameMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 10-02 (#87): the child names the doll by voice or text, and that name is the story's hero. */
class HeroNameTest {
    @Test
    fun whatAChildSaysBecomesTheName() {
        assertEquals("콩이", heroNameFrom("콩이"))
        assertEquals("콩이", heroNameFrom("콩이라고 할래"))
        assertEquals("뽀삐", heroNameFrom("이름은 뽀삐야!"))
        assertEquals("반짝이", heroNameFrom("반짝이야"))
        assertEquals("별이", heroNameFrom("별이예요"))
        assertNull(heroNameFrom("  ...  "))
    }

    @Test
    fun theDollsNameIsTheHeroAndTheChildCallIsTheFallback() {
        val s = DemoState()
        assertEquals("친구는 웃었어요.", s.nameMask().unmask("{주인공}는 웃었어요."))
        s.storyHeroCall = "콩이"
        assertEquals("콩이는 웃었어요.", s.nameMask().unmask("{주인공}는 웃었어요."))
    }

    /** 10-02 device: 「삐죽이」 was heard as 「비주기」 — a spoken name is confirmed before it is kept */
    @Test
    fun aSpokenNameIsConfirmedAndCanBeSaidAgain() = runBlocking {
        val d = Director(CoroutineScope(SupervisorJob())).apply { s.speed = 0.01; s.timerOn = false }
        val name = async { d.askHeroName(HeroAttr(), null) }
        suspend fun waitFor(heard: String?) = try {
            withTimeout(3_000) { while ((d.s.stage as? Stage.NameEntry)?.heard != heard) delay(5) }
        } catch (e: Exception) {
            throw AssertionError("waiting for heard=$heard · stage=${d.s.stage} · log=${d.s.log.take(6)}", e)
        }
        waitFor(null); delay(200); d.send(Reply.Spoke("비주기"))
        waitFor("비주기"); d.send(Reply.Tapped(NAME_AGAIN, "다시"))
        waitFor(null); delay(200); d.send(Reply.Spoke("삐죽이라고 할래"))
        waitFor("삐죽이"); d.send(Reply.Tapped(NAME_OK, "맞아"))
        assertEquals("삐죽이", withTimeout(3_000) { name.await() })
    }
}
