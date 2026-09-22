package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.DIARY_REQUIRED
import com.example.finalproject_demo.demo.DIARY_STEPS
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PARTNERS
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.diaryGiveItem
import com.example.finalproject_demo.demo.diaryLineOf
import com.example.finalproject_demo.demo.diaryPlaceBg
import com.example.finalproject_demo.demo.diaryTemplate
import com.example.finalproject_demo.demo.diarySlotOf
import com.example.finalproject_demo.demo.isSequential
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.mission2
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 일기 모드 · 부모 협업 모드 검사 (`일기모드_설계.md` · `부모협업모드_설계.md`).
 *
 * 동화 모드의 [StoryTextTest]와 같은 방식으로 **일기 책의 모든 쪽을 실제로 만들어** 읽어 본다.
 * 잡으려는 것은 넷이다.
 *  1. 설계가 못 박은 것을 코드가 지키는가 — 기승전결 네 자리 · 사다리 · 6~8쪽 · 슬롯 12종 안
 *  2. 9/21 요청이 들어갔는가 — 질문 8~12턴 · 흐름이 앞 답에서 이어짐 · 배경과 미션이 하루에 맞음
 *  3. 동화 모드의 소품(로켓 · 공룡 · 외계인)이 일기 책에 새어 나오지 않는가
 *  4. 동화 모드가 깨지지 않았는가
 */
class DiaryTest {

    private val bad = listOf("{", "}", "null", "  ", "..", "요요", "!.", "?.", ".!", "에에", "를를", "는는", "께서께서", "에게에게")

    /** 일기 모드에 절대 나오면 안 되는 말 — 상상 세계의 소품과 묻지 않는 칸 */
    private val storyOnlyWords = listOf("로켓", "거북이", "기차", "우주", "바닷속", "공룡 나라", "트리케라톱스", "티라노", "외계인", "문어")

    private fun diaryState(partner: String = "mom", coop: Boolean = false) = DemoState().apply {
        mode = if (coop) StoryMode.COOP else StoryMode.DIARY
        partnerKey = partner
    }

    /** 아이가 열한 걸음을 다 말한 하루 */
    private fun DemoState.fillAll() {
        placeLabel = "놀이터"; place = "놀이터"; slots["place"] = "놀이터에 갔어요"; slotBy["place"] = "child"
        friend = "민준이"; companionKind = "민준이"; friendName = "민준이"
        slots["companion"] = "민준이와 함께 놀았어요"; slotBy["companion"] = "child"
        problem = "블록이 무너짐"; slots["problem"] = "높이 쌓은 블록이 와르르 무너졌어요"; slotBy["problem"] = "child"
        slots["detail"] = "큰 것을 아래에, 작은 것을 위에 차곡차곡 올렸어요"; slotBy["detail"] = "child"
        cause = "같이 놀고 싶었어"; causeLine = "같이 놀고 싶었어"
        slots["cause"] = "같이 놀고 싶어서 그랬대요"; slotBy["cause"] = "child"
        slots["said"] = "민준이가 \"미안해\" 하고 말했어요"; slotBy["said"] = "child"
        solution = "다시 쌓았어"; solutionLine = "다시 쌓은 블록은 이번엔 무너지지 않았어요"
        slots["solution"] = "다시 쌓은 블록은 이번엔 무너지지 않았어요"; slotBy["solution"] = "child"
        slots["after"] = "집에 돌아와 저녁을 맛있게 먹었어요"
        slots["keep"] = "내일도 블록을 쌓고 싶어요"
        solutionItem = "block"
    }

    // ── 0. 책이 기승전결로 읽히나 (9/21 "중구난방") ─────────────────

    @Test
    fun theBookReadsInOrderWithLinkingWords() {
        val s = diaryState().apply { fillAll(); slots["try"] = "포기하지 않고 다시 해 보았어요" }
        val pages = diaryTemplate(s).pages.map { it.text(s) }

        // 승 — **하던 일이 먼저**, 일어난 일이 뒤. 전에는 거꾸로였다
        val mid = pages.first { "무너졌어요" in it }
        assertTrue("일어난 일이 하던 일보다 앞에 왔다: $mid", mid.indexOf("차곡차곡") < mid.indexOf("무너졌어요"))
        assertTrue("잇는 말이 없다: $mid", "그런데" in mid)

        // 전 — 아이가 한 시도가 미션 쪽 앞에 붙는다
        val turn = pages.first { "다시 해 보았어요" in it }
        assertTrue("시도에 잇는 말이 없다: $turn", turn.startsWith("그래서"))

        // 잇는 말이 겹쳐 붙지 않는다
        pages.forEach { p ->
            assertFalse("잇는 말이 두 번 붙었다: $p", Regex("(그래서|그런데|그리고|그때) (그래서|그런데|그리고|그때)").containsMatchIn(p))
        }
    }

    @Test
    fun theBackgroundLightsUpOnlyWhatTheChildSaid() {
        val s = diaryState().apply { fillAll() }   // 놀이터 · 블록 이야기
        val glow = s.diaryGlow
        assertTrue("아이가 말하지 않은 것이 반짝인다: $glow", glow.all { it in s.hotspots.map { h -> h.key } })
        // "미끄럼틀 탔어" 라고 말한 날에는 미끄럼틀이 켜진다
        val slide = diaryState().apply {
            fillAll(); slots["problem"] = "미끄럼틀을 쌩쌩 탔어요"
        }
        assertTrue("말한 미끄럼틀이 안 켜졌다", "slide" in slide.diaryGlow)
        // 동화 모드는 이 길을 쓰지 않는다 — "거기엔 뭐가 있을까"로 채운 것만 켠다
        assertTrue(DemoState().diaryGlow.isEmpty())
    }

    // ── 1. 질문 세트 ──────────────────────────────────────────────

    @Test
    fun theFourPartsAreRequiredAndEveryStepUsesAFrozenSlotName() {
        assertEquals(listOf("place", "problem", "cause", "solution"), DIARY_REQUIRED.map { it.slot })
        assertEquals(listOf("기", "승", "전", "결"), DIARY_REQUIRED.map { it.part })

        // 새 칸 이름을 만들지 않는다 — 슬롯 12종 안에서 끝낸다 (guidelines/2 §1-1 · 일기 §2)
        val frozen = setOf(
            "place", "problem", "reaction", "cause", "newcomer", "name",
            "companion", "sound", "adult", "solution", "title", "extra",
        )
        DIARY_STEPS.forEach { assertTrue("칸 이름 ${it.slot} 은 슬롯 12종 밖이다", it.slot in frozen) }
        // 묻지 않는 칸 — 공룡 소리는 상상 세계의 것이다
        assertTrue("일기 모드가 sound 를 묻는다", DIARY_STEPS.none { it.slot == "sound" })
    }

    @Test
    fun thereAreEnoughQuestionsToFillABook() {
        // 9/21 요청 — "질문 수가 적으면 모이는 데이터가 적어서 동화책 퀄리티가 떨어진다"
        assertTrue("걸음이 ${DIARY_STEPS.size}개뿐 — 8~12턴이 안 나온다", DIARY_STEPS.size >= 8)
        assertTrue("걸음이 ${DIARY_STEPS.size}개 — 취침 루틴에 너무 길다", DIARY_STEPS.size <= 12)
        assertEquals("기승전결 네 자리는 그대로 넷", 4, DIARY_REQUIRED.size)
        // 꼬리질문은 extra 아니면 선택 칸이어야 한다 (필수 칸을 늘리면 진행 막대가 바뀐다)
        DIARY_STEPS.filter { !it.required }.forEach {
            assertTrue("꼬리질문 ${it.bookKey} 가 필수 칸을 건드린다", it.slot in setOf("extra", "companion", "reaction"))
        }
        // 책에 쓰는 이름이 겹치면 한 문장이 다른 문장을 덮어쓴다
        assertEquals("bookKey 가 겹친다", DIARY_STEPS.size, DIARY_STEPS.map { it.bookKey }.distinct().size)
    }

    @Test
    fun everyStepHasALadderThatChangesTheQuestionInsteadOfOfferingChoices() {
        val s = diaryState()
        DIARY_STEPS.forEach { t ->
            val rungs = t.rungs(s)
            assertTrue("${t.bookKey} 사다리가 ${rungs.size}칸", rungs.size in 2..4)
            assertEquals("${t.bookKey} 사다리에 같은 질문이 두 번", rungs.size, rungs.distinct().size)
            rungs.forEach { q ->
                assertTrue("${t.bookKey} 질문이 비었다", q.isNotBlank())
                assertTrue("${t.bookKey} 질문에 자리표시가 남았다: $q", bad.none { b -> b in q })
                assertTrue("${t.bookKey} 질문이 물음표로 끝나지 않는다: $q", q.trim().endsWith("?"))
            }
        }
    }

    @Test
    fun onlyOneQuestionWordPerRungAndNoWhenQuestions() {
        val s = diaryState()
        // 의문사 하나만 · '언제' 질문은 낮은 단계에서 뺀다 (일기 §4-5)
        val wh = listOf("누가", "누구", "뭐", "무슨", "어디", "왜", "어떻게", "언제")
        DIARY_STEPS.forEach { t ->
            t.rungs(s).forEach { q ->
                val n = wh.count { w -> w in q }
                assertTrue("[${t.bookKey}] 의문사가 ${n}개다: $q", n <= 1)
                assertFalse("[${t.bookKey}] '언제' 질문이 들어 있다: $q", "언제" in q)
            }
        }
    }

    @Test
    fun dummyAnswersCoverEveryLevelLikeTheStoryBank() {
        val s = diaryState()
        DIARY_STEPS.forEach { t ->
            val a = t.answers(s)
            assertTrue("${t.bookKey} 더미 답이 ${a.size}개", a.size >= 4)
            assertTrue("${t.bookKey} 수준 1~3 답이 섞여 있지 않음", a.map { it.lv }.toSet().size >= 2)
            a.filter { it.value.isNotEmpty() }.forEach {
                assertTrue("${t.bookKey} 답 \"${it.text}\" 에 책 문장이 없다", diaryLineOf(it.value) != null)
                assertTrue("${t.bookKey} 답 값이 비었다", diarySlotOf(it.value).isNotBlank())
                assertTrue("${t.bookKey} 책 문장에 자리표시가 남았다: ${it.value}", bad.none { b -> b in it.value })
            }
        }
    }

    // ── 2. 흐름 — 앞 답에서 다음 질문이 나온다 ──────────────────────

    @Test
    fun theNextQuestionComesFromWhatTheChildJustSaid() {
        val s = diaryState()
        val problem = DIARY_STEPS.first { it.bookKey == "problem" }

        // 장소를 못 들었을 때와 들었을 때의 첫 질문이 달라야 한다
        val before = problem.rungs(s).first()
        assertEquals("오늘 무슨 일이 있었어?", before)
        s.placeLabel = "오늘 있었던 곳"; s.place = "오늘 있었던 곳"; s.slotBy["place"] = "mascot"
        assertEquals("장소를 못 들었는데 임시 장소를 되묻는다", before, problem.rungs(s).first())
        s.placeLabel = "놀이터"; s.place = "놀이터"
        s.slotBy["place"] = "child"
        val after = problem.rungs(s).first()
        assertNotEquals("장소를 듣고도 같은 질문을 한다", before, after)
        assertTrue("장소를 되받지 않는다: $after", "놀이터" in after)
    }

    @Test
    fun aStepWithNothingToAskAboutIsSkipped() {
        val said = DIARY_STEPS.first { it.bookKey == "said" }
        val alone = diaryState().apply { companionKind = "혼자" }
        val withFriend = diaryState().apply { companionKind = "민준이" }
        assertFalse("혼자 논 날에 \"그 친구가 뭐라고 했어?\"를 묻는다", said.ask(alone))
        assertFalse("아무도 안 나온 날에 묻는다", said.ask(diaryState()))
        assertTrue("사람이 나왔는데도 건너뛴다", said.ask(withFriend))

        // 이미 마음을 말했으면 또 묻지 않는다
        val feel = DIARY_STEPS.first { it.slot == "reaction" }
        assertTrue(feel.ask(diaryState()))
        assertFalse("마음을 말했는데 또 묻는다", feel.ask(diaryState().apply { reaction = "기뻤던 마음" }))
    }

    @Test
    fun whyIsOnlyAskedWhenSomethingWentWrong() {
        val cause = DIARY_STEPS.first { it.slot == "cause" }
        val plain = diaryState().apply { problem = "미끄럼틀" }
        val trouble = diaryState().apply { problem = "블록이 무너짐" }
        assertFalse("아무 일 없던 날에 \"왜 그렇게 됐을까?\"를 묻는다: ${plain.let { cause.rungs(it).first() }}",
            "왜 그렇게" in cause.rungs(plain).first())
        assertTrue("일이 어긋났는데 까닭을 안 묻는다", "왜" in cause.rungs(trouble).first())
    }

    @Test
    fun whyLadderDoesNotAskAboutAnUnknownFriendOrTheNextAction() {
        val cause = DIARY_STEPS.first { it.slot == "cause" }
        val alone = diaryState().apply { problem = "블록이 무너짐"; companionKind = "혼자" }
        val rungs = cause.rungs(alone)
        assertTrue("혼자인데 친구의 마음을 묻는다", rungs.none { "그 친구" in it })
        assertTrue("까닭 질문에서 다음 행동을 묻는다", rungs.none { "어떻게 했어" in it })
    }

    @Test
    fun silenceFallbacksDoNotRecordUnreportedActivitiesAsFacts() {
        val s = diaryState()
        val problem = DIARY_STEPS.first { it.slot == "problem" }.mascot!!.invoke(s).value
        val solution = DIARY_STEPS.first { it.slot == "solution" }.mascot!!.invoke(s).value
        assertFalse("놀았다는 말을 듣지 않았는데 기록했다", "놀았" in problem)
        assertFalse("집에 왔다는 말을 듣지 않았는데 기록했다", "집에 왔" in solution)
        s.solution = diarySlotOf(solution)
        s.slots["solution"] = diaryLineOf(solution)!!
        s.slotBy["solution"] = "mascot"
        assertFalse("답을 못 들은 쪽에 '마침내'가 붙었다", s.bookCaption(5).startsWith("마침내"))
    }

    @Test
    fun theMascotNeverInventsAPlaceOrAPerson() {
        // 일기 §3-2 — 데이터는 by 가 지켜 주지만, 아이가 가지 않은 곳이 그 아이의 하루로 적히는 것은 내용 문제다
        val place = DIARY_STEPS.first { it.slot == "place" }.mascot!!.invoke(diaryState())
        listOf("어린이집", "놀이터", "할머니", "학교", "공원", "집").forEach {
            assertFalse("마스코트가 장소를 지어냈다: ${place.value}", it in place.value)
        }
        // 사람은 아예 지어내지 않는다 — 없는 친구를 앱이 만들어 내면 안 된다
        assertTrue("마스코트가 사람을 지어낸다", DIARY_STEPS.first { it.slot == "companion" }.mascot == null)
    }

    @Test
    fun sequentialAnswersAreNotCountedAsReasons() {
        // 일기 §4-4 — "가서 먹었어" 는 일이 일어난 차례를 말한 것이지 까닭이 아니다 (평가셋 '-서' 함정)
        val solution = DIARY_STEPS.first { it.slot == "solution" }.answers(diaryState())
        val traps = solution.filter { it.isSequential() }
        assertTrue("순차 답 함정이 더미 답에 없다 — 판정이 가장 많이 틀리는 자리다", traps.isNotEmpty())
        traps.forEach { assertFalse("순차 답 \"${it.text}\" 이 S1로 표시돼 있다", it.reason) }
    }

    // ── 3. 배경 · 미션이 하루에 맞는가 (9/21 요청) ──────────────────

    @Test
    fun theBackgroundFollowsWhatTheChildSaid() {
        assertEquals("bg_playground", diaryPlaceBg("놀이터"))
        assertEquals("bg_daycare", diaryPlaceBg("어린이집"))
        assertEquals("bg_grandma", diaryPlaceBg("할머니 집"))
        assertEquals("bg_park", diaryPlaceBg("공원"))
        assertEquals("bg_mart", diaryPlaceBg("마트"))
        assertEquals("bg_home", diaryPlaceBg("집"))
        // "할머니 집"이 "집"보다 먼저 걸려야 한다
        assertEquals("할머니 집이 그냥 집으로 떨어진다", "bg_grandma", diaryPlaceBg("할머니 집에 갔어"))
        // 모르는 곳은 엉뚱한 배경을 보여 주지 않는다
        assertEquals("bg_today", diaryPlaceBg("우리 이모네 농장"))
        assertEquals("bg_today", diaryPlaceBg(null))

        val s = diaryState().apply { placeLabel = "놀이터" }
        assertEquals("bg_playground", s.bgName)
        assertFalse("상상 세계 배경이 일기 모드에 샌다", s.bgName.startsWith("bg_space"))
    }

    @Test
    fun theMissionPropsComeFromTheDay() {
        // 미션 1 — ① 아이가 말한 일이 먼저다 (9/21: 장소만 보다가 엉뚱한 미션이 나왔다)
        assertEquals("물감", diaryState().apply { placeLabel = "놀이터"; problem = "그림 그리기" }.mission1().blobName)
        assertEquals("모래", diaryState().apply { placeLabel = "어린이집"; problem = "모래놀이" }.mission1().blobName)
        assertEquals("블록 놀이를 물감 놀이로 바꿨다", "먼지", diaryState().apply { placeLabel = "어린이집"; problem = "블록이 무너짐" }.mission1().blobName)
        // ② 말한 일에서 못 찾으면 장소에서
        assertEquals("모래", diaryState().apply { placeLabel = "놀이터" }.mission1().blobName)
        assertEquals("물감", diaryState().apply { placeLabel = "어린이집" }.mission1().blobName)
        assertEquals("나뭇잎", diaryState().apply { placeLabel = "공원" }.mission1().blobName)
        // ③ 둘 다 없으면 무엇이 묻었다고 지어내지 않는다 — 하루 먼지를 턴다
        assertEquals("먼지", diaryState().apply { placeLabel = "할머니 집" }.mission1().blobName)
        // 동화 모드는 그대로 — 장면 4의 "누가 흔들었나"에서 나온다
        assertEquals("불", DemoState().apply { newcomerKind = "외계인" }.mission1().blobName)

        // 미션 2 — 아이가 말한 것에서
        val s = diaryState()
        assertEquals("block", diaryGiveItem("다시 쌓았어", s.apply { problem = "블록이 무너짐" }))
        assertEquals("bandaid", diaryGiveItem("약 발랐어", diaryState().apply { problem = "넘어짐" }))
        assertEquals("picturebook", diaryGiveItem("책 읽었어", diaryState()))
        assertEquals("블록", diaryState().apply { solutionItem = "block" }.mission2().itemName)
        assertEquals("반창고", diaryState().apply { solutionItem = "bandaid" }.mission2().itemName)
    }

    // ── 4. 책 ────────────────────────────────────────────────────

    @Test
    fun diaryBookIsSixToEightPagesAndReadsCleanly() {
        val out = StringBuilder()
        val problems = mutableListOf<String>()
        var checked = 0
        for (p in PARTNERS) for (withDrawing in listOf(false, true)) for (withFeeling in listOf(false, true)) for (coop in listOf(false, true)) {
            val s = diaryState(p.key, coop)
            s.fillAll()
            if (withDrawing) s.newcomer = "민준이 (아이 그림)"
            if (withFeeling) {
                s.reaction = "속상했던 마음"
                s.slots["reaction"] = "${s.childName}는 속상했던 마음이 한참 남았어요"
            }
            s.title = s.autoTitleFor()

            val pages = s.pageCount
            if (pages !in 6..8) problems += "[${p.key}/$withDrawing/$withFeeling/coop=$coop] 쪽 수 $pages"
            val book = (1..pages).map { s.bookCaption(it) }
            out.append("\n## ${p.name} · 그림 $withDrawing · 기분 $withFeeling · 협업 $coop · ${pages}쪽 · 『${s.title}』\n")
                .append(book.joinToString("\n") { "  $it" }).append('\n')
            (book + s.title!!).forEach { line ->
                checked++
                if (line.isBlank()) problems += "[${p.key}] 빈 자막"
                bad.filter { it in line }.forEach { b -> problems += "[${p.key}] '$b' in: $line" }
                storyOnlyWords.filter { it in line }.forEach { w -> problems += "[${p.key}] 동화 모드 소품 '$w' 가 일기 책에: $line" }
            }
        }
        File("build").mkdirs()
        File("build/diary_samples.txt").writeText(out.toString() + "\n\n# problems\n" + problems.joinToString("\n"))
        println("diary lines checked: $checked, problems: ${problems.size}")
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun tailAnswersActuallyMakeTheBookLonger() {
        // 질문을 늘린 이유가 여기다 — 같은 여섯 쪽이라도 아이가 한 말이 더 많이 들어간다
        val bare = diaryState().apply {
            placeLabel = "놀이터"; place = "놀이터"; slots["place"] = "놀이터에 갔어요"
            problem = "블록"; slots["problem"] = "블록을 쌓았어요"
            cause = "몰라"; slots["cause"] = "왜 그랬는지는 아직 아무도 몰라요"
            solution = "집에 옴"; slots["solution"] = "집으로 돌아왔어요"
            title = "지호의 놀이터 하루"
        }
        val rich = diaryState().apply { fillAll(); title = autoTitleFor() }
        val bareLen = (1..bare.pageCount).sumOf { bare.bookCaption(it).length }
        val richLen = (1..rich.pageCount).sumOf { rich.bookCaption(it).length }
        assertTrue("꼬리질문을 받아도 책이 길어지지 않는다 ($bareLen → $richLen)", richLen > bareLen * 1.3)
    }

    @Test
    fun optionalSlotsAddPagesExactlyAsTheDesignSays() {
        // 기 1 / 승 1 / 전 2 / 결 1 / 에필로그 1 = 6쪽, 선택 칸이 차면 한 쪽씩 (일기 §2-2 · §7-1 ①)
        val base = diaryState().apply { fillAll(); companionKind = "혼자"; friendName = "{친구1}" }
        assertEquals(6, base.pageCount)
        val withPerson = diaryState().apply { fillAll() }
        assertEquals("사람이 나오면 등장 쪽 한 장", 7, withPerson.pageCount)
        val both = diaryState().apply { fillAll(); reaction = "속상했던 마음" }
        assertEquals("선택 칸 둘이 다 차면 8쪽", 8, both.pageCount)
    }

    @Test
    fun theBookIsStillMadeWhenTheChildSaidAlmostNothing() {
        // 일기 §5 — 모인 칸이 둘뿐이어도 책은 나온다. 빈 자리는 이야기로 메운다
        val s = diaryState()
        s.placeLabel = "놀이터"; s.place = "놀이터"; s.slots["place"] = "놀이터에 갔어요"; s.slotBy["place"] = "child"
        s.problem = "블록 놀이"; s.slots["problem"] = "블록을 쌓았어요"; s.slotBy["problem"] = "child"
        DIARY_REQUIRED.filter { it.slot == "cause" || it.slot == "solution" }.forEach { t ->
            val a = t.mascot!!.invoke(s)
            s.slots[t.bookKey] = diaryLineOf(a.value)!!
            s.slotBy[t.bookKey] = "mascot"
        }
        s.cause = "잘 모르겠어"; s.solution = "그러고 집에 왔어"
        s.title = s.autoTitleFor()
        assertEquals(6, s.pageCount)
        (1..s.pageCount).forEach { i ->
            val line = s.bookCaption(i)
            assertTrue("${i}쪽이 비었다", line.isNotBlank())
            assertTrue("${i}쪽에 자리표시가 남았다: $line", bad.none { it in line })
        }
    }

    @Test
    fun titleNeverLeaksAnImaginaryPlace() {
        val s = diaryState()
        val t = s.autoTitleFor()
        assertFalse("제목에 상상 세계 이름이 들어갔다: $t", storyOnlyWords.any { it in t })
        assertTrue("제목에 자리표시가 남았다: $t", bad.none { it in t })
        // 흔한 호칭은 제목에 쓰지 않는다 ("놀이터에서 만난 친구"는 이름이 아니다)
        val common = diaryState().apply { placeLabel = "놀이터"; companionKind = "친구" }
        assertFalse("호칭을 이름처럼 제목에 썼다: ${common.autoTitleFor()}", "만난 친구" in common.autoTitleFor())
    }

    // ── 5. 부모 협업 모드 ────────────────────────────────────────

    @Test
    fun coopNeverAddsAFourthSourceKind() {
        // 협업 §4-1 — `by: parent` 를 새로 만들지 않는다. 스키마도 평가셋 100개도 그대로다
        val s = diaryState(coop = true)
        s.fillAll()
        s.slotBy["cause"] = "mascot"     // 부모가 지은 자리는 by: mascot 으로 들어간다
        assertTrue("출처에 parent 가 생겼다", s.slotBy.values.all { it in setOf("child", "card", "mascot") })
    }

    // ── 6. 동화 모드가 깨지지 않았는가 ────────────────────────────

    @Test
    fun diaryQuestionsNeverEnterTheStoryQuestionBank() {
        assertTrue("일기 질문이 BANK에 섞였다", BANK.none { it.id.startsWith("diary_") })
        val s = DemoState()
        assertFalse(s.isDiary)
        repeat(20) { s.askedThisStory.clear(); assertFalse(s.pick("cause").id.startsWith("diary_")) }
    }

    @Test
    fun requiredSlotCountDependsOnTheMode() {
        assertEquals("동화 모드는 필수 6칸 (구현대본 §2)", 6, DemoState().reqCount)
        assertEquals("일기 모드는 기승전결 네 자리", 4, diaryState().reqCount)
        assertEquals("협업 모드도 같은 네 자리", 4, diaryState(coop = true).reqCount)

        val s = diaryState()
        assertEquals(0, s.filled)
        s.fillAll()
        assertEquals(4, s.filled)
        assertTrue("네 자리가 다 차면 story_ready", s.diaryReady)
    }

    @Test
    fun startingANewStoryClearsEveryModeSpecificThing() {
        val s = diaryState(coop = true)
        s.fillAll()
        s.mascotPicks = 2; s.endReason = "mascot_pick"
        s.parentCard = "거기서 무슨 일이 있었어?"; s.parentRung = 2; s.adultLine = "어른이 지은 말"
        s.resetStory()
        assertEquals(StoryMode.STORY, s.mode)
        assertEquals(0, s.mascotPicks)
        assertTrue(s.slotBy.isEmpty())
        assertTrue(s.endReason == null && s.reaction == null && s.adultLine == null)
        assertTrue("부모 띠가 남았다", s.parentCard == null && s.parentRung == 0)
        assertEquals("", s.companionKind)
    }
}
