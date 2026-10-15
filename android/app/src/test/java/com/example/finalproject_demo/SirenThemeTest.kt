package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.missions.SaidSound
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.demo.missions.slot1Prop
import com.example.finalproject_demo.demo.missions.themeSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실기기(10-14) — 경찰관 이야기에서 아이가 「삐뽀삐뽀하면서 도둑 잡아다녀」라고 했더니 소리 미션이
 * 「소방차처럼 「삐뽀삐뽀!」…」로 나왔다. 자리 1 말에 소리 내는 것이 없으면 이야기 전체(카드 · 아이 답)의 주제로 부른다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SirenThemeTest {

    @Test
    fun aPoliceStorySirenIsAPoliceCar() {
        assertEquals("경찰차", themeSound(SoundProp.SIREN, "삐뽀삐뽀하면서 도둑 잡아다녀", "경찰관 경찰과 도둑 게임할 거야")?.word)
        assertEquals("구급차", themeSound(SoundProp.SIREN, "삐뽀삐뽀 소리가 났어", "병원에 갔어")?.word)
        val police = themeSound(SoundProp.SIREN, "삐뽀삐뽀", "경찰관")!!
        assertEquals("경찰차처럼 「삐뽀삐뽀!」 크게 소리 내 볼래?", police.ask)
        assertEquals("경찰차가 출동 준비를 하고 있어요.", police.before)
        assertEquals("prop_police_car", police.art)
    }

    @Test
    fun theChildsOwnMakerOrAFireStoryStays() {
        // the slot-1 words named the maker — saidSound / the prop decide, not the theme
        assertNull(themeSound(SoundProp.SIREN, "소방차가 삐뽀삐뽀 지나갔어", "경찰관"))
        assertNull(themeSound(SoundProp.SIREN, "구급차가 삐뽀삐뽀", "경찰관"))
        // a fire story stays the fire truck
        assertNull(themeSound(SoundProp.SIREN, "삐뽀삐뽀", "소방관 불을 껐어"))
        // no theme — the fire truck as before
        assertNull(themeSound(SoundProp.SIREN, "삐뽀삐뽀", "놀이터에 갔어"))
        // other sounds have no theme
        assertNull(themeSound(SoundProp.LION, "어흥", "경찰관"))
    }

    @Test
    fun theBookAsksForThePoliceCar() {
        val s = DemoState().apply {
            mode = StoryMode.COOP
            coopPick = CoopPick("job", "경찰관", "soon")
            problem = "삐뽀삐뽀하면서 도둑 잡아다녀."
            slots["problem"] = problem!!; slotBy["problem"] = "child"
            slots["solution"] = "경찰과 도둑 게임할 거야"; slotBy["solution"] = "child"
        }
        val p = s.slot1Prop()
        assertTrue("$p", p is SaidSound)
        assertEquals("경찰차", (p as SaidSound).word)
    }
}
