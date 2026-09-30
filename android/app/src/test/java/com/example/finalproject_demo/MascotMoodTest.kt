package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.demo.Scene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 마스코트가 상황마다 다르게 움직이는가 (09-29) — 얼굴은 `Mood` 신호만 보고 움직이므로 **신호가 제때 켜지는지**를 본다.
 *
 *   놀람    「쉿」 「앗」 으로 시작하는 마스코트 말
 *   기다림  아이가 말이 없을 때 켜지고, 그 질문이 끝나면 꺼진다
 *   성공    아이가 답했을 때 · 미션을 해냈을 때
 *   생각 중 신호가 따로 없다 — 화면이 `Stage.Making` 이면 얼굴이 알아서 생각한다
 */
class MascotMoodTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) {
            while (!cond()) delay(3)
            true
        }

    private suspend fun Director.tap(part: String): Boolean {
        repeat(14) {
            if (await(2_500) { s.buttons.any { b -> part in b.label } } == null) return false
            s.buttons.first { part in it.label }.onClick()
            if (await(500) { s.buttons.none { b -> part in b.label } } != null) return true
        }
        return false
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + sup)
        val d = Director(scope)
        d.s.speed = 0.01
        try {
            block(d)
        } finally {
            sup.cancel()
        }
    }

    @Test
    fun surpriseComesFromHowTheLineStarts() = run { d ->
        val s = d.s
        d.say("안녕! 오늘은 무슨 일이 있었어?")
        assertEquals(Mood.NONE, s.mood)

        val before = s.moodId
        d.say("쉿, 창문에서 뭔가 움직였어! 누구일까?")
        assertEquals(Mood.SURPRISED, s.mood)
        assertNotEquals("같은 놀람이 또 와도 다시 터져야 한다", before, s.moodId)

        d.say("앗, 이게 뭐지?")
        assertEquals(before + 2, s.moodId)

        // 아이 말은 마스코트가 놀랄 일이 아니다
        s.mood = Mood.NONE
        d.say("쉿 조용히 해", who = s.childName)
        assertEquals(Mood.NONE, s.mood)
    }

    @Test
    fun silenceMakesOttoWaitAndAnAnswerMakesItCheer() = run { d ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue("시작 화면이 안 떴다", d.tap("오늘 있었던 일로"))
        assertTrue("그림일기로 바로 안 왔다", await { s.scene == Scene.DIARY } != null)
        if (await(2_000) { s.buttons.any { "그림 없이 이야기할래" in it.label } } != null) d.tap("그림 없이 이야기할래")

        // `tap` 을 쓰지 않는다 — 사다리 칸마다 「여전히 대답 없음」 이 떠서 버튼이 사라질 때까지 누르면 질문 끝까지 넘어간다.
        // 감독이 말을 마치기 전 입력은 버리므로(`drain`) 기다림이 켜질 때까지만 다시 누른다
        assertTrue("대답 없음 버튼이 없다", await { s.buttons.any { "🤐 대답 없음" in it.label } } != null)
        val waiting = await {
            if (s.mood != Mood.WAITING) s.buttons.firstOrNull { "🤐 대답 없음" in it.label }?.onClick()
            s.mood == Mood.WAITING
        }
        assertTrue("말이 없는데 오또가 기다리지 않는다 (기분=${s.mood})", waiting != null)

        val before = s.moodId
        // 다음 질문에도 🗣 버튼이 떠서 `tap`(버튼이 사라질 때까지)은 못 쓴다 — 반응이 올 때까지만 누른다
        val cheered = await {
            if (s.moodId == before) s.buttons.firstOrNull { "🗣" in it.label }?.onClick()
            s.moodId > before
        }
        assertTrue("답할 수가 없다 (버튼=${s.buttons.map { it.label }})", cheered != null)
        assertEquals("아이가 답했는데 오또가 기뻐하지 않는다", Mood.CHEER, s.mood)
        assertTrue("질문이 끝났는데 기다림이 남아 있다", await { s.mood != Mood.WAITING } != null)
    }
}
