package com.example.finalproject_demo.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Raw local answers across books. No stage label, age estimate, or network upload. */
data class AnswerSession(val bookId: String, val day: String, val mode: String, val answers: List<TalkLine>)

object AnswerHistory {
    private var prefs: SharedPreferences? = null
    private val sessions = mutableListOf<AnswerSession>()
    var profileId: String = UUID.randomUUID().toString()
        private set
    var revision by mutableIntStateOf(0)
        private set

    var readFailed by androidx.compose.runtime.mutableStateOf(false)
        private set
    var writeFailed by androidx.compose.runtime.mutableStateOf(false)
        private set

    @Synchronized fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("answer_history", Context.MODE_PRIVATE)
        reload()
    }

    @Synchronized fun reload() {
        val id = prefs?.getString("profile", null)
        profileId = id ?: UUID.randomUUID().toString()
        sessions.clear()
        readFailed = false; writeFailed = false
        val raw = prefs?.getString("sessions", null)
        if (raw != null) {
            val loaded = runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val j = array.getJSONObject(i)
                    val answers = j.getJSONArray("answers")
                    AnswerSession(j.getString("bookId"), j.getString("day"), j.getString("mode"),
                        (0 until answers.length()).map { n ->
                            val a = answers.getJSONObject(n)
                            TalkLine(a.getString("who"), a.getString("text"),
                                questionType = ReportQuestionType.fromKey(a.optString("questionType")),
                                question = a.optString("question").takeUnless { it.isBlank() || it == "null" })
                        })
                }
            }.onFailure { readFailed = true }.getOrDefault(emptyList())
            sessions.addAll(loaded)
        }
        revision++
    }

    @Synchronized fun all(): List<AnswerSession> { revision; return sessions.toList() }

    @Synchronized fun languageAnswers(): List<TalkLine> = all().flatMap { it.answers }.filter {
        it.who == "child" && it.questionType in setOf(ReportQuestionType.OPEN, ReportQuestionType.REASON)
    }

    @Synchronized fun choiceAnswers(): List<TalkLine> = all().flatMap { it.answers }.filter {
        it.who in setOf("child", "card") && it.questionType == ReportQuestionType.CHOICE
    }

    @Synchronized fun keep(report: SessionReport) {
        // A paused book for a previous child must not enter the new child's history.
        if (readFailed || report.bookId.isBlank() || report.profileId != profileId) return
        val next = sessions.filterNot { it.bookId == report.bookId } + AnswerSession(
            report.bookId, report.day, report.mode, report.talk.filter { it.who != "otto" })
        writeFailed = !save(next, profileId)
        if (!writeFailed) { sessions.clear(); sessions.addAll(next); revision++ }
    }

    /** Persist the removal first. Do not delete a book if its raw copy cannot be removed safely. */
    @Synchronized fun forget(bookId: String, deleteBook: () -> Boolean = { true }): Boolean {
        if (readFailed) return false
        val before = sessions.toList()
        val next = before.filterNot { it.bookId == bookId }
        if (next.size == before.size) return runCatching(deleteBook).getOrDefault(false)
        writeFailed = !save(next, profileId)
        if (writeFailed) return false
        if (!runCatching(deleteBook).getOrDefault(false)) {
            writeFailed = !save(before, profileId)
            if (writeFailed) { sessions.clear(); sessions.addAll(next); revision++ }
            return false
        }
        sessions.clear(); sessions.addAll(next); revision++
        return true
    }

    /** A different child or an explicit guardian reset starts a fresh history; books stay on the shelf. */
    @Synchronized fun startNewChild(): Boolean {
        val id = UUID.randomUUID().toString()
        writeFailed = !save(emptyList(), id)
        if (writeFailed) return false
        readFailed = false
        sessions.clear(); profileId = id; revision++
        return true
    }

    @Synchronized fun clear() {
        sessions.clear(); profileId = UUID.randomUUID().toString(); revision++
        readFailed = false; writeFailed = false
        prefs?.edit()?.clear()?.commit()
    }

    private fun save(next: List<AnswerSession>, id: String): Boolean {
        val json = JSONArray().also { a -> next.forEach { session ->
            a.put(JSONObject().put("bookId", session.bookId).put("day", session.day).put("mode", session.mode)
                .put("answers", JSONArray().also { answers -> session.answers.forEach { line ->
                    answers.put(JSONObject().put("who", line.who).put("text", line.text)
                        .put("questionType", line.questionType.key).put("question", line.question ?: JSONObject.NULL))
                } }))
        } }
        return prefs?.edit()?.putInt("schema_version", 1)?.putString("profile", id)?.putString("sessions", json.toString())?.commit() ?: true
    }
}