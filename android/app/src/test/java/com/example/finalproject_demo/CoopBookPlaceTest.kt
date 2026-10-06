package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopBookPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-06 실기기 — 장소 칸의 아이 문장 「소방서에서 일할 것 같았어.」가 그대로 서버로 가서
 * 곧 해요 책 1쪽이 「소방서에서 일할 것 같다고 생각할 거예요」가 됐다. 책에는 곳 이름만 보낸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopBookPlaceTest {
    private fun place(p: String?) = DemoState().apply {
        mode = StoryMode.COOP; coopPick = CoopPick("job", "소방관", "soon"); place = p
    }.coopBookPlace()

    @Test
    fun aSentenceSendsOnlyThePlaceName() {
        assertEquals("소방서", place("소방서에서 일할 것 같았어."))
        assertEquals("동물원", place("동물원에 갔어"))
        assertEquals("큰 소방서", place("큰 소방서에서 일해"))
        assertEquals("제주도", place("제주도에 갈 거야"))
        assertEquals("할머니 집", place("할머니 집에서 잤어"))
    }

    /** 곳 이름이 아니라 말 토막이면 원문 */
    @Test
    fun aPhraseIsNotTakenAsAPlace() {
        assertEquals("엄마랑 집에서 놀았어", place("엄마랑 집에서 놀았어"))
        assertEquals("거기에 또 가고 싶어", place("거기에 또 가고 싶어"))
        assertEquals("놀이터에", place("놀이터에"))      // 뒤에 서술어가 없으면 손대지 않는다(이름 칸 다듬기는 heardPlace 몫)
    }

    @Test
    fun aNameStaysAsItIs() {
        assertEquals("소방서", place("소방서"))
        assertEquals("기린 마당", place("기린 마당"))
    }

    /** 이름을 못 떼면 원문 — 지어낸 이름보다 아이 말이 낫다 */
    @Test
    fun aSentenceWithNoPlaceNameIsSentAsSaid() {
        assertEquals("기린 마당이 보였어.", place("기린 마당이 보였어."))
        assertEquals("기린 있는 데가 제일 좋았어", place("기린 있는 데가 제일 좋았어"))
        assertNull(place(null))
    }

    /** 칸 값은 그대로 — 부모 리포트는 원문을 보여 준다 */
    @Test
    fun theSlotKeepsTheChildsWords() {
        val s = DemoState().apply { mode = StoryMode.COOP; place = "소방서에서 일할 것 같았어." }
        s.coopBookPlace()
        assertEquals("소방서에서 일할 것 같았어.", s.place)
    }
}
