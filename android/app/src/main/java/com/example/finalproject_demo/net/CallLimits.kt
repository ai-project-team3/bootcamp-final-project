package com.example.finalproject_demo.net

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Server call limits for the server-connected Play build (10-06 lead · team agreement · #30 · #172).
 *
 * Not a turn limit (rule 3): the child keeps talking. Past a session or day limit a call simply
 * returns null, as when the server is down — the line plays baked or the app's own question is asked.
 *
 * **Stars (10-13 lead · #445).** One star starts one server book — a book is what costs money, and the star is
 * what pays for it. This replaces the hidden 「two server books a day」 cap, which refused a start the parent's
 * own setting still allowed. A device gets [FREE_STARS_PER_DAY] a day (not carried over) and [WELCOME_STARS] once;
 * bought stars will go to the same bank (the shop is shown, payment is not wired yet — `ui/shell/StarShop.kt`).
 * The parent's 「하루에 쓸 수 있는 별」 is a separate brake on how many a child may use in a day.
 *
 * Numbers from #172 (one book: /tts 31–47 lines · /turn 6–11): a session never reaches 60 / 40. A book is about
 * 150 won at the 10-02 prices, so a free device costs about 150 won a day after the welcome stars.
 * Kept on the phone; clearing the app's data resets it (enough for closed testing — the server
 * keeps its own daily total too). On in the store build only: the team's dev app and the tests are not limited.
 */
object CallLimits {
    /** Set by MainActivity for a store (non-debuggable) build */
    @Volatile var enabled = false

    const val FREE_STARS_PER_DAY = 1
    const val WELCOME_STARS = 3
    const val SESSION_TTS = 60
    const val SESSION_TURN = 40
    const val DAY_TTS = 120

    private var prefs: SharedPreferences? = null
    private var day = ""
    private var books = 0        // free stars used today
    private var bank = WELCOME_STARS  // stars that stay until used — the welcome stars, later bought ones
    private var tts = 0

    /** Tests may move the clock — the day the counters belong to */
    @Volatile var today: () -> String = { LocalDate.now().toString() }

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("call_limits", Context.MODE_PRIVATE)
        prefs?.let { p ->
            day = p.getString("day", "") ?: ""; books = p.getInt("books", 0); tts = p.getInt("tts", 0)
            bank = p.getInt("bank", WELCOME_STARS)     // no value yet = a new install: the welcome stars
        }
    }

    @Synchronized private fun roll() {
        val now = today()
        if (day != now) { day = now; books = 0; tts = 0; save() }
    }

    private fun save() { prefs?.edit()?.putString("day", day)?.putInt("books", books)?.putInt("tts", tts)?.putInt("bank", bank)?.apply() }

    private fun freeLeft() = (FREE_STARS_PER_DAY - books).coerceAtLeast(0)

    /** Stars this device can still start a server book with — today's free ones and the bank */
    @Synchronized fun starsLeft(): Int { if (!enabled) return FREE_STARS_PER_DAY + WELCOME_STARS; roll(); return freeLeft() + bank }

    /** A server book started — one star, taken at the start as the parent's are. Today's free star goes first */
    @Synchronized fun starSpent() {
        if (!enabled) return
        roll()
        if (freeLeft() > 0) books++ else if (bank > 0) bank--
        save()
    }

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
    @Synchronized fun reset() { enabled = false; prefs = null; day = ""; books = 0; bank = WELCOME_STARS; tts = 0; today = { LocalDate.now().toString() } }
}
