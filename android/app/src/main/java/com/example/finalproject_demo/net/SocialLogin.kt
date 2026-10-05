package com.example.finalproject_demo.net

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.finalproject_demo.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.util.NidOAuthCallback
import com.navercorp.nid.profile.domain.vo.NidProfile
import com.navercorp.nid.profile.util.NidProfileCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/*
 * ── 소셜 로그인 — 카카오 · 네이버 · Google SDK 를 실제로 부른다 (10-05 · 치영) ─────────────────────
 *
 * 화면(`ui/shell/Onboarding.kt`)은 [SocialLogin.signIn] 한 곳만 부른다. 결과는 [SocialIdentity](누구인가)이고,
 * 그것을 [AccountApi.loginSocial] 에 넘기면 우리 계정으로 기억한다. 예외는 던지지 않는다 — 실패는 [SocialResult.Fail].
 *
 *   카카오   카카오톡이 깔려 있으면 카카오톡으로, 없거나 실패하면 카카오계정(웹)으로 → 사용자 정보(me)
 *   네이버   네이버 앱 · 웹 로그인 창 → 프로필 API
 *   Google  Credential Manager 「Google 로 로그인」 창 → ID 토큰(이메일 · 이름)
 *
 * 키는 `local.properties`(레포에 올리지 않음) → BuildConfig. 받는 법은 `docs/소셜로그인_키_발급.md`.
 * **키가 없으면** 그 버튼은 진짜 로그인을 할 수 없다:
 *   - 디버그 빌드 · JVM 검사 — 개발용 가짜 계정으로 넘어간다(화면 흐름을 확인할 수 있게). 계정 탭에 「개발용」으로 보인다
 *   - 스토어 빌드 — 「아직 준비 중이에요」를 띄우고 넘어가지 않는다(가짜로 로그인한 척하지 않는다)
 *
 * 개인정보: SDK 에서 받는 것은 **보호자** 계정의 고유 번호 · 이메일 · 이름(별명)뿐이다. 아이 정보는 받지 않는다.
 */

/** 소셜 로그인으로 알아낸 보호자 — [token] 은 서버가 생기면 `/auth/{provider}` 로 보내 확인받는다 */
data class SocialIdentity(val provider: AuthProvider, val id: String, val email: String, val name: String, val token: String = "", val dev: Boolean = false)

sealed interface SocialResult {
    data class Ok(val who: SocialIdentity) : SocialResult
    /** 보호자가 창을 닫았다 — 알림 없이 그대로 둔다 */
    data object Cancelled : SocialResult
    data class Fail(val why: String) : SocialResult
}

object SocialLogin {
    /** JVM 검사(Robolectric)에서는 SDK 창을 띄울 수 없다 */
    private val underTest get() = Build.FINGERPRINT == "robolectric"

    fun configured(p: AuthProvider): Boolean = when (p) {
        AuthProvider.KAKAO -> BuildConfig.KAKAO_APP_KEY.isNotBlank()
        AuthProvider.NAVER -> BuildConfig.NAVER_CLIENT_ID.isNotBlank() && BuildConfig.NAVER_CLIENT_SECRET.isNotBlank()
        AuthProvider.GOOGLE -> BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
        AuthProvider.EMAIL -> true
    }

    private var inited = false

    /** 앱이 켜질 때 한 번 (`OttoShell`) — 키가 있는 SDK 만 깨운다 */
    fun init(ctx: Context) {
        if (inited || underTest) return
        inited = true
        val app = ctx.applicationContext
        if (configured(AuthProvider.KAKAO)) runCatching { KakaoSdk.init(app, BuildConfig.KAKAO_APP_KEY) }
        if (configured(AuthProvider.NAVER)) runCatching {
            NidOAuth.initialize(app, BuildConfig.NAVER_CLIENT_ID, BuildConfig.NAVER_CLIENT_SECRET, "오또")
        }
    }

    suspend fun signIn(activity: Activity?, p: AuthProvider): SocialResult {
        if (p == AuthProvider.EMAIL) return SocialResult.Fail("이메일은 이메일 화면에서 로그인해요")
        if (underTest || activity == null || !configured(p)) {
            return if (BuildConfig.DEBUG || underTest) SocialResult.Ok(devIdentity(p))
            else SocialResult.Fail("${p.label} 로그인은 아직 준비 중이에요. 다른 방법으로 시작해 주세요")
        }
        init(activity)
        return runCatching {
            when (p) {
                AuthProvider.KAKAO -> kakao(activity)
                AuthProvider.NAVER -> naver(activity)
                AuthProvider.GOOGLE -> google(activity)
                AuthProvider.EMAIL -> SocialResult.Cancelled
            }
        }.getOrElse { SocialResult.Fail("${p.label} 로그인 중에 문제가 생겼어요. 잠시 뒤 다시 해 주세요") }
    }

    /** 로그아웃 — SDK 쪽 세션도 끊는다(다음에 다른 계정을 고를 수 있게). 실패해도 우리 쪽 로그아웃은 된다 */
    suspend fun signOut(ctx: Context, p: AuthProvider) {
        if (underTest || !configured(p)) return
        runCatching {
            when (p) {
                AuthProvider.KAKAO -> suspendCancellableCoroutine { c -> UserApiClient.instance.logout { c.resume(Unit) } }
                AuthProvider.NAVER -> suspendCancellableCoroutine { c -> NidOAuth.logout(done(c)) }
                AuthProvider.GOOGLE -> CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
                AuthProvider.EMAIL -> {}
            }
        }
    }

    /**
     * 회원 탈퇴 — 각 사 정책대로 **앱과의 연결을 끊는다**(카카오 unlink · 네이버 토큰 삭제).
     * 끊어야 다음에 가입할 때 동의 창이 다시 뜨고, 그 회사에 남은 「오또와 연결됨」 기록도 지워진다.
     */
    suspend fun unlink(ctx: Context, p: AuthProvider) {
        if (underTest || !configured(p)) return
        runCatching {
            when (p) {
                AuthProvider.KAKAO -> suspendCancellableCoroutine { c -> UserApiClient.instance.unlink { c.resume(Unit) } }
                AuthProvider.NAVER -> suspendCancellableCoroutine { c -> NidOAuth.disconnect(done(c)) }
                AuthProvider.GOOGLE -> CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
                AuthProvider.EMAIL -> {}
            }
        }
    }

    private fun devIdentity(p: AuthProvider) =
        SocialIdentity(p, "dev-${p.name.lowercase()}", "보호자@${p.name.lowercase()}.dev", "개발용 보호자", dev = true)

    // ── 카카오 ─────────────────────────────────────────────

    private suspend fun kakao(activity: Activity): SocialResult {
        val api = UserApiClient.instance
        var token: OAuthToken? = null
        var error: Throwable? = null
        if (api.isKakaoTalkLoginAvailable(activity)) {
            kakaoCall { cb -> api.loginWithKakaoTalk(activity, callback = cb) }.let { (t, e) -> token = t; error = e }
            // 카카오톡 창에서 보호자가 직접 취소했으면 웹으로 넘기지 않는다(카카오 가이드)
            if (error is ClientError && (error as ClientError).reason == ClientErrorCause.Cancelled) return SocialResult.Cancelled
        }
        if (token == null) {
            kakaoCall { cb -> api.loginWithKakaoAccount(activity, callback = cb) }.let { (t, e) -> token = t; error = e }
        }
        val t = token ?: return if (error is ClientError && (error as ClientError).reason == ClientErrorCause.Cancelled) SocialResult.Cancelled
            else SocialResult.Fail("카카오 로그인에 실패했어요. 다시 해 주세요")
        return suspendCancellableCoroutine { c ->
            api.me { user, e ->
                if (user == null) c.resume(SocialResult.Fail("카카오 계정 정보를 받지 못했어요 (${e?.message ?: "알 수 없음"})"))
                else c.resume(SocialResult.Ok(SocialIdentity(
                    AuthProvider.KAKAO, user.id?.toString() ?: "", user.kakaoAccount?.email ?: "",
                    user.kakaoAccount?.profile?.nickname ?: "", t.accessToken,
                )))
            }
        }
    }

    private suspend fun kakaoCall(start: ((OAuthToken?, Throwable?) -> Unit) -> Unit): Pair<OAuthToken?, Throwable?> =
        suspendCancellableCoroutine { c -> start { t, e -> if (c.isActive) c.resume(t to e) } }

    // ── 네이버 ─────────────────────────────────────────────

    /** 성공이든 실패든 끝나면 이어 간다 — 로그아웃 · 연결 끊기는 실패해도 우리 쪽은 진행한다 */
    private fun done(c: kotlinx.coroutines.CancellableContinuation<Unit>) = object : NidOAuthCallback {
        override fun onSuccess() { if (c.isActive) c.resume(Unit) }
        override fun onFailure(errorCode: String, errorDesc: String) { if (c.isActive) c.resume(Unit) }
    }

    private suspend fun naver(activity: Activity): SocialResult {
        val login = suspendCancellableCoroutine<SocialResult?> { c ->
            NidOAuth.requestLogin(activity, object : NidOAuthCallback {
                override fun onSuccess() { if (c.isActive) c.resume(null) }
                override fun onFailure(errorCode: String, errorDesc: String) {
                    // 보호자가 창을 닫으면 user_cancel 이 온다 — 알림 없이 그대로
                    val cancelled = errorCode.contains("cancel", true) || errorDesc.contains("cancel", true)
                    if (c.isActive) c.resume(if (cancelled) SocialResult.Cancelled else SocialResult.Fail("네이버 로그인에 실패했어요. 다시 해 주세요"))
                }
            })
        }
        if (login != null) return login
        val token = NidOAuth.getAccessToken() ?: ""
        return suspendCancellableCoroutine { c ->
            NidOAuth.getUserProfile(object : NidProfileCallback<NidProfile> {
                override fun onSuccess(result: NidProfile) {
                    val p = result.profile
                    if (c.isActive) c.resume(SocialResult.Ok(SocialIdentity(AuthProvider.NAVER, p?.id ?: "", p?.email ?: "", p?.nickname ?: p?.name ?: "", token)))
                }
                override fun onFailure(errorCode: String, errorDesc: String) { if (c.isActive) c.resume(SocialResult.Fail("네이버 계정 정보를 받지 못했어요")) }
            })
        }
    }

    // ── Google ────────────────────────────────────────────

    private suspend fun google(activity: Activity): SocialResult {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val cred = CredentialManager.create(activity).getCredential(activity, req).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                SocialResult.Ok(SocialIdentity(AuthProvider.GOOGLE, g.id, g.id, g.displayName ?: "", g.idToken))
            } else SocialResult.Fail("Google 계정 정보를 읽지 못했어요")
        } catch (_: GetCredentialCancellationException) {
            SocialResult.Cancelled
        } catch (_: NoCredentialException) {
            SocialResult.Fail("이 폰에 Google 계정이 없어요. 설정에서 계정을 추가해 주세요")
        } catch (e: GetCredentialException) {
            SocialResult.Fail("Google 로그인에 실패했어요. 다시 해 주세요")
        }
    }
}
