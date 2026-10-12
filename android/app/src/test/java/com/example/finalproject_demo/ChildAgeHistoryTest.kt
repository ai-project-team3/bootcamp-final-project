package com.example.finalproject_demo

import android.content.Context
import com.example.finalproject_demo.demo.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChildAgeHistoryTest {
    @Before fun setup() { AnswerHistory.attach(RuntimeEnvironment.getApplication()); AnswerHistory.clear() }
    @After fun cleanup() = AnswerHistory.clear()

    @Test fun optionalAgePersistsBeforeAnyBookAndDoesNotChangeProfile() {
        val profile = AnswerHistory.profileId
        assertTrue(AnswerHistory.setChildAge(ChildAge(4, 1)))
        AnswerHistory.reload()
        assertEquals(profile, AnswerHistory.profileId)
        assertEquals(ChildAge(4, 1), AnswerHistory.childAge)
        assertTrue(AnswerHistory.all().isEmpty())
    }

    @Test fun keepingOrDeletingBooksPreservesAgeButChangingChildClearsIt() {
        AnswerHistory.setChildAge(ChildAge(5, 0))
        val s = DemoState()
        s.talk += TalkLine("child", "곰이 걸었어", questionType = ReportQuestionType.OPEN)
        AnswerHistory.keep(s.buildSessionReport("book"))
        AnswerHistory.reload()
        assertEquals(ChildAge(5, 0), AnswerHistory.childAge)
        assertEquals(1, AnswerHistory.all().size)
        assertTrue(AnswerHistory.forget("book"))
        AnswerHistory.reload()
        assertEquals(ChildAge(5, 0), AnswerHistory.childAge)
        assertTrue(AnswerHistory.startNewChild())
        AnswerHistory.reload()
        assertNull(AnswerHistory.childAge)
    }

    @Test fun removingAgeKeepsTheChildAndAnswers() {
        AnswerHistory.setChildAge(ChildAge(3, null))
        val s = DemoState()
        s.talk += TalkLine("child", "곰이 걸었어", questionType = ReportQuestionType.OPEN)
        AnswerHistory.keep(s.buildSessionReport("book"))
        val id = AnswerHistory.profileId
        assertTrue(AnswerHistory.setChildAge(null))
        AnswerHistory.reload()
        assertEquals(id, AnswerHistory.profileId)
        assertNull(AnswerHistory.childAge)
        assertEquals(1, AnswerHistory.all().size)
    }

    @Test fun invalidStoredAgeIsNotUsedAndDoesNotDestroyOldAnswers() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("answer_history", Context.MODE_PRIVATE)
        prefs.edit().putString("child_age", "{\"years\":99,\"half\":0}").commit()
        AnswerHistory.reload()
        assertNull(AnswerHistory.childAge)
        assertFalse(AnswerHistory.readFailed)
    }

    @Test fun unreadableHistoryCannotBeReplacedBySavingAge() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("answer_history", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", "not-json").commit()
        AnswerHistory.reload()
        assertFalse(AnswerHistory.setChildAge(ChildAge(4, 0)))
        assertEquals("not-json", prefs.getString("sessions", null))
    }
}
