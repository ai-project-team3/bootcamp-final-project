package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.mission2
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.FixProp
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.demo.soonTense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-06 실기기(#172 · 소방관 곧 체험해요) — 서버 쪽은 「물을 뿌릴 거예요」인데 앱이 붙인 미션 결과는 「불이 다 꺼졌어요」였다.
 * 곧 해요 책의 결과 문장은 「-ㄹ 거예요」로, 소품 전부가 바뀌어야 한다.
 */
class CoopTenseTest {
    private val past = Regex("(었|았|했)어요")

    @Test
    fun theLinesSeenOnThePhoneBecomeFuture() {
        assertEquals("소방차가 힘차게 출동할 거예요.", soonTense("소방차가 힘차게 출동했어요."))
        assertEquals("불이 다 꺼질 거예요.", soonTense("불이 다 꺼졌어요."))
        assertEquals("모래가 사라질 거예요.", soonTense("모래가 사라졌어요."))
        assertEquals("블록 탑이 높이 설 거예요.", soonTense("블록 탑이 높이 섰어요."))
        assertEquals("수도꼭지를 빙글빙글 돌려 꽉 잠글 거예요.", soonTense("수도꼭지를 빙글빙글 돌려 꽉 잠갔어요."))
        assertEquals("공을 데굴데굴 굴려 골대에 넣을 거예요.", soonTense("공을 데굴데굴 굴려 골대에 넣었어요."))
        assertEquals("촛불을 후~ 불 거예요.", soonTense("촛불을 후~ 불었어요."))
        assertEquals("친구는 엄마와 아빠에게 반짝이는 별을 건네줄 거예요.", soonTense("친구는 엄마와 아빠에게 반짝이는 별을 건네주었어요."))
        assertEquals("반창고를 붙여 줄 거예요", soonTense("반창고를 붙여 주었어요"))
    }

    @Test
    fun everyPropResultLineConverts() {
        val lines = BlowProp.entries.flatMap { listOf(it.result, it.did) } +
            FixProp.entries.flatMap { listOf(it.result, it.did) } +
            SoundProp.entries.flatMap { listOf(it.result, it.did) } +
            listOf("gem", "invite", "balloon", "block", "picturebook", "bandaid", "star").map { DemoState().apply { solutionItem = it }.mission2().give + "." } +
            listOf("그림 조각을 모두 맞춰 한 장면을 완성했어요.", "먼지가 사라졌어요.")
        lines.forEach { line ->
            val out = soonTense(line)
            assertTrue("「$line」 → 「$out」", out.trimEnd('.', '!') .endsWith("거예요"))
            assertFalse("「$line」 → 「$out」 에 과거형이 남았다", past.containsMatchIn(out))
        }
    }

    @Test
    fun aLineTheRulesCannotReadStaysAsItIs() {
        // 규칙에 안 걸리면 틀린 꼴보다 원문 — 현재형 · 명사 끝 · 이미 미래형
        listOf("불이 활활 타오르고 있어요.", "슛!", "물을 뿌릴 거예요.", "").forEach { assertEquals(it, soonTense(it)) }
    }
}
