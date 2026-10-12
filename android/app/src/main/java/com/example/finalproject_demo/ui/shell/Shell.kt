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
 *   켤 때마다   ⓪ CLAP → ① 타이틀 ─┬─ 처음이면  ② 로그인(1/5) → ③ 동의(2/5) → ④ 마이크(3/5) → 부모 비밀번호(4/5) → 맞춤 설정(5/5)
 *                                  │            → ⑥ 기능 안내(보호자) → ⑦ 건네기 → ⑧ 방 둘러보기(아이) → ⑨ 말해 보기 ─┐
 *                                  ├─ 로그인이 풀렸거나 약관 판이 바뀌었으면  다시 로그인 → (판이 바뀌었으면) 다시 동의 ─┤
 *                                  └─ 아니면 ──────────────────────────────────────────────────→ ⑨ 오또의 방
 *   처음 설정을 마친 폰에 **새 보호자 계정**이 들어오면 (10-08 #319): 로그인 → 동의 → 마이크 → ⑥ 기능 안내 → ⑦ ~ ⑨ 튜토리얼.
 *   부모 비밀번호 · 맞춤 설정은 폰 단위라 물려받는다. 같은 보호자의 다시 동의는 동의만(튜토리얼 없음)
 *   ⑨ 오또의 방은 흐름의 첫 화면(`Stage.Adult`) 위에 그린다. 방의 물건은 지금 첫 화면 버튼과 **같은 신호**
 *   (`start` · `diary` · `coop` · `shelf` · `parent`)를 흐름에 보낸다 → 흐름은 그대로 돈다.
 *
 * 기억(폰 안): 처음 설정을 끝냈나 · 튜토리얼을 봤나. 계정은 `net/Account.kt`, 동의는 `ConsentStore`(원래 것 그대로).
 */
enum class Step { CLAP, TITLE, LOGIN, EMAIL, CONSENT, MIC, PIN, SETUP, HANDOFF, TUTORIAL_TAP, TUTORIAL_TALK, FEATURES, EXPIRED, APP }

/** 부모 영역에서 여는 큰 창 */
enum class Sheet { NONE, WITHDRAW_INFO, WITHDRAW_VERIFY, WITHDRAW_CONFIRM, PIN_CHANGE }

object Shell {
    var step by mutableStateOf(Step.CLAP)
    var sheet by mutableStateOf(Sheet.NONE)

    /** 이야기 도중 🏠 를 눌러 「방으로 갈까?」를 묻는 중 */
    var askHome by mutableStateOf(false)

    /** 같이 만들기 도중 부모 「그만하기」를 눌러 「이야기를 여기서 마칠까?」를 묻는 중 (#36) */
    var askStop by mutableStateOf(false)

    /** 처음 설정(로그인 · 동의 · 마이크 · 튜토리얼)을 끝냈나 */
    var onboarded by mutableStateOf(false)
        private set

    /** 로그인 없이 샘플 책 보기 — 녹음 없이 책장만 */
    var sampleOnly by mutableStateOf(false)

    /** 부모 영역에서 연 기능 안내 다시 보기 (10-05) */
    var guide by mutableStateOf(false)

    /** 부모 영역에서 펼친 약관 전문 (10-05) — 화면 전체를 덮어야 해서 `OttoShell` 이 그린다 */
    var doc by mutableStateOf<TermsDoc?>(null)

    /** 로그인 화면에서 고른 이메일 화면 — 로그인 · 회원가입 (10-05) */
    var emailMode by mutableStateOf(EmailMode.LOGIN)

    /** 탈퇴 ① 에서 「폰 안의 책 · 그림 · 녹음도 함께 지우기」 */
    var wipeLocal by mutableStateOf(false)

    private var prefs: android.content.SharedPreferences? = null

    fun attach(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("otto_shell", Context.MODE_PRIVATE)
        onboarded = prefs!!.getBoolean("onboarded", false)
        newsSince = prefs!!.getLong("news_at", 0L).takeIf { it > 0 }
    }

    /** The full opening is shown once to an onboarded household. */
    val openingSeen: Boolean get() = prefs?.getBoolean("opening_seen", false) == true

    fun markOpeningSeen() { prefs?.edit()?.putBoolean("opening_seen", true)?.apply() }

    /** Keep account and consent gates identical for both first and repeat launches. */
    fun startStep(): Step = when {
        !onboarded -> Step.LOGIN
        com.example.finalproject_demo.net.Accounts.guardian == null -> Step.EXPIRED
        // 10-08 #319 — consent is taken from **the guardian who logs in**, so a changed or missing consent logs in first
        // (never a consent screen straight on an account left on the phone); afterLogin() then asks that guardian
        !consentOk(com.example.finalproject_demo.net.Accounts.guardian) -> Step.EXPIRED
        else -> Step.APP
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

    // ── 약관 동의 판 · 소식 알림 (10-05) ─────────────────────────────

    /** 보호자가 동의한 약관 판 — [TERMS_VERSION] 과 다르면 다시 묻는다 (null = 이 판 이전 · 묻는다) */
    val consentVersion: String? get() = prefs?.getString("consent_version", null)

    /** 소식 알림(광고성 정보) 수신에 동의한 날 — 끄면 null */
    var newsSince by mutableStateOf<Long?>(null)
        private set

    /**
     * 마지막으로 동의한 보호자 계정 ([com.example.finalproject_demo.net.Guardian.key] · 10-08 #319).
     * 동의 기록은 폰에 하나라, 이것이 없으면 이 폰에서 준비를 마친 **다른** 보호자가 들어왔을 때 앞 보호자의 동의를 물려받았다.
     * null = 이 기록 전에 받은 동의 — 그대로 인정한다
     */
    val consentBy: String? get() = prefs?.getString("consent_by", null)

    fun saveConsent(version: String, news: Boolean, at: Long) {
        prefs?.edit()?.putString("consent_version", version)
            ?.putString("consent_by", com.example.finalproject_demo.net.Accounts.guardian?.key)?.apply()
        setNews(news, at)
    }

    /** 남은 동의가 다른 보호자의 것인가 */
    fun consentByOther(g: com.example.finalproject_demo.net.Guardian?): Boolean = consentBy.let { it != null && it != g?.key }

    /** 그 보호자의 동의가 지금 판으로 남아 있나 — 아니면 로그인 → 다시 동의 (10-08 #319) */
    fun consentOk(g: com.example.finalproject_demo.net.Guardian?): Boolean =
        g != null && com.example.finalproject_demo.ui.ConsentStore.guardianAgreed && consentVersion == TERMS_VERSION && !consentByOther(g)

    fun setNews(on: Boolean, at: Long = System.currentTimeMillis()) {
        newsSince = if (on) at else null
        prefs?.edit()?.putLong("news_at", if (on) at else 0L)?.apply()
    }

    // ── 계정마다 동의 · 마이크 (10-05) ─────────────────────────────
    //
    // 법정대리인 동의와 마이크 사용 동의는 **그 보호자**가 하는 것이다. 같은 폰에서 다른 계정으로 가입 · 로그인하면
    // 처음 설정을 이미 끝냈어도 동의 → 마이크를 다시 거친다. 탈퇴하면 이 기록이 통째로 지워져(resetToFirstRun) 처음부터 다시.

    /** 이 폰에서 동의 · 마이크까지 마친 보호자 계정들 ([com.example.finalproject_demo.net.Guardian.key]) */
    private fun readySet(): Set<String> = prefs?.getStringSet("ready_accounts", emptySet()) ?: emptySet()

    fun isReady(g: com.example.finalproject_demo.net.Guardian?): Boolean = g != null && g.key in readySet()

    fun markReady(g: com.example.finalproject_demo.net.Guardian?) {
        g ?: return
        prefs?.edit()?.putStringSet("ready_accounts", readySet() + g.key)?.apply()
    }

    /**
     * 휴대폰 마이크 권한을 돌려준다 — 로그아웃 · 탈퇴 (10-05). 다시 들어오면 안드로이드 권한 창부터 다시 뜬다.
     * **안드로이드 13+ 만** 앱이 스스로 물릴 수 있고(앱이 다음에 꺼질 때 적용), 12 이하는 방법이 없어 앱 안의 마이크 동의만 다시 받는다
     * (탈퇴 마지막 화면에 「설정에서 끄기」를 보여 준다)
     */
    fun giveBackMic(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && android.os.Build.FINGERPRINT != "robolectric")
            runCatching { ctx.revokeSelfPermissionOnKill(android.Manifest.permission.RECORD_AUDIO) }
    }

    /** 앱이 스스로 마이크 권한을 물릴 수 없는 폰(안드로이드 12 이하)인데 권한이 켜져 있나 */
    fun micStuckOn(ctx: Context): Boolean = android.os.Build.VERSION.SDK_INT < 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun finishOnboarding() {
        markReady(com.example.finalproject_demo.net.Accounts.guardian)
        onboarded = true
        prefs?.edit()?.putBoolean("onboarded", true)?.apply()
        step = Step.APP
    }

    /**
     * 처음 설정 다시 하기 (부모 영역 · 계정 탭 · 09-29 · 10-08 #319) — 스플래시 · 로그인 · 동의 · 마이크 · 비밀번호 · 맞춤 설정 ·
     * 기능 안내 · 튜토리얼을 처음부터 다시 거친다. **처음 설정의 진행 기록만** 지운다(마친 표시 · 준비된 계정 · 마이크 안내) —
     * 책 · 그림 · 녹음 · 비밀번호 · 맞춤 설정 값 · 동의 기록은 그대로 둔다. 부모 영역에는 테스트 빌드에만 보인다([redoAvailable])
     */
    fun redoOnboarding() {
        onboarded = false
        prefs?.edit()?.putBoolean("onboarded", false)?.remove("ready_accounts")?.apply()
        com.example.finalproject_demo.ui.ConsentStore.forgetMic()
        sheet = Sheet.NONE
        step = Step.CLAP
    }

    /**
     * 「처음 설정 다시 하기」를 부모 영역에 보이나 (10-08 #319) — 개발 앱(「오또 개발」 · 디버그 빌드)과
     * 비공개 테스트 판(versionName 이 `-closed` 로 끝남 · `BuildConfig.TESTER_TOOLS`)만. 정식 판에서 보호자가 잘못 누르면
     * 아이가 쓰던 폰이 처음 설정으로 돌아간다
     */
    val redoAvailable: Boolean
        get() = com.example.finalproject_demo.BuildConfig.DEBUG || com.example.finalproject_demo.BuildConfig.TESTER_TOOLS

    /** 탈퇴 끝 — 처음 설치한 상태로 (⓪ CLAP 부터) */
    fun resetToFirstRun() {
        onboarded = false
        newsSince = null
        doc = null
        guide = false
        sampleOnly = false
        prefs?.edit()?.clear()?.apply()
        sheet = Sheet.NONE
        step = Step.CLAP
    }
}
