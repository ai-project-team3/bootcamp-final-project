package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.heroNameFrom
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
}
