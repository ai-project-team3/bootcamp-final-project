package com.example.finalproject_demo.net

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Server call limits for the server-connected Play build (10-06 lead · team agreement · #30 · #172).
 *
 * Not a turn limit (rule 3): the child keeps talking. Past a session or day limit a call simply
 * returns null, as when the server is down — the line plays baked or the app's own question is asked.
 * Only the start of a third server story in a day is refused, with a line that says why.
 *
 * Numbers from #172 (one book: /tts 31–47 lines · /turn 6–11): a session never reaches 60 / 40, and two
 * books a day stay under 120 server lines — about 300 won a device a day at the 10-02 prices.
 * Apart from the parent's 「하루 책 수」 stars, which a parent can raise or turn off: this one is the cost cap.
 * Kept per day on the phone; clearing the app's data resets it (enough for closed testing — the server
 * keeps its own daily total too). On in the store build only: the team's dev app and the tests are not limited.
 */
object CallLimits {
    /** Set by MainActivity for a store (non-debuggable) build */
    @Volatile var enabled = false

    const val BOOKS_PER_DAY = 2
    const val SESSION_TTS = 60
    const val SESSION_TURN = 40
    const val DAY_TTS = 120

    private var prefs: SharedPreferences? = null
    private var day = ""
    private var books = 0
    private var tts = 0

    /** Tests may move the clock — the day the counters belong to */
    @Volatile var today: () -> String = { LocalDate.now().toString() }

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("call_limits", Context.MODE_PRIVATE)
        prefs?.let { p -> day = p.getString("day", "") ?: ""; books = p.getInt("books", 0); tts = p.getInt("tts", 0) }
    }

    @Synchronized private fun roll() {
        val now = today()
        if (day != now) { day = now; books = 0; tts = 0; save() }
    }

    private fun save() { prefs?.edit()?.putString("day", day)?.putInt("books", books)?.putInt("tts", tts)?.apply() }

    /** Server stories still allowed today */
    @Synchronized fun booksLeftToday(): Int { if (!enabled) return BOOKS_PER_DAY; roll(); return (BOOKS_PER_DAY - books).coerceAtLeast(0) }

    /** A server story started — counted at the start, as the parent's stars are */
    @Synchronized fun bookStarted() { if (!enabled) return; roll(); books++; save() }

    /** Why [path] may not be called now, or null. [session] is this session's calls so far, by path */
    @Synchronized fun blocks(path: String, session: Map<String, Int>): String? {
        if (!enabled) return null
        roll()
        return when (path) {
            "/tts" -> when {
                (session["/tts"] ?: 0) >= SESSION_TTS -> "session /tts $SESSION_TTS"
                tts >= DAY_TTS -> "day /tts $DAY_TTS"
                else -> null
            }
            "/turn" -> if ((session["/turn"] ?: 0) >= SESSION_TURN) "session /turn $SESSION_TURN" else null
            else -> null
        }
    }

    /** A call went out — only the paid voice is kept per day */
    @Synchronized fun counted(path: String) { if (enabled && path == "/tts") { roll(); tts++; save() } }

    /** Tests: a fresh day with no phone storage */
    @Synchronized fun reset() { enabled = false; prefs = null; day = ""; books = 0; tts = 0; today = { LocalDate.now().toString() } }
}
