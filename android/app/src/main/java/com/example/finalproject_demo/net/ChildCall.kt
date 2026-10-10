package com.example.finalproject_demo.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the mascot calls the child — a nickname the guardian types in parent mode (10-02 조장).
 *
 * Not a real-name field: the guardian is asked how Otto should call the child ("별명을 권해요" · 10-06), and
 * that word goes to the server and the voice as is. Names are no longer masked (10-02 — rule 6 revised):
 * a name alone does not single out a child, and hiding it made the mascot say 「너」 · 「그 친구」.
 * Unset → [DEFAULT]. Kept on the phone only (shared_prefs, no backup — the manifest already excludes it).
 */
object ChildCall {
    const val DEFAULT = "친구"            // no batchim, like the old demo name — particles stay the same
    private const val PREFS = "child_call"
    private const val KEY = "name"
    private const val MAX = 10           // a call, not a sentence

    private var ctx: Context? = null
    var name by mutableStateOf<String?>(null)
        private set

    fun attach(context: Context) {
        ctx = context.applicationContext
        name = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null)
    }

    /** Blank clears it (back to [DEFAULT]). Only letters and digits, at most [MAX] — it is read aloud. */
    internal fun clean(raw: String): String? = raw.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(MAX).ifBlank { null }

    fun set(raw: String) {
        val clean = clean(raw)
        name = clean
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
            if (clean == null) remove(KEY) else putString(KEY, clean)
        }?.apply()
    }

    /** For tests: forget without a context. */
    fun reset() { name = null }

    val call: String get() = name ?: DEFAULT
}
