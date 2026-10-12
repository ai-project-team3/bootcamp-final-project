package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.nameMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 10-09 device — the hero was the nameless card 「빨간 옷 친구」. `{주인공}` came back as the child's call 「친구」 while the
 * server kept the child's 「빨간 옷 친구」 as someone else: 「친구는 빨간 옷 친구를 만났어요」. One child, one name.
 */
class StoryHeroCardNameTest {

    private fun story() = DemoState().apply { mode = StoryMode.STORY; storyHeroCard = "빨간 옷 친구" }

    @Test
    fun theCardNameIsTheHeroBothWays() {
        val mask = story().nameMask()
        assertEquals("빨간 옷 친구", mask.names.first())
        assertEquals("{주인공}가 숲에서 길을 잃었어", mask.mask("빨간 옷 친구가 숲에서 길을 잃었어"))
        val back = mask.unmask("숲길을 지나던 {주인공}는 집으로 돌아갔어요.")
        assertEquals("숲길을 지나던 빨간 옷 친구는 집으로 돌아갔어요.", back)
        assertFalse("a lone 「친구」 is not masked", "{주인공}" in mask.mask("친구랑 놀았어"))
    }

    /** #392 review — a new story forgets the last hero card; the next doll request keeps its words */
    @Test
    fun aNewStoryForgetsTheCard() {
        val s = story()
        s.resetStory()
        s.mode = StoryMode.STORY
        assertEquals(null, s.storyHeroCard)
        assertEquals("빨간 옷 친구처럼 생긴 토끼", s.nameMask().mask("빨간 옷 친구처럼 생긴 토끼"))
        val app = story().apply { reset(); mode = StoryMode.STORY }
        assertEquals(null, app.storyHeroCard)
    }

    @Test
    fun aNamedDollOrAnotherModeIsUnchanged() {
        val named = story().apply { storyHeroCall = "도리" }.nameMask()
        assertEquals("도리", named.names.first())
        assertEquals("빨간 옷 친구가 왔어", named.mask("빨간 옷 친구가 왔어"))
        val diary = story().apply { mode = StoryMode.DIARY }.nameMask()
        assertEquals(DemoState().childName, diary.names.first())
        assertEquals("the book's own lines use the card too", "빨간 옷 친구", story().storyActor)
    }
}
