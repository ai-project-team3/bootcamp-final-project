package com.example.finalproject_demo.net

import android.util.Log
import com.example.finalproject_demo.BuildConfig

/**
 * The conversation into logcat, **debug builds only** (「오또 개발」 · 10-05). A device round used to leave
 * only recording lengths behind — what the child was heard to say, what was asked next and how long each
 * server call took were nowhere, so a bad turn could not be traced.
 *
 * - Tag `OttoTrace`: `adb logcat -s OttoTrace` · one line per event, `kind | text`
 * - Stays on the phone; read over USB only. The Play build (release) writes nothing
 * - Words are as the app holds them — names are not masked anymore (rule 6, 10-02)
 */
object Trace {
    private val on = BuildConfig.DEBUG

    fun line(kind: String, text: String) {
        if (!on) return
        // plain JVM unit tests have no android.util.Log — never let tracing break a test
        try { Log.i("OttoTrace", "$kind | $text") } catch (_: RuntimeException) {}
    }
}
