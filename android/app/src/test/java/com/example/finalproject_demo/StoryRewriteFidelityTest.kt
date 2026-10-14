package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs: loss at the app's slot and fallback boundaries, not model accuracy. */
class StoryRewriteFidelityTest {
    private fun verdict(slot: String, value: String, correction: Boolean = false) = Server.Verdict(
        reason = "test", fills = listOf(slot to value), nextSlot = null, noLongerNeeded = null,
        storyReady = false, unclear = false, unclearOf = null, contradiction = correction,
        s1Reason = false, s2Addition = false, emotion = null,
    )

    @Test fun aFollowUpEndingKeepsTheEarlierSolutionAndTheLaterEvent() {
        val s = DemoState()
        s.applyStoryVerdict(verdict("solution", "고기로 유인하고 문을 닫았어"), "child", "solution")
        s.applyStoryVerdict(verdict("solution", "그 뒤 시장에서 밥을 먹었어"), "child", "extra")
        assertEquals("고기로 유인하고 문을 닫았어 / 그 뒤 시장에서 밥을 먹었어", s.storyServerInput().slots["solution"])
        assertEquals("child", s.slotBy["solution"])
    }

    @Test fun aFreeFollowUpDoesNotEraseEarlierCauseOrExtra() {
        val s = DemoState()
        for (slot in listOf("problem", "reaction", "cause", "solution", "extra")) {
            s.applyStoryVerdict(verdict(slot, "길이 막혔어"), "child", slot)
            s.applyStoryVerdict(verdict(slot, "비도 내렸어"), "child")
            assertEquals(slot, "길이 막혔어 / 비도 내렸어", s.slots[slot])
        }
    }

    @Test fun repeatedFragmentsDoNotGrowOnEachVerdict() {
        val s = DemoState()
        s.applyStoryVerdict(verdict("solution", "문을 닫았어"), "child", "solution")
        repeat(3) { s.applyStoryVerdict(verdict("solution", "문을 닫았어 / 밥을 먹었어"), "child", "extra") }
        assertEquals("문을 닫았어 / 밥을 먹었어", s.slots["solution"])
    }

    @Test fun anExplicitCorrectionStillReplacesTheWrongEvent() {
        val s = DemoState()
        s.applyStoryVerdict(verdict("solution", "집에 갔어"), "child", "solution")
        s.applyStoryVerdict(verdict("solution", "집에 안 갔어", correction = true), "child", "extra")
        assertEquals("집에 안 갔어", s.slots["solution"])
        assertFalse(s.storyServerInput().slots.values.any { it == "집에 갔어" })
    }

    @Test fun answeringTheSameSlotAgainStillClarifiesIt() {
        val s = DemoState()
        s.applyStoryVerdict(verdict("solution", "갔어"), "child", "solution")
        s.applyStoryVerdict(verdict("solution", "집에 갔어"), "child", "solution")
        assertEquals("집에 갔어", s.slots["solution"])
    }

    @Test fun aCardSupplementCannotRelabelTheChildsSolution() {
        val s = DemoState().apply { templateKey = "C" }
        s.applyStoryVerdict(verdict("solution", "문을 닫았어"), "child", "solution")
        s.applyStoryVerdict(verdict("solution", "집으로 돌아갔어요"), "card", "extra")
        assertEquals("문을 닫았어", s.slots["solution"])
        assertEquals("child", s.slotBy["solution"])
        val input = s.storyServerInput()
        assertTrue(input.slots["extra"].orEmpty().contains("집으로 돌아갔어요"))
        assertEquals("card", input.sources["extra"])
    }

    @Test fun aRepeatedCardValueCannotRelabelTheSameChildWords() {
        val s = DemoState()
        s.applyStoryVerdict(verdict("solution", "문을 닫았어"), "child", "solution")
        s.applyStoryVerdict(verdict("solution", "문을 닫았어"), "card", "extra")
        assertEquals("child", s.slotBy["solution"])
    }

    @Test fun correctingASlotAlsoRemovesItsOlderDifferentlySourcedSupplement() {
        for (direct in listOf(false, true)) {
            val s = DemoState().apply { templateKey = "C" }
            s.applyStoryVerdict(verdict("solution", "집에 갔어"), "child", "solution")
            s.applyStoryVerdict(verdict("solution", "호수에도 갔어요"), "card", "extra")
            s.applyStoryVerdict(verdict("solution", "집에 안 갔어", correction = !direct),
                "child", if (direct) "solution" else "extra")
            assertFalse(s.storyServerInput().slots["extra"].orEmpty().contains("호수에도 갔어요"))
            assertFalse(s.bookCaption(s.pageCount).contains("호수에도 갔어요"))
        }
    }

    @Test fun everyFallbackTemplateUsesTheFullRecordedEndingInsteadOfAnInventedPromise() {
        for (template in TEMPLATES) {
            val s = DemoState().apply { templateKey = template.key }
            s.recordTemplateAnswer(StoryPrompt("resolve", "끝에는 어떻게 됐어?", true),
                "시장으로 돌아갔고 장사는 그만뒀어", "child")
            s.slots["extra"] = "장사는 그만뒀어"; s.slotBy["extra"] = "child"
            assertFalse(s.useGeneratedStory(null))
            val caption = s.bookCaption(s.pageCount)
            assertTrue(template.key, caption.contains("시장으로 돌아갔고 장사는 그만뒀어"))
            assertFalse(template.key, caption.contains("또 오자"))
            assertFalse(template.key, caption.contains("손가락을 걸었어요"))
            assertFalse(template.key, caption.contains("다음에도 사이좋게"))
        }
    }

    @Test fun fallbackKeepsTheGoalAsAWishAndKeepsTheNegation() {
        val s = DemoState().apply { templateKey = "E" }
        s.slots["solution"] = "친구를 때리지 않았어"; s.slotBy["solution"] = "child"
        s.recordTemplateAnswer(StoryPrompt("resolve", "", true), "둘이 집으로 돌아갔어", "child")
        s.recordTemplateAnswer(StoryPrompt("goal", "", true), "내일은 다시 가고 싶어", "child")
        assertFalse(s.useGeneratedStory(listOf("한 쪽만 왔어요.")))
        val ending = s.bookCaption(s.pageCount)
        assertTrue(ending.contains("친구를 때리지 않았어"))
        assertTrue(ending.contains("둘이 집으로 돌아갔어"))
        assertTrue(ending.contains("내일은 다시 가고 싶어"))
        assertFalse(ending.contains("내일 다시 갔어요"))
    }

    @Test fun generatedCaptionsAndOtherModesKeepTheirExistingRendering() {
        val s = DemoState().apply { templateKey = "E" }
        s.slots["resolve"] = "집에 갔어"; s.slotBy["resolve"] = "child"
        val caps = List(s.pageCount) { "생성된 자막이에요." }
        assertTrue(s.useGeneratedStory(caps))
        assertEquals("생성된 자막이에요.", s.bookCaption(s.pageCount))
        s.mode = StoryMode.COOP; s.storyCaptions = null
        assertFalse(s.bookCaption(s.pageCount).contains("「집에 갔어」"))
    }

    @Test fun aScriptedReflectionDoesNotReplaceTheActualSolution() {
        val s = DemoState().apply { templateKey = "A"; solutionLine = "블록을 다시 쌓았어요" }
        s.slots["reflect"] = "다시 하면 된다는 걸 알았어"
        s.slotBy["reflect"] = "child"
        assertTrue(s.bookCaption(s.pageCount).contains("블록을 다시 쌓았어요"))
        assertTrue(s.bookCaption(s.pageCount).contains("다시 하면 된다는 걸 알았어"))
    }
}
