package com.example.finalproject_demo.ui.shell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * ── 앱 틀 (09-29 · 치영) — 디자인 시스템의 「처음 한 번」 · 오또의 방 · 부모 계정 · 예외 ─────────────
 *
 * 대화 흐름(`demo/Director` · `Model`)은 **건드리지 않는다**(조장 요청 09-29). 이 틀은 흐름 **바깥**을 맡는다:
 *
 *   켤 때마다   ⓪ CLAP → ① 타이틀 ─┬─ 처음이면  ② 로그인(1/3) → ③ 동의(2/3) → ④ 마이크(3/3) → ⑤ 아이에게 건네기
 *                                  │            → ⑥ 튜토리얼 ① 눌러 보기 → ② 말해 보기 → ⑦ 기능 소개 ─┐
 *                                  ├─ 로그인이 풀렸으면  예외 · 로그인이 풀렸을 때 → ② 로그인 ────────┤
 *                                  └─ 아니면 ──────────────────────────────────────────────────→ ⑨ 오또의 방
 *   ⑨ 오또의 방은 흐름의 첫 화면(`Stage.Adult`) 위에 그린다. 방의 물건은 지금 첫 화면 버튼과 **같은 신호**
 *   (`start` · `diary` · `coop` · `shelf` · `parent`)를 흐름에 보낸다 → 흐름은 그대로 돈다.
 *
 * 기억(폰 안): 처음 설정을 끝냈나 · 튜토리얼을 봤나. 계정은 `net/Account.kt`, 동의는 `ConsentStore`(원래 것 그대로).
 */
enum class Step { CLAP, TITLE, LOGIN, EMAIL, CONSENT, MIC, PIN, SETUP, HANDOFF, TUTORIAL_TAP, TUTORIAL_TALK, FEATURES, EXPIRED, APP }

/** 부모 영역에서 여는 큰 창 */
enum class Sheet { NONE, WITHDRAW_INFO, WITHDRAW_CONFIRM, PIN_CHANGE }

object Shell {
    var step by mutableStateOf(Step.CLAP)
    var sheet by mutableStateOf(Sheet.NONE)

    /** 이야기 도중 🏠 를 눌러 「방으로 갈까?」를 묻는 중 */
    var askHome by mutableStateOf(false)

    /** 처음 설정(로그인 · 동의 · 마이크 · 튜토리얼)을 끝냈나 */
    var onboarded by mutableStateOf(false)
        private set

    /** 로그인 없이 샘플 책 보기 — 녹음 없이 책장만 */
    var sampleOnly by mutableStateOf(false)

    /** 탈퇴 ① 에서 「폰 안의 책 · 그림 · 녹음도 함께 지우기」 */
    var wipeLocal by mutableStateOf(false)

    private var prefs: android.content.SharedPreferences? = null

    fun attach(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("otto_shell", Context.MODE_PRIVATE)
        onboarded = prefs!!.getBoolean("onboarded", false)
    }

    /**
     * ⑤ 맞춤 설정에서 고른 값 (09-29) — 하루에 만들 책 수(null = 제한 없음) · 그림체 · 시작할 때 어른 확인.
     * 폰에 저장하고, 켤 때마다 흐름 상태(`DemoState`)에 넣는다 — 부모 설정 탭과 같은 값이다
     */
    fun saveSetup(limit: Int?, style: String, pinToStart: Boolean) {
        prefs?.edit()?.putInt("limit", limit ?: 0)?.putString("style", style)?.putBoolean("pinStart", pinToStart)?.putBoolean("setup", true)?.apply()
    }

    /** 저장해 둔 맞춤 설정을 흐름 상태에 넣는다 (설정한 적이 없으면 그대로) */
    fun applySetup(s: com.example.finalproject_demo.demo.DemoState) {
        val p = prefs ?: return
        if (!p.getBoolean("setup", false)) return
        val limit = p.getInt("limit", 3)
        s.limitOn = limit > 0
        if (limit > 0) s.dailyLimit = limit
        s.artStyle = p.getString("style", "felt") ?: "felt"
        s.pinToStart = p.getBoolean("pinStart", false)
    }

    // ── 부모 비밀번호 (09-29) — 폰 안에만 · 해시로만 저장한다(원래 숫자는 남기지 않는다) ─────────────

    /** 부모 비밀번호를 정했나 */
    val hasPin: Boolean get() = prefs?.getString("pin_hash", null) != null

    fun setPin(pin: String) { prefs?.edit()?.putString("pin_hash", hash(pin))?.apply() }

    fun checkPin(pin: String): Boolean = prefs?.getString("pin_hash", null) == hash(pin)

    private fun hash(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest("otto-pin:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun finishOnboarding() {
        onboarded = true
        prefs?.edit()?.putBoolean("onboarded", true)?.apply()
        step = Step.APP
    }

    /**
     * 처음 설정 다시 보기 (부모 영역 · 계정 탭 · 09-29) — 스플래시 · 로그인 · 동의 · 마이크 · 맞춤 설정 · 튜토리얼을 다시 거친다.
     * 처음 설정은 **한 번 끝내면 다시 나오지 않아**「로그인이랑 초반 설정이 사라졌다」는 말을 들었다. 책 · 동의 기록은 그대로 둔다
     */
    fun redoOnboarding() {
        onboarded = false
        prefs?.edit()?.putBoolean("onboarded", false)?.apply()
        sheet = Sheet.NONE
        step = Step.CLAP
    }

    /** 탈퇴 끝 — 처음 설치한 상태로 (⓪ CLAP 부터) */
    fun resetToFirstRun() {
        onboarded = false
        sampleOnly = false
        prefs?.edit()?.clear()?.apply()
        sheet = Sheet.NONE
        step = Step.CLAP
    }
}
