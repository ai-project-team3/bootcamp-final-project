package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPartPack
import com.example.finalproject_demo.demo.heardPlace
import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.questionHint
import com.example.finalproject_demo.ui.templateQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 고른 이야기에 맞춘 한 걸음 (10-01) — 사다리 · 시연 답 · 마스코트 채움이 그 이야기 것인가.
 * 전에는 첫 질문만 템플릿 말이고 답은 일기 것("어린이집!")이었다. 뼈대 네 자리는 요소별, 꼬리질문 일곱은 고른 이유의 시제별이다.
 */
class CoopTemplatePackTest {

    private val parts = COOP_STEPS.filter { it.required }
    private val tails = COOP_STEPS.filterNot { it.required }

    private fun state(pick: CoopPick?) = DemoState().apply { mode = StoryMode.COOP; coopPick = pick }

    /** 템플릿마다 목록의 요소 전부 + 직접 쓴 예시, 이유 셋 + 안 고름 */
    private fun everyPick() = COOP_KINDS.flatMap { k ->
        (k.items + k.customExample).flatMap { name ->
            (CoopReason.entries.map { it.key } + listOf(null)).map { r -> CoopPick(k.key, name, r) }
        }
    }

    /**
     * 10-06 실기기(학교 다녀왔어요) — 사다리 첫 칸 「들어가자마자 뭐가 보였어?」에 「친구들」(사람)이 와서 장소 칸에 맞지 않았다.
     * 첫 칸도 곳을 묻고, 선택지 칸은 그 곳 안의 자리(교실 · 운동장 · 도서관)를 쓴다
     */
    @Test
    fun thePlaceLadderAsksForAPlaceAndOffersThatPlacesSpots() {
        val school = state(CoopPick("place", "학교", "done")).coopPartPack(parts[0])!!.rungs
        assertEquals("학교 안에서 어디가 제일 좋았어?", school[0])
        assertEquals("학교에서 제일 오래 있었던 데는 어디야?", school[1])
        listOf("교실", "운동장", "도서관").forEach { assertTrue("「${school[2]}」에 $it 가 없다", it in school[2]) }
        assertTrue(school.none { "뭐가 보였어" in it })
        // 자리 목록이 없는 곳(직접 쓴 곳)은 어디에나 맞는 선택지
        val custom = state(CoopPick("place", "할머니 집", "done")).coopPartPack(parts[0])!!.rungs
        assertTrue("「${custom[2]}」", "입구" in custom[2] && "맨 안쪽" in custom[2])
        assertEquals("학교에 가면 어디부터 가 보고 싶어?", state(CoopPick("place", "학교", "soon")).coopPartPack(parts[0])!!.rungs[1])
        assertEquals("학교에서 제일 가 보고 싶은 데는 어디야?", state(CoopPick("place", "학교", "dream")).coopPartPack(parts[0])!!.rungs[1])
    }

    @Test
    fun theFourPartsAreInOrderPlaceProblemCauseSolution() {
        assertEquals(listOf("place", "problem", "cause", "solution"), parts.map { it.slot })
    }

    @Test
    fun everyPickHasAPackForEachPartStartingWithItsTemplateQuestion() {
        everyPick().forEach { pick ->
            val s = state(pick)
            parts.forEachIndexed { i, step ->
                val pack = s.coopPartPack(step)
                assertNotNull("$pick ${step.slot}", pack)
                assertEquals("$pick ${step.slot}", pick.templateQuestions()[i], pack!!.rungs.first())
                assertTrue("$pick ${step.slot}: 쉬운 질문이 모자란다", pack.rungs.size >= 3)
            }
        }
    }

    /** 앱이 묻는 쉬운 질문도 부모에게 거는 귀띔(의문사 하나 · '언제' 없음 · 예/아니오로 안 끝남)을 지킨다 — 꼬리질문까지 */
    @Test
    fun everyRungPassesOurOwnQuestionRules() {
        everyPick().forEach { pick ->
            val s = state(pick)
            COOP_STEPS.forEach { step ->
                s.coopPartPack(step)!!.rungs.forEach { q -> assertNull("$pick ${step.slot}: $q", questionHint(q)) }
            }
        }
    }

    @Test
    fun answersAreSlotPipeLineAndCoverEveryLevel() {
        everyPick().forEach { pick ->
            val s = state(pick)
            COOP_STEPS.forEach { step ->
                val pack = s.coopPartPack(step)!!
                val said = pack.answers.filter { it.value.isNotEmpty() }
                assertTrue("$pick ${step.slot}: 답이 모자란다", said.size >= 4)
                (said + listOfNotNull(pack.mascot)).forEach { a ->
                    val (slot, line) = a.value.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    assertTrue("$pick ${step.slot}: 칸 값이 비었다 — ${a.text}", slot.isNotBlank())
                    assertTrue("$pick ${step.slot}: 책 문장이 -요로 안 끝난다 — $line", line.endsWith("요"))
                }
                assertEquals("$pick ${step.slot}: 수준 1~3이 다 있어야 흉내가 돈다", setOf(1, 2, 3), said.map { it.lv }.toSet())
                assertTrue("$pick ${step.bookKey}: 일기 답이 섞였다", pack.answers.none { "어린이집" in it.text || "블록" in it.text || "미끄럼틀" in it.text })
            }
            // 까닭 자리에는 까닭(S1)으로 세는 답이 있어야 판정 시연이 된다
            assertTrue("$pick: 까닭 답이 없다", s.coopPartPack(parts[2])!!.answers.any { it.reason })
        }
    }

    /** 다녀왔어요 · 곧 해요는 아이의 실제 일 — 마스코트가 지어 채우지 않는다. 상상만 지어 채운다 */
    @Test
    fun theMascotOnlyInventsForImaginedStories() {
        COOP_KINDS.forEach { k ->
            listOf("done", "soon").forEach { r ->
                val s = state(CoopPick(k.key, k.items[0], r))
                parts.drop(1).forEach { step ->
                    assertTrue("${k.key} $r ${step.slot}: ${s.coopPartPack(step)!!.mascot!!.text}", s.coopPartPack(step)!!.mascot!!.text.startsWith("아직 못 들은"))
                }
            }
            val dream = state(CoopPick(k.key, k.items[0], "dream"))
            parts.drop(1).forEach { step -> assertTrue(!dream.coopPartPack(step)!!.mascot!!.text.startsWith("아직 못 들은")) }
        }
    }

    /** 꼬리질문은 마스코트가 지어내지 않는다(걸음 정의와 같다). 누구랑 답은 kind 를 달아 뒤의 「한 말」 걸음을 살린다 */
    @Test
    fun tailStepsNeverInventAndCompanionAnswersCarryAKind() {
        everyPick().forEach { pick ->
            val s = state(pick)
            tails.forEach { step -> assertNull("$pick ${step.bookKey}", s.coopPartPack(step)!!.mascot) }
            val who = s.coopPartPack(tails.first { it.slot == "companion" })!!.answers
            assertTrue("$pick: 누구랑 답에 kind 가 없다", who.all { it.kind.isNotBlank() })
        }
    }

    /** 꼬리질문의 시제가 고른 이유를 따른다 — 곧 해요에 「집에 와서 뭐 했어?」를 묻지 않는다 */
    @Test
    fun tailQuestionsFollowTheReasonsTense() {
        val after = tails.first { it.bookKey == "after" }
        assertEquals("다 끝나고 집에 와서 뭐 했어?", state(CoopPick("place", "동물원", "done")).coopPartPack(after)!!.rungs[0])
        assertEquals("다녀와서 뭐 할 거야?", state(CoopPick("place", "동물원", "soon")).coopPartPack(after)!!.rungs[0])
        assertEquals("이야기가 끝나고 뭐 했을까?", state(CoopPick("place", "동물원", "dream")).coopPartPack(after)!!.rungs[0])
        assertEquals("이야기가 끝나고 뭐 했을까?", state(CoopPick("place", "동물원", null)).coopPartPack(after)!!.rungs[0])
    }

    /** 앞에서 말한 곳이 이름처럼 짧은 말이면 질문의 「거기」 자리에 들어간다 (10-01) */
    @Test
    fun theHeardPlaceTakesThePlaceOfThere() {
        val s = state(CoopPick("job", "소방관", "soon")).apply { place = "큰 건물" }
        assertEquals("큰 건물에서 불이 나면 소방관은 무슨 일을 할까?", s.coopPartPack(parts[1])!!.rungs[0])
        val done = state(CoopPick("place", "동물원", "done")).apply { place = "동물원 입구" }
        assertEquals("동물원 입구에 누구랑 같이 갔어?", done.coopPartPack(tails.first { it.slot == "companion" })!!.rungs[0])
        assertEquals("동물원 입구에서 누구를 만났어?", done.coopPartPack(tails.first { it.slot == "companion" })!!.rungs[2])
    }

    /** 칸에 문장이 들어 있거나 마스코트가 「아직 못 들은 …」으로 메웠으면 「거기」를 그대로 둔다 */
    @Test
    fun aSentenceOrAnUnheardPlaceLeavesThereAlone() {
        fun heard(p: String?) = state(CoopPick("job", "소방관", "soon")).apply { place = p }.heardPlace()
        assertEquals("놀이터", heard("놀이터에"))
        assertEquals("큰 건물", heard("큰 건물에서"))
        assertEquals("소방관이 일하는 곳", heard("소방관이 일하는 곳"))
        assertNull(heard("큰 건물에서 일할 것 같아"))
        assertNull(heard("놀이터에 갔어"))
        // 10-06 실기기 — 끝 「.」 때문에 문장이 이름으로 끼어 「에 누구랑 같이 갔어?」가 됐다
        assertNull(heard("이동이 마당이 좋았어."))
        assertNull(heard("기린 봤어!"))
        assertEquals("기린 마당", heard("기린 마당."))
        assertNull(heard("아직 못 들은 곳"))
        assertNull(heard(null))
        val s = state(CoopPick("job", "소방관", "soon")).apply { place = "큰 건물에서 일할 것 같아" }
        assertEquals("거기서 불이 나면 소방관은 무슨 일을 할까?", s.coopPartPack(parts[1])!!.rungs[0])
    }

    /** 곳이 들어간 질문도 우리 귀띔 규칙을 지킨다 */
    @Test
    fun rungsWithAHeardPlaceStillPassOurRules() {
        everyPick().forEach { pick ->
            val s = state(pick).apply { place = "놀이공원 입구" }
            COOP_STEPS.forEach { step ->
                s.coopPartPack(step)!!.rungs.forEach { q -> assertNull("$pick ${step.bookKey}: $q", questionHint(q)) }
            }
        }
    }

    @Test
    fun noPackWithoutAPickOrOutsideCoop() {
        val firefighter = CoopPick("job", "소방관", "soon")
        COOP_STEPS.forEach { step ->
            assertNull(state(null).coopPartPack(step))
            assertNull(DemoState().apply { coopPick = firefighter }.coopPartPack(step))     // 협업이 아니다
        }
        assertNull(state(CoopPick("nope", "소방관", null)).coopPartPack(parts[0]))
    }
}
