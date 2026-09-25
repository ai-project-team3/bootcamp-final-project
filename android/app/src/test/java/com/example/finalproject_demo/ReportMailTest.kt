package com.example.finalproject_demo

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.example.finalproject_demo.ui.REPORT_TO
import com.example.finalproject_demo.ui.ReportSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 앱 내 신고가 **실제로 어딘가로 간다** (09-25).
 *
 * 전에는 메모리 리스트에 쌓고 끝이었다 — 로그 문구가 *"저장만 하고 보내지 않음"* 이라고 자백했다.
 * Play 가 요구하는 「앱 내 신고」가 아무 데도 안 가는 것이었다. 서버가 아직 없으므로 메일 앱으로 넘긴다.
 *
 * 이 검사는 **메일 앱을 여는 의도(Intent)가 맞는 주소·내용으로 나가는가**만 본다.
 * 부모가 거기서 보내기를 눌렀는지는 앱도 검사도 모른다 — 화면도 그렇게 말한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h393dp-land-320dpi")
class ReportMailTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reportOpensMailToTheListedAddressWithOnlyWhatTheParentWrote() {
        compose.setContent { ReportSection() }

        compose.onNodeWithText("신고하기").performClick()
        compose.onNodeWithText("오류").performClick()
        // 진짜 입력칸이다 — 전에는 누르면 「(부모가 적은 내용)」이 박히는 가짜였다
        compose.onNode(hasSetTextAction()).performTextInput("책 넘기기가 멈췄어요")
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()

        val sent = shadowOf(compose.activity).nextStartedActivity
        assert(sent != null) { "보내기를 눌렀는데 아무 화면도 안 열렸다" }
        assert(sent.action == Intent.ACTION_SENDTO) { "메일 의도가 아니다: ${sent.action}" }

        val uri = sent.data.toString()
        assert(uri.startsWith("mailto:$REPORT_TO")) {
            "받는 주소가 처리방침의 연락처와 다르다: $uri"
        }
        val body = sent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assert("오류" in body) { "사유가 안 실렸다" }
        assert("책 넘기기가 멈췄어요" in body) { "부모가 적은 글이 안 실렸다" }

        // 화면은 「보냈다」가 아니라 「메일 앱이 열렸다」고 말한다 — 보냈는지는 모른다
        compose.onNodeWithText("메일 앱이 열렸어요. 거기서 보내기를 누르시면 저희에게 전달돼요.")
            .assertExists()
    }
}
