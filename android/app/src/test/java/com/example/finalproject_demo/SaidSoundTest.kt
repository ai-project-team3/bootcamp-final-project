package com.example.finalproject_demo

import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.demo.missions.saidSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 협업 책 점검(10-14 · k18) — 「호랑이 사범님」 태권도 이야기의 소리 미션이 사자 소품으로 가서, 책에
 * 「매트 옆 사자는 눈을 감고 있었어요」가 나왔다. 같은 소리를 내는 다른 것을 아이가 말했으면 그 낱말로 부르고 보낸다.
 */
class SaidSoundTest {

    @Test
    fun theChildsWordNamesTheSoundProp() {
        val tiger = saidSound(SoundProp.LION, "호랑이랑 겨루기를 했어")!!
        assertEquals("호랑이", tiger.word)
        assertEquals("호랑이가 졸고 있어요.", tiger.before)
        assertEquals("호랑이처럼 「어흥!」 크게 소리 내 볼래?", tiger.ask)
        assertEquals("버스가 출발하려고 해요.", saidSound(SoundProp.CAR, "버스를 탔어")!!.before)
        assertEquals("구급차가 출동 준비를 하고 있어요.", saidSound(SoundProp.SIREN, "구급차가 지나갔어")!!.before)
    }

    @Test
    fun thePropsOwnWordOrNoneKeepsTheProp() {
        assertNull(saidSound(SoundProp.LION, "사자가 어흥 했어"))
        assertNull(saidSound(SoundProp.LION, "어흥 소리가 났어"))
        assertNull(saidSound(SoundProp.DOG, "강아지가 짖었어"))
    }

    @Test
    fun theParticleFollowsTheNewWord() {
        // 기차가 → 지하철이
        assertEquals("지하철이 떠나려고 해요.", saidSound(SoundProp.TRAIN, "지하철 탔어")!!.before)
    }
}
