package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.NAME_AGAIN
import com.example.finalproject_demo.demo.NAME_OK
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.askHeroName
import com.example.finalproject_demo.demo.heroNameFrom
import com.example.finalproject_demo.demo.spokenYesNo
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
import org.junit.Assert.assertFalse
import org.junit.Test

/** 10-02 (#87): the child names the doll by voice or text, and that name is the story's hero. */
class HeroNameTest {
    @Test
    fun aNameSaidOnTheConfirmationScreenMustBeConfirmedAgain() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        val name = async { d.askHeroName(HeroAttr(), null) }
        suspend fun waitFor(heard: String) {
            withTimeout(3_000) {
                while ((d.s.stage as? Stage.NameEntry)?.heard != heard) delay(5)
            }
        }
        try {
            withTimeout(3_000) { while (!d.s.micEnabled) delay(5) }
            delay(30)
            d.send(Reply.Spoke("비주기"))
            waitFor("비주기")
            d.send(Reply.Spoke("삐죽이라고 할래"))
            delay(200)
            assertFalse("a new spoken name is not an explicit confirmation", name.isCompleted)
            assertEquals("삐죽이", (d.s.stage as? Stage.NameEntry)?.heard)
            assertEquals("「삐죽이」 맞아?", d.s.line)
            d.send(Reply.Tapped(NAME_OK, "이 이름이야"))
            assertEquals("삐죽이", withTimeout(3_000) { name.await() })
        } finally { name.cancel(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
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

    /** 10-05 device: the child said 「응」 to 「삐죽이 맞아?」 and nothing moved — the confirm now hears yes and no */
    @Test
    fun yesAndNoAreHeard() {
        listOf("응", "응!", "어", "네", "맞아", "맞아요", "그래").forEach { assertEquals(it, true, spokenYesNo(it)) }
        listOf("아니", "아니야", "아냐 다시", "다시 할래").forEach { assertEquals(it, false, spokenYesNo(it)) }
        // a name said again, or 「어흥」, is not a yes
        listOf("삐죽이", "어흥이", "응가 공주라고 할래").forEach { assertNull(it, spokenYesNo(it)) }
    }
}
