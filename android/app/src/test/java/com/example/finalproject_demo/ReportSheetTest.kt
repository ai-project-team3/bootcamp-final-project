package com.example.finalproject_demo

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performTextInput
import com.example.finalproject_demo.demo.ReportBook
import com.example.finalproject_demo.net.ReportNames
import com.example.finalproject_demo.net.ReportUpload
import com.example.finalproject_demo.ui.REPORT_TO
import com.example.finalproject_demo.ui.ReportSection
import com.example.finalproject_demo.ui.ReportSheet
import com.example.finalproject_demo.ui.SentReports
import com.example.finalproject_demo.ui.shell.LocalWipe
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #283 ② 신고 화면 (설계 §7-1 A7 · A8 · A9) — 미리 보기 안에서만 보내고, 서버가 받으면 접수 번호,
 * 닿지 못하면 보호자가 누를 때만 메일(첨부 없이). 설정에서 오면 첨부 칸이 없다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h600dp-land-320dpi")
class ReportSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var sentBody: JSONObject? = null

    private val book = ReportBook(
        mode = "story",
        pages = listOf("지민이는 숲에 갔어요", "뽀삐가 크게 울었어요"),
        pictures = emptyList(),
        presetBackground = "bg_forest",
        names = ReportNames("지민", listOf("뽀삐")),
    )

    @Before fun setUp() {
        val ctx = compose.activity.applicationContext
        ctx.getSharedPreferences(SentReports.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        SentReports.attach(ctx)
    }

    @After fun tearDown() { ReportUpload.sendOverride = null }

    /** 앱에서는 책장 정리 · 설정 목록 안(스크롤)에 뜬다 — 가로 600dp 화면에서 아래 칸이 밖으로 나간다 */
    @androidx.compose.runtime.Composable
    private fun Scrolling(content: @androidx.compose.runtime.Composable () -> Unit) =
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) { content() }

    private fun fill(category: String, note: String) {
        compose.onNodeWithText(category).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput(note)
    }

    /** A8 — 서버가 받으면 「접수됐어요」 + 번호 · 기기에 번호가 남고 · 기기 비우기로 지워진다 */
    @Test
    fun acceptedShowsTheNumberAndKeepsIt() {
        ReportUpload.sendOverride = { body -> sentBody = body; "R-1007-3FA2" }
        compose.setContent { Scrolling { ReportSheet(book) } }
        compose.onNodeWithText("2쪽").performScrollTo().performClick()
        fill("부적절한 표현", "무서운 말이 있어요")
        compose.onNodeWithText("문제 된 그림 / 문장 같이 보내기").performScrollTo().performClick()
        compose.onNodeWithText("문장").performScrollTo().performClick()
        compose.onNodeWithText("보낼 내용 미리 보기").performScrollTo().performClick()
        // 보내는 문장은 가린 그대로 미리 보인다(보이는 것 = 가는 것)
        compose.onNodeWithText("“친구1가 크게 울었어요”").assertExists()
        compose.onNodeWithText("보내기").performScrollTo().performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("접수됐어요 · 접수 번호 R-1007-3FA2").fetchSemanticsNodes().isNotEmpty() }

        val b = sentBody!!
        assertEquals("text", b.getString("category"))
        assertEquals(2, b.getInt("page"))
        assertEquals("친구1가 크게 울었어요", b.getJSONObject("attachment").getString("text"))
        assertEquals(listOf("R-1007-3FA2"), SentReports.list.map { it.id })
        val ctx = compose.activity.applicationContext
        assertTrue(ctx.getSharedPreferences(SentReports.PREFS, Context.MODE_PRIVATE).getString("list", "")!!.contains("R-1007-3FA2"))
        LocalWipe.wipe(ctx)
        assertTrue("기기를 비우면 번호도 지운다", SentReports.list.isEmpty())
    }

    /** A7 — 서버에 닿지 못하면 메일 단추 · 누를 때만 열리고 · 메일에 첨부 문장이 없다 */
    @Test
    fun unreachedOffersMailWithoutTheAttachment() {
        ReportUpload.sendOverride = { null }
        compose.setContent { Scrolling { ReportSheet(book) } }
        fill("부적절한 표현", "책 넘기기가 멈췄어요")
        compose.onNodeWithText("문제 된 그림 / 문장 같이 보내기").performScrollTo().performClick()
        compose.onNodeWithText("문장").performScrollTo().performClick()
        compose.onNodeWithText("보낼 내용 미리 보기").performScrollTo().performClick()
        compose.onNodeWithText("보내기").performScrollTo().performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("서버에 닿지 못했어요.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("메일은 누르기 전에 열지 않는다", null, shadowOf(compose.activity).nextStartedActivity)

        compose.onNodeWithText("메일로 보내기").performScrollTo().performClick()
        val sent = shadowOf(compose.activity).nextStartedActivity!!
        assertEquals(Intent.ACTION_SENDTO, sent.action)
        assertTrue(sent.data.toString().startsWith("mailto:$REPORT_TO"))
        val body = sent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue("부적절한 표현" in body && "책 넘기기가 멈췄어요" in body && "1쪽" in body)
        assertFalse("메일에 첨부 문장이 실렸다", "숲에 갔어요" in body || "친구1" in body)
        assertTrue(SentReports.list.isEmpty())
    }

    /** A9 — 설정에서 오면(책 없음) 첨부 칸이 없고 분류 · 설명만 */
    @Test
    fun fromSettingsThereIsNoAttachment() {
        ReportUpload.sendOverride = { body -> sentBody = body; "R-1007-0001" }
        compose.setContent { Scrolling { ReportSection() } }
        compose.onNodeWithText("신고하기").performScrollTo().performClick()
        assertEquals(0, compose.onAllNodesWithText("문제 된 그림 / 문장 같이 보내기").fetchSemanticsNodes().size)
        fill("오류", "앱이 멈췄어요")
        compose.onNodeWithText("보낼 내용 미리 보기").performScrollTo().performClick()
        compose.onNodeWithText("보내기").performScrollTo().performClick()
        compose.waitUntil(3_000) { sentBody != null }
        val b = sentBody!!
        assertTrue(b.isNull("mode") && b.isNull("page") && b.isNull("attachment"))
        assertEquals("error", b.getString("category"))
    }

    /** 분류를 고르기 전에는 미리 보기로 넘어가지 않는다 · 보내기 단추는 미리 보기 안에만 */
    @Test
    fun sendOnlyFromThePreview() {
        compose.setContent { Scrolling { ReportSheet(book) } }
        assertEquals(0, compose.onAllNodesWithText("보내기").fetchSemanticsNodes().size)
        compose.onNodeWithText("보낼 내용 미리 보기").performScrollTo().performClick()
        compose.onNodeWithText("부적절한 그림").assertExists()
        assertEquals(0, compose.onAllNodesWithText("보내기").fetchSemanticsNodes().size)
    }
}
