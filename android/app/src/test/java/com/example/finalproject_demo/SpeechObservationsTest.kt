package com.example.finalproject_demo

import com.example.finalproject_demo.demo.ReportQuestionType
import com.example.finalproject_demo.demo.TalkLine
import com.example.finalproject_demo.demo.summarizeSpeech
import org.junit.Assert.*
import org.junit.Test

class SpeechObservationsTest {
    private fun answer(text: String, type: ReportQuestionType = ReportQuestionType.OPEN, who: String = "child") =
        TalkLine(who, text, questionType = type)

    @Test fun languageSummaryExcludesSelectionsConfirmationsUnknownAndOtherSpeakers() {
        val result = summarizeSpeech(listOf(
            answer("곰이 집에 갔어"), answer("배가 아프니까 쉬었어", ReportQuestionType.REASON),
            answer("공룡", ReportQuestionType.CHOICE), answer("응", ReportQuestionType.CONFIRMATION),
            answer("긴 답이지만 질문은 모름", ReportQuestionType.UNKNOWN),
            answer("오또가 지어낸 아주 긴 이야기", who = "mascot"),
            answer("어른이 한 이야기", who = "adult"), answer("그림으로 답함", who = "draw"),
            answer("카드의 긴 답", who = "card")))
        assertEquals(2, result.recordedAnswers)
        assertEquals(2, result.windowAnswers)
        assertEquals(3.0, result.meanWords!!, 0.0)
        assertEquals(6, result.distinctWords)
        assertEquals(1, result.reasonAnswers)
    }

    @Test fun absentLanguageIsUnknownRatherThanAZeroAbilityScore() {
        val result = summarizeSpeech(listOf(answer("숲", ReportQuestionType.CHOICE), answer("! …"), answer("  ")))
        assertEquals(0, result.recordedAnswers)
        assertNull(result.meanWords)
        assertEquals(0, result.distinctWords)
    }

    @Test fun standaloneNonAnswersDoNotCountButAStoryContainingThoseWordsDoes() {
        val result = summarizeSpeech(listOf(answer("몰라!"), answer("더 없어"), answer("몰라서 엄마에게 물어봤어")))
        assertEquals(1, result.recordedAnswers)
        assertEquals(3.0, result.meanWords!!, 0.0)
    }

    @Test fun theLastFiftyValidAnswersFormTheWindowWithoutDeletingRepeatedWords() {
        val result = summarizeSpeech(listOf(answer("처음의 아주 긴 다섯 낱말")) +
            List(50) { answer("집에 갔어") } + listOf(answer("무시할 카드", who = "card")))
        assertEquals(51, result.recordedAnswers)
        assertEquals(50, result.windowAnswers)
        assertEquals(2.0, result.meanWords!!, 0.0)
        assertEquals(2, result.distinctWords)
    }

    @Test fun surfaceWordsAreNotLemmasOrMorphemes() {
        val result = summarizeSpeech(listOf(answer("곰이 곰을 곰에게")))
        assertEquals(3.0, result.meanWords!!, 0.0)
        assertEquals(3, result.distinctWords)
    }

    @Test fun punctuationWhitespaceAndLatinCaseDoNotInventNewWordTypes() {
        val result = summarizeSpeech(listOf(answer("  곰이,\n집에 갔어! "), answer("곰이 집에 갔어. CAT cat")))
        assertEquals(4.0, result.meanWords!!, 0.0)
        assertEquals(4, result.distinctWords)
    }

    @Test fun reasonCountMeasuresAnswersWithObservedExpressionsNotAllReasonQuestions() {
        val result = summarizeSpeech(listOf(answer("배가 아파", ReportQuestionType.REASON),
            answer("밤이라서 추우니까 집에 갔어", ReportQuestionType.REASON),
            answer("바나나니까", ReportQuestionType.CHOICE)))
        assertEquals(1, result.reasonAnswers)
    }

    @Test fun pickedAnswersAreFrequencyCountsNotLanguageOrInventedInterestCategories() {
        val result = summarizeSpeech(listOf(answer("공룡", ReportQuestionType.CHOICE),
            answer("공룡!", ReportQuestionType.CHOICE, "card"), answer("우주", ReportQuestionType.CHOICE, "card"),
            answer("아이가 고르지 않은 것", ReportQuestionType.CHOICE, "mascot"),
            answer("응", ReportQuestionType.CONFIRMATION)))
        assertEquals(0, result.recordedAnswers)
        assertEquals(listOf("공룡" to 2, "우주" to 1), result.choices.map { it.text to it.count })
    }
}
