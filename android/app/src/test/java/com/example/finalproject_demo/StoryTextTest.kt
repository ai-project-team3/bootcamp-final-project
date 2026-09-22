package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.PARTNERS
import com.example.finalproject_demo.demo.TEMPLATES
import com.example.finalproject_demo.demo.SNOW_BUDDIES
import com.example.finalproject_demo.demo.THEMES
import com.example.finalproject_demo.ui.HERO_ROWS
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.demo.heroImageName
import com.example.finalproject_demo.ui.ridingFrom
import com.example.finalproject_demo.ui.wavingFrom
import com.example.finalproject_demo.demo.dinoKind
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.chooseTemplate
import com.example.finalproject_demo.demo.partnerLine
import com.example.finalproject_demo.demo.partnerQuestion
import com.example.finalproject_demo.demo.pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 이야기 엔진 글자 검사 (v0.9) — 템플릿 × 함께 하는 사람 × 장소 × 답 조합을 모두 만들어
 * 남은 자리표시 · 겹친 문장부호 · 높임말 누락 같은 것을 찾는다. 결과 문장은 build/story_samples.txt 에 남긴다.
 */
class StoryTextTest {

    /**
     * 「골라서 만들기」가 고르게 하는 것 — **성별은 묻지 않는다** (9/21 요청으로 뺐다).
     *
     * 화면으로 보려면 에뮬레이터를 띄워야 하는데 이 기기에서는 그럴 수 없어서(트러블슈팅 6-12),
     * 줄 목록을 밖으로 꺼내 여기서 본다.
     */
    @Test
    fun theHeroBuilderAsksForLooksNotForGender() {
        val keys = HERO_ROWS.map { it.second }
        assertEquals(listOf("hair", "shirt", "eyes", "glasses", "bottom"), keys)
        assertTrue("성별을 다시 묻고 있다", HERO_ROWS.none { it.first.contains("성별") })
        assertTrue("남/여 선택지가 남아 있다",
            HERO_ROWS.none { row -> row.third.any { it.first == "남자" || it.first == "여자" } })

        // 줄마다 선택지가 셋이고, 고른 값이 그림 이름으로 이어진다
        HERO_ROWS.forEach { (name, key, opts) ->
            assertEquals("$name 줄의 선택지가 셋이 아니다", 3, opts.size)
            assertTrue("$key 줄에 빈 값이 있다", opts.all { it.first.isNotBlank() && it.second.isNotBlank() })
        }

        // 9/21에 구조가 바뀌었다 — 완성본 81장을 갈아 끼우던 것을 **몸 27장 + 얹는 안경**으로 바꿨다.
        // 그래서 그림 이름은 **안경을 빼고** 옷 3 × 하의 3 × 머리 3 = 27가지다.
        // (안경은 [HeroImage] 가 `gl_round` · `gl_square` 를 위에 얹는다 — 그림 이름에 들어가지 않는다)
        val hairs = HERO_ROWS.first { it.second == "hair" }.third.map { it.second }
        val shirts = HERO_ROWS.first { it.second == "shirt" }.third.map { it.second }
        val glasses = HERO_ROWS.first { it.second == "glasses" }.third.map { it.second }
        val bottoms = HERO_ROWS.first { it.second == "bottom" }.third.map { it.second }
        val names = mutableSetOf<String>()
        hairs.forEach { h ->
            shirts.forEach { c ->
                bottoms.forEach { b ->
                    val attr = HeroAttr(
                        hair = h, bottom = b,
                        shirt = androidx.compose.ui.graphics.Color(c.toLong(16) or 0xFF000000),
                    )
                    names += heroImageName(attr)
                }
            }
        }
        assertEquals("옷 3 × 하의 3 × 머리 3 = 27가지 몸 그림", 27, names.size)

        // ⚠️ **안경을 바꿔도 몸 그림 이름은 그대로여야 한다** — 안경이 그림에 박혀 있던 시절의 버그다
        glasses.forEach { g ->
            assertEquals(
                "안경($g)을 바꿨는데 몸 그림이 바뀐다",
                heroImageName(HeroAttr()),
                heroImageName(HeroAttr(glasses = g)),
            )
        }
        // 눈도 마찬가지 — 눈은 벡터로 얹는다
        listOf("round", "smile", "star").forEach { e ->
            assertEquals(
                "눈($e)을 바꿨는데 몸 그림이 바뀐다",
                heroImageName(HeroAttr()),
                heroImageName(HeroAttr(eyes = e)),
            )
        }
    }

    /**
     * 책이 **쪽에 적힌 대로 움직이는가** — 자막의 낱말로 자리와 몸짓을 정한다 (9/21).
     *
     * 에뮬레이터에서 "타고"(로켓 위에 올라탐)와 오탐 없음("흔들렸어요"인데 인사 안 함)은 눈으로 봤다.
     * 여기서는 **템플릿 5종의 모든 쪽 문장**을 돌려 판정이 어긋나지 않는지 본다 —
     * 화면으로는 한 갈래밖에 못 보기 때문이다.
     */
    @Test
    fun thePagePoseFollowsWhatTheCaptionSays() {
        // 타는 문장
        assertTrue(ridingFrom("지호는 로켓을 타고 반짝이는 우주로 떠났어요."))
        assertTrue(ridingFrom("트리케라톱스가 기차에 올라탔어요!"))
        assertFalse("타는 말이 없는데 태웠다", ridingFrom("지호는 놀이터에 갔어요."))

        // 인사하는 문장 — **손**이나 **인사**가 같이 있어야 한다
        assertTrue(wavingFrom("창밖을 보니 외계인 뿌뿌가 손을 흔들고 있었어요."))
        assertTrue(wavingFrom("모두 안녕! 하고 헤어졌어요."))
        // ⚠️ 오탐 — 흔들린 것이지 인사한 것이 아니다 (에뮬레이터에서 확인한 갈래)
        assertFalse("기차가 흔들린 걸 인사로 읽었다", wavingFrom("그런데 땅이 쿵쿵 울리고 기차가 흔들렸어요."))
        assertFalse("붙잡고 흔든 걸 인사로 읽었다",
            wavingFrom("외계인 뿌뿌가 거북이를 붙잡고 마구 흔들고 있었어요!"))

        // 템플릿 5종의 모든 쪽을 돌려도 터지지 않는다
        val s = DemoState()
        TEMPLATES.forEach { tpl ->
            tpl.pages.forEach { pg ->
                val line = pg.text(s)
                ridingFrom(line); wavingFrom(line)
                assertTrue("빈 쪽 문장: ${tpl.code}", line.isNotBlank())
            }
        }
    }

    /**
     * 장면 4 · 7을 건너뛰어도 **그 장소의 것**이 나온다 (9/21).
     * 바닷속에 "외계인 뿌뿌"가 서 있던 것을 에뮬레이터에서 보고 고쳤다.
     */
    @Test
    fun skippingASceneStillLeavesThePlaceConsistent() {
        THEMES.forEach { th ->
            val s = DemoState().apply { themeKey = th.key }
            // 장면을 건너뛴 자리에서 앱이 채우는 기본값
            val nc = if (s.newcomerKind in th.newcomers.map { it.value }) s.newcomerKind else th.defaultNewcomer
            assertTrue("${th.label}에 없는 새 친구: $nc", nc in th.newcomers.map { it.value })
            assertTrue("${th.label}에 없는 동행 친구", th.buddies.first() in th.buddies)
        }
    }

    /**
     * 같이 갈 친구는 **아이가 고른 장소의 것**이어야 한다.
     *
     * v0.11까지는 공룡 셋뿐이라 우주에 가도 바닷속에 가도 공룡이 로켓에 탔다 (9/21 지적).
     * 그림 · 소리 · 생김새 설명까지 장소를 따라가는지 함께 본다.
     */
    @Test
    fun theBuddyAlwaysBelongsToThePlaceTheChildChose() {
        THEMES.forEach { th ->
            val s = DemoState().apply { themeKey = th.key }
            assertEquals("${th.label}의 친구 후보가 테마와 다르다", th.buddies, s.buddies)
            assertTrue("${th.label}의 친구가 셋이 아니다", s.buddies.size == 3)
            s.buddies.forEach { b ->
                assertEquals("키로 못 찾는 친구: ${b.key}", b, dinoKind(b.key))
                assertTrue("그림 이름이 없다: ${b.key}", b.art.isNotBlank())
                assertTrue("소리가 없다: ${b.key}", b.sound.isNotBlank())
                assertTrue("생김새 설명이 없다: ${b.key}", b.look.isNotBlank())
                // 아이가 말하는 관형형 — "~ 거!" 앞에 붙는다. 종결형이면 "혼자서 빛나요 거!" 가 된다 (9/21)
                assertTrue("관형형이 없다: ${b.key}", b.said.isNotBlank())
                assertFalse("관형형이 종결형이다: ${b.key} = \"${b.said}\"", b.said.endsWith("요") || b.said.endsWith("다"))
            }
            // 기본값도 그 장소의 것이어야 한다 — 장면 7을 건너뛰어도 어긋나지 않게
            s.dinoKey = s.buddies.first().key
            assertTrue("기본 친구가 장소 밖이다", s.dino in s.buddies)
        }
        // "눈 오는 데"는 뼈대만 공룡 나라에서 가져오고 친구는 눈나라 친구다
        val snow = DemoState().apply { themeKey = "dino"; generatedBg = true }
        assertEquals(SNOW_BUDDIES, snow.buddies)
        assertTrue("눈나라 부름말이 아니다: ${snow.buddyCall}", "눈나라" in snow.buddyCall)
    }

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
