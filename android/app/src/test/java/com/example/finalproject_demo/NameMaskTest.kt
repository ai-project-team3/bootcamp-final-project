package com.example.finalproject_demo

import com.example.finalproject_demo.net.NameMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Rule 6: real names never leave the phone, and they come back reading right. */
class NameMaskTest {
    private val m = NameMask("지민", listOf("민수", "하늘"))

    @Test
    fun namesBecomePlaceholdersBeforeTheyLeave() {
        assertEquals("{주인공}이랑 {친구1}가 놀았어", m.mask("지민이랑 민수가 놀았어"))
        assertEquals("{친구2}야 같이 가자", m.mask("하늘야 같이 가자"))
    }

    @Test
    fun aNameInsideAnotherWordStaysPut() {
        assertEquals("보민수는 없어", m.mask("보민수는 없어"))
    }

    @Test
    fun slotsAreMaskedWholeAndNullsStayNull() {
        val out = m.maskSlots(mapOf("companion" to "민수랑", "place" to null))
        assertEquals("{친구1}랑", out["companion"])
        assertEquals(null, out["place"])
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
    fun roundTripKeepsTheSentence() {
        val said = "지민이 민수한테 사탕을 줬어"
        assertEquals(said, m.unmask(m.mask(said)))
    }

    @Test
    fun anUnknownPlaceholderNeverShowsBraces() {
        val out = m.unmask("{친구3}가 왔어요.")
        assertEquals("친구가 왔어요.", out)
        assertFalse("{" in NameMask(null).unmask("{주인공}는 웃었어요."))
    }

    @Test
    fun theVoiceReadsTheNameOnlyWithConsent() {
        val line = "지민이 민수랑 공룡 나라에 갔구나!"
        assertEquals(line, m.speakable(line, named = true))
        assertEquals("네가 그 친구랑 공룡 나라에 갔구나!", m.speakable(line, named = false))
        // a masked line from the server reads the same way
        assertEquals("너는 웃었어.", m.speakable("{주인공}은 웃었어.", named = false))
    }

    /** #50 · 10-01: the child and a story friend both came out as "친구" in one line */
    @Test
    fun withoutConsentTheChildIsYouAndAFriendIsThatFriend() {
        assertEquals("너와 함께 갈 친구는 누구일까?", m.speakable("{주인공}와 함께 갈 친구는 누구일까?", named = false))
        assertEquals("친구야, 어디로 갈래?", m.speakable("{주인공}아, 어디로 갈래?", named = false))   // calling the child
        assertEquals("친구야! 같이 가자", m.speakable("{주인공}야! 같이 가자", named = false))
        assertEquals("그 친구가 너를 불렀어.", m.speakable("{친구1}가 {주인공}를 불렀어.", named = false))
        assertEquals("네가 먼저 해 볼래?", m.speakable("{주인공}이가 먼저 해 볼래?", named = false))
    }

    @Test
    fun withoutConsentNoRealNameIsInTheVoice() {
        val out = m.speakable("하늘아, 지민이가 민수를 불렀어", named = false)
        listOf("지민", "민수", "하늘").forEach { assertFalse("$it leaked into the voice: $out", it in out) }
    }

    @Test
    fun oneLetterAndPlaceholderNamesAreIgnored() {
        val short = NameMask("호", listOf("{친구1}"))
        assertEquals("호수에 갔어", short.mask("호수에 갔어"))
    }
}
