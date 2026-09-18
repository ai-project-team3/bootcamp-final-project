package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.PARTNERS
import com.example.finalproject_demo.demo.TEMPLATES
import com.example.finalproject_demo.demo.THEMES
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.chooseTemplate
import com.example.finalproject_demo.demo.partnerLine
import com.example.finalproject_demo.demo.partnerQuestion
import com.example.finalproject_demo.demo.pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 이야기 엔진 글자 검사 (v0.9) — 템플릿 × 함께 하는 사람 × 장소 × 답 조합을 모두 만들어
 * 남은 자리표시 · 겹친 문장부호 · 높임말 누락 같은 것을 찾는다. 결과 문장은 build/story_samples.txt 에 남긴다.
 */
class StoryTextTest {

    private val bad = listOf("{", "}", "null", "  ", "..", "요요", "!.", "?.", ".!", "에에", "를를", "이이 ", "는는", "께서께서", "에게에게")

    private fun state(theme: String, partner: String, snow: Boolean = false) = DemoState().apply {
        themeKey = theme
        partnerKey = partner
        if (snow) { placeLabel = "눈 오는 데"; generatedBg = true }
        newcomerKind = th.newcomers.first().value
        friendName = "뭉치"
        causeLine = "친구가 없어서 심심했어"
        solutionLine = "같이 별을 땄어요"
    }

    @Test
    fun allTemplatePagesReadCleanly() {
        val out = StringBuilder()
        var pages = 0
        val problems = mutableListOf<String>()
        for (t in TEMPLATES) for (p in PARTNERS) for (th in THEMES.map { it.key } + "snow") {
            val s = state(if (th == "snow") "dino" else th, p.key, th == "snow")
            s.templateKey = t.key
            // 템플릿 칸마다 가능한 답을 하나씩 돌려 가며 채운다
            val slotAnswers = (t.plot + t.ending).associateWith { slot ->
                BANK.filter { it.slot == slot }.flatMap { v -> v.answers(s).map { it.value } }.filter { it.isNotEmpty() }.distinct()
            }
            val maxN = slotAnswers.values.maxOf { it.size }.coerceAtLeast(1)
            val (_, partnerAnswers) = partnerQuestion(s)
            for (i in 0 until maxN) {
                s.slots.clear()
                slotAnswers.forEach { (k, vs) -> if (k != "resolve" && vs.isNotEmpty()) s.slots[k] = vs[i % vs.size] }
                slotAnswers["resolve"]?.getOrNull(i % (slotAnswers["resolve"]!!.size))?.split(":", limit = 3)?.let {
                    s.solutionKey = it[0]; s.solutionItem = it[1]; s.solutionLine = it[2]
                }
                s.partnerHelpLine = if (i % 3 == 2) null else partnerAnswers[i % partnerAnswers.size].value
                s.title = s.autoTitleFor()
                val book = (1..t.pages.size).map { s.bookCaption(it) }
                if (i == 0) out.append("\n## ${t.code} ${t.name} · ${p.name} · $th · 『${s.title}』\n").append(book.joinToString("\n") { "  $it" }).append('\n')
                (book + s.title!!).forEach { line ->
                    pages++
                    bad.filter { it in line }.forEach { b -> problems += "[${t.code}/${p.key}/$th] '$b' in: $line" }
                    if (p.honor && s.partnerHelpLine != null && line.contains(p.name) && !line.contains("께서") && !line.contains("께 ")) {
                        problems += "[${t.code}/${p.key}] 높임말 누락: $line"
                    }
                }
            }
        }
        // 함께 하는 사람의 말 · 질문
        for (p in PARTNERS) {
            val s = state("space", p.key)
            repeat(6) { out.append("Q(${p.name}): ${partnerQuestion(s).first}\n") }
            listOf("place", "cause", "shake", "drawn", "picked").forEach { out.append("  ${p.name} 대사: ${partnerLine(s, it)}\n") }
        }
        File("build").mkdirs()
        File("build/story_samples.txt").writeText(out.toString() + "\n\n# problems\n" + problems.joinToString("\n"))
        println("pages checked: $pages, problems: ${problems.size}")
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun templatesHaveSixToEightPagesAndMatchLevels() {
        TEMPLATES.forEach { assertTrue("${it.code} ${it.pages.size}", it.pages.size in 6..8) }
        assertEquals("E", chooseTemplate(Level.PICK, "lonely").first)
        assertEquals("C", chooseTemplate(Level.CHAIN, "play").first)
        assertEquals("D", chooseTemplate(Level.CHAIN, "lost").first)
        assertEquals("A", chooseTemplate(Level.REASON, "lonely").first)
        assertEquals("G", chooseTemplate(Level.REASON, "prank").first)
        assertEquals("직업 체험", chooseTemplate(Level.CHAIN, "hurt").second)
        assertEquals("교훈", chooseTemplate(Level.REASON, "strong").second)
    }

    @Test
    fun bankCoversEveryLevelWithVariedDummyAnswers() {
        val s = DemoState()
        val report = StringBuilder()
        val slots = BANK.map { it.slot }.distinct()
        for (slot in slots) {
            val vs = BANK.filter { it.slot == slot }
            val answers = vs.sumOf { it.answers(s).size }
            report.append("$slot: 변형 ${vs.size}개 · 더미 답 ${answers}개\n")
            vs.forEach { v ->
                val a = v.answers(s)
                assertTrue("${v.id} 답이 ${a.size}개", a.size >= 4)
                assertTrue("${v.id} 수준 1~3 답이 섞여 있지 않음", a.map { it.lv }.toSet().size >= 2)
            }
        }
        // 템플릿이 쓰는 slot은 어느 수준에서도 질문이 나와야 한다
        TEMPLATES.flatMap { it.plot + it.ending }.distinct().filter { it != "reflect" }.forEach { slot ->
            Level.entries.forEach { lv -> assertTrue("$slot @ $lv", BANK.any { it.slot == slot && lv in it.levels }) }
        }
        File("build").mkdirs()
        File("build/bank_report.txt").writeText(report.toString())
    }

    @Test
    fun nextStoryAvoidsLastStorysQuestion() {
        val s = DemoState()
        s.level = Level.CHAIN
        val first = s.pick("cause").id
        s.askedThisStory.clear()
        val second = s.pick("cause").id
        assertTrue("같은 질문이 연달아 나옴: $first", first != second)
    }

    @Test
    fun sharedSlotsUseOnlyTheCurrentTemplatesQuestions() {
        for (t in TEMPLATES) {
            val s = DemoState()
            s.templateKey = t.key
            s.level = t.level
            repeat(10) {
                s.askedThisStory.clear()
                val v = s.pick("resolve")
                assertTrue("${t.key} 템플릿에서 ${v.id}", v.id.startsWith(t.key.lowercase() + "_"))
            }
        }
    }
}
