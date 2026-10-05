package com.example.finalproject_demo.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/*
 * ── 보호자 계정 (09-29 뼈대 · 10-05 소셜 SDK · 이메일 가입 · 치영) ─────────────────────────────
 *
 * 디자인 시스템(`design/디자인시스템.md` 「로그인 · 계정 결정」):
 *   - 로그인: 카카오 · 네이버 · Google · 이메일. **보호자 정보만** 저장한다. 아이 이름 · 나이는 받지 않는다
 *   - 녹음 · 그림 · 책은 **폰 안에만** 둔다
 *   - 탈퇴 · 데이터 삭제는 부모 영역 → 계정 (스토어 심사 필수)
 *
 * 화면은 [AccountApi] 한 곳만 부른다. 소셜 로그인은 [SocialLogin] 이 각 사 SDK 로 「누구인지」를 알아 오고,
 * 여기 [AccountApi.loginSocial] 이 그 사람을 우리 계정으로 기억한다.
 *
 * 우리 서버에는 아직 계정 기능이 없다(`backend/` 는 이야기 · 목소리만). 그래서 지금은 [LocalAccountApi]
 * (폰 안 SharedPreferences)가 맡는다 — 이메일 가입도 **이 폰 안에** 계정을 만든다(비밀번호는 PBKDF2 해시로만).
 * 서버가 생기면 [ServerAccountApi] 의 할 일(TODO)만 채우고 [Accounts.api] 를 바꿔 끼우면 화면은 그대로다.
 *
 * `net/Server.kt` 와 같은 약속 — **예외를 던지지 않는다.** 실패는 [AuthResult.Fail] 로 돌려준다.
 */

/** 로그인 방법 */
enum class AuthProvider(val label: String) { KAKAO("카카오"), NAVER("네이버"), GOOGLE("Google"), EMAIL("이메일") }

/**
 * 로그인한 보호자 — 서버에 두는 것은 이것뿐이다.
 * @param name 소셜 계정의 별명(없으면 빈칸) · @param dev 키가 없어 개발용 가짜로 들어왔다(계정 탭에 보인다)
 */
data class Guardian(val provider: AuthProvider, val email: String, val since: Long, val name: String = "", val dev: Boolean = false, val uid: String = "") {
    /** 이 폰에서 이 보호자를 가리키는 열쇠 — 카카오처럼 이메일이 없는 계정도 고유 번호로 구분한다 (10-05 · 계정마다 동의 · 마이크) */
    val key: String get() = provider.name + ":" + uid.ifBlank { email.lowercase() }
}

sealed interface AuthResult {
    data class Ok(val guardian: Guardian) : AuthResult
    data class Fail(val why: String) : AuthResult
}

/**
 * 동의 기록 — 무엇에 · 언제 · **어느 판(版)** 에 동의했나 (법정대리인 동의 증빙 · 개인정보보호법 제22조 · 제22조의2).
 * 약관이 바뀌어 [version] 이 올라가면 다시 동의를 받는다.
 */
data class ConsentRecord(
    val terms: Boolean, val privacy: Boolean, val guardian: Boolean, val marketing: Boolean, val at: Long,
    val overseas: Boolean = false, val nameVoice: Boolean = false, val version: String = "",
)

/** 이메일 · 비밀번호 규칙 — 화면이 입력하는 동안 보여 주는 것과 저장할 때 막는 것이 같아야 한다 */
object EmailRules {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun emailOk(e: String) = EMAIL.matches(e.trim())
    fun longEnough(p: String) = p.length in 8..64
    fun hasLetterAndDigit(p: String) = p.any { it.isLetter() } && p.any { it.isDigit() }
    fun passwordOk(p: String) = longEnough(p) && hasLetterAndDigit(p) && p.none { it.isWhitespace() }
}

interface AccountApi {
    /** 이메일 로그인 */
    suspend fun login(email: String, password: String): AuthResult
    /** 이메일 회원가입 — 성공하면 바로 로그인한 상태가 된다 */
    suspend fun signUp(email: String, password: String): AuthResult
    /** 소셜 SDK 로 알아낸 보호자를 우리 계정으로 */
    suspend fun loginSocial(who: SocialIdentity): AuthResult
    /** 지금 로그인한 이메일 계정의 비밀번호가 맞나 — 탈퇴 전 본인 확인 (10-05). 로그인 상태는 바꾸지 않는다 */
    suspend fun verifyPassword(email: String, password: String): Boolean
    /** 비밀번호 다시 정하기 — 서버가 생기면 메일 링크로 바뀐다 */
    suspend fun resetPassword(email: String, newPassword: String): AuthResult
    suspend fun logout()
    /** 세션이 살아 있나 — 죽었으면 「로그인이 풀렸을 때」 화면 */
    suspend fun sessionAlive(): Boolean
    suspend fun recordConsent(r: ConsentRecord): Boolean
    /** 회원 탈퇴 — 서버 계정 · 기록을 **즉시** 지운다. 폰 안 데이터는 부르는 쪽이 따로 지운다 */
    suspend fun deleteAccount(): Boolean
}

/** 지금 쓰는 곳 — 서버 없이 폰 안(SharedPreferences)에만 기억한다 */
class LocalAccountApi(private val ctx: Context) : AccountApi {
    private val prefs get() = ctx.getSharedPreferences("otto_account", Context.MODE_PRIVATE)

    private fun key(email: String) = "acct_" + email.trim().lowercase()

    override suspend fun login(email: String, password: String): AuthResult {
        if (!EmailRules.emailOk(email)) return AuthResult.Fail("이메일 주소를 확인해 주세요")
        val saved = prefs.getString(key(email), null) ?: return AuthResult.Fail("이 폰에서 가입한 이메일이 아니에요. 처음이면 「회원가입」을 눌러 주세요")
        if (!Password.matches(password, saved)) return AuthResult.Fail("비밀번호가 맞지 않아요")
        return remember(Guardian(AuthProvider.EMAIL, email.trim(), prefs.getLong(key(email) + "_since", System.currentTimeMillis()), uid = email.trim().lowercase()))
    }

    override suspend fun signUp(email: String, password: String): AuthResult {
        if (!EmailRules.emailOk(email)) return AuthResult.Fail("이메일 주소를 확인해 주세요")
        if (!EmailRules.passwordOk(password)) return AuthResult.Fail("비밀번호는 영문과 숫자를 섞어 8자 이상이에요")
        if (prefs.contains(key(email))) return AuthResult.Fail("이미 가입한 이메일이에요. 로그인해 주세요")
        val now = System.currentTimeMillis()
        prefs.edit().putString(key(email), Password.hash(password)).putLong(key(email) + "_since", now).apply()
        return remember(Guardian(AuthProvider.EMAIL, email.trim(), now, uid = email.trim().lowercase()))
    }

    override suspend fun loginSocial(who: SocialIdentity): AuthResult {
        // 같은 소셜 계정으로 다시 들어오면 가입한 날을 그대로 둔다
        val idKey = "social_${who.provider.name}_${who.id}"
        val since = prefs.getLong(idKey, 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { prefs.edit().putLong(idKey, it).apply() }
        return remember(Guardian(who.provider, who.email.ifBlank { "${who.provider.label} 계정" }, since, who.name, who.dev, who.id))
    }

    override suspend fun verifyPassword(email: String, password: String): Boolean =
        prefs.getString(key(email), null)?.let { Password.matches(password, it) } ?: false

    override suspend fun resetPassword(email: String, newPassword: String): AuthResult {
        if (!prefs.contains(key(email))) return AuthResult.Fail("이 폰에서 가입한 이메일이 아니에요")
        if (!EmailRules.passwordOk(newPassword)) return AuthResult.Fail("비밀번호는 영문과 숫자를 섞어 8자 이상이에요")
        prefs.edit().putString(key(email), Password.hash(newPassword)).apply()
        return login(email, newPassword)
    }

    private fun remember(g: Guardian): AuthResult {
        prefs.edit().putString("provider", g.provider.name).putString("email", g.email).putLong("since", g.since)
            .putString("name", g.name).putBoolean("dev", g.dev).putString("uid", g.uid).apply()
        return AuthResult.Ok(g)
    }

    override suspend fun logout() { prefs.edit().remove("provider").remove("email").remove("since").remove("name").remove("dev").remove("uid").apply() }

    override suspend fun sessionAlive() = prefs.contains("provider")

    override suspend fun recordConsent(r: ConsentRecord): Boolean {
        prefs.edit().putBoolean("c_terms", r.terms).putBoolean("c_privacy", r.privacy).putBoolean("c_guardian", r.guardian)
            .putBoolean("c_marketing", r.marketing).putBoolean("c_overseas", r.overseas).putBoolean("c_name_voice", r.nameVoice)
            .putString("c_version", r.version).putLong("c_at", r.at).apply()
        return true
    }

    /** 지금 로그인한 계정만 지운다 — 같은 폰에 가입한 다른 이메일 계정까지 지우면 안 된다 */
    override suspend fun deleteAccount(): Boolean {
        val g = saved()
        val e = prefs.edit()
        if (g?.provider == AuthProvider.EMAIL) e.remove(key(g.email)).remove(key(g.email) + "_since")
        if (g != null) prefs.all.keys.filter { it.startsWith("social_${g.provider.name}_") }.forEach { e.remove(it) }
        prefs.all.keys.filter { it.startsWith("c_") }.forEach { e.remove(it) }
        e.remove("provider").remove("email").remove("since").remove("name").remove("dev").remove("uid").apply()
        return true
    }

    fun saved(): Guardian? {
        val p = prefs.getString("provider", null) ?: return null
        val provider = runCatching { AuthProvider.valueOf(p) }.getOrNull() ?: return null
        return Guardian(provider, prefs.getString("email", "") ?: "", prefs.getLong("since", 0), prefs.getString("name", "") ?: "", prefs.getBoolean("dev", false),
            prefs.getString("uid", "") ?: "")
    }
}

/** 비밀번호는 원래 글자를 남기지 않는다 — PBKDF2(HMAC-SHA256 · 12만 번) + 소금 16바이트 */
internal object Password {
    private const val ROUNDS = 120_000
    fun hash(pw: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String =
        "pbkdf2$" + ROUNDS + "$" + salt.hex() + "$" + derive(pw, salt, ROUNDS).hex()

    fun matches(pw: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val want = parts[3]
        val got = derive(pw, parts[2].unhex(), rounds).hex()
        // 끝까지 비교한다 — 어디서 틀렸는지 걸린 시간으로 새지 않게
        return want.length == got.length && want.indices.fold(0) { acc, i -> acc or (want[i].code xor got[i].code) } == 0
    }

    private fun derive(pw: String, salt: ByteArray, rounds: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw.toCharArray(), salt, rounds, 256)).encoded

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * 서버에 붙일 자리 — 엔드포인트는 서버에 계정이 생기면 정한다(아래는 제안 · API 계약 `guidelines/3` 에 먼저 올린다).
 * 붙이는 순서: ① [SocialLogin] 이 받은 토큰을 `/auth/{provider}` 로 보내 서버가 각 사에 확인 → ② 우리 세션 받기 →
 * ③ 세션을 안전한 저장소에. 개인정보: 보호자 이메일 · 로그인 방법 · 동의 기록만. **아이 말 · 그림 · 책은 보내지 않는다.**
 */
class ServerAccountApi : AccountApi {
    override suspend fun login(email: String, password: String): AuthResult = AuthResult.Fail("TODO: POST /auth/email/login")
    override suspend fun signUp(email: String, password: String): AuthResult = AuthResult.Fail("TODO: POST /auth/email/signup — 인증 메일")
    override suspend fun loginSocial(who: SocialIdentity): AuthResult = AuthResult.Fail("TODO: POST /auth/${who.provider.name.lowercase()} {token}")
    override suspend fun verifyPassword(email: String, password: String): Boolean = false /* TODO: POST /auth/email/verify */
    override suspend fun resetPassword(email: String, newPassword: String): AuthResult = AuthResult.Fail("TODO: POST /auth/email/reset — 메일 링크")
    override suspend fun logout() { /* TODO: POST /auth/logout */ }
    override suspend fun sessionAlive(): Boolean = false /* TODO: GET /auth/me */
    override suspend fun recordConsent(r: ConsentRecord): Boolean = false /* TODO: POST /consent */
    override suspend fun deleteAccount(): Boolean = false /* TODO: DELETE /account — 즉시 삭제 (Play 요건) */
}

/** 화면이 부르는 곳 — 지금은 폰 안. 서버가 생기면 `api = ServerAccountApi()` */
object Accounts {
    lateinit var api: AccountApi
        private set
    private var local: LocalAccountApi? = null

    /** 로그인한 보호자 — 화면이 이것을 본다 (null = 로그인 전 · 샘플 책 보기) */
    var guardian by mutableStateOf<Guardian?>(null)

    private var boundTo: Context? = null

    /**
     * 앱이 새로 뜨면(같은 프로세스에 새 Application — 테스트가 그렇다) 그 앱의 저장소에 다시 붙는다.
     * 전에는 처음 붙은 것을 계속 써서, 앞 테스트가 가입한 이메일이 남아 「이미 가입한 이메일」로 막혔다(#119 CI)
     */
    fun attach(ctx: Context) {
        val app = ctx.applicationContext
        if (::api.isInitialized && boundTo === app) return
        val l = LocalAccountApi(app)
        local = l; api = l; boundTo = app
        guardian = l.saved()
    }

    /** 로그아웃 — SDK 세션도 끊고, 우리 쪽 기억을 지운다. 폰 안의 책은 그대로 */
    suspend fun logout(ctx: Context) {
        guardian?.let { SocialLogin.signOut(ctx, it.provider) }
        api.logout(); guardian = null
    }

    /** 회원 탈퇴 — 소셜이면 그 회사와 앱의 연결을 끊고(unlink), 우리 계정을 지운다 */
    suspend fun withdraw(ctx: Context) {
        guardian?.let { SocialLogin.unlink(ctx, it.provider) }
        api.deleteAccount(); guardian = null
    }
}
