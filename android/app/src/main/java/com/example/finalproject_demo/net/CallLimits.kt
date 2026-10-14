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
 * own setting still allowed. A device gets [WELCOME_STARS] once and none per day (10-14 lead): after that stars
 * are bought or earned by watching an ad, and go to the same bank (the shop is shown, neither payment nor ads
 * are wired yet — `ui/shell/StarShop.kt`).
 * The parent's 「하루에 쓸 수 있는 별」 is a separate brake on how many a child may use in a day.
 *
 * Numbers from #172 (one book: /tts 31–47 lines · /turn 6–11): a session never reaches 60 / 40. A book is about
 * 150 won at the 10-02 prices, so a device that never pays costs about 750 won in all.
 * Kept on the phone; clearing the app's data resets it (enough for closed testing — the server
 * keeps its own daily total too). On in the store build only: the team's dev app and the tests are not limited.
 */
object CallLimits {
    /** Set by MainActivity for a store (non-debuggable) build */
    @Volatile var enabled = false

    const val WELCOME_STARS = 5

    /**
     * ⚠️ Closed testing only (10-14 lead): neither payment nor ads are wired, so a tester would stop for good after
     * five books. While this is on, a new day tops the stars back up to [WELCOME_STARS] (never above it).
     * The screens still describe the real rule. Turn it off in the release that wires payment or ads.
     */
    @Volatile var testerDailyRefill = true
    const val SESSION_TTS = 60
    const val SESSION_TURN = 40
    const val DAY_TTS = 120

    private var prefs: SharedPreferences? = null
    private var day = ""
    private var bank = WELCOME_STARS  // stars stay until used — the welcome stars, later bought or earned ones
    private var tts = 0

    /** Tests may move the clock — the day the counters belong to */
    @Volatile var today: () -> String = { LocalDate.now().toString() }

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("call_limits", Context.MODE_PRIVATE)
        prefs?.let { p ->
            day = p.getString("day", "") ?: ""; tts = p.getInt("tts", 0)
            bank = p.getInt("bank", WELCOME_STARS)     // no value yet = a new install: the welcome stars
        }
    }

    @Synchronized private fun roll() {
        val now = today()
        if (day != now) {
            val first = day.isEmpty()
            day = now; tts = 0
            if (testerDailyRefill && !first && bank < WELCOME_STARS) bank = WELCOME_STARS
            save()
        }
    }

    private fun save() { prefs?.edit()?.putString("day", day)?.putInt("tts", tts)?.putInt("bank", bank)?.apply() }

    /** Stars this device can still start a server book with */
    @Synchronized fun starsLeft(): Int { if (!enabled) return WELCOME_STARS; roll(); return bank }

    /** A server book started — one star, taken at the start as the parent's are */
    @Synchronized fun starSpent() {
        if (!enabled) return
        roll()
        if (bank > 0) bank--
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
    @Synchronized fun reset() { enabled = false; prefs = null; day = ""; bank = WELCOME_STARS; testerDailyRefill = true; tts = 0; today = { LocalDate.now().toString() } }
}
