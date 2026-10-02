package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.DIARY_STEPS
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPartPack
import com.example.finalproject_demo.demo.pieceNameFrom
import com.example.finalproject_demo.demo.you
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * **협업이 아닌 모드의 질문이 한 글자도 바뀌지 않았나** (10-02 · 협업 질문 업그레이드의 회귀 기준).
 *
 * 동화 질문 은행([BANK]) · 일기 걸음([DIARY_STEPS]) · 협업의 앱 질문(템플릿 없이, [COOP_STEPS]) ·
 * 공용 도구(이름 떼기 · 나→너)의 결과를 펼쳐 기준 파일과 견준다. 무작위가 섞인 줄은 두 번 펼쳐 다르면 뺀다.
 * 기준을 새로 찍을 때만 환경 변수 `GOLDEN_RECORD=1` 로 돌린다 — 협업 작업으로 이 파일이 바뀌면 안 된다.
 */
class NonCoopQuestionsGoldenTest {
    private val golden = File("src/test/resources/golden/non_coop_questions.txt")

    private fun state(mode: StoryMode, level: Level) = DemoState().apply { this.mode = mode; this.level = level }

    private fun dump(): String = buildString {
        fun line(key: String, f: () -> String) {
            // 선택지 순서를 섞는 질문이 있다 — 여러 번 펼쳐 한 번이라도 다르면 무작위로 친다
            val seen = (1..12).map { runCatching(f).getOrElse { e -> "!${e.javaClass.simpleName}" } }.toSet()
            appendLine(if (seen.size == 1) "$key = ${seen.single()}" else "$key = <random>")
        }
        BANK.forEach { v -> v.levels.sortedBy { it.rank }.forEach { lv ->
            val s = state(StoryMode.STORY, lv)
            line("story ${v.id} ${lv.name} text") { v.text(s) }
            line("story ${v.id} ${lv.name} easier") { v.easier(s) }
            v.hint?.let { h -> line("story ${v.id} ${lv.name} hint") { h(s) } }
        } }
        listOf(StoryMode.DIARY to DIARY_STEPS, StoryMode.COOP to COOP_STEPS).forEach { (mode, steps) ->
            Level.entries.forEach { lv ->
                val s = state(mode, lv)
                steps.forEach { st -> line("${mode.name} ${st.bookKey} ${lv.name} rungs") { st.rungs(s).joinToString(" | ") } }
            }
        }
        listOf("유치원이야", "수영장이요", "바다", "음 그러니까 공원", "소방차", "곰", "엄마랑 나", "배고파", "몰라",
            "우리 집이야!", "아니, 블록이야", "이건 강아지야", "고양이야", "큰 소방서에서 일해")
            .forEach { t -> line("pieceNameFrom $t") { pieceNameFrom(Reply.Spoke(t)) ?: "null" } }
        listOf("엄마랑 나", "내 동생", "나무", "나비", "저랑 아빠").forEach { t -> line("you $t") { you(t) } }
    }

    /** 줄바꿈(CRLF · LF)은 견주지 않는다 — 깃이 체크아웃할 때 바꿀 수 있다 */
    private fun String.lf() = replace("\r\n", "\n")

    @Test
    fun nonCoopQuestionsAreUnchanged() {
        val now = dump()
        if (System.getenv("GOLDEN_RECORD") == "1") { golden.parentFile.mkdirs(); golden.writeText(now); return }
        // 어느 한쪽이라도 무작위로 표시된 줄은 견주지 않는다 (나머지는 한 글자도 같아야 한다)
        fun lines(t: String) = t.lf().lines().filter { it.isNotBlank() }.associate { it.substringBefore(" = ") to it.substringAfter(" = ") }
        val want = lines(golden.readText())
        val got = lines(now)
        assertEquals("질문 목록이 달라졌다", want.keys, got.keys)
        val changed = want.keys.filter { k -> want[k] != "<random>" && got[k] != "<random>" && want[k] != got[k] }
            .map { k -> "$k · 기준: ${want[k]} · 지금: ${got[k]}" }
        assertEquals("협업이 아닌 모드의 질문 · 공용 도구 결과가 바뀌었다 (기준: ${golden.path})", emptyList<String>(), changed)
    }

    @Test
    fun theCoopPackStaysOutsideCoop() {
        COOP_STEPS.forEach { st ->
            listOf(StoryMode.STORY, StoryMode.DIARY).forEach { m ->
                val s = state(m, Level.CHAIN).apply { coopPick = com.example.finalproject_demo.demo.CoopPick("job", "소방관", "soon") }
                assertNull("$m ${st.bookKey}", s.coopPartPack(st))
            }
        }
    }
}
