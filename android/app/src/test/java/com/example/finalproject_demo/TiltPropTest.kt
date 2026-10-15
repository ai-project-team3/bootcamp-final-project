package com.example.finalproject_demo

import com.example.finalproject_demo.demo.missions.FixProp
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.fixPropIn
import com.example.finalproject_demo.demo.missions.forMission
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 기울여 굴리기(D4) 소품 넓히기 (10-14) — 공 하나뿐이던 D4 가 구슬 · 눈덩이도 굴린다.
 * - 아이가 말한 굴리는 것이 먼저다: 「구슬을 굴렸어」가 공의 「굴렸」에 먹히지 않는다
 * - 순서로 돌아온 D4 는 구슬 — 축구공 · 골대는 「공놀이를 했다」는 이야기를 세운다
 */
class TiltPropTest {

    @Test
    fun theRolledThingTheChildSaidIsTheProp() {
        listOf(
            ("구슬을 데굴데굴 굴렸어" to "") to FixProp.MARBLE,
            ("구슬치기 했어" to "") to FixProp.MARBLE,
            ("" to "구슬이 굴러가 버렸어") to FixProp.MARBLE,
            ("눈덩이를 굴렸어" to "") to FixProp.SNOWBALL,
            ("다 같이 눈사람을 만들었어" to "") to FixProp.SNOWBALL,
            ("" to "눈사람이 녹았어") to FixProp.SNOWBALL,
            // the ball is unchanged
            ("공을 데굴데굴 굴렸어" to "") to FixProp.BALL,
            ("" to "공이 굴러가서 없어졌어") to FixProp.BALL,
        ).forEach { (t, want) -> assertEquals(t.toString(), want, fixPropIn(t.first, t.second)) }
        // every one of them is the same tilt mission
        listOf(FixProp.MARBLE, FixProp.BALL, FixProp.SNOWBALL).forEach { assertEquals(MissionId.D4, it.mission) }
    }

    @Test
    fun aRotatedRollIsTheMarble() {
        assertEquals(FixProp.MARBLE, FixProp.forMission(MissionId.D4))
        assertEquals("구슬 굴린 손", FixProp.MARBLE.badge)
        assertEquals("눈사람 만든 손", FixProp.SNOWBALL.badge)
    }

    @Test
    fun snowAloneOrEyesAreNoSnowball() {
        // 「눈」 is snow and eye — only the snowball and snowman words pick it
        listOf("눈이 왔어", "눈을 감았어", "눈물이 났어").forEach { assertEquals(it, null, fixPropIn(it, "")) }
    }
}
