package com.example.finalproject_demo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.finalproject_demo.demo.AnswerHistory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AnswerHistoryInitializationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("answer_history", Context.MODE_PRIVATE)

    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun clean() { AnswerHistory.attach(context); AnswerHistory.clear() }

    @Test fun initializationPersistsOnlyTheMissingIdWithoutReplacingUnreadableHistory() {
        prefs.edit().putString("sessions", "not-json").putString("child_age", "{\"years\":4}").commit()
        AnswerHistory.attach(context)
        val profile = AnswerHistory.profileId
        assertEquals(profile, prefs.getString("profile", null))
        assertEquals("not-json", prefs.getString("sessions", null))
        assertEquals("{\"years\":4}", prefs.getString("child_age", null))
        assertTrue(AnswerHistory.readFailed)
        AnswerHistory.reload()
        assertEquals(profile, AnswerHistory.profileId)
        assertTrue(AnswerHistory.readFailed)
    }

    @Test fun failedInitialIdWriteIsReportedAndCanBeRetried() {
        val real = prefs
        val failing = object : SharedPreferences by real {
            override fun edit(): SharedPreferences.Editor {
                val editor = real.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit() = false
                }
            }
        }
        val wrapper = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "answer_history") failing else super.getSharedPreferences(name, mode)
        }
        AnswerHistory.attach(wrapper)
        assertTrue(AnswerHistory.writeFailed)
        assertFalse(real.contains("profile"))

        AnswerHistory.attach(context)
        assertFalse(AnswerHistory.writeFailed)
        assertEquals(AnswerHistory.profileId, real.getString("profile", null))
    }

    @Test fun anExistingProfileNeedsNoInitializationWrite() {
        prefs.edit().putString("profile", "existing-child").commit()
        AnswerHistory.attach(context)
        AnswerHistory.reload()
        assertEquals("existing-child", AnswerHistory.profileId)
        assertFalse(AnswerHistory.writeFailed)
        assertFalse(prefs.contains("sessions"))
        assertFalse(prefs.contains("child_age"))
    }
}
