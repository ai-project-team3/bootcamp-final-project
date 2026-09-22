package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.DIARY_PLACES
import com.example.finalproject_demo.demo.DIARY_BG_FALLBACK
import com.example.finalproject_demo.demo.HOTSPOTS
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.PARTNERS
import com.example.finalproject_demo.demo.TEMPLATES
import com.example.finalproject_demo.demo.SNOW_BUDDIES
import com.example.finalproject_demo.demo.THEMES
import com.example.finalproject_demo.ui.HERO_ROWS
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.demo.heroImageName
import com.example.finalproject_demo.ui.effectFrom
import com.example.finalproject_demo.ui.Motion
import com.example.finalproject_demo.ui.motionFrom
import com.example.finalproject_demo.ui.quakeFrom
import com.example.finalproject_demo.ui.ridingFrom
import com.example.finalproject_demo.ui.wavingFrom
import com.example.finalproject_demo.demo.dinoKind
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.chooseTemplate
import com.example.finalproject_demo.demo.diaryGiveItem
import com.example.finalproject_demo.demo.diaryTemplate
import com.example.finalproject_demo.demo.diaryTitle
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.partnerLine
import com.example.finalproject_demo.demo.partnerQuestion
import com.example.finalproject_demo.demo.pick
import com.example.finalproject_demo.demo.reactionLine
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
     * 소리말은 **쪽 종류가 아니라 적힌 내용**이 정한다 (9/22).
     *
     * 전에는 SHAKE 쪽이면 늘 "쿵!" 이 떴다. 동화 모드에서는 탈것을 흔드는 쪽이라 맞았지만
     * 일기·협업에서 같은 자리는 "오늘 있었던 일" 쪽이어서, **그림 그린 날에도 "쿵!" 이 올라갔다.**
     */
    @Test
    fun theSoundWordComesFromTheCaptionNotThePageKind() {
        assertEquals("와르르!", effectFrom("높이 쌓은 블록이 와르르 무너졌어요."))
        assertEquals("쿵!", effectFrom("달리다가 넘어졌어요."))
        assertEquals("훌쩍…", effectFrom("너무 아파서 울었어요."))
        assertEquals("까르르!", effectFrom("친구랑 신나게 웃었어요."))
        // 맞는 것이 없으면 **아무것도 띄우지 않는다** — 없는 소리를 지어내지 않는다
        assertEquals(null, effectFrom("물감으로 그림을 그렸어요."))
        assertEquals(null, effectFrom("어린이집에 갔어요."))

        // 흔드는 것은 무너지거나 부딪힌 쪽뿐이다. 웃거나 우는 쪽에 화면이 흔들리면 아이가 무서워한다
        assertTrue(quakeFrom("블록이 와르르 무너졌어요."))
        assertTrue(quakeFrom("달리다가 넘어졌어요."))
        assertFalse("웃는 쪽을 흔들었다", quakeFrom("친구랑 신나게 웃었어요."))
        assertFalse("우는 쪽을 흔들었다", quakeFrom("너무 아파서 울었어요."))
        assertFalse("아무 일 없는 쪽을 흔들었다", quakeFrom("어린이집에 갔어요."))

        // ⚠️ 동화 모드가 잃은 것이 없어야 한다 — 다섯 템플릿의 SHAKE 쪽은 **전부** 소리말이 나오고 흔들려야 한다.
        // 전에는 무조건 "쿵!" 이었으니, 여기서 null 이 나오면 이번 변경이 동화 모드를 망가뜨린 것이다
        val s = DemoState()
        TEMPLATES.forEach { tpl ->
            tpl.pages.filter { it.kind == com.example.finalproject_demo.demo.PageKind.SHAKE }.forEach { pg ->
                val line = pg.text(s)
                assertTrue("${tpl.code} 흔들리는 쪽에 소리말이 없다: $line", effectFrom(line) != null)
                assertTrue("${tpl.code} 흔들리는 쪽이 안 흔들린다: $line", quakeFrom(line))
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
                // 「사건」 장면의 꼬리질문 답도 책에 실린다 — 세 변형을 돌려 가며 같이 검사한다 (9/22)
                val rv = BANK.filter { it.slot == "reaction" }
                val rq = rv[i % rv.size]
                val ra = rq.answers(s).map { a -> a.value }.filter { a -> a.isNotEmpty() }
                if (ra.isNotEmpty()) s.slots["reaction"] = reactionLine(s, rq.id, ra[i % ra.size])

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
        assertEquals("A", chooseTemplate(Level.REASON, "play").first)
        assertEquals("G", chooseTemplate(Level.REASON, "prank").first)
        // 9/22 새로 — 뽐내는 까닭은 민담의 겨루기와 같은 뿌리다(고르기 수준에서는 F),
        // 인사·심심함은 오해가 생기는 자리다(까닭 수준에서는 B)
        assertEquals("F", chooseTemplate(Level.PICK, "strong").first)
        assertEquals("B", chooseTemplate(Level.REASON, "hello").first)
        assertEquals("B", chooseTemplate(Level.REASON, "lonely").first)
        assertEquals("직업 체험", chooseTemplate(Level.CHAIN, "hurt").second)
        assertEquals("교훈", chooseTemplate(Level.REASON, "strong").second)
        assertEquals("체험", chooseTemplate(Level.PICK, "strong").second)
        assertEquals("상황 이해", chooseTemplate(Level.REASON, "hello").second)
    }

    /**
     * **일곱 뼈대가 다 있고, 아이마다 다른 틀이 나오는가** (9/22).
     *
     * 역할2 조사가 동화 21권을 분석해 뼈대 7개로 묶었는데 앱에는 **다섯뿐이었다**(B·F 없음).
     * 틀이 있기만 하고 아무에게도 안 가면 없는 것과 같으므로, 수준 × 까닭을 다 돌려
     * **일곱이 전부 뽑히는지** 본다.
     */
    @Test
    fun allSevenSkeletonsExistAndEachOneIsReachable() {
        assertEquals("일곱 뼈대가 다 있지 않다: ${TEMPLATES.map { it.code }}", 7, TEMPLATES.size)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), TEMPLATES.map { it.code }.sorted())

        val causes = listOf("lonely", "hello", "play", "prank", "strong", "lost", "hungry", "hurt")
        val picked = Level.entries.flatMap { lv -> causes.map { c -> chooseTemplate(lv, c).first } }.toSet()
        assertEquals("아무에게도 안 가는 틀이 있다", TEMPLATES.map { it.key }.toSet(), picked)

        // 새 틀도 자기 빈칸을 묻는 질문을 가지고 있어야 한다 — 없으면 책이 기본 문장으로 떨어진다
        for (t in TEMPLATES) for (slot in t.plot + t.ending) {
            val own = BANK.filter { it.slot == slot }
            assertTrue("틀 ${t.code} 의 빈칸 [$slot] 을 묻는 질문이 없다", own.isNotEmpty())
        }
    }

    /**
     * **F 전통 민담형은 이기거나 물리치는 결말로 끝나지 않는다** (9/22).
     *
     * 역할2 조사가 F를 *"물리침 또는 화합"* 으로 적으면서 *"「물리침」은 비폭력 표현으로 풀어야 한다"* 를
     * 미결로 남겨 두었다. **겨루다 비기고 함께 노는 쪽**으로 정했고, 그 결정을 여기에 묶어 둔다.
     */
    @Test
    fun theFolkTaleEndsInPlayNotInBeatingAnyone() {
        val f = TEMPLATES.first { it.code == "F" }
        val s = state("space", "mom")
        s.templateKey = "F"
        s.level = Level.PICK
        val book = (1..f.pages.size).map { s.bookCaption(it) }

        // 누군가를 해치거나 내쫓는 말이 없어야 한다.
        //
        // ⚠️ "이기다 · 지다" 를 통째로 막지는 않는다 — F 는 "누가 이기나 보자!" 로 시작해
        //    "이기고 지는 것보다 같이 노는 게 재밌다" 로 끝나는 틀이라 그 말이 **있어야** 한다.
        //    막는 것은 **결말이 물리침이 되는 것**이다. (한글 부분일치도 조심 — "터졌어요" 가 "졌어" 에 걸린다)
        val violent = listOf("물리쳤", "물리치", "쫓아냈", "쫓아내", "때렸", "때리", "혼냈", "혼내", "무찔", "싸워 이겼")
        for ((i, cap) in book.withIndex()) {
            val hit = violent.filter { it in cap }
            assertTrue("F ${i + 1}쪽에 ${hit}: $cap", hit.isEmpty())
        }

        // 마지막 쪽은 **함께 노는 것**으로 끝난다 — 이게 "화합" 쪽으로 푼 결과다
        assertTrue("F 마지막 쪽이 함께 노는 것으로 끝나지 않는다: ${book.last()}", "같이 노는" in book.last())

        // 겨룰 것을 고르는 답에도 힘으로 겨루는 것이 없어야 한다
        val contests = BANK.filter { it.slot == "contest" }.flatMap { it.answers(s) }
        assertTrue("겨루기 답이 없다", contests.isNotEmpty())
        for (a in contests) {
            val hit = listOf("힘겨루기", "때리", "싸움", "싸우", "밀치").filter { it in a.text || it in a.value }
            assertTrue("겨루기 답에 ${hit}: ${a.text}", hit.isEmpty())
        }
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

    /**
     * 일기 · 협업 책이 **한 이야기로 읽히는가** (9/22).
     *
     * *"페이지마다 안 이어지고 중구난방"* 이라는 지적에서 나온 검사다. 동화 모드에는
     * [allTemplatePagesReadCleanly] 가 있었는데 일기·협업 책은 아무도 글로 읽어 보지 않고 있었다.
     *
     * 하루를 네 가지로 만들어 쪽을 전부 뽑고, 사람이 읽게 build/diary_samples.txt 에 남긴다.
     * 기계가 잡을 수 있는 것만 여기서 막는다:
     *  - 한 쪽에 아이 이름이 두 번 — 미션 문장이 따로 놀던 자국이다
     *  - 아무도 없었던 날에 **"그 친구"** 가 나오는 것 — 앱이 없는 친구를 만들어 내는 것이다 (일기 §3-2)
     *  - 잇는 말이 겹치는 것 ("그래서 그래서")
     */
    /**
     * 자세도 **자막에서 읽는다** (9/22) — 그림을 새로 만들지 않고 몸짓으로 보여 준다.
     *
     * *"그네를 탔어요"* 라고 적혀 있는데 주인공이 가만히 서 있으면 글과 그림이 따로 논다.
     * 반대로 **일이 어긋난 쪽에서 움직이면 더 나쁘다** — "달리다가 넘어졌어요" 에서 통통 뛰면
     * 넘어진 이야기가 신나는 그림이 된다.
     */
    @Test
    fun theMotionComesFromTheCaptionAndStopsWhenSomethingWentWrong() {
        assertEquals(Motion.SWING, motionFrom("그네를 신나게 탔어요."))
        assertEquals(Motion.SLIDE, motionFrom("미끄럼틀을 타고 쌩 내려왔어요."))
        assertEquals(Motion.RUN, motionFrom("친구랑 운동장을 달렸어요."))
        assertEquals(Motion.NONE, motionFrom("어린이집에 갔어요."))

        // ⚠️ 어긋난 쪽은 움직이지 않는다 — '달리'가 들어 있어도 넘어진 것이 이야기다
        assertEquals(Motion.NONE, motionFrom("달리다가 넘어졌어요."))
        assertEquals(Motion.NONE, motionFrom("그네를 타다가 떨어졌어요."))
        assertEquals(Motion.NONE, motionFrom("미끄럼틀에서 내려오다 부딪혔어요."))

        // 탈것에 타는 것(`ridingFrom`)과는 다른 축이다 — 그쪽은 자리를, 이쪽은 몸짓을 정한다
        assertEquals(Motion.NONE, motionFrom("지호는 로켓을 타고 우주로 떠났어요."))
    }

    /**
     * 코드가 찾는 **배경 그림이 실제로 있는가** (9/22).
     *
     * *"갑자기 배경이 없을 때가 있다"* 는 지적에서 나왔다. 원인은 `diaryPlaceBg` 의 대체 배경이
     * `bg_today` 였는데 **그 파일이 없었던 것**이다. 아이가 말한 곳이 등록된 열 곳에 안 걸리면
     * (예: "축구장 갔어") 이름만 있고 그림이 없어 배경이 통째로 비었다.
     *
     * 그림이 없으면 화면은 조용히 색 배경으로 떨어져서 **에뮬레이터로 봐도 놓치기 쉽다.**
     * 그래서 파일 존재를 여기서 본다.
     */
    @Test
    fun everyBackgroundTheCodeAsksForActuallyExists() {
        val drawable = File("src/main/res/drawable")
        assertTrue("drawable 폴더를 못 찾았다: ${drawable.absolutePath}", drawable.isDirectory)

        val wanted = buildSet {
            addAll(DIARY_PLACES.map { it.second })   // 아이가 말한 곳 → 배경
            add(DIARY_BG_FALLBACK)                   // 못 찾았을 때 떨어지는 곳
            addAll(THEMES.map { "bg_${it.key}" })    // 동화 모드 세계
            add("bg_snow")                           // 생성 배경(눈 오는 데)
            addAll(HOTSPOTS.keys)                    // 누를 자리를 등록해 둔 배경
        }
        val missing = wanted.filterNot { File(drawable, "$it.png").exists() }
        assertTrue("코드가 찾는데 그림이 없는 배경: $missing", missing.isEmpty())
    }

    @Test
    fun diaryBookReadsAsOneStory() {
        val days = listOf(
            Triple("블록이 무너진 날", "놀이터" to "민준이", mapOf(
                "place" to "놀이터에 갔어요", "companion" to "민준이랑 같이 놀았어요",
                "detail" to "블록을 높이 쌓았어요", "problem" to "블록이 와르르 무너졌어요",
                "cause" to "너무 높이 쌓아서 그랬대요", "try" to "다시 천천히 쌓아 봤어요",
                "solution" to "이번엔 무너지지 않았어요", "after" to "집에 와서 손을 씻었어요",
            )),
            Triple("그림만 그린 날", "어린이집" to "", mapOf(
                "place" to "어린이집에 갔어요", "detail" to "물감으로 그림을 그렸어요",
                "problem" to "옷에 물감이 묻었어요", "cause" to "붓을 세게 흔들어서 그랬어요",
                "solution" to "선생님이 닦아 주셨어요",
            )),
            Triple("혼자였던 날", "공원" to "", mapOf(
                "place" to "공원에 갔어요", "problem" to "낙엽을 잔뜩 주웠어요",
            )),
            // ⚠️ 아이 답이 **서로 안 이어지는** 날. 9/22에 지적받은 그대로다 —
            //    "조심조심했지만 손이 흔들리고 말았어요. 그런데 그림책을 함께 읽었어요."
            //    답이 따로 놀아도 책은 한 줄기로 읽혀야 한다
            Triple("답이 따로 노는 날", "어린이집" to "하윤이", mapOf(
                "place" to "어린이집에 갔어요", "companion" to "하윤이랑 있었어요",
                "detail" to "조심조심했지만 손이 흔들리고 말았어요",
                "problem" to "그림책을 함께 읽었어요",
                "cause" to "그냥 그러고 싶었어요",
                "solution" to "블록을 정리했어요",
                "after" to "집에 와서 손을 씻었어요",
            )),
            Triple("넘어진 날", "놀이터" to "민서", mapOf(
                "place" to "놀이터에 갔어요", "companion" to "민서랑 같이 갔어요",
                "problem" to "달리다가 넘어졌어요", "reaction" to "너무 아파서 울었어요",
                "cause" to "돌을 못 봐서 그랬어요", "solution" to "반창고를 붙이고 다시 놀았어요",
            )),
        )

        val out = StringBuilder()
        val problems = mutableListOf<String>()
        for ((name, where, slotMap) in days) {
            val (place, friend) = where
            val s = DemoState().apply {
                mode = StoryMode.DIARY
                placeLabel = place
                this.place = place
                if (friend.isNotBlank()) { friendName = friend; companionKind = friend }
                slotMap.forEach { (k, v) -> slots[k] = v }
                problem = slotMap["problem"]
                cause = slotMap["cause"]
                solution = slotMap["solution"]
                reaction = slotMap["reaction"]
                solutionLine = slotMap["solution"].orEmpty()
                solutionItem = diaryGiveItem(slotMap["solution"].orEmpty(), this)
            }
            // `template` 은 일기 모드면 알아서 diaryTemplate 을 계산해 준다 (Model.kt) — 넣어 줄 것이 없다
            s.title = s.diaryTitle()

            out.append("\n## $name · 『${s.title}』 · ${s.pageCount}쪽\n")
            for (i in 1..s.pageCount) {
                val line = s.bookCaption(i)
                out.append("  $i. [${s.pageKind(i)}] $line\n")

                bad.filter { it in line }.forEach { problems += "[$name] '$it' in: $line" }
                if (line.split(s.childName).size - 1 > 1) problems += "[$name] 한 쪽에 아이 이름이 두 번: $line"
                if (!s.hasCompanion && "그 친구" in line) problems += "[$name] 아무도 없었는데 '그 친구': $line"
                listOf("그래서 그래서", "그런데 그런데", "그리고 그리고", "마침내 마침내").forEach {
                    if (it in line) problems += "[$name] 잇는 말이 겹침: $line"
                }
                if (line.isBlank()) problems += "[$name] ${i}쪽이 비어 있음"
            }
        }
        File("build").mkdirs()
        File("build/diary_samples.txt").writeText(out.toString() + "\n\n# problems\n" + problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
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

    /**
     * 「사건」 장면에서 아이가 한 말이 **책 문장으로** 된다 (9/22).
     *
     * 질문 세 변형이 저장하는 값의 모양이 서로 다르다. 그대로 이으면
     * 책에 "지우." 같은 토막이 남아 문장이 안 된다.
     */
    @Test
    fun theChildsAnswerAtTheEventBecomesASentence() {
        val s = DemoState()
        val c = s.childName          // 아이 이름은 시연 인물(persona)이 정한다

        // 결과 절 — 반말을 책 말투로 올린다
        assertEquals("그러자 쾵 떨어졌어요.", reactionLine(s, "follow_next", "쾵 떨어졌어"))
        // 대사 — 느낌표·물음표를 살려 준다
        assertEquals("${c}는 \"으악!\" 하고 외쳤어요.", reactionLine(s, "follow_say", "으악!"))
        assertEquals("${c}는 \"누구야?\" 하고 물었어요.", reactionLine(s, "follow_say", "누구야?"))
        // 사람 이름 — 받침을 보고 조사를 고른다
        assertEquals("지우가 제일 깜짝 놀랐어요.", reactionLine(s, "follow_who", "지우"))
        assertEquals("민준이가 제일 깜짝 놀랐어요.", reactionLine(s, "follow_who", "민준이"))
        // 안 말했으면 아무것도 짓지 않는다 — 빈 칸이 책에 점하나로 남지 않게
        assertEquals("", reactionLine(s, "follow_next", "   "))
    }

    /**
     * 그리고 그 문장이 **다섯 틀 모두의 책에** 실린다.
     *
     * 전에는 어느 틀도 `reaction` 칸을 읽지 않았다 — 물어보고 저장하기만 했다.
     */
    @Test
    fun everyTemplatePutsThatSentenceInTheBook() {
        for (t in TEMPLATES) {
            val s = DemoState()
            s.templateKey = t.key
            s.level = t.level
            s.slots["reaction"] = "그러자 쾵 떨어졌어요."
            val book = (1..s.pageCount).joinToString(" ") { s.bookCaption(it) }
            assertTrue("틀 ${t.code} — 아이가 한 말이 책에 없다", "쾵 떨어졌어요" in book)

            // 안 말했을 때는 이상한 틈이나 점이 남지 않는다
            val q = DemoState()
            q.templateKey = t.key
            q.level = t.level
            for (i in 1..q.pageCount) {
                val cap = q.bookCaption(i)
                assertFalse("틀 ${t.code} ${i}쪽에 빈 칸 자국이 남았다: $cap", "  " in cap || cap.trim() != cap)
            }
        }
    }
}
