package com.example.finalproject_demo

import com.example.finalproject_demo.net.ChildCall
import com.example.finalproject_demo.net.NameMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 10-02 (rule 6 revised): names are no longer hidden — the child's call is a nickname the guardian
 * chose and goes out as is. What stays is turning the server's placeholders back into names.
 */
class NameMaskTest {
    private val m = NameMask("지민", listOf("민수", "하늘"))

    @Test
    fun namesLeaveAsTheyAre() {
        assertEquals("지민이랑 민수가 놀았어", m.mask("지민이랑 민수가 놀았어"))
        assertEquals(mapOf("companion" to "민수랑", "place" to null),
            m.maskSlots(mapOf("companion" to "민수랑", "place" to null)))
    }

    @Test
    fun theParticleFollowsTheRealName() {
        // 지민 has a final consonant; the model wrote the forms for a name without one
        assertEquals("지민은 민수와 하늘이랑 떠났어요.", m.unmask("{주인공}는 {친구1}과 {친구2}랑 떠났어요."))
        assertEquals("지민이 민수를 불렀어요.", m.unmask("{주인공}가 {친구1}을 불렀어요."))
        assertEquals("지민으로", m.unmask("{주인공}로"))
    }

    @Test
    fun rieulTakesRoNotEuro() {
        assertEquals("하늘로 갔어요", NameMask("하늘").unmask("{주인공}으로 갔어요"))
    }

    @Test
    fun anUnknownPlaceholderNeverShowsBraces() {
        assertEquals("친구가 왔어요.", m.unmask("{친구3}가 왔어요."))
        assertFalse("{" in NameMask(null).unmask("{주인공}는 웃었어요."))
    }

    @Test
    fun theVoiceReadsNamesAndTurnsPlaceholdersBack() {
        assertEquals("지민이 민수랑 공룡 나라에 갔구나!", m.speakable("지민이 민수랑 공룡 나라에 갔구나!"))
        assertEquals("지민아, 어디로 갈래?", m.speakable("{주인공}아, 어디로 갈래?"))
        assertEquals("민수가 지민을 불렀어.", m.speakable("{친구1}가 {주인공}를 불렀어."))
    }

    @Test
    fun aOneLetterCallStillComesBack() {
        assertEquals("콩은 웃었어.", NameMask("콩").unmask("{주인공}는 웃었어."))
    }

    @Test
    fun withNoCallTheChildIsFriend() {
        ChildCall.reset()
        assertEquals("친구", ChildCall.call)
    }
}
