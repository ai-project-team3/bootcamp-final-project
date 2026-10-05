package com.example.finalproject_demo

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.finalproject_demo.net.AuthProvider
import com.example.finalproject_demo.net.AuthResult
import com.example.finalproject_demo.net.EmailRules
import com.example.finalproject_demo.net.LocalAccountApi
import com.example.finalproject_demo.net.SocialIdentity
import com.example.finalproject_demo.net.SocialLogin
import com.example.finalproject_demo.net.SocialResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 보호자 계정 (10-05) — 이메일 회원가입 · 로그인 · 비밀번호 다시 정하기 · 소셜 계정 기억 · 탈퇴.
 * 서버가 없어 폰 안(`LocalAccountApi`)에 둔다. 비밀번호 원문이 저장되지 않는 것까지 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = ctx.getSharedPreferences("otto_account", Context.MODE_PRIVATE)
    private lateinit var api: LocalAccountApi

    @Before fun fresh() { prefs.edit().clear().commit(); api = LocalAccountApi(ctx) }

    @Test fun passwordRules() {
        assertTrue(EmailRules.emailOk("parent@example.com"))
        assertFalse(EmailRules.emailOk("parent@example"))
        assertFalse(EmailRules.passwordOk("abcdefgh"))        // 숫자 없음
        assertFalse(EmailRules.passwordOk("1234567a".take(7))) // 7자
        assertFalse(EmailRules.passwordOk("abcd 1234"))       // 빈칸
        assertTrue(EmailRules.passwordOk("otto2026"))
    }

    @Test fun signUpThenLoginKeepsOnlyAHash() = runBlocking {
        val r = api.signUp("Parent@Example.com", "otto2026")
        assertTrue(r is AuthResult.Ok)
        assertFalse("비밀번호 원문이 저장됐다", prefs.all.values.any { it.toString().contains("otto2026") })
        // 같은 이메일(대소문자만 다름)로 또 가입할 수 없다
        assertTrue(api.signUp("parent@example.com", "otto2027") is AuthResult.Fail)
        api.logout()
        assertNull(api.saved())
        assertTrue(api.login("parent@example.com", "wrong999") is AuthResult.Fail)
        val ok = api.login("parent@example.com", "otto2026")
        assertTrue(ok is AuthResult.Ok)
        assertEquals(AuthProvider.EMAIL, (ok as AuthResult.Ok).guardian.provider)
    }

    @Test fun unknownEmailCannotLogInOrReset() = runBlocking {
        assertTrue(api.login("nobody@example.com", "otto2026") is AuthResult.Fail)
        assertTrue(api.resetPassword("nobody@example.com", "otto2027") is AuthResult.Fail)
    }

    @Test fun resetPasswordReplacesTheOldOne() = runBlocking {
        api.signUp("p@example.com", "otto2026"); api.logout()
        assertTrue(api.resetPassword("p@example.com", "fresh2027") is AuthResult.Ok)
        api.logout()
        assertTrue(api.login("p@example.com", "otto2026") is AuthResult.Fail)
        assertTrue(api.login("p@example.com", "fresh2027") is AuthResult.Ok)
    }

    @Test fun socialAccountKeepsItsJoinDate() = runBlocking {
        val who = SocialIdentity(AuthProvider.KAKAO, "42", "p@kakao.com", "보호자")
        val first = (api.loginSocial(who) as AuthResult.Ok).guardian
        api.logout()
        Thread.sleep(5)
        val again = (api.loginSocial(who) as AuthResult.Ok).guardian
        assertEquals("다시 로그인했는데 가입한 날이 바뀌었다", first.since, again.since)
        assertEquals("보호자", again.name)
    }

    @Test fun withdrawRemovesOnlyThatAccount() = runBlocking {
        api.signUp("a@example.com", "otto2026"); api.logout()
        api.signUp("b@example.com", "otto2026")
        api.deleteAccount()
        assertNull(api.saved())
        assertTrue("탈퇴한 계정으로 다시 로그인됐다", api.login("b@example.com", "otto2026") is AuthResult.Fail)
        assertTrue("같은 폰의 다른 계정까지 지워졌다", api.login("a@example.com", "otto2026") is AuthResult.Ok)
    }

    /** JVM 검사에는 각 사 SDK 창이 없다 — 개발용 계정으로 넘어가고, 계정 탭에서 그것이 보인다 */
    @Test fun socialLoginWithoutSdkIsMarkedAsDev() = runBlocking {
        val r = SocialLogin.signIn(null, AuthProvider.NAVER)
        assertTrue(r is SocialResult.Ok)
        val g = (api.loginSocial((r as SocialResult.Ok).who) as AuthResult.Ok).guardian
        assertTrue(g.dev)
        assertNotNull(api.saved()?.dev?.takeIf { it })
    }
}
