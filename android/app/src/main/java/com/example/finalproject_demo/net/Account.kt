package com.example.finalproject_demo.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * ── 보호자 계정 — 서버에 붙일 **뼈대** (09-29 · 치영) ─────────────────────────────
 *
 * 디자인 시스템(`design/디자인시스템.md` 「로그인 · 계정 결정」):
 *   - 로그인: 카카오 · 네이버 · Google · 이메일. **보호자 정보만** 저장한다. 아이 이름 · 나이는 받지 않는다
 *   - 녹음 · 그림 · 책은 **폰 안에만** 둔다
 *   - 탈퇴 · 데이터 삭제는 부모 영역 → 계정 (스토어 심사 필수)
 *
 * 지금은 서버에 계정 기능이 없다. 그래서 화면은 [AccountApi] 한 곳만 부르고, 그 뒤는 [LocalAccountApi]
 * (폰 안에서만 도는 가짜)가 맡는다. 서버가 생기면 [ServerAccountApi] 의 할 일(TODO)만 채우고
 * [Accounts.api] 를 바꿔 끼우면 화면은 그대로다.
 *
 * `net/Server.kt` 와 같은 약속 — **예외를 던지지 않는다.** 실패는 [AuthResult.Fail] 로 돌려준다.
 * 소셜 로그인 SDK(카카오 · 네이버 · Google)는 각 사에 앱을 등록하고 키를 받아야 붙일 수 있다 — 아직 없다.
 */

/** 로그인 방법 */
enum class AuthProvider(val label: String) { KAKAO("카카오"), NAVER("네이버"), GOOGLE("Google"), EMAIL("이메일") }

/** 로그인한 보호자 — 서버에 두는 것은 이것뿐이다 */
data class Guardian(val provider: AuthProvider, val email: String, val since: Long)

sealed interface AuthResult {
    data class Ok(val guardian: Guardian) : AuthResult
    data class Fail(val why: String) : AuthResult
}

/** 동의 기록 — 무엇에 · 언제 동의했나 (법정대리인 동의 증빙) */
data class ConsentRecord(val terms: Boolean, val privacy: Boolean, val guardian: Boolean, val marketing: Boolean, val at: Long)

interface AccountApi {
    suspend fun login(provider: AuthProvider, email: String? = null, password: String? = null): AuthResult
    suspend fun logout()
    /** 세션이 살아 있나 — 죽었으면 「로그인이 풀렸을 때」 화면 */
    suspend fun sessionAlive(): Boolean
    suspend fun recordConsent(r: ConsentRecord): Boolean
    /** 회원 탈퇴 — 서버 계정 · 기록을 **즉시** 지운다. 폰 안 데이터는 부르는 쪽이 따로 지운다 */
    suspend fun deleteAccount(): Boolean
}

/** 지금 쓰는 가짜 — 서버 없이 폰 안(SharedPreferences)에만 기억한다 */
class LocalAccountApi(private val ctx: Context) : AccountApi {
    private val prefs get() = ctx.getSharedPreferences("otto_account", Context.MODE_PRIVATE)

    override suspend fun login(provider: AuthProvider, email: String?, password: String?): AuthResult {
        if (provider == AuthProvider.EMAIL) {
            if (email.isNullOrBlank() || !email.contains('@')) return AuthResult.Fail("이메일을 확인해 주세요")
            if (password == null || password.length < 8) return AuthResult.Fail("비밀번호는 8자 이상이에요")
        }
        // 소셜 로그인은 SDK 가 붙기 전까지 **자리만** — 누르면 그 방법으로 로그인한 것으로 친다
        val g = Guardian(provider, email ?: "보호자@${provider.name.lowercase()}", System.currentTimeMillis())
        prefs.edit().putString("provider", g.provider.name).putString("email", g.email).putLong("since", g.since).apply()
        return AuthResult.Ok(g)
    }

    override suspend fun logout() { prefs.edit().remove("provider").remove("email").remove("since").apply() }

    override suspend fun sessionAlive() = prefs.contains("provider")

    override suspend fun recordConsent(r: ConsentRecord): Boolean {
        prefs.edit().putBoolean("c_terms", r.terms).putBoolean("c_privacy", r.privacy).putBoolean("c_guardian", r.guardian)
            .putBoolean("c_marketing", r.marketing).putLong("c_at", r.at).apply()
        return true
    }

    override suspend fun deleteAccount(): Boolean { prefs.edit().clear().apply(); return true }

    fun saved(): Guardian? {
        val p = prefs.getString("provider", null) ?: return null
        return Guardian(AuthProvider.valueOf(p), prefs.getString("email", "") ?: "", prefs.getLong("since", 0))
    }
}

/**
 * 서버에 붙일 자리 — 엔드포인트는 서버에 계정이 생기면 정한다(아래는 제안).
 * 붙이는 순서: ① 소셜 SDK 로 토큰 받기 → ② `/auth/{provider}` 로 보내 우리 세션 받기 → ③ 세션을 안전한 저장소에.
 * 개인정보: 보호자 이메일 · 로그인 방법 · 동의 기록만. **아이 말 · 그림 · 책은 보내지 않는다.**
 */
class ServerAccountApi : AccountApi {
    override suspend fun login(provider: AuthProvider, email: String?, password: String?): AuthResult =
        AuthResult.Fail("TODO: POST /auth/${provider.name.lowercase()} — 서버 계정 기능이 아직 없다")
    override suspend fun logout() { /* TODO: POST /auth/logout */ }
    override suspend fun sessionAlive(): Boolean = false /* TODO: GET /auth/me */
    override suspend fun recordConsent(r: ConsentRecord): Boolean = false /* TODO: POST /consent */
    override suspend fun deleteAccount(): Boolean = false /* TODO: DELETE /account — 즉시 삭제 (Play 요건) */
}

/** 화면이 부르는 곳 — 지금은 가짜. 서버가 생기면 `api = ServerAccountApi()` */
object Accounts {
    lateinit var api: AccountApi
        private set
    private var local: LocalAccountApi? = null

    /** 로그인한 보호자 — 화면이 이것을 본다 (null = 로그인 전 · 샘플 책 보기) */
    var guardian by mutableStateOf<Guardian?>(null)

    fun attach(ctx: Context) {
        if (::api.isInitialized) return
        val l = LocalAccountApi(ctx.applicationContext)
        local = l; api = l
        guardian = l.saved()
    }
}
